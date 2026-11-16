package com.wupengfei.worldclock.content

import kotlin.math.*

/** Blender scene 02_Original_Animation, action frames 44..113, 60fps with 65/100 time remap.
 * Position is retargeted to the approved 48m globe at 60m, 4 degrees below the eye.
 * V28 choreography: the 4s Mixed VST reveal leads; the 4s globe approach
 * begins only when the veil has expanded to 50%.
 * Spin handoff overlaps the final 0.65s of the globe approach.
 * Position easing and Mixed VST fade adaptation are project choices.
 */
internal class GlobeEntrance {
    companion object {
        const val VEIL_SECONDS = 4.0
        // Project choice: establish a dark centre before the globe enters.
        private const val VEIL_FADE_SECONDS = 0.8
        const val APPROACH_SECONDS = 4.0
        const val HANDOFF_SECONDS = 0.65
        // Derive the cue from actual expansion, not half the elapsed veil time.
        // Compute once so future easing changes cannot move the cue before 50%.
        val APPROACH_START_SECONDS: Double = run {
            var low = 0.0; var high = 1.0
            repeat(48) {
                val middle = (low+high)*0.5
                if (dampedApproach(middle) < 0.5) low = middle else high = middle
            }
            high*VEIL_SECONDS
        }
        val DURATION = APPROACH_START_SECONDS+APPROACH_SECONDS
        val HANDOFF_START = DURATION-HANDOFF_SECONDS
        private const val SOURCE_FRAME_RATE = 69.0/APPROACH_SECONDS
        private val START = GlobeVector3(-0.03638710081577301,-0.021410038694739342,-10.005571365356445)
        private val END = GlobeVector3(-0.02436714619398117,-0.009348705410957336,-5.807074069976807)
        private const val ROT_START = 6.9453325271606445
        private const val ROT_HANDLE = 7.536655902862549
        private const val ROT_END = 7.606634140014648
        private fun smooth(value: Double): Double {
            val t = value.coerceIn(0.0,1.0); return t*t*(3.0-2.0*t)
        }
        private fun sourceSpin(frame: Double): Double {
            val u = ((frame-51.0)/159.0).coerceIn(0.0,1.0); val v = 1.0-u
            return (3*v*u*u*(ROT_HANDLE-ROT_START)+u*u*u*(ROT_END-ROT_START))*180.0/PI
        }
        private fun sourceSpeed(frame: Double): Double {
            val u = ((frame-51.0)/159.0).coerceIn(0.0,1.0); val v = 1-u
            return (6*v*u*(ROT_HANDLE-ROT_START)+3*u*u*(ROT_END-ROT_HANDLE))*SOURCE_FRAME_RATE/159.0*180.0/PI
        }
        private fun dampedApproach(value: Double): Double {
            val u = value.coerceIn(0.0,1.0)
            // Integral of 60*u^2*(1-u)^3. Zero endpoint velocity/acceleration,
            // peak speed at 40% and a longer, non-oscillating deceleration tail.
            return (u*u*u*(20.0+u*(-45.0+u*(36.0-10.0*u)))).coerceIn(0.0,1.0)
        }
        fun sample(seconds: Double): Pose {
            val t = if(seconds.isFinite()) seconds.coerceIn(0.0,DURATION) else 0.0
            val approachTime = (t-APPROACH_START_SECONDS).coerceIn(0.0,APPROACH_SECONDS)
            val spinFrame = 44.0+SOURCE_FRAME_RATE*min(approachTime,APPROACH_SECONDS-HANDOFF_SECONDS)
            val approachProgress = dampedApproach(approachTime/APPROACH_SECONDS)
            val p = START+(END-START)*approachProgress
            val depth = -p.z/(-END.z)*60.0
            val position = GlobeVector3(depth*(p.x/(-p.z)-END.x/(-END.z)),
                depth*(-tan(4.0*PI/180.0)+p.y/(-p.z)-END.y/(-END.z)), -depth)
            // Original animated spherical mask's centre reveal, with its EASE colour ramp.
            // Earth/stars stay hidden until the half-open cue starts their local clock.
            val revealFrame = 44.0+39.0*approachTime
            val mask = -1.0+1.809999942779541*smooth((revealFrame-46.0)/39.0)
            val alpha = smooth((1.0+mask-0.5272725820541382)/(1.0-0.5272725820541382))
            // The veil starts immediately and continues opening behind the delayed globe.
            val veilProgress = dampedApproach(t/VEIL_SECONDS)
            val tail = (t-HANDOFF_START).coerceIn(0.0,HANDOFF_SECONDS)
            // Match source velocity at the cut, then ease down to the accepted 3 degrees/s.
            val spin = sourceSpin(spinFrame)+3.0*tail+(sourceSpeed(spinFrame)-3.0)*
                (tail-tail*tail/HANDOFF_SECONDS+tail*tail*tail/(3.0*HANDOFF_SECONDS*HANDOFF_SECONDS))
            return Pose(position,spin,alpha,veilProgress,smooth(t/VEIL_FADE_SECONDS))
        }
    }
    data class Pose(val position: GlobeVector3,val spinDegrees: Double,val opacity: Double,val veilProgress: Double,val veilOpacity: Double)
    var elapsed = 0.0; private set
    val finished get() = elapsed >= DURATION
    val pose get() = sample(elapsed)
    /** Returns only the part of this frame that belongs to idle; never skips opening frames on load. */
    fun advance(dt: Double, paused: Boolean = false): Double {
        if(paused || !dt.isFinite() || dt <= 0.0) return 0.0
        val step = dt.coerceAtMost(0.05)
        val used = min(step,(DURATION-elapsed).coerceAtLeast(0.0))
        elapsed = (elapsed+used).coerceAtMost(DURATION)
        return (step-used).coerceAtLeast(0.0)
    }
}
