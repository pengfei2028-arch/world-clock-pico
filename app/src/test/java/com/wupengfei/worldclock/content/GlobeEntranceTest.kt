package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GlobeEntranceTest {
    @Test fun sourceRevealAndRetimedSpinPreserveBlenderReferenceBeforeHandoff() {
        val stream = javaClass.classLoader!!.getResourceAsStream("blender_entrance.csv")!!
        stream.bufferedReader().useLines { lines -> lines.forEach { line ->
            val n = line.split(',').map { it.toDouble() }
            val retimed = GlobeEntrance.APPROACH_START_SECONDS+n[0]*4.0/(69.0/39.0)
            if (retimed <= GlobeEntrance.HANDOFF_START) {
                assertEquals(n[4],GlobeEntrance.sample(retimed).spinDegrees,0.0001)
            }
            val a = ((1+n[5]-0.5272725820541382)/(1-0.5272725820541382)).coerceIn(0.0,1.0)
            assertEquals(a*a*(3-2*a),GlobeEntrance.sample(GlobeEntrance.APPROACH_START_SECONDS+n[0]).opacity,0.00001)
        } }
    }
    @Test fun approachTakesFourSecondsWithSoftStartAndLongerDampedArrival() {
        assertEquals(4.0,GlobeEntrance.APPROACH_SECONDS,0.0)
        assertEquals(GlobeEntrance.APPROACH_START_SECONDS+4.0,GlobeEntrance.DURATION,0.0)
        fun z(t: Double) = GlobeEntrance.sample(GlobeEntrance.APPROACH_START_SECONDS+t).position.z
        fun speed(t: Double) = (z(t+0.0001)-z(t-0.0001))/0.0002
        assertEquals(0.0,speed(0.0),0.0001)
        assertEquals(0.0,speed(4.0),0.0001)
        assertTrue(speed(2.0)>speed(3.0)); assertTrue(speed(3.0)>speed(3.5))
        assertTrue(speed(3.5)>speed(3.9))
        assertTrue(z(3.0)<-60.1); assertTrue(z(3.9)<-60.0)
        assertEquals(-60.0,z(4.0),1e-9)
        assertEquals(z(4.0),z(4.1),0.0)
    }
    @Test fun veilReachesHalfBeforeGlobeCanAppearMoveOrSpin() {
        val first = GlobeEntrance.sample(0.0)
        val cue = GlobeEntrance.APPROACH_START_SECONDS
        repeat(101) { step ->
            val pose = GlobeEntrance.sample(cue*step/100.0)
            assertEquals(first.position,pose.position)
            assertEquals(0.0,pose.opacity,0.0)
            assertEquals(0.0,pose.spinDegrees,0.0)
        }
        assertEquals(0.5,GlobeEntrance.sample(cue).veilProgress,1e-12)
        assertEquals(0.0,first.veilOpacity,0.0)
        assertEquals(1.0,GlobeEntrance.sample(cue).veilOpacity,0.0)
        assertTrue(GlobeEntrance.sample(cue+0.1).position.z > first.position.z)
        assertTrue(GlobeEntrance.sample(cue+0.5).opacity > 0.0)
        val veilDone = GlobeEntrance.sample(GlobeEntrance.VEIL_SECONDS)
        assertEquals(1.0,veilDone.veilProgress,0.0)
        assertTrue(veilDone.position.z < -60.0)
    }
    @Test fun firstPoseAndFinalFramingAreExact() {
        val a=GlobeEntrance.sample(0.0); val b=GlobeEntrance.sample(GlobeEntrance.DURATION)
        assertEquals(0.0,a.spinDegrees,0.0); assertEquals(0.0,a.opacity,0.0)
        assertTrue(-a.position.z > 100.0); assertEquals(-60.0,b.position.z,1e-9)
        assertEquals(0.0,b.position.x,1e-9); assertEquals(-4.1956087166,b.position.y,1e-8)
        assertEquals(1.0,b.opacity,0.0); assertEquals(1.0,b.veilProgress,0.0)
    }
    @Test fun approachAndRevealAreMonotonicAndDoNotOvershoot() {
        var old=GlobeEntrance.sample(0.0)
        repeat(700) { val p=GlobeEntrance.sample((it+1)/100.0)
            assertTrue(p.position.z >= old.position.z-1e-9)
            assertTrue(p.opacity >= old.opacity && p.opacity in 0.0..1.0)
            assertTrue(p.veilProgress >= old.veilProgress && p.veilProgress in 0.0..1.0)
            assertTrue(p.veilOpacity >= old.veilOpacity && p.veilOpacity in 0.0..1.0)
            assertTrue(p.spinDegrees >= old.spinDegrees); old=p
        }
    }
    @Test fun spinHandoffIsContinuousAndEndsAtIdleSpeed() {
        val h=0.00001; val t=GlobeEntrance.HANDOFF_START
        val left=(GlobeEntrance.sample(t).spinDegrees-GlobeEntrance.sample(t-h).spinDegrees)/h
        val right=(GlobeEntrance.sample(t+h).spinDegrees-GlobeEntrance.sample(t).spinDegrees)/h
        assertEquals(left,right,0.001)
        val end=GlobeEntrance.DURATION
        assertEquals(3.0,(GlobeEntrance.sample(end).spinDegrees-GlobeEntrance.sample(end-h).spinDegrees)/h,0.0001)
    }
    @Test fun loadingGapsAndPinchesCannotSkipTheOpeningAndIdleGetsOnlyRemainder() {
        val e=GlobeEntrance(); e.advance(20.0); assertEquals(0.05,e.elapsed,0.0)
        val p=e.pose; repeat(300) {e.advance(1.0/90.0,true)}; assertEquals(p,e.pose)
        var idle=0.0; repeat(700) {idle+=e.advance(0.01)}
        assertTrue(e.finished); assertEquals(7.05-GlobeEntrance.DURATION,idle,1e-8)
    }
}
