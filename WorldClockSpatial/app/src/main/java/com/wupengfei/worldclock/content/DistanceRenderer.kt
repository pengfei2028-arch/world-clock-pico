package com.wupengfei.worldclock.content

import android.util.Log
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import kotlinx.coroutines.*
import kotlin.math.*

/** One batched local-space arc and two rings; one reusable native glass readout.
 * A new mesh is built only when the city pair changes. Parent transforms carry motion. */
internal class DistanceRenderer(private val coordinates:CoordinateRenderer,private val origin:Entity,
    private val content:SpatialViewContent,private val attachments:SpatialViewAttachments,private val clock:ClockState):AutoCloseable {
    private val state get()=clock.distance
    private val root=Entity()
    private val opacity=OpacityControllerComponent(0f)
    private val material=UnlitMaterial.create(BlendingMode.TRANSPARENT).apply{
        toGlobal();setBaseColor(Color4(1f,1f,1f,.90f));setApplyToneMapping(false)
        setDepthTest(true);setDepthWrite(false);setCullingMode(MaterialCullingMode.BACK)
    }
    private val framePath=EntityTransformPath(coordinates.surfaceEntity,origin)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var job:Job?=null
    private var wanted:Pair<ClockCity,ClockCity>?=null
    private var committed:Pair<ClockCity,ClockCity>?=null
    private var path:DistancePath?=null
    private var mesh:MeshResource?=null
    private val retired=mutableListOf<Pair<Int,MeshResource>>()
    private var frame=0
    private var panel:Entity?=null
    private var mounted=false
    private var fade=0f
    private val visibility=PinVisibility()
    private var closed=false
    init {
        root.setName("DistanceArc_V83");root.components.set(opacity)
        root.components.set(EnvironmentLightingSettingsComponent(0f));check(coordinates.surfaceEntity.addChild(root));root.enabled=false
        Log.i("WorldClockDistance","V83 ready: nativeModels=1 nativeReadouts=1 textures=0 greatCircleRadiusKm=${DistanceStyle.RADIUS_KM}")
    }
    fun selectAt(coordinate:GeoCoordinate){
        if(!state.enabled||clock.mode==EarthMode.EARTHQUAKE)return
        GreatCircle.nearest(clock.cities,coordinate)?.let{clock.focus(it)}
    }
    private fun request(pair:Pair<ClockCity,ClockCity>?){
        if(wanted==pair)return
        wanted=pair;job?.cancel()
        if(pair==null)return
        // Only two UV lookups on the scene thread. Worker gets immutable positions.
        val nextPath=DistancePath.create(coordinates.mapping,pair.first,pair.second)
        job=scope.launch{
            var next:MeshResource?=null
            try{
                val geometry=withContext(Dispatchers.Default){nextPath.geometry()}
                withContext(Dispatchers.IO+NonCancellable){
                    next=MeshResource.createWithMeshModel(MeshModel(geometry.positions.map(CoordinateRenderer::sdk),geometry.indices),name="DistanceArc").also{it.toGlobal()}
                }
                currentCoroutineContext().ensureActive()
                if(closed||wanted!=pair)return@launch
                val model=root.components[ModelComponent::class.java]
                if(model==null)root.components.set(ModelComponent(checkNotNull(next),material))else model.mesh=checkNotNull(next)
                mesh?.let{retired.add((frame+3) to it)};mesh=next;next=null
                path=nextPath;committed=pair;state.failure=""
                Log.i("WorldClockDistance","V83 pair=${pair.first.id},${pair.second.id} km=${GreatCircle.km(pair.first,pair.second)} triangles=${geometry.indices.size/3} rebuild=selectionOnly")
            }catch(e:CancellationException){throw e}catch(e:Exception){
                state.failure="距离图层暂不可用，请重新开启";Log.e("WorldClockDistance","Arc build",e)
            }finally{next?.let{if(it.valid)it.close()}}
        }
    }
    fun update(dt:Double,head:Vector3,globeVisibility:Float,ready:Boolean){
        if(closed)return
        state.readoutAnchor=null
        frame++;val iterator=retired.iterator()
        while(iterator.hasNext()){val item=iterator.next();if(item.first<=frame){if(item.second.valid)item.second.close();iterator.remove()}}
        val active=state.enabled&&clock.mode!=EarthMode.EARTHQUAKE&&ready
        val pair=if(active)state.pair(clock.cities)else null
        request(pair)
        val step=(dt.coerceIn(0.0,.1)/DistanceStyle.FADE_SECONDS).toFloat()
        fade=if(active&&pair!=null&&pair==committed)min(1f,fade+step)else max(0f,fade-step)
        // Never draw the preceding pair while its replacement is being built.
        root.enabled=fade>.001f&&(pair==committed||!active);opacity.opacity=fade*globeVisibility
        if(panel==null)panel=attachments.entity("distance_card")
        val label=panel?:return
        val midpoint=path?.midpoint
        if(midpoint==null||fade<.001f||pair!=committed){label.enabled=false;state.panelAlpha=0f;return}
        val transform=framePath.frame();val point=transform.point(midpoint);val c=transform.zero
        val h=CoordinateRenderer.vec(head);val normal=(point-c).normalized()
        val reveal=visibility.update(PinVisibility.atLimb(normal.dot((h-point).normalized())),dt)*fade*globeVisibility
        state.panelAlpha=reveal
        if(reveal<=.001f){label.enabled=false;return}
        if(!mounted){label.enabled=true;content.addEntity(label);mounted=true}else label.enabled=true
        // Put the readout in front of the sphere while retaining the midpoint's
        // screen projection. No leader or second card; Earth occludes the back arc.
        val radius=sqrt(transform.x.dot(transform.x))*2
        val z=c.z+radius*1.16
        val ray=point-h
        val k=if(abs(ray.z)<1e-6)1.0 else ((z-h.z)/ray.z).coerceIn(.01,1.0)
        val anchor=h+ray*k
        state.readoutAnchor=anchor
        label.components[TransformComponent::class.java]!!.apply{
            setPosition(CoordinateRenderer.sdk(anchor));setScaleVector(Vector3(CityLabelLayout.SCALE,CityLabelLayout.SCALE,CityLabelLayout.SCALE))
            setQuaternion(Quat(0f,0f,0f,1f))
        }
    }
    override fun close(){
        if(closed)return;closed=true;scope.cancel();root.destroy();panel?.enabled=false;state.panelAlpha=0f;state.readoutAnchor=null
        mesh?.let{if(it.valid)it.close()};mesh=null
        retired.forEach{if(it.second.valid)it.second.close()};retired.clear();if(material.valid)material.close()
        Log.i("WorldClockDistance","V83 released arc and material; liveMeshes=0")
    }
}
