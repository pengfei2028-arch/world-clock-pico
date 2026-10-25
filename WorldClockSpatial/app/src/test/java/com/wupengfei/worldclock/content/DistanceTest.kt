package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.*

class DistanceTest {
    companion object {
        private val catalog by lazy{CityCatalog.read(File("src/main/assets/cities/catalog.tsv").bufferedReader())}
        private val surface by lazy{CoordinateSurface.read(File("src/main/assets/coordinates/earth_surface.bin").inputStream())}
        private val beijing get()=checkNotNull(catalog.city("1816670"))
        private val sydney get()=checkNotNull(catalog.city("2147714"))
        private val london get()=checkNotNull(catalog.city("2643743"))
    }
    @Test fun knownCityDistanceAndSymmetry(){
        assertEquals(8948.764,GreatCircle.km(beijing,sydney),.02)
        assertEquals(GreatCircle.km(beijing,sydney),GreatCircle.km(sydney,beijing),1e-9)
        assertEquals(0.0,GreatCircle.km(beijing,beijing),1e-9)
    }
    @Test fun equatorDateLineAndPolesUseShortestSurfacePath(){
        val r=DistanceStyle.RADIUS_KM
        assertEquals(r*PI/2,GreatCircle.km(0.0,0.0,0.0,90.0),1e-8)
        assertEquals(r*Math.toRadians(2.0),GreatCircle.km(0.0,179.0,0.0,-179.0),1e-8)
        assertEquals(0.0,GreatCircle.km(90.0,23.0,90.0,-141.0),1e-8)
        assertEquals(r*PI,GreatCircle.km(90.0,0.0,-90.0,15.0),1e-8)
    }
    @Test fun exactAndNearAntipodesHaveFiniteContinuousBoundedArcs(){
        for(lon in listOf(180.0,179.999999,179.0,0.0)){
            val a=GreatCircle.unit(0.0,0.0);val b=GreatCircle.unit(0.0,lon)
            val path=(0..360).map{GreatCircle.interpolate(a,b,it/360.0)}
            assertEquals(a,path.first());assertEquals(b,path.last())
            assertTrue(path.all{it.x.isFinite()&&it.y.isFinite()&&it.z.isFinite()&&abs(it.dot(it)-1)<1e-10})
            val sum=path.zipWithNext().sumOf{GreatCircle.angle(it.first,it.second)}
            assertEquals(GreatCircle.angle(a,b),sum,1e-8)
            assertTrue(path.zipWithNext().all{GreatCircle.angle(it.first,it.second)<.02})
        }
    }
    @Test fun meshArcAnchorsUseTheOriginalAssetAndStayAboveClouds(){
        val path=DistancePath.create(surface,beijing,sydney)
        val expected=surface.point(beijing.latitude,beijing.longitude).normalized()
        assertTrue(path.samples.first().normalized().dot(expected)>1-1e-12)
        assertTrue(path.samples.last().normalized().dot(surface.point(sydney.latitude,sydney.longitude).normalized())>1-1e-12)
        assertTrue(path.samples.all{sqrt(it.dot(it))>=2*DistanceStyle.SURFACE_SCALE-1e-10})
        val g=path.geometry();assertTrue(g.indices.all{it in g.positions.indices});assertTrue(g.indices.size<20000)
        assertTrue(g.positions.all{it.x.isFinite()&&it.y.isFinite()&&it.z.isFinite()})
    }
    @Test fun presentationRotationTiltScaleAndTranslationDoNotChangeKmOrAnchors(){
        val point=surface.point(beijing.latitude,beijing.longitude)
        for(scale in listOf(.2,12.0,17.0))for(deg in listOf(-180.0,0.0,70.0,360.0)){
            val rotation=GlobeQuaternion.axisAngle(GlobeVector3.Y,deg)*GlobeQuaternion.axisAngle(GlobeVector3.X,23.44)
            val frame=QuakeSurfaceFrame.trs(GlobeVector3(3.0,2.0,-60.0),rotation,GlobeVector3(scale,scale,scale))
            val coord=checkNotNull(surface.coordinate(frame.inverse().point(frame.point(point))))
            assertEquals(GreatCircle.km(beijing,sydney),GreatCircle.km(coord.latitude,coord.longitude!!,sydney.latitude,sydney.longitude),.001)
        }
    }
    @Test fun exactlyTwoCitiesAutoConnectButManyWaitForSequentialPicks(){
        val s=DistanceState();s.toggle(listOf(beijing,sydney));assertEquals(beijing to sydney,s.pair(listOf(beijing,sydney)))
        s.disable();s.toggle(listOf(beijing,sydney,london));assertNull(s.firstId)
        s.pick(beijing);s.pick(beijing);assertNull(s.secondId)
        s.pick(sydney);assertEquals(beijing.id,s.firstId);assertEquals(sydney.id,s.secondId)
        s.pick(london);assertEquals(london.id,s.firstId);assertNull(s.secondId)
    }
    @Test fun removalReconcilesAndModeExitClearsSelection(){
        val s=DistanceState();s.toggle(listOf(beijing,sydney));s.reconcile(listOf(beijing));assertNull(s.secondId)
        s.reconcile(emptyList());assertNull(s.firstId);s.disable();assertFalse(s.enabled)
    }
    @Test fun distanceClicksDoNotTriggerCityFocusAndDraggingRetainsMeasurement(){
        val c=ClockState();c.cities=listOf(beijing,sydney,london);c.citiesOpen=true;c.toggleDistance()
        assertFalse(c.citiesOpen);assertFalse(c.timeToolsOpen);val focus=c.focusRequest
        c.focus(beijing);c.focus(sydney);assertEquals(focus,c.focusRequest);assertNull(c.selectedId)
        c.manualInteraction();assertEquals(beijing to sydney,c.distance.pair(c.cities))
        c.switchMode(EarthMode.EARTHQUAKE);assertFalse(c.distance.enabled)
    }
    @Test fun surfaceTapRequiresCityProximity(){
        assertEquals(beijing,GreatCircle.nearest(listOf(beijing,sydney),GeoCoordinate(beijing.latitude,beijing.longitude)))
        assertNull(GreatCircle.nearest(listOf(beijing,sydney),GeoCoordinate(0.0,0.0)))
    }
    @Test fun repeatedToggleAndReplacementRetainOnlyOnePair(){
        val s=DistanceState();repeat(1000){s.toggle(listOf(beijing,sydney));s.pick(london);s.pick(beijing)
            assertEquals(london to beijing,s.pair(listOf(beijing,sydney,london)));s.disable();assertNull(s.firstId);assertNull(s.secondId)}
    }
}
