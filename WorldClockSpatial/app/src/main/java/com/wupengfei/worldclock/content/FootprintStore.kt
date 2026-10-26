package com.wupengfei.worldclock.content

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class FootprintPhoto(val id:String,val addedAt:Long,val takenDate:String?=null)
internal data class FootprintAlbum(val cityId:String,val photos:List<FootprintPhoto> = emptyList())

/** Independent of world_clock preferences. Stores only local filenames, never provider URIs. */
internal class FootprintStore(val directory:File) {
    companion object {
        const val MAX_CITIES=16
        const val MAX_PHOTOS=200
        const val MAX_BATCH=20
        const val MAX_FILE_BYTES=64L*1024*1024
        fun validId(id:String)=id.matches(Regex("[a-f0-9]{64}"))
        fun sampledSize(width:Int,height:Int,maxEdge:Int):Pair<Int,Int> {
            require(width>0&&height>0&&maxEdge>0)
            val scale=minOf(1.0,maxEdge.toDouble()/maxOf(width,height))
            return maxOf(1,(width*scale).toInt()) to maxOf(1,(height*scale).toInt())
        }
    }
    private val index get()=File(directory,"albums.json")
    @Synchronized fun read():List<FootprintAlbum> {
        if(!index.exists())return emptyList()
        val root=JSONObject(index.readText());require(root.getInt("version")==1){"Unsupported album format"}
        val array=root.getJSONArray("albums")
        val albums=(0 until array.length()).map { i ->
            val a=array.getJSONObject(i);val p=a.getJSONArray("photos")
            FootprintAlbum(a.getString("cityId"),(0 until p.length()).map { j ->
                val f=p.getJSONObject(j);val id=f.getString("id");require(validId(id))
                FootprintPhoto(id,f.getLong("addedAt"),f.optString("takenDate").takeIf{it.isNotBlank()})
            }.distinctBy{it.id})
        }
        require(albums.map{it.cityId}.distinct().size==albums.size)
        require(albums.size<=MAX_CITIES&&albums.all{it.cityId.matches(Regex("[0-9]+"))&&it.photos.size<=MAX_PHOTOS})
        return albums
    }
    @Synchronized private fun save(albums:List<FootprintAlbum>) {
        check(directory.isDirectory||directory.mkdirs())
        val array=JSONArray()
        albums.forEach { a ->
            val photos=JSONArray();a.photos.forEach { p ->
                photos.put(JSONObject().put("id",p.id).put("addedAt",p.addedAt).put("takenDate",p.takenDate?:""))
            }
            array.put(JSONObject().put("cityId",a.cityId).put("photos",photos))
        }
        val temp=File(directory,"albums.pending")
        temp.outputStream().use { out -> out.write(JSONObject().put("version",1).put("albums",array).toString().toByteArray());out.fd.sync() }
        // Both files are in the same private directory; no intermediate empty index.
        Files.move(temp.toPath(),index.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }
    @Synchronized fun addCity(id:String):List<FootprintAlbum> {
        require(id.matches(Regex("[0-9]+")))
        val old=read();if(old.any{it.cityId==id})return old
        require(old.size<MAX_CITIES){"最多保存 $MAX_CITIES 个足迹地点"}
        return (old+FootprintAlbum(id)).also(::save)
    }
    @Synchronized fun append(cityId:String,photo:FootprintPhoto):List<FootprintAlbum> {
        require(validId(photo.id))
        val old=read();val album=old.firstOrNull{it.cityId==cityId}?:error("足迹地点已不存在")
        if(album.photos.any{it.id==photo.id})return old
        require(album.photos.size<MAX_PHOTOS){"每个地点最多保存 $MAX_PHOTOS 张照片"}
        require(listOf("original","preview.jpg","thumb.jpg").all{photoFile(photo.id,it).isFile}){"Photo incomplete"}
        return old.map{if(it.cityId==cityId)it.copy(photos=it.photos+photo)else it}.also(::save)
    }
    @Synchronized fun removeEmptyCity(id:String):List<FootprintAlbum> {
        val old=read()
        require(old.firstOrNull{it.cityId==id}?.photos.isNullOrEmpty()){"已有照片的地点暂不支持移除"}
        return old.filterNot{it.cityId==id}.also(::save)
    }
    fun photoFile(id:String,name:String):File {
        require(validId(id));require(name in setOf("original","preview.jpg","thumb.jpg"))
        return File(File(File(directory,"photos"),id),name)
    }
}
