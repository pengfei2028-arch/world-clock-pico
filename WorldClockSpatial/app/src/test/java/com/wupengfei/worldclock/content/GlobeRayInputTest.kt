package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test

class GlobeRayInputTest {
    @Test fun rayMotionRotatesWithStationaryDeviceAndNeverSelectsOnRelease(){
        val motion=GlobeMotion();val input=GlobePointerInput(motion);val ray=GlobeRayInput();val tap=CoordinateTap()
        val head=GlobeVector3(0.0,1.65,-.256)
        ray.begin(head,GlobeQuaternion.IDENTITY)
        val start=ray.relative(head+GlobeVector3(0.0,0.0,-60.0))
        tap.begin(1,GeoCoordinate(20.0,30.0),0.0);tap.position(start)
        input.updateHand(1,true,true,start,GlobeVector3.ZERO,0.0)
        repeat(60){i->
            val point=ray.relative(head+GlobeVector3((i+1)*.2,0.0,-60.0))
            tap.position(point)
            input.updateHand(1,false,true,point,GlobeVector3.ZERO,(i+1)/60.0)
            tap.observe(input.moving,input.rejectedSamples>0)
        }
        assertTrue(motion.presentation.angleTo(GlobeQuaternion.IDENTITY)>20)
        assertEquals(0,input.rejectedSamples)
        input.updateHand(1,false,false,start,GlobeVector3.ZERO,1.05)
        assertFalse(motion.dragging);assertNull(tap.end(1,1.05))
    }
    @Test fun rayProjectionDoesNotChangeWhenTheSurfaceDepthChanges(){
        val ray=GlobeRayInput();val head=GlobeVector3(4.0,1.65,2.0)
        val q=GlobeQuaternion.axisAngle(GlobeVector3.Y,35.0);ray.begin(head,q)
        val direction=q.rotate(GlobeVector3(.2,-.1,-1.0))
        val first=ray.relative(head+direction*24.0)
        for(depth in listOf(30.0,60.0,100.0)){
            val p=ray.relative(head+direction*depth)
            assertEquals(first.x,p.x,1e-12);assertEquals(first.y,p.y,1e-12);assertEquals(first.z,p.z,1e-12)
        }
        assertFalse(ray.relative(head-direction).x.isFinite())
    }
    @Test fun readDwellDoesNotBlockDragOrChangeTheSelectedDistanceCities(){
        val state=DistanceState()
        val cities=CityCatalog.read(java.io.File("src/main/assets/cities/catalog.tsv").bufferedReader()).let{
            listOf(checkNotNull(it.city("1816670")),checkNotNull(it.city("2147714")),checkNotNull(it.city("2643743")))
        }
        state.toggle(cities);state.pick(cities[0])
        for(complete in listOf(false,true)){
            if(complete)state.pick(cities[1])
            val selected=state.firstId to state.secondId
            val motion=GlobeMotion();motion.readCoordinates(0.0)
            val input=GlobePointerInput(motion);input.update(1,true,true,0.0,0.0,.01)
            repeat(60){i->input.update(1,false,true,(i+1)*.002,0.0,.01+(i+1)/60.0)}
            assertTrue(motion.presentation.angleTo(GlobeQuaternion.IDENTITY)>20)
            assertEquals(selected,state.firstId to state.secondId)
        }
    }
}
