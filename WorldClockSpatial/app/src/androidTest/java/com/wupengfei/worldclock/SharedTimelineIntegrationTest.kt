package com.wupengfei.worldclock
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wupengfei.worldclock.platform.LaunchActivity
import com.wupengfei.worldclock.content.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedTimelineIntegrationTest {
 // PICO ECS getters are main-thread APIs too, not only setters.
 private fun <T> onMain(block:()->T):T {
  var result:Result<T>?=null
  InstrumentationRegistry.getInstrumentation().runOnMainSync { result=runCatching(block) }
  return checkNotNull(result).getOrThrow()
 }
 @Test fun sharedRulerFadeAndRestore(){
  val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
  c.startActivity(Intent(c,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  fun waitFor(label:String,timeout:Long=30000,predicate:()->Boolean){val end=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<end){if(onMain(predicate))return;SystemClock.sleep(25)};fail(label)}
  waitFor("Stage ready",60000){TemperatureTestAccess.state?.let{it.ready&&it.temperature.manifest!=null}==true}
  val s=checkNotNull(TemperatureTestAccess.state);val renderer=checkNotNull(TemperatureTestAccess.renderer)
  fun shellOpacity()=onMain { renderer.shellOpacity() }
  fun surfaceIntact()=onMain { renderer.originalSurfaceIntact() }
  fun loaded(){waitFor("Requested frame"){s.temperature.displayed==s.temperature.requested&&!s.temperature.loading}}
  fun capture(name:String){
   val bitmap=i.uiAutomation.takeScreenshot()?:return
   // This is Android's default display, which may omit the PICO XR compositor.
   // Keep diagnostic captures separate from the host's official PICO capture.
   try{java.io.File(c.getExternalFilesDir(null),name+"-android.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}finally{bitmap.recycle()}
  }
  val cities=s.cities.map{it.id}
  // Other integration cases may leave the shared session mid-fade or in
  // temperature mode. Establish the world baseline before measuring fade-in.
  i.runOnMainSync{s.switchMode(EarthMode.WORLD_TIME)}
  waitFor("Initial world mode fully visible",5000){renderer.shellOpacity()==0f}
  i.runOnMainSync{s.setOffset(120f)};val world=s.clockInstant
  SystemClock.sleep(1000);capture("v57-world")
  i.runOnMainSync{s.switchMode(EarthMode.TEMPERATURE);s.temperature.select(6)}
  // Observe while loading too: a cached frame can begin fading before the
  // newly requested frame finishes preparing, and that is valid behavior.
  val samples=mutableListOf<Float>()
  waitFor("First requested frame"){
   samples.add(renderer.shellOpacity())
   s.temperature.displayed==s.temperature.requested&&!s.temperature.loading
  }
  repeat(10){samples.add(shellOpacity());SystemClock.sleep(70)}
  assertTrue("Visible intermediate fade",samples.any{it>.01f&&it<.99f})
  waitFor("Shell fade completed",5000){renderer.shellOpacity()>=.999f}
  assertEquals(1f,shellOpacity(),.001f);assertEquals(1f,s.temperature.reveal,.001f)
  assertTrue(surfaceIntact());assertEquals(s.temperature.displayed!!.validTime,s.instant)
  assertNotNull(s.temperature.grid?.sample(39.9042,116.4074));capture("v57-temperature")
  val old=s.temperature.index
  i.runOnMainSync{s.beginTimeDrag();s.dragTimeBy(60f);s.finishTimeDrag()};loaded()
  assertEquals(old+1,s.temperature.index);assertNull(s.temperature.scrubTime)
  val tempIndex=s.temperature.index;val tempOrigin=s.rulerOrigin
  // Interrupt both directions; opacity must continue from the current frame.
  repeat(3){i.runOnMainSync{s.switchMode(EarthMode.WORLD_TIME)}
   waitFor("Fade-out reaches an interruptible frame",2000){renderer.shellOpacity() in .2f.. .85f}
   val partial=shellOpacity();assertTrue("Fade-out should be partial: $partial",partial>0f&&partial<1f)
   i.runOnMainSync{s.switchMode(EarthMode.TEMPERATURE)}
   // Re-entry can prepare a cached forecast frame asynchronously. Observe the
   // actual reversal instead of assuming a fixed 180ms wall-clock sleep is a frame barrier.
   waitFor("Interrupted fade should brighten from $partial",2000){renderer.shellOpacity()>partial+.01f}
  }
  waitFor("Interrupted fade completed",5000){renderer.shellOpacity()>=.999f};assertEquals(1f,shellOpacity(),.001f)
  assertEquals(tempIndex,s.temperature.index);assertEquals(tempOrigin,s.rulerOrigin)
  i.runOnMainSync{s.switchMode(EarthMode.WORLD_TIME)};waitFor("World mode fade completed",5000){renderer.shellOpacity()==0f}
  assertEquals(0f,shellOpacity(),.001f);assertEquals(0f,s.temperature.reveal,.001f)
  assertEquals(world,s.instant);assertEquals(cities,s.cities.map{it.id});assertTrue(surfaceIntact())
  assertTrue(renderer.liveTextureCount()<=2);capture("v57-restored")
  Log.i("TemperatureAcceptance","V57 PASS; fadeSamples="+samples+"; textures="+renderer.liveTextureCount()+"; commonSixHourRuler=true; worldTimeRestored=true")
  i.runOnMainSync{s.live();s.switchMode(EarthMode.TEMPERATURE)};loaded();SystemClock.sleep(1000)
 }
}
