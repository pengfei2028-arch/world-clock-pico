package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test

class EntityFrameTest {
    private fun assertVector(a:GlobeVector3,b:GlobeVector3){assertEquals(a.x,b.x,1e-8);assertEquals(a.y,b.y,1e-8);assertEquals(a.z,b.z,1e-8)}
    @Test fun localHierarchyCompositionIncludesRotationScaleAndOffset(){
        val child=QuakeSurfaceFrame.trs(GlobeVector3(.2,-.3,.4),GlobeQuaternion.axisAngle(GlobeVector3.Z,67.0),GlobeVector3(2.0,3.0,4.0))
        val parent=QuakeSurfaceFrame.trs(GlobeVector3(0.0,-4.1956,-60.0),GlobeQuaternion.axisAngle(GlobeVector3.Y,145.0),GlobeVector3(12.0,12.0,12.0))
        val p=GlobeVector3(.3,.7,-.4)
        assertVector(parent.point(child.point(p)),child.then(parent).point(p))
        assertVector(p,child.then(parent).then(child.then(parent).inverse()).point(p))
    }
    @Test fun relativeAncestorFrameExcludesPresentationForEveryRotation(){
        val local=QuakeSurfaceFrame.trs(GlobeVector3.ZERO,GlobeQuaternion.axisAngle(GlobeVector3.X,-90.0)*GlobeQuaternion.axisAngle(GlobeVector3.Y,-23.44),GlobeVector3(1.0,1.0,1.0))
        val p=GlobeGeography.direction(-6.1737,147.1659)*2.024
        for(angle in 0..360 step 13){
            val parent=QuakeSurfaceFrame.trs(GlobeVector3(2.0,-4.0,-60.0),GlobeQuaternion.axisAngle(GlobeVector3(1.0,2.0,3.0),angle.toDouble()),GlobeVector3(12.0,12.0,12.0))
            assertVector(local.point(p),local.then(parent).then(parent.inverse()).point(p))
        }
    }
    @Test fun inverseWorksWithNonuniformScaleAndRotatedChildShear(){
        val a=QuakeSurfaceFrame.trs(GlobeVector3(1.0,3.0,2.0),GlobeQuaternion.axisAngle(GlobeVector3.Z,37.0),GlobeVector3(1.0,2.0,3.0))
        val b=QuakeSurfaceFrame.trs(GlobeVector3(3.0,-1.0,-6.0),GlobeQuaternion.axisAngle(GlobeVector3.Y,-41.0),GlobeVector3(2.0,3.0,1.0))
        for(p in listOf(GlobeVector3.X,GlobeVector3.Y,GlobeVector3.Z,GlobeVector3.ZERO))assertVector(p,a.then(b).inverse().point(a.then(b).point(p)))
    }
}
