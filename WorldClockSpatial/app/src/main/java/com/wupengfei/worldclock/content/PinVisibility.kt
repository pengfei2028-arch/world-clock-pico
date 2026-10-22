package com.wupengfei.worldclock.content
import kotlin.math.*
/** Fade starts before the limb; all pin parts use the same opacity. */
internal class PinVisibility {
    var alpha=0f;private set
    fun update(target:Float,seconds:Double):Float {
        val t=target.coerceIn(0f,1f)
        val k=(1.0-exp(-seconds.coerceAtLeast(0.0)/0.12)).toFloat()
        alpha+=(t-alpha)*k
        if(abs(alpha-t)<0.002f)alpha=t
        return alpha
    }
    companion object {
        fun atLimb(facing:Double):Float {
            val t=((facing-0.04)/0.24).coerceIn(0.0,1.0)
            return (t*t*t*(t*(t*6-15)+10)).toFloat()
        }
    }
}
