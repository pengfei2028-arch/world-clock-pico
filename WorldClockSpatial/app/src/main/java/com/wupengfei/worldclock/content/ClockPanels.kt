package com.wupengfei.worldclock.content

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.*
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.ui.foundation.gesture.detectDragGestures
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.hover.spatialHoverEffectGroup
import com.pico.spatial.ui.foundation.hover.SpatialHoverEffectGroup
import com.pico.spatial.ui.foundation.hover.disableSpatialHoverEffect
import com.pico.spatial.ui.platform.containers.closeStage
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.launch
import com.pico.spatial.ui.graphics.SpatialHoverStyle
import com.pico.spatial.ui.platform.Material
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp

@Stable
internal class ClockState {
    var mode by mutableStateOf(EarthMode.WORLD_TIME)
    val temperature=TemperatureState()
    private var temperatureDragHours=0f
    fun switchMode(next:EarthMode){touchUi();mode=next;timeToolsOpen=false}
    fun weatherLabel(city:ClockCity,compact:Boolean=false)=if(mode==EarthMode.TEMPERATURE)temperature.label(city) else (weather[city.id]?:CityWeatherState()).label(now,compact)
    var catalog by mutableStateOf<CityCatalog?>(null)
    var weather by mutableStateOf<Map<String,CityWeatherState>>(emptyMap())
    var cities by mutableStateOf<List<ClockCity>>(emptyList())
    var selectedId by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var searching by mutableStateOf(false)
    var notice by mutableStateOf("")
    var clockNotice by mutableStateOf("")
    var ready by mutableStateOf(false)
    var now by mutableStateOf(Instant.now())
    var previewBase by mutableStateOf<Instant?>(null)
    var offsetMinutes by mutableFloatStateOf(0f)
    var rulerCenter by mutableStateOf<Instant?>(null)
    val labelOnRight=mutableStateListOf(true,true,true,true,true,true,true,true)
    val labelOpacity=mutableStateListOf(0f,0f,0f,0f,0f,0f,0f,0f)
    var citiesOpen by mutableStateOf(false)
    var timeToolsOpen by mutableStateOf(false)
    val toolbarDrag=ToolbarDrag()
    var toolbarHandPoint:((Float,Float,Float)->Offset)?=null
    var toolbarHandGain:((Float,Float,Float)->Double)?=null
    var onTimeScrub:((Boolean)->Unit)?=null
    var uiManipulating by mutableStateOf(false)
    var uiActivity=0
    fun touchUi(){uiActivity++}
    fun toggleCities(){touchUi();citiesOpen=!citiesOpen;timeToolsOpen=false}
    fun toggleTimeTools(){touchUi();timeToolsOpen=!timeToolsOpen;citiesOpen=false}
    var focusRequest=0
    var resumeRequest=0
    val clockInstant get()=(previewBase?:now).plusMillis((offsetMinutes*60000).toLong())
    val instant get()=if(mode==EarthMode.TEMPERATURE)temperature.displayed?.validTime?:clockInstant else clockInstant
    val preview get()=previewBase!=null
    val rulerOrigin get()=rulerCenter?:instant
    val nowMarkerMinutes get()=Duration.between(rulerOrigin,now).toMillis()/60000f
    val previewMarkerMinutes get()=Duration.between(rulerOrigin,instant).toMillis()/60000f
    val selected get()=cities.firstOrNull{it.id==selectedId}
    fun setOffset(value:Float){touchUi();if(previewBase==null)previewBase=now;offsetMinutes=value}
    fun beginTimeDrag(){
        onTimeScrub?.invoke(true);manualInteraction()
        touchUi()
        if(mode==EarthMode.TEMPERATURE){temperatureDragHours=temperature.index.toFloat();return}
        if(rulerCenter==null)rulerCenter=instant
        previewBase=instant;offsetMinutes=0f
    }
    fun dragTimeBy(minutes:Float){
        if(!minutes.isFinite())return
        if(mode==EarthMode.TEMPERATURE){temperatureDragHours=(temperatureDragHours+minutes/60f).coerceIn(0f,(temperature.manifest?.frames?.lastIndex?:0).toFloat());temperature.select(kotlin.math.round(temperatureDragHours).toInt());return}
        setOffset(offsetMinutes+minutes)
        val center=rulerCenter?:instant
        val cursor=Duration.between(center,instant).toMillis()/60000.0
        // Keep the visible thumb under the hand; pan the six-hour ruler at its edges.
        val excess=cursor-cursor.coerceIn(-170.0,170.0)
        if(excess!=0.0)rulerCenter=center.plusMillis((excess*60000).toLong())
    }
    fun finishTimeDrag(){onTimeScrub?.invoke(false);touchUi();if(mode==EarthMode.WORLD_TIME&&preview){previewBase=instant;offsetMinutes=0f}}
    fun live(){touchUi();previewBase=null;offsetMinutes=0f;rulerCenter=null}
    fun shiftDay(days:Long){
        touchUi()
        previewBase=instant.atZone(ZoneId.systemDefault()).plusDays(days).toInstant()
        offsetMinutes=0f;rulerCenter=previewBase
    }
    fun chooseDate(date:LocalDate){
        require(date.year in 1900..2100)
        touchUi()
        val local=instant.atZone(ZoneId.systemDefault())
        previewBase=date.atTime(local.toLocalTime()).atZone(local.zone).toInstant()
        offsetMinutes=0f;rulerCenter=previewBase
    }
    fun setDate(text:String):Boolean=try{chooseDate(LocalDate.parse(text));true}catch(_:Exception){false}
    fun manualInteraction(){selectedId=null;focusRequest++}
    fun focus(city:ClockCity){touchUi();selectedId=city.id;focusRequest++;citiesOpen=false;timeToolsOpen=false}
    fun add(city:ClockCity,context:Context){
        if(cities.none{it.id==city.id}){
            if(cities.size>=8){notice="最多添加 8 座城市";return}
            cities=cities+city;save(context)
        }
        focus(city);searching=false;query="";notice=""
    }
    fun remove(city:ClockCity,context:Context){
        touchUi();cities=cities.filterNot{it.id==city.id}
        if(selectedId==city.id){selectedId=null;resumeRequest++}
        save(context)
    }
    fun save(context:Context){context.getSharedPreferences("world_clock",0).edit().putString("cityIds",cities.joinToString(","){it.id}).apply()}
}

