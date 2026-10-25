package com.wupengfei.worldclock.content

import java.time.Instant
import kotlin.math.*
import java.util.Locale

internal enum class QuakePeriod(val days:Long,val label:String,val feed:String) {
    DAY(1,"24 小时","day"),WEEK(7,"7 天","week"),MONTH(30,"30 天","month")
}
internal enum class QuakeSort(val label:String) { MAGNITUDE("按震级"), TIME("按时间") }
internal data class QuakeEvent(val id:String,val aliases:Set<String>,val place:String,
    val occurredAt:Instant,val updatedAt:Instant?,val latitude:Double,val longitude:Double,
    val depthKm:Double?,val magnitude:Double?,val magnitudeType:String?,val url:String) {
    val coordinate get()=GeoCoordinate(latitude,longitude)
    val magnitudeText get()=magnitude?.let{String.format(Locale.ROOT,"%.1f",it)}?:"暂无"
}
internal data class QuakeCatalog(val period:QuakePeriod,val generatedAt:Instant,val fetchedAt:Instant,
    val events:List<QuakeEvent>,val partial:Boolean=false,val skipped:Int=0,val windowEnd:Instant=generatedAt) {
    val end get()=windowEnd
    val start get()=end.minusSeconds(period.days*86400)
    fun filtered(minimum:Double)=events.filter{(it.magnitude!=null&&it.magnitude>=maxOf(QuakeRules.CATALOG_MINIMUM,minimum))&&it.occurredAt>=start&&it.occurredAt<=end}
}
internal object QuakeRules {
    const val CATALOG_MINIMUM=4.0
    fun sortedEvents(events:List<QuakeEvent>,order:QuakeSort):List<QuakeEvent> {
        val magnitude:(QuakeEvent)->Double={it.magnitude?.takeIf(Double::isFinite)?:Double.NEGATIVE_INFINITY}
        return events.sortedWith(when(order){
            QuakeSort.MAGNITUDE->compareByDescending(magnitude).thenByDescending{it.occurredAt}.thenBy{it.id}
            QuakeSort.TIME->compareByDescending<QuakeEvent>{it.occurredAt}.thenByDescending(magnitude).thenBy{it.id}
        })
    }
    fun sourceUrl(event:QuakeEvent):String?=event.id.takeIf{it.matches(Regex("[A-Za-z0-9_-]{1,128}"))}
        ?.let{"https://earthquake.usgs.gov/earthquakes/eventpage/$it"}
    private val distancePrefix=Regex("^\\d+(?:\\.\\d+)?\\s*km\\s+[NSEW]{1,3}\\s+of\\s+",RegexOption.IGNORE_CASE)
    fun regionName(place:String)=place.replace(Regex("\\s+")," ").trim().replace(distancePrefix,"").ifBlank{"地点暂无"}
    // Complete snapshots replace the window, including removals and ID reassignment.
    fun deduplicate(events:List<QuakeEvent>):List<QuakeEvent> {
        val owner=mutableMapOf<String,Int>();val parents=IntArray(events.size){it}
        fun root(x:Int):Int {var i=x;while(parents[i]!=i){parents[i]=parents[parents[i]];i=parents[i]};return i}
        events.forEachIndexed{i,e->(e.aliases+e.id).forEach{id->owner[id]?.let{parents[root(i)]=root(it)};owner[id]=i}}
        return events.indices.groupBy{root(it)}.values.map{group->group.map{events[it]}.maxWith(compareBy<QuakeEvent>{it.updatedAt?:it.occurredAt}.thenBy{it.id})}.sortedBy{it.occurredAt}
    }
    fun fraction(time:Instant,start:Instant,end:Instant):Double {
        val span=end.toEpochMilli()-start.toEpochMilli()
        return if(span<=0)0.0 else ((time.toEpochMilli()-start.toEpochMilli()).toDouble()/span).coerceIn(0.0,1.0)
    }
    fun atFraction(fraction:Double,start:Instant,end:Instant):Instant =
        start.plusMillis(((end.toEpochMilli()-start.toEpochMilli())*fraction.coerceIn(0.0,1.0)).toLong())
    fun visible(events:List<QuakeEvent>,time:Instant?)=if(time==null)events else events.filter{it.occurredAt<=time}
    fun magnitudeBand(magnitude:Double?)=when { magnitude==null||!magnitude.isFinite()->0; magnitude>=7->2; magnitude>=5->1; else->0 }
    fun pointDiameter(magnitude:Double?)=(5.0+3.0*((magnitude?:4.0)-4.0)).coerceIn(4.0,20.0)
    fun ringPhase(seconds:Double,ring:Int,seed:Double):Double =
        ((seconds+seed-ring*QuakeStyle.RING_STAGGER)%QuakeStyle.RING_SECONDS+QuakeStyle.RING_SECONDS)%QuakeStyle.RING_SECONDS/QuakeStyle.RING_SECONDS
    fun ringOpacity(phase:Double):Double {
        val rise=(phase/.12).coerceIn(0.0,1.0);val fall=(1-phase).coerceIn(0.0,1.0)
        return rise*rise*(3-2*rise)*fall*fall
    }
}
internal object QuakeStyle {
    const val RING_SECONDS=2.4
    const val RING_STAGGER=.8
    // V89 visual emphasis only, never a physical propagation/impact radius.
    const val WAVE_RANGE_SCALE=1.6
    const val WAVE_PATCH_DEGREES=6.5*WAVE_RANGE_SCALE
    const val WAVE_START_DEGREES=.3
    const val WAVE_NORMAL_DEGREES=3.5*WAVE_RANGE_SCALE
    const val WAVE_SELECTED_DEGREES=6.0*WAVE_RANGE_SCALE
    const val WAVE_LINE_CORE_DEGREES=.052
    const val WAVE_LINE_EDGE_DEGREES=.1495
    const val WAVE_NORMAL_ALPHA=.22f
    const val WAVE_SELECTED_ALPHA=.72f
    const val RADIUS_SCALE=1.012 // above existing 1.00765 cloud shell
    const val REPLAY_SECONDS=60.0
    const val FADE_SECONDS=.6
    const val MAX_RIPPLE_GROUPS=6 // Existing shader ABI only; no event animation cap in V83.
    const val MAX_CLUSTER_LABELS=8
    const val CLUSTER_DP=24.0
    const val CLUSTER_RELEASE_DP=32.0
    const val PANEL_DP=288f
    const val PANEL_BODY_DP=360f
    const val PICK_SCALE=32f
    const val PANEL_ICON_X_DP=-304f // 784dp Dock: -392 + 64 + 24.
    const val PANEL_BOTTOM_Y_DP=8f // Icon top -8dp; keep a 16dp gap above it.
}
internal data class QuakeProjection(val event:QuakeEvent,val x:Double,val y:Double)
internal data class QuakeCluster(val representative:QuakeEvent,val events:List<QuakeEvent>)
internal object QuakeClustering {
    fun group(points:List<QuakeProjection>,previous:Map<String,String> = emptyMap()):List<QuakeCluster> {
        val groups=mutableListOf<Pair<QuakeProjection,MutableList<QuakeEvent>>>()
        for(p in points.sortedWith(compareByDescending<QuakeProjection>{it.event.magnitude?:0.0}.thenBy{it.event.id})) {
            val best=groups.firstOrNull{(r,_)->
                val radius=if(previous[p.event.id]==r.event.id)QuakeStyle.CLUSTER_RELEASE_DP else QuakeStyle.CLUSTER_DP
                hypot(p.x-r.x,p.y-r.y)<radius
            }
            if(best==null)groups.add(p to mutableListOf(p.event))else best.second.add(p.event)
        }
        return groups.map{QuakeCluster(it.first.event,it.second.toList())}
    }
}
