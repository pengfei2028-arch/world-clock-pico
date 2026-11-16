package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class QuakeCoverageTest {
    private val end=Instant.parse("2026-10-25T00:00:00Z")
    private fun event(i:Int,lat:Double=0.0,lon:Double=i*30.0)=QuakeEvent("e$i",setOf("e$i"),"Fixture",end.minusSeconds(i*3600L),end,lat,lon,12.0,5.5,"mw","")
    @Test fun allHemispheresRemainRepresentedAcrossRotationsTiltsAndScales(){
        val events=(0..11).map{event(it)}+listOf(event(12,89.9,179.9),event(13,-89.9,-179.9))
        val groups=QuakeCoverage.group(events)
        assertEquals(events.map{it.id}.toSet(),groups.flatMap{it.events}.map{it.id}.toSet())
        assertEquals(events.size,groups.sumOf{it.events.size})
        // The exact same native point geometry contains both halves; no camera filter.
        val g=QuakeDecalGeometry().apply{groups.forEachIndexed{i,c->patch(GlobeGeography.direction(c.representative.latitude,c.representative.longitude)*2.024,.01,false,0,.1,-1.0,i+1)}}
        for(scale in listOf(.3,1.0,3.0))for(tilt in listOf(0.0,.4,1.3))for(turn in 0..12){
            val q=GlobeQuaternion.axisAngle(GlobeVector3.Y,turn*Math.PI/6)*GlobeQuaternion.axisAngle(GlobeVector3.X,tilt)
            val rotated=g.positions.map{q.rotate(it)*scale}
            assertTrue(rotated.any{it.z>0});assertTrue(rotated.any{it.z<0})
            assertEquals(groups.size,g.meta.map{it.second}.distinct().size)
        }
    }
    @Test fun realCatalogGlobalCoverageIsCompleteAfterEachPeriodOrMagnitudeChange(){
        val raw=javaClass.classLoader!!.getResourceAsStream("usgs_quake_week_fixture.geojson")!!.bufferedReader().readText()
        val catalog=QuakeJson.parse(raw,QuakePeriod.WEEK,end)
        for(period in QuakePeriod.entries)for(minimum in listOf(4.0,5.0,7.0)){
            val events=catalog.copy(period=period).filtered(minimum)
            val groups=QuakeCoverage.group(events)
            val ids=groups.flatMap{it.events}.map{it.id}
            assertEquals(events.map{it.id}.toSet(),ids.toSet());assertEquals(events.size,ids.size)
            assertTrue(groups.count{it.events.size>1}<=QuakeStyle.MAX_CLUSTER_LABELS)
            assertTrue(groups.all{it.representative in it.events})
        }
    }
    @Test fun datelineAndPolesUseAngularNeighborsNotFlatUvDistance(){
        val events=listOf(event(0,lon=179.0),event(1,lon=-179.0),event(2,89.8,0.0),event(3,89.8,180.0))
        val groups=QuakeCoverage.group(events)
        assertEquals(2,groups.size);assertTrue(groups.all{it.events.size==2})
    }
    @Test fun selectionSeparatesAnEventWithoutDroppingClusterMembers(){
        val events=listOf(event(0,lon=1.0),event(1,lon=2.0),event(2,lon=3.0))
        val groups=QuakeCoverage.group(events,selected="e1")
        assertEquals(3,groups.sumOf{it.events.size})
        assertEquals(1,groups.single{it.representative.id=="e1"}.events.size)
    }
    @Test fun replayRewindAndEmptyFilterNeverReuseOppositeHemisphereStaleEvents(){
        val events=(0..11).map{event(it)}
        val first=QuakeCoverage.group(events);val prior=first.flatMap{g->g.events.map{it.id to g.representative.id}}.toMap()
        val visible=QuakeRules.visible(events,end.minusSeconds(5*3600L))
        assertEquals(visible.map{it.id}.toSet(),QuakeCoverage.group(visible,prior).flatMap{it.events}.map{it.id}.toSet())
        assertTrue(QuakeCoverage.group(emptyList(),prior).isEmpty())
    }
}