@Composable
internal fun ClockTheme(content:@Composable ()->Unit){
    val c=PicoTheme.colorScheme
    PicoTheme(colorScheme=c.copy(labelPrimary=c.labelPrimaryLight,
        labelSecondary=c.labelPrimaryLight.copy(alpha=.78f),
        labelTertiary=c.labelPrimaryLight.copy(alpha=.58f),
        labelQuaternary=c.labelPrimaryLight.copy(alpha=.4f))){
        // Each actionable control uses the official Highlight style; no global hover blocker.
        Box { content() }
    }
}
@Composable
internal fun uiText(size:Int=16,weight:FontWeight=FontWeight.Normal)=
    PicoTheme.typography.bodyLarge.copy(fontFamily=FontFamily.SansSerif,
        fontSize=size.sp,lineHeight=(size+4).sp,fontWeight=weight,color=Color.White)

private val LocalInsideGlass=staticCompositionLocalOf { false }

@Composable
internal fun Glass(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    CompositionLocalProvider(LocalInsideGlass provides true) {
        Column(modifier.clip(RoundedCornerShape(20.dp)).backgroundMaterial().padding(14.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}

@Composable
internal fun GlassAction(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    shape:RoundedCornerShape=CircleShape,horizontalPadding:Dp=14.dp,content:@Composable RowScope.()->Unit){
    val interaction=remember{MutableInteractionSource()}
    val hovered by interaction.collectIsHoveredAsState()
    Row(modifier.clip(shape).backgroundMaterial(style=if(LocalInsideGlass.current)Material.None else Material.Regular)
        .graphicsLayer{clip=true;this.shape=shape;alpha=if(enabled)1f else .45f}
        .spatialHoverEffect(SpatialHoverStyle.Highlight,enabled)
        .background(Color.White.copy(alpha=if(hovered).16f else .025f))
        .hoverable(interaction,enabled=enabled)
        .clickable(enabled=enabled,interactionSource=interaction,indication=null,onClick=onClick)
        .padding(horizontal=horizontalPadding),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center,content=content)
}

@Composable
internal fun CityPanel(state:ClockState,context:Context){
    var manage by remember{mutableStateOf(false)}
    if(state.searching){
        Glass(Modifier.width(264.dp)){
            Row(Modifier.fillMaxWidth().height(32.dp),verticalAlignment=Alignment.CenterVertically){
                Text(if(manage)"管理城市" else "添加城市",style=uiText(16,FontWeight.Medium),modifier=Modifier.weight(1f))
                GlassAction({state.searching=false;manage=false},Modifier.size(32.dp),horizontalPadding=0.dp){Text("×",style=uiText(20))}
            }
            if(manage){
                Column(Modifier.heightIn(max=220.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    state.cities.forEach{city->
                        Row(Modifier.fillMaxWidth().height(40.dp),verticalAlignment=Alignment.CenterVertically){
                            Text(city.name,style=uiText(),modifier=Modifier.weight(1f),maxLines=1)
                            GlassAction({state.remove(city,context)},Modifier.height(32.dp)){Text("移除",style=uiText(13))}
                        }
                    }
                }
                GlassAction({manage=false},Modifier.fillMaxWidth().height(38.dp)){Text("添加城市",style=uiText(14))}
                Text("地名 © GeoNames · CC BY 4.0",style=uiText(10),color=PicoTheme.colorScheme.labelTertiary)
            }else{
                TextField(value=state.query,onValueChange={state.touchUi();state.query=it},
                    modifier=Modifier.fillMaxWidth().spatialHoverEffect(SpatialHoverStyle.Highlight),textStyle=uiText(16),
                    colors=TextFieldDefaults.textFieldColors(textColor=Color.White,backgroundColor=Color.White.copy(alpha=.06f),focusedColor=Color.White.copy(alpha=.12f),placeholderColor=Color.White.copy(alpha=.55f)),
                    placeholder={Text("城市或国家",style=uiText(16))},enabled=state.catalog!=null)
                val results=remember(state.catalog,state.query){state.catalog?.search(state.query).orEmpty()}
                Column(Modifier.heightIn(max=220.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    if(results.isEmpty())Text(if(state.catalog==null)"正在载入" else "没有匹配城市",style=uiText(14))
                    results.forEach{city->
                        GlassAction({state.add(city,context)},Modifier.fillMaxWidth().height(40.dp),state.ready){
                          Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                            Text(city.name,style=uiText(15),modifier=Modifier.weight(1f).alignByBaseline(),
                                maxLines=1,overflow=TextOverflow.Ellipsis)
                            Spacer(Modifier.width(8.dp))
                            Text(city.country,style=uiText(12),color=PicoTheme.colorScheme.labelSecondary,
                                modifier=Modifier.widthIn(max=76.dp).alignByBaseline(),maxLines=1,overflow=TextOverflow.Ellipsis)
                          }
                        }
                    }
                }
                if(state.cities.isNotEmpty())GlassAction({manage=true},Modifier.fillMaxWidth().height(34.dp)){Text("管理城市",style=uiText(13))}
            }
            if(state.notice.isNotBlank())Text(state.notice,style=uiText(12),color=PicoTheme.colorScheme.labelSecondary)
        }
        return
    }
    Column(Modifier.width(208.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            state.cities.forEach{city->
                GlassAction({state.focus(city)},Modifier.fillMaxWidth().height(64.dp),state.ready){
                  Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp)){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Text(city.name,style=uiText(16),modifier=Modifier.weight(1f).alignByBaseline(),maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(city.time(state.instant),style=uiText(18,FontWeight.Medium).copy(fontFeatureSettings="tnum"),
                            modifier=Modifier.width(68.dp).alignByBaseline(),textAlign=TextAlign.End,maxLines=1)
                    }
                    Text(state.weatherLabel(city),style=uiText(12),
                        color=PicoTheme.colorScheme.labelSecondary,maxLines=1,overflow=TextOverflow.Ellipsis)
                  }
                }
            }
        }
        GlassAction({state.touchUi();manage=false;state.searching=true},
            Modifier.fillMaxWidth().height(40.dp).semantics{contentDescription="添加城市"},state.catalog!=null){
            Text("＋",style=uiText(20))
        }
        Text(if(state.mode==EarthMode.TEMPERATURE)"气温预报 NOAA GFS · 2 m" else "天气 Open-Meteo · CC BY 4.0",style=uiText(10),color=PicoTheme.colorScheme.labelSecondary,
            modifier=Modifier.fillMaxWidth().disableSpatialHoverEffect(true).clickable(
                interactionSource=remember{MutableInteractionSource()},indication=null) {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(if(state.mode==EarthMode.TEMPERATURE)"https://www.nco.ncep.noaa.gov/pmb/products/gfs/nomads/" else "https://open-meteo.com/en/licence")))
            },textAlign=TextAlign.Center)
    }
}

