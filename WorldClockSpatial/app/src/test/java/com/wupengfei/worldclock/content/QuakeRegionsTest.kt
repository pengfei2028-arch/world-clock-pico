package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class QuakeRegionsTest {
    private val now=Instant.parse("2026-10-25T08:00:00Z")
    private fun event(id:String,time:Instant=now.minusSeconds(100),mag:Double?=5.2)=
        QuakeEvent(id,setOf(id),"94 km WSW of Gorontalo, Indonesia",time,time,35.6,140.9,24.0,mag,"mw","")
    private fun catalog(vararg events:QuakeEvent)=QuakeCatalog(QuakePeriod.WEEK,now,now,events.toList())

    @Test fun recentRegionsUsesStrictMagnitudeAndRealRolling24HourWindow(){
        val c=catalog(event("exact-five",mag=5.0),event("missing",mag=null),event("edge",now.minusSeconds(86400)),
            event("old",now.minusSeconds(86400).minusMillis(1),8.0),event("future",now.plusMillis(1)),event("recent"))
        assertEquals(listOf("recent","edge"),QuakeRules.recentRegions(c,now).map{it.id})
        assertTrue(QuakeRules.recentRegions(c,now.plusSeconds(2*86400)).isEmpty())
    }
    @Test fun recentListIsIndependentOfMapMinimumAndReplayCursor(){
        val q=EarthquakeState();q.accept(catalog(event("m5"),event("m7",mag=7.1),event("week",now.minusSeconds(3*86400))))
        q.chooseMinimum(7.0);q.scrub(0.0)
        assertTrue(q.visible.isEmpty());assertEquals(setOf("m5","m7"),q.recentRegions(now).map{it.id}.toSet())
    }
    @Test fun shortcutReturnsToOverviewPreservesPeriodAndMakesTargetVisible(){
        val q=EarthquakeState();q.accept(catalog(event("target")));q.chooseMinimum(7.0);q.scrub(.1)
        assertTrue(q.selectRecent("target",now));assertEquals(QuakePeriod.WEEK,q.period)
        assertEquals(5.0,q.minimum,0.0);assertFalse(q.replay);assertFalse(q.playing)
        assertEquals("target",q.selectedId);assertEquals("target",q.visible.single().id)
        assertEquals(1,q.focusRequest);assertTrue(q.selectionUsesCityFocus);assertTrue(q.panelOpen)
    }
    @Test fun selectionUsesLatestPendingRecordAndDoesNotNarrowAnInclusiveFilter(){
        val q=EarthquakeState();val old=event("target");q.accept(catalog(old));q.scrub(.5)
        val revised=old.copy(latitude=-20.2,longitude=-174.2,magnitude=7.1)
        q.accept(catalog(revised));assertNotNull(q.pending)
        assertTrue(q.selectRecent("target",now));assertEquals(4.0,q.minimum,0.0)
        assertEquals(revised,q.selected);assertNull(q.pending)
    }
    @Test fun removedExpiredAndFutureEventsCannotTriggerFocus(){
        val q=EarthquakeState();q.accept(catalog(event("removed")));q.scrub(.5);q.accept(catalog())
        assertFalse(q.selectRecent("removed",now));assertEquals(0,q.focusRequest);assertNull(q.selected)
        q.overview();q.accept(catalog(event("old",now.minusSeconds(90000)),event("future",now.plusSeconds(100))))
        assertFalse(q.selectRecent("old",now));assertFalse(q.selectRecent("future",now));assertEquals(0,q.focusRequest)
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
        assertTrue(clock.earthquakes.selectRecent("target",now));clock.toggleEarthquakePanel()
        assertEquals(EarthMode.EARTHQUAKE,clock.mode);assertFalse(clock.earthquakes.panelOpen);assertNull(clock.earthquakes.selected)
        clock.toggleEarthquakePanel();assertTrue(clock.earthquakes.panelOpen)
        clock.switchMode(EarthMode.WORLD_TIME);assertEquals(original,clock.clockInstant)
    }
    @Test fun manualTakeoverCancelsSelectionAndDeferredListFocus(){
        val clock=ClockState();clock.switchMode(EarthMode.EARTHQUAKE);clock.earthquakes.accept(catalog(event("target")))
        clock.earthquakes.selectRecent("target",now);clock.manualInteraction()
        assertNull(clock.earthquakes.selected);assertFalse(clock.earthquakes.selectionUsesCityFocus)
        assertFalse(clock.earthquakes.panelOpen) // V82: direct globe interaction dismisses pages.
    }
    @Test fun leavingEarthquakeClearsPinnedSelectionBeforeReentry(){
        val clock=ClockState();clock.switchMode(EarthMode.EARTHQUAKE)
        clock.earthquakes.accept(catalog(event("target")));clock.earthquakes.selectRecent("target",now)
        clock.switchMode(EarthMode.TEMPERATURE)
        assertNull(clock.earthquakes.selected);assertFalse(clock.earthquakes.selectionUsesCityFocus)
        clock.switchMode(EarthMode.EARTHQUAKE);assertNull(clock.earthquakes.selected)
    }
}
