package com.wupengfei.worldclock.content

import androidx.compose.runtime.*

internal object DistanceStyle {
    const val RADIUS_KM=6371.0088
    const val SURFACE_SCALE=1.025
    const val ARC_LIFT=.018
    const val LINE_WIDTH=.010
    const val ENDPOINT_RADIUS=.026
    const val FADE_SECONDS=.22
    const val PICK_DEGREES=4.0
    const val PANEL_WIDTH=172
    const val PANEL_HEIGHT=52
    const val CITY_WIDTH=96
    const val CITY_HEIGHT=40
}

@Stable
internal class DistanceState {
    var enabled by mutableStateOf(false);private set
    var firstId by mutableStateOf<String?>(null);private set
    var secondId by mutableStateOf<String?>(null);private set
    var readRequest=0;private set
    var panelAlpha by mutableFloatStateOf(0f)
    // Scene-thread layout data, not Compose state. Published before ClockScene.update.
    var readoutAnchor:GlobeVector3?=null
    var failure by mutableStateOf("")
    fun toggle(cities:List<ClockCity>){
        if(enabled){disable();return}
        enabled=true;firstId=null;secondId=null
        if(cities.size==2){firstId=cities[0].id;secondId=cities[1].id}
        readRequest++
    }
    fun disable(){enabled=false;firstId=null;secondId=null}
    fun pick(city:ClockCity){
        if(!enabled)return
        if(secondId!=null||firstId==null){firstId=city.id;secondId=null}
        else if(firstId!=city.id)secondId=city.id
        readRequest++
    }
    fun reconcile(cities:List<ClockCity>){
        val ids=cities.map{it.id}.toSet()
        if(firstId !in ids){firstId=null;secondId=null}
        else if(secondId !in ids)secondId=null
    }
    fun pair(cities:List<ClockCity>):Pair<ClockCity,ClockCity>? {
        val a=cities.firstOrNull{it.id==firstId}?:return null
        val b=cities.firstOrNull{it.id==secondId}?:return null
        return a to b
    }
    fun hint(cities:List<ClockCity>)=when {
        failure.isNotBlank()->failure
        cities.size<2->"请先从时钟菜单添加两座城市"
        firstId==null->"依次点选两座城市"
        secondId==null->"请选择第二座城市"
        else->"地表最短距离 · 点选城市可重新测量"
    }
}
