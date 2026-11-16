package com.wupengfei.worldclock.content

import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.math.*
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import kotlin.math.*

/** Photos are native Compose attachments; this layer only supplies geographic anchors.
 * One bounded set of reusable pins and one Dock-aligned album; no leader or per-photo scene assets. */
internal class FootprintRenderer(private val coordinates:CoordinateRenderer,private val origin:Entity,
    private val earth:Entity,private val content:SpatialViewContent,private val attachments:SpatialViewAttachments,
    private val clock:ClockState,private val eye:Vector3,private val density:Float):AutoCloseable {
    private val state get()=clock.footprints
    private val surfaceToOrigin=EntityTransformPath(coordinates.surfaceEntity,origin)
    private val surfaceToEarth=EntityTransformPath(coordinates.surfaceEntity,earth)
    private val localPoints=mutableMapOf<String,GlobeVector3>()
    private val mounts=FootprintAttachmentMounts<Entity>(
        attach={entity->
            content.addEntity(entity)
            val attached=entity.getParent()==origin.getParent()
            if(attached)android.util.Log.i("WorldClockFootprints","V98 attachment mounted in active Stage: ${entity.getName()}")
            attached
        },
        enable={entity,enabled->entity.enabled=enabled},
    )
    private val pins=arrayOfNulls<Entity>(FootprintStore.MAX_CITIES)
    private val visibilities=Array(FootprintStore.MAX_CITIES){PinVisibility()}
    private var panel:Entity?=null
    private var places:Entity?=null
    private var closed=false
    private val unit=content.convertSize(Vector3(density,0f,0f),ViewCoordinateSpace.Local,origin).x.toDouble()*CityLabelLayout.SCALE
    private val toolbarUnit=unit/CityLabelLayout.SCALE*3.5
    private fun local(city:ClockCity)=localPoints.getOrPut(city.id){coordinates.mapping.point(city.latitude,city.longitude)*1.012}
    fun directionInEarth(city:ClockCity)=surfaceToEarth.frame().point(local(city)).normalized()
    private fun show(entity:Entity?,visible:Boolean){
        // The SDK explicitly disables this parent on PAUSE/STOP (e.g. system picker).
        // Never cache a successful mount while the parent is inactive.
        mounts.show(entity,visible,origin.getParent()?.enabled==true)
    }
    private fun pose(entity:Entity,position:GlobeVector3,scale:Float=CityLabelLayout.SCALE,q:GlobeQuaternion=GlobeQuaternion.IDENTITY){
        entity.components[TransformComponent::class.java]!!.apply{
            setPosition(CoordinateRenderer.sdk(position));setScaleVector(Vector3(scale,scale,scale))
            setQuaternion(Quat(q.x.toFloat(),q.y.toFloat(),q.z.toFloat(),q.w.toFloat()))
        }
    }
    fun update(dt:Double,head:Vector3,visibility:Float,ready:Boolean){
        if(closed)return
        val active=ready&&state.enabled&&visibility>.001f&&origin.getParent()?.enabled==true
        val cities=state.cities(clock.catalog)
        if(panel==null)panel=attachments.entity("footprint_album")
        if(places==null)places=attachments.entity("footprint_places")
        val bar=GlobeVector3(eye.x+clock.toolbarDrag.x*toolbarUnit,eye.y-1-clock.toolbarDrag.y*toolbarUnit,eye.z-2.5)
        places?.let{pose(it,bar+GlobeVector3(FootprintLayout.PLACES_LEFT_DP*toolbarUnit,44*toolbarUnit,.025),3.5f)}
        show(places,active&&state.placesOpen)
        val transform=surfaceToOrigin.frame();val c=transform.zero
        val h=CoordinateRenderer.vec(head);val radius=sqrt(transform.x.dot(transform.x))*2
        val plane=c.z+radius*1.16
        for(i in pins.indices){
            if(pins[i]==null&&i<cities.size)pins[i]=attachments.entity("footprint_pin_$i")?.also{it.setName("FootprintPin_slot_$i")}
            val city=cities.getOrNull(i);val entity=pins[i]
            if(!active||city==null||entity==null){show(entity,false);state.pinAlpha[i]=0f;continue}
            val point=transform.point(local(city));val n=(point-c).normalized()
            val alpha=visibilities[i].update(PinVisibility.atLimb(n.dot((h-point).normalized())),dt)*visibility
            state.pinAlpha[i]=alpha
            if(alpha>.001f){
                val ray=point-h
                val t=if(abs(ray.z)<1e-6)1.0 else ((plane-h.z)/ray.z).coerceIn(.01,1.0)
                val anchor=h+ray*t
                val q=GlobeQuaternion.align(GlobeVector3.Z,(h-anchor).normalized())
                // Component 200x96: exact city point is (36,84), not the photo centre.
                val center=anchor+q.rotate(GlobeVector3(64*unit,36*unit,0.0))
                pose(entity,center,q=q)
            }
            show(entity,alpha>.001f)
        }
        val wanted=if(active&&state.albumOpen&&state.city(clock.catalog)!=null&&!state.placesOpen)1f else 0f
        val step=(dt.coerceIn(0.0,.1)/.18).toFloat()
        state.albumAlpha=if(wanted>state.albumAlpha)min(wanted,state.albumAlpha+step)else max(wanted,state.albumAlpha-step)
        val shown=active&&state.albumOpen&&state.albumAlpha>.001f
        // Same stage plane, scale and absolute toolbar motion as the shared Dock.
        // Attachment uses BOTTOM_CENTER, so changing its height grows upwards.
        val bottom=FootprintLayout.albumBottomDp(state.viewerOpen)
        panel?.let{pose(it,bar+GlobeVector3(0.0,bottom*toolbarUnit,.035),3.5f)}
        show(panel,shown)

    }
    override fun close(){
        if(closed)return;closed=true;mounts.close();pins.fill(null)
        state.albumAlpha=0f;state.pinAlpha.indices.forEach{state.pinAlpha[it]=0f};state.images?.clear()
        android.util.Log.i("WorldClockFootprints","V97 Dock album renderer released; photo textures owned by disposed Compose views")
    }
}
