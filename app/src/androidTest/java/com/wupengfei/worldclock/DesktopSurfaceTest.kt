package com.wupengfei.worldclock

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pico.spatial.core.container.SpaceState
import com.pico.spatial.core.container.getSelfSpaceState
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.Vector3
import com.wupengfei.worldclock.content.*
import com.wupengfei.worldclock.content.DesktopRenderTestAccess as Probe
import com.wupengfei.worldclock.content.WorldClockSession as Session
import com.wupengfei.worldclock.platform.LaunchActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real scene/collider/resource integration. Injected hand samples do not verify physical tracking. */
@RunWith(AndroidJUnit4::class)
class DesktopSurfaceTest {
    @Test fun cloudsGesturesAndResourceRelease() {
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        fun main(b:()->Unit)=i.runOnMainSync(b)
        fun now()=SystemClock.elapsedRealtimeNanos()/1e9
        fun waitFor(label:String,p:()->Boolean){
            repeat(600){var ok=false;main{ok=p()};if(ok)return;SystemClock.sleep(100)}
            fail("Timeout $label")
        }
        fun capture(label:String){
            SystemClock.sleep(1500);Log.i("DesktopSurfaceAudit",label)
            if(InstrumentationRegistry.getArguments().getString("awaitCapture")!="true")return
            val ack=java.io.File(c.getExternalFilesDir(null),"surface-$label.done")
            repeat(900){if(ack.exists()){ack.delete();return};SystemClock.sleep(100)}
            fail("Missing XR capture $label")
        }
        c.startActivity(Intent(c,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitFor("Full ready"){Session.clock.ready&&Session.stageNavigator!=null}
        val beforeResources=mutableListOf<Resource>()
        var surface:ShaderGraphMaterial?=null
        try {
            main{Session.clock.previewBase=java.time.Instant.parse("2026-10-22T19:00:00Z");Session.clock.offsetMinutes=0f;Session.collapse(requireNotNull(Session.stageNavigator))}
            waitFor("Shared ready"){Session.desktopResources&&!Session.busy&&!Session.stageMounted}
            main{
                assertEquals(SpaceState.SHARED_SPACE,c.getSelfSpaceState())
                assertFalse(Session.immersiveResources)
                val earth=requireNotNull(Probe.earth)
                assertNotNull(earth.components[CollisionComponent::class.java])
                assertNotNull(earth.components[InteractableComponent::class.java])
                fun collect(e:Entity){
                    e.components[ModelComponent::class.java]?.let{m->
                        beforeResources.add(m.mesh)
                        m.materials.forEach{
                            val s=it as ShaderGraphMaterial;surface=s;beforeResources.add(s)
                            assertEquals(BlendingMode.OPAQUE,s.getBlendingMode())
                            assertEquals(1f,s.getParameter<Float>("opacity"),0f)
                            for(name in listOf("Day_file","Night_file","Detail_file"))
                                beforeResources.add(s.getParameter(name,TextureResource::class.java))
                        }
                    };e.getChildren().forEach{collect(it)}
                };collect(earth)
            }
            waitFor("Clouds visible"){Probe.cloudOpacity>.64f}
            main{Probe.freezeMotion=true;Session.setDesktopDiameter(.8f)}
            capture("DAY_CLOUDS")
            main{Session.clock.previewBase=java.time.Instant.parse("2026-10-22T07:00:00Z")}
            capture("NIGHT_CLOUDS")
            if(InstrumentationRegistry.getArguments().getString("awaitCapture")=="true") {
                main{surface!!.setParameter("GlossStrength_outhigh",0f);surface!!.setParameter("GlossStrength_outlow",0f)}
                try{capture("GLINT_OFF")}finally{main{surface!!.setParameter("GlossStrength_outhigh",.70f);surface!!.setParameter("GlossStrength_outlow",.012f)}}
            }
            main{Probe.freezeMotion=false}
            val input=Probe.input!!
            var held=0.0;var pose=GlobeQuaternion.IDENTITY
            main{input.update(91,true,true,0.0,0.0,now());held=Session.motion.idleDegrees;pose=Session.motion.presentation}
            SystemClock.sleep(700)
            main{assertEquals(held,Session.motion.idleDegrees,1e-8);assertEquals(0f,Probe.cloudOpacity,.001f)}
            repeat(40){step->SystemClock.sleep(20);main{input.update(91,false,true,step*.003,step*.001,now())}}
            main{assertTrue("Injected drag must rotate globe",pose.angleTo(Session.motion.presentation)>5.0);input.update(91,false,false,.117,.039,now());held=Session.motion.idleDegrees}
            SystemClock.sleep(2100)
            main{assertEquals("Release dwell",held,Session.motion.idleDegrees,1e-8);assertEquals(0f,Probe.cloudOpacity,.001f)}
            waitFor("Clouds return with motion"){Probe.cloudOpacity>.64f&&Session.motion.idleDegrees!=held}
            main{
                val s=surface!!;val a=s.getParameter("SunDot_in2",Vector3::class.java);val b=s.getParameter("HalfSum_in2",Vector3::class.java)
                assertEquals(a,b)
                Session.expand(requireNotNull(Session.desktopNavigator))
            }
            waitFor("Full restored"){Session.clock.ready&&!Session.busy&&Session.stageMounted&&!Session.desktopResources}
            main{
                assertEquals(SpaceState.FULL_SPACE,c.getSelfSpaceState())
                assertNull(Probe.input);assertFalse(Session.motion.dragging)
                beforeResources.forEach{assertFalse("Released shared resource $it",it.valid)}
            }
            Log.i("DesktopSurfaceAudit","PASS shared collider, gesture pause/drag/dwell, cloud fade, solar uniforms, opaque material, native resources released")
        }finally{main{Probe.freezeMotion=false;Probe.input?.cancel(now());Session.clock.previewBase=null;Session.clock.offsetMinutes=0f}}
    }
}
