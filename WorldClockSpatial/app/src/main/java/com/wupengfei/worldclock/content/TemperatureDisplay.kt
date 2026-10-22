package com.wupengfei.worldclock.content

import kotlin.math.*

/** Display-only normalized convolution. Raw GFS samples and city readings are never changed.
 * Three radius-2 box passes approximate a 0.61-degree Gaussian at the 0.25-degree source grid.
 * Longitude wraps; latitude clamps; a missing source cell remains missing after filtering.
 */
internal object TemperatureDisplay {
    const val SHELL_SCALE = 1.003f
    const val DEFAULT_OPACITY = 1f
    fun smooth(grid:TemperatureGrid):FloatArray {
        val w=grid.width;val h=grid.height;val raw=grid.copyCelsius()
        var sums=FloatArray(raw.size){if(raw[it].isFinite())raw[it] else 0f}
        var weights=FloatArray(raw.size){if(raw[it].isFinite())1f else 0f}
        fun pass(input:FloatArray,horizontal:Boolean):FloatArray {
            val out=FloatArray(input.size);val rows=if(horizontal)h else w;val cols=if(horizontal)w else h
            fun at(row:Int,col:Int):Int=if(horizontal)row*w+((col%w+w)%w) else col.coerceIn(0,h-1)*w+row
            for(row in 0 until rows){
                var sum=0.0;for(k in -2..2)sum+=input[at(row,k)]
                for(col in 0 until cols){out[at(row,col)]=(sum/5).toFloat();sum+=input[at(row,col+3)]-input[at(row,col-2)]}
            }
            return out
        }
        repeat(3){sums=pass(pass(sums,true),false);weights=pass(pass(weights,true),false)}
        for(i in sums.indices)sums[i]=if(raw[i].isFinite()&&weights[i]>1e-6f)sums[i]/weights[i] else Float.NaN
        // Every longitude meets at one point at each pole; avoid a pinwheel at the UV fan.
        for(y in listOf(0,h-1)){
            var total=0.0;var count=0
            for(x in 0 until w){val v=sums[y*w+x];if(v.isFinite()){total+=v;count++}}
            if(count>0)for(x in 0 until w)if(raw[y*w+x].isFinite())sums[y*w+x]=(total/count).toFloat()
        }
        return sums
    }
    /** RGB numeric encoding retains the V53 decoder; one wrapped column and polar gutter per side. */
    fun pixels(grid:TemperatureGrid,values:FloatArray):IntArray {
        require(values.size==grid.width*grid.height)
        val w=grid.width;val h=grid.height
        return IntArray((w+2)*(h+2)){i->
            val x=((i%(w+2)-1)%w+w)%w;val y=(i/(w+2)-1).coerceIn(0,h-1);val v=values[y*w+x]
            if(!v.isFinite())0xff000000.toInt() else {
                val q=((v+100)*100).roundToInt().coerceIn(0,65535)
                0xff0000ff.toInt() or ((q shr 8) shl 16) or ((q and 255) shl 8)
            }
        }
    }
}
