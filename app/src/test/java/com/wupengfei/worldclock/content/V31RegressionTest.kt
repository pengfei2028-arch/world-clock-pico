package com.wupengfei.worldclock.content
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import kotlin.math.*
class V31RegressionTest {
 @Test fun beijingAtTwoPMIsDayAndTwoAMIsNight(){
  val afternoon=ZonedDateTime.of(2026,10,21,14,0,0,0,ZoneId.of("Asia/Shanghai")).toInstant()
  assertEquals(Instant.parse("2026-10-21T06:00:00Z"),afternoon)
  val sun=SolarPosition.at(afternoon)
  assertTrue(sun.altitude(39.9042,116.4074)>30.0)
  assertEquals("白昼",sun.daylight(39.9042,116.4074))
  assertEquals("夜晚",SolarPosition.at(afternoon.minusSeconds(43200)).daylight(39.9042,116.4074))
  println("Beijing 14:00 altitude="+sun.altitude(39.9042,116.4074)+" subsolar="+sun.longitude)
 }
 @Test fun labelPlaneCannotIntersectEarthAtAnyCityAngle(){
  val c=GlobeVector3(0.0,-4.1956,-60.0)
  for(lat in -90..90 step 10)for(lon in -180..180 step 10){
   val tip=c+GlobeGeography.direction(lat.toDouble(),lon.toDouble())*25.8
   val p=CityLabelLayout.place(c,tip,0.036)
   for(dx in listOf(-p.halfWidth,0.0,p.halfWidth))for(dy in listOf(-p.halfHeight,0.0,p.halfHeight)){
    val d=p.center+GlobeVector3(dx,dy,0.0)-c
    assertTrue(d.dot(d)>24.0*24.0)
   }
  }
 }
 @Test fun manualTakeoverCancelsPendingCityFocusRequest(){
  val state=ClockState();state.selectedId="1816670";state.focusRequest=3
  state.manualInteraction();assertNull(state.selectedId);assertEquals(4,state.focusRequest)
  val m=GlobeMotion();m.focusTo(GlobeQuaternion.axisAngle(GlobeVector3.X,45.0))
  m.begin(1.0);m.drag(100.0,80.0,1.05);m.end(1.1)
  repeat(1000){m.tick(.01,1.11+it*.01)}
  assertFalse(m.focusLocked);assertTrue(m.idleDegrees>0.0);assertTrue(m.tiltErrorDegrees<.0001)
 }
 @Test fun previewTimeStaysFixedWhileManualReleaseRestartsPresentation(){
  val state=ClockState();state.now=Instant.parse("2026-10-21T06:00:00Z");state.setOffset(120f)
  val preview=state.instant;state.manualInteraction()
  val m=GlobeMotion();m.begin(0.0);m.drag(40.0,30.0,.05);m.end(.1)
  repeat(1000){m.tick(.01,.11+it*.01)}
  assertTrue(state.preview);assertEquals(preview,state.instant);assertTrue(m.idleDegrees>0.0)
 }
}
