package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.math.*

class FinalPolishTest {
    private fun clock()=ClockState().apply {
        val catalog=CityCatalog.read(File("src/main/assets/cities/catalog.tsv").bufferedReader())
        cities=listOf(checkNotNull(catalog.city("1816670")),checkNotNull(catalog.city("2643743")))
        now=Instant.parse("2026-10-25T10:00:00Z")
        setOffset(73f);rulerCenter=instant
    }
    @Test fun clockAndDistanceNeverRemainSelectedTogetherAndKeepPreview(){
        val c=clock();val time=c.instant;val ruler=c.rulerOrigin
        repeat(100){
            c.toggleDistance();assertTrue(c.distance.enabled);assertFalse(c.timeModeActive)
            assertNotNull(c.distance.pair(c.cities))
            c.toggleCities();assertTrue(c.timeModeActive);assertFalse(c.distance.enabled)
            assertNull(c.distance.firstId);assertNull(c.distance.secondId);assertTrue(c.citiesOpen)
            assertEquals(time,c.instant);assertEquals(ruler,c.rulerOrigin)
        }
    }
    @Test fun sameClockModeReentryStillExitsDistance(){
        val c=clock();c.toggleDistance();assertEquals(EarthMode.WORLD_TIME,c.mode)
        c.switchMode(EarthMode.WORLD_TIME)
        assertFalse(c.distance.enabled);assertTrue(c.timeModeActive)
    }
    @Test fun usingTimeRulerExitsDistanceButGlobeDragPreservesIt(){
        val c=clock();c.toggleDistance();val pair=c.distance.pair(c.cities)
        c.manualInteraction();assertEquals(pair,c.distance.pair(c.cities));assertFalse(c.timeModeActive)
        c.beginTimeDrag();assertFalse(c.distance.enabled);assertTrue(c.timeModeActive)
        c.dragTimeBy(30f);c.finishTimeDrag()
        assertEquals(Instant.parse("2026-10-25T11:43:00Z"),c.instant)
    }
    @Test fun otherPrimaryModesAndDistanceCannotLeaveStaleSelections(){
        val c=clock()
        for(mode in listOf(EarthMode.TEMPERATURE,EarthMode.EARTHQUAKE)){
            c.switchMode(mode);c.toggleDistance()
            assertTrue(c.distance.enabled);assertFalse(c.timeModeActive);assertEquals(EarthMode.WORLD_TIME,c.mode)
            c.switchMode(mode);assertFalse(c.distance.enabled);assertFalse(c.timeModeActive)
        }
    }
    @Test fun captionFastMotionHasLessThanOneFrameOfFilterLag(){
        for(rate in listOf(60,72,90,120)){
            val d=ToolbarDrag();d.begin(0.0,0.0,0.0)
            val speed=180.0
            repeat(rate){
                val t=(it+1).toDouble()/rate;val target=t*speed
                d.sample(target,0.0,t);d.advance(1.0/rate)
                assertTrue(d.x<=target)
                assertTrue("rate=$rate lag="+(target-d.x),(target-d.x)/speed<.008)
            }
            d.end();assertEquals(180.0-.35,d.x,1e-8)
            val last=d.x;repeat(rate){d.advance(1.0/rate)};assertEquals(last,d.x,0.0)
        }
    }
    @Test fun captionReleaseConsumesFinalInputBeforeNextRenderFrame(){
        val d=ToolbarDrag();d.begin(0.0,0.0,0.0)
        d.sample(10.0,0.0,.016);d.end()
        assertEquals(9.65,d.x,1e-9);assertFalse(d.active)
        d.begin(10.0,0.0,.030);assertEquals(9.65,d.x,1e-9)
        d.sample(20.0,0.0,.046);d.advance(.016)
        assertTrue(d.x>9.65+9.65*.85) // At least 85% of new movement in the first 16ms.
    }
    @Test fun captionTrackingLossCannotCatchUpTowardStaleTarget(){
        val d=ToolbarDrag();d.begin(0.0,0.0,0.0)
        d.sample(15.0,0.0,.016);d.advance(.004);val held=d.x
        d.sample(300.0,100.0,1.0);d.advance(.016)
        assertEquals(held,d.x,0.0);assertEquals(0.0,d.y,0.0)
        d.sample(300.2,100.2,1.016);d.advance(.016);assertEquals(held,d.x,0.0)
    }
}
