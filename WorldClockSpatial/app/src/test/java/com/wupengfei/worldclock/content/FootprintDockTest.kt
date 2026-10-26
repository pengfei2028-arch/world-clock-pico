package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class FootprintDockTest {
    @get:Rule val temporary=TemporaryFolder()
    private val a=FootprintPhoto("a".repeat(64),1)
    private val b=FootprintPhoto("b".repeat(64),2)
    private fun state()=FootprintState().apply{
        enabled=true
        accept(listOf(FootprintAlbum("1",listOf(a,b)),FootprintAlbum("2")))
    }
    @Test fun selectingCityShowsTrayOnlyAndThumbnailExplicitlyOpensViewer(){
        val f=state();f.selectCity("1")
        assertTrue(f.albumOpen);assertFalse(f.viewerOpen)
        f.openPhoto(b.id);assertTrue(f.viewerOpen);assertEquals(b,f.photo)
        f.closeViewer();assertFalse(f.viewerOpen);assertTrue(f.albumOpen);assertEquals(b,f.photo)
        f.openPhoto(a.id);assertTrue(f.viewerOpen);assertEquals(a,f.photo)
    }
    @Test fun anotherCityCannotRetainAnExpandedOrStalePhoto(){
        val f=state();f.selectCity("1");f.openPhoto(b.id);f.selectCity("2")
        assertTrue(f.albumOpen);assertFalse(f.viewerOpen);assertNull(f.photo)
        f.openPhoto(b.id);assertFalse(f.viewerOpen);assertNull(f.photo)
    }
    @Test fun closedAlbumCannotBeReopenedByAnOldThumbnailCallback(){
        val f=state();f.selectCity("1");f.openPhoto(a.id);f.closeAlbum();f.openPhoto(b.id)
        assertFalse(f.viewerOpen);assertFalse(f.albumOpen);assertEquals(a,f.photo)
    }
    @Test fun placesListThenSearchAreDistinctAndDoNotLosePhotos(){
        val f=state();f.selectCity("1");f.openPhoto(b.id);f.openPlaces()
        assertTrue(f.placesOpen);assertFalse(f.placesSearching);assertFalse(f.albumOpen);assertFalse(f.viewerOpen)
        f.searchPlaces();f.query="巴黎";assertTrue(f.placesSearching)
        f.closeSearch();assertFalse(f.placesSearching);assertTrue(f.placesOpen);assertEquals("",f.query)
        assertEquals(listOf(a,b),f.albums.first().photos)
    }
    @Test fun footprintIconReopensOwnMenuWithoutDisablingLayerOrChangingClockCities(){
        val clock=ClockState();clock.footprints.accept(listOf(FootprintAlbum("1",listOf(a))))
        clock.toggleFootprints();assertTrue(clock.footprints.placesOpen)
        clock.footprints.selectCity("1");clock.footprints.openPhoto(a.id)
        clock.toggleFootprints()
        assertTrue(clock.footprints.enabled);assertTrue(clock.footprints.placesOpen);assertFalse(clock.footprints.viewerOpen)
        clock.toggleFootprints();assertFalse(clock.footprints.placesOpen);assertTrue(clock.footprints.enabled)
        clock.toggleCities();assertFalse(clock.footprints.enabled);assertTrue(clock.citiesOpen)
        assertTrue(clock.cities.isEmpty());assertEquals(a,clock.footprints.albums.single().photos.single())
    }
    @Test fun repeatedGlobeAndFeatureInteractionsClearTransientViewerAndSearch(){
        val clock=ClockState();clock.footprints.accept(listOf(FootprintAlbum("1",listOf(a))))
        repeat(50){
            clock.toggleFootprints();clock.footprints.selectCity("1");clock.footprints.openPhoto(a.id)
            clock.manualInteraction();assertFalse(clock.footprints.albumOpen);assertFalse(clock.footprints.viewerOpen)
            clock.footprints.openPlaces();clock.footprints.searchPlaces();clock.footprints.query="London"
            clock.toggleDistance()
            assertFalse(clock.footprints.enabled);assertFalse(clock.footprints.placesSearching);assertFalse(clock.footprints.placesOpen)
            assertEquals("",clock.footprints.query)
        }
        assertEquals(a,clock.footprints.albums.single().photos.single())
    }
    @Test fun pickerCancellationRetainsCurrentCityTrayWithoutOpeningViewer(){
        val f=state();f.selectCity("1");f.restorePicker("1")
        f.pickerResult(emptyList())
        assertNull(f.pickingCityId);assertTrue(f.albumOpen);assertFalse(f.viewerOpen);assertEquals("1",f.selectedCityId)
    }
    @Test fun removalOfLastSelectedPhotoClosesViewer(){
        val f=state();f.selectCity("1");f.openPhoto(a.id)
        f.accept(listOf(FootprintAlbum("1")))
        assertFalse(f.viewerOpen);assertNull(f.photo);assertTrue(f.albumOpen)
    }
    @Test fun removingEmptyPlaceNeverDeletesAnotherAlbumOrAnyPhoto(){
        val store=FootprintStore(temporary.newFolder());store.addCity("1");store.addCity("2")
        listOf("original","preview.jpg","thumb.jpg").forEach{store.photoFile(a.id,it).apply{parentFile!!.mkdirs();writeText("local")}}
        store.append("1",a)
        store.removeEmptyCity("2")
        assertEquals(listOf(FootprintAlbum("1",listOf(a))),store.read())
        assertThrows(IllegalArgumentException::class.java){store.removeEmptyCity("1")}
        assertEquals(a,store.read().single().photos.single());assertTrue(store.photoFile(a.id,"original").isFile)
    }
}
