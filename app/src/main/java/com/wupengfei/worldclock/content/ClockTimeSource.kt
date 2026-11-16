package com.wupengfei.worldclock.content

import java.time.Instant
import kotlin.math.abs

/** Prefer Android's network UTC estimate when available; remain fully usable offline. */
internal object ClockTimeSource {
    data class Reading(val instant:Instant,val corrected:Boolean,val skewSeconds:Long)
    fun read():Reading {
        val system=Instant.now()
        val network=try { android.os.SystemClock.currentNetworkTimeClock().instant() } catch(_:Exception){ null }
        val skew=network?.let{java.time.Duration.between(system,it).seconds}?:0L
        return Reading(network?:system,network!=null&&abs(skew)>60,skew)
    }
}
