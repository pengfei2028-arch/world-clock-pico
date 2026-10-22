package com.wupengfei.worldclock.content

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.*

/** Meeus solar equations as published by NOAA/GML, full Julian-century form.
 * UTC instant is shared by every city. Presentation rotation never advances UTC.
 * Rendering uses geometric solar direction; no atmospheric-refraction shader is added.
 */
internal data class SolarPosition(val declination: Double, val longitude: Double,
    val obliquity: Double, val equationOfTimeMinutes: Double) {
    fun altitude(latitude: Double, eastLongitude: Double): Double {
        val p=Math.toRadians(latitude); val d=Math.toRadians(declination)
        val h=Math.toRadians(eastLongitude-longitude)
        return Math.toDegrees(asin((sin(p)*sin(d)+cos(p)*cos(d)*cos(h)).coerceIn(-1.0,1.0)))
    }
    fun daylight(latitude: Double, eastLongitude: Double): String {
        val elevation=altitude(latitude,eastLongitude)
        return if(elevation>=0.0) "白昼" else if(elevation>=-6.0) "晨昏" else "夜晚"
    }
    companion object {
        fun wrap(degrees: Double) = ((degrees+180.0)%360.0+360.0)%360.0-180.0
        fun at(instant: Instant): SolarPosition {
            val jd=2440587.5+instant.epochSecond/86400.0+instant.nano/86400e9
            val t=(jd-2451545.0)/36525.0
            val l=Math.toRadians(wrap(280.46646+t*(36000.76983+t*0.0003032)))
            val m=Math.toRadians(wrap(357.52911+t*(35999.05029-0.0001537*t)))
            val e=0.016708634-t*(0.000042037+0.0000001267*t)
            val c=sin(m)*(1.914602-t*(0.004817+0.000014*t))+
                sin(2*m)*(0.019993-0.000101*t)+sin(3*m)*0.000289
            val omega=Math.toRadians(125.04-1934.136*t)
            val lambda=l+Math.toRadians(c-0.00569-0.00478*sin(omega))
            val meanEps=23.0+(26.0+(21.448-t*(46.815+t*(0.00059-t*0.001813)))/60.0)/60.0
            val eps=meanEps+0.00256*cos(omega)
            val er=Math.toRadians(eps)
            val decl=Math.toDegrees(asin(sin(er)*sin(lambda)))
            val y=tan(er/2).pow(2)
            val eot=4*Math.toDegrees(y*sin(2*l)-2*e*sin(m)+4*e*y*sin(m)*cos(2*l)-
                0.5*y*y*sin(4*l)-1.25*e*e*sin(2*m))
            val utc=instant.atOffset(ZoneOffset.UTC)
            val minutes=utc.hour*60.0+utc.minute+utc.second/60.0+utc.nano/60e9
            return SolarPosition(decl,wrap(180.0-minutes/4.0-eot/4.0),eps,eot)
        }
    }
}

internal object GlobeGeography {
    // Calibrated against the original mesh UV: Greenwich lies at local Z azimuth 198 degrees.
    // Blender geographic frame has a +Z pole; scene conversion is handled by the imported hierarchy.
    fun direction(latitude: Double, longitude: Double): GlobeVector3 {
        val p=Math.toRadians(latitude); val l=Math.toRadians(longitude+198.0)
        return GlobeVector3(cos(p)*cos(l),cos(p)*sin(l),sin(p))
    }
    fun openingSpin(sun: GlobeVector3, viewInGeographic: GlobeVector3): Double {
        val v=viewInGeographic.normalized(); val a=v.x*sun.x+v.y*sun.y
        val b=-v.x*sun.y+v.y*sun.x; val c=v.z*sun.z
        val phase=acos(((cos(Math.toRadians(78.0))-c)/hypot(a,b)).coerceIn(-1.0,1.0))
        val base=atan2(b,a)
        val first=SolarPosition.wrap(Math.toDegrees(base+phase))
        val second=SolarPosition.wrap(Math.toDegrees(base-phase))
        return if(abs(first)<abs(second)) first else second
    }
}
