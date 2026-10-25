package com.wupengfei.worldclock.content

import kotlin.math.cos

/** Chooses front-facing representatives plus the pinned selection; animation never advances event time. */
internal class QuakeWaveSchedule {
    private var assigned=emptyList<String>()
    private val served=mutableMapOf<String,Double>()
    private var nextRound=Double.NEGATIVE_INFINITY
    private var selected:String?=null
    private var recent=emptySet<String>()
    private var major=emptySet<String>()
    private var available=emptySet<String>()
    companion object {
        // Start with a visible expanding ring; keep this seed fixed for the whole selection.
        fun selectionSeed(seconds:Double):Double {
            val seed=.2-seconds/QuakeStyle.RING_SECONDS
            return seed-kotlin.math.floor(seed)
        }
    }
    fun reset(){assigned=emptyList();served.clear();nextRound=Double.NEGATIVE_INFINITY;selected=null;recent=emptySet();major=emptySet();available=emptySet()}
    fun choose(now:Double,candidates:List<QuakeEvent>,selectedId:String?,recentIds:Set<String>):List<QuakeEvent> {
        val byId=candidates.associateBy{it.id}
        val selection=selectedId?.takeIf{it in byId}
        val fresh=recentIds.intersect(byId.keys)
        val strong=candidates.filter{(it.magnitude?:0.0)>=7}.map{it.id}.toSet()
        val due=now>=nextRound
        val changed=selection!=selected || (fresh-recent).isNotEmpty() || (strong-major).isNotEmpty()
        val lost=assigned.any{it !in byId}
        if(!due&&!changed&&!lost&&(assigned.size==QuakeStyle.MAX_RIPPLE_GROUPS||available==byId.keys))
            return assigned.mapNotNull{byId[it]}
        val chosen=mutableListOf<QuakeEvent>()
        val directions=candidates.associate{it.id to GlobeGeography.direction(it.latitude,it.longitude).normalized()}
        fun add(e:QuakeEvent):Boolean {
            if(chosen.size>=QuakeStyle.MAX_RIPPLE_GROUPS||chosen.any{it.id==e.id})return false
            val n=directions.getValue(e.id)
            if(chosen.any{other->
                val angle=if(other.id==selection)10.0 else QuakeStyle.WAVE_SEPARATION_DEGREES
                n.dot(directions.getValue(other.id))>cos(Math.toRadians(angle))
            })return false
            chosen.add(e);return true
        }
        val fairness=compareBy<QuakeEvent>{served[it.id]?:Double.NEGATIVE_INFINITY}
            .thenByDescending{it.magnitude?:0.0}.thenByDescending{it.occurredAt}.thenBy{it.id}
        selection?.let{add(byId.getValue(it))}
        // Reserve up to two places for M7+, rotated fairly if there are more.
        var majors=0
        for(e in candidates.filter{it.id in strong&&it.id!=selection}.sortedWith(fairness))
            if(majors<2&&add(e))majors++
        var arrivals=0
        for(e in candidates.filter{it.id in fresh}.sortedByDescending{it.occurredAt})
            if(arrivals<2&&add(e))arrivals++
        if(!due)assigned.mapNotNull{byId[it]}.forEach{add(it)}
        candidates.sortedWith(fairness).forEach{add(it)}
        chosen.forEach{if(due||it.id !in assigned)served[it.id]=now}
        val wanted=chosen.map{it.id}.toMutableSet()
        // A selected event owns slot zero immediately, regardless of its former slot.
        val slots=MutableList<String?>(QuakeStyle.MAX_RIPPLE_GROUPS){null}
        if(selection!=null){slots[0]=selection;wanted.remove(selection)}
        for(i in slots.indices)if(slots[i]==null)slots[i]=assigned.getOrNull(i)?.takeIf{wanted.remove(it)}
        for(id in wanted){val index=slots.indexOfFirst{it==null};if(index>=0)slots[index]=id}
        for(i in slots.indices)if(slots[i]==null){
            val tail=slots.indexOfLast{it!=null};if(tail>i){slots[i]=slots[tail];slots[tail]=null}
        }
        assigned=slots.filterNotNull();selected=selection;recent=fresh;major=strong;available=byId.keys
        if(due)nextRound=now+QuakeStyle.WAVE_ROUND_SECONDS
        return assigned.mapNotNull{byId[it]}
    }
}
