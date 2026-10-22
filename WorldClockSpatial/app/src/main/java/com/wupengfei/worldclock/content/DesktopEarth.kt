package com.wupengfei.worldclock.content

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.*
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.layout.depth
import com.pico.spatial.ui.platform.LocalSpatialContainerStateManager
import com.pico.spatial.ui.platform.meters
import com.pico.spatial.ui.platform.containers.*
import kotlinx.coroutines.*
import kotlin.math.*

@Composable internal fun ImmersiveHost() {
    val navigator=LocalSpatialNavigator.current
    val context=LocalContext.current.applicationContext
    DisposableEffect(Unit) {
        WorldClockSession.applicationContext=context
        WorldClockSession.stageMounted=true;WorldClockSession.stageNavigator=navigator
        onDispose {
            WorldClockSession.stageMounted=false;WorldClockSession.stageNavigator=null
            Log.i("WorldClockSpace","V58 Stage composition disposed")
        }
    }
    if(WorldClockSession.immersiveRendering)HomeStage()
}

@Composable internal fun WindowContainerScope.DesktopEarth() {
    val navigator=LocalSpatialNavigator.current
    val activity=LocalContext.current as? android.app.Activity
    DisposableEffect(Unit) {
        WorldClockSession.desktopMounted=true;WorldClockSession.desktopNavigator=navigator
        onDispose {
            WorldClockSession.desktopMounted=false;WorldClockSession.desktopNavigator=null
            WorldClockSession.desktopClosed()
            Log.i("WorldClockSpace","V58 desktop container disposed")
        }
    }
    // Physical envelope includes 35cm Earth plus bottom controls; system handle moves the volume.
    val width=.45f.meters;val height=.50f.meters;val depth=.45f.meters
    val button=.055f.meters;val labelSize=.022f.meters.value.toInt()
    ClockTheme {
        Box(Modifier.windowConstraints(width,height,depth).fillMaxSize().depth(depth)) {
            if(WorldClockSession.desktopRendering)DesktopScene()
            Column(Modifier.align(Alignment.BottomCenter).padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                if(WorldClockSession.error.isNotEmpty())Text(WorldClockSession.error,style=uiText(12))
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    GlassAction({WorldClockSession.resizeDesktop(-.025f)},Modifier.size(button),!WorldClockSession.busy&&WorldClockSession.diameter>.1801f){Text("−",style=uiText(labelSize))}
                    GlassAction({WorldClockSession.expand(navigator)},Modifier.width(.14f.meters).height(button),!WorldClockSession.busy){Text(if(WorldClockSession.busy)"切换中…" else "展开",style=uiText(labelSize))}
                    GlassAction({WorldClockSession.resizeDesktop(.025f)},Modifier.size(button),!WorldClockSession.busy&&WorldClockSession.diameter<.3499f){Text("+",style=uiText(labelSize))}
                }
                if(WorldClockSession.error.isNotEmpty())GlassAction({WorldClockSession.closeSoftware(navigator,activity)},Modifier.height(32.dp),!WorldClockSession.busy){Text("关闭软件",style=uiText(13))}
            }
        }
    }
}

private class DesktopResources : AutoCloseable {
    var bundle:AssetBundle?=null
    var earth:Entity?=null
    var frames:Cancellable?=null
    val materials=linkedSetOf<Material>()
    val textures=linkedSetOf<TextureResource>()
    val meshes=linkedSetOf<MeshResource>()
    var closed=false
    override fun close() {
        if(closed)return
        closed=true;frames?.cancel();frames=null
        earth?.destroy();earth=null
        // The system window stays alive/minimized to retain placement. Explicitly close
        // its scene-owned resources instead of waiting for the window's destruction.
        materials.forEach{if(it.valid)it.close()};materials.clear()
        textures.forEach{if(it.valid)it.close()};textures.clear()
        meshes.forEach{if(it.valid)it.close()};meshes.clear()
        bundle?.close();bundle=null
        WorldClockSession.desktopResources=false
        Log.i("WorldClockSpace","V58 desktop resources released; no live desktop bundle")
    }
}

