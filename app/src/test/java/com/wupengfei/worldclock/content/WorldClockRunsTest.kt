package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WorldClockRunsTest {
    private fun cold(runs:WorldClockRuns)=runs.launch(false,false,false,false,false)
    private fun finishEntrance(run:WorldClockRun) { repeat(150){run.entrance.advance(.05)} }

    @Test fun closeAndReopenInSameProcessRestartsTheEntireEntrance() {
        val runs=WorldClockRuns()
        assertTrue(cold(runs))
        val first=runs.current
        finishEntrance(first)
        first.clock.ready=true
        first.clock.previewBase=Instant.parse("2026-01-01T00:00:00Z")
        first.motion.begin(1.0)
        first.geographicBase=GlobeQuaternion.axisAngle(GlobeVector3.Z,80.0)
        assertTrue(first.entrance.finished)
        assertTrue(cold(runs))
        val next=runs.current
        assertNotSame(first,next)
        assertNotSame(first.clock,next.clock)
        assertNotSame(first.motion,next.motion)
        assertEquals(0.0,next.entrance.elapsed,0.0)
        assertFalse(next.entrance.finished)
        assertEquals(0.0,next.entrance.pose.opacity,0.0)
        assertEquals(0.0,next.entrance.pose.veilOpacity,0.0)
        assertFalse(next.clock.ready)
        assertFalse(next.motion.dragging)
        assertNull(next.clock.previewBase)
        assertNull(next.geographicBase)
        // Old callbacks cannot advance the next run's entrance.
        first.entrance.advance(.05)
        assertEquals(0.0,next.entrance.elapsed,0.0)
    }

    @Test fun fullSharedAndTransitionLaunchesKeepTimeOrientationAndEntrance() {
        val runs=WorldClockRuns();cold(runs)
        val current=runs.current;finishEntrance(current)
        current.clock.previewBase=Instant.parse("2026-10-26T12:00:00Z")
        current.geographicBase=GlobeQuaternion.axisAngle(GlobeVector3.Z,45.0)
        for(flags in listOf(
            booleanArrayOf(true,false,false,true,false),
            booleanArrayOf(false,true,false,false,true),
            booleanArrayOf(false,false,true,false,false),
            booleanArrayOf(false,false,false,true,false),
            booleanArrayOf(false,false,false,false,true),
            booleanArrayOf(true,true,true,true,true))) {
            assertFalse(runs.launch(flags[0],flags[1],flags[2],flags[3],flags[4]))
            assertSame(current,runs.current)
            assertTrue(runs.current.entrance.finished)
            assertEquals(Instant.parse("2026-10-26T12:00:00Z"),runs.current.clock.previewBase)
        }
    }

    @Test fun repeatedWarmProcessReopensAlwaysBeginHidden() {
        val runs=WorldClockRuns()
        repeat(20) {
            assertTrue(cold(runs))
            assertEquals(it+1,runs.generation)
            assertEquals(0.0,runs.current.entrance.pose.opacity,0.0)
            finishEntrance(runs.current)
            assertTrue(runs.current.entrance.finished)
        }
    }
}
