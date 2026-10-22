package com.wupengfei.worldclock.platform

import android.util.Log
import com.pico.spatial.ui.platform.stub.SpatialLaunchActivity
import com.wupengfei.worldclock.content.WorldClockSession

/** In-app collapse keeps the shared volume alive; explicit close retains its closing semantics. */
class LaunchActivity : SpatialLaunchActivity() {
    private var entered = false
    private var closing = false
    override fun onCreate(savedInstanceState:android.os.Bundle?) {
        WorldClockSession.onLaunch()
        super.onCreate(savedInstanceState)
    }
    override fun onResume() {
        super.onResume()
        entered = true
    }
    override fun onStop() {
        super.onStop()
        // onPause also occurs for system overlays; only close once the stage is no longer visible.
        if (entered && !isChangingConfigurations && !closing && !WorldClockSession.keepTaskAlive) {
            closing = true
            Log.i("WorldClockLifecycle", "V37 stage stopped; finishing and removing task")
            finishAndRemoveTask()
        }
    }
}
