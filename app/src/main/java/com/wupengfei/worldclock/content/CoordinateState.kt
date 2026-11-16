package com.wupengfei.worldclock.content

import androidx.compose.runtime.*

internal data class CoordinateSelection(val coordinate:GeoCoordinate,val cityId:String?=null,val title:String="选中位置")

@Stable internal class CoordinateState {
    var enabled by mutableStateOf(false);private set
    var selection by mutableStateOf<CoordinateSelection?>(null);private set
    var panelAlpha by mutableFloatStateOf(0f)
    var reveal by mutableFloatStateOf(0f)
    val rulerAlpha=mutableStateListOf<Float>().apply{repeat(CoordinateRuler.slots.size){add(0f)}}
    var surface:CoordinateSurface?=null
    var failure by mutableStateOf("")
    fun toggle(city:ClockCity?=null){enabled=!enabled;if(enabled&&city!=null)selectCity(city)}
    fun finishHidden(){if(!enabled){selection=null;panelAlpha=0f;rulerAlpha.indices.forEach{rulerAlpha[it]=0f}}}
    fun select(coordinate:GeoCoordinate){if(enabled)selection=CoordinateSelection(coordinate)}
    fun selectCity(city:ClockCity){if(enabled)selection=CoordinateSelection(GeoCoordinate(city.latitude,city.longitude),city.id,city.name)}
}

/** Project visual values in original radius=2 mesh units; no Full/Shared asset changes. */
internal object CoordinateStyle {
    const val SURFACE_SCALE=1.025
    const val GRID_WIDTH=.0085
    const val EQUATOR_WIDTH=.016
    const val PRIME_WIDTH=.014
    const val EQUATOR_RGB=0x61D8F0
    const val PRIME_RGB=0xFFD087
    const val GRID_ALPHA=.34f
    const val MAJOR_ALPHA=.74f
    const val ARC_ALPHA=.52f
    const val MARKER_RADIUS=.025
    const val MARKER_WIDTH=.008
    const val FADE_SECONDS=.25
    const val READ_DWELL_SECONDS=8.0
    const val LABEL_SCALE=56f
    const val LABEL_TEXT_SP=22
    const val LABEL_ROLL_FOLLOW_DEGREES=55.0
    const val LABEL_ROLL_LEVEL_DEGREES=85.0
    const val LABEL_ROLL_MAX_DEGREES=60.0
    const val LABEL_ROLL_RESPONSE_SECONDS=.10
    const val LABEL_ROLL_MAX_SPEED=140.0
    const val RULER_RADIUS=1.09
    const val RULER_LABEL_RADIUS=1.20
    const val RULER_WIDTH=.0085
    const val LATITUDE_RULER_LONGITUDE=0.0
    const val LONGITUDE_RULER_LATITUDE=0.0
    const val LEADER_RADIUS=.045f
    const val UI_LINE_DP=2f
    const val POLAR_BLEND_START=70.0
    const val POLAR_BLEND_END=75.0
    const val SEAM_BLEND_DEGREES=6.0
}

/** Tap ownership is the same as the rotation gesture; invalid or dragged samples never select. */
internal class CoordinateTap {
    private var owner:Long?=null
    private var candidate:GeoCoordinate?=null
    private var start=0.0
    private var rejected=false
    private var startPosition:GlobeVector3?=null
    fun begin(id:Long,coordinate:GeoCoordinate?,now:Double){owner=id;candidate=coordinate;start=now;rejected=false;startPosition=null}
    fun position(value:GlobeVector3){
        if(owner==null||!value.x.isFinite()||!value.y.isFinite()||!value.z.isFinite())return
        val first=startPosition
        if(first==null)startPosition=value else if((value-first).let{it.dot(it)}>.018*.018)rejected=true
    }
    fun observe(moved:Boolean,trackingRejected:Boolean){if(moved||trackingRejected)rejected=true}
    fun end(id:Long,now:Double):GeoCoordinate? {
        val value=if(id==owner&&!rejected&&now-start in .04..1.2)candidate else null
        cancel();return value
    }
    fun cancel(){owner=null;candidate=null;rejected=false;startPosition=null}
}
