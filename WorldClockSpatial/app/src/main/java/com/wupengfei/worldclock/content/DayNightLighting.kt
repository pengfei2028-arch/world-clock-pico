package com.wupengfei.worldclock.content
import kotlin.math.*
/** Surface lighting comes directly from the Sun. Only city emission fades onto the night side. */
internal object DayNightLighting {
    // Art choice: continuous night-light fade immediately below the horizon; no -3 degree cutoff.
    val nightStartDot=0f
    val nightFullDot=sin(Math.toRadians(-12.0)).toFloat()
    private fun ease(t:Double):Double {val x=t.coerceIn(0.0,1.0);return x*x*x*(x*(6*x-15)+10)}
    fun nightWeight(solarAltitude:Double):Double {
        val dot=sin(Math.toRadians(solarAltitude))
        return ease((dot-nightStartDot)/(nightFullDot-nightStartDot))
    }
}
