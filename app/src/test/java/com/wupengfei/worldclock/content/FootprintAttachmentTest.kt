package com.wupengfei.worldclock.content

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class FootprintAttachmentTest {
    private class Pin {
        var nativeLinked=false
        var enabled=false
        val visible get()=nativeLinked&&enabled
    }
    private class Stage {
        var active=true
        var failNext=false
        var attempts=0
        val registrations=mutableSetOf<Pin>()
        val mounts=FootprintAttachmentMounts<Pin>(
            attach={pin->
                attempts++
                if(failNext){failNext=false;false}else{
                    // Mirrors SDK onAddToTree: inactive-parent addition does not
                    // establish the native attachment's isAddedToTree flag.
                    pin.nativeLinked=active;registrations.add(pin);true
                }
            },enable={pin,value->pin.enabled=value},
        )
        fun show(pin:Pin?,visible:Boolean=true)=mounts.show(pin,visible,active)
    }
    @Test fun newYorkCreatedDuringPickerPauseMountsOnlyAfterResume(){
        val stage=Stage();val beijing=Pin();val london=Pin();val newYork=Pin()
        stage.show(beijing);stage.show(london);assertEquals(2,stage.attempts)
        stage.active=false
        repeat(120){stage.show(beijing);stage.show(london);stage.show(newYork)}
        assertEquals(2,stage.attempts);assertEquals(2,stage.mounts.size);assertFalse(newYork.visible)
        stage.active=true
        stage.show(beijing);stage.show(london);stage.show(newYork)
        assertTrue(beijing.visible);assertTrue(london.visible);assertTrue(newYork.visible)
        assertEquals(3,stage.attempts);assertEquals(3,stage.registrations.size)
    }
    @Test fun rejectedAddIsRetriedInsteadOfPermanentlyMarkedMounted(){
        val stage=Stage();val pin=Pin();stage.failNext=true
        stage.show(pin);assertEquals(0,stage.mounts.size);assertFalse(pin.visible)
        stage.show(pin);assertEquals(1,stage.mounts.size);assertTrue(pin.visible);assertEquals(2,stage.attempts)
    }
    @Test fun notYetCreatedAndBackSidePinsRemainPending(){
        val stage=Stage();stage.show(null);val pin=Pin()
        repeat(30){stage.show(pin,false)}
        assertEquals(0,stage.attempts)
        stage.show(pin);assertTrue(pin.visible);assertEquals(1,stage.attempts)
    }
    @Test fun oneHundredPickerReturnsReuseTheSameNativeObjects(){
        val stage=Stage();val pins=List(16){Pin()};pins.forEach(stage::show)
        repeat(100){
            stage.active=false;pins.forEach(stage::show);assertTrue(pins.none{it.visible})
            stage.active=true;pins.forEach(stage::show);assertTrue(pins.all{it.visible})
        }
        assertEquals(16,stage.attempts);assertEquals(16,stage.mounts.size);assertEquals(16,stage.registrations.size)
        stage.mounts.close();assertEquals(0,stage.mounts.size);assertTrue(pins.none{it.visible})
    }
    @Test fun nativeLegacyFailureExplainsWhyVisibilityOnlyCannotRepairPausedAddition(){
        // Regression evidence from the SDK contract, not a claimed device reproduction.
        val stage=Stage();val pin=Pin()
        stage.active=false
        pin.enabled=true
        pin.nativeLinked=stage.active // old addEntity during pause
        stage.active=true;pin.enabled=true // old code only retries enabled, never add
        assertFalse(pin.visible)
        stage.show(pin)
        assertTrue(pin.visible)
    }
    @Test fun addingNewYorkPhotoUpdatesItsLiveSlotWithoutRestartOrChangingOtherPhotos(){
        val catalog=CityCatalog.read(File("src/main/assets/cities/catalog.tsv").bufferedReader())
        val surface=CoordinateSurface.read(File("src/main/assets/coordinates/earth_surface.bin").inputStream())
        val ids=listOf("1816670","2643743","5128581")
        val f=FootprintState()
        val photos=List(3){FootprintPhoto(('a'+it).toString().repeat(64),it.toLong())}
        f.accept(ids.take(2).mapIndexed{i,id->FootprintAlbum(id,listOf(photos[i]))})
        f.accept(f.albums+FootprintAlbum(ids.last()))
        f.selectCity(ids.last())
        assertEquals("纽约",f.city(catalog)!!.name)
        f.accept(f.albums.map{if(it.cityId==ids.last())it.copy(photos=listOf(photos.last()))else it})
        assertEquals(photos.last(),f.photo)
        assertEquals(ids,f.cities(catalog).map{it.id})
        assertEquals(photos,f.albums.map{it.photos.single()})
        ids.forEach { id->
            val city=checkNotNull(catalog.city(id))
            val p=surface.point(city.latitude,city.longitude)
            val actual=checkNotNull(surface.coordinate(p))
            assertEquals(city.latitude,actual.latitude,.0002)
            assertEquals(0.0,SolarPosition.wrap(actual.longitude!!-city.longitude),.0002)
            val toward=GlobeVector3(0.0,.07,1.0).normalized()
            val q=GlobeQuaternion.align(p.normalized(),toward)
            assertEquals(1.0,q.rotate(p.normalized()).dot(toward),1e-8)
        }
    }
}
