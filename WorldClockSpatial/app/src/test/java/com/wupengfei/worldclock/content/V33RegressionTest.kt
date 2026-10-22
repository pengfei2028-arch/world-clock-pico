package com.wupengfei.worldclock.content
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class V33RegressionTest {
 private fun focused():Pair<GlobeMotion,Double>{
  val m=GlobeMotion();m.focusTo(GlobeQuaternion.axisAngle(GlobeVector3.X,48.0));var now=0.0
  while(m.focusing&&now<10){now+=.01;m.tick(.01,now)}
  assertFalse(m.focusing);assertTrue(m.focusLocked);return m to now
 }
 @Test fun beijingAtFivePMDoesNotShowNightLights(){
  val at=ZonedDateTime.of(2026,10,21,17,0,0,0,ZoneId.of("Asia/Shanghai")).toInstant()
  val altitude=SolarPosition.at(at).altitude(39.9042,116.4074)
  assertTrue(altitude>3.0);assertEquals(0.0,DayNightLighting.nightWeight(altitude),1e-8)
  val evening=SolarPosition.at(at.plusSeconds(3600)).altitude(39.9042,116.4074)
  assertTrue(evening< -6.0);assertTrue(DayNightLighting.nightWeight(evening)>.5)
  assertEquals(1.0,DayNightLighting.nightWeight(-12.0),1e-8)
  println("Beijing 17:00 altitude="+altitude+"; 18:00 altitude="+evening)
 }
 @Test fun twilightFadeIsConfinedToBelowHorizon(){
  assertEquals(0.0,DayNightLighting.nightWeight(0.0),1e-8)
  assertEquals(0.0,DayNightLighting.nightWeight(28.0),1e-8)
  var previous=0.0
  for(i in 0..120){val current=DayNightLighting.nightWeight(-i/10.0);assertTrue(current>=previous);previous=current}
  assertEquals(1.0,previous,1e-8)
 }
 @Test fun cityFocusHoldsFiveSecondsThenRestoresAndSpins(){
  val (m,arrival)=focused();val held=m.presentation
  m.tick(.01,arrival+4.99);assertTrue(m.focusLocked);assertEquals(held,m.presentation)
  m.tick(.01,arrival+5.01);assertFalse(m.focusLocked);assertTrue(m.restoringTilt)
  val returnError=m.tiltErrorDegrees
  repeat(100){m.tick(.01,arrival+5.02+it*.01)}
  assertTrue(m.restoringTilt);assertTrue(m.tiltErrorDegrees>returnError*.85)
  repeat(500){m.tick(.01,arrival+6.02+it*.01)}
  assertTrue(m.tiltErrorDegrees<.0001);assertTrue(m.idleDegrees>0)
 }
 @Test fun uiActivityRenewsTheFocusDwell(){
  val (m,arrival)=focused();m.noteUiActivity(arrival+4.0)
  m.tick(.01,arrival+8.99);assertTrue(m.focusLocked)
  m.tick(.01,arrival+9.01);assertFalse(m.focusLocked)
 }
 @Test fun pinchTakesOverFocusAndCannotTimeOutWhileHeld(){
  val (m,arrival)=focused();m.begin(arrival+1)
  val held=m.presentation;m.tick(.05,arrival+50)
  assertTrue(m.dragging);assertEquals(held,m.presentation)
  m.end(arrival+50);m.tick(.05,arrival+52.99);assertEquals(held,m.presentation)
  repeat(600){m.tick(.01,arrival+53.01+it*.01)};assertTrue(m.tiltErrorDegrees<.0001)
 }
 @Test fun labelsAlwaysExtendRightAcrossTheWholeGlobe(){
  val c=GlobeVector3(0.0,0.0,-60.0)
  for(x in -24..24){val tip=c+GlobeVector3(x.toDouble(),2.0,20.0);val p=CityLabelLayout.place(c,tip,.0288)
   assertTrue(p.labelOnRight);assertEquals(8.7,p.center.x-tip.x,1e-8);assertTrue(p.elbow.x>tip.x)
   assertTrue(p.center.z>c.z+24.0)
  }
 }
 @Test fun toolbarMenusAreExclusiveAndHiddenByDefault(){
  val s=ClockState();assertFalse(s.citiesOpen);assertFalse(s.timeToolsOpen)
  s.toggleCities();assertTrue(s.citiesOpen);assertFalse(s.timeToolsOpen)
  s.toggleTimeTools();assertFalse(s.citiesOpen);assertTrue(s.timeToolsOpen)
  s.toggleTimeTools();assertFalse(s.timeToolsOpen)
 }
}
