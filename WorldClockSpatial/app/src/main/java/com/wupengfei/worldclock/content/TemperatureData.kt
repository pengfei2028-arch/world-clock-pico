package com.wupengfei.worldclock.content

import java.time.Instant
import kotlin.math.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

internal enum class EarthMode { WORLD_TIME, TEMPERATURE }
internal data class TemperatureFrame(val validTime:Instant,val lead:Int,val grid:String,val texture:String,
    val gridSha:String,val textureSha:String,val gridBytes:Int,val textureBytes:Int)
internal data class TemperatureManifest(val id:String,val runTime:Instant,val fetchedAt:Instant,val frames:List<TemperatureFrame>,val directory:java.io.File,val receivedAt:Instant?=null) {
    fun nearest(now:Instant)=frames.indices.minByOrNull { abs(frames[it].validTime.epochSecond-now.epochSecond) }?:0
    fun stale(now:Instant)=now.isAfter(frames.last().validTime)||now.epochSecond-runTime.epochSecond>18*3600
}
internal class TemperatureGrid(val width:Int,val height:Int,private val values:ShortArray){
    init{require(width>=2&&height>=2&&values.size==width*height)}
    fun sample(latitude:Double,longitude:Double):Double? {
        if(!latitude.isFinite()||!longitude.isFinite()||latitude !in -90.0..90.0)return null
        val x=((longitude+180)%360+360)%360/360*width
        val y=(90-latitude)/180*(height-1)
        val x0=floor(x).toInt()%width;val x1=(x0+1)%width;val y0=floor(y).toInt().coerceIn(0,height-1);val y1=min(y0+1,height-1)
        val fx=x-floor(x);val fy=y-y0
        var sum=0.0
        for((i,w) in listOf(y0*width+x0 to (1-fx)*(1-fy),y0*width+x1 to fx*(1-fy),y1*width+x0 to (1-fx)*fy,y1*width+x1 to fx*fy)){
            if(w<=1e-10)continue
            if(values[i]==Short.MIN_VALUE)return null
            sum+=values[i]*w/100.0
        }
        return sum
    }
    companion object {
        fun read(file:java.io.File):TemperatureGrid {
            val bytes=GZIPInputStream(file.inputStream()).use { input ->
                val expected=12+1440*721*2
                val result=input.readNBytes(expected+1);require(result.size==expected);result
            }
            val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(String(bytes,0,4,Charsets.US_ASCII)=="WCT1");b.position(4)
            val w=b.int;val h=b.int;require(w==1440&&h==721)
            val values=ShortArray(w*h){b.short};return TemperatureGrid(w,h,values)
        }
    }
}
internal object TemperaturePalette {
    val breaks=listOf(-30,-20,-10,0,10,20,30,40)
    val colors=listOf(0xFF173D8AL,0xFF2462AEL,0xFF2D97CAL,0xFF76C5D3L,0xFFC7DDCEL,0xFFF2D583L,0xFFF3AB58L,0xFFDD653CL,0xFFA92C32L)
    fun band(c:Double)=breaks.count{c>=it}
}
