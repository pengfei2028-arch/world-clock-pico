package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class DistanceLabelLayoutTest {
    private fun city(id:String,x:Double,y:Double,unit:Double=1.0):CityLabelCandidate {
        val p=GlobeVector3(x*unit,y*unit,-30.0)
        return CityLabelCandidate(id,CityLabelLayout(p,p,true,48*unit,20*unit))
    }
    private fun readout(p:GlobeVector3,unit:Double)=CityLabelLayout(p,p,true,86*unit,26*unit)
    private fun checkClear(a:CityLabelLayout,b:CityLabelLayout){
        assertTrue("Overlapping rectangles: $a / $b",abs(a.center.x-b.center.x)+1e-8>=a.halfWidth+b.halfWidth ||
            abs(a.center.y-b.center.y)+1e-8>=a.halfHeight+b.halfHeight)
    }
    @Test fun nearbyCityNamesCannotCoverTheMidpointOrEachOther(){
        val layout=DistanceLabelLayout();val midpoint=GlobeVector3(0.0,0.0,-30.0)
        val cities=listOf(city("beijing",-130.0,-12.0),city("tokyo",42.0,12.0))
        val result=layout.update(cities,midpoint,1.0,-1000.0,1000.0,.016)
        assertEquals(setOf("beijing","tokyo"),result.keys)
        result.values.forEach{checkClear(it,readout(midpoint,1.0))}
        checkClear(result.getValue("beijing"),result.getValue("tokyo"))
        cities.forEach{assertEquals(it.layout.center.x,result.getValue(it.id).center.x,0.0)}
    }
    @Test fun movingEightCityLabelsRemainClearAtDifferentPlatformScales(){
        for(unit in listOf(.015,.0288,.060)){
            val layout=DistanceLabelLayout()
            repeat(720){frame->
                val t=frame*PI/180
                val midpoint=GlobeVector3(50*unit*sin(t),40*unit*cos(t),-30.0)
                val cities=(0..7).map{i->city("city$i",80*sin(t+i*.4),80*cos(t+i*.4),unit)}
                val result=layout.update(cities,midpoint,unit,-30.0,30.0,1.0/60)
                result.values.forEach{checkClear(it,readout(midpoint,unit))}
                val cards=result.values.toList()
                for(i in cards.indices)for(j in i+1 until cards.size)checkClear(cards[i],cards[j])
            }
        }
    }
    @Test fun closingLayerClearsItsReservedSlotAndCanReopen(){
        val layout=DistanceLabelLayout();val p=GlobeVector3(0.0,0.0,-30.0)
        repeat(20){
            val a=layout.update(listOf(city("a",0.0,0.0)),p,1.0,-1000.0,1000.0,.016)
            checkClear(a.getValue("a"),readout(p,1.0))
            assertTrue(layout.update(emptyList(),null,1.0,-1000.0,1000.0,.016).isEmpty())
        }
    }
}
