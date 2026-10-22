package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class V42InteractionTest {
    @Test fun cloudsStayHiddenForWholeHoldAndReleaseDwell(){
        val m=GlobeMotion();assertTrue(m.cloudsMayShow(0.0))
        m.begin(0.0);m.drag(0.0,100.0,.05)
        repeat(200){m.tick(.05,.1+it*.05);assertFalse(m.cloudsMayShow(.1+it*.05))}
        m.end(11.0);assertTrue(m.restoringTilt)
        assertFalse(m.cloudsMayShow(13.999))
        m.tick(.016,14.016);assertTrue(m.restoringTilt);assertTrue(m.cloudsMayShow(14.016))
        m.begin(14.02);assertFalse(m.cloudsMayShow(14.02))
    }
    @Test fun focusCloudsWaitThroughCenteringAndFiveSecondDwell(){
        val m=GlobeMotion();m.focusTo(GlobeQuaternion.axisAngle(GlobeVector3.X,40.0))
        var time=0.0
        while(m.focusing){time+=.02;m.tick(.02,time);assertFalse(m.cloudsMayShow(time))}
        repeat(240){time+=.02;m.tick(.02,time);assertFalse(m.cloudsMayShow(time))}
        m.noteUiActivity(time)
        repeat(240){time+=.02;m.tick(.02,time);assertFalse(m.cloudsMayShow(time))}
        repeat(15){time+=.02;m.tick(.02,time)}
        assertFalse(m.focusLocked);assertTrue(m.cloudsMayShow(time));assertTrue(m.restoringTilt)
    }
    @Test fun cloudFadeReversesContinuouslyAndCompletesAtDifferentFrameRates(){
        for(rate in listOf(30,60,90)){
            val c=CloudVisibility();repeat(rate){c.update(false,1.0/rate)}
            assertEquals(0f,c.opacity,0f)
            repeat(rate/2){c.update(true,1.0/rate)}
            assertTrue(c.opacity in .1f.. .5f)
            val before=c.opacity;assertEquals(before,c.update(false,0.0),0f)
            repeat(rate){c.update(false,1.0/rate)};assertEquals(0f,c.opacity,0f)
            repeat(rate*2){c.update(true,1.0/rate)};assertEquals(1f,c.opacity,0f)
        }
    }
    @Test fun captionNoiseDoesNotAccumulateAndReleaseStopsIt(){
        val d=ToolbarDrag();d.begin(100.0,200.0,0.0)
        repeat(900){val t=(it+1)/90.0;d.sample(100+.2*sin(it.toDouble()),200+.2*cos(it.toDouble()),t);d.advance(1.0/90)}
        assertEquals(0.0,d.x,0.0);assertEquals(0.0,d.y,0.0)
        d.sample(120.0,200.0,10.01);d.advance(.016);d.end();val x=d.x
        repeat(100){d.advance(.016)};assertEquals(x,d.x,0.0)
    }
    @Test fun captionAbsoluteTrackingStaysMonotonicAcrossSampleRates(){
        val ends=listOf(30,60,90).map { rate ->
            val d=ToolbarDrag();d.begin(100.0,0.0,0.0);var previous=0.0
            repeat(rate*2){val t=(it+1).toDouble()/rate;d.sample(100+t*40,0.0,t);d.advance(1.0/rate);assertTrue(d.x>=previous);previous=d.x}
            assertTrue(d.x>77.0);assertTrue(d.x<80.0);d.x
        }
        assertTrue(ends.maxOrNull()!!-ends.minOrNull()!!<.6)
    }
    @Test fun captionTrackingGapCannotTeleportPanel(){
        val d=ToolbarDrag();d.begin(0.0,0.0,0.0)
        d.sample(500.0,500.0,2.0);d.advance(.016)
        assertEquals(0.0,d.x,0.0);assertEquals(0.0,d.y,0.0)
        d.sample(501.0,500.0,2.016);d.advance(.016)
        assertTrue(d.x in 0.0..1.0);assertEquals(0.0,d.y,0.0)
    }
}
