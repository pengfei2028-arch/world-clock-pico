package com.wupengfei.worldclock.platform

import android.util.Log
import android.content.Intent
import com.pico.spatial.ui.platform.stub.SpatialLaunchActivity
import com.wupengfei.worldclock.content.WorldClockSession
import androidx.activity.result.contract.ActivityResultContracts
import com.wupengfei.worldclock.content.FootprintStore

/** In-app collapse keeps the shared volume alive; explicit close retains its closing semantics. */
class LaunchActivity : SpatialLaunchActivity() {
    // Keep V96's result slot for a pending result restored across Activity recreation.
    // New requests use the dedicated planar FootprintPickerActivity (V101+).
    private val photoPicker=registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(FootprintStore.MAX_BATCH)){uris->
        WorldClockSession.clock.footprints.pickerResult(uris)
    }
    private var entered = false
    private var closing = false
    override fun onCreate(savedInstanceState:android.os.Bundle?) {
        WorldClockSession.onLaunch()
        super.onCreate(savedInstanceState)
        WorldClockSession.clock.footprints.initialize(applicationContext)
        savedInstanceState?.getString("footprint_picker_city")?.let{WorldClockSession.clock.footprints.restorePicker(it)}
    }
    override fun onSaveInstanceState(outState:android.os.Bundle) {
        WorldClockSession.clock.footprints.pickingCityId?.let{outState.putString("footprint_picker_city",it)}
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        WorldClockSession.onLaunch()
        super.onNewIntent(intent)
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
