package com.wupengfei.worldclock.content

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*
import kotlinx.coroutines.*
import kotlin.math.*

/** Three fixed batches: surface points, procedural waves, and shared system-font digits.
 * No per-event Entity/AttachmentPanel; no image sequence or per-frame mesh generation. */
internal class EarthquakeRenderer private constructor(
    private val coordinates:CoordinateRenderer,private val origin:Entity,
    private val content:SpatialViewContent,private val attachments:SpatialViewAttachments,
    private val clock:ClockState,private val unit:Double
):AutoCloseable {
    private val state get()=clock.earthquakes
    private val root=Entity()
    private val rootAlpha=OpacityControllerComponent(0f)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val materials=mutableListOf<Material>()
    private val textures=mutableListOf<TextureResource>()
    private var bundle:AssetBundle?=null
    private val batches=mutableListOf<QuakeDecalBatch>()
    private lateinit var shader:ShaderGraphMaterial
    private lateinit var points:QuakeDecalBatch
    private lateinit var waves:QuakeDecalBatch
    private lateinit var digits:QuakeDecalBatch
    private val locals=mutableMapOf<String,GlobeVector3>()
    private val tokens=mutableMapOf<String,Int>()
    private val births=mutableMapOf<String,Double>()
    private var groups=listOf<QuakeCluster>()
    private var previousGroups=mapOf<String,String>()
    private var rippleEvents=emptyList<QuakeEvent>()
    private var coverageDirty=true
    private var groupTimer=1.0
    private var auditTimer=0.0
    private var lastRevision=-1
    private var lastSelected:String?=null
    private var selectedSeed=0.0
    private var lastVisibleIds=emptySet<String>()
    private var lastEvents:List<QuakeEvent>?=null
    private var elapsed=0.0
    private var fade=0f
    private var closed=false
    private var hadContent=false
    private var head=GlobeVector3.ZERO
    private var center=GlobeVector3.ZERO
    private var surfaceFrame=QuakeSurfaceFrame(GlobeVector3.ZERO,GlobeVector3.X,GlobeVector3.Y,GlobeVector3.Z)
    private val surfaceToOrigin=EntityTransformPath(coordinates.surfaceEntity,origin)
    private var surfaceToEarth:EntityTransformPath?=null
    private data class Decal(val point:GlobeVector3,val angle:Double,val wave:Boolean,val slot:Int,val seed:Double,val birth:Double,val token:Int,val magnitudeBand:Int)
    private fun failure(e:Throwable){state.renderFailure="地震图层暂不可用，可切回时钟后重试";Log.e("WorldClockQuakes","Decal rendering",e)}
    private suspend fun initialize(){
        root.setName("EarthquakeDecals_V76");root.components.set(rootAlpha);root.enabled=false
        check(coordinates.surfaceEntity.addChild(root))
        val asset=AssetBundle.load("asset://QuakeDecals.bundle");bundle=asset
        val template=asset.loadModelSuspend("QuakeDecalsV78")
        fun model(e:Entity):ModelComponent?=e.components[ModelComponent::class.java]?:e.getChildren().firstNotNullOfOrNull{model(it)}
        try{
            val m=checkNotNull(model(template));shader=m.materials[0] as ShaderGraphMaterial
            shader.toGlobal();materials.add(shader)
            val mesh=m.mesh;template.destroy();if(mesh.valid)mesh.close()
        }finally{template.destroy()}
        shader.setDepthTest(true);shader.setDepthWrite(false);shader.setCullingMode(MaterialCullingMode.BACK)
        shader.setParameter("Animation_in2",0f);shader.setParameter("SelectionDelta_in2",-1f)
        shader.setParameter("Cycles_in2",QuakeStyle.RING_SECONDS.toFloat())
        shader.setParameter("WaveStrength_bg",QuakeStyle.WAVE_NORMAL_ALPHA)
        shader.setParameter("WaveStrength_fg",QuakeStyle.WAVE_SELECTED_ALPHA)
        check(QuakeStyle.WAVE_SELECTED_DEGREES+QuakeStyle.WAVE_LINE_EDGE_DEGREES<QuakeStyle.WAVE_PATCH_DEGREES)
        Log.i("WorldClockQuakes","V89 rippleRangeScale=1.6 selectedRadiusDeg=9.6 normalM7RadiusDeg=5.6 patchHalfAngleDeg=10.4 rings=3 cycleSeconds=2.4; visualMarkerNotImpactArea")
        repeat(QuakeStyle.MAX_RIPPLE_GROUPS){shader.setParameter("Gain${it}_in2",if(it==0)1f else 0f)}
        repeat(3){
            shader.setParameter("Ring${it}Offset_in2",(1-it*QuakeStyle.RING_STAGGER/QuakeStyle.RING_SECONDS).toFloat())
            shader.setParameter("Ring${it}Radius_in2",(QuakeStyle.WAVE_START_DEGREES/QuakeStyle.WAVE_PATCH_DEGREES).toFloat())
            shader.setParameter("Ring${it}Range_bg",((QuakeStyle.WAVE_NORMAL_DEGREES-QuakeStyle.WAVE_START_DEGREES)/QuakeStyle.WAVE_PATCH_DEGREES).toFloat())
            shader.setParameter("Ring${it}Range_fg",((QuakeStyle.WAVE_SELECTED_DEGREES-QuakeStyle.WAVE_START_DEGREES)/QuakeStyle.WAVE_PATCH_DEGREES).toFloat())
            shader.setParameter("Ring${it}Edge_low",(QuakeStyle.WAVE_LINE_CORE_DEGREES/QuakeStyle.WAVE_PATCH_DEGREES).toFloat())
            shader.setParameter("Ring${it}Edge_high",(QuakeStyle.WAVE_LINE_EDGE_DEGREES/QuakeStyle.WAVE_PATCH_DEGREES).toFloat())
        }
        check(abs(shader.getParameter<Float>("SelectionDelta_in2")+1f)<.0001f)
        fun batch(name:String,mat:Material)=QuakeDecalBatch(name,root,mat,scope,::failure).also{batches.add(it)}
        points=batch("QuakePoints_Batch",shader);waves=batch("QuakeWaves_Batch",shader)
        val fontBitmap=Bitmap.createBitmap(528,64,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(fontBitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.WHITE;textSize=46f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);textAlign=Paint.Align.CENTER}
        "0123456789+".forEachIndexed{i,ch->canvas.drawText(ch.toString(),i*48f+24,49f,paint)}
        val font=try{TextureResource.create(fontBitmap).also{it.toGlobal();textures.add(it)}}finally{fontBitmap.recycle()}
        val fontMat=UnlitMaterial.create(BlendingMode.TRANSPARENT).apply{
            toGlobal();materials.add(this);setBaseColor(Color4(.94f,.97f,1f,1f));setBaseColorTexture(font)
            setApplyToneMapping(false);setDepthTest(true);setDepthWrite(false);setCullingMode(MaterialCullingMode.BACK)
        }
        digits=batch("QuakeCounts_Batch",fontMat)
        Log.i("WorldClockQuakes","V78 ready: fixedBatches=3 proceduralMaterial=1 waveTextures=0 fontAtlas=528x64 quakePanels=1 depthTest=true selectedAccent=#745BFF")
    }
    private fun refreshFrame(){
        surfaceFrame=surfaceToOrigin.frame()
    }
    private fun local(event:QuakeEvent)=locals.getOrPut(event.id){coordinates.mapping.point(event.latitude,event.longitude)*QuakeStyle.RADIUS_SCALE}
    private fun world(event:QuakeEvent)=surfaceFrame.point(local(event))
    private fun token(event:QuakeEvent)=tokens.getOrPut(event.id){tokens.size+1}
    fun direction(event:QuakeEvent,c:Vector3):GlobeVector3 {refreshFrame();return (world(event)-CoordinateRenderer.vec(c)).normalized()}
    fun directionInEarth(event:QuakeEvent,earth:Entity):GlobeVector3 {
        // Exact same UV point as the decal, including all imported child transforms,
        // tilt and geographic spin. Excludes Earth's animated presentation transform.
        val point=coordinates.mapping.point(event.latitude,event.longitude)*QuakeStyle.RADIUS_SCALE
        val path=surfaceToEarth?:EntityTransformPath(coordinates.surfaceEntity,earth).also{surfaceToEarth=it}
        return path.frame().point(point).normalized()
    }
    fun nativeDirection(event:QuakeEvent,c:Vector3)=
        (CoordinateRenderer.vec(coordinates.surfaceEntity.convertPositionTo(CoordinateRenderer.sdk(local(event)),origin))-CoordinateRenderer.vec(c)).normalized()
    private fun front(event:QuakeEvent):Boolean {val p=world(event);return (p-center).normalized().dot((head-p).normalized())>.045}
    private fun project(p:GlobeVector3):Pair<Double,Double>{
        val distance=max(.1,head.z-center.z);val depth=max(.1,head.z-p.z)
        return (p.x-head.x)*distance/depth/unit to (p.y-head.y)*distance/depth/unit
    }
    fun selectAt(coordinate:GeoCoordinate){
        if(clock.mode!=EarthMode.EARTHQUAKE||fade<.95f)return
        refreshFrame()
        val p=coordinates.mapping.point(coordinate.latitude,coordinate.longitude?:0.0)*QuakeStyle.RADIUS_SCALE
        val hit=project(surfaceFrame.point(p))
        val closest=groups.filter{front(it.representative)}.minByOrNull{g->val q=project(world(g.representative));hypot(q.first-hit.first,q.second-hit.second)}?:return
        val q=project(world(closest.representative));if(hypot(q.first-hit.first,q.second-hit.second)>24)return
        if(closest.events.size>1)state.selectGroup(closest.events)else state.select(closest.representative)
        clock.touchUi();Log.i("WorldClockQuakes","V76 selected group=${closest.events.size} id=${closest.representative.id}")
    }
    private fun request(batch:QuakeDecalBatch,data:List<Decal>){batch.request(data){QuakeDecalGeometry().apply{
        data.forEach{patch(it.point,it.angle,it.wave,it.slot,it.seed,it.birth,it.token,it.magnitudeBand)}
    }}}
    private fun seed(e:QuakeEvent)=(e.id.hashCode().toLong() and 65535L)/65536.0
    fun update(seconds:Double,c:Vector3,h:Vector3,visibility:Float,ready:Boolean){
        if(closed)return
        val dt=seconds.coerceIn(0.0,.1);elapsed+=dt;head=CoordinateRenderer.vec(h);center=CoordinateRenderer.vec(c)
        val active=clock.mode==EarthMode.EARTHQUAKE&&ready
        val step=(dt/QuakeStyle.FADE_SECONDS).toFloat();fade=if(active)min(1f,fade+step)else max(0f,fade-step)
        root.enabled=fade>.001f;rootAlpha.opacity=fade*visibility
        batches.forEach{it.update()}
        if(!active&&fade==0f){
            if(hadContent){request(points,emptyList());request(waves,emptyList());digits.request(emptyList<String>()){QuakeDecalGeometry()}
                locals.clear();births.clear();tokens.clear();groups=emptyList();previousGroups=emptyMap();lastVisibleIds=emptySet();lastEvents=null;hadContent=false;coverageDirty=true;rippleEvents=emptyList()
                Log.i("WorldClockQuakes","V76 inactive: fixed batch handles retained; no event entities or animation updates")}
            state.clusterGroups=emptyList();return
        }
        hadContent=true;refreshFrame()
        if(lastRevision!=state.revision){lastRevision=state.revision;locals.clear();groupTimer=1.0;coverageDirty=true}
        val events=state.visible;val ids=if(events===lastEvents)lastVisibleIds else events.map{it.id}.toSet()
        lastEvents=events
        if(ids!=lastVisibleIds){
            groupTimer=1.0;coverageDirty=true;(births.keys-ids).forEach{births.remove(it)}
            (ids-lastVisibleIds).forEach{births[it]=if(state.playing)elapsed else -1.0};lastVisibleIds=ids
        }
        groupTimer+=dt
        if(lastSelected!=state.selectedId){
            lastSelected=state.selectedId;groupTimer=1.0;coverageDirty=true
            selectedSeed=QuakeWaveSchedule.selectionSeed(elapsed)
            Log.i("WorldClockQuakes","V80 pinned selection=${state.selectedId} immediate=true continuous=true")
        }
        if(coverageDirty){
            coverageDirty=false
            groups=QuakeCoverage.group(events,previousGroups,state.selectedId)
            previousGroups=groups.flatMap{g->g.events.map{it.id to g.representative.id}}.toMap()
            check(previousGroups.keys==ids){"Incomplete global quake coverage"}
            request(points,groups.map{g->val e=g.representative;val r=QuakeRules.pointDiameter(e.magnitude)*.0015
                Decal(local(e),r/(sqrt(local(e).dot(local(e)))*.65),false,0,seed(e),births[e.id]?:-1.0,token(e),QuakeRules.magnitudeBand(e.magnitude))})
            val texts=groups.filter{it.events.size>1}.map{local(it.representative) to it.events.size.toString()}
            digits.request(texts){QuakeDecalGeometry().apply{texts.forEach{text(it.first,it.second)}}}
            Log.i("WorldClockQuakes","V77 global coverage events="+events.size+" represented="+previousGroups.size+" points="+groups.size+" cameraIndependent=true")
        }
        if(groupTimer>=.10){
            groupTimer=0.0
            val frontGroups=groups.filter{front(it.representative)}
            state.clusterGroups=frontGroups.filter{it.events.size>1}.map{it.events}
            val reference=if(state.replay)state.replayTime?:clock.now else clock.now
            rippleEvents=QuakeWaveSchedule.choose(events,reference,state.selectedId)
            request(waves,rippleEvents.map{e->Decal(local(e),Math.toRadians(QuakeWaveSchedule.patchDegrees(e,e.id==state.selectedId)),true,0,
                if(e.id==state.selectedId)selectedSeed else seed(e),-1.0,token(e),QuakeRules.magnitudeBand(e.magnitude))})
        }
        shader.setParameter("Animation_in2",elapsed.toFloat())
        val selected=state.selected
        val selection=selected?.let{token(it).toFloat()}?:-1f
        if(shaderSelection!=selection){shaderSelection=selection;shader.setParameter("SelectionDelta_in2",selection)
            Log.i("WorldClockQuakes","V78 selection=${selected?.id} token=$selection accent=${if(selected!=null)"#745BFF" else "magnitude"}")}
        state.selectedFront=selected==null||front(selected)
        auditTimer+=dt
        if(auditTimer>=5){auditTimer=0.0;Log.i("WorldClockQuakes","V84 active=$active filtered=${state.filtered.size} visible=${events.size} globalDecals=${groups.size} frontDecals=${groups.count{front(it.representative)}} clusters=${state.clusterGroups.size} waves=${rippleEvents.size} frontWaves=${rippleEvents.count{front(it)}} rule=24h_M4plus_OR_7d_M6plus noSlotCap=true animationSeconds=$elapsed fixedBatches=3 meshHandles=${batches.sumOf{it.liveMeshes()}} triangles=${batches.sumOf{it.triangles}} meshCommits=${batches.map{it.commits}} nativeQuakePanels=1 waveTextures=0 selected=${selected?.id} playing=${state.playing}")}
    }
    private var shaderSelection=-1f
    override fun close(){
        if(closed)return;closed=true;scope.cancel();batches.forEach{it.close()};root.destroy()
        locals.clear();tokens.clear();births.clear()
        materials.forEach{if(it.valid)it.close()};textures.forEach{if(it.valid)it.close()};bundle?.close();bundle=null
        state.pause();state.clusterGroups=emptyList()
        Log.i("WorldClockQuakes","V76 released: batches=${batches.sumOf{it.liveMeshes()}} resources=${materials.count{it.valid}+textures.count{it.valid}}")
    }
    companion object {
        suspend fun create(coordinates:CoordinateRenderer,origin:Entity,content:SpatialViewContent,attachments:SpatialViewAttachments,clock:ClockState,density:Float):EarthquakeRenderer{
            val unit=content.convertSize(Vector3(density,0f,0f),ViewCoordinateSpace.Local,origin).x.toDouble()*QuakeStyle.PICK_SCALE
            require(unit.isFinite()&&unit>0){"Invalid platform UI scale"}
            val renderer=EarthquakeRenderer(coordinates,origin,content,attachments,clock,unit)
            try{renderer.initialize();currentCoroutineContext().ensureActive();return renderer}catch(e:Throwable){renderer.close();throw e}
        }
    }
}
