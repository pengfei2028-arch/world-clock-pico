package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test

class V43InteractionTest {
    @Test fun timelineFreezesPresentationButNotLightShimmerAndWaitsThreeSeconds(){
        val m=GlobeMotion();m.tick(.05,.05)
        m.setTimelineHeld(true,.1);val spin=m.idleDegrees;val pose=m.presentation;val effects=m.effectSeconds
        repeat(600){m.tick(1.0/60,.1+(it+1)/60.0)}
        assertEquals(spin,m.idleDegrees,0.0);assertEquals(pose,m.presentation)
        assertTrue(m.effectSeconds>effects+9.9);assertFalse(m.cloudsMayShow(10.1))
        m.setTimelineHeld(false,10.2);repeat(179){m.tick(1.0/60,10.2+(it+1)/60.0)}
        assertEquals(spin,m.idleDegrees,0.0);assertEquals(pose,m.presentation)
        m.tick(.02,13.22);assertTrue(m.idleDegrees>spin)
    }
    @Test fun timelineInterruptsFocusAndReturnsSlowlyFromThatPose(){
        val m=GlobeMotion();m.focusTo(GlobeQuaternion.axisAngle(GlobeVector3.X,60.0))
        repeat(90){m.tick(1.0/60,(it+1)/60.0)}
        val pose=m.presentation;assertTrue(m.tiltErrorDegrees>10)
        m.setTimelineHeld(true,1.6);assertFalse(m.focusLocked)
        repeat(300){m.tick(.02,1.62+it*.02)};assertEquals(pose,m.presentation)
        m.setTimelineHeld(false,7.7);m.tick(.02,10.72)
        assertTrue(m.restoringTilt);assertTrue(pose.angleTo(m.presentation)<.01)
        repeat(250){m.tick(.02,10.74+it*.02)}
        assertFalse(m.restoringTilt);assertTrue(m.tiltErrorDegrees<1e-5)
    }
    @Test fun overlappingGlobeAndTimelineHoldsCannotReleaseEachOther(){
        val m=GlobeMotion();m.begin(0.0);m.drag(100.0,100.0,.05);val pose=m.presentation
        m.setTimelineHeld(true,.1);m.drag(100.0,100.0,.15);assertEquals(pose,m.presentation)
        m.end(.2);repeat(300){m.tick(.02,.22+it*.02)};assertEquals(pose,m.presentation)
        m.setTimelineHeld(false,6.3);m.tick(.02,9.31);assertTrue(m.restoringTilt)
        m.setTimelineHeld(true,9.32);val duringReturn=m.presentation
        repeat(100){m.tick(.02,9.34+it*.02)};assertEquals(duringReturn,m.presentation)
    }
    @Test fun initialCloudsStayHiddenForEntireEntranceThenFadeSmoothly(){
        val c=CloudVisibility();val m=GlobeMotion();val entrance=GlobeEntrance()
        assertEquals(0f,c.opacity,0f);assertTrue(m.cloudsMayShow(0.0))
        // A paused entrance must not reveal clouds just because wall time passes.
        repeat(120){entrance.advance(1.0/60,true);assertEquals(0f,c.update(c.canReveal(entrance.finished),1.0/60),0f)}
        while(!entrance.finished){
            assertFalse(c.canReveal(entrance.finished))
            assertEquals(0f,c.update(c.canReveal(entrance.finished),1.0/60),0f)
            entrance.advance(1.0/60)
        }
        assertEquals(GlobeEntrance.DURATION,entrance.elapsed,1e-8)
        assertTrue(c.canReveal(entrance.finished))
        val first=c.update(c.canReveal(entrance.finished) && m.cloudsMayShow(entrance.elapsed),1.0/60)
        assertTrue(first>0f && first<.001f)
        repeat(41){c.update(true,1.0/60)};assertEquals(.5f,c.opacity,.001f)
        repeat(42){c.update(true,1.0/60)};assertEquals(1f,c.opacity,.001f)
    }
    @Test fun remoteCaptionRestoresDepthProportionalTravelWithoutChangingMouseTravel(){
        val normal=ToolbarDrag();val remote=ToolbarDrag()
        val gain=ToolbarDrag.depthGain(2.5,.5);assertEquals(5.0,gain,0.0)
        normal.begin(0.0,0.0,0.0);remote.begin(0.0,0.0,0.0,gain)
        repeat(120){val t=(it+1)/60.0;normal.sample(t*10,0.0,t);remote.sample(t*10,0.0,t);normal.advance(1.0/60);remote.advance(1.0/60)}
        assertTrue(normal.x in 18.0..20.0);assertTrue(remote.x in 97.0..100.0)
        assertEquals(6.0,ToolbarDrag.depthGain(2.5,.01),0.0)
        assertEquals(1.0,ToolbarDrag.depthGain(2.5,Double.NaN),0.0)
    }
}
