package com.wupengfei.worldclock.content

import kotlin.math.*

/** Geodesic calculation is independent of mesh UVs, presentation pose and time. */
internal object GreatCircle {
    fun unit(lat:Double,lon:Double):GlobeVector3 {
        require(lat.isFinite()&&lon.isFinite()&&lat in -90.0..90.0)
        val a=Math.toRadians(lat);val b=Math.toRadians(lon)
        return GlobeVector3(cos(a)*cos(b),sin(a),cos(a)*sin(b))
    }
    fun angle(a:GlobeVector3,b:GlobeVector3):Double {
        val x=a.normalized();val y=b.normalized();val cross=x.cross(y)
        return atan2(sqrt(cross.dot(cross)),x.dot(y).coerceIn(-1.0,1.0))
    }
    fun km(a:ClockCity,b:ClockCity)=km(a.latitude,a.longitude,b.latitude,b.longitude)
    fun km(latA:Double,lonA:Double,latB:Double,lonB:Double)=
        DistanceStyle.RADIUS_KM*angle(unit(latA,lonA),unit(latB,lonB))
    /** A deterministic semicircle for exact antipodes (all semicircles are shortest).
     * Derive each sample from the original pair, never repeatedly rotate old points. */
    fun interpolate(a:GlobeVector3,b:GlobeVector3,t:Double):GlobeVector3 {
        val x=a.normalized();val y=b.normalized()
        if(t<=0)return x;if(t>=1)return y
        val theta=angle(x,y)
        if(theta<1e-10)return (x*(1-t)+y*t).normalized()
        val tangent=y-x*x.dot(y)
        val n=if(tangent.dot(tangent)>1e-20)tangent.normalized() else {
            val basis=listOf(GlobeVector3.X,GlobeVector3.Y,GlobeVector3.Z).minBy{abs(it.dot(x))}
            (basis-x*basis.dot(x)).normalized()
        }
        return (x*cos(theta*t)+n*sin(theta*t)).normalized()
    }
    fun nearest(cities:List<ClockCity>,coordinate:GeoCoordinate):ClockCity? {
        val hit=unit(coordinate.latitude,coordinate.longitude?:0.0)
        val city=cities.minByOrNull{angle(hit,unit(it.latitude,it.longitude))}?:return null
        return city.takeIf{angle(hit,unit(it.latitude,it.longitude))<=Math.toRadians(DistanceStyle.PICK_DEGREES)}
    }
}

internal data class DistancePath(val samples:List<GlobeVector3>,val midpoint:GlobeVector3) {
    fun geometry()=CoordinateGeometry().apply {
        tube(samples,DistanceStyle.LINE_WIDTH)
        for(p in listOf(samples.first(),samples.last())){
            val n=p.normalized()
            val a=n.cross(if(abs(n.z)<.95)GlobeVector3.Z else GlobeVector3.X).normalized()
            val b=n.cross(a).normalized()
            tube((0..48).map{i->val t=i*2*PI/48;p+(a*cos(t)+b*sin(t))*DistanceStyle.ENDPOINT_RADIUS},DistanceStyle.LINE_WIDTH)
        }
    }
    companion object {
        fun create(surface:CoordinateSurface,a:ClockCity,b:ClockCity):DistancePath {
            // Exact authored city anchors; smooth shortest spherical arc in the same
            // mesh-local frame. UV seam/polar fans cannot introduce kinks into a line.
            val start=surface.point(a.latitude,a.longitude).normalized()
            val end=surface.point(b.latitude,b.longitude).normalized()
            val segments=max(8,ceil(Math.toDegrees(GreatCircle.angle(start,end))*2).toInt()).let{it+it%2}
            val samples=(0..segments).map{i->val t=i.toDouble()/segments
                GreatCircle.interpolate(start,end,t)*(2*(DistanceStyle.SURFACE_SCALE+DistanceStyle.ARC_LIFT*sin(PI*t)))
            }
            return DistancePath(samples,samples[segments/2])
        }
    }
}
