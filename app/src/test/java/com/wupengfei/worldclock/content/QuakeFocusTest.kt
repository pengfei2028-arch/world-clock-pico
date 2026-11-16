package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class QuakeFocusTest {
    private val surface=CoordinateSurface.read(File("src/main/assets/coordinates/earth_surface.bin").inputStream())
    private val tilt=GlobeQuaternion.axisAngle(GlobeVector3.X,-90.0)*GlobeQuaternion.axisAngle(GlobeVector3.Y,-23.44)
    private val toward=GlobeVector3(0.0,.07,1.0).normalized()
    private val places=listOf(-13.9418 to 165.6471,-6.1737 to 147.1659,-14.9964 to 167.3039,39.9 to 116.4,51.5 to -.13,-33.9 to 151.2,40.7 to -74.0,0.0 to 179.99,0.0 to -179.99,89.99 to 25.0,-89.99 to -110.0)
    private fun point(lat:Double,lon:Double,spin:Double)=tilt.rotate(GlobeQuaternion.axisAngle(GlobeVector3.Z,spin).rotate(surface.point(lat,lon))).normalized()
    private fun finish(m:GlobeMotion,start:Double):Double {
        var now=start;var frames=0
        while(m.focusing&&frames++<1000){now+=.01;m.tick(.01,now)}
        assertFalse(m.focusing);assertTrue(m.focusLocked)
        return now
    }
    @Test fun repeatedSelectionsCenterTheirActualUvPointsWithoutAccumulatingRotation(){
        val m=GlobeMotion();m.setNormalPole(tilt.rotate(GlobeVector3.Z));var now=0.0
        repeat(3){round->for((lat,lon)in places){
            val local=point(lat,lon,89.57+round*40)
            val before=m.presentation;m.focusLocalPoint(local,toward)
            assertEquals(before,m.presentation) // Selection itself never snaps the globe.
            now=finish(m,now)
            assertEquals(1.0,m.presentation.rotate(local).dot(toward),1e-8)
        }}
    }
    @Test fun clickingAgainDuringAnUnfinishedFocusUsesCurrentPoseAndNewTarget(){
        val m=GlobeMotion();m.setNormalPole(tilt.rotate(GlobeVector3.Z));var now=0.0
        for((lat,lon)in places){
            m.focusLocalPoint(point(lat,lon,25.0),toward)
            repeat(20){now+=.01;m.tick(.01,now)}
        }
        val final=point(-6.1737,147.1659,25.0)
        m.focusLocalPoint(final,toward);finish(m,now)
        assertEquals(1.0,m.presentation.rotate(final).dot(toward),1e-8)
    }
    @Test fun returningToSameEventDoesNotApplyPreviousPresentationTwice(){
        val m=GlobeMotion();m.setNormalPole(tilt.rotate(GlobeVector3.Z))
        val local=point(-13.9418,165.6471,89.57);m.focusLocalPoint(local,toward)
        var now=finish(m,0.0);val first=m.presentation
        repeat(5){m.focusLocalPoint(local,toward);now=finish(m,now);assertEquals(0.0,first.angleTo(m.presentation),1e-5)}
    }
    @Test fun selectionDuringReturnAndIdleUsesCurrentGeographicSpin(){
        val m=GlobeMotion();m.setNormalPole(tilt.rotate(GlobeVector3.Z))
        m.focusLocalPoint(point(39.9,116.4,0.0),toward);var now=finish(m,0.0)
        repeat(850){now+=.01;m.tick(.01,now)}
        val local=point(-33.9,151.2,m.idleDegrees)
        m.focusLocalPoint(local,toward);finish(m,now)
        assertEquals(1.0,m.presentation.rotate(local).dot(toward),1e-8)
    }
    @Test fun manualTakeoverStillCancelsFocus(){
        val m=GlobeMotion();m.focusLocalPoint(point(0.0,0.0,0.0),toward)
        m.tick(.01,.01);m.begin(.02);assertFalse(m.focusLocked);assertFalse(m.focusing)
    }
}
