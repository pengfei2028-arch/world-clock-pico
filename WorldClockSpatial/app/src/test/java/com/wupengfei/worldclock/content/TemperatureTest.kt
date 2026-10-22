package com.wupengfei.worldclock.content
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TemperatureTest {
 @Test fun geographicGridWrapsAndDoesNotFlipNorth(){
  val g=TemperatureGrid(4,3,shortArrayOf(1000,1000,1000,1000,0,100,200,300,-1000,-1000,-1000,-1000))
  assertEquals(10.0,g.sample(90.0,120.0)!!,1e-9);assertEquals(-10.0,g.sample(-90.0,-40.0)!!,1e-9)
  assertEquals(0.0,g.sample(0.0,-180.0)!!,1e-9);assertEquals(g.sample(0.0,-180.0),g.sample(0.0,180.0))
  assertEquals(1.5,g.sample(0.0,135.0)!!,1e-9);assertEquals(2.0,g.sample(0.0,0.0)!!,1e-9)
 }
 @Test fun missingNeverBecomesZeroOrCold(){
  val g=TemperatureGrid(2,2,shortArrayOf(Short.MIN_VALUE,1000,0,1000))
  assertNull(g.sample(90.0,-180.0));assertNull(g.sample(0.0,-90.0));assertEquals(0.0,g.sample(-90.0,-180.0)!!,0.0)
  assertNull(g.sample(Double.NaN,0.0));assertNull(g.sample(91.0,0.0))
 }
 @Test fun fixedPaletteHasExplicitExtremes(){
  assertEquals(0,TemperaturePalette.band(-80.0));assertEquals(14,TemperaturePalette.band(60.0))
  assertEquals(4,TemperaturePalette.band(0.0));assertEquals(8,TemperaturePalette.band(20.0))
  assertEquals(TemperaturePalette.band(25.0),TemperaturePalette.band(27.99))
  assertNotEquals(TemperaturePalette.band(27.99),TemperaturePalette.band(28.0))
  assertEquals(listOf(25,28,31,34,37,40),TemperaturePalette.breaks.filter{it>=25})
  assertEquals(1f,TemperatureState().strength,0f)
 }
 @Test fun timeModeRoundtripPreservesClockPreviewAndCitySelection(){
  val s=ClockState();s.now=Instant.parse("2026-10-22T06:00:00Z");s.setOffset(135f);s.rulerCenter=s.instant;s.selectedId="beijing"
  val before=s.instant;val origin=s.rulerOrigin
  s.switchMode(EarthMode.TEMPERATURE)
  s.temperature.displayed=TemperatureFrame(Instant.parse("2026-10-23T01:00:00Z"),25,"a","b","c","d",1,1)
  assertEquals(s.temperature.displayed!!.validTime,s.instant);assertEquals("beijing",s.selectedId)
  s.switchMode(EarthMode.WORLD_TIME);assertEquals(before,s.instant);assertEquals(origin,s.rulerOrigin);assertEquals("beijing",s.selectedId)
 }
 @Test fun temperatureScrubUsesSameHoldRulesWithoutChangingClockTime(){
  val s=ClockState();s.now=Instant.parse("2026-10-22T06:00:00Z");s.setOffset(10f);val time=s.instant
  val calls=mutableListOf<Boolean>();s.onTimeScrub={calls.add(it)};s.switchMode(EarthMode.TEMPERATURE)
  s.beginTimeDrag();s.dragTimeBy(600f);s.finishTimeDrag();assertEquals(listOf(true,false),calls)
  s.switchMode(EarthMode.WORLD_TIME);assertEquals(time,s.instant)
 }
}
