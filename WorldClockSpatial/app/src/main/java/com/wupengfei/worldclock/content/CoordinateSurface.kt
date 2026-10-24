package com.wupengfei.worldclock.content

import java.io.DataInputStream
import java.io.InputStream
import java.util.Locale
import kotlin.math.*

internal data class GeoCoordinate(val latitude:Double,val longitude:Double?) {
    fun latitudeText()=format(latitude,true)
    fun longitudeText()=longitude?.let{format(it,false)}?:"经度不唯一 · 极点"
    companion object {
        private fun format(value:Double,latitude:Boolean):String {
            // A rounded zero always uses N/E; never display a negative zero.
            val v=if(abs(value)<.005)0.0 else value
            val suffix=if(latitude){if(v<0)"S" else "N"}else{if(v<0)"W" else "E"}
            return String.format(Locale.ROOT,"%.2f° %s",abs(v),suffix)
        }
    }
}

/** UV barycentric mapping of the actual shipped mesh, not an ideal sphere approximation. */
internal class CoordinateSurface private constructor(private val triangles:List<Triangle>) {
    private class Triangle(val a:GlobeVector3,val b:GlobeVector3,val c:GlobeVector3,val uv:DoubleArray) {
        val e=b-a;val f=c-a;val normal=e.cross(f);val plane=normal.dot(a)
        val ee=e.dot(e);val ef=e.dot(f);val ff=f.dot(f);val determinant=ee*ff-ef*ef
        val uvDet=(uv[3]-uv[5])*(uv[0]-uv[4])+(uv[4]-uv[2])*(uv[1]-uv[5])
        fun uvWeights(u:Double,v:Double):DoubleArray? {
            if(abs(uvDet)<1e-14)return null
            val x=((uv[3]-uv[5])*(u-uv[4])+(uv[4]-uv[2])*(v-uv[5]))/uvDet
            val y=((uv[5]-uv[1])*(u-uv[4])+(uv[0]-uv[4])*(v-uv[5]))/uvDet
            return doubleArrayOf(x,y,1-x-y)
        }
        fun rayUv(d:GlobeVector3):GeoCoordinate? {
            val denom=normal.dot(d)
            if(abs(denom)<1e-12||determinant<1e-20)return null
            val t=plane/denom
            if(t<=0)return null
            val p=d*t-a;val pe=p.dot(e);val pf=p.dot(f)
            val y=(ff*pe-ef*pf)/determinant;val z=(ee*pf-ef*pe)/determinant;val x=1-y-z
            if(minOf(x,y,z)<-1e-7)return null
            val lat=((uv[1]*x+uv[3]*y+uv[5]*z)*180-90).coerceIn(-90.0,90.0)
            val lon=SolarPosition.wrap((uv[0]*x+uv[2]*y+uv[4]*z)*360-180)
            return GeoCoordinate(lat,if(abs(lat)>89.99999)null else lon)
        }
    }
    private val bins=Array(64*32){mutableListOf<Triangle>()}
    private val latitudeContours=mutableMapOf<Double,List<Pair<Double,GlobeVector3>>>()
    // The original UV pole vertices are about 0.1 degree off the ideal +Z/-Z axis.
    // Use the authored vertices; forcing every meridian to an ideal axis makes a kink.
    val northPole:GlobeVector3=authoredPole(true)
    val southPole:GlobeVector3=authoredPole(false)
    private fun authoredPole(north:Boolean):GlobeVector3 {
        var sum=GlobeVector3.ZERO;var count=0
        for(t in triangles)for(i in 0..2){val v=t.uv[i*2+1]
            if(if(north)v>=1-1e-7 else v<=1e-7){sum+=when(i){0->t.a;1->t.b;else->t.c};count++}
        }
        check(count>0){"Original mesh has no authored UV pole"}
        return sum.normalized()*2.0
    }
    private fun contour(value:Double,axis:Int):List<Pair<Double,GlobeVector3>> {
        val points=mutableListOf<Pair<Double,GlobeVector3>>()
        for(t in triangles){val vertices=listOf(t.a,t.b,t.c)
            for(i in 0..2){val j=(i+1)%3;val a=t.uv[i*2+axis];val b=t.uv[j*2+axis]
                if(abs(a-b)<1e-12)continue
                val f=(value-a)/(b-a)
                if(f in -1e-7..1.0000001){
                    val other=t.uv[i*2+1-axis]+(t.uv[j*2+1-axis]-t.uv[i*2+1-axis])*f
                    points.add(other to (vertices[i]+(vertices[j]-vertices[i])*f))
                }
            }
        }
        return points.sortedBy{it.first}.fold(mutableListOf()){out,p->
            if(out.isEmpty()||abs(p.first-out.last().first)>1e-7)out.add(p);out
        }
    }
    fun latitudeLine(latitude:Double):List<GlobeVector3> {
        // Uniform samples avoid stitching separate UV contour branches by sort order.
        return (-180..180).map{gridPoint(latitude,it.toDouble())}
    }
    /** Rendering-only bridge across the original scalloped +/-180 UV chart boundary. */
    private fun gridPoint(latitude:Double,longitude:Double):GlobeVector3 {
        val lon=SolarPosition.wrap(longitude)
        val edge=180.0-CoordinateStyle.SEAM_BLEND_DEGREES
        if(abs(lon)<=edge)return point(latitude,lon).normalized()*2.0
        val a=point(latitude,edge).normalized();val b=point(latitude,-edge).normalized()
        val t=((if(lon<0)lon+360 else lon)-edge)/(2*CoordinateStyle.SEAM_BLEND_DEGREES)
        val angle=acos(a.dot(b).coerceIn(-1.0,1.0))
        val direction=if(angle<1e-10)a else (a*sin((1-t)*angle)+b*sin(t*angle)).normalized()
        // Correct the bridge's midpoint latitude, spreading the correction with a
        // smooth bump. Correcting every point separately would reproduce the UV
        // chart's sawtooth. The equator still reads zero at the crossing.
        val midpoint=(a+b).normalized();var correctedMidpoint=midpoint
        repeat(3){
            val actual=coordinate(correctedMidpoint)?.latitude?:return@repeat
            val corrected=asin(correctedMidpoint.z.coerceIn(-1.0,1.0))+Math.toRadians(latitude-actual)
            val horizontal=hypot(correctedMidpoint.x,correctedMidpoint.y)
            if(horizontal>1e-8)correctedMidpoint=GlobeVector3(correctedMidpoint.x*cos(corrected)/horizontal,correctedMidpoint.y*cos(corrected)/horizontal,sin(corrected))
        }
        val correction=asin(correctedMidpoint.z.coerceIn(-1.0,1.0))-asin(midpoint.z.coerceIn(-1.0,1.0))
        val corrected=asin(direction.z.coerceIn(-1.0,1.0))+correction*4*t*(1-t)
        val horizontal=hypot(direction.x,direction.y)
        return GlobeVector3(direction.x*cos(corrected)/horizontal,direction.y*cos(corrected)/horizontal,sin(corrected))*2.0
    }
    fun longitudeLine(longitude:Double):List<GlobeVector3> {
        val lon=SolarPosition.wrap(longitude)
        val start=CoordinateStyle.POLAR_BLEND_START
        val south=gridPoint(-start,lon).normalized();val north=gridPoint(start,lon).normalized()
        return (-90..90).map{degrees->
            val lat=degrees.toDouble();val absolute=abs(lat)
            if(absolute<=start)gridPoint(lat,lon) else {
                val a=if(lat>0)north else south
                val b=(if(lat>0)northPole else southPole).normalized()
                val t=(absolute-start)/(90-start)
                val angle=acos(a.dot(b).coerceIn(-1.0,1.0))
                val cap=(a*(sin((1-t)*angle)/sin(angle))+b*(sin(t*angle)/sin(angle))).normalized()
                if(absolute>=CoordinateStyle.POLAR_BLEND_END)cap*2.0 else {
                    // Only the distorted polar UV fan transitions to a smooth spherical
                    // cap. Point picking and all city coordinates keep exact mesh UVs.
                    val f=((absolute-start)/(CoordinateStyle.POLAR_BLEND_END-start)).coerceIn(0.0,1.0)
                    val weight=f*f*(3-2*f)
                    (gridPoint(lat,lon).normalized()*(1-weight)+cap*weight).normalized()*2.0
                }
            }
        }
    }
    init {
        for(t in triangles){
            val loX=floor(minOf(t.uv[0],t.uv[2],t.uv[4])*64).toInt().coerceIn(0,63)
            val hiX=floor(maxOf(t.uv[0],t.uv[2],t.uv[4])*64).toInt().coerceIn(0,63)
            val loY=floor(minOf(t.uv[1],t.uv[3],t.uv[5])*32).toInt().coerceIn(0,31)
            val hiY=floor(maxOf(t.uv[1],t.uv[3],t.uv[5])*32).toInt().coerceIn(0,31)
            for(y in loY..hiY)for(x in loX..hiX)bins[y*64+x].add(t)
        }
    }
    fun point(latitude:Double,longitude:Double):GlobeVector3 {
        require(latitude.isFinite()&&longitude.isFinite()&&latitude in -90.0..90.0)
        if(abs(latitude)>89.99999)return if(latitude>=0)northPole else southPole
        val u=(SolarPosition.wrap(longitude)+180)/360;val v=(latitude+90)/180
        val candidates=bins[(v*32).toInt().coerceIn(0,31)*64+(u*64).toInt().coerceIn(0,63)]
        for(t in candidates){val w=t.uvWeights(u,v)?:continue
            if(w.minOrNull()!!>=-1e-6)return t.a*w[0]+t.b*w[1]+t.c*w[2]
        }
        // The original UV seam is slightly scalloped, and pole fans don't cover a
        // rectangular UV chart. Bridge only these gaps along the same latitude
        // contour; actual hit readout below still uses the original triangles.
        val line=latitudeContours.getOrPut(latitude){contour(v,1)}
        check(line.isNotEmpty()){"Original UV latitude contour missing: $latitude"}
        val next=line.indexOfFirst{it.first>=u}
        val left=when{next<0->line.last();next==0->line.last().let{it.first-1 to it.second};else->line[next-1]}
        val right=if(next<0)line.first().let{it.first+1 to it.second} else line[next]
        val f=((u-left.first)/(right.first-left.first)).coerceIn(0.0,1.0)
        return (left.second+(right.second-left.second)*f).normalized()*2.0
    }
    fun coordinate(pointInMesh:GlobeVector3):GeoCoordinate? {
        val d=pointInMesh.normalized()
        if(d==GlobeVector3.ZERO)return null
        // Longitude is undefined at the actual shared UV vertices, not a guessed axis.
        if(d.dot(northPole.normalized())>1-5e-13)return GeoCoordinate(90.0,null)
        if(d.dot(southPole.normalized())>1-5e-13)return GeoCoordinate(-90.0,null)
        // The ideal direction is only a search hint. Every returned coordinate is
        // still calculated from the actual intersected mesh triangle's UVs.
        val u=(SolarPosition.wrap(Math.toDegrees(atan2(d.y,d.x))-198.0)+180)/360
        val v=(Math.toDegrees(asin(d.z.coerceIn(-1.0,1.0)))+90)/180
        val bx=(u*64).toInt().coerceIn(0,63);val by=(v*32).toInt().coerceIn(0,31)
        for(y in max(0,by-1)..min(31,by+1))for(dx in -1..1){
            val x=(bx+dx+64)%64
            for(t in bins[y*64+x])t.rayUv(d)?.let{return it}
        }
        for(t in triangles)t.rayUv(d)?.let{return it}
        return null
    }
    companion object {
        fun read(stream:InputStream):CoordinateSurface=DataInputStream(stream.buffered()).use{input->
            require(input.readInt()==0x47454F31)
            val count=input.readInt();require(count in 1..100_000)
            CoordinateSurface(List(count){
                fun vertex()=GlobeVector3(input.readFloat().toDouble(),input.readFloat().toDouble(),input.readFloat().toDouble())
                Triangle(vertex(),vertex(),vertex(),DoubleArray(6){input.readFloat().toDouble()})
            })
        }
    }
}
