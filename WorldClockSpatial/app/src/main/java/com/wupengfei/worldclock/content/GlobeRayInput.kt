package com.wupengfei.worldclock.content

/** Emulator mouse rays rotate around a stationary virtual hand. Project the ray
 * onto a fixed 60 cm view plane; target depth and globe rotation cannot amplify it.
 * Physical headsets continue to use real hand translation. */
internal class GlobeRayInput {
    private var eye=GlobeVector3.ZERO
    private var forward=GlobeVector3(0.0,0.0,-1.0)
    fun begin(head:GlobeVector3,view:GlobeQuaternion){eye=head;forward=view.rotate(GlobeVector3(0.0,0.0,-1.0))}
    fun relative(hit:GlobeVector3):GlobeVector3 {
        val ray=hit-eye;val depth=ray.dot(forward)
        if(!depth.isFinite()||depth<=.001)return GlobeVector3(Double.NaN,Double.NaN,Double.NaN)
        return ray*(.60/depth)
    }
}
