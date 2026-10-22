package com.wupengfei.worldclock.content

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** No GRIB parsing in the headset. Immutable runs, verified bytes, atomic pointer. */
internal class TemperatureRepository(private val context:Context,
    endpoint:String=com.wupengfei.worldclock.BuildConfig.TEMPERATURE_BASE_URL,
    storage:File=File(context.filesDir,"temperature"),
    private val transport:((String,Int)->ByteArray)?=null) {
    private val root=storage.apply{mkdirs()}
    private val refreshLock=Mutex()
    private val sample=File(root,"sample")
    val baseUrl:String=endpoint.trimEnd('/')
    private fun hash(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
    private fun safeName(s:String):String {require(s.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]*"))&&!s.contains(".."));return s}
    private fun verified(file:File,size:Int,sha:String){require(size in 1..5_000_000&&file.length()==size.toLong()&&hash(file.readBytes())==sha){"温度资源校验失败"}}
    fun parse(bytes:ByteArray,dir:File):TemperatureManifest {
        val j=JSONObject(bytes.toString(Charsets.UTF_8));require(j.getInt("schema")==1)
        require(j.getString("source")=="NOAA GFS"&&j.getString("variable")=="TMP"&&j.getString("level")=="2 m above ground"&&j.getString("units")=="C")
        require(j.getInt("width")==1440&&j.getInt("height")==721&&j.getInt("missingValue")==-32768)
        require(j.getInt("textureWidth")==1442&&j.getInt("textureHeight")==723)
        require((0 until j.getJSONArray("legendBreaksC").length()).map{j.getJSONArray("legendBreaksC").getInt(it)}==TemperaturePalette.breaks)
        require(j.getDouble("longitudeStart")==-180.0&&j.getDouble("latitudeStart")==90.0&&j.getDouble("stepDegrees")==.25)
        val run=Instant.parse(j.getString("runTime"));val a=j.getJSONArray("frames");require(a.length() in 1..49)
        val frames=(0 until a.length()).map { i->val f=a.getJSONObject(i)
            TemperatureFrame(Instant.parse(f.getString("validTime")),f.getInt("forecastHour"),safeName(f.getString("grid")),safeName(f.getString("texture")),f.getString("gridSha256"),f.getString("textureSha256"),f.getInt("gridBytes"),f.getInt("textureBytes"))
        }
        require(frames.all{it.gridSha.matches(Regex("[a-f0-9]{64}"))&&it.textureSha.matches(Regex("[a-f0-9]{64}"))&&it.gridBytes in 1..5_000_000&&it.textureBytes in 1..5_000_000})
        require(frames.all{it.lead in 0..120&&it.validTime==run.plusSeconds(it.lead*3600L)})
        require(frames.zipWithNext().all{(a,b)->b.validTime.epochSecond-a.validTime.epochSecond==3600L})
        val id=safeName(j.getString("id"));require(id !in setOf("sample","pending","current.txt","current.tmp"))
        return TemperatureManifest(id,run,Instant.parse(j.getString("fetchedAt")),frames,dir,try{Instant.parse(File(dir,"received-at.txt").readText())}catch(_:Exception){null})
    }
    private fun verifyAll(m:TemperatureManifest){m.frames.forEach{verified(File(m.directory,it.grid),it.gridBytes,it.gridSha);verified(File(m.directory,it.texture),it.textureBytes,it.textureSha)}}
    suspend fun initial():TemperatureManifest=withContext(Dispatchers.IO){
        val pointer=File(root,"current.txt")
        if(pointer.exists())try {val d=File(root,safeName(pointer.readText()));val m=parse(File(d,"manifest.json").readBytes(),d);verifyAll(m);return@withContext m}catch(e:CancellationException){throw e}catch(_:Exception){}
        sample.mkdirs()
        val bytes=context.assets.open("temperature/manifest.json").use{it.readBytes()}
        val bundled=parse(bytes,sample)
        for(f in bundled.frames){for((name,size,sha) in listOf(Triple(f.grid,f.gridBytes,f.gridSha),Triple(f.texture,f.textureBytes,f.textureSha))){
            ensureActive();val dest=File(sample,name)
            val good=try{verified(dest,size,sha);true}catch(_:Exception){false}
            if(!good)context.assets.open("temperature/"+name).use{input->dest.outputStream().use{input.copyTo(it)}}
        }}
        verifyAll(bundled)
        val oldId=try{JSONObject(File(sample,"manifest.json").readText()).getString("id")}catch(_:Exception){null}
        if(oldId!=bundled.id||!File(sample,"received-at.txt").exists())File(sample,"received-at.txt").writeText(ClockTimeSource.read().instant.toString())
        File(sample,"manifest.json").writeBytes(bytes)
        parse(bytes,sample)
    }
    private fun download(url:String,max:Int):ByteArray {
        transport?.let{return it(url,max).also{b->require(b.size<=max)}}
        val u=URL(url);require(u.protocol=="https"||(com.wupengfei.worldclock.BuildConfig.DEBUG&&u.protocol=="http"&&u.host in listOf("127.0.0.1","localhost","10.0.2.2")))
        val conn=u.openConnection() as HttpURLConnection
        conn.connectTimeout=12000;conn.readTimeout=25000;conn.instanceFollowRedirects=false
        try{require(conn.responseCode==200);require(conn.contentLengthLong<=max)
            return conn.inputStream.use{val b=it.readNBytes(max+1);require(b.size<=max);b}
        }finally{conn.disconnect()}
    }
    suspend fun refresh(previous:TemperatureManifest):TemperatureManifest=refreshLock.withLock { withContext(Dispatchers.IO){
        if(baseUrl.isBlank())return@withContext previous
        val pointer=JSONObject(download(baseUrl+"/latest.json",16384).toString(Charsets.UTF_8))
        val rel=pointer.getString("manifest");require(rel.matches(Regex("[A-Za-z0-9_-]+/manifest\\.json")))
        val bytes=download(baseUrl+"/"+rel,250000);require(hash(bytes)==pointer.getString("sha256"))
        val parsed=parse(bytes,root)
        if(parsed.runTime<previous.runTime)return@withContext previous
        if(parsed.id==previous.id)return@withContext previous
        val stage=File(root,"pending");stage.deleteRecursively();stage.mkdirs()
        try {
            val m=parse(bytes,stage);val url=baseUrl+"/"+rel.substringBeforeLast('/')
            for(f in m.frames){ensureActive()
                for((name,size,sha) in listOf(Triple(f.grid,f.gridBytes,f.gridSha),Triple(f.texture,f.textureBytes,f.textureSha))){
                    require(size in 1..5_000_000)
                    // Sliding the 24h window reuses unchanged frames from the same model run.
                    val cached=File(previous.directory,name)
                    val reusable=try{verified(cached,size,sha);true}catch(_:Exception){false}
                    if(reusable)cached.copyTo(File(stage,name),overwrite=true)
                    else{val b=download(url+"/"+name,size);require(b.size==size&&hash(b)==sha);File(stage,name).writeBytes(b)}
                }
            }
            ensureActive();verifyAll(m);File(stage,"manifest.json").writeBytes(bytes)
            File(stage,"received-at.txt").writeText(ClockTimeSource.read().instant.toString())
            val target=File(root,m.id)
            if(target.exists()){
                val good=try{val existing=File(target,"manifest.json").readBytes();require(existing.contentEquals(bytes));verifyAll(parse(existing,target));true}catch(_:Exception){false}
                if(!good){check(target!=previous.directory);check(target.deleteRecursively())}
            }
            ensureActive()
            if(!target.exists())check(stage.renameTo(target)) else stage.deleteRecursively()
            val tmp=File(root,"current.tmp");tmp.writeText(m.id);check(tmp.renameTo(File(root,"current.txt")))
            root.listFiles()?.filter{it.isDirectory&&it.name !in setOf("sample",m.id,previous.directory.name)}?.forEach{it.deleteRecursively()}
            parse(bytes,target)
        }catch(e:Exception){stage.deleteRecursively();throw e}
    }}
    suspend fun frame(m:TemperatureManifest,index:Int):Pair<TemperatureFrame,TemperatureGrid> = withContext(Dispatchers.IO){
        val f=m.frames[index];verified(File(m.directory,f.grid),f.gridBytes,f.gridSha);verified(File(m.directory,f.texture),f.textureBytes,f.textureSha)
        f to TemperatureGrid.read(File(m.directory,f.grid))
    }
}
