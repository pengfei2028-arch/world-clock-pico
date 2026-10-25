package com.wupengfei.worldclock.content

import kotlin.math.cos

/** Global, geographic grouping: camera position must never remove catalogue coverage.
 * The Earth and back-face/depth tests provide occlusion; turning it needs no point mesh rebuild. */
internal object QuakeCoverage {
    fun group(events:List<QuakeEvent>,previous:Map<String,String> = emptyMap(),selected:String?=null):List<QuakeCluster> {
        val directions=events.associate{it.id to GlobeGeography.direction(it.latitude,it.longitude).normalized()}
        val buckets=mutableListOf<MutableList<QuakeEvent>>()
        val enter=cos(Math.toRadians(3.0));val release=cos(Math.toRadians(4.0))
        for(e in events.filter{it.id!=selected}.sortedWith(compareByDescending<QuakeEvent>{it.magnitude?:0.0}.thenBy{it.id})){
            val n=directions.getValue(e.id)
            val bucket=buckets.firstOrNull{members->
                val rep=members.first();val threshold=if(previous[e.id]==rep.id)release else enter
                n.dot(directions.getValue(rep.id))>threshold
            }
            if(bucket==null)buckets.add(mutableListOf(e))else bucket.add(e)
        }
        var labels=0
        val grouped=buckets.flatMap{members->
            if(members.size>1&&labels++>=QuakeStyle.MAX_CLUSTER_LABELS)members.map{QuakeCluster(it,listOf(it))}
            else listOf(QuakeCluster(members.first(),members.toList()))
        }
        return grouped+events.filter{it.id==selected}.map{QuakeCluster(it,listOf(it))}
    }
}