/** Same geography, UTC and presentation state; only this renderer's asset budget differs. */
@Composable private fun DesktopScene() {
    val container=LocalSpatialContainerStateManager.current
    // Focus is deliberately excluded. isOnstage also becomes false for partial
    // occlusion: using it would freeze the still-visible part beside another app.
    val visible=rememberUpdatedState(container.isSighted.value)
    val resources=remember{DesktopResources()}
    DisposableEffect(Unit){onDispose{resources.close()}}
    LaunchedEffect(Unit) {
        while(isActive){WorldClockSession.clock.now=ClockTimeSource.read().instant;delay(1000)}
    }
    SpatialView(Modifier.fillMaxSize().depth(.45f.meters),initial={content,_->
        if(resources.closed)return@SpatialView
        try {
            val bundle=AssetBundle.load("asset://DesktopEarth.bundle")
            resources.bundle=bundle;check(bundle.valid)
            val earth=bundle.loadModelSuspend("DesktopEarthV58")
            if(resources.closed){earth.destroy();return@SpatialView}
            resources.earth=earth
            val geographic=checkNotNull(findGlobeEntity(earth,"Geographic_Rotation"))
            val transform=checkNotNull(earth.components[TransformComponent::class.java])
            val geographicTransform=checkNotNull(geographic.components[TransformComponent::class.java])
            val authored=geographicTransform.quaternion
            val base=WorldClockSession.geographicBase?:GlobeQuaternion(authored.x.toDouble(),authored.y.toDouble(),authored.z.toDouble(),authored.w.toDouble())
            val materials=mutableListOf<ShaderGraphMaterial>()
            fun bind(e:Entity){
                e.components[ModelComponent::class.java]?.let { model ->
                    resources.meshes.add(model.mesh)
                    model.materials.forEach {
                        val material=it as ShaderGraphMaterial
                        materials.add(material);resources.materials.add(material)
                        resources.textures.add(material.getParameter("Day_file",TextureResource::class.java))
                        resources.textures.add(material.getParameter("Night_file",TextureResource::class.java))
                    }
                }
                e.components.set(EnvironmentLightingSettingsComponent(0f))
                e.getChildren().forEach{bind(it)}
            }
            bind(earth);check(materials.size==1)
            val opacity=OpacityControllerComponent(0f);earth.components.set(opacity)
            // Imported model diameter is 4m. Root placement stays inside the physical volume.
            transform.setPosition(Vector3(0f,.025f,0f))
            content.addEntity(earth)
            var solarInstant=WorldClockSession.clock.instant
            var solar=SolarPosition.at(solarInstant)
            var sampleSeconds=0.0;var sampleFrames=0
            resources.frames=content.subscribe(SceneEvents.Update::class.java){event->
                if(visible.value){
                    val dt=event.deltaTime.toDouble().coerceIn(0.0,.05)
                    val motion=WorldClockSession.motion
                    if(!WorldClockSession.busy)motion.tickDesktop(dt)
                    val p=motion.presentation
                    transform.setQuaternion(Quat(p.x.toFloat(),p.y.toFloat(),p.z.toFloat(),p.w.toFloat()))
                    val scale=WorldClockSession.diameter/4f
                    transform.setScaleVector(Vector3(scale,scale,scale))
                    val q=base*GlobeQuaternion.axisAngle(GlobeVector3.Z,WorldClockSession.entrance.pose.spinDegrees+motion.idleDegrees)
                    geographicTransform.setQuaternion(Quat(q.x.toFloat(),q.y.toFloat(),q.z.toFloat(),q.w.toFloat()))
                    val utc=WorldClockSession.clock.instant
                    if(utc!=solarInstant){solarInstant=utc;solar=SolarPosition.at(utc)}
                    val sun=GlobeGeography.direction(solar.declination,solar.longitude)
                    val center=geographic.convertPositionTo(Vector3(0f,0f,0f),null)
                    val tip=geographic.convertPositionTo(Vector3(sun.x.toFloat(),sun.y.toFloat(),sun.z.toFloat()),null)
                    val x=tip.x-center.x;val y=tip.y-center.y;val z=tip.z-center.z
                    val length=sqrt(x*x+y*y+z*z).coerceAtLeast(1e-8f)
                    materials.forEach{it.setParameter("SunDot_in2",Vector3(x/length,y/length,z/length))}
                    opacity.opacity=WorldClockSession.desktopVisibility
                    WorldClockSession.desktopFrames++;sampleFrames++;sampleSeconds+=dt
                    if(sampleSeconds>=5){Log.i("WorldClockSpace","V58 desktop frames="+sampleFrames+" seconds="+sampleSeconds+" focused="+container.isFocused.value+" UTC="+solarInstant+" diameter="+WorldClockSession.diameter);sampleSeconds=0.0;sampleFrames=0}
                }
            }
            WorldClockSession.desktopResources=true
            Log.i("WorldClockSpace","V58 desktop ready: 3072x1536 day + 2048x1024 night; 1 opaque material; no 8K/clouds/temperature assets")
        }catch(e:CancellationException){resources.close();throw e}
        catch(e:Exception){resources.close();WorldClockSession.failedToLoad(e)}
    })
}

internal fun findGlobeEntity(entity:Entity,name:String):Entity? {
    if(entity.getName()==name)return entity
    entity.getChildren().forEach{findGlobeEntity(it,name)?.let{return it}}
    return null
}

