package com.wupengfei.worldclock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wupengfei.worldclock.content.*
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class TemperatureCacheTest {
 @Test fun localHttpDownloadAndReload():Unit=runBlocking {
  org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("localEndpoint")=="true")
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  val storage=File(c.cacheDir,"temperature-http-test");storage.deleteRecursively()
  try{
   val repo=TemperatureRepository(c,"http://127.0.0.1:8765",storage)
   val bytes=c.assets.open("temperature/manifest.json").use{it.readBytes()}
   val previous=repo.parse(bytes,File(storage,"previous")).copy(id="old-cache")
   val next=repo.refresh(previous);assertNotEquals(previous.id,next.id);assertTrue(next.frames.size>=25)
   assertEquals(next.id,repo.initial().id);assertNotNull(repo.frame(next,0).second.sample(39.9,116.4))
   android.util.Log.i("TemperatureAcceptance","HTTP_DOWNLOAD_PASS; frames="+next.frames.size+"; receivedAt="+next.receivedAt)
  }finally{storage.deleteRecursively()}
 }
 @Test fun validatedUpdateOfflineAndCorruptionFallback():Unit=runBlocking {
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  val storage=File(c.cacheDir,"temperature-cache-test");storage.deleteRecursively();storage.mkdirs()
  try {
   val bundled=c.assets.open("temperature/manifest.json").use{it.readBytes()}
   val j=JSONObject(bundled.toString(Charsets.UTF_8));val frame=j.getJSONArray("frames").getJSONObject(0)
   j.put("frames",JSONArray().put(frame));j.put("id","test-package-one")
   val mbytes=j.toString().toByteArray()
   fun sha(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
   val pointer=JSONObject().put("manifest","test-package-one/manifest.json").put("sha256",sha(mbytes)).toString().toByteArray()
   var fail=false;var corrupt=false;var assetRequests=0
   val repo=TemperatureRepository(c,"https://test.invalid",storage){url,_->
    if(fail)throw IOException("Simulated offline")
    when {
     url.endsWith("latest.json")->pointer
     url.endsWith("manifest.json")->mbytes
     else->{assetRequests++;c.assets.open("temperature/"+url.substringAfterLast('/')).use{it.readBytes()}.let{if(corrupt)it.copyOf(12)else it}}
    }
   }
   val previous=repo.parse(bundled,File(storage,"previous"))
   // Full validation must finish before the active pointer can change.
   corrupt=true
   try{repo.refresh(previous);fail("Corrupt download accepted")}catch(_:IllegalArgumentException){}
   assertFalse(File(storage,"current.txt").exists())
   corrupt=false;val loaded=repo.refresh(previous)
   assertEquals("test-package-one",File(storage,"current.txt").readText());assertNotNull(loaded.receivedAt)
   val grid=repo.frame(loaded,0).second;assertNotNull(grid.sample(39.9,116.4))
   val downloads=assetRequests
   assertEquals(loaded.id,repo.refresh(loaded.copy(id="older-manifest")).id)
   assertEquals(downloads,assetRequests)
   fail=true
   try{repo.refresh(loaded);fail("Offline request accepted")}catch(_:IOException){}
   assertEquals("test-package-one",File(storage,"current.txt").readText())
   assertEquals(loaded.id,repo.initial().id)
   // Corrupt persisted content falls back to the authentic bundled package on restart.
   File(loaded.directory,loaded.frames.first().grid).writeBytes(byteArrayOf(1,2))
   assertEquals(previous.id,repo.initial().id)
   assertTrue(loaded.stale(Instant.parse("2030-01-01T00:00:00Z")))
  }finally{storage.deleteRecursively()}
 }
}
