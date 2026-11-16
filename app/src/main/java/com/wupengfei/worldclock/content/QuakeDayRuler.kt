package com.wupengfei.worldclock.content

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Calendar-day cells clipped to the actual catalog window, never equal-spaced date guesses. */
internal object QuakeDayRuler {
    data class Day(val date:LocalDate,val startFraction:Double,val endFraction:Double) {
        val label get()="${date.monthValue}/${date.dayOfMonth}"
    }
    fun days(start:Instant,end:Instant):List<Day> {
        if(end<=start)return emptyList()
        val result=mutableListOf<Day>()
        var date=start.atOffset(ZoneOffset.UTC).toLocalDate()
        while(date.atStartOfDay().toInstant(ZoneOffset.UTC)<end){
            val a=date.atStartOfDay().toInstant(ZoneOffset.UTC).coerceAtLeast(start)
            val b=date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).coerceAtMost(end)
            result.add(Day(date,QuakeRules.fraction(a,start,end),QuakeRules.fraction(b,start,end)))
            date=date.plusDays(1)
        }
        return result
    }
}
