package com.wupengfei.worldclock.content

import java.time.Instant
import kotlin.math.floor

/** Membership is geographic and time based; never a camera-facing or six-slot lottery. */
internal object QuakeWaveSchedule {
    fun selectionSeed(seconds:Double):Double {
        val seed=.2-seconds/QuakeStyle.RING_SECONDS
        return seed-floor(seed)
    }
    fun continuous(event:QuakeEvent,reference:Instant):Boolean {
        if(event.occurredAt>reference||(event.magnitude?:Double.NEGATIVE_INFINITY)<QuakeRules.CATALOG_MINIMUM)return false
        val age=java.time.Duration.between(event.occurredAt,reference)
        return age<=java.time.Duration.ofHours(24) ||
            ((event.magnitude?:Double.NEGATIVE_INFINITY)>=6.0 && age<=java.time.Duration.ofDays(7))
    }
    fun choose(events:List<QuakeEvent>,reference:Instant,selectedId:String?):List<QuakeEvent> =
        events.filter{(it.magnitude?:Double.NEGATIVE_INFINITY)>=QuakeRules.CATALOG_MINIMUM&&it.occurredAt<=reference&&(it.id==selectedId||continuous(it,reference))}.distinctBy{it.id}
    // Small events have compact waves; all eligible events still animate continuously.
    fun patchDegrees(event:QuakeEvent,selected:Boolean)=when {
        selected || (event.magnitude?:0.0)>=7->QuakeStyle.WAVE_PATCH_DEGREES
        (event.magnitude?:0.0)>=6->5.0*QuakeStyle.WAVE_RANGE_SCALE
        (event.magnitude?:0.0)>=5->3.8*QuakeStyle.WAVE_RANGE_SCALE
        (event.magnitude?:0.0)>=3->2.6*QuakeStyle.WAVE_RANGE_SCALE
        else->1.6*QuakeStyle.WAVE_RANGE_SCALE
    }
}
