package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant
import java.io.File
import kotlin.math.*

class EarthquakeTest {
    private val end=Instant.parse("2026-10-25T00:00:00Z")
    private fun event(id:String="a",time:Instant=end.minusSeconds(100),mag:Double?=5.2,lat:Double=35.0,lon:Double=140.0)=
        QuakeEvent(id,setOf(id),"Test fixture",time,time.plusSeconds(2),lat,lon,24.0,mag,"mw","")
    private fun catalog(events:List<QuakeEvent>,period:QuakePeriod=QuakePeriod.WEEK)=QuakeCatalog(period,end,end.plusSeconds(5),events)
    private fun feature(id:String="a",status:String="reviewed",updated:Long=end.toEpochMilli())=JSONObject()
        .put("type","Feature").put("id",id)
        .put("properties",JSONObject().put("type","earthquake").put("status",status).put("time",end.minusSeconds(10).toEpochMilli())
            .put("updated",updated).put("mag",5.2).put("magType","mw").put("place","Test fixture").put("ids",",$id,"))
        .put("geometry",JSONObject().put("type","Point").put("coordinates",JSONArray().put(140.0).put(-35.0).put(24.0)))
    private fun json(vararg features:JSONObject)=JSONObject().put("type","FeatureCollection")
        .put("metadata",JSONObject().put("generated",end.toEpochMilli()).put("status",200).put("count",features.size))
        .put("features",JSONArray(features.toList())).toString()

