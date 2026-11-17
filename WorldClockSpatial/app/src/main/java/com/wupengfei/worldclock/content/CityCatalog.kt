package com.wupengfei.worldclock.content

import java.io.BufferedReader
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class ClockCity(val id: String,val name: String,val english: String,val countryCode: String,
    val country: String,val region: String,val latitude: Double,val longitude: Double,val zoneName: String,
    val feature: String,val population: Long,val aliases: String) {
    var timeZones: CityTimeZones? = null
    val isGeographicPlace get()=feature in GEOGRAPHIC_FEATURES
    // Island browsing uses an explicit reference offset, not a claim about jurisdiction or historical DST.
    fun zoneAt(instant: Instant): ZoneId = if(isGeographicPlace)ZoneOffset.of(zoneName)
        else timeZones?.offset(zoneName,instant)?:ZoneId.of(zoneName)
    fun isDaylightSaving(instant: Instant)=if(isGeographicPlace)false
        else timeZones?.change(zoneName,instant)?.dst?:zoneAt(instant).rules.isDaylightSavings(instant)
    var mappedDirection: GlobeVector3? = null
    val direction: GlobeVector3 get()=mappedDirection?:GlobeGeography.direction(latitude,longitude)
    val searchKey=CityCatalog.normalize(name+" "+english+" "+aliases+if(isGeographicPlace)" "+region else "")
    private val exactAliases=if(isGeographicPlace)aliases.split('|').map{CityCatalog.normalize(it.trim())}.filter{it.isNotBlank()}.toSet() else emptySet()
    fun exactSearchMatch(key:String)=CityCatalog.normalize(name)==key||CityCatalog.normalize(english)==key||key in exactAliases
    val locationLabel get()=country.ifBlank{region}
    val searchContext get()=if(!isGeographicPlace)country else when(feature){
        "ARCHIPELAGO"->"群岛"
        "ISLAND"->region.removeSuffix("群岛")+" · 岛"
        "CAY"->region.removeSuffix("群岛")+" · 沙洲"
        else->region.removeSuffix("群岛")+" · 礁"
    }
    val albumTitle get()=listOf(name,locationLabel).filter{it.isNotBlank()}.distinct().joinToString(" · ")
    val countryKey=CityCatalog.normalize(country+" "+countryCode+" "+Locale("",countryCode).getDisplayCountry(Locale.ENGLISH))
    fun time(instant: Instant): String=DateTimeFormatter.ofPattern("HH:mm").format(instant.atZone(zoneAt(instant)))
    fun date(instant: Instant): String=DateTimeFormatter.ofPattern("M月d日 EEEE",Locale.SIMPLIFIED_CHINESE).format(instant.atZone(zoneAt(instant)))
    fun offset(instant: Instant): String {
        val seconds=zoneAt(instant).rules.getOffset(instant).totalSeconds
        return "UTC"+(if(seconds>=0) "+" else "−")+"%02d:%02d".format(Locale.ROOT,kotlin.math.abs(seconds)/3600,kotlin.math.abs(seconds)%3600/60)
    }
    fun difference(instant: Instant,local: ZoneId=ZoneId.systemDefault()): String {
        val minutes=(zoneAt(instant).rules.getOffset(instant).totalSeconds-local.rules.getOffset(instant).totalSeconds)/60
        if(minutes==0)return "与本地同一时间"
        val hours=kotlin.math.abs(minutes)/60;val mins=kotlin.math.abs(minutes)%60
        return "比本地"+(if(minutes>0) "快" else "慢")+hours+"小时"+(if(mins>0) " "+mins+"分" else "")
    }
    companion object {
        val GEOGRAPHIC_FEATURES=setOf("ARCHIPELAGO","ISLAND","REEF","CAY")
    }
}
internal class CityCatalog(val cities: List<ClockCity>) {
    private val byId=cities.associateBy{it.id}
    fun city(id: String)=byId[id]
    /** Keep the original city catalogue/pins intact; supplemental points use the actual mesh UV mapping. */
    fun withGeographicPlaces(reader:BufferedReader,surface:CoordinateSurface):CityCatalog {
        val additions=read(reader).cities
        require(additions.map{it.id}.distinct().size==additions.size){"Duplicate geographic place ID"}
        additions.forEach{place->
            require(place.id.matches(Regex("[0-9]+"))&&place.id !in byId){"Geographic place ID collision"}
            require(place.isGeographicPlace&&place.countryCode.isBlank()&&place.country.isBlank()){
                "Supplemental places must describe natural features, not administrative cities"
            }
            require(place.latitude.isFinite()&&place.latitude in -90.0..90.0&&
                place.longitude.isFinite()&&place.longitude in -180.0..180.0){"Invalid geographic coordinate"}
            require(place.name.isNotBlank()&&place.region.isNotBlank())
            ZoneOffset.of(place.zoneName)
            place.mappedDirection=surface.point(place.latitude,place.longitude).normalized()
            require(place.mappedDirection!=GlobeVector3.ZERO)
        }
        return CityCatalog(cities+additions)
    }
    fun bindPinPositions(reader: BufferedReader) {
        reader.useLines { lines->lines.filter{it.isNotBlank()}.forEach { line->
            val f=line.split('\t');require(f.size==4)
            val p=GlobeVector3(f[1].toDouble(),f[2].toDouble(),f[3].toDouble()).normalized()
            require(p!=GlobeVector3.ZERO)
            byId[f[0]]?.mappedDirection=p
        }}
        require(cities.all{it.mappedDirection!=null}) { "Missing geographic pin coordinate" }
    }
    fun search(query: String,limit: Int=40): List<ClockCity> {
        val key=normalize(query.trim())
        if(key.isEmpty())return listOf("1816670","5128581","2643743","1850147","2988507","2147714","1880252")
            .mapNotNull{byId[it]}
        return cities.asSequence().filter { key in it.searchKey || key in it.countryKey }
            .sortedWith(compareBy<ClockCity> { when {
                it.exactSearchMatch(key) ->0
                normalize(it.name).startsWith(key) || normalize(it.english).startsWith(key)->1
                else->2 } }.thenByDescending{it.population}).take(limit).toList()
    }
    companion object {
        fun normalize(text: String)=Normalizer.normalize(text,Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).replace("’", "'")
        fun read(reader: BufferedReader): CityCatalog=CityCatalog(reader.useLines { lines->lines.filter{it.isNotBlank()}.map { line->
            val f=line.split('\t');require(f.size==12) { "Invalid city record" }
            ClockCity(f[0],f[1],f[2],f[3],f[4],f[5],f[6].toDouble(),f[7].toDouble(),f[8],f[9],f[10].toLong(),f[11])
        }.toList() })
    }
}
