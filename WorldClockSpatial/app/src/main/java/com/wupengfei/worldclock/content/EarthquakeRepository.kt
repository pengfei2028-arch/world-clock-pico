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
internal class EarthquakeRepository(context:Context) {
    private val directory=File(context.filesDir,"earthquake-v1").apply{mkdirs()}
    private fun file(period:QuakePeriod)=AtomicFile(File(directory,period.feed+".json"))
    suspend fun cached(period:QuakePeriod):QuakeCatalog?=withContext(Dispatchers.IO) {
        runCatching {val saved=JSONObject(file(period).openRead().bufferedReader().use{it.readText()})
            QuakeJson.parse(saved.getString("feed"),period,Instant.parse(saved.getString("fetchedAt"))).takeIf{!it.partial}
        }.getOrNull()
    }
    suspend fun refresh(period:QuakePeriod):QuakeCatalog=withContext(Dispatchers.IO) {
        // >=2.5 includes all records needed by the approved >=4 / >=5 / >=7 filters.
        val endpoint="https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_"+period.feed+".geojson"
        val connection=URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=15000;connection.readTimeout=20000
            connection.setRequestProperty("Accept","application/geo+json, application/json")
            connection.setRequestProperty("User-Agent","EarthObserver-PICO/0.73")
            require(connection.responseCode==200){"USGS HTTP "+connection.responseCode}
            val bytes=connection.inputStream.use{input->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384)
                while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break
                    require(output.size()+n<=16*1024*1024){"目录下载超过限制"};output.write(buffer,0,n)}
                output.toByteArray()
            }
            val raw=bytes.toString(Charsets.UTF_8);val fetched=ClockTimeSource.read().instant
            val catalog=QuakeJson.parse(raw,period,fetched);currentCoroutineContext().ensureActive()
            require(!catalog.partial){"目录校验未通过，保留上次数据"}
            val cache=file(period);val stream=cache.startWrite()
            try {stream.write(JSONObject().put("fetchedAt",fetched.toString()).put("feed",raw).toString().toByteArray());cache.finishWrite(stream)}
            catch(e:Throwable){cache.failWrite(stream);throw e}
            catalog
        }finally{connection.disconnect()}
    }
}
