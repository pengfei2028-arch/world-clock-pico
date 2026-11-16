package com.wupengfei.worldclock.content

/** Stage +Y is up (the authored Blender +Z). Project layout shared with Compose. */
internal data class CityLabelLayout(val center:GlobeVector3,val elbow:GlobeVector3,val labelOnRight:Boolean,
    val halfWidth:Double,val halfHeight:Double){
    companion object {
        const val WIDTH_DP=340
        const val HEIGHT_DP=56
        const val CHIP_HEIGHT_DP=40
        const val LINE_Y_DP=HEIGHT_DP-CHIP_HEIGHT_DP/2
        const val SCALE=36f
        fun place(globe:GlobeVector3,tip:GlobeVector3,metresPerDp:Double):CityLabelLayout {
            val halfWidth=WIDTH_DP*metresPerDp/2
            val halfHeight=HEIGHT_DP*metresPerDp/2
            val right=true // User V33: never swap sides while the globe rotates.
            val side=1.0
            // A shared upright plane keeps full card rectangles comparable and in
            // front of the globe, including the near-side pin tips (radius 24.9).
            val z=globe.z+26.5
            val center=GlobeVector3(tip.x+side*8.7,tip.y+3.5,z)
            // Meet the exact attachment edge and same Y as its painted horizontal rule.
            val lineY=(HEIGHT_DP/2.0-LINE_Y_DP)*metresPerDp
            return CityLabelLayout(center,center+GlobeVector3(-side*halfWidth,lineY,0.0),right,halfWidth,halfHeight)
        }
    }

    fun atY(y:Double):CityLabelLayout {
        val shift=GlobeVector3(0.0,y-center.y,0.0)
        return copy(center=center+shift,elbow=elbow+shift)
    }
}
