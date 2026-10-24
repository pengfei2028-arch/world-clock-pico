package com.wupengfei.worldclock.content

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class CoordinateTest {
    companion object {
        private val surface by lazy{CoordinateSurface.read(File("src/main/assets/coordinates/earth_surface.bin").inputStream())}
        private val catalog by lazy{CityCatalog.read(File("src/main/assets/cities/catalog.tsv").bufferedReader()).apply{
            bindPinPositions(File("src/main/assets/cities/pin_positions.tsv").bufferedReader())
        }}
    }
    @Test fun uvRoundTripAtEquatorPrimeSeamAndPoles(){
        for(lat in listOf(-60.0,-30.0,0.0,30.0,60.0))for(lon in listOf(-170.0,-90.0,-.001,0.0,.001,90.0,170.0)){
            val actual=checkNotNull(surface.coordinate(surface.point(lat,lon)))
            assertEquals("latitude $lat $lon",lat,actual.latitude,.0002)
            assertEquals("longitude $lat $lon",0.0,SolarPosition.wrap(checkNotNull(actual.longitude)-lon),.0002)
        }
        for(lat in listOf(-90.0,90.0)){
            val actual=checkNotNull(surface.coordinate(surface.point(lat,0.0)))
            assertEquals(lat,actual.latitude,.0002);assertNull(actual.longitude)
        }
        assertNull(surface.coordinate(GlobeVector3.ZERO))
        assertNull(surface.coordinate(GlobeVector3(Double.NaN,0.0,0.0)))
    }
    @Test fun actualMeshHitsStayFiniteAcrossDatelineAndNearPolarUvFans(){
        for(lat in listOf(-89.99,-89.9,-60.0,0.0,60.0,89.9,89.99)){
            val west=checkNotNull(surface.coordinate(GlobeGeography.direction(lat,179.999)))
            val east=checkNotNull(surface.coordinate(GlobeGeography.direction(lat,-179.999)))
            for(c in listOf(west,east)){
                assertTrue(c.latitude.isFinite()&&c.latitude in -90.0..90.0)
                assertTrue(c.longitude==null||c.longitude!!.isFinite()&&c.longitude!! in -180.0..180.0)
                // Invert an actual UV hit, not a nonexistent point in a UV chart gap.
                val again=checkNotNull(surface.coordinate(surface.point(c.latitude,c.longitude?:0.0)))
                assertEquals(c.latitude,again.latitude,.001)
            }
            if(abs(lat)<80)assertTrue(abs(SolarPosition.wrap(east.longitude!!-west.longitude!!))<2.0)
        }
    }
    @Test fun actualCityPinsAgreeWithMeshUvIncludingFourHemispheres(){
        for(id in listOf("1816670","2643743","2147714","5128581","3448439")){
            val city=checkNotNull(catalog.city(id))
            val result=checkNotNull(surface.coordinate(city.direction))
            assertEquals(city.name,city.latitude,result.latitude,.001)
            assertEquals(city.name,city.longitude,checkNotNull(result.longitude),.001)
            assertTrue(surface.point(city.latitude,city.longitude).normalized().dot(city.direction)>.999999)
        }
    }
    @Test fun nestedRotationsScaleAndTranslationCannotChangeCoordinates(){
        val lat=39.9075;val lon=116.39723;val point=surface.point(lat,lon)
        for(scale in listOf(.045,.125,.2,12.0))for(angle in listOf(-179.9,-90.0,0.0,47.0,180.0)){
            val parent=GlobeQuaternion.axisAngle(GlobeVector3.Y,angle)
            val child=GlobeQuaternion.axisAngle(GlobeVector3.X,-90.0)*GlobeQuaternion.axisAngle(GlobeVector3.Y,23.44)*GlobeQuaternion.axisAngle(GlobeVector3.Z,angle*2)
            val offset=GlobeVector3(2.3,-4.2,-60.0)
            val world=parent.rotate(child.rotate(point)*scale)+offset
            val local=child.inverse().rotate(parent.inverse().rotate(world-offset)*(1/scale))
            val actual=checkNotNull(surface.coordinate(local))
            assertEquals(lat,actual.latitude,.0002);assertEquals(lon,checkNotNull(actual.longitude),.0002)
        }
    }
    @Test fun formattedZeroAndPoleAreUnambiguous(){
        assertEquals("0.00° N",GeoCoordinate(-.0001,0.0).latitudeText())
        assertEquals("0.00° E",GeoCoordinate(0.0,-.0001).longitudeText())
        assertEquals("33.87° S",GeoCoordinate(-33.8688,151.2093).latitudeText())
        assertEquals("74.01° W",GeoCoordinate(40.7128,-74.006).longitudeText())
        assertEquals("经度不唯一 · 极点",GeoCoordinate(90.0,null).longitudeText())
    }
    @Test fun tapNeverLeaksFromDragTrackingGapLongHoldOrOtherPointer(){
        val tap=CoordinateTap();val value=GeoCoordinate(0.0,0.0)
        tap.begin(1,value,0.0);assertEquals(value,tap.end(1,.2))
        tap.begin(1,value,0.0);tap.observe(true,false);assertNull(tap.end(1,.2))
        tap.begin(1,value,0.0);tap.observe(false,true);assertNull(tap.end(1,.2))
        tap.begin(1,value,0.0);assertNull(tap.end(1,2.0))
        tap.begin(1,value,0.0);assertNull(tap.end(2,.2))
        tap.begin(1,value,0.0);tap.cancel();assertNull(tap.end(1,.2))
        tap.begin(1,value,0.0);tap.position(GlobeVector3.ZERO);tap.position(GlobeVector3(.03,0.0,0.0));assertNull(tap.end(1,.2))
    }
    @Test fun selectingHoldsPresentationButNotEffectTime(){
        val motion=GlobeMotion();motion.readCoordinates(1.0)
        repeat(420){motion.tick(1.0/60,1.0+it/60.0)}
        assertEquals(0.0,motion.idleDegrees,1e-10);assertEquals(7.0,motion.effectSeconds,1e-8)
        repeat(120){motion.tick(1.0/60,9.0+it/60.0)}
        assertTrue(motion.idleDegrees>5)
    }
    @Test fun gridAndTicksAreFiniteBatchedGeometryOutsideTemperatureShell(){
        val minor=CoordinateGeometry.grid(surface)
        val equator=CoordinateGeometry.reference(surface,true);val prime=CoordinateGeometry.reference(surface,false)
        val arc=CoordinateGeometry.rulers(surface)
        for(mesh in listOf(minor,equator,prime,arc,CoordinateGeometry.ring())){
            assertTrue(mesh.positions.size<40000);assertEquals(0,mesh.indices.size%3)
            assertTrue(mesh.indices.all{it in mesh.positions.indices})
            assertTrue(mesh.positions.all{it.x.isFinite()&&it.y.isFinite()&&it.z.isFinite()})
            for(i in mesh.indices.indices step 3){val a=mesh.positions[mesh.indices[i]];val b=mesh.positions[mesh.indices[i+1]];val c=mesh.positions[mesh.indices[i+2]]
                assertTrue("degenerate line triangle",(b-a).cross(c-a).let{it.dot(it)}>1e-18)
            }
        }
        assertTrue((minor.positions+equator.positions+prime.positions).all{sqrt(it.dot(it))>2*1.003})
    }
    @Test fun layerToggleIsIndependentOfWorldTimeAndTemperature(){
        val state=ClockState();val time=state.instant
        state.toggleCoordinates();state.coordinates.select(GeoCoordinate(40.0,-74.0))
        state.switchMode(EarthMode.TEMPERATURE)
        assertTrue(state.coordinates.enabled);assertEquals(-74.0,state.coordinates.selection!!.coordinate.longitude!!,0.0)
        state.switchMode(EarthMode.WORLD_TIME);assertEquals(time,state.instant)
        repeat(100){state.toggleCoordinates();state.coordinates.finishHidden();state.toggleCoordinates()}
        state.toggleCoordinates();state.coordinates.finishHidden()
        assertNull(state.coordinates.selection);assertFalse(state.coordinates.enabled)
    }
    @Test fun meridiansHaveNoPolarStitchJumpsAndMeetTheAuthoredUvPoles(){
        assertTrue(hypot(surface.northPole.x,surface.northPole.y)>.001)
        for(lon in -180 until 180 step 30){
            val path=surface.longitudeLine(lon.toDouble())
            assertEquals(181,path.size)
            assertTrue((path.first()-surface.southPole).let{it.dot(it)}<1e-16)
            assertTrue((path.last()-surface.northPole).let{it.dot(it)}<1e-16)
            for(i in 1..path.lastIndex){
                val angle=Math.toDegrees(acos(path[i-1].normalized().dot(path[i].normalized()).coerceIn(-1.0,1.0)))
                assertTrue("longitude $lon sample $i jumps by $angle degrees",angle<2.8)
            }
            for(cap in listOf(path.take(16),path.takeLast(16))){
                val plane=cap.first().cross(cap.last()).normalized()
                assertTrue(cap.all{abs(it.normalized().dot(plane))<1e-8})
            }
            val equator=checkNotNull(surface.coordinate(path[90]))
            assertEquals(0.0,equator.latitude,.001)
        }
    }
    @Test fun roundLineKeepsItsWidthAtEveryViewingAzimuth(){
        val width=CoordinateStyle.GRID_WIDTH
        val line=CoordinateGeometry().tube(listOf(GlobeVector3(2.0,0.0,-.2),GlobeVector3(2.0,0.0,.2)),width)
        val section=line.positions.take(8)
        for(degree in 0 until 360 step 5){val radians=Math.toRadians(degree.toDouble())
            val projected=section.map{it.x*cos(radians)+it.y*sin(radians)}
            assertTrue(projected.maxOrNull()!!-projected.minOrNull()!!>width*.92)
        }
        for(i in line.indices.indices step 3){
            val a=line.positions[line.indices[i]];val b=line.positions[line.indices[i+1]];val c=line.positions[line.indices[i+2]]
            val center=(a+b+c)*(1.0/3);val normal=(b-a).cross(c-a)
            val outward=if(abs(center.z)<.199999)GlobeVector3(center.x-2,center.y,0.0) else GlobeVector3(0.0,0.0,center.z)
            assertTrue("tube back-face winding",normal.dot(outward)>0)
        }
    }
}
