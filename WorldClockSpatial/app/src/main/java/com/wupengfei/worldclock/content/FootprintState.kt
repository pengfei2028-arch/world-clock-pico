package com.wupengfei.worldclock.content

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import java.io.File

@Stable internal class FootprintState {
    var enabled by mutableStateOf(false)
    var initialized by mutableStateOf(false);private set
    var albums by mutableStateOf<List<FootprintAlbum>>(emptyList());private set
    var selectedCityId by mutableStateOf<String?>(null);private set
    var selectedPhotoId by mutableStateOf<String?>(null);private set
    var albumOpen by mutableStateOf(false)
    var placesOpen by mutableStateOf(false)
    var placesSearching by mutableStateOf(false)
    var viewerOpen by mutableStateOf(false);private set
    var query by mutableStateOf("")
    var busy by mutableStateOf(false);private set
    var pickingCityId by mutableStateOf<String?>(null);private set
    var progress by mutableStateOf("");private set
    var error by mutableStateOf("");private set
    var errorCityId by mutableStateOf<String?>(null);private set
    var focusRequest=0;private set
    var images by mutableStateOf<FootprintImages?>(null);private set
    val pinAlpha=mutableStateListOf<Float>().apply{repeat(FootprintStore.MAX_CITIES){add(0f)}}
    var albumAlpha by mutableFloatStateOf(0f)
    private var scope:CoroutineScope?=null
    private var loadJob:Job?=null
    private fun scope()=scope?:CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate).also{scope=it}
    val album get()=albums.firstOrNull{it.cityId==selectedCityId}
    val photo get()=album?.photos?.firstOrNull{it.id==selectedPhotoId}?:album?.photos?.firstOrNull()
    fun city(catalog:CityCatalog?)=selectedCityId?.let{catalog?.city(it)}
    fun cities(catalog:CityCatalog?)=albums.mapNotNull{catalog?.city(it.cityId)}
    internal fun accept(next:List<FootprintAlbum>){
        albums=next
        if(selectedCityId!=null&&album==null){selectedCityId=null;selectedPhotoId=null;albumOpen=false;viewerOpen=false}
        else if(album?.photos?.none{it.id==selectedPhotoId}!=false)selectedPhotoId=album?.photos?.firstOrNull()?.id
        if(photo==null)viewerOpen=false
    }
    fun initialize(context:Context) {
        if(initialized||busy)return
        busy=true;error=""
        val app=context.applicationContext
        val media=images?:FootprintImages(app,FootprintStore(File(app.noBackupFilesDir,"footprints"))).also{images=it}
        loadJob=scope().launch {
            try{accept(withContext(Dispatchers.IO){media.store.read()});initialized=true}
            catch(e:CancellationException){throw e}catch(e:Exception){error="相册暂时无法读取，请重试";android.util.Log.e("WorldClockFootprints","Read albums",e)}
            finally{busy=false}
        }
    }
    fun add(city:ClockCity) {
        if(!initialized||busy)return
        busy=true;error=""
        scope().launch {
            try{accept(withContext(Dispatchers.IO){checkNotNull(images).store.addCity(city.id)});placesOpen=false;query="";selectCity(city.id)}
            catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"添加地点失败，请重试"}
            finally{busy=false}
        }
    }
    fun selectCity(id:String) {
        if(albums.none{it.cityId==id})return
        selectedCityId=id;selectedPhotoId=album?.photos?.firstOrNull()?.id;albumOpen=true;viewerOpen=false;albumAlpha=0f;placesOpen=false;placesSearching=false;query="";focusRequest++
    }
    fun selectPhoto(id:String){if(album?.photos?.any{it.id==id}==true)selectedPhotoId=id}
    fun openPhoto(id:String){if(albumOpen&&album?.photos?.any{it.id==id}==true){selectPhoto(id);if(!viewerOpen)albumAlpha=0f;viewerOpen=true}}
    fun closeViewer(){if(viewerOpen)albumAlpha=0f;viewerOpen=false}
    fun closeAlbum(){albumOpen=false;viewerOpen=false}
    fun openPlaces(){closeAlbum();placesOpen=true;placesSearching=false;query=""}
    fun searchPlaces(){placesSearching=true;query=""}
    fun closeSearch(){placesSearching=false;query=""}
    fun dismiss(){closeAlbum();placesOpen=false;closeSearch()}
    fun removeEmptyCity(id:String){
        if(!initialized||busy||pickingCityId!=null)return
        busy=true;error="";errorCityId=null
        scope().launch {
            try{accept(withContext(Dispatchers.IO){checkNotNull(images).store.removeEmptyCity(id)})}
            catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"移除地点失败，请重试"}
            finally{busy=false}
        }
    }
    fun disable(){enabled=false;dismiss();images?.clear()}
    fun beginPicker():String? {
        val city=selectedCityId?:return null
        if(busy||!initialized||pickingCityId!=null)return null
        if((album?.photos?.size?:0)>=FootprintStore.MAX_PHOTOS){fail("每个地点最多保存 ${FootprintStore.MAX_PHOTOS} 张照片",city);return null}
        pickingCityId=city;error="";errorCityId=null;progress="正在选择照片…";return city
    }
    fun fail(message:String,city:String?=pickingCityId){pickingCityId=null;error=message;errorCityId=city;progress=""}
    fun restorePicker(cityId:String){if(cityId.matches(Regex("[0-9]+"))){pickingCityId=cityId;selectedCityId=cityId;enabled=true;albumOpen=true}}
    fun pickerResult(uris:List<Uri>) {
        val city=pickingCityId?:return
        pickingCityId=null;progress=""
        if(uris.isEmpty())return
        val chosen=uris.distinct().take(FootprintStore.MAX_BATCH)
        busy=true;error="";errorCityId=null
        scope().launch {
            var failed=0;var reason="";var latest:FootprintPhoto?=null
            try{
                loadJob?.join();busy=true
                check(initialized){"相册未能读取，请重新添加"}
                chosen.forEachIndexed { i,uri ->
                    progress="正在添加 ${i+1} / ${chosen.size}"
                    try{
                        val before=albums.firstOrNull{it.cityId==city}?.photos.orEmpty().map{it.id}.toSet()
                        accept(checkNotNull(images).import(uri,city))
                        albums.firstOrNull{it.cityId==city}?.photos?.lastOrNull{it.id !in before}?.let{latest=it}
                    }catch(e:CancellationException){throw e}catch(e:Exception){failed++;reason=e.message?:"无法读取照片";android.util.Log.w("WorldClockFootprints","Import failed",e)}
                }
                // Import target is captured at picker launch; switching cities cannot redirect it.
                if(selectedCityId==city&&enabled){latest?.let{selectedPhotoId=it.id};albumOpen=true;viewerOpen=false}
                if(failed>0){error="$failed 张照片未添加：$reason";errorCityId=city}
                else if(uris.size>FootprintStore.MAX_BATCH){error="每次最多添加 ${FootprintStore.MAX_BATCH} 张，其余可再次添加";errorCityId=city}
                android.util.Log.i("WorldClockFootprints","V96 import complete: saved=${chosen.size-failed} failed=$failed localOnly=true")
            }catch(e:CancellationException){throw e}catch(e:Exception){error="添加失败，请重试";errorCityId=city;android.util.Log.e("WorldClockFootprints","Import unavailable",e)}
            finally{busy=false;progress=""}
        }
    }
    fun close(){scope?.cancel();scope=null;images?.clear();pickingCityId=null;busy=false;progress=""}
}
