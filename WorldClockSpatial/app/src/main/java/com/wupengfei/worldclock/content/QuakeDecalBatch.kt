package com.wupengfei.worldclock.content

import android.util.Log
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*
import kotlinx.coroutines.*

/** One native model per batch. Latest-only jobs; old handles retire after three scene frames. */
internal class QuakeDecalBatch(private val name:String,parent:Entity,private val material:Material,
    private val scope:CoroutineScope,private val failure:(Throwable)->Unit):AutoCloseable{
    val entity=Entity().apply{setName(name);enabled=false;components.set(EnvironmentLightingSettingsComponent(0f));check(parent.addChild(this))}
    private var requestedKey:Any?=null
    private var committedKey:Any?=null
    private var pending:Pair<Any,()->QuakeDecalGeometry>?=null
    private var job:Job?=null
    private var current:MeshResource?=null
    private val retired=mutableListOf<Pair<Int,MeshResource>>()
    private var tick=0
    private var closed=false
    var commits=0;private set
    var triangles=0;private set
    var wanted=true
    fun request(key:Any,build:()->QuakeDecalGeometry){
        if(closed||requestedKey==key)return
        requestedKey=key;pending=key to build
        if(job?.isActive==true)return
        job=scope.launch{
            while(!closed){
                val task=pending?:break;pending=null
                var mesh:MeshResource?=null
                try{
                    val geometry=withContext(Dispatchers.Default){task.second()}
                    if(geometry.indices.isNotEmpty())withContext(Dispatchers.IO+NonCancellable){
                        mesh=MeshResource.createWithMeshModel(MeshModel(
                            geometry.positions.map(CoordinateRenderer::sdk),geometry.indices,
                            normals=geometry.normals.map(CoordinateRenderer::sdk),
                            uv0=geometry.uv.map{Vector2(it.first.toFloat(),it.second.toFloat())},
                            uv1=geometry.meta.map{Vector2(it.first.toFloat(),it.second.toFloat())},
                            colors=geometry.colors.map{Color4(it.wave.toFloat(),it.slot.toFloat(),it.seed.toFloat(),it.magnitude.toFloat())}),name=name).also{it.toGlobal()}
                    }
                    currentCoroutineContext().ensureActive()
                    if(!closed&&requestedKey==task.first){
                        val next=mesh
                        if(next!=null){
                            val model=entity.components[ModelComponent::class.java]
                            if(model==null)entity.components.set(ModelComponent(next,material))else model.mesh=next
                            current?.let{retired.add((tick+3) to it)};current=next;mesh=null
                        }
                        triangles=geometry.triangleCount;committedKey=task.first;commits++
                        entity.enabled=wanted&&triangles>0
                    }
                }catch(e:CancellationException){throw e}catch(e:Exception){
                    requestedKey=committedKey;Log.e("WorldClockQuakes","Batch failed: $name",e);failure(e)
                }finally{mesh?.let{if(it.valid)it.close()}}
            }
        }
    }
    fun update(){
        tick++;val visible=wanted&&triangles>0;if(entity.enabled!=visible)entity.enabled=visible
        val iterator=retired.iterator();while(iterator.hasNext()){val item=iterator.next();if(item.first<=tick){if(item.second.valid)item.second.close();iterator.remove()}}
    }
    fun liveMeshes()=(if(current?.valid==true)1 else 0)+retired.count{it.second.valid}
    override fun close(){if(closed)return;closed=true;pending=null;job?.cancel();entity.destroy()
        current?.let{if(it.valid)it.close()};current=null;retired.forEach{if(it.second.valid)it.second.close()};retired.clear()}
}
