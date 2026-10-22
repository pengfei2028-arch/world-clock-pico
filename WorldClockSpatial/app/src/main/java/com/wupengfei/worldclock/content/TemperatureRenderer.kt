package com.wupengfei.worldclock.content

import android.graphics.Bitmap
import android.util.Log
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.*
import java.io.File

/** A separate transparent shell follows the original mesh's complete transform hierarchy. */
internal class TemperatureRenderer private constructor(private val earthModel:ModelComponent,
    private val owner:Entity,private val shell:Entity,private val bundle:AssetBundle,
    private val template:ModelComponent,private val state:TemperatureState,private val cache:File) : AutoCloseable {
    private val originalMesh=earthModel.mesh
    private val originalMaterial=earthModel.materials[0] as ShaderGraphMaterial
    private val reflectionParameters=listOf("OceanLandIOR_outlow","OceanLandIOR_outhigh").associateWith { originalMaterial.getParameter<Float>(it) }
    private var lastStrength=-1f
    private val temperatureMesh=template.mesh
    private val material=template.materials[0] as ShaderGraphMaterial
    private val opacity=OpacityControllerComponent(0f)
    private data class Prepared(val manifest:TemperatureManifest,val frame:TemperatureFrame,val grid:TemperatureGrid,val texture:TextureResource,val file:File) {
        fun close(){texture.close();file.delete()}
    }
    private var pending:Prepared?=null
    private var current:Prepared?=null
    private val retired=mutableListOf<Pair<Int,Prepared>>()
    private var tick=0
    private var active=false
    private var closed=false
    @Synchronized fun liveTextureCount()=(if(current!=null)1 else 0)+(if(pending!=null)1 else 0)+retired.size
    @Synchronized fun originalSurfaceIntact()=earthModel.mesh==originalMesh&&earthModel.materials[0]==originalMaterial
    @Synchronized fun shellOpacity()=opacity.opacity
    init {
        material.toGlobal();temperatureMesh.toGlobal()
        // The shell has its own model and scalar data texture. The original Earth is untouched.
        material.setParameter("Surface_opacity",1f)
        material.setParameter("Day_file",originalMaterial.getParameter<TextureResource>("Day_file"))
        material.setParameter("Sea_file",originalMaterial.getParameter<TextureResource>("SpecularTex_file"))
        shell.setName("TemperatureShell_V55")
        shell.components.set(opacity)
        shell.components.set(EnvironmentLightingSettingsComponent(0f))
        shell.components.set(ModelComponent(temperatureMesh,material))
        shell.components[TransformComponent::class.java]?.setScaleVector(Vector3(TemperatureDisplay.SHELL_SCALE,TemperatureDisplay.SHELL_SCALE,TemperatureDisplay.SHELL_SCALE))
        Log.i("WorldClockTemperature","V55 independent shell; radius=1.003; opacity=1; fixed 3C hot bands; original 8K Earth untouched")
    }
    suspend fun prepare(m:TemperatureManifest,frame:TemperatureFrame,grid:TemperatureGrid){
        var tex:TextureResource?=null;var file:File?=null
        try {
            withContext(Dispatchers.IO){
                val smooth=TemperatureDisplay.smooth(grid)
                ensureActive()
                val bitmap=Bitmap.createBitmap(TemperatureDisplay.pixels(grid,smooth),grid.width+2,grid.height+2,Bitmap.Config.ARGB_8888)
                try {
                    cache.mkdirs();file=File.createTempFile("temperature-v55-",".png",cache)
                    checkNotNull(file).outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
                }finally{bitmap.recycle()}
                ensureActive()
                tex=TextureResource.load(checkNotNull(file).absolutePath,LoadType.FROM_STORAGE,TextureCreateOption().apply{
                    colorSpace=TextureColorSpace.RAW;textureEncoding=TextureEncoding.LINEAR;mipmapMode=TextureMipmapMode.NONE
                }).also{it.toGlobal()}
            }
            currentCoroutineContext().ensureActive()
            synchronized(this){if(closed)return;pending?.close();pending=Prepared(m,frame,grid,checkNotNull(tex),checkNotNull(file));tex=null;file=null}
        }finally{tex?.close();file?.delete()}
    }
    @Synchronized fun update(mode:EarthMode){
        if(closed)return
        tick++
        pending?.let { next ->
            material.setParameter("Temperature_file",next.texture)
            current?.let{retired.add((tick+3) to it)}
            current=next;pending=null
            state.displayedManifest=next.manifest;state.displayed=next.frame;state.grid=next.grid;state.loading=false
            Log.i("WorldClockTemperature","V55 frame committed "+next.frame.validTime+"; smoothed display; original-grid Beijing="+next.grid.sample(39.9042,116.4074))
        }
        val want=mode==EarthMode.TEMPERATURE&&current!=null
        opacity.opacity=if(want)state.strength.coerceIn(0f,1f) else 0f
        if(opacity.opacity!=lastStrength){
            lastStrength=opacity.opacity
            reflectionParameters.forEach{(key,value)->originalMaterial.setParameter(key,1f+(value-1f)*(1f-lastStrength))}
        }
        if(want!=active){active=want;Log.i("WorldClockTemperature","V55 mode="+mode+"; shell="+want+"; originalSurfaceIntact="+originalSurfaceIntact())}
        val it=retired.iterator();while(it.hasNext()){val item=it.next();if(item.first<=tick){item.second.close();it.remove()}}
    }
    @Synchronized override fun close(){
        closed=true;opacity.opacity=0f
        reflectionParameters.forEach{(key,value)->originalMaterial.setParameter(key,value)}
        shell.destroy();owner.destroy()
        pending?.close();current?.close();retired.forEach{it.second.close()}
        pending=null;current=null;retired.clear();material.close();temperatureMesh.close();bundle.close()
    }
    companion object {
        suspend fun create(earth:Entity,state:TemperatureState,cache:File):TemperatureRenderer {
            fun find(e:Entity):Entity?=if(e.components[ModelComponent::class.java]!=null)e else e.getChildren().firstNotNullOfOrNull{find(it)}
            val surface=checkNotNull(find(earth));val model=checkNotNull(surface.components[ModelComponent::class.java])
            val bundle=AssetBundle.load("asset://Temperature.bundle")
            val owner=bundle.loadModelSuspend("TemperatureV55")
            val shell=Entity()
            val renderer=TemperatureRenderer(model,owner,shell,bundle,checkNotNull(find(owner)?.components?.get(ModelComponent::class.java)),state,cache)
            check(surface.addChild(shell)){"Cannot attach temperature shell"}
            return renderer
        }
    }
}
