package com.wupengfei.worldclock.content
import android.util.Log
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import kotlinx.coroutines.*
import java.io.File

/** Swaps only the material and UV-augmented copy of the same mesh. Original handles survive. */
internal class TemperatureRenderer(private val model:ModelComponent,private val original:ShaderGraphMaterial,
    private val owner:Entity,private val bundle:AssetBundle,private val template:ModelComponent,
    private val state:TemperatureState) : AutoCloseable {
    private val originalMesh=model.mesh
    private val temperatureMesh=template.mesh
    private val material=template.materials[0] as ShaderGraphMaterial
    private data class Prepared(val manifest:TemperatureManifest,val frame:TemperatureFrame,val grid:TemperatureGrid,val texture:TextureResource)
    private var pending:Prepared?=null
    private var current:Prepared?=null
    private val retired=mutableListOf<Pair<Int,TextureResource>>()
    private var tick=0
    private var active=false
    private var closed=false
    @Synchronized fun liveTextureCount()=(if(current!=null)1 else 0)+(if(pending!=null)1 else 0)+retired.size
    init {
        original.toGlobal();originalMesh.toGlobal();material.toGlobal();temperatureMesh.toGlobal()
        material.setParameter("Day_file",original.getParameter<TextureResource>("Day_file"))
        material.setParameter("Normal_file",original.getParameter<TextureResource>("NormalTex_file"))
        Log.i("WorldClockTemperature","V53 shader ready; numeric RGB; UV1; fixed scale; same original 8K Day/Normal handles")
    }
    suspend fun prepare(m:TemperatureManifest,frame:TemperatureFrame,grid:TemperatureGrid){
        var tex:TextureResource?=null
        try {
            withContext(Dispatchers.IO){
                tex=TextureResource.load(File(m.directory,frame.texture).absolutePath,LoadType.FROM_STORAGE,TextureCreateOption().apply{
                    colorSpace=TextureColorSpace.RAW;textureEncoding=TextureEncoding.LINEAR;mipmapMode=TextureMipmapMode.NONE
                }).also{it.toGlobal()}
            }
            currentCoroutineContext().ensureActive()
            synchronized(this){if(closed)return;pending?.texture?.close();pending=Prepared(m,frame,grid,checkNotNull(tex));tex=null}
        }finally{tex?.close()}
    }
    @Synchronized fun update(mode:EarthMode){
        if(closed)return
        tick++
        pending?.let { next ->
            material.setParameter("Temperature_file",next.texture)
            current?.let{retired.add((tick+3) to it.texture)}
            current=next;pending=null
            state.displayedManifest=next.manifest;state.displayed=next.frame;state.grid=next.grid;state.loading=false
            Log.i("WorldClockTemperature","V53 frame committed "+next.frame.validTime+"; 1440x721; texture1442x723; RAW+LINEAR; sampled Beijing="+next.grid.sample(39.9042,116.4074))
        }
        material.setParameter("Overlay_mix",state.strength)
        val want=mode==EarthMode.TEMPERATURE&&current!=null
        if(want!=active){
            model.mesh=if(want)temperatureMesh else originalMesh
            model.materials[0]=if(want)material else original
            active=want
            Log.i("WorldClockTemperature","V53 mode="+mode+"; temperatureMaterial="+want+"; originalRestored="+(!want && model.materials[0]==original))
        }
        val it=retired.iterator();while(it.hasNext()){val item=it.next();if(item.first<=tick){item.second.close();it.remove()}}
    }
    @Synchronized override fun close(){
        closed=true
        model.mesh=originalMesh;model.materials[0]=original
        pending?.texture?.close();current?.texture?.close();retired.forEach{it.second.close()}
        pending=null;current=null;retired.clear();owner.destroy();material.close();temperatureMesh.close();original.close();originalMesh.close();bundle.close()
    }
    companion object {
        suspend fun create(earth:Entity,state:TemperatureState):TemperatureRenderer {
            fun find(e:Entity):ModelComponent?=e.components[ModelComponent::class.java]?:e.getChildren().firstNotNullOfOrNull{find(it)}
            val model=checkNotNull(find(earth));val original=model.materials[0] as ShaderGraphMaterial
            val bundle=AssetBundle.load("asset://Temperature.bundle")
            val owner=bundle.loadModelSuspend("TemperatureV53")
            return TemperatureRenderer(model,original,owner,bundle,checkNotNull(find(owner)),state)
        }
    }
}
