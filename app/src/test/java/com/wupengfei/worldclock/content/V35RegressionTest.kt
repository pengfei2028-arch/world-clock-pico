package com.wupengfei.worldclock.content
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class V35RegressionTest {
 @Test fun earlyBeijingTwilightHasOnlyFaintGradualCityLamps(){
  val at=ZonedDateTime.of(2026,10,21,17,30,0,0,ZoneId.of("Asia/Shanghai")).toInstant()
  val alt=SolarPosition.at(at).altitude(39.9042,116.4074)
  assertTrue(alt<0);assertTrue(alt> -3)
  assertTrue(DayNightLighting.nightWeight(alt)>0.0 && DayNightLighting.nightWeight(alt)<.05)
 }
 @Test fun cityLightFadeKeepsDayAndNightEndpoints(){
  assertEquals(0.0,DayNightLighting.nightWeight(0.0),1e-7)
  assertEquals(1.0,DayNightLighting.nightWeight(-12.0),1e-7)
 }
 @Test fun timePinDragIsRelativeAndDoesNotJumpWhenGrabbedAgain(){
  val s=ClockState();s.now=Instant.parse("2026-10-21T09:30:00Z")
  s.beginTimeDrag();s.dragTimeBy(12f);val first=s.instant
  s.finishTimeDrag();assertEquals(first,s.instant)
  s.beginTimeDrag();assertEquals(first,s.instant)
  s.dragTimeBy(-5f);s.finishTimeDrag();assertEquals(s.now.plusSeconds(420),s.instant)
  s.live();assertEquals(s.now,s.instant);assertFalse(s.preview)
 }
}
