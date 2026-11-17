package com.wupengfei.worldclock.content

import java.io.File
import java.time.Instant
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class GeographicPlaceTest {
    companion object {
        private val assets=File("src/main/assets")
        private val surface by lazy{CoordinateSurface.read(File(assets,"coordinates/earth_surface.bin").inputStream())}
        private val base by lazy{CityCatalog.read(File(assets,"cities/catalog.tsv").bufferedReader()).apply{
            bindPinPositions(File(assets,"cities/pin_positions.tsv").bufferedReader())
            val rules=CityTimeZones.read(File(assets,"cities/timezones.tsv").bufferedReader())
            cities.forEach{it.timeZones=rules}
        }}
        private val catalog by lazy{base.withGeographicPlaces(File(assets,"places/south_china_sea.tsv").bufferedReader(),surface)}
        private val places get()=catalog.cities.filter{it.isGeographicPlace}
        private fun place(name:String)=places.single{it.name==name}
    }
    @Test fun originalCitiesDirectionsAndDefaultSearchArePreserved(){
        assertEquals(base.search("").map{it.id},catalog.search("").map{it.id})
        base.cities.forEach{assertSame(it,catalog.city(it.id))}
        assertEquals("1816670",catalog.search("北京").first().id)
        assertEquals("5128581",catalog.search("New York").first().id)
        assertTrue(catalog.search("Japan").all{it.countryCode=="JP"})
        assertTrue(catalog.search("美国").all{it.countryCode=="US"})
    }
    @Test fun southChinaSeaNamesAliasesAndGroupSearchAreOffline(){
        for(name in listOf("西沙群岛","南沙群岛","东沙群岛","中沙群岛","永兴岛","东沙岛","太平岛","美济礁","永暑礁","黄岩岛")){
            assertEquals(name,catalog.search(name).first().name)
        }
        assertEquals("西沙群岛",catalog.search("Paracel Islands").first().name)
        assertEquals("永兴岛",catalog.search("Woody Island").first().name)
        assertEquals("西沙群岛",catalog.search("xisha").first().name)
        assertTrue(catalog.search("西沙",200).any{it.name=="永兴岛"})
        assertTrue(catalog.search("南沙",200).any{it.name=="美济礁"})
        assertEquals("西沙 · 岛",place("东岛").searchContext)
    }
    @Test fun independentGeographicTableIsCompleteAndIdSafe(){
        assertTrue(places.size>=50)
        assertEquals(catalog.cities.size,catalog.cities.map{it.id}.distinct().size)
        places.forEach{
            assertTrue(it.id.matches(Regex("[0-9]{12}")))
            assertTrue(it.countryCode.isBlank()&&it.country.isBlank())
            assertTrue(it.region.isNotBlank()&&it.locationLabel.isNotBlank())
            assertNotNull(it.mappedDirection)
        }
    }
    @Test fun geographicCoordinatesRoundTripThroughShippedMeshUnderTransforms(){
        val rotations=listOf(GlobeQuaternion.IDENTITY,
            GlobeQuaternion.axisAngle(GlobeVector3.Y,123.0)*GlobeQuaternion.axisAngle(GlobeVector3.X,23.44))
        for(p in places)for(rotation in rotations)for(scale in listOf(.25,1.0,2.0)){
            val translation=GlobeVector3(1.5,-.2,4.0)
            val world=rotation.rotate(p.direction*scale)+translation
            val actual=checkNotNull(surface.coordinate(rotation.inverse().rotate(world-translation)*(1.0/scale)))
            assertEquals(p.name,p.latitude,actual.latitude,.0002)
            assertEquals(p.name,p.longitude,checkNotNull(actual.longitude),.0002)
        }
    }
    @Test fun wellKnownIslandsLandInIndependentlyCheckedGeographicWindows(){
        val y=place("永兴岛");assertTrue(y.latitude in 16.82..16.85&&y.longitude in 112.32..112.36)
        val d=place("东沙岛");assertTrue(d.latitude in 20.68..20.72&&d.longitude in 116.71..116.75)
        val m=place("美济礁");assertTrue(m.latitude in 9.87..9.94&&m.longitude in 115.50..115.58)
        val t=place("太平岛");assertTrue(t.latitude in 10.36..10.39&&t.longitude in 114.35..114.38)
    }
    @Test fun referenceOffsetsNeverUseUnrelatedCityDstRules(){
        for(time in listOf("1900-06-01T00:00:00Z","1988-06-01T00:00:00Z","2026-06-01T00:00:00Z","2100-06-01T00:00:00Z")){
            val instant=Instant.parse(time)
            places.forEach{
                assertEquals("08:00",it.time(instant));assertEquals("UTC+08:00",it.offset(instant))
                assertFalse(it.isDaylightSaving(instant))
            }
        }
    }
    @Test fun newNumericPlaceIdsPersistAlongsideExistingFootprintAlbums(){
        val dir=Files.createTempDirectory("island-catalog-test").toFile()
        try{
            val store=FootprintStore(dir);store.addCity("1816670");store.addCity(place("永兴岛").id)
            val restored=FootprintStore(dir).read()
            assertEquals(listOf("1816670",place("永兴岛").id),restored.map{it.cityId})
            val state=FootprintState();state.accept(restored)
            assertEquals(listOf("北京","永兴岛"),state.cities(catalog).map{it.name})
        }finally{
            val tempRoot=File(requireNotNull(System.getProperty("java.io.tmpdir"))).canonicalFile.toPath()
            require(dir.canonicalFile.toPath().startsWith(tempRoot)&&dir.name.startsWith("island-catalog-test"))
            dir.deleteRecursively()
        }
    }
    @Test fun rapidSuccessiveFocusUsesActualPinDirections(){
        val motion=GlobeMotion();motion.setNormalPole(GlobeVector3.Z)
        val target=GlobeVector3(.1,.2,1.0).normalized();var now=0.0
        for(p in listOf(place("永兴岛"),place("美济礁"),place("东沙岛"),place("永兴岛"))){
            // ClockScene provides the current displayed direction, not the immutable local pin.
            motion.focusCity(motion.presentation.rotate(p.direction),target)
            repeat(12){now+=.01;motion.tick(.01,now)}
        }
        while(motion.focusing&&now<30){now+=.01;motion.tick(.01,now)}
        assertEquals(1.0,motion.presentation.rotate(place("永兴岛").direction).dot(target),1e-7)
    }
    @Test fun geographicPointAndCityRemainUsableAsDistanceEndpoints(){
        val a=place("永兴岛");val b=checkNotNull(catalog.city("1816670"))
        val state=DistanceState();state.toggle(listOf(a,b))
        assertEquals(a to b,state.pair(listOf(a,b)))
        assertTrue(GreatCircle.km(a,b) in 2500.0..2700.0)
        val path=DistancePath.create(surface,a,b)
        assertTrue(path.samples.first().normalized().dot(a.direction)>.999999)
        assertTrue(path.samples.last().normalized().dot(b.direction)>.999999)
    }
    @Test fun invalidOrDuplicateSupplementNeverMutatesOriginalCatalogue(){
        fun row(id:String,lat:String="16.83",feature:String="ISLAND")=
            "$id\t测试岛\tTest Island\t\t\t西沙群岛\t$lat\t112.34\t+08:00\t$feature\t0\ttest"
        for(data in listOf(row("1816670"),row("900000999999","NaN"),row("900000999999",feature="PPL"),
            row("900000999999")+"\n"+row("900000999999"))){
            try{base.withGeographicPlaces(data.reader().buffered(),surface);fail("invalid supplement accepted")}
            catch(expected:IllegalArgumentException){assertNull(base.city("900000999999"))}
        }
    }
}