    @Test fun realUsGsSnapshotUsesLonLatDepthAndDistinctTimestamps(){
        val raw=javaClass.classLoader!!.getResourceAsStream("usgs_quake_week_fixture.geojson")!!.bufferedReader().readText()
        val q=QuakeJson.parse(raw,QuakePeriod.WEEK,end)
        assertFalse(q.partial);assertEquals(406,q.events.size);assertEquals(240,q.filtered(4.0).size)
        assertEquals(q.events.size,q.events.map{it.id}.distinct().size)
        val source=JSONObject(raw).getJSONArray("features").getJSONObject(0)
        val expected=q.events.single{it.id==source.getString("id")}
        val xyz=source.getJSONObject("geometry").getJSONArray("coordinates");val props=source.getJSONObject("properties")
        assertEquals(xyz.getDouble(0),expected.longitude,1e-9);assertEquals(xyz.getDouble(1),expected.latitude,1e-9)
        assertEquals(xyz.getDouble(2),expected.depthKm!!,1e-9)
        assertEquals(props.getLong("time"),expected.occurredAt.toEpochMilli())
        assertEquals(props.getLong("updated"),expected.updatedAt!!.toEpochMilli())
        assertEquals(end,q.fetchedAt)
    }
    @Test fun missingOptionalFieldsRemainMissingAndNullMagnitudeDoesNotPassFilter(){
        val f=feature();val p=f.getJSONObject("properties")
        for(k in listOf("mag","magType","place","updated"))p.put(k,JSONObject.NULL)
        f.getJSONObject("geometry").getJSONArray("coordinates").put(2,JSONObject.NULL)
        val q=QuakeJson.parse(json(f),QuakePeriod.WEEK,end)
        assertFalse(q.partial);val e=q.events.single()
        assertNull(e.magnitude);assertNull(e.depthKm);assertNull(e.updatedAt);assertNull(e.magnitudeType)
        assertEquals("暂无",e.magnitudeText);assertTrue(q.filtered(4.0).isEmpty())
    }
    @Test fun badCoordinatesAndTruncatedCountNeverPretendComplete(){
        val f=feature();f.getJSONObject("geometry").getJSONArray("coordinates").put(1,95)
        val q=QuakeJson.parse(json(f),QuakePeriod.WEEK,end);assertTrue(q.partial);assertEquals(1,q.skipped)
        val truncated=JSONObject(json(feature())).also{it.getJSONObject("metadata").put("count",2)}
        assertTrue(QuakeJson.parse(truncated.toString(),QuakePeriod.WEEK,end).partial)
    }
    @Test fun explicitDeletionWinsOverOlderCopyWithoutGeometry(){
        val deleted=feature(status="deleted",updated=end.toEpochMilli()+1000).put("geometry",JSONObject.NULL)
        val q=QuakeJson.parse(json(feature(),deleted),QuakePeriod.WEEK,end)
        assertFalse(q.partial);assertTrue(q.events.isEmpty())
    }
    @Test fun olderDeletionDoesNotRemoveNewRevision(){
        val q=QuakeJson.parse(json(feature(),feature(status="deleted",updated=end.toEpochMilli()-1000)),QuakePeriod.WEEK,end)
        assertEquals(1,q.events.size)
    }
    @Test fun aliasChainsMergeAndNewestRevisionWins(){
        val a=event("a").copy(aliases=setOf("a","x"))
        val b=event("b").copy(aliases=setOf("b","x","y"),updatedAt=end)
        val c=event("c").copy(aliases=setOf("c","y"),updatedAt=end.plusSeconds(1),latitude=-30.0,magnitude=6.0)
        assertEquals(c,QuakeRules.deduplicate(listOf(c,a,b)).single())
    }
    @Test fun filtersIncludeExactWindowEdgesAndMagnitudeBoundary(){
        val start=end.minusSeconds(7*86400)
        val c=catalog(listOf(event("start",start,4.0),event("end",end,5.0),event("old",start.minusMillis(1),8.0),event("future",end.plusMillis(1),8.0),event("small",mag=3.9)))
        assertEquals(listOf("start","end"),c.filtered(4.0).map{it.id})
        assertEquals(listOf("end"),c.filtered(5.0).map{it.id})
    }
    @Test fun replayNeverInventsEventsPauseAndScrubAreExact(){
        val q=EarthquakeState();val c=catalog(listOf(event("a",end.minusSeconds(6*86400)),event("b",end.minusSeconds(86400))))
        q.accept(c);q.togglePlayback();assertTrue(q.visible.isEmpty())
        q.scrub(.5);assertFalse(q.playing);assertEquals(listOf("a"),q.visible.map{it.id})
        val time=q.replayTime;q.advance(.05);assertEquals(time,q.replayTime)
        q.scrub(0.0);assertTrue(q.visible.isEmpty());q.scrub(1.0);assertEquals(2,q.visible.size)
        q.togglePlayback();repeat(1200){q.advance(.05)};assertEquals(c.end,q.replayTime);assertFalse(q.playing)
    }
    @Test fun newSnapshotIsDeferredWhileScrubbingAndReplacesRemovedRecordsOnOverview(){
        val q=EarthquakeState();q.accept(catalog(listOf(event("a"))));q.scrub(.8)
        val snapshot=catalog(listOf(event("b"))).copy(generatedAt=end.plusSeconds(60))
        q.accept(snapshot);assertEquals(end,q.catalog!!.end);assertEquals("a",q.filtered.single().id)
        q.overview();assertEquals(snapshot,q.catalog);assertEquals("b",q.filtered.single().id);assertNull(q.pending)
    }
    @Test fun revisionUpdatesSelectedLocationAndRemovalClearsSelection(){
        val q=EarthquakeState();val old=event();q.accept(catalog(listOf(old)));q.select(old)
        val newer=old.copy(latitude=-33.0,magnitude=5.8);q.accept(catalog(listOf(newer)))
        assertEquals(-33.0,q.selected!!.latitude,0.0)
        q.accept(catalog(emptyList()));assertNull(q.selected);assertTrue(q.status.contains("撤回"))
    }
    @Test fun reassignedIdRetainsSelectedEventThroughAliases(){
        val q=EarthquakeState();val old=event();q.accept(catalog(listOf(old)));q.select(old)
        val revised=old.copy(id="us-new",aliases=setOf("a","us-new"));q.accept(catalog(listOf(revised)))
        assertEquals("us-new",q.selectedId)
    }
    @Test fun switchingPeriodCannotExposeOldWindowAndIgnoresLateResponse(){
        val q=EarthquakeState();q.accept(catalog(listOf(event())));q.choosePeriod(QuakePeriod.MONTH)
        assertNull(q.catalog);assertTrue(q.filtered.isEmpty())
        q.accept(catalog(listOf(event())));assertNull(q.catalog)
        q.accept(catalog(listOf(event()),QuakePeriod.MONTH));assertEquals(1,q.filtered.size)
    }
    @Test fun selectingAnEventPausesPlaybackAndFutureSelectionClearsOnScrub(){
        val q=EarthquakeState();val e=event();q.accept(catalog(listOf(e)));q.scrub(1.0);q.select(e)
        assertFalse(q.playing);q.scrub(0.0);assertNull(q.selected)
    }
    @Test fun moduleRoundTripPreservesWorldTimeAndTemperatureStates(){
        val clock=ClockState();clock.now=end;clock.previewBase=end.minusSeconds(86400);clock.offsetMinutes=90f
        val oldTime=clock.clockInstant;clock.temperature.index=9;clock.temperature.strength=.7f;clock.temperature.autoNow=false
        clock.switchMode(EarthMode.TEMPERATURE);clock.switchMode(EarthMode.EARTHQUAKE)
        assertEquals(EarthMode.TEMPERATURE,clock.earthquakes.returnMode)
        clock.earthquakes.accept(catalog(listOf(event())));clock.beginTimeDrag();clock.dragTimeBy(120f);clock.finishTimeDrag()
        clock.switchMode(clock.earthquakes.returnMode)
        assertEquals(oldTime,clock.clockInstant);assertEquals(9,clock.temperature.index);assertEquals(.7f,clock.temperature.strength,0f)
        assertFalse(clock.earthquakes.playing)
    }
    @Test fun clustersNeverMoveEpicentersAndHysteresisPreventsBoundaryFlicker(){
        val a=event("a",mag=6.0);val b=event("b",mag=5.0)
        val first=QuakeClustering.group(listOf(QuakeProjection(a,0.0,0.0),QuakeProjection(b,23.0,0.0))).single()
        assertEquals(a,first.representative);assertEquals(2,first.events.size)
        val moved=listOf(QuakeProjection(a,0.0,0.0),QuakeProjection(b,28.0,0.0))
        assertEquals(2,QuakeClustering.group(moved).size)
        assertEquals(1,QuakeClustering.group(moved,mapOf("b" to "a")).size)
        assertEquals(2,QuakeClustering.group(listOf(moved[0],QuakeProjection(b,33.0,0.0)),mapOf("b" to "a")).size)
    }
    @Test fun sphericalWavesFollowActualUvSurfaceAcrossDatelineAndPoles(){
        val path=listOf(File("src/main/assets/coordinates/earth_surface.bin"),File("app/src/main/assets/coordinates/earth_surface.bin")).first{it.isFile}
        val mapping=CoordinateSurface.read(path.inputStream())
        for(lat in listOf(-90.0,-33.9,0.0,39.9,90.0))for(lon in listOf(-180.0,-74.0,0.0,116.4,179.99)){
            val epicenter=mapping.point(lat,lon)*QuakeStyle.RADIUS_SCALE
            val patch=QuakeDecalGeometry().apply{patch(epicenter,Math.toRadians(6.5),true,0,.1,-1.0,1)}
            patch.positions.forEachIndexed{i,p->
                assertEquals(sqrt(epicenter.dot(epicenter)),sqrt(p.dot(p)),1e-9)
                val uv=patch.uv[i]
                val separation=acos(epicenter.normalized().dot(p.normalized()).coerceIn(-1.0,1.0))*180/PI
                assertEquals(hypot(uv.first,uv.second)*6.5,separation,1e-5)
            }
        }
    }
    @Test fun loopHasNoHardCutAndThreeRingsAreStaggered(){
        assertEquals(0.0,QuakeRules.ringOpacity(0.0),0.0);assertEquals(0.0,QuakeRules.ringOpacity(1.0),0.0)
        assertTrue(QuakeRules.ringOpacity(.999)<.00001)
        for(i in 0..300){val s=i*.03
            val a=(0..2).map{QuakeRules.ringPhase(s,it,0.0)}
            assertEquals(3,a.distinct().size);assertTrue(a.all{it>=0&&it<1})
            assertTrue(a.sumOf{QuakeRules.ringOpacity(it)}>.05)
        }
    }
}
