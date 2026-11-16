package com.wupengfei.worldclock.content

import java.io.BufferedReader
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class ClockCity(val id: String,val name: String,val english: String,val countryCode: String,
    val country: String,val region: String,val latitude: Double,val longitude: Double,val zoneName: String,
    val feature: String,val population: Long,val aliases: String) {
    var timeZones: CityTimeZones? = null
    fun zoneAt(instant: Instant): ZoneId = timeZones?.offset(zoneName,instant)?:ZoneId.of(zoneName)
    fun isDaylightSaving(instant: Instant)=timeZones?.change(zoneName,instant)?.dst?:zoneAt(instant).rules.isDaylightSavings(instant)
    var mappedDirection: GlobeVector3? = null
    val direction: GlobeVector3 get()=mappedDirection?:GlobeGeography.direction(latitude,longitude)
    val searchKey=CityCatalog.normalize(name+" "+english+" "+aliases)
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
}
internal class CityCatalog(val cities: List<ClockCity>) {
    private val byId=cities.associateBy{it.id}
    fun city(id: String)=byId[id]
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
                normalize(it.name)==key || normalize(it.english)==key ->0
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
