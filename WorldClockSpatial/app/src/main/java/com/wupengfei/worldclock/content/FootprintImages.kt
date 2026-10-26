package com.wupengfei.worldclock.content

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

internal class FootprintImages(private val context:Context,val store:FootprintStore) {
    // Shared thumbnail cache, one hero at a time. No 8K decode or GPU assets are introduced.
    private val thumbnails=object:LruCache<String,Bitmap>(8*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
    private val decoding=Mutex()
    suspend fun thumbnail(photo:FootprintPhoto):Bitmap?=withContext(Dispatchers.IO){decoding.withLock{
        thumbnails.get(photo.id)?:BitmapFactory.decodeFile(store.photoFile(photo.id,"thumb.jpg").path)?.also{thumbnails.put(photo.id,it)}
    }}
    suspend fun preview(photo:FootprintPhoto):Bitmap?=withContext(Dispatchers.IO){decoding.withLock{
        BitmapFactory.decodeFile(store.photoFile(photo.id,"preview.jpg").path)
    }}
    fun clear(){thumbnails.evictAll()} // Composed images may still reference a bitmap; never recycle them in place.

    suspend fun import(uri:Uri,cityId:String):List<FootprintAlbum> = withContext(Dispatchers.IO) {
        require(uri.scheme=="content"){"请选择系统相册或文件中的照片"}
        require(store.read().firstOrNull{it.cityId==cityId}?.photos?.size?.let{it<FootprintStore.MAX_PHOTOS}==true){"这个地点不能继续添加照片"}
        val staging=File(store.directory,"pending-${UUID.randomUUID()}")
        check(staging.mkdirs()){"无法创建照片目录"}
        try {
            require(store.directory.usableSpace>128L*1024*1024){"本机空间不足，请先清理存储"}
            val original=File(staging,"original");val digest=MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                original.outputStream().use { output ->
                    val buffer=ByteArray(65536);var total=0L
                    while(true){
                        currentCoroutineContext().ensureActive()
                        val count=input.read(buffer);if(count<0)break
                        total+=count;require(total<=FootprintStore.MAX_FILE_BYTES){"单张照片不能超过 64 MB"}
                        digest.update(buffer,0,count);output.write(buffer,0,count)
                    }
                    require(total>0){"照片文件为空"};output.fd.sync()
                }
            }?:error("无法读取所选照片")
            val id=digest.digest().joinToString(""){"%02x".format(it)}
            if(store.read().first{it.cityId==cityId}.photos.any{it.id==id})return@withContext store.read()
            // ImageDecoder applies EXIF orientation and samples during decode, before allocation.
            val preview=ImageDecoder.decodeBitmap(ImageDecoder.createSource(original)){decoder,info,_->
                require(info.size.width.toLong()*info.size.height<=100_000_000L){"照片尺寸过大"}
                val (w,h)=FootprintStore.sampledSize(info.size.width,info.size.height,1600)
                decoder.setTargetSize(w,h);decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
            }
            try {
                File(staging,"preview.jpg").outputStream().use{check(preview.compress(Bitmap.CompressFormat.JPEG,92,it))}
                val(w,h)=FootprintStore.sampledSize(preview.width,preview.height,384)
                val thumb=Bitmap.createScaledBitmap(preview,w,h,true)
                try{File(staging,"thumb.jpg").outputStream().use{check(thumb.compress(Bitmap.CompressFormat.JPEG,88,it))}}
                finally{if(thumb!==preview)thumb.recycle()}
            }finally{preview.recycle()}
            val date=runCatching {
                val raw=ExifInterface(original.path).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?:return@runCatching null
                LocalDate.parse(raw.take(10),DateTimeFormatter.ofPattern("yyyy:MM:dd")).toString()
            }.getOrNull()
            currentCoroutineContext().ensureActive()
            synchronized(store){
                val destination=store.photoFile(id,"original").parentFile!!
                destination.parentFile!!.mkdirs()
                val created=!destination.exists()
                if(created)check(staging.renameTo(destination)){"无法保存照片"}
                try{store.append(cityId,FootprintPhoto(id,System.currentTimeMillis(),date))}
                catch(e:Exception){
                    // A failed index commit must not leave a new unowned photo behind.
                    if(created&&destination.parentFile==File(store.directory,"photos")&&FootprintStore.validId(destination.name))destination.deleteRecursively()
                    throw e
                }
            }
        } finally {
            // Only this import's UUID staging folder, never provider originals or saved photos.
            if(staging.parentFile==store.directory&&staging.name.startsWith("pending-"))staging.deleteRecursively()
        }
    }
}
