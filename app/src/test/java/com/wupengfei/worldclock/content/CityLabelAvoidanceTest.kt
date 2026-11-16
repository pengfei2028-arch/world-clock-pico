package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class CityLabelAvoidanceTest {
    private val unit=.0288
    private val globe=GlobeVector3(0.0,0.0,-60.0)
    private fun city(id:String,x:Double=0.0,y:Double=0.0)=
        CityLabelCandidate(id,CityLabelLayout.place(globe,GlobeVector3(x,y, -36.0),unit))
    private fun separated(cards:Collection<CityLabelLayout>){
        val list=cards.toList()
        for(i in list.indices)for(j in i+1 until list.size){val a=list[i];val b=list[j]
            assertTrue("Overlapping cards $i and $j",abs(a.center.x-b.center.x)>=a.halfWidth+b.halfWidth-1e-7 ||
                abs(a.center.y-b.center.y)>=a.halfHeight+b.halfHeight-1e-7)
        }
    }
    @Test fun coincidentPlacesBothKeepReadableCardsAndExactRightHandAnchors(){
        val input=listOf(city("Beijing"),city("Tianjin"))
        val out=CityLabelAvoidance().update(input,unit,-26.0,26.0,.016)
        assertEquals(input.map{it.id}.toSet(),out.keys);separated(out.values)
        input.forEach{c->val placed=out.getValue(c.id)
            assertTrue(placed.labelOnRight)
            assertEquals(c.layout.center.x,placed.center.x,1e-9)
            assertEquals(c.layout.elbow.x,placed.elbow.x,1e-9)
            assertEquals(c.layout.center.y-c.layout.elbow.y,placed.center.y-placed.elbow.y,1e-9)
        }
        assertEquals(input.map{it.layout.center.y}.average(),out.values.map{it.center.y}.average(),1e-9)
    }
    @Test fun eightDenseCitiesFitWithoutHidingClippingOrChangingDepth(){
        val input=(1..8).map{city("city$it",it*.03,24.8)}
        val out=CityLabelAvoidance().update(input,unit,-26.0,26.0,.016)
        assertEquals(8,out.size);separated(out.values)
        out.values.forEach{
            assertTrue(it.center.y+it.halfHeight<=26.0+1e-8)
            assertTrue(it.center.y-it.halfHeight>= -26.0-1e-8)
            assertEquals(globe.z+26.5,it.center.z,1e-8)
        }
    }
    @Test fun reorderingInputAndSmallMotionCannotSwapCloseCities(){
        val layout=CityLabelAvoidance()
        var before=layout.update(listOf(city("a"),city("b")),unit,-26.0,26.0,.016)
        repeat(180){frame->
            val jitter=if(frame%2==0).002 else -.002
            val after=layout.update(listOf(city("b",0.0,jitter),city("a",0.0,-jitter)),unit,-26.0,26.0,.016)
            assertTrue(after.getValue("a").center.y<after.getValue("b").center.y)
            after.forEach{(id,p)->assertTrue(abs(p.center.y-before.getValue(id).center.y)<.01)}
            separated(after.values);before=after
        }
    }
    @Test fun newCityYieldsSpaceThenBothCardsSettleSmoothly(){
        val layout=CityLabelAvoidance()
        val initial=layout.update(listOf(city("a")),unit,-26.0,26.0,.016).getValue("a")
        var out=layout.update(listOf(city("a"),city("b")),unit,-26.0,26.0,.016)
        assertTrue(abs(out.getValue("a").center.y-initial.center.y)<.05)
        separated(out.values)
        repeat(120){
            val next=layout.update(listOf(city("a"),city("b")),unit,-26.0,26.0,.016)
            assertTrue(abs(next.getValue("a").center.y-out.getValue("a").center.y)<.08)
            separated(next.values);out=next
        }
        assertEquals(initial.center.y,out.values.map{it.center.y}.average(),.01)
    }
    @Test fun separationReleasesOffsetAndRemovalKeepsCorrectCityIdentity(){
        val layout=CityLabelAvoidance()
        layout.update(listOf(city("a"),city("b"),city("c")),unit,-26.0,26.0,.016)
        val moved=listOf(city("c",20.0),city("a",-20.0))
        var out=emptyMap<String,CityLabelLayout>()
        repeat(180){out=layout.update(moved,unit,-26.0,26.0,.016);separated(out.values)}
        assertEquals(setOf("a","c"),out.keys)
        moved.forEach{assertEquals(it.layout.center.y,out.getValue(it.id).center.y,.001)}
        assertTrue(layout.update(emptyList(),unit,-26.0,26.0,.016).isEmpty())
    }
    @Test fun fullRotationLikeCrossingNeverDropsOrOverlapsAnyOfEightCards(){
        val layout=CityLabelAvoidance()
        repeat(720){frame->
            val phase=frame*.009
            val input=(0..7).map{i->city("$i",18*sin(phase+i*.19),16*cos(phase+i*.27))}
            val out=layout.update(if(frame%2==0)input else input.reversed(),unit,-26.0,26.0,1.0/60)
            assertEquals(8,out.size);separated(out.values)
        }
    }
}
