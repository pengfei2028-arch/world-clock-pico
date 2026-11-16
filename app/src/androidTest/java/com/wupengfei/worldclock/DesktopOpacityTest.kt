package com.wupengfei.worldclock

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.Color4
import com.wupengfei.worldclock.content.DesktopRenderTestAccess as Probe
import com.wupengfei.worldclock.content.WorldClockSession as Session
import com.wupengfei.worldclock.platform.LaunchActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesktopOpacityTest {
    @Test fun diagnoseOpaqueComposition() {
        org.junit.Assume.assumeTrue("Manual XR diagnostic requires external capture acknowledgements",
            InstrumentationRegistry.getArguments().getString("diagnoseOpacity")=="true")
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        fun main(b:()->Unit)=i.runOnMainSync(b)
        fun waitFor(p:()->Boolean){repeat(600){var ok=false;main{ok=p()};if(ok)return;SystemClock.sleep(100)};fail("Timeout")}
        fun phase(n:String){
            SystemClock.sleep(1200);Log.i("DesktopOpacityAudit",n)
            val ack=java.io.File(c.getExternalFilesDir(null),"opacity-$n.done")
            repeat(900){if(ack.exists()){ack.delete();return};SystemClock.sleep(100)}
            fail("Missing capture $n")
        }
        c.startActivity(Intent(c,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitFor{Session.clock.ready&&Session.stageNavigator!=null}
        main{Session.clock.previewBase=java.time.Instant.parse("2026-10-22T07:00:00Z");Session.collapse(requireNotNull(Session.stageNavigator))}
        waitFor{Session.desktopResources&&!Session.busy}
        var model:ModelComponent?=null;var graph:ShaderGraphMaterial?=null;var plain:UnlitMaterial?=null
        try {
            main{
                Probe.freezeMotion=true;Session.setDesktopDiameter(.35f)
                fun collect(e:Entity){
                    Log.i("DesktopOpacityAudit","entity=${e.getName()} opacity=${e.components[OpacityControllerComponent::class.java]?.opacity}")
                    e.components[ModelComponent::class.java]?.let{model=it;graph=it.materials[0] as ShaderGraphMaterial;graph!!.toGlobal()}
                    e.getChildren().forEach{collect(it)}
                };collect(requireNotNull(Probe.earth))
                var ancestor=Probe.earth?.getParent()
                while(ancestor!=null){
                    Log.i("DesktopOpacityAudit","ancestor=${ancestor.getName()} opacity=${ancestor.components[OpacityControllerComponent::class.java]?.opacity}")
                    ancestor=ancestor.getParent()
                }
            }
            // V60 baseline and controller removal were already captured; repeat only when requested.
            if(InstrumentationRegistry.getArguments().getString("repeatBaseline")=="true")phase("ORIGINAL")
            main{Probe.skipOpacityUpdate=true;Probe.earth!!.components.remove(OpacityControllerComponent::class.java)}
            if(InstrumentationRegistry.getArguments().getString("repeatBaseline")=="true")phase("NO_CONTROLLER")
            main{
                plain=UnlitMaterial.create(BlendingMode.OPAQUE).apply{setBaseColor(Color4(0f,0f,0f,1f));setApplyToneMapping(false)}
                model!!.materials[0]=plain!!
            }
            phase("NATIVE_BLACK")
            main{model!!.materials[0]=graph!!;graph!!.setParameter("applyPostProcessToneMap",true)}
            phase("GRAPH_TONEMAP")
            main{graph!!.setParameter("applyPostProcessToneMap",false)}
            phase("GRAPH_RESTORED")
        }finally{main{
            if(Probe.earth!=null){model?.materials?.set(0,graph!!);graph?.setParameter("applyPostProcessToneMap",false);Probe.earth!!.components.set(OpacityControllerComponent(1f))}
            plain?.let{if(it.valid)it.close()};Probe.freezeMotion=false;Probe.skipOpacityUpdate=false;Session.clock.previewBase=null
        }}
    }
}
