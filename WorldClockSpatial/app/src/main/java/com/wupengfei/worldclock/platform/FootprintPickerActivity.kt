package com.wupengfei.worldclock.platform

import android.os.Bundle
import android.util.Log
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.pico.spatial.ui.platform.containers.closeWindowContainer
import com.pico.spatial.ui.platform.stub.SpatialStubActivity
import com.wupengfei.worldclock.content.FootprintStore
import com.wupengfei.worldclock.content.WorldClockSession

/** Dedicated planar parent for the system photo picker; never owns or closes the Stage. */
class FootprintPickerActivity : SpatialStubActivity() {
    companion object { const val CONTAINER = "FootprintPhotoPickerV101" }
    private val state get() = WorldClockSession.clock.footprints
    private var city: String? = null
    private var launched = false
    private var completed = false
    private var contentMounted = false
    private val picker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(FootprintStore.MAX_BATCH)
    ) { uris ->
        if (!completed) {
            completed = true
            Log.i("WorldClockFootprints", "V101 planar picker result count=${uris.size} city=$city task=$taskId")
            if (state.pickingCityId == city) state.pickerResult(uris)
            finishAfterImport()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state.initialize(applicationContext)
        city = savedInstanceState?.getString("city") ?: state.pickingCityId
        launched = savedInstanceState?.getBoolean("launched") ?: false
        completed = savedInstanceState?.getBoolean("completed") ?: false
        if (!completed && state.pickingCityId == null) city?.let(state::restorePicker)
        if (city == null) closeHost()
        else if (completed) finishAfterImport()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("city", city)
        outState.putBoolean("launched", launched)
        outState.putBoolean("completed", completed)
        super.onSaveInstanceState(outState)
    }

    fun contentReady() { contentMounted = true; launchWhenReady() }
    override fun onResume() { super.onResume(); launchWhenReady() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) launchWhenReady()
    }

    private fun launchWhenReady() {
        window.decorView.post {
            if (!contentMounted || launched || completed || isFinishing || isDestroyed ||
                !hasWindowFocus() || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@post
            if (city == null || state.pickingCityId != city) { cancelSelection(); return@post }
            launched = true
            try {
                // No source bounds/scale animation: the system inherits this planar task.
                Log.i("WorldClockFootprints", "V101 launch picker owner=FootprintPickerActivity task=$taskId display=${display?.displayId} planarContentReady=true")
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (e: Exception) {
                completed = true
                if (state.pickingCityId == city) state.fail("此设备暂未提供可用的系统照片选择器", city)
                Log.e("WorldClockFootprints", "V101 planar picker launch failed", e)
                closeHost()
            }
        }
    }

    fun cancelSelection() {
        if (completed && state.busy) return
        if (!completed) {
            completed = true
            if (state.pickingCityId == city) state.pickerResult(emptyList())
        }
        closeHost()
    }

    private fun finishAfterImport() {
        // The system's temporary URI grant belongs to this Activity's task. Keep it
        // alive until the asynchronous local copy has finished, including failures.
        // A recreated host resumes the wait rather than closing the grant owner early.
        lifecycleScope.launch {
            snapshotFlow { state.busy }.first { !it }
            closeHost()
        }
    }

    private fun closeHost() {
        closeWindowContainer(CONTAINER)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations && !completed && state.pickingCityId == city) {
            state.pickerResult(emptyList())
        }
        Log.i("WorldClockFootprints", "V101 picker host destroyed completed=$completed recreation=$isChangingConfigurations")
    }
}
