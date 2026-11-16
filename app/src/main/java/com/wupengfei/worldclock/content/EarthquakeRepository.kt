package com.wupengfei.worldclock.content

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

internal object QuakeJson {
    fun parse(raw:String,period:QuakePeriod,fetched:Instant):QuakeCatalog {
        val json=JSONObject(raw);require(json.getString("type")=="FeatureCollection")
        val meta=json.getJSONObject("metadata");require(meta.optInt("status",200)==200)
        val generated=Instant.ofEpochMilli(meta.getLong("generated"));require(generated.toEpochMilli()>0)
        val features=json.getJSONArray("features");require(features.length()<=20000){"目录超出本版容量"}
        val events=mutableListOf<QuakeEvent>();val deleted=mutableMapOf<String,Long>();var skipped=0
        for(i in 0 until features.length())try {
            val f=features.getJSONObject(i);val p=f.getJSONObject("properties")
            if(p.optString("status")=="deleted"){
                val updated=p.optLong("updated",Long.MAX_VALUE)
                (p.optString("ids").split(',')+f.optString("id")).filter{it.isNotBlank()}.forEach{id->deleted[id]=maxOf(deleted[id]?:Long.MIN_VALUE,updated)}
                continue
            }
            if(p.optString("type")!="earthquake")continue
            val g=f.getJSONObject("geometry");require(g.getString("type")=="Point")
            val a=g.getJSONArray("coordinates")
            val lon=a.getDouble(0);val lat=a.getDouble(1)
            require(lat.isFinite()&&lat in -90.0..90.0&&lon.isFinite()&&lon in -180.0..180.0)
            val id=f.getString("id");require(id.isNotBlank()&&id.length<=128)
            fun number(key:String)=if(p.isNull(key))null else p.getDouble(key).takeIf{it.isFinite()}
            val occurred=Instant.ofEpochMilli(p.getLong("time"));require(occurred.toEpochMilli()>0)
            events.add(QuakeEvent(id,p.optString("ids").split(',').filter{it.isNotBlank()}.toSet(),
                p.optString("place").takeIf{it.isNotBlank()&&it!="null"}?:"地点暂无",
                occurred,if(p.isNull("updated"))null else Instant.ofEpochMilli(p.getLong("updated")),
                lat,SolarPosition.wrap(lon),if(a.isNull(2))null else a.getDouble(2).takeIf{it.isFinite()},
                number("mag"),p.optString("magType").takeIf{it.isNotBlank()&&it!="null"},p.optString("url")))
        }catch(_:Exception){skipped++}
        return QuakeCatalog(period,generated,fetched,QuakeRules.deduplicate(events.filter{e->(e.aliases+e.id).none{id->(deleted[id]?:Long.MIN_VALUE)>=(e.updatedAt?:e.occurredAt).toEpochMilli()}}),
            skipped>0||meta.optInt("count",features.length())!=features.length(),skipped)
    }
}
/** Server-side M4 floor reduces transfer/storage, unlike hiding small records after download. */
internal object QuakeQuery {
    fun endpoint(period:QuakePeriod,end:Instant):String {
        val start=end.minusSeconds(period.days*86400)
        return "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&eventtype=earthquake"+
            "&minmagnitude=4&starttime=$start&endtime=$end&orderby=time-asc"
    }
    fun admitted(catalog:QuakeCatalog)=catalog.copy(events=catalog.events.filter{(it.magnitude?:Double.NEGATIVE_INFINITY)>=QuakeRules.CATALOG_MINIMUM})
}
internal class EarthquakeRepository(context:Context) {
    private val directory=File(context.filesDir,"earthquake-v3-m4").apply{mkdirs()}
    private val oldDirectories=listOf("earthquake-v2-all","earthquake-v1").map{File(context.filesDir,it)}
    private fun file(period:QuakePeriod)=AtomicFile(File(directory,period.feed+".json"))
    suspend fun cached(period:QuakePeriod):QuakeCatalog?=withContext(Dispatchers.IO) {
        // Preserve a usable older snapshot offline, but never admit its sub-M4 records.
        val candidates=listOf(file(period))+oldDirectories.map{AtomicFile(File(it,period.feed+".json"))}
        candidates.firstNotNullOfOrNull{cache->runCatching {
            val saved=JSONObject(cache.openRead().bufferedReader().use{it.readText()})
            val parsed=QuakeJson.parse(saved.getString("feed"),period,Instant.parse(saved.getString("fetchedAt")))
            val end=saved.optString("windowEnd").takeIf{it.isNotBlank()}?.let(Instant::parse)?:parsed.end
            QuakeQuery.admitted(parsed.copy(windowEnd=end)).takeIf{!it.partial}
        }.getOrNull()}
    }
    suspend fun refresh(period:QuakePeriod):QuakeCatalog=withContext(Dispatchers.IO) {
        val end=ClockTimeSource.read().instant.truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
        val connection=URL(QuakeQuery.endpoint(period,end)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=15000;connection.readTimeout=20000
            connection.setRequestProperty("Accept","application/geo+json, application/json")
            connection.setRequestProperty("User-Agent","EarthObserver-PICO/0.84")
            val code=connection.responseCode
            require(code==200||code==204){"USGS HTTP $code"}
            val fetched=ClockTimeSource.read().instant
            val raw=if(code==204)JSONObject().put("type","FeatureCollection")
                .put("metadata",JSONObject().put("generated",fetched.toEpochMilli()).put("status",200).put("count",0))
                .put("features",org.json.JSONArray()).toString() else connection.inputStream.use{input->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384)
                while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break
                    require(output.size()+n<=16*1024*1024){"目录下载超过限制"};output.write(buffer,0,n)}
                output.toString(Charsets.UTF_8.name())
            }
            val catalog=QuakeQuery.admitted(QuakeJson.parse(raw,period,fetched).copy(windowEnd=end))
            currentCoroutineContext().ensureActive();require(!catalog.partial){"目录校验未通过，保留上次数据"}
            val cache=file(period);val stream=cache.startWrite()
            try {stream.write(JSONObject().put("fetchedAt",fetched.toString()).put("windowEnd",end.toString()).put("feed",raw).toString().toByteArray());cache.finishWrite(stream)}
            catch(e:Throwable){cache.failWrite(stream);throw e}
            catalog
        }finally{connection.disconnect()}
    }
}
