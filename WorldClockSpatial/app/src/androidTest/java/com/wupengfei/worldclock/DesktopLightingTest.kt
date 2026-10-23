package com.wupengfei.worldclock

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pico.spatial.core.container.SpaceState
import com.pico.spatial.core.container.getSelfSpaceState
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.math.Color4
import com.wupengfei.worldclock.content.DesktopRenderTestAccess as Probe
import com.wupengfei.worldclock.content.WorldClockSession as Session
import com.wupengfei.worldclock.platform.LaunchActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Paired XR captures are taken externally at each marker, with pose and UTC frozen. */
@RunWith(AndroidJUnit4::class)
class DesktopLightingTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
    private fun awaitReady(predicate:()->Boolean) {
        repeat(600){var ready=false;main{ready=predicate()};if(ready)return;SystemClock.sleep(100)}
        fail("Desktop lighting audit timed out")
    }
    @Test fun isolateExternalLightingAtMaximumSize() {
        val context=instrumentation.targetContext
        context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitReady{Session.clock.ready&&Session.stageNavigator!=null}
        val fixed=java.time.Instant.parse("2026-10-22T07:00:00Z")
        main{Session.clock.previewBase=fixed;Session.clock.offsetMinutes=0f;Session.collapse(requireNotNull(Session.stageNavigator))}
        awaitReady{Session.desktopResources&&!Session.busy&&!Session.stageMounted}
        val nodes=mutableListOf<Entity>()
        var light:Entity?=null
        fun setIbl(scale:Float)=nodes.forEach{it.components[EnvironmentLightingSettingsComponent::class.java]!!.scale=scale}
        fun capturePhase(name:String){
            SystemClock.sleep(2000)
            Log.i("DesktopLightingAudit",name)
            if(InstrumentationRegistry.getArguments().getString("awaitCapture")=="true"){
                val ack=java.io.File(context.getExternalFilesDir(null),"capture-$name.done")
                val deadline=SystemClock.elapsedRealtime()+90000
                while(!ack.exists()&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100)
                assertTrue("XR capture acknowledgement missing: $name",ack.exists());ack.delete()
            }else SystemClock.sleep(10000)
        }
        try {
            main{
                assertEquals(SpaceState.SHARED_SPACE,context.getSelfSpaceState())
                Session.setDesktopDiameter(.35f);Probe.freezeMotion=true
                fun collect(e:Entity){nodes.add(e);e.getChildren().forEach{collect(it)}}
                collect(requireNotNull(Probe.earth))
                nodes.forEach{assertEquals(0f,it.components[EnvironmentLightingSettingsComponent::class.java]!!.scale,0f)}
                assertEquals(fixed,Session.clock.instant)
            }
            capturePhase("BASELINE")
            main{setIbl(64f)}
            capturePhase("IBL64")
            main{
                setIbl(0f)
                light=Entity().apply{components.set(DirectionalLightComponent(Color4(1f,0f,0f,1f),100000f,false))}
                requireNotNull(Probe.earth).addChild(requireNotNull(light))
            }
            capturePhase("RED_LIGHT")
            main{light?.destroy();light=null;setIbl(0f)}
            capturePhase("RESTORED")
            val seconds=InstrumentationRegistry.getArguments().getString("inspectionSeconds")?.toLongOrNull()?:0L
            Log.i("DesktopLightingAudit","INSPECT maximum-size controls; all external lights reset")
            SystemClock.sleep(seconds*1000)
        }finally{
            main{light?.destroy();if(Probe.earth!=null)setIbl(0f);Probe.freezeMotion=false;Session.clock.previewBase=null;Session.clock.offsetMinutes=0f}
        }
    }
}
