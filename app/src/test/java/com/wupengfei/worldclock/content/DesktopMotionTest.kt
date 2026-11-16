package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test

class DesktopMotionTest {
    @Test fun handoffKeepsOrientationAndClearsOldGesture() {
        val motion=GlobeMotion()
        motion.begin(1.0);motion.drag(100.0,50.0,1.05)
        val orientation=motion.presentation
        motion.setTimelineHeld(true,1.1);motion.enterDesktop()
        assertEquals(orientation,motion.presentation)
        assertFalse(motion.dragging);assertFalse(motion.timelineHeld)
        repeat(120){motion.tickDesktop(1.0/60,2.0+it/60.0)}
        assertEquals(18.0,motion.idleDegrees,1e-6)
        assertEquals(orientation,motion.presentation)
    }
    @Test fun resumeDoesNotCatchUpHiddenWallTime() {
        val motion=GlobeMotion();motion.enterDesktop()
        motion.tickDesktop(600.0,600.0)
        assertEquals(.45,motion.idleDegrees,1e-6)
        motion.tickDesktop(Double.NaN,601.0)
        assertEquals(.45,motion.idleDegrees,1e-6)
    }
    @Test fun presentationCannotAdvancePreviewUtc() {
        val clock=ClockState();val utc=java.time.Instant.parse("2026-10-22T07:00:00Z")
        clock.previewBase=utc
        val motion=GlobeMotion();motion.enterDesktop()
        repeat(3600){motion.tickDesktop(1.0/60,it/60.0)}
        assertEquals(180.0,motion.idleDegrees,1e-5)
        assertEquals(utc,clock.instant)
    }
    @Test fun desktopIsFasterAndReturningToFullRestoresOriginalSpeed() {
        val desktop=GlobeMotion();desktop.enterDesktop()
        val full=GlobeMotion()
        repeat(600){desktop.tickDesktop(1.0/60,it/60.0);full.tick(1.0/60,it/60.0)}
        assertEquals(30.0,full.idleDegrees,1e-9)
        assertEquals(90.0,desktop.idleDegrees,1e-9)
        // Full smoothly converges to its original 3deg/s after the handoff.
        repeat(120){desktop.tick(1.0/60,11.0+it/60.0)}
        val before=desktop.idleDegrees
        repeat(60){desktop.tick(1.0/60,20.0+it/60.0)}
        assertEquals(3.0,desktop.idleDegrees-before,1e-9)
    }
    @Test fun desktopHoldAndDwellPauseSpinWhileNightEffectClockContinues() {
        val motion=GlobeMotion();motion.enterDesktop();motion.begin(1.0)
        repeat(120){motion.tickDesktop(1.0/60,1.0+it/60.0)}
        assertEquals(0.0,motion.idleDegrees,1e-9)
        assertEquals(2.0,motion.effectSeconds,1e-9)
        motion.end(3.0)
        repeat(120){motion.tickDesktop(1.0/60,3.0+it/60.0)}
        assertEquals(0.0,motion.idleDegrees,1e-9)
        assertEquals(4.0,motion.effectSeconds,1e-9)
        repeat(120){motion.tickDesktop(1.0/60,6.0+it/60.0)}
        assertTrue(motion.idleDegrees>16.0)
    }
}
