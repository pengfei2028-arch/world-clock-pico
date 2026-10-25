package com.wupengfei.worldclock.content

import androidx.compose.ui.draw.alpha

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialView
import kotlinx.coroutines.*
import kotlin.math.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.pico.spatial.tracking.hmd.HMDTrackingProvider

private class EarthResources {
    var closed = false; private set
    var startupEye=Vector3(0f,1.5f,0f)
    var bundle: AssetBundle? = null
    var effectsBundle: AssetBundle? = null
    var cloudsBundle: AssetBundle? = null
    var temperatureRenderer: TemperatureRenderer? by mutableStateOf(null)
    var coordinateRenderer:CoordinateRenderer?=null
    var earthquakeRenderer:EarthquakeRenderer?=null
    var coordinateHit:((Vector3)->GeoCoordinate?)?=null
    var interactionReady = false
    var handToStage: ((Vector3) -> Vector3)? = null
    var headInStage: (() -> Vector3)? = null
    var viewRotationInStage: (() -> GlobeQuaternion)? = null
    var frameSubscription: Cancellable? = null
    val entities = mutableListOf<Entity>()
    val textures = mutableListOf<TextureResource>()
    val materials = mutableListOf<Material>()
    val importedResources = linkedSetOf<Resource>()
    // Bundle.close / Entity.destroy do not guarantee immediate native release
    // while Kotlin frame/material references are still alive. Own every imported
    // handle explicitly so returning from Shared never overlaps two 8K scenes.
    fun ownModelResources(root:Entity, samplers:List<String> = emptyList()) {
        root.components[ModelComponent::class.java]?.let { model ->
            importedResources.add(model.mesh)
            model.materials.forEach { material ->
                importedResources.add(material)
                // SDK 6.1.9 returns an empty getParameterNames() on these imported
                // graphs. Use the known exported sampler contract, not reflection.
                if(material is ShaderGraphMaterial)samplers.forEach { name ->
                        importedResources.add(material.getParameter(name,TextureResource::class.java))
                    }
            }
        }
        root.getChildren().forEach { ownModelResources(it,samplers) }
    }
    fun ensureOpen(entity:Entity?=null) {
        if(closed){entity?.destroy();throw CancellationException("Immersive renderer disposed")}
    }
    fun close() {
        if(closed)return
        closed=true
        interactionReady = false
        frameSubscription?.cancel()
        frameSubscription = null
        handToStage = null; headInStage = null; viewRotationInStage = null
        temperatureRenderer?.close();temperatureRenderer=null
        earthquakeRenderer?.close();earthquakeRenderer=null
        coordinateHit=null;coordinateRenderer?.close();coordinateRenderer=null
        entities.forEach { it.destroy() }
        entities.clear()
        materials.forEach { if(it.valid)it.close() };materials.clear()
        textures.forEach { if(it.valid)it.close() }
        textures.clear()
        importedResources.forEach { if(it.valid)it.close() }
        Log.i("WorldClockLifecycle","V61 explicitly released imported handles="+importedResources.size+" remainingValid="+importedResources.count{it.valid})
        importedResources.clear()
        bundle?.close()
        bundle = null
        effectsBundle?.close();effectsBundle=null
        cloudsBundle?.close();cloudsBundle=null
        Log.i("WorldClockLifecycle","V37 scene resources released; frame updates stopped")
    }
}

// Project art choices: 10x scene scale and 5x night emission; not PICO UI defaults.
private const val WORLD_SCALE = 10f
private const val NIGHT_STRENGTH = 0.9f
// Reversible source-cloud look trial; static packed Blender map, not satellite weather.
private const val ENABLE_SOURCE_CLOUDS = true
// Project framing: 20% smaller globe, center 4 degrees below opening eye level.
private const val EARTH_DISPLAY_SCALE = 1.2f * WORLD_SCALE
private const val EARTH_CENTER_DROP_M = 4.1956f
// Project animation choice: scene-frame updates, one revolution every two minutes.

