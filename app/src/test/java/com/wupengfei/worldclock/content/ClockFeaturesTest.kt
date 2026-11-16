package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.*
import kotlin.math.*

class ClockFeaturesTest {
    private fun catalog():CityCatalog {
        val file=listOf(File("src/main/assets/cities/catalog.tsv"),File("app/src/main/assets/cities/catalog.tsv")).first{it.isFile}
        return CityCatalog.read(file.bufferedReader()).apply {
            val rules=CityTimeZones.read(File(file.parentFile,"timezones.tsv").bufferedReader())
            cities.forEach{it.timeZones=rules}
            bindPinPositions(File(file.parentFile,"pin_positions.tsv").bufferedReader())
        }
    }
    @Test fun solarAgreesWithIndependentAstronomyEngine1900Through2100(){
        var worst=0.0
        javaClass.classLoader!!.getResourceAsStream("solar_reference.csv")!!.bufferedReader().useLines{lines->lines.forEach{
            val f=it.split(',');val s=SolarPosition.at(Instant.parse(f[0]));val d=abs(s.declination-f[1].toDouble())
            val l=abs(SolarPosition.wrap(s.longitude-f[2].toDouble()));worst=max(worst,max(d,l))
            assertTrue(f[0]+" declination error "+d,d<0.03)
            assertTrue(f[0]+" longitude error "+l,l<0.03)
        }}
        println("Solar reference worst angular error degrees="+worst)
    }
    @Test fun seasonHemisphereAndNoonAreCorrect(){
        val summer=SolarPosition.at(Instant.parse("2026-06-21T12:00:00Z"))
        val winter=SolarPosition.at(Instant.parse("2026-12-21T12:00:00Z"))
        assertTrue(summer.declination>23.4);assertTrue(winter.declination< -23.4)
        assertTrue(summer.altitude(90.0,0.0)>0);assertTrue(winter.altitude(90.0,0.0)<0)
        assertEquals(90.0,summer.altitude(summer.declination,summer.longitude),1e-5)
        assertTrue(summer.altitude(-summer.declination,summer.longitude+180.0)< -89.99)
    }
    @Test fun utcMidnightDoesNotJumpSunDirection(){
        val a=SolarPosition.at(Instant.parse("2026-12-31T23:59:59Z"));val b=SolarPosition.at(Instant.parse("2027-01-01T00:00:00Z"))
        assertTrue(abs(SolarPosition.wrap(a.longitude-b.longitude))<0.01)
        assertTrue(abs(a.declination-b.declination)<0.001)
    }
    @Test fun offlineChineseEnglishCountriesAndAdministrativeFilter(){
        val c=catalog();assertTrue(c.cities.size>7000)
        assertEquals("1816670",c.search("北京").first().id)
        assertEquals("1816670",c.search("beijing").first().id)
        assertEquals("5128581",c.search("纽约").first().id)
        assertTrue(c.search("美国").all{it.countryCode=="US"})
        assertTrue(c.search("Japan").all{it.countryCode=="JP"})
        assertTrue(c.cities.none{it.feature in listOf("PPLX","PPLA3","PPLA4","PPLA5","PPLL")})
        assertTrue(c.cities.filter{it.countryCode=="CN"}.all{it.feature in listOf("PPLC","PPLA","PPLA2")})
        assertTrue(c.cities.all{it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0})
        assertTrue(c.search("美国").map{it.zoneName}.distinct().size>1)
    }
    @Test fun daylightSavingAndDateLineUseTheSameInstant(){
        val c=catalog();val ny=c.city("5128581")!!;val bj=c.city("1816670")!!
        assertEquals("01:59",ny.time(Instant.parse("2026-03-08T06:59:00Z")))
        assertEquals("03:00",ny.time(Instant.parse("2026-03-08T07:00:00Z")))
        assertEquals("UTC−05:00",ny.offset(Instant.parse("2026-01-01T00:00:00Z")))
        assertEquals("UTC−04:00",ny.offset(Instant.parse("2026-07-01T00:00:00Z")))
        val instant=Instant.parse("2026-12-31T18:00:00Z")
        assertEquals(2027,instant.atZone(bj.zoneAt(instant)).year);assertEquals(2026,instant.atZone(ny.zoneAt(instant)).year)
    }
    @Test fun focusCentersArbitraryCityAndHoldsThenPinchTakesOver(){
        val m=GlobeMotion();val city=GlobeGeography.direction(39.9042,116.4074)
        val toward=GlobeVector3(0.0,0.07,1.0).normalized()
        m.focusTo(GlobeQuaternion.align(city,toward));var now=0.0
        while(m.focusing&&now<10){now+=.01;m.tick(.01,now)}
        assertTrue(m.focusLocked);assertEquals(1.0,m.presentation.rotate(city).dot(toward),1e-8)
        assertEquals(0.0,m.idleDegrees,0.0)
        val still=m.presentation;repeat(100){now+=.01;m.tick(.01,now)};assertEquals(still,m.presentation)
        m.begin(now);assertFalse(m.focusLocked);m.end(now+.1,true);repeat(1000){m.tick(.01,now+.1+it*.01)}
        assertTrue(m.tiltErrorDegrees<0.0001);assertTrue(m.idleDegrees>0)
    }
    @Test fun cityFocusPreservesNorthOnScreenAcrossHemispheres(){
        val toward=GlobeVector3(0.0,0.07,1.0).normalized()
        for(lat in listOf(-80.0,-33.9,0.0,39.9,80.0))for(lon in listOf(-180.0,-74.0,0.0,116.4,179.0)){
            val m=GlobeMotion();m.setNormalPole(GlobeVector3.Z)
            val city=GlobeGeography.direction(lat,lon)
            m.focusCity(city,toward);var now=0.0
            while(m.focusing&&now<10){now+=.01;m.tick(.01,now)}
            assertEquals(1.0,m.presentation.rotate(city).dot(toward),1e-8)
            val north=m.presentation.rotate(m.normalPole)
            val projected=(north-toward*north.dot(toward)).normalized()
            val expected=(m.normalPole-toward*m.normalPole.dot(toward)).normalized()
            assertEquals(1.0,projected.dot(expected),1e-8)
        }
    }
    @Test fun bundledZonesCoverNewCoyhaiqueAndFutureDst(){
        val c=catalog();val q=c.cities.first{it.zoneName=="America/Coyhaique"}
        assertEquals("UTC−03:00",q.offset(Instant.parse("2026-07-01T00:00:00Z")))
        val ny=c.city("5128581")!!
        assertEquals("UTC−04:00",ny.offset(Instant.parse("2100-07-01T00:00:00Z")))
        assertTrue(ny.isDaylightSaving(Instant.parse("2100-07-01T00:00:00Z")))
    }
    @Test fun previewFreezesPresentationSpinButDoesNotBreakTiltReturn(){
        val m=GlobeMotion();m.begin(0.0);m.drag(300.0,300.0,.05);m.end(.1,true)
        repeat(500){m.tick(.01,.1+it*.01,autoSpin=false)}
        assertTrue(m.restoringTilt) // 3s dwell + 2s into the slower 4s movement.
        repeat(300){m.tick(.01,5.1+it*.01,autoSpin=false)}
        assertEquals(0.0,m.idleDegrees,0.0);assertTrue(m.tiltErrorDegrees<0.0001)
        assertFalse(m.restoringTilt)
    }
    @Test fun previewDateValidationAndLiveRecovery(){
        val state=ClockState();state.now=Instant.parse("2026-10-21T12:00:00Z")
        state.setOffset(60f);assertEquals(Instant.parse("2026-10-21T13:00:00Z"),state.instant)
        state.now=state.now.plusSeconds(60);assertEquals(Instant.parse("2026-10-21T13:00:00Z"),state.instant)
        assertFalse(state.setDate("2026-02-30"));assertTrue(state.setDate("2028-02-29"))
        state.live();assertFalse(state.preview);assertEquals(0f,state.offsetMinutes,0f)
    }
}
