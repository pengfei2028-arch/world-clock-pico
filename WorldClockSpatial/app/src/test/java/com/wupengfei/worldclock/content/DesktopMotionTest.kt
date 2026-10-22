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
        repeat(120){motion.tickDesktop(1.0/60)}
        assertEquals(1.0,motion.idleDegrees,1e-6)
        assertEquals(orientation,motion.presentation)
    }
    @Test fun resumeDoesNotCatchUpHiddenWallTime() {
        val motion=GlobeMotion();motion.enterDesktop()
        motion.tickDesktop(600.0)
        assertEquals(.025,motion.idleDegrees,1e-6)
        motion.tickDesktop(Double.NaN)
        assertEquals(.025,motion.idleDegrees,1e-6)
    }
    @Test fun presentationCannotAdvancePreviewUtc() {
        val clock=ClockState();val utc=java.time.Instant.parse("2026-10-22T07:00:00Z")
        clock.previewBase=utc
        val motion=GlobeMotion();motion.enterDesktop()
        repeat(3600){motion.tickDesktop(1.0/60)}
        assertEquals(30.0,motion.idleDegrees,1e-5)
        assertEquals(utc,clock.instant)
    }
}