// Official SDK 6.1.9. This is a look-development Stage, not a device benchmark.
@Composable
fun HomeStage() {
    key(WorldClockSession.clock.immersiveRetry){ImmersiveHomeStage()}
}
@Composable
private fun ImmersiveHomeStage() {
    val resources = remember { EarthResources() }
    val motion = WorldClockSession.motion
    val entrance = WorldClockSession.entrance
    val context = LocalContext.current
    val clock = WorldClockSession.clock
    DisposableEffect(clock){
        WorldClockSession.immersiveResources=true
        clock.ready=false;clock.startupFailure="";WorldClockSession.loadFailure=null
        if(com.wupengfei.worldclock.BuildConfig.DEBUG)TemperatureTestAccess.state=clock
        onDispose{if(TemperatureTestAccess.state===clock){TemperatureTestAccess.state=null;TemperatureTestAccess.renderer=null}}
    }
    val quakeRepository=remember { EarthquakeRepository(context) }
    LaunchedEffect(clock.mode,clock.earthquakes.period,clock.earthquakes.refreshRequest){
        val q=clock.earthquakes
        if(clock.mode==EarthMode.EARTHQUAKE){
            val period=q.period
            if(q.catalog==null)try{quakeRepository.cached(period)?.let{q.accept(it);q.status="缓存目录 · 正在检查更新"}}catch(e:CancellationException){throw e}catch(_:Exception){}
            try{
                while(isActive){
                    q.loading=true
                    try{
                        val catalog=quakeRepository.refresh(period);ensureActive();q.status="目录已更新";q.accept(catalog)
                        Log.i("WorldClockQuakes","USGS period=${period.days} generated=${catalog.generatedAt} fetched=${catalog.fetchedAt} count=${catalog.events.size} partial=${catalog.partial}")
                    }catch(e:CancellationException){throw e}catch(e:Exception){
                        q.status=if(q.catalog!=null)"更新失败 · 使用已缓存周期" else "无法连接数据源 · 请重试"
                        Log.w("WorldClockQuakes","USGS refresh failed; keeping cache",e)
                    }finally{q.loading=false}
                    delay(60_000)
                }
            }finally{q.loading=false;q.pause()}
        }
    }
    val temperatureRepository=remember { TemperatureRepository(context) }
    LaunchedEffect(Unit) {
        try { if(clock.temperature.manifest==null){val m=temperatureRepository.initial();clock.temperature.manifest=m;clock.temperature.now(ClockTimeSource.read().instant)}
            clock.temperature.status=if(temperatureRepository.baseUrl.isBlank())"内置真实样例 · 自动更新待配置" else "缓存数据"
        }catch(e:CancellationException){throw e}catch(e:Exception){clock.temperature.status="气温数据不可用";Log.e("WorldClockTemperature","Initial data",e)}
    }
    LaunchedEffect(clock.temperature.refreshRequest) {
        while(isActive && clock.temperature.manifest==null)delay(200)
        while(isActive){
            val previous=clock.temperature.manifest
            if(previous!=null && temperatureRepository.baseUrl.isNotBlank())try {
                val next=temperatureRepository.refresh(previous)
                val time=clock.temperature.requested?.validTime?:clock.now
                clock.temperature.manifest=next
                clock.temperature.index=next.nearest(if(clock.temperature.autoNow)clock.now else time)
                clock.temperature.status="缓存已校验"
            }catch(e:CancellationException){throw e}catch(e:Exception){clock.temperature.status="更新失败 · 保留原数据";Log.w("WorldClockTemperature","Refresh",e)}
            delay(3_600_000)
        }
    }
    LaunchedEffect(clock.mode,clock.temperature.manifest,clock.temperature.index,resources.temperatureRenderer){
        val m=clock.temperature.manifest;val renderer=resources.temperatureRenderer
        if(clock.mode==EarthMode.TEMPERATURE&&m!=null&&renderer!=null){
            clock.temperature.loading=true
            try { delay(70);val (frame,grid)=temperatureRepository.frame(m,clock.temperature.index);renderer.prepare(m,frame,grid) }
            catch(e:CancellationException){throw e}catch(e:Exception){clock.temperature.loading=false;clock.temperature.status="载入失败 · 保留上一帧";Log.e("WorldClockTemperature","Frame load",e)}
        }
    }
    LaunchedEffect(Unit) {
        try {
            if(clock.catalog==null){val catalog=withContext(Dispatchers.IO) {
                CityCatalog.read(context.assets.open("cities/catalog.tsv").bufferedReader()).apply {
                    bindPinPositions(context.assets.open("cities/pin_positions.tsv").bufferedReader())
                    val rules=CityTimeZones.read(context.assets.open("cities/timezones.tsv").bufferedReader())
                    cities.forEach{it.timeZones=rules}
                }
            }
            clock.catalog=catalog
            val ids=context.getSharedPreferences("world_clock",0).getString("cityIds", "").orEmpty().split(',')
            clock.cities=ids.mapNotNull{catalog.city(it)}.distinctBy{it.id}.take(8)
            }
        } catch(error: Exception) { clock.notice="城市库载入失败，请重新启动";Log.e("WorldClockEarth","City catalog failed",error) }
        while(isActive){
            val reading=ClockTimeSource.read();clock.now=reading.instant
            clock.clockNotice=if(reading.corrected) "设备时间有误，已按网络时间校准" else ""
            if(clock.mode==EarthMode.TEMPERATURE&&clock.temperature.autoNow)clock.temperature.now(reading.instant)
            delay(1000)
        }
    }
    val weatherRepository=remember { WeatherRepository(context) }
    LaunchedEffect(clock.cities.map { it.id }) {
        val slots=Semaphore(2)
        while(isActive){
            val cities=clock.cities.toList()
            val current=ClockTimeSource.read().instant
            val readings=coroutineScope {
                cities.map { city -> async { slots.withPermit { city.id to weatherRepository.current(city,current) } } }.awaitAll()
            }
            ensureActive()
            clock.weather=readings.toMap()
            delay(60_000)
        }
    }
    fun nowSeconds() = android.os.SystemClock.elapsedRealtimeNanos() / 1_000_000_000.0
    val hmd = remember { HMDTrackingProvider() }
    DisposableEffect(Unit) {
        hmd.start()
        clock.onTimeScrub={held->
            motion.setTimelineHeld(held,nowSeconds())
            Log.i("WorldClockTimeline","V43 held="+held+"; presentation="+motion.presentation+"; idleDegrees="+motion.idleDegrees)
        }
        onDispose { clock.onTimeScrub=null;clock.toolbarHandPoint=null;clock.toolbarHandGain=null; hmd.stop(); resources.close(); clock.ready=false; WorldClockSession.immersiveResources=false }
    }

    // SDK 6.1.9 caches attachment IDs, so allocation is monotonic within this
    // SpatialView. Never remove/re-add cached destroyed entities.
    var quakePanelAllocated by remember { mutableStateOf(false) }
    LaunchedEffect(clock.ready,clock.mode){
        if(clock.ready&&clock.mode==EarthMode.EARTHQUAKE){
            quakePanelAllocated=true
        }
    }
    SpatialView(
        modifier = Modifier.pointerInput(context) {
            val input = GlobePointerInput(motion)
            val coordinateTap=CoordinateTap()
            var lastInputLog = Double.NEGATIVE_INFINITY
            try {
                detectSpatialPointerEvent(
                    context = context,
                    targetedToEntity = TargetEntity.any { it.getName() == "EarthInteraction" }
                ) { pointers ->
                    var consumed = false
                    pointers.forEach { pointer ->
                        if (!resources.interactionReady || WorldClockSession.busy) return@forEach
                        val previousOwner = input.activePointerId
                        val now = nowSeconds()
                        if (previousOwner == null && pointer.isDownEvent()) {
                            clock.manualInteraction()
                            resources.viewRotationInStage?.invoke()?.let { input.setViewOrientation(it) }
                            if((clock.coordinates.enabled||clock.mode==EarthMode.EARTHQUAKE)&&!clock.uiManipulating){
                                val hit=pointer.position3D
                                coordinateTap.begin(pointer.pointerId.value,resources.coordinateHit?.invoke(Vector3(hit.x,hit.y,hit.z)),now)
                            }else coordinateTap.cancel()
                        }
                        val rawHand = pointer.inputDevicePose.rawPosition
                        val hand = resources.handToStage?.invoke(Vector3(rawHand.x, rawHand.y, rawHand.z))
                        val head = resources.headInStage?.invoke()
                        // DOWN/UP remain authoritative even if tracking coordinates are unavailable.
                        fun toInputVector(v: Vector3?) = GlobeVector3(
                            v?.x?.toDouble() ?: Double.NaN, v?.y?.toDouble() ?: Double.NaN, v?.z?.toDouble() ?: Double.NaN)
                        if(previousOwner==null||previousOwner==pointer.pointerId.value)coordinateTap.position(toInputVector(hand)-toInputVector(head))
                        // Remember movement before UP resets the rotation input's state.
                        if(previousOwner==pointer.pointerId.value)coordinateTap.observe(input.moving,input.rejectedSamples>0)
                        consumed = input.updateHand(pointer.pointerId.value, pointer.isDownEvent(), pointer.pressed,
                            toInputVector(hand), toInputVector(head), now) || consumed
                        coordinateTap.observe(input.moving,input.rejectedSamples>0)
                        if(previousOwner==pointer.pointerId.value&&!pointer.pressed){
                            val selected=coordinateTap.end(pointer.pointerId.value,now)
                            if((clock.coordinates.enabled||clock.mode==EarthMode.EARTHQUAKE)&&!clock.uiManipulating&&selected!=null){
                                if(clock.mode==EarthMode.EARTHQUAKE)resources.earthquakeRenderer?.selectAt(selected)
                                else clock.coordinates.select(selected)
                                motion.readCoordinates(now)
                                Log.i("WorldClockCoordinates","V65 Full selected lat=${selected.latitude} lon=${selected.longitude}; original mesh UV")
                            }
                        }
                        if (pointer.pressed && input.activePointerId == pointer.pointerId.value && now - lastInputLog >= 0.5) {
                            lastInputLog = now
                            Log.i("WorldClockEarth", "V31 hand sample: raw=$rawHand; handM=$hand; headM=$head; " +
                                "moving=" + input.moving + "; rejected=" + input.rejectedSamples +
                                "; orientation=" + motion.presentation + "; tiltError=" + motion.tiltErrorDegrees)
                        }
                        if (previousOwner == null && input.activePointerId != null) {
                            Log.i("WorldClockEarth", "V31 pointer DOWN: id=" + input.activePointerId + "; kind=" + pointer.kind)
                        } else if (previousOwner != null && input.activePointerId == null) {
                            Log.i("WorldClockEarth", "V31 pointer UP: id=$previousOwner")
                        }
                    }
                    // Empty collections / no movement cannot release an existing hold.
                    consumed
                }
            } finally {
                coordinateTap.cancel()
                input.cancel(nowSeconds())
                Log.i("WorldClockEarth", "V31 pointer scope disposed")
            }
        },
        attachments = {
            AttachmentPanel("cities",alignment=AttachmentPanelComponent.Alignment.BOTTOM_LEFT) { ClockTheme { CityPanel(clock,context) } }
            AttachmentPanel("timeline",alignment=AttachmentPanelComponent.Alignment.TOP_CENTER) { ClockTheme { TimePanel(clock) } }
            AttachmentPanel("time_tools",alignment=AttachmentPanelComponent.Alignment.BOTTOM_RIGHT) { ClockTheme { TimeOptionsPanel(clock) } }
            if(quakePanelAllocated)AttachmentPanel("quake_panel",alignment=AttachmentPanelComponent.Alignment.BOTTOM_CENTER){ClockTheme{EarthquakePanel(clock)}}
            AttachmentPanel("coordinate_card",alignment=AttachmentPanelComponent.Alignment.CENTER){ClockTheme{
                CoordinateCard(clock.coordinates.selection,Modifier.alpha(clock.coordinates.panelAlpha))
            }}
            for(i in CoordinateRuler.slots.indices){
                AttachmentPanel("coordinate_ruler_$i",alignment=AttachmentPanelComponent.Alignment.CENTER){ClockTheme{
                    CoordinateRulerLabel(clock.coordinates,i)
                }}
            }
            for(slot in 0 until 8) {
                AttachmentPanel("city_card_"+slot,alignment=AttachmentPanelComponent.Alignment.CENTER) { ClockTheme { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.alpha(clock.labelOpacity[slot])) { CityPinCard(clock.cities.getOrNull(slot),clock,clock.labelOnRight[slot]) } } }
            }
        },
        initial = { content, attachments ->
            try {
                resources.ensureOpen()
                // The Stage is aligned to the user's facing direction on opening.
                // Sample eye height once; the scene remains fixed when the user turns.
                val stageOrigin = Entity()
                resources.entities.add(stageOrigin)
                content.addEntity(stageOrigin)
                resources.handToStage = { raw -> content.convertPosition(raw, ViewCoordinateSpace.Global, stageOrigin) }
                val toolbarUnit=content.convertSize(Vector3(context.resources.displayMetrics.density,0f,0f),ViewCoordinateSpace.Local,stageOrigin).x*3.5f
                clock.toolbarHandPoint={x,y,z->
                    val point=content.convertPosition(Vector3(x,y,z),ViewCoordinateSpace.Global,stageOrigin)
                    androidx.compose.ui.geometry.Offset(point.x/toolbarUnit,-point.y/toolbarUnit)
                }
                resources.headInStage = { stageOrigin.convertPositionFrom(hmd.latestData.hmdPose.position, null) }
                resources.viewRotationInStage = {
                    val q = stageOrigin.convertRotationFrom(hmd.latestData.hmdPose.rotation, null)
                    GlobeQuaternion(q.x.toDouble(),q.y.toDouble(),q.z.toDouble(),q.w.toDouble()).normalized()
                }
                val pose = withTimeoutOrNull(1500L) {
                    hmd.dataFlow.first().hmdPose
                } ?: hmd.latestData.hmdPose
                resources.ensureOpen()
                val trackedEye = stageOrigin.convertPositionFrom(pose.position, null)
                val eye = if (trackedEye.y.isFinite() && trackedEye.y in 0.3f..2.6f) {
                    trackedEye
                } else Vector3(0f, 1.5f, 0f)
                resources.startupEye=eye
                clock.toolbarHandGain={x,y,z->
                    val hand=content.convertPosition(Vector3(x,y,z),ViewCoordinateSpace.Global,stageOrigin)
                    val head=resources.headInStage?.invoke()?:eye
                    ToolbarDrag.depthGain((eye.z-2.5f-head.z).toDouble(),(hand.z-head.z).toDouble())
                }
                // Black forward cap only. Outside its feathered edge, Mixed Stage shows VST.
                val veilTexture = TextureResource.load("ForwardVeilAlpha.png", LoadType.FROM_ASSETS,
                    TextureCreateOption().apply {
                        colorSpace = TextureColorSpace.RAW
                        textureEncoding = TextureEncoding.LINEAR
                    })
                resources.textures.add(veilTexture)
                val veilMaterial = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                    setBaseColor(Color4(1f, 1f, 1f, 1f))
                    setBaseColorTexture(veilTexture)
                    setCullingMode(MaterialCullingMode.NONE)
                    setDepthTest(true)
                    setDepthWrite(false)
                    setApplyToneMapping(false)
                }
                resources.materials.add(veilMaterial)
                fun replaceMeshMaterial(entity: Entity, material: Material): Int {
                    var count = 0
                    entity.components[ModelComponent::class.java]?.let { model ->
                        if (model.materials.size == 0) model.materials.add(material)
                        else for (i in 0 until model.materials.size) model.materials[i] = material
                        count++
                    }
                    entity.getChildren().forEach { count += replaceMeshMaterial(it, material) }
                    return count
                }
                val veil = Entity.loadSuspend("asset://ForwardVeil.glb")
                resources.ensureOpen(veil)
                resources.entities.add(veil)
                val veilOpacity = OpacityControllerComponent(0f)
                veil.components.set(veilOpacity)
                check(replaceMeshMaterial(veil, veilMaterial) == 1)
                veil.components[TransformComponent::class.java]?.apply {
                    setPosition(eye)
                    setScaleVector(Vector3(WORLD_SCALE, WORLD_SCALE, WORLD_SCALE))
                }
                content.addEntity(veil)
                Log.i("WorldClockEarth", "V06 Mixed forward veil loaded; solid +/-42deg, feather to +/-58deg, rear open; eye=$eye")
                val bundle = AssetBundle.load("asset://Earth.bundle")
                check(bundle.valid) { "Earth bundle is invalid" }
                resources.bundle = bundle
                val earth = bundle.loadModelSuspend("EarthSwanV51")
                resources.ensureOpen(earth)
                resources.entities.add(earth)
                resources.ownModelResources(earth,listOf("Day_file","Night_file","NormalTex_file","SpecularTex_file"))
                val earthOpacity = OpacityControllerComponent(0f)
                earth.components.set(earthOpacity)
                // Data maps must bypass sRGB decoding. The v06 USDZ packer emitted
                // SRGB8_A8 for these two samplers despite MaterialX colorspace=raw.
                // V52: load the original normal and user AI specular 8192x4096 files through the SDK's explicit
                // RAW + LINEAR path. Day/night remain sRGB color textures.
                val linearDataOptions = TextureCreateOption().apply {
                    colorSpace = TextureColorSpace.RAW
                    textureEncoding = TextureEncoding.LINEAR
                }
                // The bundle's SRGB normal/specular samplers are never used after
                // RAW replacements. Detach and release them BEFORE allocating replacements:
                // keeping both 8K copies alive can exceed the platform resource budget.
                val stagingBitmap=android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888)
                stagingBitmap.eraseColor(android.graphics.Color.rgb(128,128,255))
                val stagingTexture=try{TextureResource.create(stagingBitmap)}finally{stagingBitmap.recycle()}
                resources.textures.add(stagingTexture)
                val redundantTextures=linkedSetOf<TextureResource>()
                val retainedColorTextures=linkedSetOf<TextureResource>()
                fun detachUnusedDataMaps(e:Entity){
                    e.components[ModelComponent::class.java]?.materials?.forEach{material->
                        val m=material as ShaderGraphMaterial
                        retainedColorTextures.add(m.getParameter("Day_file",TextureResource::class.java))
                        retainedColorTextures.add(m.getParameter("Night_file",TextureResource::class.java))
                        for(name in listOf("NormalTex_file","SpecularTex_file")){
                            redundantTextures.add(m.getParameter(name,TextureResource::class.java))
                            m.setParameter(name,stagingTexture)
                        }
                    }
                    e.getChildren().forEach{detachUnusedDataMaps(it)}
                }
                detachUnusedDataMaps(earth)
                val released=redundantTextures.filter{it !in retainedColorTextures}
                released.forEach{if(it.valid)it.close();resources.importedResources.remove(it)}
                Log.i("WorldClockResources","V74 released unused bundle data maps before RAW load: count="+released.size+" remainingValid="+released.count{it.valid})
                // Allow native release requests to reach the resource service before the next 8K allocation.
                delay(80)
                resources.ensureOpen()
                val normalData = TextureResource.load("earth_data/normal_8k.jpg",
                    LoadType.FROM_ASSETS, linearDataOptions)
                resources.textures.add(normalData)
                val specularData = TextureResource.load("earth_data/specular_ai_v52_8k.png",
                    LoadType.FROM_ASSETS, linearDataOptions)
                resources.textures.add(specularData)
                var correctedMaterials = 0
                val earthMaterials = mutableListOf<ShaderGraphMaterial>()
                fun bindLinearDataMaps(entity: Entity) {
                    entity.components[ModelComponent::class.java]?.let { model ->
                        for (i in 0 until model.materials.size) {
                            val material = model.materials[i] as? ShaderGraphMaterial
                                ?: error("Earth material must be ShaderGraphMaterial")
                            val names = material.getParameterNames().toSet()
                            Log.i("WorldClockEarth", "Data-map parameter names: $names")
                            // Compiled input names are validated by setParameter itself.
                            earthMaterials.add(material)
                            material.setParameter("NormalTex_file", normalData)
                            material.setParameter("SpecularTex_file", specularData)
                            material.setParameter("NightMask_Remap_inlow", DayNightLighting.nightStartDot)
                            material.setParameter("NightMask_Remap_inhigh", DayNightLighting.nightFullDot)
                            check(abs(material.getParameter<Float>("NightMask_Remap_inlow")-DayNightLighting.nightStartDot)<0.00001f)
                            material.setParameter("NightDim_in2", NIGHT_STRENGTH)
                            check(abs(material.getParameter<Float>("NightDim_in2") - NIGHT_STRENGTH) < 0.0001f)
                            check(material.getParameter<TextureResource>("NormalTex_file") == normalData)
                            check(material.getParameter<TextureResource>("SpecularTex_file") == specularData)
                            // Approved V47b reflection branch only; assert the imported values once.
                            for ((parameter, expected) in listOf(
                                "OceanLandRoughness_outlow" to .78f,
                                "OceanLandRoughness_outhigh" to .30f,
                                "OceanLandIOR_outlow" to 1.45f,
                                "OceanLandIOR_outhigh" to 1.333f,
                                "metallic" to 0f
                            )) check(abs(material.getParameter<Float>(parameter)-expected)<.00001f) {
                                "Unexpected V49 reflection parameter: $parameter"
                            }
                            Log.i("WorldClockEarth", "V49 RAW maps and reflection readback verified; land/ocean roughness=.78/.30; IOR=1.45/1.333; metallic=0")
                            correctedMaterials++
                        }
                    }
                    entity.getChildren().forEach { bindLinearDataMaps(it) }
                }
                Log.i("WorldClockEarth", "V42 view-plane manual rotation; gain 336deg/m; drag capped 70deg/s; release dwell 3s then gentle pole return; independent shimmer clock")
                bindLinearDataMaps(earth)
                check(correctedMaterials == 1)
                stagingTexture.close();resources.textures.remove(stagingTexture)
                Log.i("WorldClockResources","V74 RAW maps ready at original 8192x4096; replaced imports released; startupQuakePanels=0")
                try { resources.temperatureRenderer=TemperatureRenderer.create(earth,clock.temperature,java.io.File(context.cacheDir,"temperature-display-v56"));if(com.wupengfei.worldclock.BuildConfig.DEBUG)TemperatureTestAccess.renderer=resources.temperatureRenderer } catch(e:Exception){clock.temperature.status="气温材质加载失败";Log.e("WorldClockTemperature","Material load failed",e)}
                Log.i("WorldClockEarth", "V52 user AI 8192x4096 specular map bound RAW+LINEAR; original normal unchanged; V51 Earth bundle and AI day texture preserved")
                // Keep the imported axial tilt hierarchy. Only scale and translate its parent.
                earth.components[TransformComponent::class.java]?.apply {
                    setPosition(Vector3(eye.x, eye.y - EARTH_CENTER_DROP_M, eye.z - 6f * WORLD_SCALE))
                    setScaleVector(Vector3(EARTH_DISPLAY_SCALE, EARTH_DISPLAY_SCALE, EARTH_DISPLAY_SCALE))
                }
                fun disableEnvironmentFill(entity: Entity) {
                    entity.components.set(EnvironmentLightingSettingsComponent(0f))
                    entity.getChildren().forEach { disableEnvironmentFill(it) }
                }
                // Sphere collider uses the imported radius (2m), scaled with the distant globe.
                earth.setName("EarthInteraction")
                earth.components.set(InteractableComponent())
                earth.components.set(CollisionComponent(
                    collisionShape = listOf(ShapeResource.createSphere(2f)),
                    physicsMaterial = PhysicsMaterialResource()))
                disableEnvironmentFill(earth)
                content.addEntity(earth)

                val sun = Entity().apply {
                    setName("EarthSun")
                    components.set(DirectionalLightComponent(Color4(1f, 1f, 1f, 1f), 2000f, false))
                    // +Z points toward the sun; this component emits along -Z.
                    components[TransformComponent::class.java]?.setQuaternion(
                        Quat(-0.00052465f, 0.6300265f, 0f, 0.776573f)
                    )
                }
                resources.entities.add(sun)
                content.addEntity(sun)
                // Opening view keeps a visible terminator; geographic illumination follows system UTC.
                // Sun rotates with presentation, while only the time slider changes preview time.
                fun findGeographicRotation(entity: Entity): Entity? {
                    if (entity.getName() == "Geographic_Rotation") return entity
                    for (child in entity.getChildren()) {
                        findGeographicRotation(child)?.let { return it }
                    }
                    return null
                }
                // Spin inside the imported tilt/presentation hierarchy about Blender's local Z pole.
                val geographicEntity = checkNotNull(findGeographicRotation(earth))
                val geographicTransform = geographicEntity.components[TransformComponent::class.java] ?: error("Missing geographic transform")
                val cloudOpacity=OpacityControllerComponent(0f)
                val cloudVisibility=CloudVisibility()
                var cloudsVisible=false
                if(ENABLE_SOURCE_CLOUDS){
                    val cloudBundle=AssetBundle.load("asset://Clouds.bundle")
                    check(cloudBundle.valid){"Cloud bundle invalid"};resources.cloudsBundle=cloudBundle
                    val clouds=cloudBundle.loadModelSuspend("CloudsV41")
                    resources.ensureOpen(clouds)
                    resources.ownModelResources(clouds,listOf("CloudMask_file"))
                    // Attach a zero-opacity controller before joining the live scene.
                    // Keep the entity active throughout fades; avoid enable/material races.
                    clouds.components.set(cloudOpacity)
                    // The imported mask is replaced by the RAW original. Release the unused
                    // 8K copy before allocating its replacement, as for normal/specular maps.
                    val cloudStagingBitmap=android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888)
                    val cloudStaging=try{TextureResource.create(cloudStagingBitmap)}finally{cloudStagingBitmap.recycle()}
                    resources.textures.add(cloudStaging)
                    val redundantCloudMasks=linkedSetOf<TextureResource>()
                    fun detachCloudMask(e:Entity){
                        e.components[ModelComponent::class.java]?.materials?.forEach{base->
                            val material=base as ShaderGraphMaterial
                            redundantCloudMasks.add(material.getParameter("CloudMask_file",TextureResource::class.java))
                            material.setParameter("CloudMask_file",cloudStaging)
                        }
                        e.getChildren().forEach{detachCloudMask(it)}
                    }
                    detachCloudMask(clouds)
                    redundantCloudMasks.forEach{if(it.valid)it.close();resources.importedResources.remove(it)}
                    Log.i("WorldClockResources","V76 released redundant 8K cloud mask: count="+redundantCloudMasks.size+" remainingValid="+redundantCloudMasks.count{it.valid})
                    delay(80);resources.ensureOpen()
                    val mask=TextureResource.load("cloud_data/clouds_8k.jpg",LoadType.FROM_ASSETS,
                        TextureCreateOption().apply{colorSpace=TextureColorSpace.RAW;textureEncoding=TextureEncoding.LINEAR})
                    resources.textures.add(mask)
                    var cloudMaterials=0
                    fun bindCloud(e:Entity){
                        e.components[ModelComponent::class.java]?.materials?.forEach { base ->
                            val material=base as ShaderGraphMaterial
                            material.setParameter("CloudMask_file",mask)
                            material.setParameter("CloudDensity_in2",.65f)
                            check(abs(material.getParameter<Float>("CloudDensity_in2")-.65f)<.0001f)
                            cloudMaterials++
                        }
                        e.getChildren().forEach{bindCloud(it)}
                    }
                    bindCloud(clouds);check(cloudMaterials==1)
                    if(cloudStaging.valid)cloudStaging.close();resources.textures.remove(cloudStaging)
                    disableEnvironmentFill(clouds)
                    clouds.setName("SourceCloudLayer_V41")
                    check(geographicEntity.addChild(clouds)){"Cannot attach source cloud layer"}
                    Log.i("WorldClockClouds","V41 source pSphere4 loaded; original 8K map; 23960 triangles; radius ratio 1.0076545; density .65; geographic child")
                }

                var initialRotation = geographicTransform.quaternion
                // UTC drives the geographic Sun. Retarget only the opening view to show twilight.
                val initialCenter = earth.convertPositionTo(Vector3(0f,0f,0f),stageOrigin)
                var solarInstant=clock.instant
                var solar=SolarPosition.at(solarInstant)
                val localView=geographicEntity.convertPositionFrom(eye,stageOrigin)
                val localCenter=geographicEntity.convertPositionFrom(initialCenter,stageOrigin)
                val view=GlobeVector3((localView.x-localCenter.x).toDouble(),(localView.y-localCenter.y).toDouble(),(localView.z-localCenter.z).toDouble())
                val cueSpin=GlobeGeography.openingSpin(GlobeGeography.direction(solar.declination,solar.longitude),view)
                val iq=WorldClockSession.geographicBase ?: (GlobeQuaternion(initialRotation.x.toDouble(),initialRotation.y.toDouble(),initialRotation.z.toDouble(),initialRotation.w.toDouble())*
                    GlobeQuaternion.axisAngle(GlobeVector3.Z,cueSpin)).also { WorldClockSession.geographicBase=it }
                initialRotation=Quat(iq.x.toFloat(),iq.y.toFloat(),iq.z.toFloat(),iq.w.toFloat())
                geographicTransform.setQuaternion(initialRotation)
                val coordinateRenderer=try{
                    CoordinateRenderer.create(context,earth,clock.coordinates,rulerOrigin=stageOrigin).also{clock.coordinates.failure=""}
                }catch(e:CancellationException){throw e}catch(e:Exception){clock.coordinates.failure="经纬图层加载失败";Log.e("WorldClockCoordinates","V67 optional layer unavailable; original Earth retained",e);null}
                resources.coordinateRenderer=coordinateRenderer
                if(resources.closed)coordinateRenderer?.close()
                resources.ensureOpen()
                resources.coordinateHit=coordinateRenderer?.let{renderer->{hit->renderer.localHit(content.convertPosition(hit,ViewCoordinateSpace.Local,renderer.surfaceEntity))}}
                val earthTransform = checkNotNull(earth.components[TransformComponent::class.java])
                val sunTransform = checkNotNull(sun.components[TransformComponent::class.java])
                val initialPoleTip = geographicEntity.convertPositionTo(Vector3(0f, 0f, 1f), stageOrigin)
                val normalPole = Vector3(initialPoleTip.x - initialCenter.x,
                    initialPoleTip.y - initialCenter.y, initialPoleTip.z - initialCenter.z)
                motion.setNormalPole(GlobeVector3(normalPole.x.toDouble(),normalPole.y.toDouble(),normalPole.z.toDouble()))
                fun normalAxisErrorDegrees(): Double {
                    val center = earth.convertPositionTo(Vector3(0f, 0f, 0f), stageOrigin)
                    val tip = geographicEntity.convertPositionTo(Vector3(0f, 0f, 1f), stageOrigin)
                    val x = (tip.x - center.x).toDouble()
                    val y = (tip.y - center.y).toDouble()
                    val z = (tip.z - center.z).toDouble()
                    val nx = normalPole.x.toDouble(); val ny = normalPole.y.toDouble(); val nz = normalPole.z.toDouble()
                    val cosine = (x * nx + y * ny + z * nz) / sqrt((x*x + y*y + z*z) * (nx*nx + ny*ny + nz*nz))
                    return acos(cosine.coerceIn(-1.0, 1.0)) * 180.0 / PI
                }
                // 40 stars: four brightness groups, 80 triangles. Most are faint; only two are prominent.
                // Depth and brightness are project art choices, not PICO UI parameters.
                val stars = Entity.loadSuspend("asset://DistantStars.glb")
                resources.ensureOpen(stars)
                val starsOpacity = OpacityControllerComponent(0f)
                stars.components.set(starsOpacity)
                resources.entities.add(stars)
                val starTexture = TextureResource.load("StarPointAlpha.png", LoadType.FROM_ASSETS,
                    TextureCreateOption().apply { colorSpace = TextureColorSpace.RAW; textureEncoding = TextureEncoding.LINEAR })
                resources.textures.add(starTexture)
                val starMaterials = listOf(0.24f, 0.45f, 0.7f, 1.0f).map { brightness ->
                    UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                        setBaseColor(Color4(brightness * 0.94f, brightness * 0.97f, brightness, 1f))
                        setBaseColorTexture(starTexture)
                        setApplyToneMapping(false)
                        setDepthTest(true)
                        setDepthWrite(false)
                        setCullingMode(MaterialCullingMode.NONE)
                    }
                }
                resources.materials.addAll(starMaterials)
                stars.components[TransformComponent::class.java]?.apply {
                    setPosition(Vector3(eye.x, eye.y - 1.5f * WORLD_SCALE, eye.z))
                    setScaleVector(Vector3(WORLD_SCALE, WORLD_SCALE, WORLD_SCALE))
                }
                var starMeshes = 0
                fun applyStarMaterial(entity: Entity) {
                    entity.components[ModelComponent::class.java]?.let { model ->
                        for (index in 0 until model.materials.size) {
                            val level = entity.getName().removePrefix("StarLevel").toIntOrNull()
                                ?: error("Unknown star group: " + entity.getName())
                            model.materials[index] = starMaterials[level]
                        }
                        starMeshes++
                    }
                    entity.getChildren().forEach { applyStarMaterial(it) }
                }
                applyStarMaterial(stars)
                check(starMeshes == 4) { "Expected four star groups, found $starMeshes" }
                content.addEntity(stars)
                Log.i("WorldClockEarth", "V17 stars loaded; 40 stars; 4 brightness groups; 80 triangles; 310-480m depth; unlit")
                val effectsBundle=AssetBundle.load("asset://SunEffects.bundle")
                check(effectsBundle.valid){"Sun effects bundle invalid"};resources.effectsBundle=effectsBundle
                val sunEffect=effectsBundle.loadModelSuspend("SunEffectsV36")
                resources.ensureOpen(sunEffect)
                resources.ownModelResources(sunEffect)
                val features=ClockScene(content,attachments,clock,geographicEntity,stageOrigin,eye,resources.entities,sunEffect,context.resources.displayMetrics.density,coordinateRenderer)
                Log.i("WorldClockEarth","V31 procedural Sun loaded: white core, soft halo, subtle horizontal rays; frosted PBR pins")
                resources.earthquakeRenderer=coordinateRenderer?.let{renderer->
                    try{EarthquakeRenderer.create(renderer,stageOrigin,content,attachments,clock,context.resources.displayMetrics.density)}
                    catch(e:CancellationException){throw e}
                    catch(e:Exception){clock.earthquakes.renderFailure="地震图层加载失败，请重新打开应用";Log.e("WorldClockQuakes","Renderer initialization",e);null}
                }
                var lastQuakeFocus=clock.earthquakes.focusRequest
                var quakeFocusAudit:String?=null
                var quakeFocusAuditAfter=0.0
                var lastFocusRequest=clock.focusRequest
                var lastResumeRequest=clock.resumeRequest
                var lastUiActivity=0
                var sampleStart = nowSeconds()
                var previousFrame = sampleStart
                var sampleFrames = 0
                var workSeconds = 0.0
                var maxWorkSeconds = 0.0
                var maxGapSeconds = 0.0
                var nextOpeningAudit = 0.0
                val veilTransform = checkNotNull(veil.components[TransformComponent::class.java])
                fun applyOpeningPose() {
                    val opening = entrance.pose
                    earthTransform.setPosition(Vector3(eye.x+opening.position.x.toFloat(),
                        eye.y+opening.position.y.toFloat(),eye.z+opening.position.z.toFloat()))
                    earthOpacity.opacity = opening.opacity.toFloat()
                    veilOpacity.opacity = opening.veilOpacity.toFloat()
                    val width = WORLD_SCALE*opening.veilProgress.coerceAtLeast(0.001).toFloat()
                    veilTransform.setScaleVector(Vector3(width,width,WORLD_SCALE))
                    starsOpacity.opacity = opening.opacity.toFloat()
                }
                applyOpeningPose()
                // SDK scene events keep transforms and shader inputs on the same scene frame.
                // A fixed 33ms delay limited the old animation to ~29 updates/sec on 90Hz SWAN.
                resources.frameSubscription = content.subscribe(SceneEvents.Update::class.java) { event ->
                    val now = nowSeconds()
                    resources.temperatureRenderer?.update(clock.mode,event.deltaTime.toDouble())
                    maxGapSeconds = max(maxGapSeconds, now - previousFrame)
                    previousFrame = now
                    val quake=clock.earthquakes
                    val quakeActive=clock.mode==EarthMode.EARTHQUAKE
                    val fadeStep=(event.deltaTime.toDouble()/QuakeStyle.FADE_SECONDS).toFloat()
                    quake.reveal=if(quakeActive)min(1f,quake.reveal+fadeStep) else max(0f,quake.reveal-fadeStep)
                    if(quakeActive)quake.advance(event.deltaTime.toDouble())
                    val wasRestoringTilt = motion.restoringTilt
                    val idleDelta = entrance.advance(event.deltaTime.toDouble(),motion.dragging)
                    if (!resources.interactionReady) {
                        applyOpeningPose()
                        if (entrance.elapsed+1e-8 >= nextOpeningAudit || entrance.finished) {
                            Log.i("WorldClockEarth", "V31 opening: t="+entrance.elapsed+
                                "; position="+earthTransform.position+"; opacity="+earthOpacity.opacity+
                                "; spin="+entrance.pose.spinDegrees+"; veil="+entrance.pose.veilProgress+
                                "; veilOpacity="+veilOpacity.opacity)
                            nextOpeningAudit += 0.25
                        }
                        if (entrance.finished) {
                            resources.interactionReady = true
                            clock.ready=true
                            Log.i("WorldClockEarth", "V31 opening complete; mask 4s, globe 4s after half-open; hand interaction ready; idle 3deg/s")
                        }
                    }
                    val visibility=WorldClockSession.immersiveVisibility
                    earthOpacity.opacity=entrance.pose.opacity.toFloat()*visibility
                    starsOpacity.opacity=entrance.pose.opacity.toFloat()*visibility
                    veilOpacity.opacity=entrance.pose.veilOpacity.toFloat()*visibility
                    val displayScale=EARTH_DISPLAY_SCALE*(.25f+.75f*visibility)
                    earthTransform.setScaleVector(Vector3(displayScale,displayScale,displayScale))
                    if(resources.interactionReady && !motion.dragging && !motion.timelineHeld) {
                        if(clock.focusRequest!=lastFocusRequest&&clock.mode!=EarthMode.EARTHQUAKE) {
                            lastFocusRequest=clock.focusRequest
                            clock.selected?.let{city->
                                val c=earth.convertPositionTo(Vector3(0f,0f,0f),stageOrigin)
                                val head=resources.headInStage?.invoke()?:eye
                                val toward=GlobeVector3((head.x-c.x).toDouble(),(head.y-c.y).toDouble(),(head.z-c.z).toDouble()).normalized()
                                motion.focusCity(features.cityDirection(city,c),toward)
                                Log.i("WorldClockEarth","V31 focus city="+city.name+"; id="+city.id+"; lat="+city.latitude+"; lon="+city.longitude)
                            }
                        }
                        if(clock.resumeRequest!=lastResumeRequest){lastResumeRequest=clock.resumeRequest;motion.resumeIdle(now)}
                    }
                    if(clock.uiActivity!=lastUiActivity || clock.uiManipulating){
                        lastUiActivity=clock.uiActivity;motion.noteUiActivity(now)
                    }
                    if(quakeActive&&quake.focusRequest!=lastQuakeFocus&&!motion.dragging&&!motion.timelineHeld){
                        lastQuakeFocus=quake.focusRequest
                        quake.selected?.let{selected->resources.earthquakeRenderer?.let{renderer->
                            val c=earth.convertPositionTo(Vector3(0f,0f,0f),stageOrigin)
                            val head=resources.headInStage?.invoke()?:eye
                            val local=renderer.directionInEarth(selected,earth)
                            val toward=(CoordinateRenderer.vec(head)-CoordinateRenderer.vec(c)).normalized()
                            motion.focusLocalPoint(local,toward)
                            quakeFocusAudit=selected.id;quakeFocusAuditAfter=0.0
                            Log.i("WorldClockQuakes","V82 focus event=${selected.id} lat=${selected.latitude} lon=${selected.longitude} rootLocal=$local toward=$toward from=${motion.presentation} explicitHierarchy=true")
                        }}
                    }
                    if(quakeActive&&!quake.selectionUsesCityFocus&&(quake.selected!=null||quake.groupIds.isNotEmpty())&&!motion.focusing)motion.readCoordinates(now)
                    val wasFocused=motion.focusLocked
                    // Preview freezes UTC, never the independent presentation orbit.
                    motion.tick(if(resources.interactionReady) event.deltaTime.toDouble() else idleDelta, now)
                    if(wasFocused&&!motion.focusLocked&&clock.mode!=EarthMode.EARTHQUAKE){clock.selectedId=null;Log.i("WorldClockEarth","V33 city focus released after 5s idle")}
                    // Clouds stay fully transparent for the entire VST reveal and globe approach.
                    val revealClouds=clock.mode!=EarthMode.TEMPERATURE && cloudVisibility.canReveal(entrance.finished) && motion.cloudsMayShow(now)
                    if(revealClouds!=cloudsVisible){
                        cloudsVisible=revealClouds
                        Log.i("WorldClockClouds","V47 fade "+(if(revealClouds)"in" else "out for interaction")+"; openingElapsed="+entrance.elapsed+"; openingFinished="+entrance.finished+"; held="+motion.dragging+"; focus="+motion.focusLocked)
                    }
                    val cloudAlpha=cloudVisibility.update(revealClouds,event.deltaTime.toDouble())
                    cloudOpacity.opacity=cloudAlpha*visibility
                    // Project art choice: UV noise cells (100 x 50), +/-50% local variation.
                    // Time is the third noise coordinate; every region evolves independently.
                    val seconds = motion.effectSeconds
                    // Manual rotation uses the latched view plane, above the imported tilt.
                    val p = motion.presentation
                    earthTransform.setQuaternion(Quat(p.x.toFloat(),p.y.toFloat(),p.z.toFloat(),p.w.toFloat()))
                    // Only automatic idle spin follows the authored geographic Z pole.
                    val halfAngle = (entrance.pose.spinDegrees+motion.idleDegrees) * PI / 360.0
                    val spinSin = sin(halfAngle).toFloat()
                    val spinCos = cos(halfAngle).toFloat()
                    // initialRotation * local-Z spin preserves the exact first-frame geography.
                    geographicTransform.setQuaternion(Quat(
                        initialRotation.x * spinCos + initialRotation.y * spinSin,
                        initialRotation.y * spinCos - initialRotation.x * spinSin,
                        initialRotation.z * spinCos + initialRotation.w * spinSin,
                        initialRotation.w * spinCos - initialRotation.z * spinSin))
                    val noiseTime = Vector3(0f, 0f, (0.37 + seconds * 0.55).toFloat())
                    earthMaterials.forEach { it.setParameter("NoiseTimePosition_in2", noiseTime) }
                    val center = earth.convertPositionTo(Vector3(0f, 0f, 0f), stageOrigin)
                    if(clock.instant!=solarInstant){
                        solarInstant=clock.instant;solar=SolarPosition.at(solarInstant)
                    }
                    val geographicSun=GlobeGeography.direction(solar.declination,solar.longitude)
                    val sunTip = geographicEntity.convertPositionTo(Vector3(geographicSun.x.toFloat(),geographicSun.y.toFloat(),geographicSun.z.toFloat()), stageOrigin)
                    val lx = sunTip.x - center.x
                    val ly = sunTip.y - center.y
                    val lz = sunTip.z - center.z
                    val n = sqrt(lx*lx + ly*ly + lz*lz)
                    val light = Vector3(lx/n, ly/n, lz/n)
                    val qw = sqrt((1f + light.z).coerceAtLeast(0f) * 0.5f)
                    val rotation = if (qw > 0.0001f) Quat(-light.y/(2f*qw), light.x/(2f*qw), 0f, qw)
                        else Quat(0f, 1f, 0f, 0f)
                    sunTransform.setQuaternion(rotation)
                    val worldOrigin = stageOrigin.convertPositionTo(Vector3(0f, 0f, 0f), null)
                    val worldTip = stageOrigin.convertPositionTo(light, null)
                    val worldLight = Vector3(worldTip.x-worldOrigin.x, worldTip.y-worldOrigin.y, worldTip.z-worldOrigin.z)
                    earthMaterials.forEach { it.setParameter("SunDot_in2", worldLight) }
                    coordinateRenderer?.update(event.deltaTime.toDouble(),visibility*(if(quakeActive).30f else 1f))
                    resources.earthquakeRenderer?.update(event.deltaTime.toDouble(),center,resources.headInStage?.invoke()?:eye,visibility,resources.interactionReady)
                    if(quakeFocusAudit!=null){
                        if(!quakeActive||quake.selectedId!=quakeFocusAudit||motion.dragging)quakeFocusAudit=null
                        else if(!motion.focusing){
                            if(quakeFocusAuditAfter==0.0)quakeFocusAuditAfter=now+.2
                            else if(now>=quakeFocusAuditAfter){
                                quake.selected?.let{selected->resources.earthquakeRenderer?.let{renderer->
                                    val head=resources.headInStage?.invoke()?:eye
                                    val toward=(CoordinateRenderer.vec(head)-CoordinateRenderer.vec(center)).normalized()
                                    val actual=renderer.direction(selected,center)
                                    val error=Math.toDegrees(acos(actual.dot(toward).coerceIn(-1.0,1.0)))
                                    val native=renderer.nativeDirection(selected,center)
                                    val nativeError=Math.toDegrees(acos(native.dot(toward).coerceIn(-1.0,1.0)))
                                    Log.i("WorldClockQuakes","V82 focus settled event=${selected.id} hierarchyErrorDegrees=$error nativeErrorDegrees=$nativeError actual=$actual target=$toward")
                                }}
                                quakeFocusAudit=null
                            }
                        }
                    }
                    features.update(resources.interactionReady,center,resources.headInStage?.invoke()?:eye,light,earthOpacity.opacity,entrance.pose.veilProgress.toFloat(),veilOpacity.opacity)
                    if (!motion.dragging && wasRestoringTilt && !motion.restoringTilt) {
                        Log.i("WorldClockEarth", "V31 tilt restored; axisErrorDegrees=" + normalAxisErrorDegrees() +
                            "; orientation=" + motion.presentation)
                    }
                    val work = nowSeconds() - now
                    sampleFrames++
                    workSeconds += work
                    maxWorkSeconds = max(maxWorkSeconds, work)
                    val elapsed = now - sampleStart
                    if (elapsed >= 5.0) {
                        Log.i("WorldClockEarth", "V31 animation cadence: updates=$sampleFrames; seconds=$elapsed; " +
                            "hz=" + sampleFrames / elapsed + "; meanWorkMs=" + workSeconds * 1000 / sampleFrames +
                            "; maxWorkMs=" + maxWorkSeconds * 1000 + "; maxGapMs=" + maxGapSeconds * 1000 +
                            "; idleDegrees=" + motion.idleDegrees + "; tiltError=" + motion.tiltErrorDegrees +
                            "; orientation=" + motion.presentation + "; axisErrorDegrees=" + normalAxisErrorDegrees() +
                            "; solarUTC="+solarInstant+"; subsolarLat="+solar.declination+"; subsolarLon="+solar.longitude+"; beijingAltitude="+solar.altitude(39.9042,116.4074)+"; beijingNightWeight="+DayNightLighting.nightWeight(solar.altitude(39.9042,116.4074))+"; cities="+clock.cities.size+
                            "; held=" + motion.dragging + "; timelineHeld="+motion.timelineHeld+"; cloudAlpha="+cloudAlpha+"; effectSeconds=" + motion.effectSeconds)
                        sampleStart = now
                        sampleFrames = 0
                        workSeconds = 0.0
                        maxWorkSeconds = 0.0
                        maxGapSeconds = 0.0
                    }
                }
                Log.i("WorldClockEarth", "V31 Earth loaded; night emission 0.9 (5x) with spatial UV noise +/-50%; V51 user AI 8192x4096 day colour map; original 8192x4096 night/normal; V52 user AI 8192x4096 specular map; V49 approved reflection branch; diameter 48m, forward distance 60m, center 4deg lower, angular diameter about 47deg; IBL disabled")
            } catch (cancelled: CancellationException) {
                resources.close()
                throw cancelled
            } catch (error: Exception) {
                resources.close()
                WorldClockSession.immersiveResources=false
                Log.e("WorldClockEarth", "Earth load failed", error)
                WorldClockSession.failedToLoad(error)
                clock.startupFailure="地球加载失败，请重试。若仍失败，可以关闭后重新打开。"
                // Reuse the already allocated Dock attachment: even resource failure
                // leaves a visible retry/close surface instead of an empty VST scene.
                attachments.entity("timeline")?.let{panel->
                    val eye=resources.startupEye
                    panel.components[TransformComponent::class.java]?.apply{
                        setPosition(Vector3(eye.x,eye.y+.15f,eye.z-2.5f))
                        setScaleVector(Vector3(3.5f,3.5f,3.5f));setQuaternion(Quat(0f,0f,0f,1f))
                    }
                    panel.enabled=true;content.addEntity(panel)
                }
            }
        }
    )
}
