package com.wupengfei.worldclock.content

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.*
import com.pico.spatial.ui.design.*
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.ui.foundation.layout.depth
import com.pico.spatial.ui.foundation.layout.offset
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.LocalSpatialContainerStateManager
import com.pico.spatial.ui.platform.LocalVolumeViewPointManager
import com.pico.spatial.ui.platform.meters
import com.pico.spatial.ui.platform.resize.VolumeBasePanelType
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
    val containerParams=LocalWindowContainerParamsUpdater.current
    LaunchedEffect(containerParams,WorldClockSession.desktopRendering) {
        if(!WorldClockSession.desktopRendering)return@LaunchedEffect
        // The OS can time out parameter updates during Stage teardown. Wait for the
        // confirmed Shared transition and fade to finish, then update persisted windows.
        val ready=withTimeoutOrNull(30_000) {
            while(!WorldClockSession.desktopResources||WorldClockSession.busy||WorldClockSession.stageMounted)delay(100)
            delay(250)
            true
        }?:false
        if(!ready){Log.w("WorldClockSpace","V63 base panel update deferred: Shared not ready");return@LaunchedEffect}
        repeat(3){attempt->
            if(!WorldClockSession.desktopRendering||WorldClockSession.busy||WorldClockSession.stageMounted)return@LaunchedEffect
            when(val result=containerParams.setVolumeBasePanelType(VolumeBasePanelType.None)) {
                is WindowContainerParamsUpdater.UpdateResult.Success -> {
                    Log.i("WorldClockSpace","V63 volume base panel disabled after Shared ready; attempt=${attempt+1}; system caption retained")
                    return@LaunchedEffect
                }
                is WindowContainerParamsUpdater.UpdateResult.Failure -> Log.w("WorldClockSpace","V63 base panel attempt=${attempt+1} failed: ${result.code} ${result.reason}")
            }
            if(attempt<2)delay(500)
        }
    }
    DisposableEffect(Unit) {
        WorldClockSession.desktopMounted=true;WorldClockSession.desktopNavigator=navigator
        onDispose {
            WorldClockSession.desktopMounted=false;WorldClockSession.desktopNavigator=null
            WorldClockSession.desktopClosed()
            Log.i("WorldClockSpace","V58 desktop container disposed")
        }
    }
    // Physical envelope includes the 80cm Earth and a separate bottom control region.
    val width=DesktopEarthLayout.WIDTH.meters;val height=DesktopEarthLayout.HEIGHT.meters;val depth=DesktopEarthLayout.DEPTH.meters
    // Same visible height as the official Regular slider's 32dp capsule.
    val button=32.dp
    ClockTheme {
        Box(Modifier.windowConstraints(width,height,depth).fillMaxSize().depth(depth)) {
            if(WorldClockSession.desktopRendering)DesktopScene()
            // Keep controls ahead of the largest sphere and below its lower bound.
            // This is a separate UI plane, never scaled with the Earth.
            Column(Modifier.align(Alignment.BottomCenter).offset(z=DesktopEarthLayout.CONTROLS_Z.meters)
                .padding(bottom=DesktopEarthLayout.CONTROLS_BOTTOM_PADDING.meters)
                .height(DesktopEarthLayout.CONTROLS_HEIGHT.meters),
                verticalArrangement=Arrangement.Bottom,horizontalAlignment=Alignment.CenterHorizontally) {
                if(WorldClockSession.error.isNotEmpty())Text(WorldClockSession.error,style=uiText(12))
                Row(horizontalArrangement=Arrangement.spacedBy(.015f.meters),verticalAlignment=Alignment.CenterVertically) {
                    Slider(
                        value=WorldClockSession.diameter,
                        onValueChange=WorldClockSession::setDesktopDiameter,
                        valueRange=DesktopEarthLayout.MIN_DIAMETER..DesktopEarthLayout.MAX_DIAMETER,
                        enabled=!WorldClockSession.busy,
                        colors=SliderDefaults.sliderColors().copy(
                            trackColor=Color.White.copy(alpha=.18f),
                            progressColor=Color.White.copy(alpha=.65f),
                            progressHighColor=Color.White,
                            thumbColor=Color.White,
                            thumbHighColor=Color.White,
                        ),
                        modifier=Modifier.width(.22f.meters).clip(CircleShape)
                            .backgroundMaterial()
                            .semantics { contentDescription="地球大小" },
                    )
                    IconButton(
                        onClick={WorldClockSession.expand(navigator)},
                        size=IconButtonDefaults.iconButtonSize(button),
                        shape=CircleShape,
                        colors=IconButtonDefaults.iconButtonColors(containerColor=Color.Transparent,contentColor=Color.White),
                        modifier=Modifier.clip(CircleShape).backgroundMaterial(),
                        enabled=!WorldClockSession.busy,
                    ) {
                        Icon(painterResource(com.pico.spatial.ui.design.R.drawable.ic_sui_double_arrow_right),
                            contentDescription="展开到沉浸模式",modifier=Modifier.size(18.dp))
                    }
                }
                if(WorldClockSession.error.isNotEmpty())GlassAction({WorldClockSession.closeSoftware(navigator,activity)},Modifier.height(32.dp),!WorldClockSession.busy){Text("关闭软件",style=uiText(13))}
            }
        }
    }
}

