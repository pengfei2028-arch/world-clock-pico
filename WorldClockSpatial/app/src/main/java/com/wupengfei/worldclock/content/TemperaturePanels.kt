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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
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

/** Project tint/icon approved in Figma; the surface and highlight stay PICO native. */
@Composable internal fun TemperatureToggle(state:ClockState){
    val active=state.mode==EarthMode.TEMPERATURE
    GlassAction({state.switchMode(if(active)EarthMode.WORLD_TIME else EarthMode.TEMPERATURE)},
        Modifier.size(48.dp).semantics{contentDescription="全球气温";selected=active},state.ready,
        containerTint=if(active)Color(0xFF745BFF).copy(alpha=.82f) else Color.Transparent){
        androidx.compose.foundation.Image(painterResource(com.wupengfei.worldclock.R.drawable.ic_temperature),
            contentDescription=null,modifier=Modifier.size(20.dp))
    }
}
/** Shares the time ruler's 528 dp bounds and uses proportional Celsius intervals. */
@Composable internal fun TemperatureLegend(modifier:Modifier=Modifier){
    val tm=rememberTextMeasurer();val style=uiText(10)
    val labels=remember(tm,style){TemperaturePalette.displayTicks.map { value ->
        tm.measure(when(value){-30->"≤ −30";40->"≥ 40 °C";else->value.toString().replace("-","−")},style=style)
    }}
    Canvas(modifier.height(48.dp)){
        val low=-35f;val high=45f
        fun x(c:Float)=(c-low)/(high-low)*size.width
        val edges=listOf(low)+TemperaturePalette.breaks.map{it.toFloat()}+listOf(high)
        val top=8.dp.toPx();val height=10.dp.toPx()
        val capsule=Path().apply{addRoundRect(RoundRect(0f,top,size.width,top+height,CornerRadius(height/2)))}
        clipPath(capsule){
            TemperaturePalette.colors.forEachIndexed{i,c->
                val left=x(edges[i]);val right=x(edges[i+1])
                drawRect(Color(c),Offset(left,top),Size(right-left,height))
            }
        }
        labels.forEachIndexed{i,label->
            val center=x(TemperaturePalette.displayTicks[i].toFloat())
            drawText(label,Color.White,topLeft=Offset((center-label.size.width/2f).coerceIn(0f,size.width-label.size.width),25.dp.toPx()))
        }
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
@Composable internal fun TemperatureOptions(state:ClockState){
    val navigator=com.pico.spatial.ui.platform.containers.LocalSpatialNavigator.current
    val t=state.temperature;val m=t.displayedManifest?:t.manifest
    Glass(Modifier.width(310.dp)){
        GlassAction({WorldClockSession.collapse(navigator)},Modifier.fillMaxWidth().height(40.dp),state.ready&&!WorldClockSession.busy){Text("收起到桌面",style=uiText(15))}
        Text(t.kindLabel+" · NOAA GFS",style=uiText(16))
        Text("全球 0.25° · 离地 2 m 空气温度",style=uiText(12))
        TemperatureStrength(state)
        Text("起报："+(m?.runTime?.toString()?:"—"),style=uiText(12))
        Text("有效："+(t.displayed?.validTime?.toString()?:"—"),style=uiText(12))
        Text("服务端获取："+(m?.fetchedAt?.toString()?:"—"),style=uiText(12))
        Text("设备取得："+(m?.receivedAt?.toString()?:"—"),style=uiText(12))
        Text((if(m?.stale(state.now)==true)"数据已过期 · " else "")+t.status+(if(t.loading)" · 切换中" else ""),style=uiText(12),color=Color.White.copy(alpha=.7f))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            GlassAction({state.touchUi();t.select(t.index-1)},Modifier.weight(1f).height(34.dp),t.index>0){Text("前一小时",style=uiText(12))}
            GlassAction({state.touchUi();t.select(t.index+1)},Modifier.weight(1f).height(34.dp),t.index<(t.manifest?.frames?.lastIndex?:0)){Text("后一小时",style=uiText(12))}
        }
        GlassAction({t.refreshRequest++;state.touchUi()},Modifier.fillMaxWidth().height(34.dp)){Text("检查更新",style=uiText(13))}
    }
}
