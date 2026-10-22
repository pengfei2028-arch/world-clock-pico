package com.wupengfei.worldclock.content

import kotlin.math.*

/** Physical hand motion, independent of the distant target's projected hit point.
 * Thresholds and response are project interaction choices, not PICO design defaults.
 */
internal class GlobePointerInput(private val motion: GlobeMotion) {
    companion object {
        private const val START_DISTANCE_M = 0.012
        private const val MOVING_DEADBAND_M = 0.004
        private const val FILTER_SECONDS = 0.04
        private const val INPUT_UNITS_PER_METRE = 3360.0 // GlobeMotion uses 0.1 degree per unit.
        private const val TRACKING_GAP_SECONDS = 0.15
    }
    var activePointerId: Long? = null; private set
    var moving = false; private set
    var rejectedSamples = 0; private set
    private var viewRight = GlobeVector3.X
    private var viewUp = GlobeVector3.Y
    private var hasBaseline = false
    private var rawX = 0.0; private var rawY = 0.0
    private var filteredX = 0.0; private var filteredY = 0.0
    private var anchorX = 0.0; private var anchorY = 0.0
    private var lastTime = 0.0
    private var lastAppliedTime = 0.0

    private fun rebase(x: Double, y: Double, now: Double) {
        rawX = x; rawY = y; filteredX = x; filteredY = y
        anchorX = x; anchorY = y
        lastTime = now; lastAppliedTime = now
        moving = false; hasBaseline = true
    }

    // Latch the view plane once per press; head turns during the hold cannot move its axes.
    fun setViewOrientation(orientation: GlobeQuaternion) {
        if (activePointerId != null) return
        val q = orientation.normalized()
        viewRight = q.rotate(GlobeVector3.X); viewUp = q.rotate(GlobeVector3.Y)
        motion.setDragBasis(viewRight,viewUp)
    }
    fun updateHand(id: Long, down: Boolean, pressed: Boolean,
        hand: GlobeVector3, head: GlobeVector3, now: Double): Boolean {
        val relative = hand-head
        return update(id,down,pressed,relative.dot(viewRight),-relative.dot(viewUp),now)
    }

    fun update(id: Long, down: Boolean, pressed: Boolean, xM: Double, yM: Double, now: Double): Boolean {
        if (!now.isFinite()) return false
        val valid = xM.isFinite() && yM.isFinite()
        if (activePointerId == null) {
            if (!down || !pressed) return false
            activePointerId = id
            motion.begin(now)
            hasBaseline = false; moving = false; rejectedSamples = 0
            if (valid) rebase(xM, yM, now)
            return true
        }
        if (activePointerId != id) return false
        if (!pressed) {
            activePointerId = null; moving = false; hasBaseline = false
            motion.end(now)
            return true
        }
        if (!valid) {
            hasBaseline = false; moving = false; rejectedSamples++
            motion.drag(0.0, 0.0, now)
            return true
        }
        if (!hasBaseline) {
            rebase(xM, yM, now); motion.drag(0.0, 0.0, now)
            return true
        }
        val dt = now - lastTime
        if (dt <= 0.0) return true
        // Reacquisition / implausible discontinuity: re-anchor without moving the globe.
        if (dt > TRACKING_GAP_SECONDS || hypot(xM - rawX, yM - rawY) > max(0.06, dt * 1.5)) {
            rejectedSamples++
            rebase(xM, yM, now); motion.drag(0.0, 0.0, now)
            return true
        }
        lastTime = now; rawX = xM; rawY = yM
        val alpha = 1.0 - exp(-dt / FILTER_SECONDS)
        filteredX += (xM - filteredX) * alpha
        filteredY += (yM - filteredY) * alpha
        if (moving && now - lastAppliedTime > 0.18) {
            moving = false; anchorX = filteredX; anchorY = filteredY
        }
        val dx = filteredX - anchorX; val dy = filteredY - anchorY
        val distance = hypot(dx, dy)
        val threshold = if (moving) MOVING_DEADBAND_M else START_DISTANCE_M
        if (distance <= threshold + 0.00025) {
            motion.drag(0.0, 0.0, now)
            return true
        }
        // Consume the threshold; never jump to the initial 12mm offset on activation.
        val scale = (distance - threshold) / distance
        val appliedX = dx * scale; val appliedY = dy * scale
        // Switching thresholds must not reintroduce the discarded start distance next frame.
        anchorX = filteredX - dx / distance * MOVING_DEADBAND_M
        anchorY = filteredY - dy / distance * MOVING_DEADBAND_M
        moving = true; lastAppliedTime = now
        motion.drag(appliedX * INPUT_UNITS_PER_METRE, appliedY * INPUT_UNITS_PER_METRE, now)
        return true
    }

    fun cancel(now: Double) {
        activePointerId = null; moving = false; hasBaseline = false
        motion.end(now, cancelled = true)
    }
}