private class DesktopResources : AutoCloseable {
    var bundle:AssetBundle?=null
    var earth:Entity?=null
    var origin:Entity?=null
    var pointToVolume:((Vector3)->Vector3)?=null
    val input=GlobePointerInput(WorldClockSession.motion)
    val collisionResources=mutableListOf<Resource>()
    var frames:Cancellable?=null
    val materials=linkedSetOf<Material>()
    val textures=linkedSetOf<TextureResource>()
    val meshes=linkedSetOf<MeshResource>()
    var closed=false
    override fun close() {
        if(closed)return
        closed=true;frames?.cancel();frames=null
        input.cancel(desktopNowSeconds())
        pointToVolume=null
        earth?.destroy();earth=null
        origin?.destroy();origin=null
        collisionResources.forEach{if(it.valid)it.close()};collisionResources.clear()
        DesktopRenderTestAccess.earth=null;DesktopRenderTestAccess.input=null
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

private fun desktopNowSeconds()=android.os.SystemClock.elapsedRealtimeNanos()/1_000_000_000.0

/** Same geography, UTC and presentation state; only this renderer's asset budget differs. */
@Composable private fun DesktopScene() {
    val container=LocalSpatialContainerStateManager.current
    // Focus is deliberately excluded. isOnstage also becomes false for partial
    // occlusion: using it would freeze the still-visible part beside another app.
    val visible=rememberUpdatedState(container.isSighted.value)
    val resources=remember{DesktopResources()}
    val context=LocalContext.current
    val viewPoint=rememberUpdatedState(LocalVolumeViewPointManager.current.viewpoint.value)
    DisposableEffect(Unit){onDispose{resources.close()}}
    LaunchedEffect(Unit) {
        while(isActive){WorldClockSession.clock.now=ClockTimeSource.read().instant;delay(1000)}
    }
    SpatialView(Modifier.fillMaxSize().depth(DesktopEarthLayout.DEPTH.meters).pointerInput(context){
        val input=resources.input
        var right=GlobeVector3.X;var up=GlobeVector3.Y
        var pressPose=GlobeQuaternion.IDENTITY
        try {
            detectSpatialPointerEvent(context,targetedToEntity=TargetEntity.any{it.getName()=="DesktopEarthInteraction"}){pointers->
                var consumed=false
                pointers.forEach{pointer->
                    if(resources.closed||resources.earth==null||WorldClockSession.busy)return@forEach
                    val previous=input.activePointerId
                    val now=desktopNowSeconds()
                    if(previous==null&&pointer.isDownEvent()){
                        pressPose=WorldClockSession.motion.presentation
                        WorldClockSession.clock.manualInteraction()
                        val view=GlobeQuaternion.axisAngle(GlobeVector3.Y,viewPoint.value.value*90.0)
                        input.setViewOrientation(view);right=view.rotate(GlobeVector3.X);up=view.rotate(GlobeVector3.Y)
                    }
                    // Shared exposes spatial pointer positions, not Full-only HMD poses.
                    // Convert the hit in the stationary view (never through the rotating
                    // sphere), then reuse Full's threshold, filter and rotation policy.
                    val local=pointer.position3D
                    val point=resources.pointToVolume?.invoke(Vector3(local.x,local.y,local.z))
                    val v=GlobeVector3(point?.x?.toDouble()?:Double.NaN,point?.y?.toDouble()?:Double.NaN,point?.z?.toDouble()?:Double.NaN)
                    consumed=input.update(pointer.pointerId.value,pointer.isDownEvent(),pointer.pressed,v.dot(right),-v.dot(up),now)||consumed
                    if(previous!=input.activePointerId&&com.wupengfei.worldclock.BuildConfig.DEBUG)
                        Log.i("WorldClockSpace","V61 desktop pointer owner="+input.activePointerId+
                            " gestureDegrees="+pressPose.angleTo(WorldClockSession.motion.presentation)+
                            " rejectedSamples="+input.rejectedSamples)
                }
                consumed
            }
        }finally{input.cancel(desktopNowSeconds())}
    },initial={content,_->
        if(resources.closed)return@SpatialView
        try {
            val bundle=AssetBundle.load("asset://DesktopEarth.bundle")
            resources.bundle=bundle;check(bundle.valid)
            val earth=bundle.loadModelSuspend("DesktopEarthV62")
            if(resources.closed){earth.destroy();return@SpatialView}
            resources.earth=earth
            DesktopRenderTestAccess.earth=earth
            DesktopRenderTestAccess.input=resources.input
            // Stationary meter-space origin: dragging must not feed back through the
            // rotating/scaling globe, and must follow system movement of the container.
            val origin=Entity();resources.origin=origin;content.addEntity(origin)
            resources.pointToVolume={point->content.convertPosition(point,ViewCoordinateSpace.Local,origin)}
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
                        material.setBlendingMode(BlendingMode.OPAQUE)
                        materials.add(material);resources.materials.add(material)
                        material.setParameter("NightMask_Remap_inlow",DayNightLighting.nightStartDot)
                        material.setParameter("NightMask_Remap_inhigh",DayNightLighting.nightFullDot)
                        check(!material.getParameter<Boolean>("applyPostProcessToneMap"))
                        resources.textures.add(material.getParameter("Day_file",TextureResource::class.java))
                        resources.textures.add(material.getParameter("Night_file",TextureResource::class.java))
                        resources.textures.add(material.getParameter("Detail_file",TextureResource::class.java))
                    }
                }
                e.components.set(EnvironmentLightingSettingsComponent(0f))
                check(e.components[EnvironmentLightingSettingsComponent::class.java]?.scale==0f)
                check(e.components[DirectionalLightComponent::class.java]==null)
                check(e.components[PointLightComponent::class.java]==null)
                check(e.components[SpotLightComponent::class.java]==null)
                e.getChildren().forEach{bind(it)}
            }
            bind(earth);check(materials.size==1)
            Log.i("WorldClockSpace","V61 material blend="+materials.single().getBlendingMode()+" opacity="+materials.single().getParameter<Float>("opacity"))
            val opacity=OpacityControllerComponent(0f);earth.components.set(opacity)
            // Imported model diameter is 4m. Root placement stays inside the physical volume.
            transform.setPosition(Vector3(0f,DesktopEarthLayout.CENTER_Y,0f))
            earth.setName("DesktopEarthInteraction")
            val shape=ShapeResource.createSphere(2f);val physics=PhysicsMaterialResource()
            resources.collisionResources.add(shape);resources.collisionResources.add(physics)
            earth.components.set(InteractableComponent())
            earth.components.set(CollisionComponent(listOf(shape),physics))
            content.addEntity(earth)
            val clouds=CloudVisibility()
            var solarInstant=WorldClockSession.clock.instant
            var solar=SolarPosition.at(solarInstant)
            var sampleSeconds=0.0;var sampleFrames=0
            resources.frames=content.subscribe(SceneEvents.Update::class.java){event->
                if((!visible.value||WorldClockSession.busy)&&resources.input.activePointerId!=null)resources.input.cancel(desktopNowSeconds())
                if(visible.value){
                    val dt=event.deltaTime.toDouble().coerceIn(0.0,.05)
                    val motion=WorldClockSession.motion
                    val now=desktopNowSeconds()
                    if(!WorldClockSession.busy&&!DesktopRenderTestAccess.freezeMotion)motion.tickDesktop(dt,now)
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
                    val sunWorld=Vector3(x/length,y/length,z/length)
                    val cloudOpacity=clouds.update(motion.cloudsMayShow(now),dt)*.65f
                    materials.forEach{
                        it.setParameter("SunDot_in2",sunWorld)
                        it.setParameter("HalfSum_in2",sunWorld)
                        it.setParameter("CloudDensity_in2",cloudOpacity)
                        it.setParameter("NoiseTimePosition_in2",Vector3(0f,0f,(.37+motion.effectSeconds*.55).toFloat()))
                    }
                    DesktopRenderTestAccess.cloudOpacity=cloudOpacity
                    if(!DesktopRenderTestAccess.skipOpacityUpdate)opacity.opacity=WorldClockSession.desktopVisibility
                    WorldClockSession.desktopFrames++;sampleFrames++;sampleSeconds+=dt
                    if(sampleSeconds>=5){Log.i("WorldClockSpace","V58 desktop frames="+sampleFrames+" seconds="+sampleSeconds+" focused="+container.isFocused.value+" UTC="+solarInstant+" diameter="+WorldClockSession.diameter);sampleSeconds=0.0;sampleFrames=0}
                }
            }
            WorldClockSession.desktopResources=true
            Log.i("WorldClockSpace","V63 desktop ready: opaque Unlit; IBL=0; 3K+2K+2K; soft +/-18deg surface twilight; local night shimmer; idle 9deg/s; diameter=${WorldClockSession.diameter}; max=0.8m; volume=0.9x1.05x0.9m")
        }catch(e:CancellationException){resources.close();throw e}
        catch(e:Exception){resources.close();WorldClockSession.failedToLoad(e)}
    })
}

/** Instrumentation-only inspection; no UI entry, reset by test finally blocks. */
internal object DesktopRenderTestAccess {
    var earth:Entity?=null
    var freezeMotion=false
    var skipOpacityUpdate=false
    var input:GlobePointerInput?=null
    var cloudOpacity=0f
}

internal fun findGlobeEntity(entity:Entity,name:String):Entity? {
    if(entity.getName()==name)return entity
    entity.getChildren().forEach{findGlobeEntity(it,name)?.let{return it}}
    return null
}
