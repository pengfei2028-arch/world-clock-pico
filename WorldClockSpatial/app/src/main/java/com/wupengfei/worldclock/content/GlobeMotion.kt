package com.wupengfei.worldclock.content

import kotlin.math.*

/** Project interaction tuning, not PICO design-token values. */
internal class GlobeMotion {
    companion object {
        const val MAX_DRAG_DEGREES_PER_SECOND = 70.0
        const val MAX_RELEASE_DEGREES_PER_SECOND = 18.0
        const val RELEASE_DWELL_SECONDS = 3.0
        const val FOCUS_DWELL_SECONDS = 5.0
        private const val IDLE_DEGREES_PER_SECOND = 3.0
        private const val IDLE_BLEND_RATE = 12.0
        private const val INERTIA_DAMPING = 6.0
        // V34 project tuning: return movement itself is slow, separate from the 3s/5s dwell.
        const val MIN_RETURN_SECONDS = 4.0
        const val MAX_RETURN_DEGREES_PER_SECOND = 24.0
    }
    var presentation = GlobeQuaternion.IDENTITY; private set
    var normalPole = GlobeVector3(-sin(23.44*PI/180.0), cos(23.44*PI/180.0), 0.0); private set
    var idleDegrees = 0.0; private set
    var dragging = false; private set
    var timelineHeld = false; private set
    var effectSeconds = 0.0; private set
    var restoringTilt = false; private set
    var focusLocked = false; private set
    private var focusFrom = GlobeQuaternion.IDENTITY
    private var focusTarget = GlobeQuaternion.IDENTITY
    private var focusElapsed = 0.0
    private var focusDuration = 0.0
    private var focusResumeAt = Double.POSITIVE_INFINITY
    // True only once the release/focus dwell is over, including zero-tilt return.
    fun cloudsMayShow(now:Double)=!dragging && !timelineHeld && !focusLocked && now>=resumeAfter
    val focusing get()=focusLocked && focusElapsed<focusDuration
    val tiltErrorDegrees get() = acos(presentation.rotate(normalPole).dot(normalPole).coerceIn(-1.0,1.0))*180.0/PI
    private var viewRight = GlobeVector3.X
    private var viewUp = GlobeVector3.Y
    private var velocityX = 0.0; private var velocityY = 0.0
    private var idleSpeed = IDLE_DEGREES_PER_SECOND
    private var lastInput = 0.0
    private var lastMotion = Double.NEGATIVE_INFINITY
    private var coastSpeed = 0.0; private var coastAngle = 0.0
    private var resumeAfter = Double.NEGATIVE_INFINITY
    private var returnFrom = GlobeQuaternion.IDENTITY
    private var returnTo = GlobeQuaternion.IDENTITY
    private var returnElapsed = 0.0; private var returnDuration = MIN_RETURN_SECONDS

