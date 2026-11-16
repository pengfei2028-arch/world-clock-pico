package com.wupengfei.worldclock.content
import org.junit.Assert.*
import org.junit.Test
class V37RegressionTest {
 @Test fun cityLightFadeIsContinuousWithoutADeadBandAfterSunset() {
  var previous=0.0
  for(step in 0..1200){
   val weight=DayNightLighting.nightWeight(-step/100.0)
   assertTrue("monotonic fade",weight+1e-9>=previous)
   assertTrue("no brightness jump",weight-previous<.005)
   assertTrue("bounded fade",weight in 0.0..1.0)
   previous=weight
  }
  assertEquals(1.0,previous,1e-8)
  assertTrue(DayNightLighting.nightWeight(-.5)>0.0)
  assertTrue(DayNightLighting.nightWeight(-2.0)>DayNightLighting.nightWeight(-.5))
 }
 @Test fun sunlitCitiesRemainUnlitWhileDeepNightKeepsFullEmission() {
  assertEquals(0.0,DayNightLighting.nightWeight(4.0),1e-8)
  assertEquals(0.0,DayNightLighting.nightWeight(0.0),1e-8)
  assertTrue(DayNightLighting.nightWeight(-6.0) in .45.. .55)
  assertEquals(1.0,DayNightLighting.nightWeight(-15.0),1e-8)
 }
}
