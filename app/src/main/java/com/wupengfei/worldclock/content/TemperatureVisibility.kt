package com.wupengfei.worldclock.content

/** Project motion: 600 ms, shared by the shell and legend; reversible from current opacity. */
internal class TemperatureVisibility {
    var opacity=0f; private set
    private var from=0f
    private var target=0f
    private var elapsed=0.0
    fun update(visible:Boolean,seconds:Double):Float {
        val next=if(visible)1f else 0f
        if(next!=target){from=opacity;target=next;elapsed=0.0}
        if(seconds.isFinite())elapsed+=seconds.coerceIn(0.0,.1)
        val t=(elapsed/.6).coerceIn(0.0,1.0)
        val eased=t*t*t*(t*(6*t-15)+10)
        opacity=(from+(target-from)*eased).toFloat()
        return opacity
    }
}
