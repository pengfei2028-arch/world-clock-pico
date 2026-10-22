package com.wupengfei.worldclock.content
import androidx.compose.runtime.*
import java.time.Instant
import java.util.Locale

@Stable
internal class TemperatureState {
    var manifest by mutableStateOf<TemperatureManifest?>(null)
    var index by mutableIntStateOf(0)
    var strength by mutableFloatStateOf(TemperatureDisplay.DEFAULT_OPACITY)
    var displayedManifest by mutableStateOf<TemperatureManifest?>(null)
    var displayed by mutableStateOf<TemperatureFrame?>(null)
    var grid by mutableStateOf<TemperatureGrid?>(null)
    var loading by mutableStateOf(false)
    var status by mutableStateOf("正在准备气温数据")
    var refreshRequest by mutableIntStateOf(0)
    var autoNow by mutableStateOf(true)
    var rulerCenter by mutableStateOf<Instant?>(null)
    var scrubTime by mutableStateOf<Instant?>(null)
    // Updated by the SDK scene frame; shared with the legend drawing layer.
    var reveal by mutableFloatStateOf(0f)
    val kindLabel get()=if(displayed?.lead==0)"模型分析" else "气温预报"
    val requested get()=manifest?.frames?.getOrNull(index)
    fun select(i:Int){index=i.coerceIn(0,(manifest?.frames?.lastIndex?:0));autoNow=false}
    fun now(t:Instant){manifest?.let{index=it.nearest(t)};autoNow=true;rulerCenter=null;scrubTime=null}
    fun beginDrag(){
        val start=requested?.validTime?:return
        if(rulerCenter==null)rulerCenter=start
        scrubTime=start;autoNow=false
    }
    fun dragBy(minutes:Float){
        if(!minutes.isFinite())return
        val m=manifest?:return
        val start=scrubTime?:requested?.validTime?:return
        val target=start.plusMillis((minutes*60000).toLong()).coerceIn(m.frames.first().validTime,m.frames.last().validTime)
        scrubTime=target;select(m.nearest(target))
        val center=rulerCenter?:start
        val cursor=java.time.Duration.between(center,target).toMillis()/60000.0
        val excess=cursor-cursor.coerceIn(-170.0,170.0)
        if(excess!=0.0)rulerCenter=center.plusMillis((excess*60000).toLong())
    }
    fun label(city:ClockCity):String=grid?.sample(city.latitude,city.longitude)?.let{String.format(Locale.ROOT,"%.1f °C · %s",it,if(displayed?.lead==0)"分析" else "预报")}?:"气温暂无数据"
}
