package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone

class QuakeDayRulerTest {
    private fun at(s:String)=Instant.parse(s)
    @Test fun rolling24HoursCrossesOneRealMidnight(){
        val days=QuakeDayRuler.days(at("2026-10-24T08:00:00Z"),at("2026-10-25T08:00:00Z"))
        assertEquals(listOf("10/24","10/25"),days.map{it.label})
        assertEquals(0.0,days.first().startFraction,0.0)
        assertEquals(2.0/3,days.first().endFraction,1e-9)
        assertEquals(days.first().endFraction,days.last().startFraction,0.0)
        assertEquals(1.0,days.last().endFraction,0.0)
    }
    @Test fun sevenDaysHaveDailyTicksAndClippedEndpointDays(){
        val days=QuakeDayRuler.days(at("2026-10-18T08:30:00Z"),at("2026-10-25T08:30:00Z"))
        assertEquals(8,days.size)
        days.zipWithNext().forEach{(a,b)->assertEquals(a.endFraction,b.startFraction,0.0)}
        days.drop(1).dropLast(1).forEach{assertEquals(1.0/7,it.endFraction-it.startFraction,1e-9)}
    }
    @Test fun leapDayAndYearBoundaryRemainValidCalendarDays(){
        val leap=QuakeDayRuler.days(at("2024-02-28T00:00:00Z"),at("2024-03-02T00:00:00Z"))
        assertEquals(listOf("2/28","2/29","3/1"),leap.map{it.label})
        val year=QuakeDayRuler.days(at("2026-12-31T00:00:00Z"),at("2027-01-02T00:00:00Z"))
        assertEquals(LocalDate.of(2027,1,1),year.last().date)
    }
    @Test fun thirtyDaysCoverFullWindowWithoutExtraDayAtMidnight(){
        val start=at("2026-09-25T00:00:00Z");val end=at("2026-10-25T00:00:00Z")
        val days=QuakeDayRuler.days(start,end)
        assertEquals(30,days.size);assertEquals("10/24",days.last().label)
        days.forEach{assertEquals(1.0/30,it.endFraction-it.startFraction,1e-9)}
        assertEquals(end,QuakeRules.atFraction(1.0,start,end))
    }
    @Test fun localTimezoneCannotShiftTheUtcDates(){
        val old=TimeZone.getDefault()
        try{
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            val days=QuakeDayRuler.days(at("2026-10-24T23:00:00Z"),at("2026-10-25T01:00:00Z"))
            assertEquals(listOf("10/24","10/25"),days.map{it.label})
            assertEquals(.5,days.first().endFraction,1e-9)
        }finally{TimeZone.setDefault(old)}
    }
    @Test fun invalidOrEmptyIntervalsHaveNoTicks(){
        val t=at("2026-10-25T00:00:00Z")
        assertTrue(QuakeDayRuler.days(t,t).isEmpty());assertTrue(QuakeDayRuler.days(t,t.minusSeconds(1)).isEmpty())
    }
}
