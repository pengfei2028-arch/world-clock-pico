package com.wupengfei.worldclock.content

/** Project look: 450ms out, 1.4s in; interruptible without opacity discontinuities. */
internal class CloudVisibility {
    var opacity=0f; private set
    private var target=0f
    private var from=0f
    private var elapsed=0.0
    private var duration=1.4
    fun canReveal(openingFinished:Boolean)=openingFinished
    fun update(visible:Boolean,seconds:Double):Float {
        val next=if(visible)1f else 0f
        if(next!=target){from=opacity;target=next;elapsed=0.0;duration=if(visible)1.4 else .45}
        if(seconds.isFinite())elapsed+=seconds.coerceIn(0.0,.1)
        val t=(elapsed/duration).coerceIn(0.0,1.0)
        val eased=t*t*t*(t*(6.0*t-15.0)+10.0)
        opacity=(from+(target-from)*eased).toFloat()
        return opacity
    }
}
