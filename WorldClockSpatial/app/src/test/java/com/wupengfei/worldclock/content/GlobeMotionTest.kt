package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GlobeMotionTest {
    private fun drag(m: GlobeMotion, dx: Double = 20.0, dy: Double = 10.0) {
        m.begin(0.0); repeat(60) { m.drag(dx,dy,(it+1)*0.02) }
    }
    @Test fun swipeDirectionFollowsViewPlaneAtEveryAngleEvenAfterEarlierRotation() {
        for (view in listOf(GlobeQuaternion.IDENTITY,
            GlobeQuaternion.axisAngle(GlobeVector3.Y,70.0),
            GlobeQuaternion.axisAngle(GlobeVector3(0.3,0.7,0.2),53.0))) {
            val right = view.rotate(GlobeVector3.X); val up = view.rotate(GlobeVector3.Y)
            val front = view.rotate(GlobeVector3.Z)
            for ((x,y) in listOf(1.0 to 0.0,-1.0 to 0.0,0.0 to 1.0,0.0 to -1.0,
                1.0 to 1.0,-1.0 to -1.0,2.0 to 1.0,-1.0 to 2.0)) {
                val m = GlobeMotion(); m.setDragBasis(right,up); m.begin(0.0)
                m.drag(100.0,-70.0,0.05)
                val before = m.presentation
                m.drag(x*1e6,y*1e6,0.07)
                val delta = m.presentation*before.inverse()
                val moved = delta.rotate(front)
                val actualX = moved.dot(right); val actualDown = -moved.dot(up)
                assertTrue(actualX*x+actualDown*y > 0.0)
                assertEquals(0.0,actualX*y-actualDown*x,1e-8)
            }
        }
    }
    @Test fun combinedSpeedCapPreservesDirectionAndIgnoresDuplicateTimes() {
        for (rate in listOf(30,60,240)) {
            val m = GlobeMotion(); m.begin(0.0)
            repeat(rate) {
                val before = m.presentation; val now = (it+1).toDouble()/rate
                m.drag(2e6,1e6,now)
                assertTrue(before.angleTo(m.presentation) <= GlobeMotion.MAX_DRAG_DEGREES_PER_SECOND/rate+1e-5)
                val once = m.presentation
                repeat(8) { m.drag(-1e6,1e6,now) }
                assertEquals(once,m.presentation)
            }
            assertEquals(GlobeMotion.MAX_DRAG_DEGREES_PER_SECOND,GlobeQuaternion.IDENTITY.angleTo(m.presentation),1e-6)
        }
    }
    @Test fun releaseRestoresOriginalPoleWithoutResettingSelectedLongitudeOrSnapping() {
        val m = GlobeMotion(); drag(m,1000.0,0.0)
        val held = m.presentation; assertTrue(m.tiltErrorDegrees > 1.0)
        m.end(1.5)
        assertEquals(held,m.presentation)
        repeat(150){m.tick(.02,1.52+it*.02)}
        repeat(400) { m.tick(0.02,4.52+it*0.02) }
        assertTrue(m.tiltErrorDegrees < 1e-5)
        assertTrue(m.presentation.angleTo(GlobeQuaternion.IDENTITY) > 20.0)
        assertFalse(m.restoringTilt)
    }
    @Test fun upsideDownPoleReturnsSmoothlyWithoutOverspeed() {
        val m = GlobeMotion(); m.setNormalPole(GlobeVector3.Y); m.begin(0.0)
        // Deliberate 180-degree turn below the drag speed cap, independent of tuning.
        repeat(72) { m.drag(0.0,25.0,(it+1)*0.05) }
        assertEquals(180.0,m.tiltErrorDegrees,1e-5); m.end(5.3)
        repeat(300) {
            val before = m.presentation; m.tick(0.05,8.35+it*0.05)
            assertTrue(before.angleTo(m.presentation) <= 24.0*0.05+1e-5)
        }
        assertTrue(m.tiltErrorDegrees < 1e-5); assertFalse(m.restoringTilt)
    }
    @Test fun smallReturnTakesFourSecondsWithGentleStartAndFinish() {
        val m = GlobeMotion(); m.setNormalPole(GlobeVector3.Y); m.begin(0.0)
        repeat(10) { m.drag(0.0,100.0,(it+1)*0.02) }
        val error = m.tiltErrorDegrees; m.end(0.5)
        m.tick(0.05,3.55)
        assertTrue((error-m.tiltErrorDegrees)/error < 0.002)
        repeat(19) { m.tick(0.05,3.6+it*0.05) }
        assertTrue(m.restoringTilt)
        assertTrue(m.tiltErrorDegrees > error*0.85) // Still near the starting pose at 1s.
        repeat(58) { m.tick(0.05,4.55+it*0.05) }
        assertTrue(m.restoringTilt)
        assertTrue(m.tiltErrorDegrees < error*0.001)
        repeat(2) { m.tick(0.05,7.45+it*0.05) }
        assertFalse(m.restoringTilt); assertTrue(m.tiltErrorDegrees < 1e-5)
    }
    @Test fun regrabFreezesPoseButKeepsCityLightClockAlive() {
        val m = GlobeMotion(); drag(m); m.end(1.21); m.tick(0.05,1.26); m.begin(1.27)
        val pose = m.presentation; val idle = m.idleDegrees; val time = m.effectSeconds
        repeat(200) { m.tick(0.05,1.32+it*0.05) }
        assertEquals(pose,m.presentation); assertEquals(idle,m.idleDegrees,0.0)
        assertTrue(m.effectSeconds>time+9.9)
    }
    @Test fun releaseHoldsForThreeSecondsThenBlendsIntoIdle() {
        val m=GlobeMotion();m.begin(0.0);m.drag(50.0,50.0,.05);m.end(1.0)
        val pose=m.presentation
        repeat(59){m.tick(.05,1.05+it*.05)}
        assertEquals(pose,m.presentation);assertEquals(0.0,m.idleDegrees,0.0)
        m.tick(.04,4.04);assertTrue(m.idleDegrees>0.0);assertTrue(m.idleDegrees<.12)
        repeat(7){m.tick(.04,4.08+it*.04)}
        val before=m.idleDegrees;m.tick(.04,4.36)
        assertTrue((m.idleDegrees-before)/.04>2.9)
    }
    @Test fun cancelRestoresPoleWithoutReleaseCoast() {
        val m = GlobeMotion(); drag(m); val held = m.presentation
        val expected = GlobeQuaternion.align(held.rotate(m.normalPole),m.normalPole)*held
        m.end(1.21,true); repeat(400) { m.tick(0.02,4.23+it*0.02) }
        assertTrue(m.presentation.angleTo(expected) < 1e-5)
        assertTrue(m.tiltErrorDegrees < 1e-5); assertTrue(m.idleDegrees > 0.0)
    }
    @Test fun invalidInputAndLargeFrameGapCannotCauseJump() {
        val m = GlobeMotion(); m.begin(0.0); m.drag(Double.NaN,2.0,0.01)
        assertEquals(GlobeQuaternion.IDENTITY,m.presentation)
        m.drag(1e6,1e6,100.0)
        assertTrue(m.presentation.angleTo(GlobeQuaternion.IDENTITY) <= 5.00001)
        m.end(100.1,true); m.tick(1000.0,1100.1)
        assertTrue(m.idleDegrees <= 0.15)
    }
    @Test fun returnIdleAndCoastRemainStableAcrossFrameRates() {
        val slow = GlobeMotion(); val fast = GlobeMotion()
        for (m in listOf(slow,fast)) { drag(m); m.end(1.21) }
        repeat(50) { slow.tick(0.04,4.25+it*0.04) }
        repeat(200) { fast.tick(0.01,4.22+it*0.01) }
        assertTrue(slow.restoringTilt); assertTrue(fast.restoringTilt)
        assertEquals(slow.idleDegrees,fast.idleDegrees,1e-8)
        assertTrue(slow.presentation.angleTo(fast.presentation) < 1e-5)
    }
}
