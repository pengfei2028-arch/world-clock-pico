package com.wupengfei.worldclock.content

import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*
import kotlin.math.*

/** Project geometry/placement choices; panels themselves use PICO design components. */
internal class ClockScene(private val content: SpatialViewContent,attachments: SpatialViewAttachments,
    private val state: ClockState,private val geographic: Entity,private val origin: Entity,
    private val eye: Vector3,owned: MutableList<Entity>,private val sunEffect: Entity,private val uiDensity:Float) {
    private val temperatureInfo=attachments.entity("temperature_info")
    private val modulePanel=attachments.entity("module")
    private val citiesPanel=attachments.entity("cities")
    private val timelinePanel=attachments.entity("timeline")
    private val toolsPanel=attachments.entity("time_tools")
    private val mounted=mutableSetOf<Entity>()
    private val toolbarUnit=content.convertSize(Vector3(uiDensity,0f,0f),ViewCoordinateSpace.Local,origin).x*3.5f
    private val sunMaterials=mutableListOf<ShaderGraphMaterial>()
    private var lastSunAudit=0L
    private val labelAvoidance=CityLabelAvoidance()
    private var lastLabelAudit=0L
    private fun show(e:Entity?,visible:Boolean){
        if(e==null)return
        if(visible && mounted.add(e)){e.enabled=true;content.addEntity(e)}
        else if(e in mounted && e.enabled!=visible)e.enabled=visible
    }
    private data class Pin(val stem:Entity,val head:Entity,val line:Entity,val card:Entity?,
        val stemAlpha:OpacityControllerComponent=OpacityControllerComponent(0f),
        val headAlpha:OpacityControllerComponent=OpacityControllerComponent(0f),
        val lineAlpha:OpacityControllerComponent=OpacityControllerComponent(0f),
        val cardAlpha:OpacityControllerComponent=OpacityControllerComponent(0f),
        var cityId:String?=null,var visibility:PinVisibility=PinVisibility())
    private var previousFrameNanos=java.lang.System.nanoTime()
    // Built-in PICO PBR: frosted translucent pearl, restrained highlight; project art values.
    private val pinMaterial=PhysicallyBasedMaterial.create(BlendingMode.TRANSPARENT).apply{
        setBaseColor(Color4(0.76f,0.83f,0.90f,1f));setOpacity(0.72f)
        setRoughness(0.52f);setMetallic(0f);setEmissiveColor(Color4(0.035f,0.04f,0.045f,1f))
        setDepthTest(true);setDepthWrite(false);setCullingMode(MaterialCullingMode.BACK)
    }
    private val leaderMaterial=UnlitMaterial.create(BlendingMode.TRANSPARENT).apply{
        setBaseColor(Color4(0.92f,0.95f,1f,0.65f));setApplyToneMapping(false);setDepthTest(true);setDepthWrite(false)
    }
    private val stemMesh=MeshResource.createCylinder(height=1f,radius=0.07f)
    private val headMesh=MeshResource.createSphere(0.16f)
    private val lineMesh=MeshResource.createCylinder(height=1f,radius=0.025f)
    private fun model(mesh: MeshResource,material: Material,name: String,content: SpatialViewContent,owned: MutableList<Entity>): Entity = Entity().apply {
        setName(name);components.set(ModelComponent(mesh,material));owned.add(this);content.addEntity(this);enabled=false
    }
    private val pins=(0 until 8).map{slot->
        val card=attachments.entity("city_card_"+slot)
        Pin(model(stemMesh,pinMaterial,"CityStem_"+slot,content,owned),
            model(headMesh,pinMaterial,"CityPin_"+slot,content,owned),
            model(lineMesh,leaderMaterial,"CityLeader_"+slot,content,owned),
            card).also{pin->
                pin.stem.components.set(pin.stemAlpha);pin.head.components.set(pin.headAlpha)
                pin.line.components.set(pin.lineAlpha);pin.card?.components?.set(pin.cardAlpha)
            }
    }
    init {
        owned.add(sunEffect);content.addEntity(sunEffect);sunEffect.enabled=false
        fun collectSun(e:Entity){
            e.components[ModelComponent::class.java]?.materials?.forEach{sunMaterials.add(it as ShaderGraphMaterial)}
            e.getChildren().forEach{collectSun(it)}
        }
        collectSun(sunEffect)
        check(sunMaterials.isNotEmpty()){ "Sun veil material missing" }
        // V42 project art tuning: retain the bright core/rays, contract the diffuse disk.
        sunMaterials.forEach{
            it.setParameter("InnerBloomFalloff_in2",-150f)
            it.setParameter("InnerBloomStrength_in2",.35f)
            it.setParameter("WideHaloFalloff_in2",-32f)
            it.setParameter("WideHaloStrength_in2",.045f)
            check(abs(it.getParameter<Float>("WideHaloStrength_in2")-.045f)<.0001f)
        }
        android.util.Log.i("WorldClockSun","V42 compact profile: core unchanged; bloom .35/-150; halo .045/-32; angular size fixed")
        android.util.Log.i("WorldClockSun","V36 material parameters="+sunMaterials.first().getParameterNames().joinToString())
        listOfNotNull(temperatureInfo,modulePanel,citiesPanel,timelinePanel,toolsPanel).forEach{
            transform(it).apply{setScaleVector(Vector3(3.5f,3.5f,3.5f));setQuaternion(Quat(0f,0f,0f,1f))}
        }
    }

    private fun vec(v: Vector3)=GlobeVector3(v.x.toDouble(),v.y.toDouble(),v.z.toDouble())
    private fun sdk(v: GlobeVector3)=Vector3(v.x.toFloat(),v.y.toFloat(),v.z.toFloat())
    private fun quat(q: GlobeQuaternion)=Quat(q.x.toFloat(),q.y.toFloat(),q.z.toFloat(),q.w.toFloat())
    private fun facing(position:Vector3,head:Vector3)=quat(GlobeQuaternion.align(GlobeVector3.Z,(vec(head)-vec(position)).normalized()))
    private fun transform(e:Entity)=e.components[TransformComponent::class.java]!!
    fun cityDirection(city:ClockCity,center:Vector3):GlobeVector3 {
        val point=geographic.convertPositionTo(sdk(city.direction),origin)
        return (vec(point)-vec(center)).normalized()
    }
    fun update(ready:Boolean,center:Vector3,head:Vector3,light:Vector3,globeOpacity:Float,veilProgress:Float,veilOpacity:Float) {
        val frameNow=java.lang.System.nanoTime()
        val seconds=((frameNow-previousFrameNanos)/1e9).coerceIn(0.0,0.1)
        previousFrameNanos=frameNow
        state.toolbarDrag.advance(seconds)
        val unit=toolbarUnit
        val bar=Vector3(eye.x+state.toolbarDrag.x.toFloat()*unit,eye.y-1f-state.toolbarDrag.y.toFloat()*unit,eye.z-2.5f)
        val timeBar=Vector3(bar.x,bar.y-(if(state.mode==EarthMode.TEMPERATURE)125f*unit else 0f),bar.z)
        timelinePanel?.let{transform(it).setPosition(Vector3(bar.x,timeBar.y+32f*unit,bar.z))}
        citiesPanel?.let{transform(it).setPosition(Vector3(bar.x-328f*unit,timeBar.y+44f*unit,bar.z+.015f))}
        toolsPanel?.let{transform(it).setPosition(Vector3(bar.x+328f*unit,timeBar.y+44f*unit,bar.z+.015f))}
        modulePanel?.let{transform(it).setPosition(Vector3(bar.x-450f*unit,bar.y+695f*unit,bar.z))}
        temperatureInfo?.let{transform(it).setPosition(Vector3(bar.x,bar.y-235f*unit,bar.z))}
        show(temperatureInfo,ready&&state.mode==EarthMode.TEMPERATURE)
        show(modulePanel,ready)
        show(timelinePanel,ready);show(citiesPanel,ready&&state.citiesOpen);show(toolsPanel,ready&&state.timeToolsOpen)
        val c=vec(center);val h=vec(head)
        val sunPosition=c+vec(light)*350.0
        sunEffect.enabled=globeOpacity>0.001f && veilOpacity>0.001f
        // Clip before the black veil begins showing VST: fade 36..42 degrees.
        // The veil itself feathers 42..58 degrees. Both share the opening spread.
        val sunFacing=GlobeQuaternion.align(GlobeVector3.Z,(h-sunPosition).normalized())
        val sunRelative=sunPosition-vec(eye)
        val toHead=h-sunPosition
        val visualScale=sqrt(toHead.dot(toHead))/350.0
        val sunRight=sunFacing.rotate(GlobeVector3.X)*(40.0*visualScale)
        val sunUp=sunFacing.rotate(GlobeVector3.Y)*(40.0*visualScale)
        val spread=veilProgress.coerceAtLeast(0.001f).toDouble()
        val edge=(1/sqrt(1+(spread*tan(Math.toRadians(42.0))).pow(2))).toFloat()
        val solid=(1/sqrt(1+(spread*tan(Math.toRadians(36.0))).pow(2))).toFloat()
        sunMaterials.forEach{
            it.setParameter("VeilCenter_in2",sdk(sunRelative))
            it.setParameter("VeilRight_in1",sdk(sunRight));it.setParameter("VeilUp_in1",sdk(sunUp))
            it.setParameter("VeilForward_in2",Vector3(0f,0f,-1f))
            it.setParameter("VeilMask_Remap_inlow",edge);it.setParameter("VeilMask_Remap_inhigh",solid)
            it.setParameter("VeilReveal_in2",globeOpacity*veilOpacity)
        }
        transform(sunEffect).apply{setPosition(sdk(sunPosition));setQuaternion(quat(sunFacing));setScaleVector(Vector3(visualScale.toFloat(),visualScale.toFloat(),visualScale.toFloat()))}
        val auditNow=android.os.SystemClock.elapsedRealtime()
        if(auditNow-lastSunAudit>=5000){
            lastSunAudit=auditNow
            android.util.Log.i("WorldClockSun","V36 sun cone="+Math.toDegrees(acos((-sunRelative.normalized().z).coerceIn(-1.0,1.0)))+
                "; edgeDot="+edge+"; solidDot="+solid+"; enabled="+sunEffect.enabled)
        }
        // PICO converts view pixels to metres using the current device UI scale.
        // 1dp is not 1mm; use the same density as the attachment content.
        val metresPerDp=content.convertSize(Vector3(uiDensity,0f,0f),ViewCoordinateSpace.Local,origin).x.toDouble()*CityLabelLayout.SCALE
        if(metresPerDp<=0.0||!metresPerDp.isFinite())return
        data class LabelFrame(val slot:Int,val city:ClockCity,val top:GlobeVector3,
            val alpha:Float,val layout:CityLabelLayout)
        val frames=mutableListOf<LabelFrame>()
        pins.indices.forEach{i->
            val pin=pins[i];val city=state.cities.getOrNull(i)
            if(pin.cityId!=city?.id){pin.cityId=city?.id;pin.visibility=PinVisibility()}
            if(!ready||city==null){
                pin.stem.enabled=false;pin.head.enabled=false;pin.line.enabled=false;show(pin.card,false)
                if(state.labelOpacity[i]!=0f)state.labelOpacity[i]=0f
                return@forEach
            }
            val n=cityDirection(city,center);val surface=c+n*24.0;val top=c+n*24.9
            val limb=PinVisibility.atLimb(n.dot((h-surface).normalized()))
            val pinAlpha=pin.visibility.update(limb,seconds)
            val visible=pinAlpha>0.001f
            pin.stemAlpha.opacity=pinAlpha;pin.headAlpha.opacity=pinAlpha
            pin.stem.enabled=visible;pin.head.enabled=visible
            transform(pin.stem).apply{setPosition(sdk(c+n*24.45));setScaleVector(Vector3(1f,.9f,1f));setQuaternion(quat(GlobeQuaternion.align(GlobeVector3.Y,n)))}
            transform(pin.head).setPosition(sdk(top))
            // Every label remains in the same upright stage XY plane. The entire plane
            // lies beyond the globe's frontmost point, so no edge can cut into the sphere.
            val layout=CityLabelLayout.place(c,top,metresPerDp)
            if(state.labelOnRight[i]!=layout.labelOnRight)state.labelOnRight[i]=layout.labelOnRight
            frames.add(LabelFrame(i,city,top,pinAlpha,layout))
        }
        val candidates=frames.filter{it.alpha>0.001f}.map{CityLabelCandidate(it.city.id,it.layout)}
        // Reserve the globe-height band. Respect unusually large platform UI scales too.
        val halfBand=max(26.0,candidates.sumOf{it.layout.halfHeight}+6.0*metresPerDp*max(0,candidates.size-1)+metresPerDp)
        val arranged=labelAvoidance.update(candidates,metresPerDp,c.y-halfBand,c.y+halfBand,seconds)
        frames.forEach{frame->
            val i=frame.slot;val pin=pins[i]
            val layout=arranged[frame.city.id]?:frame.layout
            // Only the geographical horizon fades a city. Crowding never hides one.
            val labelAlpha=frame.alpha
            val show=labelAlpha>0.001f
            // Compose attachments need view-layer alpha; model opacity does not fade their UI.
            if(abs(state.labelOpacity[i]-labelAlpha)>.002f)state.labelOpacity[i]=labelAlpha
            pin.lineAlpha.opacity=labelAlpha
            show(pin.card,show);pin.line.enabled=show
            if(show){
                pin.card?.let{transform(it).apply{setPosition(sdk(layout.center));setScaleVector(Vector3(CityLabelLayout.SCALE,CityLabelLayout.SCALE,CityLabelLayout.SCALE));setQuaternion(Quat(0f,0f,0f,1f))}}
                val elbow=layout.elbow
                fun segment(e:Entity,a:GlobeVector3,b:GlobeVector3){val d=b-a
                    transform(e).apply{setPosition(sdk((a+b)*0.5));setScaleVector(Vector3(1f,sqrt(d.dot(d)).toFloat(),1f));setQuaternion(quat(GlobeQuaternion.align(GlobeVector3.Y,d)))}
                }
                segment(pin.line,frame.top,elbow)
            }
        }
        if(auditNow-lastLabelAudit>=5000){
            lastLabelAudit=auditNow
            val cards=arranged.values.toList()
            var overlaps=0
            for(i in cards.indices)for(j in i+1 until cards.size){val a=cards[i];val b=cards[j]
                if(abs(a.center.x-b.center.x)<a.halfWidth+b.halfWidth-.0001 &&
                    abs(a.center.y-b.center.y)<a.halfHeight+b.halfHeight-.0001)overlaps++
            }
            android.util.Log.i("WorldClockLabels","V50 front="+candidates.size+"; cards="+arranged.size+
                "; overlaps="+overlaps+"; ids="+arranged.keys.joinToString()+"; metresPerDp="+metresPerDp)
        }
    }
}
