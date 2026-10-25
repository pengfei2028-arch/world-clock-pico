package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class QuakeWaveScheduleTest {
    private val time=Instant.parse("2026-10-25T00:00:00Z")
    private fun event(i:Int,mag:Double=4.5,lon:Double=i*20.0)=
        QuakeEvent("e$i",setOf("e$i"),"Test fixture",time.minusSeconds(i*3600L),time,0.0,lon,12.0,mag,"mw","")
    @Test fun magnitudeBoundariesAndVertexPayloadMatch(){
        for((m,b) in listOf(null to 0,Double.NaN to 0,4.99 to 0,5.0 to 1,6.99 to 1,7.0 to 2,9.5 to 2)){
            assertEquals(b,QuakeRules.magnitudeBand(m))
            val g=QuakeDecalGeometry().apply{patch(GlobeVector3.Z*2.024,.1,true,5,.37,-1.0,12,b)}
            assertTrue(g.colors.all{it.magnitude==b/2.0&&it.slot==1.0})
        }
    }
    @Test fun rotatesThroughMoreEventsThanBudgetWithoutDailyCutoff(){
        val events=(0..11).map{event(it).copy(occurredAt=time.minusSeconds(it*86400L))}
        val schedule=QuakeWaveSchedule()
        val first=schedule.choose(0.0,events,null,emptySet())
        assertEquals(6,first.size)
        assertEquals(first,schedule.choose(9.0,events,null,emptySet()))
        val second=schedule.choose(9.61,events,null,emptySet())
        assertEquals(6,second.size)
        assertEquals(12,(first+second).map{it.id}.distinct().size)
    }
    @Test fun selectedRemainsAndMajorEventsHavePriority(){
        val events=(0..10).map{event(it,if(it in 8..10)7.2 else 4.5)}
        val schedule=QuakeWaveSchedule()
        val majors=mutableSetOf<String>()
        repeat(4){round->
            val result=schedule.choose(round*9.61,events,"e0",emptySet())
            assertEquals(6,result.size);assertTrue(result.any{it.id=="e0"})
            assertTrue(result.count{it.magnitude!!>=7}>=2)
            majors.addAll(result.filter{it.magnitude!!>=7}.map{it.id})
        }
        assertEquals(setOf("e8","e9","e10"),majors)
    }
    @Test fun denseDatelineAndPolarEventsAreSuppressedWithoutMovingLocations(){
        val a=event(0,lon=179.0);val b=event(1,lon=-179.0)
        val c=event(2,lon=0.0).copy(latitude=89.0);val d=event(3,lon=180.0).copy(latitude=89.0)
        val result=QuakeWaveSchedule().choose(0.0,listOf(a,b,c,d),null,emptySet())
        assertEquals(2,result.size);assertTrue(result.all{it in listOf(a,b,c,d)})
    }
    @Test fun newReplayArrivalGetsPriorityButFutureEventsCannotAppear(){
        val events=(0..10).map{event(it)}
        val schedule=QuakeWaveSchedule()
        schedule.choose(0.0,events.take(8),null,emptySet())
        val arrival=schedule.choose(.2,events.take(9),null,setOf("e8"))
        assertTrue(arrival.any{it.id=="e8"});assertFalse(arrival.any{it.id=="e9"})
        val rewound=schedule.choose(.3,events.take(3),null,emptySet())
        assertEquals(setOf("e0","e1","e2"),rewound.map{it.id}.toSet())
    }
    @Test fun selectionPreemptsDenseNeighborAndStaysInSameSlot(){
        val events=(0..9).map{event(it)}+event(11,lon=1.0)
        val schedule=QuakeWaveSchedule()
        schedule.choose(0.0,events,null,emptySet())
        val result=schedule.choose(.1,events,"e11",emptySet())
        assertTrue(result.any{it.id=="e11"});assertFalse(result.any{it.id=="e0"})
        val index=result.indexOfFirst{it.id=="e11"}
        val round=schedule.choose(9.61,events,"e11",emptySet())
        assertEquals(index,round.indexOfFirst{it.id=="e11"})
    }
    @Test fun resetAndEmptyCatalogLeaveNoRetainedAssignments(){
        val schedule=QuakeWaveSchedule();val events=(0..9).map{event(it)}
        val first=schedule.choose(0.0,events,null,emptySet())
        assertTrue(schedule.choose(.1,emptyList(),null,emptySet()).isEmpty())
        schedule.reset()
        assertEquals(first,schedule.choose(1.0,events,null,emptySet()))
    }
    @Test fun selectionTakesFirstSlotImmediatelyAndSurvivesChangingViewCandidates(){
        val events=(0..11).map{event(it)};val target=events[8]
        val schedule=QuakeWaveSchedule()
        schedule.choose(0.0,events.take(6),null,emptySet())
        repeat(20){round->
            // The renderer adds the selected event even when it is on the globe's back.
            val front=if(round%2==0)events.take(6) else events.drop(6)
            val chosen=schedule.choose(.01+round*9.61,(listOf(target)+front).distinctBy{it.id},target.id,emptySet())
            assertEquals(target,chosen.first());assertEquals(1,chosen.count{it.id==target.id})
            assertTrue(chosen.size<=QuakeStyle.MAX_RIPPLE_GROUPS)
        }
        val gone=schedule.choose(200.0,events.take(6),target.id,emptySet())
        assertFalse(gone.any{it.id==target.id}) // A withdrawn/future record is never invented.
    }
    @Test fun selectingAnAlreadyAnimatedEventMovesItToFirstSlotWithoutDuplication(){
        val events=(0..8).map{event(it)};val schedule=QuakeWaveSchedule()
        val target=schedule.choose(0.0,events,null,emptySet()).last()
        val chosen=schedule.choose(.01,events,target.id,emptySet())
        assertEquals(target,chosen.first());assertEquals(chosen.size,chosen.map{it.id}.toSet().size)
    }
    @Test fun selectedWaveStartsVisibleAndLoopsWithoutResettingItsPhase(){
        for(start in listOf(0.0,1.1,9.6,600.0,86400.0)){
            val seed=QuakeWaveSchedule.selectionSeed(start)
            assertTrue(seed>=0&&seed<1)
            fun phase(t:Double):Double {val v=t/QuakeStyle.RING_SECONDS+seed;return v-kotlin.math.floor(v)}
            assertEquals(.2,phase(start),1e-9)
            assertEquals(.7,phase(start+1.2),1e-9)
            assertEquals(.2,phase(start+2.4*100),1e-9)
        }
    }
}
