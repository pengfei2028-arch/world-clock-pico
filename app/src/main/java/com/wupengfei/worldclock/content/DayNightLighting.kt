package com.wupengfei.worldclock.content
import kotlin.math.*
/** True solar direction; compact, untinted twilight extension retains PBR daylight/specular. */
internal object DayNightLighting {
    // Project art values, not a claim of physically simulated atmospheric scattering.
    val twilightWidthDot=sin(Math.toRadians(24.0)).toFloat()
    val twilightGain=1f/(4f*twilightWidthDot)
    // Art choice: continuous night-light fade immediately below the horizon; no -3 degree cutoff.
    val nightStartDot=0f
    val nightFullDot=sin(Math.toRadians(-12.0)).toFloat()
    private fun ease(t:Double):Double {val x=t.coerceIn(0.0,1.0);return x*x*x*(x*(6*x-15)+10)}
    fun nightWeight(solarAltitude:Double):Double {
        val dot=sin(Math.toRadians(solarAltitude))
        return ease((dot-nightStartDot)/(nightFullDot-nightStartDot))
    }
}
