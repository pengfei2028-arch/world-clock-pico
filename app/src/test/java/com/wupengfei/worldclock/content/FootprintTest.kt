package com.wupengfei.worldclock.content

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class FootprintTest {
    @get:Rule val temporary=TemporaryFolder()
    private fun store()=FootprintStore(temporary.newFolder())
    private val photoA=FootprintPhoto("a".repeat(64),100,"2026-06-15")
    private val photoB=FootprintPhoto("b".repeat(64),200)
    private fun stage(store:FootprintStore,p:FootprintPhoto){
        listOf("original","preview.jpg","thumb.jpg").forEach{store.photoFile(p.id,it).apply{parentFile!!.mkdirs();writeText("test-local-payload")}}
    }
    private fun city(id:String)=ClockCity(id,"城市$id","City$id","NO","挪威","",60.4,5.32,"Europe/Oslo","PPL",1,"")
    @Test fun albumsPersistIndependentlyAndSamePhotoCanBelongToTwoCities(){
        val store=store();assertTrue(store.read().isEmpty())
        store.addCity("3161732");store.addCity("2988507");stage(store,photoA)
        store.append("3161732",photoA);store.append("2988507",photoA)
        val next=FootprintStore(store.directory).read()
        assertEquals(listOf("3161732","2988507"),next.map{it.cityId})
        assertTrue(next.all{it.photos.single()==photoA})
        assertFalse(File(store.directory,"world_clock").exists())
    }
    @Test fun duplicateImportDoesNotAppendOrAlterFirstCaptureDate(){
        val store=store();store.addCity("1");stage(store,photoA)
        store.append("1",photoA);store.append("1",photoA.copy(addedAt=999,takenDate=null));store.addCity("1")
        assertEquals(FootprintAlbum("1",listOf(photoA)),store.read().single())
    }
    @Test fun incompletePhotoOrUnknownCityCannotReplaceTheSavedIndex(){
        val store=store();store.addCity("1");val before=File(store.directory,"albums.json").readBytes()
        assertThrows(IllegalArgumentException::class.java){store.append("1",photoA)}
        stage(store,photoA);assertThrows(IllegalStateException::class.java){store.append("2",photoA)}
        assertArrayEquals(before,File(store.directory,"albums.json").readBytes())
    }
    @Test fun interruptedIndexWriteLeavesCommittedAlbumReadable(){
        val store=store();store.addCity("1");stage(store,photoA);store.append("1",photoA)
        File(store.directory,"albums.pending").writeText("{broken")
        assertEquals(photoA,FootprintStore(store.directory).read().single().photos.single())
        store.addCity("2");assertEquals(2,store.read().size)
    }
    @Test fun corruptOrFutureIndexIsNeverSilentlyReplaced(){
        val store=store();val index=File(store.directory,"albums.json")
        listOf("invalid", "{\"version\":2,\"albums\":[]}").forEach { contents ->
            index.writeText(contents)
            assertThrows(Exception::class.java){store.addCity("1")}
            assertEquals(contents,index.readText())
        }
    }
    @Test fun pathsAndLimitsRejectInvalidInputWithoutChangingPriorAlbums(){
        val store=store();repeat(FootprintStore.MAX_CITIES){store.addCity("${it+1}")}
        assertThrows(IllegalArgumentException::class.java){store.addCity("999")}
        assertThrows(IllegalArgumentException::class.java){store.addCity("../other")}
        assertThrows(IllegalArgumentException::class.java){store.photoFile("../other","original")}
        assertThrows(IllegalArgumentException::class.java){store.photoFile(photoA.id,"../../other")}
        assertEquals(FootprintStore.MAX_CITIES,store.read().size)
    }
    @Test fun decodingBoundsPreservePortraitLandscapeAndSmallImages(){
        assertEquals(1600 to 800,FootprintStore.sampledSize(8192,4096,1600))
        assertEquals(900 to 1600,FootprintStore.sampledSize(4500,8000,1600))
        assertEquals(384 to 216,FootprintStore.sampledSize(8000,4500,384))
        assertEquals(24 to 24,FootprintStore.sampledSize(24,24,1600))
    }
    @Test fun switchingCitiesCannotDisplayThePreviousCityPhoto(){
        val state=FootprintState()
        state.accept(listOf(FootprintAlbum("1",listOf(photoA,photoB)),FootprintAlbum("2")))
        state.selectCity("1");state.selectPhoto(photoB.id);assertEquals(photoB,state.photo)
        state.selectCity("2");assertNull(state.photo);assertNull(state.selectedPhotoId);assertTrue(state.albumOpen)
        state.selectCity("1");assertEquals(photoA,state.photo)
        state.selectPhoto("unknown");assertEquals(photoA,state.photo)
    }
    @Test fun photoInFlightKeepsItsCapturedCityWhileUserSelectsAnother(){
        val state=FootprintState();state.accept(listOf(FootprintAlbum("1"),FootprintAlbum("2")))
        state.restorePicker("1");state.selectCity("2")
        assertEquals("1",state.pickingCityId);assertEquals("2",state.selectedCityId)
        state.accept(listOf(FootprintAlbum("1",listOf(photoA)),FootprintAlbum("2")))
        assertNull(state.photo);assertEquals("2",state.selectedCityId)
        state.pickerResult(emptyList());assertNull(state.pickingCityId);assertTrue(state.error.isEmpty())
    }
    @Test fun timeCitiesAndModeChangesNeverDeleteFootprintAlbums(){
        val clock=ClockState();clock.cities=listOf(city("1"),city("2"))
        clock.footprints.accept(listOf(FootprintAlbum("3",listOf(photoA))))
        val instant=Instant.parse("2026-10-26T12:00:00Z");clock.previewBase=instant
        clock.toggleFootprints();assertFalse(clock.timeModeActive);assertTrue(clock.footprints.enabled)
        clock.cities=emptyList();assertEquals("3",clock.footprints.albums.single().cityId)
        clock.toggleDistance();assertFalse(clock.footprints.enabled);assertEquals(photoA,clock.footprints.albums.single().photos.single())
        clock.toggleFootprints();clock.switchMode(EarthMode.EARTHQUAKE);assertFalse(clock.footprints.enabled)
        clock.toggleFootprints();clock.switchMode(EarthMode.TEMPERATURE);assertFalse(clock.footprints.enabled)
        clock.switchMode(EarthMode.WORLD_TIME);assertEquals(instant,clock.previewBase);assertTrue(clock.cities.isEmpty())
    }
    @Test fun repeatedModeSwitchesAndGlobeInteractionDismissOnlyTransientPanels(){
        val clock=ClockState();clock.footprints.accept(listOf(FootprintAlbum("3",listOf(photoA))))
        repeat(40){
            clock.toggleFootprints();clock.footprints.selectCity("3");clock.footprints.selectPhoto(photoA.id)
            clock.manualInteraction();assertFalse(clock.footprints.albumOpen);assertTrue(clock.footprints.enabled)
            clock.toggleCities();assertFalse(clock.footprints.enabled);assertTrue(clock.citiesOpen)
        }
        assertEquals(listOf(FootprintAlbum("3",listOf(photoA))),clock.footprints.albums)
    }
}
