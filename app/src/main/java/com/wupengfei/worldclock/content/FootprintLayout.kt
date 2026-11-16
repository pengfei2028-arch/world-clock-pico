package com.wupengfei.worldclock.content

/** R4 project layout in the same dp basis as TimePanel. The visible button top
 * is bar.y - 8dp: tray gap is 44dp, viewer gap 70dp. */
internal object FootprintLayout {
    const val PLACES_LEFT_DP = -328.0
    fun albumBottomDp(viewer:Boolean)=if(viewer)62.0 else 36.0
}
