package com.wupengfei.worldclock.platform

import android.util.Log
import com.pico.spatial.ui.platform.stub.SpatialLaunchActivity

/** The app owns one immersive stage; leaving it ends this task. */
class LaunchActivity : SpatialLaunchActivity() {
    private var entered = false
    private var closing = false
    override fun onResume() {
        super.onResume()
        entered = true
    }
    override fun onStop() {
        super.onStop()
        // onPause also occurs for system overlays; only close once the stage is no longer visible.
        if (entered && !isChangingConfigurations && !closing) {
            closing = true
            Log.i("WorldClockLifecycle", "V37 stage stopped; finishing and removing task")
            finishAndRemoveTask()
        }
    }
}
