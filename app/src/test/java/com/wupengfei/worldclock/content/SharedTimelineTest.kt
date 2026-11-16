package com.wupengfei.worldclock.content
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.io.File

class SharedTimelineTest {
 private val start=Instant.parse("2026-10-22T00:00:00Z")
 private fun state():ClockState=ClockState().apply {
  now=start.plusSeconds(6*3600+1200)
  temperature.manifest=TemperatureManifest("test",start,start,(0..24).map {
   TemperatureFrame(start.plusSeconds(it*3600L),it,"grid","texture","a","b",1,1)
  },File("."))
  temperature.now(now);switchMode(EarthMode.TEMPERATURE)
 }
 @Test fun sameMinutesGainSelectsRealHourlyFrame(){
  val s=state();s.beginTimeDrag();s.dragTimeBy(60f)
  assertEquals(7,s.temperature.index)
  assertEquals(60f,s.previewMarkerMinutes,.001f)
  assertEquals(20f,s.nowMarkerMinutes,.001f)
  s.finishTimeDrag();assertNull(s.temperature.scrubTime)
 }
 @Test fun forecastPansAtEdgesAndClampsWithoutInventingHours(){
  val s=state();s.beginTimeDrag();s.dragTimeBy(720f)
  assertEquals(18,s.temperature.index)
  assertEquals(170f,s.previewMarkerMinutes,.001f)
  s.dragTimeBy(100000f);assertEquals(24,s.temperature.index)
  assertEquals(start.plusSeconds(86400),s.temperature.scrubTime)
  s.dragTimeBy(-100000f);assertEquals(0,s.temperature.index);assertEquals(start,s.temperature.scrubTime)
 }
 @Test fun toggleKeepsEachTimeViewportAndNowRemainsReal(){
  val s=state();s.switchMode(EarthMode.WORLD_TIME);s.beginTimeDrag();s.dragTimeBy(125f);s.finishTimeDrag()
  val world=s.clockInstant;val origin=s.rulerOrigin
  s.switchMode(EarthMode.TEMPERATURE);s.beginTimeDrag();s.dragTimeBy(240f);s.finishTimeDrag()
  val tempOrigin=s.rulerOrigin;val index=s.temperature.index
  s.switchMode(EarthMode.WORLD_TIME);assertEquals(world,s.instant);assertEquals(origin,s.rulerOrigin)
  s.switchMode(EarthMode.TEMPERATURE);assertEquals(tempOrigin,s.rulerOrigin);assertEquals(index,s.temperature.index)
  s.temperature.now(s.now);assertTrue(s.temperature.autoNow);assertNull(s.temperature.rulerCenter)
  assertEquals(20f,s.nowMarkerMinutes,.001f)
 }
 @Test fun missingDataCannotFabricateASelection(){
  val s=ClockState();s.switchMode(EarthMode.TEMPERATURE);s.beginTimeDrag();s.dragTimeBy(60f);s.finishTimeDrag()
  assertNull(s.temperature.requested);assertNull(s.temperature.scrubTime)
 }
 @Test fun fadeReachesEndpointsWithoutJumpingAndCanReverse(){
  val f=TemperatureVisibility();assertEquals(0f,f.update(false,.1),0f)
  val a=f.update(true,.1);assertTrue(a>0f&&a<.1f)
  repeat(2){f.update(true,.1)};assertEquals(.5f,f.opacity,.001f)
  val before=f.opacity;assertEquals(before,f.update(false,0.0),0f)
  val down=f.update(false,.1);assertTrue(down<before&&down>0f)
  assertEquals(down,f.update(true,0.0),0f)
  repeat(7){f.update(true,.1)};assertEquals(1f,f.opacity,0f)
  repeat(7){f.update(false,.1)};assertEquals(0f,f.opacity,0f)
 }
 @Test fun invalidFrameDeltaCannotPoisonOpacity(){
  val f=TemperatureVisibility();f.update(true,Double.NaN);assertEquals(0f,f.opacity,0f)
  f.update(true,-10.0);assertEquals(0f,f.opacity,0f)
  f.update(true,20.0);assertTrue(f.opacity<.1f)
 }
}
