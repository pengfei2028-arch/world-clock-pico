package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class QuakeWaveScheduleTest {
    private val now=Instant.parse("2026-10-25T00:00:00Z")
    private fun event(id:String,age:Long,mag:Double?=4.0)=QuakeEvent(id,setOf(id),"Test fixture",now.minusSeconds(age),now,0.0,0.0,12.0,mag,"mw","")
    @Test fun admittedMagnitudesPlayWithin24HoursIncludingBoundary(){
        for(mag in listOf(4.0,4.1,5.0,7.0)){
            assertTrue(QuakeWaveSchedule.continuous(event("a",86400,mag),now))
            if(mag<6)assertFalse(QuakeWaveSchedule.continuous(event("a",86401,mag),now))
        }
    }
    @Test fun majorEventsPlayForSevenDaysInclusive(){
        assertTrue(QuakeWaveSchedule.continuous(event("a",604800,6.0),now))
        assertFalse(QuakeWaveSchedule.continuous(event("a",604800,5.99),now))
        assertFalse(QuakeWaveSchedule.continuous(event("a",604801,9.0),now))
        assertFalse(QuakeWaveSchedule.continuous(event("a",-1,9.0),now))
    }
    @Test fun noSixSlotCapOrDenseRegionSuppressionAndNoDuplicates(){
        val events=(0..999).map{event("$it",it.toLong(),4.0)}
        val chosen=QuakeWaveSchedule.choose(events+events,now,null)
        assertEquals(events,chosen)
        assertEquals(chosen,QuakeWaveSchedule.choose(events,now.plusSeconds(10),null))
    }
    @Test fun selectionOverridesAgeButNeverInventsWithdrawnOrFutureEvents(){
        val old=event("old",20*86400L);val future=event("future",-10)
        assertEquals(listOf(old),QuakeWaveSchedule.choose(listOf(old,future),now,"old"))
        assertTrue(QuakeWaveSchedule.choose(listOf(old,future),now,"future").isEmpty())
        assertTrue(QuakeWaveSchedule.choose(emptyList(),now,"old").isEmpty())
    }
    @Test fun replayUsesItsCursorAndRewindingNeverShowsFutureWaves(){
        val a=event("a",5*86400L,4.0);val b=event("b",86400,7.0)
        val cursor=now.minusSeconds(4*86400)
        assertEquals(listOf(a),QuakeWaveSchedule.choose(listOf(a,b),cursor,null))
        assertEquals(listOf(b),QuakeWaveSchedule.choose(listOf(a,b),now,null))
        assertTrue(QuakeWaveSchedule.choose(listOf(a,b),now.minusSeconds(6*86400),null).isEmpty())
    }
    @Test fun selectedPhaseStartsVisibleAndNeverResetsDuringContinuousPlayback(){
        for(start in listOf(0.0,1.1,9.6,600.0,86400.0)){
            val seed=QuakeWaveSchedule.selectionSeed(start)
            fun phase(t:Double):Double {val v=t/QuakeStyle.RING_SECONDS+seed;return v-kotlin.math.floor(v)}
            assertEquals(.2,phase(start),1e-9);assertEquals(.7,phase(start+1.2),1e-9)
            assertEquals(.2,phase(start+2.4*100),1e-9)
        }
    }
    @Test fun catalogueFloorRejectsSmallUnknownAndInvalidMagnitudes(){
        val events=listOf(event("a",1,-1.0),event("b",2,null),event("c",3,6.0),event("d",1,3.99),event("e",1,4.0))
        val state=EarthquakeState();state.accept(QuakeCatalog(QuakePeriod.WEEK,now,now,events))
        assertEquals(listOf(events[2],events[4]),state.filtered)
        assertEquals(listOf(events[2],events[4]),QuakeWaveSchedule.choose(events,now,null))
        state.chooseMinimum(5.0);assertEquals(listOf(events[2]),state.filtered)
        state.chooseMinimum(4.0);assertEquals(listOf(events[2],events[4]),state.filtered)
        assertEquals(listOf(events[2],events[4]),QuakeQuery.admitted(QuakeCatalog(QuakePeriod.WEEK,now,now,events)).events)
    }
    @Test fun serverQueryHasM4FloorAndExactPeriodButNoTruncatingLimit(){
        for(period in QuakePeriod.entries){
            val url=QuakeQuery.endpoint(period,now)
            assertTrue(url.startsWith("https://earthquake.usgs.gov/fdsnws/event/1/query?"))
            assertTrue(url.contains("minmagnitude=4&"));assertTrue(url.contains("eventtype=earthquake"))
            assertTrue(url.contains("starttime=${now.minusSeconds(period.days*86400)}&endtime=$now"))
            assertFalse(url.contains("limit="))
        }
        val c=QuakeCatalog(QuakePeriod.WEEK,now.plusSeconds(3),now.plusSeconds(5),listOf(event("a",604800)),windowEnd=now)
        assertEquals(now,c.end);assertEquals(now.minusSeconds(604800),c.start)
        assertEquals(1,c.filtered(4.0).size)
    }
    @Test fun colorAndShaderPayloadContractsAreUnchanged(){
        for((m,b) in listOf(null to 0,4.99 to 0,5.0 to 1,6.99 to 1,7.0 to 2)){
            assertEquals(b,QuakeRules.magnitudeBand(m))
            val g=QuakeDecalGeometry().apply{patch(GlobeVector3.Z*2.024,.1,true,0,.37,-1.0,12,b)}
            assertTrue(g.colors.all{it.magnitude==b/2.0&&it.slot==0.0})
        }
    }
}
