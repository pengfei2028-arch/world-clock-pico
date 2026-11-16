package com.wupengfei.worldclock.content

import android.content.Context
import android.util.Log
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*
import kotlinx.coroutines.*
import kotlin.math.*

/** Owns only the auxiliary mesh/material handles. Never changes Earth models or materials. */
internal class CoordinateRenderer private constructor(val surfaceEntity:Entity,val mapping:CoordinateSurface,
    private val state:CoordinateState,private val rulerOrigin:Entity?):AutoCloseable {
    private val root=Entity()
    private val alpha=OpacityControllerComponent(0f)
    private val rulerRoot=if(rulerOrigin!=null)Entity() else null
    private val rulerLabels=mutableMapOf<Int,Entity>()
    private val labelAnchors=CoordinateRuler.slots.map{it.labelLocal(mapping)}
    private val labelSurfaces=CoordinateRuler.slots.map{it.surfaceLocal(mapping)}
    private val labelTangents=CoordinateRuler.slots.map{it.tangentLocal(mapping)}
    private val labelRolls=DoubleArray(CoordinateRuler.slots.size)
    private val labelRotations=MutableList(CoordinateRuler.slots.size){GlobeQuaternion.IDENTITY}
    private var labelLocalScale=CoordinateStyle.LABEL_SCALE/12f
    private var placements:List<CoordinateRuler.Placement>?=null
    private val meshes=mutableListOf<MeshResource>()
    private val materials=mutableListOf<UnlitMaterial>()
    private var marker:Entity?=null
    private var lastSelection:CoordinateSelection?=null
    private var selectedLocal:GlobeVector3?=null
    private var closed=false
    private var reveal=0f
    private var reportedEnabled=false
    private var auditSeconds=0.0
    private suspend fun add(geometry:CoordinateGeometry,opacity:Float,name:String,parent:Entity=root,rgb:Int=0xFFFFFF):Entity {
        val model=MeshModel(geometry.positions.map{sdk(it)},geometry.indices)
        val mesh=withContext(Dispatchers.IO+NonCancellable){MeshResource.createWithMeshModel(model,name=name)}
        meshes.add(mesh)
        val material=UnlitMaterial.create(BlendingMode.TRANSPARENT).apply{
            setBaseColor(Color4(((rgb shr 16) and 255)/255f,((rgb shr 8) and 255)/255f,(rgb and 255)/255f,1f))
            setOpacity(opacity);setApplyToneMapping(false)
            // Round tubes: draw the outward wall once, not overlapping back/front alpha.
            setCullingMode(MaterialCullingMode.BACK);setDepthTest(true);setDepthWrite(false)
        }
        materials.add(material)
        return Entity().apply{setName(name);components.set(EnvironmentLightingSettingsComponent(0f));components.set(ModelComponent(mesh,material));check(parent.addChild(this))}
    }
    private suspend fun initialize() {
        root.setName("CoordinateLayer_V72");root.components.set(alpha);root.enabled=false
        check(surfaceEntity.addChild(root))
        add(withContext(Dispatchers.Default){CoordinateGeometry.grid(mapping)},CoordinateStyle.GRID_ALPHA,"CoordinateGrid")
        add(withContext(Dispatchers.Default){CoordinateGeometry.reference(mapping,true)},CoordinateStyle.MAJOR_ALPHA,"CoordinateEquator",rgb=CoordinateStyle.EQUATOR_RGB)
        add(withContext(Dispatchers.Default){CoordinateGeometry.reference(mapping,false)},CoordinateStyle.MAJOR_ALPHA,"CoordinatePrimeMeridian",rgb=CoordinateStyle.PRIME_RGB)
        rulerRoot?.let{ruler->
            ruler.setName("CoordinateGeographicRulers_V72");ruler.enabled=false
            check(root.addChild(ruler))
            add(CoordinateGeometry.rulers(mapping),CoordinateStyle.ARC_ALPHA,"CoordinateDualRulers",ruler)
        }
        marker=add(CoordinateGeometry.ring(),.85f,"CoordinateSelectionRing").apply{enabled=false}
        Log.i("WorldClockCoordinates","V72 ready: continuous grid; ${meshes.size} meshes; labelsParent=geographic; fixedLocalPositions=true; textLeaders=0; ZRollOnly=true")
    }
    fun bindRulerLabel(label:Entity,index:Int){
        val parent=rulerRoot?:return
        if(rulerLabels[index]===label)return
        // SDK 6.1.9 attachment panels must first enter an enabled tree. Mounting
        // while disabled skips their display activation even if enabled later.
        label.enabled=true;check(parent.addChild(label));rulerLabels[index]=label
        label.components[TransformComponent::class.java]!!.apply{
            // Assigned exactly once. Native hierarchy carries translation, tilt, spin and scale.
            setPosition(sdk(labelAnchors[index]))
            setScaleVector(Vector3(labelLocalScale,labelLocalScale,labelLocalScale))
            setQuaternion(quat(labelRotations[index]))
        }
    }
    fun rulerAnchorError():Double {
        val parent=rulerRoot?:return 0.0
        return rulerLabels.maxOfOrNull{(index,label)->
            val d=vec(label.convertPositionTo(Vector3(0f,0f,0f),parent))-labelAnchors[index]
            sqrt(d.dot(d))
        }?:0.0
    }
    fun labelFacingDot():Double {
        val origin=rulerOrigin?:return 1.0
        return rulerLabels.values.minOfOrNull{label->
            val a=vec(label.convertPositionTo(Vector3(0f,0f,0f),origin))
            val b=vec(label.convertPositionTo(Vector3(0f,0f,1f),origin))
            (b-a).normalized().dot(GlobeVector3.Z)
        }?:1.0
    }
    fun labelUpDot():Double {
        val origin=rulerOrigin?:return 1.0
        return rulerLabels.values.minOfOrNull{label->
            val a=vec(label.convertPositionTo(Vector3(0f,0f,0f),origin))
            val b=vec(label.convertPositionTo(Vector3(0f,1f,0f),origin))
            (b-a).normalized().dot(GlobeVector3.Y)
        }?:1.0
    }
    fun maximumTextRoll()=labelRolls.maxOf{abs(it)}
    fun referenceWorldPositions()=placements?.let{p->
        CoordinateRuler.displayOrder.filter{CoordinateRuler.slots[it].reference}
            .joinToString{CoordinateRuler.slots[it].title+"="+p[it].position}
    }?:"unavailable"
    fun updateRuler(seconds:Double):List<CoordinateRuler.Placement>? {
        val parent=rulerRoot?:return null;val origin=rulerOrigin?:return null
        if(closed||reveal<=.001f)return null
        val center=vec(parent.convertPositionTo(Vector3(0f,0f,0f),origin))
        val x=vec(parent.convertPositionTo(Vector3(1f,0f,0f),origin))-center
        val y=vec(parent.convertPositionTo(Vector3(0f,1f,0f),origin))-center
        val z=vec(parent.convertPositionTo(Vector3(0f,0f,1f),origin))-center
        val scale=sqrt(x.dot(x))
        if(scale<.001){parent.enabled=false;return null}
        parent.enabled=true
        if(rulerLabels.isEmpty())labelLocalScale=(CoordinateStyle.LABEL_SCALE/scale).toFloat()
        val parentRotation=CoordinateRuler.rotationFromBasis(x,y,z)
        // Positions are untouched. Smooth only a bounded Z roll, never translation.
        // No camera pose, perspective projection or label avoidance is involved.
        val result=labelAnchors.indices.map{i->
            val position=vec(parent.convertPositionTo(sdk(labelAnchors[i]),origin))
            val surface=vec(surfaceEntity.convertPositionTo(sdk(labelSurfaces[i]),origin))
            CoordinateRuler.Placement(position,surface,(surface-center).normalized())
        }
        labelRotations.indices.forEach{i->
            val tangent=parentRotation.rotate(labelTangents[i])
            labelRolls[i]=CoordinateRuler.rollDegrees(tangent,CoordinateRuler.slots[i].latitude,labelRolls[i],seconds)
            labelRotations[i]=CoordinateRuler.localTextRotation(parentRotation,labelRolls[i])
        }
        rulerLabels.forEach{(i,label)->
            label.components[TransformComponent::class.java]!!.apply{
                setQuaternion(quat(labelRotations[i]))
            }
        }
        placements=result;return result
    }
    fun localHit(point:Vector3):GeoCoordinate?=mapping.coordinate(vec(point))
    fun selectedPosition(origin:Entity):GlobeVector3?=selectedLocal?.let{vec(surfaceEntity.convertPositionTo(sdk(it),origin))}
    fun update(seconds:Double,visibility:Float) {
        if(closed)return
        val target=if(state.enabled)1f else 0f
        val step=(seconds.coerceIn(0.0,.05)/CoordinateStyle.FADE_SECONDS).toFloat()
        reveal=if(target>reveal)min(target,reveal+step) else max(target,reveal-step)
        state.reveal=reveal
        root.enabled=reveal>.001f;alpha.opacity=reveal*visibility
        rulerRoot?.enabled=reveal>.001f
        if(state.selection!=lastSelection){
            lastSelection=state.selection
            selectedLocal=lastSelection?.coordinate?.let{mapping.point(it.latitude,it.longitude?:0.0)*(CoordinateStyle.SURFACE_SCALE+.003)}
            selectedLocal?.let{p->marker?.components?.get(TransformComponent::class.java)?.apply{
                setPosition(sdk(p));setQuaternion(quat(GlobeQuaternion.align(GlobeVector3.Z,p.normalized())))
            }}
        }
        marker?.enabled=selectedLocal!=null&&reveal>.001f
        if(!state.enabled&&reveal==0f)state.finishHidden()
        if(reportedEnabled!=state.enabled){reportedEnabled=state.enabled;Log.i("WorldClockCoordinates","V72 enabled=$reportedEnabled; ownedMeshes=${meshes.size}; original surface retained")}
        if(state.enabled&&com.wupengfei.worldclock.BuildConfig.DEBUG){
            auditSeconds+=seconds
            if(auditSeconds>=5){auditSeconds=0.0
                var error=0.0
                for((lat,lon) in listOf(39.9075 to 116.39723,51.50853 to -.12574,-33.86785 to 151.20732,40.71427 to -74.00597)){
                    val original=mapping.point(lat,lon)
                    val world=surfaceEntity.convertPositionTo(sdk(original),null)
                    val back=surfaceEntity.convertPositionFrom(world,null)
                    val read=mapping.coordinate(vec(back))
                    error=max(error,if(read?.longitude==null)Double.POSITIVE_INFINITY else max(abs(read.latitude-lat),abs(SolarPosition.wrap(read.longitude-lon))))
                }
                Log.i("WorldClockCoordinates","V72 passive transform audit: maxDegrees=$error; validMeshes=${meshes.count{it.valid}}; layerAlpha=$reveal; city=${state.selection?.cityId}")
            }
        }
    }
    override fun close(){
        if(closed)return;closed=true;root.destroy();rulerLabels.clear();marker=null;placements=null
        meshes.forEach{if(it.valid)it.close()};materials.forEach{if(it.valid)it.close()}
        Log.i("WorldClockCoordinates","V72 released: meshes=${meshes.size}; remainingValid=${meshes.count{it.valid}+materials.count{it.valid}}")
        meshes.clear();materials.clear();state.panelAlpha=0f;state.reveal=0f
    }
    companion object {
        fun vec(v:Vector3)=GlobeVector3(v.x.toDouble(),v.y.toDouble(),v.z.toDouble())
        fun sdk(v:GlobeVector3)=Vector3(v.x.toFloat(),v.y.toFloat(),v.z.toFloat())
        fun quat(q:GlobeQuaternion)=Quat(q.x.toFloat(),q.y.toFloat(),q.z.toFloat(),q.w.toFloat())
        suspend fun create(context:Context,earth:Entity,state:CoordinateState,rulerOrigin:Entity?=null):CoordinateRenderer {
            fun find(e:Entity):Entity?=if(e.components[ModelComponent::class.java]!=null)e else e.getChildren().firstNotNullOfOrNull{find(it)}
            // Find the original surface before adding any overlay children.
            val surface=checkNotNull(find(earth))
            val mapping=state.surface?:withContext(Dispatchers.IO){CoordinateSurface.read(context.assets.open("coordinates/earth_surface.bin"))}.also{state.surface=it}
            val renderer=CoordinateRenderer(surface,mapping,state,rulerOrigin)
            try{renderer.initialize();currentCoroutineContext().ensureActive();return renderer}catch(e:Throwable){renderer.close();throw e}
        }
    }
}