    /** Center the city while keeping the authored north-pole direction upright on screen. */
    fun focusCity(direction: GlobeVector3,towardUser: GlobeVector3) {
        val toward=towardUser.normalized()
        val centered=GlobeQuaternion.align(direction,toward)*presentation
        val pole=centered.rotate(normalPole)
        val from=(pole-toward*pole.dot(toward)).normalized()
        val to=(normalPole-toward*normalPole.dot(toward)).normalized()
        val roll=if(from==GlobeVector3.ZERO||to==GlobeVector3.ZERO)0.0
            else atan2(toward.dot(from.cross(to)),from.dot(to))*180.0/PI
        focusTo(GlobeQuaternion.axisAngle(toward,roll)*centered)
    }
    fun focusTo(target: GlobeQuaternion) {
        if (dragging || timelineHeld) return
        focusFrom=presentation; focusTarget=target.normalized(); focusElapsed=0.0
        focusDuration=max(1.4,1.875*focusFrom.angleTo(focusTarget)/55.0)
        focusLocked=true; restoringTilt=false; coastSpeed=0.0; idleSpeed=0.0
        focusResumeAt=Double.POSITIVE_INFINITY;resumeAfter=Double.NEGATIVE_INFINITY
    }
    fun noteUiActivity(now: Double) {
        if(focusLocked && focusResumeAt.isFinite() && now.isFinite())focusResumeAt=now+FOCUS_DWELL_SECONDS
    }
    fun resumeIdle(now: Double) {
        if (dragging || timelineHeld) return
        focusLocked=false
        begin(now); end(now,true); resumeAfter=now
    }
    fun setNormalPole(pole: GlobeVector3) {
        val n = pole.normalized()
        if (n != GlobeVector3.ZERO && !dragging) normalPole = n
    }
    fun setDragBasis(right: GlobeVector3, up: GlobeVector3) {
        if (dragging || timelineHeld) return
        val r = right.normalized(); val u = (up-r*up.dot(r)).normalized()
        if (r != GlobeVector3.ZERO && u != GlobeVector3.ZERO) { viewRight = r; viewUp = u }
    }
    fun begin(now: Double) {
        if (!now.isFinite()) return
        resumeAfter=Double.POSITIVE_INFINITY
        dragging = true; restoringTilt = false; focusLocked = false
        velocityX = 0.0; velocityY = 0.0; coastSpeed = 0.0
        idleSpeed = 0.0; lastMotion = Double.NEGATIVE_INFINITY; lastInput = now
    }
    fun drag(dxDp: Double, dyDp: Double, now: Double) {
        if (!dragging || timelineHeld || !dxDp.isFinite() || !dyDp.isFinite() || !now.isFinite()) return
        val elapsed = now-lastInput
        if (elapsed <= 0.0) return
        val dt = elapsed.coerceAtMost(0.05); lastInput = now
        var dx = dxDp*0.10; var dy = dyDp*0.10
        val length = hypot(dx,dy)
        val maximum = MAX_DRAG_DEGREES_PER_SECOND*dt
        if (length > maximum) { val scale = maximum/length; dx *= scale; dy *= scale }
        // One view-space axis for the complete swipe. Never use the Earth's tilted pole here.
        val axis = viewRight*dy + viewUp*dx
        presentation = (GlobeQuaternion.axisAngle(axis,hypot(dx,dy))*presentation).normalized()
        velocityX = velocityX*0.4+dx/dt*0.6; velocityY = velocityY*0.4+dy/dt*0.6
        val speed = hypot(velocityX,velocityY)
        if (speed > MAX_RELEASE_DEGREES_PER_SECOND) {
            velocityX *= MAX_RELEASE_DEGREES_PER_SECOND/speed
            velocityY *= MAX_RELEASE_DEGREES_PER_SECOND/speed
        }
        if (hypot(dx,dy) > 0.000001) lastMotion = now
    }
    fun end(now: Double, cancelled: Boolean = false) {
        if (!now.isFinite() || !dragging) return
        dragging = false
        if(!timelineHeld)scheduleReturn(now)
    }
    /** Time changes illumination while presentation, focus and pole return stay still. */
    fun setTimelineHeld(held:Boolean,now:Double){
        if(!now.isFinite()||held==timelineHeld)return
        timelineHeld=held
        if(held){
            resumeAfter=Double.POSITIVE_INFINITY
            restoringTilt=false;focusLocked=false;coastSpeed=0.0;idleSpeed=0.0
            velocityX=0.0;velocityY=0.0
        }else if(!dragging)scheduleReturn(now)
    }
    private fun scheduleReturn(now:Double){
        resumeAfter=now+RELEASE_DWELL_SECONDS
        // Keep the geographic twist; only undo the swing that displaced the authored pole.
        returnFrom = presentation
        val correction = GlobeQuaternion.align(presentation.rotate(normalPole),normalPole)
        returnTo = (correction*presentation).normalized()
        val angle = correction.angleTo(GlobeQuaternion.IDENTITY)
        // Quintic easing has a peak derivative of 1.875; cap the actual angular speed.
        returnDuration = max(MIN_RETURN_SECONDS,1.875*angle/MAX_RETURN_DEGREES_PER_SECOND); returnElapsed = 0.0
        restoringTilt = angle > 1e-7
        coastAngle = 0.0
        coastSpeed = 0.0 // Do not replay stale fling after the requested 3-second dwell.

    }
    /** Container handoff keeps orientation, but drops gesture/focus timers owned by the old view. */
    fun enterDesktop() {
        dragging=false;timelineHeld=false;focusLocked=false;restoringTilt=false
        resumeAfter=Double.NEGATIVE_INFINITY;coastSpeed=0.0;idleSpeed=0.0
    }
    /** Presentation only: this never advances UTC or the temperature forecast. */
    fun tickDesktop(seconds:Double) {
        if(!seconds.isFinite())return
        val dt=seconds.coerceIn(0.0,.05)
        effectSeconds+=dt;idleDegrees=(idleDegrees+dt*.5)%360.0
    }
    fun tick(dtSeconds: Double, now: Double, autoSpin: Boolean = true) {
        if (!dtSeconds.isFinite() || !now.isFinite()) return
        val dt = dtSeconds.coerceIn(0.0,0.05); effectSeconds += dt
        // Only presentation pauses. Night-light noise remains alive during every hold.
        if (dragging || timelineHeld || now < resumeAfter) return
        if (focusLocked) {
            focusElapsed+=dt
            val t=(focusElapsed/focusDuration).coerceIn(0.0,1.0)
            val eased=t*t*t*(t*(6.0*t-15.0)+10.0)
            presentation=GlobeQuaternion.slerp(focusFrom,focusTarget,eased)
            if(t>=1.0){
                if(!focusResumeAt.isFinite())focusResumeAt=now+FOCUS_DWELL_SECONDS
                else if(now>=focusResumeAt)resumeIdle(now)
            }
            return
        }
        val decay = exp(-INERTIA_DAMPING*dt)
        val coastStep = coastSpeed*(1.0-decay)/INERTIA_DAMPING; coastSpeed *= decay
        if (restoringTilt) {
            returnElapsed += dt; coastAngle += coastStep
            val t = (returnElapsed/returnDuration).coerceIn(0.0,1.0)
            val eased = t*t*t*(t*(6.0*t-15.0)+10.0)
            presentation = (GlobeQuaternion.axisAngle(normalPole,coastAngle)*
                GlobeQuaternion.slerp(returnFrom,returnTo,eased)).normalized()
            if (returnElapsed+1e-9 >= returnDuration) {
                presentation = (GlobeQuaternion.axisAngle(normalPole,coastAngle)*returnTo).normalized()
                restoringTilt = false
            }
        } else if (coastStep != 0.0) {
            presentation = (GlobeQuaternion.axisAngle(normalPole,coastStep)*presentation).normalized()
        }
        if (!autoSpin) { idleSpeed=0.0; return }
        val idleDecay = exp(-IDLE_BLEND_RATE*dt)
        val idleStep = IDLE_DEGREES_PER_SECOND*dt +
            (idleSpeed-IDLE_DEGREES_PER_SECOND)*(1.0-idleDecay)/IDLE_BLEND_RATE
        idleDegrees = (idleDegrees+idleStep)%360.0
        idleSpeed = IDLE_DEGREES_PER_SECOND+(idleSpeed-IDLE_DEGREES_PER_SECOND)*idleDecay
    }
}