@Composable
private fun ToolbarIcon(label:String,plus:Boolean,onClick:()->Unit){
    GlassAction(onClick,Modifier.size(48.dp).semantics{contentDescription=label}){
        Canvas(Modifier.size(20.dp)){
            val c=Color.White
            if(plus){
                drawLine(c,Offset(size.width*.5f,size.height*.15f),Offset(size.width*.5f,size.height*.85f),2.dp.toPx())
                drawLine(c,Offset(size.width*.15f,size.height*.5f),Offset(size.width*.85f,size.height*.5f),2.dp.toPx())
            }else for(i in 0..2)drawCircle(c,1.5.dp.toPx(),Offset(size.width*(.2f+.3f*i),size.height*.5f))
        }
    }
}

@Composable
internal fun TimePanel(state:ClockState){
    val context=LocalContext.current
    val density=LocalDensity.current
    val platform=LocalViewConfiguration.current
    val pickup=remember(platform,density.density){object:ViewConfiguration by platform{
        override val touchSlop=with(density){2.dp.toPx()}
    }}
    val interaction=remember{MutableInteractionSource()}
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember{mutableStateOf(false)}
    DisposableEffect(Unit){onDispose{state.onTimeScrub?.invoke(false);state.uiManipulating=false}}
    val captionHoverGroup=remember{SpatialHoverEffectGroup.obtain()}
    CompositionLocalProvider(LocalViewConfiguration provides pickup){
        Column(Modifier.width(656.dp).height(162.dp),horizontalAlignment=Alignment.CenterHorizontally){
            // Fixed slot above the ruler: entering preview never moves the controls.
            Box(Modifier.fillMaxWidth().height(32.dp),contentAlignment=Alignment.Center){
                if(state.mode==EarthMode.TEMPERATURE)GlassAction({state.touchUi();state.temperature.now(state.now)},Modifier.height(28.dp)){Text("现在 · 最近预报时次",style=uiText(13))}
                else if(state.preview)GlassAction({state.live()},Modifier.height(28.dp)){Text("回到现在",style=uiText(13))}
                else if(state.clockNotice.isNotBlank())Text(state.clockNotice,style=uiText(12))
            }
            Row(Modifier.fillMaxWidth().height(64.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(16.dp)){
                ToolbarIcon("城市菜单",true){state.toggleCities()}
                // One visible ruler, one gesture owner. No transparent Slider or separate input overlay.
                Box(Modifier.weight(1f).height(48.dp).clip(CircleShape).backgroundMaterial()
                    .background(Color.White.copy(alpha=if(hovered||dragging).12f else 0f))
                    .hoverable(interaction).spatialHoverEffect(SpatialHoverStyle.Highlight).semantics{contentDescription=if(state.mode==EarthMode.TEMPERATURE)"逐小时气温预报时间轴，左右拖动选择已加载时刻" else "六小时时间滑块，蓝色表示现在，白色表示预览时间，左右拖动选择"}
                    .pointerInput(state.ready){
                        if(state.ready)detectDragGestures(context=context,targetedToEntity=null,
                            onDragStart={dragging=true;state.uiManipulating=true;state.beginTimeDrag()},
                            onDragEnd={dragging=false;state.uiManipulating=false;state.finishTimeDrag()},
                            onDragCancel={dragging=false;state.uiManipulating=false;state.finishTimeDrag()}){change,delta->
                                change.consume()
                                state.dragTimeBy(delta.x/density.density*(if(state.mode==EarthMode.TEMPERATURE)(state.temperature.manifest?.frames?.lastIndex?:0)*60f/528f else 360f/528f))
                            }
                    }){
                    if(state.mode==EarthMode.TEMPERATURE)TemperatureRuler(state) else TimeRulerDrawing(state,dragging)
                }
                ToolbarIcon("时间选项",false){state.toggleTimeTools()}
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(42.dp)){
            // System-like caption proportions; no WindowContainer is inserted into this Mixed Stage.
            val handleInteraction=remember{MutableInteractionSource()}
            val handleHover by handleInteraction.collectIsHoveredAsState()
            CaptionCloseButton(Modifier.align(Alignment.Center).offset(x=72.dp),captionHoverGroup,handleHover)
            Box(Modifier.align(Alignment.Center).size(112.dp,42.dp).hoverable(handleInteraction)
                .spatialHoverEffectGroup(captionHoverGroup)
                .semantics{contentDescription="拖动工具栏"}
                .pointerInput(state.ready){
                    if(state.ready){
                        var owner:Long?=null
                        var spatial=false
                        try{detectSpatialPointerEvent(context=context,targetedToEntity=null){pointers->
                            var consumed=false
                            pointers.forEach { p ->
                                if(owner==null && p.isDownEvent()){
                                    val raw=p.inputDevicePose.rawPosition
                                    spatial=raw.x!=0f||raw.y!=0f||raw.z!=0f
                                    val point=if(spatial)state.toolbarHandPoint?.invoke(raw.x,raw.y,raw.z)
                                        else Offset(p.x/density.density,p.y/density.density)
                                    if(point!=null){
                                        owner=p.pointerId.value;state.uiManipulating=true;state.touchUi()
                                        val gain=if(spatial)state.toolbarHandGain?.invoke(raw.x,raw.y,raw.z)?:1.0 else 1.0
                                        state.toolbarDrag.begin(point.x.toDouble(),point.y.toDouble(),p.uptimeMillis/1000.0,gain)
                                        android.util.Log.i("WorldClockToolbar","V43 begin fixedStage="+spatial+"; depthGain="+gain)
                                    }
                                }
                                if(owner==p.pointerId.value){
                                    consumed=true
                                    if(!p.pressed){
                                        state.toolbarDrag.end();owner=null;state.uiManipulating=false;state.touchUi()
                                        android.util.Log.i("WorldClockToolbar","V42 release x="+state.toolbarDrag.x+"; y="+state.toolbarDrag.y)
                                    }else{
                                        val raw=p.inputDevicePose.rawPosition
                                        val point=if(spatial)state.toolbarHandPoint?.invoke(raw.x,raw.y,raw.z)
                                            else Offset(p.x/density.density,p.y/density.density)
                                        point?.let{state.toolbarDrag.sample(it.x.toDouble(),it.y.toDouble(),p.uptimeMillis/1000.0)}
                                    }
                                }
                            }
                            consumed
                        }}finally{state.toolbarDrag.end();state.uiManipulating=false}
                    }
                },contentAlignment=Alignment.Center){
                Box(Modifier.size(88.dp,16.dp).clip(CircleShape).spatialHoverEffect(SpatialHoverStyle.Highlight)
                    .background(Color.White.copy(alpha=if(handleHover)1f else .72f)))
            }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}


/** Position reads happen in draw, not composition/layout. A short frame-clock follower
 * fills gaps between hand samples; release removes the cursor on the next UI frame. */
@Composable
private fun TimeRulerDrawing(state:ClockState,dragging:Boolean){
    val measurer=rememberTextMeasurer()
    val style=uiText(16)
    val labels=remember(measurer,style){(0..23).map{measurer.measure(it.toString()+"时",style=style)}}
    var displayedCursor by remember{mutableFloatStateOf(0f)}
    LaunchedEffect(dragging){
        if(dragging){
            displayedCursor=state.previewMarkerMinutes
            var previous=withFrameNanos{it}
            while(true)withFrameNanos { now ->
                val dt=((now-previous)/1e9).coerceIn(0.0,.1);previous=now
                displayedCursor+=(state.previewMarkerMinutes-displayedCursor)*(1.0-exp(-dt/.025)).toFloat()
            }
        }
    }
    Canvas(Modifier.fillMaxSize()){
        val local=state.rulerOrigin.atZone(ZoneId.systemDefault())
        val fraction=(local.minute*60+local.second+local.nano/1e9)/3600.0
        val step=size.width/6f
        val nowX=size.width/2+size.width*(state.nowMarkerMinutes/360f)
        val thumbX=size.width/2+size.width*(displayedCursor/360f)
        for(h in -4..4){
            val x=(size.width/2+step*(h-fraction)).toFloat()
            if(abs(x-nowX)>22.dp.toPx() && (!dragging||abs(x-thumbX)>18.dp.toPx())){
                val text=labels[Math.floorMod(local.hour+h,24)]
                val alpha=((size.width/2-abs(x-size.width/2))/18.dp.toPx()).coerceIn(0f,1f)
                if(alpha>0f)drawText(text,Color.White.copy(alpha=alpha),topLeft=Offset(x-text.size.width/2f,(size.height-text.size.height)/2f))
            }
        }
        for(h in -4..3){
            val x=(size.width/2+step*(h+.5-fraction)).toFloat()
            if(x>8.dp.toPx()&&x<size.width-8.dp.toPx())drawCircle(Color.White.copy(alpha=.65f),1.7.dp.toPx(),Offset(x,size.height/2))
        }
        if(dragging&&abs(thumbX-nowX)>9.dp.toPx())drawRoundRect(Color.White,
            topLeft=Offset(thumbX-2.5.dp.toPx(),10.dp.toPx()),size=androidx.compose.ui.geometry.Size(5.dp.toPx(),28.dp.toPx()),
            cornerRadius=androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx()))
        if(nowX in 0f..size.width)drawRoundRect(Color(0xFF5B9DFF),
            topLeft=Offset(nowX-6.dp.toPx(),6.dp.toPx()),size=androidx.compose.ui.geometry.Size(12.dp.toPx(),36.dp.toPx()),
            cornerRadius=androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
    }
}

@Composable
private fun CaptionCloseButton(modifier:Modifier,group:SpatialHoverEffectGroup,barHovered:Boolean){
    val scope=rememberCoroutineScope()
    var closing by remember{mutableStateOf(false)}
    val interaction=remember{MutableInteractionSource()}
    val hovered by interaction.collectIsHoveredAsState()
    val closePainter=painterResource(com.pico.spatial.ui.design.R.drawable.ic_sui_close)
    Box(modifier.size(32.dp,42.dp).spatialHoverEffectGroup(group)
        .hoverable(interaction).semantics{contentDescription="关闭软件"}
        .clickable(enabled=!closing,interactionSource=interaction,indication=null){
            if(!closing){closing=true;scope.launch{try{closeStage()}finally{closing=false}}}
        },contentAlignment=Alignment.Center){
        // Keep the icon and disc in the same drawing layer as the native reveal.
        Canvas(Modifier.size(16.dp).spatialHoverEffect{hover->
            alpha(if(hover.isActive||barHovered||hovered)1f else 0f)
        }){
            drawCircle(Color.White.copy(alpha=.18f))
            with(closePainter){draw(size=size,colorFilter=ColorFilter.tint(Color.White))}
        }
    }
}

@Composable
private fun DateWheel(label:String,value:Int,range:IntRange,state:ClockState,onValue:(Int)->Unit){
    val context=LocalContext.current;val density=LocalDensity.current
    val platform=LocalViewConfiguration.current
    val pickup=remember(platform,density.density){object:ViewConfiguration by platform{
        override val touchSlop=with(density){2.dp.toPx()}
    }}
    val current by rememberUpdatedState(value);val changeValue by rememberUpdatedState(onValue)
    val limits by rememberUpdatedState(range)
    var remainder by remember{mutableFloatStateOf(0f)}
    var dragValue by remember{mutableIntStateOf(value)}
    CompositionLocalProvider(LocalViewConfiguration provides pickup){
    Column(horizontalAlignment=Alignment.CenterHorizontally){
        Text(label,style=uiText(12),color=PicoTheme.colorScheme.labelSecondary)
        Box(Modifier.width(82.dp).height(108.dp).clip(RoundedCornerShape(12.dp))
            .backgroundMaterial(style=Material.None).spatialHoverEffect(SpatialHoverStyle.Highlight)
            .background(Color.White.copy(alpha=.035f))
            .semantics{contentDescription=label+"滚轮，当前"+value}
            .pointerInput(Unit){
                detectDragGestures(context=context,targetedToEntity=null,
                    onDragStart={dragValue=current;remainder=0f;state.uiManipulating=true;state.touchUi();android.util.Log.d("V36Time","wheel start "+label+"="+current)},
                    onDragEnd={remainder=0f;state.uiManipulating=false;state.touchUi()},
                    onDragCancel={remainder=0f;state.uiManipulating=false;state.touchUi()}){change,delta->
                        change.consume();remainder-=delta.y/density.density
                        val steps=(remainder/36f).toInt()
                        if(steps!=0){
                            remainder-=steps*36f
                            dragValue=(dragValue+steps).coerceIn(limits)
                            changeValue(dragValue);state.touchUi();android.util.Log.d("V36Time","wheel "+label+"="+dragValue)
                        }
                    }
            }){
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(36.dp).clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha=.12f)))
            for(i in -2..2){
                val n=value+i
                if(n in range)Box(Modifier.offset(y=(36f+i*36f-remainder).dp).fillMaxWidth().height(36.dp),contentAlignment=Alignment.Center){
                    Text(n.toString(),style=uiText(if(i==0)19 else 16,if(i==0)FontWeight.Medium else FontWeight.Normal),
                        color=Color.White.copy(alpha=if(i==0)1f else .4f),maxLines=1)
                }
            }
        }
    }
}

}

@Composable
internal fun TimeOptionsPanel(state:ClockState){
    if(state.mode==EarthMode.TEMPERATURE){TemperatureOptions(state);return}
    var chosen by remember(state.timeToolsOpen){mutableStateOf(state.instant.atZone(ZoneId.systemDefault()).toLocalDate())}
    DisposableEffect(Unit){onDispose{state.uiManipulating=false}}
    Glass(Modifier.width(300.dp)){
        Row(Modifier.fillMaxWidth().height(30.dp),verticalAlignment=Alignment.CenterVertically){
            Text("选择日期",style=uiText(16,FontWeight.Medium),modifier=Modifier.weight(1f))
            GlassAction({state.touchUi();state.timeToolsOpen=false},Modifier.size(30.dp),horizontalPadding=0.dp){Text("×",style=uiText(18))}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            DateWheel("年",chosen.year,1900..2100,state){chosen=DateSelection.withYear(chosen,it)}
            DateWheel("月",chosen.monthValue,1..12,state){chosen=DateSelection.withMonth(chosen,it)}
            DateWheel("日",chosen.dayOfMonth,1..chosen.lengthOfMonth(),state){chosen=chosen.withDayOfMonth(it)}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            GlassAction({state.touchUi();state.timeToolsOpen=false},Modifier.weight(1f).height(36.dp)){Text("取消",style=uiText(14))}
            GlassAction({state.chooseDate(chosen);state.timeToolsOpen=false},Modifier.weight(1f).height(36.dp)){Text("查看",style=uiText(14))}
        }
    }
}

@Composable
internal fun CityPinCard(city:ClockCity?,state:ClockState,labelOnRight:Boolean){
    if(city==null){Spacer(Modifier.size(1.dp));return}
    val lineColor=PicoTheme.colorScheme.labelPrimary.copy(alpha=.65f)
    Row(Modifier.width(CityLabelLayout.WIDTH_DP.dp).height(CityLabelLayout.HEIGHT_DP.dp),verticalAlignment=Alignment.Bottom){
        Box(Modifier.weight(1f).fillMaxHeight().drawBehind{
            val y=CityLabelLayout.LINE_Y_DP.dp.toPx()
            drawLine(lineColor,Offset(0f,y),Offset(size.width,y),strokeWidth=1.2.dp.toPx())
        }){
            Text(city.date(state.instant)+"  "+city.time(state.instant),style=uiText(14),
                color=PicoTheme.colorScheme.labelPrimary,modifier=Modifier.align(Alignment.TopCenter).padding(horizontal=12.dp),
                maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        GlassAction({state.focus(city)},Modifier.widthIn(min=88.dp,max=156.dp).height(CityLabelLayout.CHIP_HEIGHT_DP.dp),state.ready){
            Column(horizontalAlignment=Alignment.CenterHorizontally){
                Text(city.name,style=uiText(16),color=PicoTheme.colorScheme.labelPrimary,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(state.weatherLabel(city,compact=!state.preview),
                    style=uiText(11),color=PicoTheme.colorScheme.labelSecondary,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}
