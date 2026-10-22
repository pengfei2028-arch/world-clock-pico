package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GlobePointerInputTest {
    private fun snapshot(m: GlobeMotion) = listOf(m.presentation,m.idleDegrees)
    private fun horizontal(m: GlobeMotion): Double {
        val v = m.presentation.rotate(GlobeVector3.Z); return atan2(v.x,v.z)*180.0/PI
    }
    private fun vertical(m: GlobeMotion): Double {
        val v = m.presentation.rotate(GlobeVector3.Z); return atan2(-v.y,v.z)*180.0/PI
    }
    private fun move(input: GlobePointerInput, vertical: Boolean = false) {
        repeat(60) {
            val distance = (it + 1) * 0.002
            input.update(1, false, true, if (vertical) 0.0 else distance,
                if (vertical) distance else 0.0, (it + 1) / 60.0)
        }
    }
    @Test fun pressWithoutMovingFreezesImmediatelyEvenWithoutFurtherInputEvents() {
        val m = GlobeMotion(); val input = GlobePointerInput(m)
        m.tick(0.03, 0.03); input.update(1, true, true, 0.2, -0.3, 0.1)
        val held = snapshot(m)
        repeat(900) { m.tick(1.0 / 90.0, 0.1 + (it + 1) / 90.0) }
        assertEquals(held, snapshot(m)); assertTrue(m.dragging)
        input.update(1, false, false, 0.2, -0.3, 10.2)
        m.tick(1.0 / 90.0, 10.22)
        assertFalse(m.dragging); assertEquals(held[1] as Double,m.idleDegrees,0.0); assertTrue(m.effectSeconds > 9.0)
    }
    @Test fun moveThenHoldDoesNotReturnUntilActualUp() {
        val m = GlobeMotion(); val input = GlobePointerInput(m)
        input.update(1, true, true, 0.0, 0.0, 0.0); move(input, true)
        assertTrue(vertical(m) > 5.0)
        repeat(60) { input.update(1, false, true, 0.0, 0.12, 1.0 + (it + 1) / 60.0) }
        val held = snapshot(m)
        repeat(300) {
            val now = 2.0 + (it + 1) / 60.0
            input.update(1, false, true, 0.003 * sin(it * 1.3), 0.12 + 0.003 * cos(it * 1.1), now)
            m.tick(1.0 / 60.0, now)
        }
        assertEquals(held, snapshot(m)); assertTrue(m.dragging)
        input.update(1, false, false, 0.0, 0.12, 7.1)
        repeat(300) { m.tick(1.0 / 60.0, 10.1 + (it + 1) / 60.0) }
        assertTrue(m.tiltErrorDegrees < 1e-5)
    }
    @Test fun pressDuringReturnFreezesCurrentPoseBeforeAnyMovement() {
        val m = GlobeMotion(); val input = GlobePointerInput(m)
        input.update(1, true, true, 0.0, 0.0, 0.0); move(input, true)
        input.update(1, false, false, 0.0, 0.12, 1.01); m.tick(0.05, 1.06)
        input.update(2, true, true, 0.2, -0.3, 1.07)
        val held = snapshot(m); assertTrue(vertical(m) > 0.0)
        repeat(200) { m.tick(0.05, 1.12 + it * 0.05) }
        assertEquals(held, snapshot(m)); assertTrue(m.dragging)
    }
    @Test fun otherPointerCannotMoveOrReleaseOwnedPress() {
        val m = GlobeMotion(); val input = GlobePointerInput(m)
        input.update(1, true, true, 0.0, 0.0, 0.0)
        assertFalse(input.update(2, true, true, 0.0, 0.0, 0.1))
        assertFalse(input.update(2, false, true, 0.1, 0.1, 0.2))
        assertFalse(input.update(2, false, false, 0.1, 0.1, 0.3))
        m.tick(0.05, 0.4)
        assertTrue(m.dragging); assertEquals(1L, input.activePointerId)
        assertEquals(listOf(GlobeQuaternion.IDENTITY,0.0), snapshot(m))
    }
    @Test fun invalidTrackingNeverReleasesAndReacquisitionCannotJump() {
        val m = GlobeMotion(); val input = GlobePointerInput(m)
        input.update(1, true, true, Double.NaN, Double.NaN, 0.0)
        assertTrue(m.dragging)
        input.update(1, false, true, 0.2, -0.3, 0.1)
        input.update(1, false, true, Double.NaN, Double.NaN, 0.12)
        input.update(1, false, true, 0.5, 0.2, 0.14)
        assertEquals(0.0, horizontal(m), 0.0); assertTrue(m.dragging)
        input.update(1, false, false, Double.NaN, Double.NaN, 0.2)
        assertFalse(m.dragging); assertNull(input.activePointerId)
    }
    @Test fun disposedInputScopeCannotLeaveGlobePermanentlyHeld() {
        val m = GlobeMotion(); val input = GlobePointerInput(m)
        input.update(1, true, true, 0.0, 0.0, 0.1); input.cancel(0.2); m.tick(0.05, 3.25)
        assertNull(input.activePointerId); assertFalse(m.dragging); assertTrue(m.idleDegrees > 0.0)
    }
    @Test fun subCentimetreJitterNeverAccumulatesAtDifferentSampleRates() {
        for (rate in listOf(30, 60, 90)) {
            val m = GlobeMotion(); val input = GlobePointerInput(m)
            input.update(1, true, true, 0.0, 0.0, 0.0)
            repeat(rate * 10) {
                val t = (it + 1).toDouble() / rate
                input.update(1, false, true, 0.008 * sin(t * 23.0), 0.006 * cos(t * 17.0), t)
                m.tick(1.0 / rate, t)
            }
            assertEquals(listOf(GlobeQuaternion.IDENTITY,0.0), snapshot(m))
            assertFalse(input.moving)
        }
    }
    @Test fun bodyTranslationCancelsWithoutDependingOnGlobeDistance() {
        val m = GlobeMotion(); val input = GlobePointerInput(m)
        input.updateHand(1,true,true,GlobeVector3(0.2,1.1,-0.4),GlobeVector3(0.0,1.6,0.0),0.0)
        repeat(600) {
            val t = (it + 1) / 60.0; val bx = 0.2 * sin(t); val by = 0.1 * cos(t)
            input.updateHand(1,false,true,GlobeVector3(0.2+bx,1.1+by,-0.4),GlobeVector3(bx,1.6+by,0.0),t)
        }
        assertEquals(0.0, horizontal(m), 0.0); assertEquals(0.0, vertical(m), 0.0)
    }
    @Test fun slowIntentionalDragAccumulatesAndHasSimilarResponseAcrossRates() {
        val angles = listOf(30, 60, 90).map { rate ->
            val m = GlobeMotion(); val input = GlobePointerInput(m)
            input.update(1, true, true, 0.0, 0.0, 0.0)
            repeat(rate * 2) {
                val t = (it + 1).toDouble() / rate
                input.update(1, false, true, 0.04 * t, 0.0, t)
            }
            assertTrue(horizontal(m) > 20.0); assertTrue(horizontal(m) < 40.0)
            horizontal(m)
        }
        assertTrue(angles.maxOrNull()!! - angles.minOrNull()!! < 0.8)
    }
    @Test fun trackingSpikesAndLongGapsReanchorWithoutTurning() {
        val m = GlobeMotion(); val input = GlobePointerInput(m)
        input.update(1, true, true, 0.0, 0.0, 0.0)
        input.update(1, false, true, 0.5, -0.5, 0.01)
        input.update(1, false, true, 0.0, 0.0, 0.02)
        input.update(1, false, true, 0.1, 0.0, 2.0)
        assertEquals(0.0, horizontal(m), 0.0); assertEquals(0.0, vertical(m), 0.0)
        assertEquals(3, input.rejectedSamples); assertTrue(m.dragging)
    }
    @Test fun handMotionUsesLatchedViewPlaneAndDoesNotChangeAxesDuringHold() {
        val m = GlobeMotion(); val input = GlobePointerInput(m)
        val view = GlobeQuaternion.axisAngle(GlobeVector3.Y,90.0)
        val right = view.rotate(GlobeVector3.X); val front = view.rotate(GlobeVector3.Z)
        input.setViewOrientation(view)
        val hand = GlobeVector3(0.2,1.1,-0.4); val head = GlobeVector3(0.0,1.6,0.0)
        input.updateHand(1,true,true,hand,head,0.0)
        repeat(60) {
            input.setViewOrientation(GlobeQuaternion.IDENTITY)
            input.updateHand(1,false,true,hand+right*((it+1)*0.002),head,(it+1)/60.0)
        }
        val moved = m.presentation.rotate(front)
        assertTrue(moved.dot(right) > 0.08)
        assertEquals(0.0,moved.dot(GlobeVector3.Y),1e-8)
    }
}
