package com.wupengfei.worldclock.content

import java.io.BufferedReader
import java.time.Instant
import java.time.ZoneOffset

/** Bundled IANA tzdata 2026.5, 1900..2100. Independent of the headset's older timezone database. */
internal class CityTimeZones(private val zones: Map<String,List<Change>>) {
    data class Change(val at: Long,val seconds: Int,val dst: Boolean)
    fun change(zone: String,instant: Instant):Change {
        val list=requireNotNull(zones[zone]) { "Missing timezone: "+zone }
        var low=0;var high=list.size
        while(low<high){val mid=(low+high) ushr 1;if(list[mid].at<=instant.epochSecond)low=mid+1 else high=mid}
        return list[(low-1).coerceAtLeast(0)]
    }
    fun offset(zone: String,instant: Instant)=ZoneOffset.ofTotalSeconds(change(zone,instant).seconds)
    companion object {
        fun read(reader: BufferedReader)=CityTimeZones(reader.useLines{lines->lines.filter{it.isNotBlank()}.associate{line->
            val parts=line.split('\t');require(parts.size==2)
            parts[0] to parts[1].split(';').map{row->val f=row.split(',');Change(f[0].toLong(),f[1].toInt(),f[2]=="1")}
        }})
    }
}
