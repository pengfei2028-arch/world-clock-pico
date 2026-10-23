package com.wupengfei.worldclock

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.pico.spatial.core.container.getSelfSpaceState
import com.pico.spatial.core.container.SpaceState
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wupengfei.worldclock.content.WorldClockSession as Session
import com.wupengfei.worldclock.platform.LaunchActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesktopSpaceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
    private fun waitFor(label:String, timeout:Long=40000, predicate:()->Boolean) {
        val start=SystemClock.elapsedRealtime()
        while(SystemClock.elapsedRealtime()-start<timeout) {
            var success=false;main{success=predicate()}
            if(success)return
            SystemClock.sleep(80)
        }
        var state="";main{state="stage=${Session.stageMounted}, desktop=${Session.desktopMounted}, heavy=${Session.immersiveResources}, light=${Session.desktopResources}, busy=${Session.busy}, error=${Session.error}"}
        fail("Timed out: $label; $state")
    }
    @Test fun realVolumeAndStageRoundTrip() {
        val context=instrumentation.targetContext
        context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitFor("initial Earth") { Session.clock.ready && Session.stageNavigator!=null }
        val arguments=InstrumentationRegistry.getArguments()
        val cycles=arguments.getString("cycles")?.toIntOrNull()?:6
        val fixed=java.time.Instant.parse("2026-10-22T07:00:00Z")
        var cityIds=listOf<String>()
        main {Session.clock.previewBase=fixed;Session.clock.offsetMinutes=0f;cityIds=Session.clock.cities.map{it.id}}
        fun sample(label:String){
            System.gc();SystemClock.sleep(1000)
            val dump=instrumentation.uiAutomation.executeShellCommand("dumpsys meminfo com.wupengfei.worldclock")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(dump).bufferedReader().use{reader->
                java.io.File(context.getExternalFilesDir(null),"desktop-memory-$label.txt").writeText(reader.readText())
            }
        }
        sample("initial-full")
        repeat(cycles) { cycle ->
            main { Session.collapse(requireNotNull(Session.stageNavigator));Session.collapse(requireNotNull(Session.stageNavigator)) }
            waitFor("shared volume") { Session.desktopResources&&!Session.stageMounted&&!Session.busy }
            main { assertFalse(Session.immersiveResources); assertTrue(Session.desktopMounted);assertEquals(SpaceState.SHARED_SPACE,context.getSelfSpaceState()) }
            var before=0L; main{before=Session.desktopFrames}
            SystemClock.sleep(2000)
            main { assertTrue("Volume must animate",Session.desktopFrames>before+20) }
            main {assertEquals(fixed,Session.clock.instant);assertEquals(cityIds,Session.clock.cities.map{it.id})}
            sample("$cycle-shared")
            Log.i("DesktopAcceptance","V58 cycle=$cycle SHARED StageDisposed=true noHeavy=true frames=${Session.desktopFrames}")
            // Leave the final shared volume on screen when requested for visual inspection.
            if(cycle==cycles-1&&arguments.getString("leaveDesktop")=="true"){SystemClock.sleep((arguments.getString("inspectionSeconds")?.toLongOrNull()?:180L)*1000);return}
            if(cycle==0){
                main {
                    Session.resizeDesktop(10f);assertEquals(.8f,Session.diameter,.0001f)
                    Session.resizeDesktop(-10f);assertEquals(.18f,Session.diameter,.0001f)
                    Session.setDesktopDiameter(.5f);assertEquals(.5f,Session.diameter,.0001f)
                }
                main { Session.rejectNextExpandForTest=true;Session.expand(requireNotNull(Session.desktopNavigator)) }
                waitFor("denied expansion rollback") {Session.desktopResources&&!Session.stageMounted&&!Session.busy&&Session.error.isNotEmpty()}
                main{assertFalse(Session.immersiveResources);assertEquals(SpaceState.SHARED_SPACE,context.getSelfSpaceState())}
                Log.i("DesktopAcceptance","V58 denied Stage recovered to desktop")
            }
            main { Session.expand(requireNotNull(Session.desktopNavigator));Session.expand(requireNotNull(Session.desktopNavigator)) }
            waitFor("Full Space restored") { Session.clock.ready&&Session.stageMounted&&!Session.busy }
            main { assertFalse(Session.desktopResources);assertTrue(Session.immersiveResources);assertEquals(SpaceState.FULL_SPACE,context.getSelfSpaceState());assertEquals(fixed,Session.clock.instant);assertEquals(cityIds,Session.clock.cities.map{it.id}) }
            SystemClock.sleep(1500)
            sample("$cycle-full")
            Log.i("DesktopAcceptance","V58 cycle=$cycle FULL restored")
        }
    }
    @Test fun temperatureSelectionSurvivesBothContainers() {
        val c=instrumentation.targetContext
        c.startActivity(Intent(c,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitFor("initial temperature capability"){Session.clock.ready&&Session.clock.temperature.manifest!=null}
        main{Session.clock.switchMode(com.wupengfei.worldclock.content.EarthMode.TEMPERATURE);Session.clock.temperature.select(6)}
        waitFor("temperature loaded"){!Session.clock.temperature.loading&&Session.clock.temperature.displayed==Session.clock.temperature.requested}
        var utc=Session.clock.instant
        main{utc=Session.clock.instant;Session.collapse(requireNotNull(Session.stageNavigator))}
        waitFor("temperature to desktop"){Session.desktopResources&&!Session.busy&&!Session.stageMounted}
        main{assertEquals(utc,Session.clock.instant);assertEquals(6,Session.clock.temperature.index);Session.expand(requireNotNull(Session.desktopNavigator))}
        waitFor("restored temperature"){Session.clock.ready&&!Session.busy&&!Session.clock.temperature.loading&&com.wupengfei.worldclock.content.TemperatureTestAccess.renderer!=null}
        SystemClock.sleep(1200)
        main{
            assertEquals(utc,Session.clock.instant);assertEquals(6,Session.clock.temperature.index)
            val renderer=requireNotNull(com.wupengfei.worldclock.content.TemperatureTestAccess.renderer)
            assertTrue(renderer.originalSurfaceIntact());assertEquals(1f,renderer.shellOpacity(),.001f)
            Session.clock.switchMode(com.wupengfei.worldclock.content.EarthMode.WORLD_TIME)
        }
        Log.i("DesktopAcceptance","V58 temperature UTC/index/shell and original 8K surface restored")
    }
    @Test fun explicitCloseRemovesDesktopAndTask() {
        val c=instrumentation.targetContext
        c.startActivity(Intent(c,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitFor("Earth before close"){Session.clock.ready&&Session.stageNavigator!=null}
        main {Session.collapse(requireNotNull(Session.stageNavigator))}
        waitFor("desktop before close"){Session.desktopResources&&!Session.stageMounted&&!Session.busy}
        main {Session.closeSoftware(requireNotNull(Session.desktopNavigator),null)}
        waitFor("all app containers closed"){!Session.stageMounted&&!Session.desktopMounted&&!Session.immersiveResources&&!Session.desktopResources&&!Session.busy}
        val manager=c.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        waitFor("no retained app task"){manager.appTasks.isEmpty()}
        Log.i("DesktopAcceptance","V58 explicit close released both scenes and removed app tasks")
    }
}
