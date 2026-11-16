package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class CityWeatherTest {
    private val now=Instant.parse("2026-10-21T12:30:00Z")
    private fun reading(code:Int=0,temp:Double=18.4)=CityWeather(temp,code,now.minusSeconds(600),now)
    @Test fun mapsRainSnowFogAndStormSeparately(){
        assertEquals("小雨",reading(61).condition);assertEquals("小雪",reading(71).condition)
        assertEquals("雾",reading(45).condition);assertEquals("雷雨伴冰雹",reading(99).condition)
        assertEquals("天气未知",reading(999).condition)
    }
    @Test fun formatsCurrentTemperatureWithoutInventingMissingWeather(){
        assertEquals("当前 18°C · 晴",CityWeatherState(reading()).label(now))
        assertEquals("天气暂不可用",CityWeatherState(failed=true).label(now))
        assertEquals("天气更新中",CityWeatherState().label(now))
    }
    @Test fun failedRefreshLabelsCachedValue(){
        assertEquals("缓存 18°C · 晴",CityWeatherState(reading(),true).label(now))
    }
    @Test fun oldWeatherIsNotPresentedAsCurrent(){
        assertTrue(CityWeatherState(reading()).label(now.plusSeconds(3600)).startsWith("缓存 "))
        assertEquals("天气暂不可用",CityWeatherState(reading(),true).label(now.plusSeconds(21601)))
    }
    @Test fun clockSkewCannotDisplayFutureReadingAsCurrent(){
        assertFalse(reading().usable(now.minusSeconds(3600)))
        assertTrue(reading().usable(now))
    }
    @Test fun negativeTemperatureAndPreviewLabelRemainClear(){
        val value=CityWeatherState(reading(3,-4.6))
        assertEquals("-5°C · 阴",value.label(now,compact=true))
        assertEquals("当前 -5°C · 阴",value.label(now,compact=false))
    }
}
