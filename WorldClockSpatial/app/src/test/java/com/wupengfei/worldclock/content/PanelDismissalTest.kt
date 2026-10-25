package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test

class PanelDismissalTest {
    private fun openAll(c:ClockState){c.citiesOpen=true;c.timeToolsOpen=true;c.searching=true;c.query="test";c.earthquakes.panelOpen=true}
    private fun assertClosed(c:ClockState){assertFalse(c.citiesOpen);assertFalse(c.timeToolsOpen);assertFalse(c.searching);assertFalse(c.earthquakes.panelOpen);assertEquals("",c.query)}
    @Test fun globeAndTimelineInteractionDismissEveryPage(){
        val c=ClockState();openAll(c);c.manualInteraction();assertClosed(c)
        openAll(c);c.beginTimeDrag();assertClosed(c)
    }
    @Test fun eachDockEntryClosesOtherPagesAndOpensOnlyItsOwn(){
        val c=ClockState();c.earthquakes.panelOpen=false
        c.toggleCities();c.searching=true;c.query="abc"
        c.toggleTimeTools();assertFalse(c.citiesOpen);assertFalse(c.searching);assertEquals("",c.query);assertTrue(c.timeToolsOpen)
        c.toggleEarthquakePanel();assertFalse(c.timeToolsOpen);assertTrue(c.earthquakes.panelOpen)
        c.toggleTimeTools();assertFalse(c.earthquakes.panelOpen);assertTrue(c.timeToolsOpen)
        c.switchMode(EarthMode.TEMPERATURE);assertClosed(c)
    }
    @Test fun inPageTouchesAndFiltersDoNotDismissThePage(){
        val c=ClockState();c.toggleEarthquakePanel();c.touchUi();c.earthquakes.chooseMinimum(5.0)
        assertTrue(c.earthquakes.panelOpen);c.earthquakes.choosePeriod(QuakePeriod.DAY);assertTrue(c.earthquakes.panelOpen)
        c.toggleCities();c.searching=true;c.touchUi();assertTrue(c.citiesOpen);assertTrue(c.searching)
    }
    @Test fun mainFeatureActionsClosePagesAndResetThirdLevelState(){
        val c=ClockState();openAll(c);val epoch=c.panelEpoch;c.toggleCoordinates();assertClosed(c);assertTrue(c.panelEpoch>epoch)
        openAll(c);c.useFeature();assertClosed(c)
        openAll(c);c.live();assertClosed(c)
    }
    @Test fun sameButtonStillTogglesOffAndReopensCleanly(){
        val c=ClockState();c.toggleCities();c.searching=true;c.toggleCities();assertClosed(c)
        c.toggleCities();assertTrue(c.citiesOpen);assertFalse(c.searching)
        c.toggleEarthquakePanel();c.toggleEarthquakePanel();assertClosed(c)
        c.toggleEarthquakePanel();assertTrue(c.earthquakes.panelOpen)
    }
}
