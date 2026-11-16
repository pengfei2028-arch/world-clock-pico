package com.wupengfei.worldclock.content

import kotlin.math.*

internal data class CityLabelCandidate(val id:String,val layout:CityLabelLayout)

/** At most eight cards: keep every front-side city, move cards, never their pins.
 * All cards share the stage XY plane, so their complete UI bounds can be used
 * directly. No head pose or selected-city priority enters the ordering.
 */
internal class CityLabelAvoidance {
    private data class Previous(val y:Double,val offset:Double)
    private val previous=mutableMapOf<String,Previous>()
    private val joined=mutableSetOf<Pair<String,String>>()

    fun update(candidates:List<CityLabelCandidate>,metresPerDp:Double,
        lower:Double,upper:Double,seconds:Double):Map<String,CityLabelLayout> {
        val ids=candidates.map{it.id}.toSet()
        previous.keys.retainAll(ids)
        joined.removeAll{it.first !in ids || it.second !in ids}
        if(candidates.isEmpty())return emptyMap()
        require(ids.size==candidates.size)
        val gap=12.0*metresPerDp // Project spacing; includes text above the capsule.
        val parent=IntArray(candidates.size){it}
        fun root(i:Int):Int { var r=i;while(parent[r]!=r)r=parent[r];return r }
        for(i in candidates.indices)for(j in i+1 until candidates.size){
            val a=candidates[i];val b=candidates[j]
            val key=if(a.id<b.id)a.id to b.id else b.id to a.id
            // Reserve space before edges touch; a wider release band prevents chatter.
            val padding=(if(key in joined)48.0 else 16.0)*metresPerDp
            val near=abs(a.layout.center.x-b.layout.center.x)<
                a.layout.halfWidth+b.layout.halfWidth+padding
            if(near){joined.add(key);parent[root(j)]=root(i)}else joined.remove(key)
        }
        val groups=candidates.indices.groupBy{root(it)}.values
        val response=1.0-exp(-seconds.coerceIn(0.0,.1)/.28)
        val result=linkedMapOf<String,CityLabelLayout>()
        for(indices in groups){
            val ordered=indices.map{candidates[it]}.sortedWith(
                compareBy<CityLabelCandidate>{previous[it.id]?.y?:it.layout.center.y}.thenBy{it.id})
            val targets=pack(ordered,ordered.map{it.layout.center.y},List(ordered.size){1.0},gap,lower,upper)
            val entering=ordered.any{it.id !in previous}
            val proposed=ordered.mapIndexed{i,c->
                val old=previous[c.id]
                if(old==null)targets[i] else c.layout.center.y+old.offset+
                    (targets[i]-c.layout.center.y-old.offset)*(if(entering)0.0 else response)
            }
            // Reproject the smoothed result: transitions must not temporarily overlap.
            // A newly appearing card yields first, so it does not kick an existing one.
            val weights=ordered.map{if(it.id in previous)1.0 else .02}
            val placed=pack(ordered,proposed,weights,gap,lower,upper)
            ordered.forEachIndexed{i,c->
                result[c.id]=c.layout.atY(placed[i])
                previous[c.id]=Previous(placed[i],placed[i]-c.layout.center.y)
            }
        }
        return result
    }

    /** Weighted isotonic packing minimizes displacement with exact row separation. */
    private fun pack(items:List<CityLabelCandidate>,desired:List<Double>,weights:List<Double>,
        gap:Double,lower:Double,upper:Double):List<Double> {
        val n=items.size
        val spacing=DoubleArray(n)
        for(i in 1 until n)spacing[i]=spacing[i-1]+
            items[i-1].layout.halfHeight+items[i].layout.halfHeight+gap
        val lo=lower+items.first().layout.halfHeight
        val hi=upper-items.last().layout.halfHeight-spacing.last()
        // The current eight-card maximum fits comfortably in the globe-height band.
        require(lo<=hi){"City cards exceed the reserved vertical band"}
        data class Block(val first:Int,var last:Int,var sum:Double,var weight:Double)
        val blocks=mutableListOf<Block>()
        for(i in 0 until n){
            blocks.add(Block(i,i,(desired[i]-spacing[i])*weights[i],weights[i]))
            while(blocks.size>1){
                val b=blocks.last();val a=blocks[blocks.lastIndex-1]
                if(a.sum/a.weight<=b.sum/b.weight)break
                a.last=b.last;a.sum+=b.sum;a.weight+=b.weight;blocks.removeAt(blocks.lastIndex)
            }
        }
        val out=DoubleArray(n)
        for(b in blocks){val y=(b.sum/b.weight).coerceIn(lo,hi)
            for(i in b.first..b.last)out[i]=y+spacing[i]
        }
        return out.toList()
    }
}
