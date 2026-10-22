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
class TemperatureIntegrationTest {
 @Test fun realHourlyTemperatureAndRestore(){
  val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
  c.startActivity(Intent(c,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  fun waitFor(label:String,timeout:Long=30000,predicate:()->Boolean){val until=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<until){if(predicate())return;SystemClock.sleep(50)};fail(label)}
  waitFor("Stage ready",60000){TemperatureTestAccess.state?.let{it.ready&&it.temperature.manifest!=null}==true}
  val s=checkNotNull(TemperatureTestAccess.state);val m=checkNotNull(s.temperature.manifest)
  assertTrue(m.frames.size>=25)
  var original:java.time.Instant?=null
  val cityIds=s.cities.map{it.id}
  i.runOnMainSync{s.setOffset(120f);original=s.clockInstant;s.switchMode(EarthMode.TEMPERATURE);s.temperature.select(2)}
  fun loaded(){waitFor("Requested frame committed"){s.temperature.displayed==s.temperature.requested&&!s.temperature.loading}}
  loaded();SystemClock.sleep(1200)
  val visual=InstrumentationRegistry.getArguments().getString("visualReview")=="true"
  if(visual)i.runOnMainSync{s.onTimeScrub?.invoke(true)}
  assertNotNull(s.temperature.grid?.sample(39.9042,116.4074))
  assertEquals(s.temperature.displayed!!.validTime,s.instant)
  Log.i("TemperatureAcceptance","HOURLY_VISIBLE; valid="+s.temperature.displayed?.validTime)
  if(visual)SystemClock.sleep(30000)
  i.runOnMainSync{s.temperature.strength=0f};SystemClock.sleep(if(visual)15000 else 1000)
  i.runOnMainSync{s.temperature.strength=.55f;if(visual)s.onTimeScrub?.invoke(false)}
  val times=mutableListOf<Long>();val nativeBytes=mutableListOf<Long>();var maxTextures=0
  repeat(2){round->
   for(index in m.frames.indices){
    val start=SystemClock.elapsedRealtime()
    i.runOnMainSync{s.temperature.select(index)};loaded()
    assertEquals(m.frames[index].validTime,s.instant)
    assertNotNull(s.temperature.grid?.sample(-33.86,151.21))
    maxTextures=maxOf(maxTextures,TemperatureTestAccess.renderer!!.liveTextureCount());assertTrue(maxTextures<=3)
    times.add(SystemClock.elapsedRealtime()-start);SystemClock.sleep(100)
   }
   System.gc();SystemClock.sleep(500);nativeBytes.add(android.os.Debug.getNativeHeapAllocatedSize())
  }
  // Exercise the same timeline hold/release entry points used by spatial dragging.
  i.runOnMainSync{s.beginTimeDrag();s.dragTimeBy(-60f);s.finishTimeDrag()};loaded()
  val tempIndex=s.temperature.index
  repeat(3){i.runOnMainSync{s.switchMode(EarthMode.WORLD_TIME)};SystemClock.sleep(300)
   assertEquals(original,s.clockInstant);assertEquals(original,s.instant)
   i.runOnMainSync{s.switchMode(EarthMode.TEMPERATURE)};loaded();assertEquals(tempIndex,s.temperature.index)}
  i.runOnMainSync{s.temperature.select(2)};loaded()
  i.runOnMainSync{s.switchMode(EarthMode.WORLD_TIME)};SystemClock.sleep(2500)
  if(visual){Log.i("TemperatureAcceptance","WORLD_VISUAL_REVIEW");SystemClock.sleep(15000)}
  assertEquals(cityIds,s.cities.map{it.id});assertEquals(original,s.clockInstant)
  Log.i("TemperatureAcceptance","PASS; switches="+times.size+"; medianMs="+times.sorted()[times.size/2]+"; maxMs="+times.max()+"; maxLiveTextures="+maxTextures+"; nativeHeap="+nativeBytes+"; worldTimeAndCitiesRestored=true")
 }
}
