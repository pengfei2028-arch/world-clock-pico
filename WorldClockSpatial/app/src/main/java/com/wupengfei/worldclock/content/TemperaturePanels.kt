package com.wupengfei.worldclock.content
import androidx.compose.runtime.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.gesture.detectDragGestures
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.graphics.SpatialHoverStyle
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val tempTime=DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
@Composable internal fun ModulePanel(state:ClockState){
    Row(Modifier.width(230.dp).height(38.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
        for((mode,title) in listOf(EarthMode.WORLD_TIME to "世界时间",EarthMode.TEMPERATURE to "全球气温")){
            GlassAction({state.switchMode(mode)},Modifier.width(112.dp).fillMaxHeight().background(if(state.mode==mode)Color.White.copy(alpha=.16f)else Color.Transparent,CircleShape)){
                Text(title,style=uiText(15),color=if(state.mode==mode)Color.White else Color.White.copy(alpha=.65f))
            }
        }
    }
}
@Composable internal fun TemperatureInfo(state:ClockState){
    val t=state.temperature;val m=t.displayedManifest?:t.manifest
    Column(Modifier.width(656.dp).height(88.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){
        Text("2 m "+t.kindLabel+" · NOAA GFS · "+(t.displayed?.validTime?.let{tempTime.format(it)+" "+ZoneId.systemDefault().id}?:"正在载入")+(if(t.loading)" · 切换中" else ""),style=uiText(12))
        Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
            TemperatureLegend(Modifier.weight(1f));TemperatureStrength(state)
        }
        Text((if(m?.stale(state.now)==true)"数据已过期 · " else "")+t.status,style=uiText(11),color=Color.White.copy(alpha=.65f))
    }
}
@Composable private fun TemperatureLegend(modifier:Modifier){
    val tm=rememberTextMeasurer();val style=uiText(10)
    val text=remember(style){listOf("−30","−20","−10","0","10","20","30","40").map{tm.measure(it,style=style)}}
    Canvas(modifier.height(48.dp)){
        val w=(size.width-42.dp.toPx())/9
        // First/last swatches explicitly extend beyond the fixed scale.
        val low=tm.measure("<",style=style);val high=tm.measure("> °C",style=style)
        drawText(low,Color.White,topLeft=Offset(0f,27.dp.toPx()))
        drawText(high,Color.White,topLeft=Offset(9*w-high.size.width,27.dp.toPx()))
        TemperaturePalette.colors.forEachIndexed{i,c->drawRect(Color(c),Offset(i*w,9.dp.toPx()),Size(w,12.dp.toPx()))}
        text.forEachIndexed{i,l->val x=(i+1)*w;drawText(l,Color.White,topLeft=Offset((x-l.size.width/2).coerceIn(0f,size.width-l.size.width),27.dp.toPx()))}
        drawRect(Color(0xFF747B83),Offset(size.width-26.dp.toPx(),9.dp.toPx()),Size(20.dp.toPx(),12.dp.toPx()))
        val missing=tm.measure("缺失",style=style);drawText(missing,Color.White,topLeft=Offset(size.width-missing.size.width,21.dp.toPx()))
    }
}
@Composable private fun TemperatureStrength(state:ClockState){
    val context=LocalContext.current;val density=LocalDensity.current
    Column(Modifier.width(130.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Text("叠加强度 "+(state.temperature.strength*100).toInt()+"%",style=uiText(11))
        Canvas(Modifier.fillMaxWidth().height(28.dp).clip(CircleShape).backgroundMaterial().spatialHoverEffect(SpatialHoverStyle.Highlight)
            .pointerInput(Unit){detectDragGestures(context=context,targetedToEntity=null,
                onDragStart={state.uiManipulating=true;state.touchUi()},onDragEnd={state.uiManipulating=false;state.touchUi()},onDragCancel={state.uiManipulating=false}){change,d->
                    change.consume();state.temperature.strength=(state.temperature.strength+d.x/density.density/130f).coerceIn(0f,1f)
                }}){
            val left=10.dp.toPx();val right=size.width-left;val y=size.height/2
            drawLine(Color.White.copy(alpha=.3f),Offset(left,y),Offset(right,y),3.dp.toPx())
            val x=left+(right-left)*state.temperature.strength
            drawLine(Color.White.copy(alpha=.8f),Offset(left,y),Offset(x,y),3.dp.toPx());drawCircle(Color.White,5.dp.toPx(),Offset(x,y))
        }
    }
}
@Composable internal fun TemperatureRuler(state:ClockState){
    val m=state.temperature.manifest
    val tm=rememberTextMeasurer();val style=uiText(13)
    val labels=remember(m,style){m?.frames?.map{tm.measure(DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(it.validTime),style=style)}.orEmpty()}
    Canvas(Modifier.fillMaxSize()){
        if(labels.isEmpty()){drawText(tm.measure("暂无可用预报",style=style),Color.White,topLeft=Offset(14.dp.toPx(),14.dp.toPx()));return@Canvas}
        val pad=26.dp.toPx();val span=size.width-2*pad;val last=labels.lastIndex.coerceAtLeast(1)
        labels.forEachIndexed{i,label->val x=pad+span*i/last
            if((i%6==0&&labels.lastIndex-i>=3)||i==labels.lastIndex)drawText(label,Color.White,topLeft=Offset(x-label.size.width/2,(size.height-label.size.height)/2))
            else drawCircle(Color.White.copy(alpha=.5f),1.5.dp.toPx(),Offset(x,size.height/2))
        }
        val selected=pad+span*state.temperature.index/last
        drawRoundRect(Color.White,Offset(selected-2.dp.toPx(),5.dp.toPx()),Size(4.dp.toPx(),size.height-10.dp.toPx()),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        val nowIndex=m?.nearest(state.now)?:0
        if(m!=null&&state.now>=m.frames.first().validTime&&state.now<=m.frames.last().validTime){val nx=pad+span*nowIndex/last;drawCircle(Color(0xFF5B9DFF),3.dp.toPx(),Offset(nx,size.height-5.dp.toPx()))}
    }
}
@Composable internal fun TemperatureOptions(state:ClockState){
    val t=state.temperature;val m=t.displayedManifest?:t.manifest
    Glass(Modifier.width(310.dp)){
        Text(t.kindLabel+" · NOAA GFS",style=uiText(16))
        Text("全球 0.25° · 离地 2 m 空气温度",style=uiText(12))
        Text("起报："+(m?.runTime?.toString()?:"—"),style=uiText(12))
        Text("有效："+(t.displayed?.validTime?.toString()?:"—"),style=uiText(12))
        Text("服务端获取："+(m?.fetchedAt?.toString()?:"—"),style=uiText(12))
        Text("设备取得："+(m?.receivedAt?.toString()?:"—"),style=uiText(12))
        Text("逐小时模型预报，不是逐小时全球实测。海洋同样显示空气温度。",style=uiText(12))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            GlassAction({state.touchUi();t.select(t.index-1)},Modifier.weight(1f).height(34.dp),t.index>0){Text("前一小时",style=uiText(12))}
            GlassAction({state.touchUi();t.select(t.index+1)},Modifier.weight(1f).height(34.dp),t.index<(t.manifest?.frames?.lastIndex?:0)){Text("后一小时",style=uiText(12))}
        }
        GlassAction({t.refreshRequest++;state.touchUi()},Modifier.fillMaxWidth().height(34.dp)){Text("检查更新",style=uiText(13))}
    }
}
