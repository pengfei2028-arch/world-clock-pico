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
    internal fun copyCelsius() = FloatArray(values.size) { if(values[it]==Short.MIN_VALUE) Float.NaN else values[it]/100f }
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
    // Fixed physical thresholds; 0 C is deep blue, with a narrow saturated blue/cyan transition.
    // Never normalize against a frame's extrema.
    val breaks=listOf(-30,-20,-10,0,5,10,15,20,25,28,31,34,37,40)
    // Dataset manifest contract remains independent of display-band refinement.
    val labeledBreaks=(-30..40 step 10).toList()
    val displayTicks=listOf(-30,-10,0,10,20,25,31,40)
    val colors=listOf(0xFF061B56L,0xFF082878L,0xFF0A359DL,0xFF0D43BAL,0xFF114FC5L,0xFF126FDBL,0xFF00ABD4L,0xFF21C5B8L,0xFFE4DA55L,0xFFFFCA46L,0xFFFFAA32L,0xFFF88328L,0xFFED5425L,0xFFD52F2AL,0xFFA5102CL)
    fun band(c:Double)=breaks.count{c>=it}
}
