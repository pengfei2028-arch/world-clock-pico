package com.wupengfei.worldclock.content

import kotlin.math.*

/** CPU work happens on membership changes, never to advance the ripple animation. */
internal data class QuakeVertexData(val wave:Double,val slot:Double,val seed:Double,val magnitude:Double)
internal class QuakeDecalGeometry {
    val positions=mutableListOf<GlobeVector3>()
    val normals=mutableListOf<GlobeVector3>()
    val indices=mutableListOf<Int>()
    val uv=mutableListOf<Pair<Double,Double>>()
    val meta=mutableListOf<Pair<Double,Double>>()
    val colors=mutableListOf<QuakeVertexData>()
    private fun basis(point:GlobeVector3):Pair<GlobeVector3,GlobeVector3>{
        val n=point.normalized()
        val east=(if(abs(n.z)<.99)GlobeVector3.Z.cross(n) else GlobeVector3.Y.cross(n)).normalized()
        return east to n.cross(east).normalized()
    }
    /** Exponential map of a tangent plane onto the sphere: UV radius is angular distance.
     * No longitude seam and no special polar billboard; triangle winding faces outward. */
    fun patch(point:GlobeVector3,halfAngle:Double,wave:Boolean,slot:Int,seed:Double,birth:Double,token:Int,magnitudeBand:Int=0){
        val steps=if(wave)8 else 2
        val (east,north)=basis(point);val n=point.normalized();val radius=sqrt(point.dot(point))
        val start=positions.size
        for(y in 0..steps)for(x in 0..steps){
            val u=x*2.0/steps-1;val v=y*2.0/steps-1;val length=hypot(u,v);val angle=length*halfAngle
            val direction=if(length<1e-10)n else n*cos(angle)+(east*u+north*v)*(sin(angle)/length)
            positions.add(direction*radius);normals.add(direction);uv.add(u to v);meta.add(birth to token.toDouble())
            colors.add(QuakeVertexData(if(wave)1.0 else 0.0,slot.toDouble()/(QuakeStyle.MAX_RIPPLE_GROUPS-1),seed,magnitudeBand/2.0))
        }
        for(y in 0 until steps)for(x in 0 until steps){
            val a=start+y*(steps+1)+x;val b=a+1;val c=a+steps+1;val d=c+1
            indices.addAll(listOf(a,b,d,a,d,c))
        }
    }
    fun text(point:GlobeVector3,text:String){
        val (east,north)=basis(point);val n=point.normalized();val radius=sqrt(point.dot(point))
        val width=Math.toRadians(1.05);val height=Math.toRadians(1.8)
        text.forEachIndexed{i,ch->
            val digit=ch.digitToIntOrNull()?:10;val start=positions.size
            for((x,y) in listOf(0 to 0,1 to 0,1 to 1,0 to 1)){
                val a=Math.toRadians(1.0)+(i+x)*width;val b=(y-.5)*height
                val direction=(n+east*a+north*b).normalized()
                positions.add(direction*radius);normals.add(direction)
                // Android bitmap top is UV v=1 in the native material path.
                // Built-in Unlit multiplies the atlas by vertex color (unlike our
                // procedural graph, where the color channels carry metadata).
                uv.add((digit*48.0+2+x*44)/528.0 to (.05+y*.90));meta.add(0.0 to 0.0);colors.add(QuakeVertexData(1.0,1.0,1.0,1.0))
            }
            indices.addAll(listOf(start,start+1,start+2,start,start+2,start+3))
        }
    }
    val triangleCount get()=indices.size/3
}

/** A frame-local affine snapshot replaces thousands of Entity JNI conversion calls. */
internal data class QuakeSurfaceFrame(val zero:GlobeVector3,val x:GlobeVector3,val y:GlobeVector3,val z:GlobeVector3){
    fun point(p:GlobeVector3)=zero+x*p.x+y*p.y+z*p.z
    fun vector(p:GlobeVector3)=x*p.x+y*p.y+z*p.z
    fun then(parent:QuakeSurfaceFrame)=QuakeSurfaceFrame(parent.point(zero),parent.vector(x),parent.vector(y),parent.vector(z))
    fun inverse():QuakeSurfaceFrame {
        val determinant=x.dot(y.cross(z));require(kotlin.math.abs(determinant)>1e-12)
        val a=y.cross(z)*(1/determinant);val b=z.cross(x)*(1/determinant);val c=x.cross(y)*(1/determinant)
        return QuakeSurfaceFrame(GlobeVector3(-a.dot(zero),-b.dot(zero),-c.dot(zero)),
            GlobeVector3(a.x,b.x,c.x),GlobeVector3(a.y,b.y,c.y),GlobeVector3(a.z,b.z,c.z))
    }
    companion object {
        val IDENTITY=QuakeSurfaceFrame(GlobeVector3.ZERO,GlobeVector3.X,GlobeVector3.Y,GlobeVector3.Z)
        fun trs(position:GlobeVector3,rotation:GlobeQuaternion,scale:GlobeVector3)=QuakeSurfaceFrame(position,
            rotation.rotate(GlobeVector3.X)*scale.x,rotation.rotate(GlobeVector3.Y)*scale.y,rotation.rotate(GlobeVector3.Z)*scale.z)
    }
}
