package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class QuakeDecalTest {
    @Test fun patchesFollowGreatCircleDistanceAtPolesSeamsAndBothHemispheres(){
        for(lat in listOf(-90.0,-89.9,-35.0,0.0,39.9,89.9,90.0))for(lon in listOf(-180.0,-179.99,0.0,116.4,179.99,180.0)){
            val center=GlobeGeography.direction(lat,lon).normalized()*2.024
            val maxAngle=Math.toRadians(QuakeStyle.WAVE_PATCH_DEGREES)
            val geometry=QuakeDecalGeometry().apply{patch(center,maxAngle,true,1,.25,-1.0,42)}
            for(i in geometry.positions.indices){
                val p=geometry.positions[i];val uv=geometry.uv[i]
                assertTrue(p.x.isFinite()&&p.y.isFinite()&&p.z.isFinite())
                assertEquals(2.024,sqrt(p.dot(p)),1e-10)
                val angle=acos(p.normalized().dot(center.normalized()).coerceIn(-1.0,1.0))
                assertEquals(hypot(uv.first,uv.second)*maxAngle,angle,3e-8)
            }
            for(t in geometry.indices.chunked(3)){
                val a=geometry.positions[t[0]];val b=geometry.positions[t[1]];val c=geometry.positions[t[2]]
                assertTrue((b-a).cross(c-a).dot(a+b+c)>0)
                // Even interpolated triangles stay above the original 1.00765 cloud shell.
                val middle=(a+b+c)*(1.0/3);assertTrue(sqrt(middle.dot(middle))>2*1.0076545)
            }
        }
    }
    @Test fun animationMetadataIsConstantPerPatchAndDoesNotNeedGeometryRebuilds(){
        val g=QuakeDecalGeometry().apply{patch(GlobeVector3.Z*2.024,.1,true,2,.37,12.5,100)}
        assertEquals(81,g.positions.size);assertEquals(128,g.triangleCount)
        assertTrue(g.meta.all{it==12.5 to 100.0});assertTrue(g.colors.all{it==QuakeVertexData(1.0,2.0/(QuakeStyle.MAX_RIPPLE_GROUPS-1),.37,0.0)})
        assertEquals(g.positions.size,g.uv.size);assertEquals(g.positions.size,g.normals.size)
    }
    @Test fun monthlyCatalogPointsShareOneCompactGeometry(){
        val g=QuakeDecalGeometry()
        repeat(2020){i->g.patch(GlobeGeography.direction(i%180-90.0,i%360-180.0).normalized()*2.024,.01,false,0,0.0,-1.0,i+1)}
        assertEquals(2020*9,g.positions.size);assertEquals(2020*8,g.triangleCount)
        assertTrue(g.indices.all{it in g.positions.indices})
    }
    @Test fun affineSnapshotIncludesNonUniformScaleTranslationAndRotation(){
        val q=GlobeQuaternion.axisAngle(GlobeVector3.Y,1.2)*GlobeQuaternion.axisAngle(GlobeVector3.Z,.4)
        val origin=GlobeVector3(10.0,-3.0,20.0)
        val frame=QuakeSurfaceFrame(origin,q.rotate(GlobeVector3.X)*2.0,q.rotate(GlobeVector3.Y)*3.0,q.rotate(GlobeVector3.Z)*4.0)
        for(p in listOf(GlobeVector3.ZERO,GlobeVector3(1.0,2.0,-3.0),GlobeVector3(-.5,.7,1.5))){
            val expected=origin+q.rotate(GlobeVector3(p.x*2,p.y*3,p.z*4));val actual=frame.point(p)
            assertEquals(expected.x,actual.x,1e-10);assertEquals(expected.y,actual.y,1e-10);assertEquals(expected.z,actual.z,1e-10)
        }
    }
    @Test fun digitsShareOneAtlasAndRemainOnSurface(){
        val g=QuakeDecalGeometry().apply{text(GlobeVector3.Z*2.024,"128")}
        assertEquals(12,g.positions.size);assertEquals(6,g.triangleCount)
        assertTrue(g.uv.all{it.first in 0.0..1.0&&it.second in 0.0..1.0})
        // Built-in Unlit vertex modulation must preserve the white font atlas.
        assertTrue(g.colors.all{it==QuakeVertexData(1.0,1.0,1.0,1.0)})
        g.positions.forEach{assertEquals(2.024,sqrt(it.dot(it)),1e-10)}
    }
}
