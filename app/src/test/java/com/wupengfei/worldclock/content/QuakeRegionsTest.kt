package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class QuakeRegionsTest {
    private val now=Instant.parse("2026-10-25T08:00:00Z")
    private fun event(id:String,time:Instant=now.minusSeconds(100),mag:Double?=5.2)=
        QuakeEvent(id,setOf(id),"94 km WSW of Gorontalo, Indonesia",time,time,35.6,140.9,24.0,mag,"mw","")
    private fun catalog(vararg events:QuakeEvent)=QuakeCatalog(QuakePeriod.WEEK,now,now,events.toList())

    @Test fun reportedRedPointsMatchOfficialRecordsAndSortAheadOfRecentSmallerEvent(){
        // Exact four-record subset from the device's USGS response fetched 2026-10-25T10:49:57.059Z.
        // All three M7 record IDs were independently fetched from the official event endpoint.
        // Test resources are not app assets and must not enter the installed catalog.
        val raw=javaClass.classLoader!!.getResourceAsStream("usgs_v90_reported_red_events.geojson")!!.bufferedReader().use{it.readText()}
        val q=EarthquakeState();val fetched=Instant.parse("2026-10-25T10:49:57.059Z")
        q.accept(QuakeJson.parse(raw,QuakePeriod.WEEK,fetched).copy(windowEnd=Instant.parse("2026-10-25T10:49:00Z")))
        assertEquals(4,q.listed.size)
        assertEquals(setOf("us6000u43g","pt26297050","pt26298050"),q.listed.take(3).map{it.id}.toSet())
        q.listed.take(3).forEach{assertEquals(7.0,it.magnitude!!,0.0);assertTrue(it.occurredAt<fetched.minusSeconds(86400))}
        assertEquals(q.visible.toSet(),q.listed.toSet())
        q.listed.forEach{assertTrue(q.selectListed(it.id));assertEquals(it,q.selected)}
        q.chooseSort(QuakeSort.TIME);assertEquals(6.5,q.listed.first().magnitude!!,0.0)
    }

    @Test fun listUsesTheWholeDisplayedPeriodIncludingOlderRedEvents(){
        val c=catalog(event("exact-five",mag=5.0),event("missing",mag=null),event("edge",now.minusSeconds(86400)),
            event("older-red",now.minusSeconds(3*86400),7.0),event("future",now.plusMillis(1)),event("recent"),event("below-four",mag=3.9))
        val q=EarthquakeState();q.accept(c)
        assertEquals(QuakeSort.MAGNITUDE,q.sortOrder)
        assertEquals(listOf("older-red","recent","edge","exact-five"),q.listed.map{it.id})
        assertEquals(q.visible.map{it.id}.toSet(),q.listed.map{it.id}.toSet())
    }
    @Test fun listSharesMapMinimumAndReplayCursorAcrossBothHemispheres(){
        val q=EarthquakeState();q.accept(catalog(event("m5"),event("m7",mag=7.1),event("week",now.minusSeconds(3*86400))))
        q.chooseMinimum(7.0);q.scrub(0.0)
        assertTrue(q.visible.isEmpty());assertTrue(q.listed.isEmpty())
        assertFalse(q.selectListed("m5"));q.scrub(1.0)
        assertEquals(listOf("m7"),q.listed.map{it.id})
        q.overview();q.chooseMinimum(4.0)
        q.accept(catalog(event("east").copy(longitude=140.0),event("west").copy(longitude=-120.0)))
        assertEquals(setOf("east","west"),q.listed.map{it.id}.toSet())
    }
    @Test fun listSelectionPreservesDisplayedPeriodMinimumAndReplayCursor(){
        val q=EarthquakeState();q.accept(catalog(event("target",now.minusSeconds(5*86400),7.0)));q.chooseMinimum(7.0);q.scrub(.5)
        val cursor=q.replayTime
        assertTrue(q.selectListed("target"));assertEquals(QuakePeriod.WEEK,q.period)
        assertEquals(7.0,q.minimum,0.0);assertTrue(q.replay);assertFalse(q.playing);assertEquals(cursor,q.replayTime)
        assertEquals("target",q.selectedId);assertEquals("target",q.visible.single().id)
        assertEquals(1,q.focusRequest);assertTrue(q.selectionUsesCityFocus);assertTrue(q.panelOpen)
    }
    @Test fun pendingRefreshCannotLeakIntoDisplayedListOrSelectedLocation(){
        val q=EarthquakeState();val old=event("target",now.minusSeconds(5*86400));q.accept(catalog(old));q.scrub(.5)
        val revised=old.copy(latitude=-20.2,longitude=-174.2,magnitude=7.1)
        q.accept(catalog(revised));assertNotNull(q.pending)
        assertEquals(old,q.listed.single());assertTrue(q.selectListed("target"))
        assertEquals(old,q.selected);assertNotNull(q.pending)
        q.overview();assertEquals(revised,q.listed.single());assertEquals(revised,q.selected);assertNull(q.pending)
    }
    @Test fun removedOutOfPeriodAndFutureEventsCannotTriggerFocus(){
        val q=EarthquakeState();q.accept(catalog(event("removed")));q.accept(catalog())
        assertFalse(q.selectListed("removed"));assertEquals(0,q.focusRequest);assertNull(q.selected)
        q.accept(catalog(event("old",now.minusSeconds(7*86400+1)),event("edge",now.minusSeconds(7*86400)),event("future",now.plusSeconds(100))))
        assertFalse(q.selectListed("old"));assertFalse(q.selectListed("future"));assertEquals(0,q.focusRequest)
        assertEquals(listOf("edge"),q.listed.map{it.id});assertTrue(q.selectListed("edge"))
    }
    @Test fun sortingIsDescendingAndStableWithoutChangingMapOrSelection(){
        val q=EarthquakeState();q.accept(catalog(event("old-large",now.minusSeconds(3600),7.1),event("b",mag=5.1),event("a",mag=5.1)))
        q.selectListed("old-large");val revision=q.revision;val filtered=q.filtered
        assertEquals(listOf("old-large","a","b"),q.listed.map{it.id})
        q.chooseSort(QuakeSort.TIME);assertEquals(listOf("a","b","old-large"),q.listed.map{it.id})
        assertEquals("old-large",q.selectedId);assertSame(filtered,q.filtered);assertEquals(revision,q.revision)
        q.clearSelection();q.selectGroup(q.visible);assertEquals(q.listed,q.group)
        q.chooseSort(QuakeSort.MAGNITUDE);assertEquals(q.listed,q.group)
    }
    @Test fun sortingHandlesMissingMagnitudeAndDoesNotMutateInput(){
        val events=listOf(event("null",mag=null),event("nan",mag=Double.NaN),event("valid",mag=4.0))
        assertEquals("valid",QuakeRules.sortedEvents(events,QuakeSort.MAGNITUDE).first().id)
        assertEquals("null",events.first().id)
    }
    @Test fun switchingPeriodsClearsOldListAndRejectsLateResponse(){
        val q=EarthquakeState();val week=catalog(event("week-red",now.minusSeconds(3*86400),7.0));q.accept(week)
        q.choosePeriod(QuakePeriod.DAY);assertTrue(q.listed.isEmpty());q.accept(week);assertTrue(q.listed.isEmpty())
        q.accept(week.copy(period=QuakePeriod.DAY));assertTrue(q.listed.isEmpty())
        q.choosePeriod(QuakePeriod.WEEK);q.accept(week);assertEquals("week-red",q.listed.single().id)
    }
    @Test fun sourceLinkUsesStableIdAndNeverUntrustedUrl(){
        assertEquals("https://earthquake.usgs.gov/earthquakes/eventpage/us6000u43g",QuakeRules.sourceUrl(event("us6000u43g").copy(url="https://example.invalid")))
        assertNull(QuakeRules.sourceUrl(event("../invalid")))
    }
    @Test fun shortNamesRetainRegionAndCountryWithoutInventingCities(){
        assertEquals("Gorontalo, Indonesia",QuakeRules.regionName("94 km WSW of Gorontalo, Indonesia"))
        assertEquals("South Sandwich Islands region",QuakeRules.regionName(" South  Sandwich\nIslands region "))
        assertEquals("Tonga",QuakeRules.regionName("12.5 km N of Tonga"))
        assertEquals("地点暂无",QuakeRules.regionName(" "))
    }
    @Test fun panelCloseAndReopenPreserveEarthquakeModeAndWorldTime(){
        val clock=ClockState();clock.now=now;clock.previewBase=now.minusSeconds(3600);val original=clock.clockInstant
        clock.toggleEarthquakePanel();clock.earthquakes.accept(catalog(event("target")))
        assertTrue(clock.earthquakes.selectListed("target"));clock.toggleEarthquakePanel()
        assertEquals(EarthMode.EARTHQUAKE,clock.mode);assertFalse(clock.earthquakes.panelOpen);assertNull(clock.earthquakes.selected)
        clock.toggleEarthquakePanel();assertTrue(clock.earthquakes.panelOpen)
        clock.switchMode(EarthMode.WORLD_TIME);assertEquals(original,clock.clockInstant)
    }
    @Test fun manualTakeoverCancelsSelectionAndDeferredListFocus(){
        val clock=ClockState();clock.switchMode(EarthMode.EARTHQUAKE);clock.earthquakes.accept(catalog(event("target")))
        clock.earthquakes.selectListed("target");clock.manualInteraction()
        assertNull(clock.earthquakes.selected);assertFalse(clock.earthquakes.selectionUsesCityFocus)
        assertFalse(clock.earthquakes.panelOpen) // V82: direct globe interaction dismisses pages.
    }
    @Test fun leavingEarthquakeClearsPinnedSelectionBeforeReentry(){
        val clock=ClockState();clock.switchMode(EarthMode.EARTHQUAKE)
        clock.earthquakes.accept(catalog(event("target")));clock.earthquakes.selectListed("target")
        clock.switchMode(EarthMode.TEMPERATURE)
        assertNull(clock.earthquakes.selected);assertFalse(clock.earthquakes.selectionUsesCityFocus)
        clock.switchMode(EarthMode.EARTHQUAKE);assertNull(clock.earthquakes.selected)
    }
}
