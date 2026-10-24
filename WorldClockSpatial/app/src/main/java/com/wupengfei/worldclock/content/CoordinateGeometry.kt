package com.wupengfei.worldclock.content

import kotlin.math.*

/** Batched round lines retain a visible cross-section when viewed at grazing angles. */
internal class CoordinateGeometry {
    val positions=mutableListOf<GlobeVector3>()
    val indices=mutableListOf<Int>()
    private fun triangle(a:Int,b:Int,c:Int){
        val cross=(positions[b]-positions[a]).cross(positions[c]-positions[a])
        if(cross.dot(cross)>1e-18)indices.addAll(listOf(a,b,c))
    }
    fun tube(samples:List<GlobeVector3>,width:Double):CoordinateGeometry {
        val path=samples.fold(mutableListOf<GlobeVector3>()){out,p->if(out.isEmpty()||(p-out.last()).let{it.dot(it)}>1e-12)out.add(p);out}
        if(path.size<2)return this
        val closed=path.size>3&&(path.first()-path.last()).let{it.dot(it)}<1e-12
        val sides=8;val start=positions.size
        path.forEachIndexed{i,p->
            val before=path[if(i==0&&closed)path.lastIndex-1 else max(i-1,0)]
            val after=path[if(i==path.lastIndex&&closed)1 else min(i+1,path.lastIndex)]
            val tangent=(after-before).normalized()
            var a=tangent.cross(p.normalized()).normalized()
            if(a==GlobeVector3.ZERO)a=tangent.cross(if(abs(tangent.z)<.95)GlobeVector3.Z else GlobeVector3.X).normalized()
            val b=tangent.cross(a).normalized()
            for(j in 0 until sides){val angle=j*2*PI/sides
                positions.add(p+(a*cos(angle)+b*sin(angle))*(width/2))
            }
        }
        for(i in 0 until path.lastIndex)for(j in 0 until sides){
            val a=start+i*sides+j;val b=start+i*sides+(j+1)%sides
            triangle(a,b,a+sides);triangle(b,b+sides,a+sides)
        }
        if(!closed){
            val first=positions.size;positions.add(path.first());val last=positions.size;positions.add(path.last())
            for(j in 0 until sides){
                triangle(first,start+(j+1)%sides,start+j)
                val end=start+path.lastIndex*sides
                triangle(last,end+j,end+(j+1)%sides)
            }
        }
        return this
    }
    companion object {
        fun grid(surface:CoordinateSurface):CoordinateGeometry {
            val mesh=CoordinateGeometry();val width=CoordinateStyle.GRID_WIDTH
            fun continuous(path:List<GlobeVector3>){
                mesh.tube(path.map{it*CoordinateStyle.SURFACE_SCALE},width)
            }
            for(lat in -60..60 step 30)if(lat!=0){
                continuous(surface.latitudeLine(lat.toDouble()))
            }
            // One half-meridian per longitude: 180 degrees is not the prime meridian.
            for(lon in -180 until 180 step 30)if(lon!=0){
                continuous(surface.longitudeLine(lon.toDouble()))
            }
            return mesh
        }
        fun reference(surface:CoordinateSurface,latitude:Boolean):CoordinateGeometry {
            val path=if(latitude)surface.latitudeLine(0.0) else surface.longitudeLine(0.0)
            val width=if(latitude)CoordinateStyle.EQUATOR_WIDTH else CoordinateStyle.PRIME_WIDTH
            return CoordinateGeometry().tube(path.map{it*CoordinateStyle.SURFACE_SCALE},width)
        }
        fun rulers(surface:CoordinateSurface):CoordinateGeometry {
            val mesh=CoordinateGeometry()
            for(latitude in listOf(true,false)){
                val path=if(latitude)surface.longitudeLine(CoordinateStyle.LATITUDE_RULER_LONGITUDE)
                    else surface.latitudeLine(CoordinateStyle.LONGITUDE_RULER_LATITUDE)
                val offset=if(latitude)90 else 180
                // A short opening at the common zero avoids two crossing outer rails.
                // The real equator/prime intersection remains on the geographic shell.
                val ranges=if(latitude)listOf(-75..-5,5..75) else listOf(-75..75)
                ranges.forEach{range->mesh.tube(range.map{path[it+offset]*CoordinateStyle.RULER_RADIUS},CoordinateStyle.RULER_WIDTH)}
                for(degree in -75..75 step 5){
                    val major=degree%30==0
                    // One ordinary zero tick; named labels have no leaders.
                    if(degree==0&&latitude)continue
                    // Numerical ticks use exact texture coordinates. The nearby arc's
                    // polar smoothing must not shift a 75-degree tick by ~0.1 degree.
                    val p=surface.point(if(latitude)degree.toDouble() else CoordinateStyle.LONGITUDE_RULER_LATITUDE,
                        if(latitude)CoordinateStyle.LATITUDE_RULER_LONGITUDE else degree.toDouble()).normalized()*2.0
                    mesh.tube(listOf(p*(CoordinateStyle.RULER_RADIUS-if(major).012 else .0),
                        p*(CoordinateStyle.RULER_RADIUS+if(major).045 else .018)),CoordinateStyle.RULER_WIDTH)
                }
            }
            return mesh
        }
        fun ring():CoordinateGeometry {
            return CoordinateGeometry().tube((0..48).map{i->val t=i*2*PI/48
                GlobeVector3(cos(t)*CoordinateStyle.MARKER_RADIUS,sin(t)*CoordinateStyle.MARKER_RADIUS,0.0)
            },CoordinateStyle.MARKER_WIDTH)
        }
    }
}
