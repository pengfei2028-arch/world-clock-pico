package com.wupengfei.worldclock.content

import android.util.Log
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.containers.openWindowContainer
import com.wupengfei.worldclock.platform.FootprintPickerActivity

/** A Stage attachment is not an Android planar window. Give the external picker a
 * real WindowContainer task instead of launching it in the immersive Stage task. */
@Composable internal fun rememberFootprintPhotoPicker(state: FootprintState): (View) -> Unit {
    val context = LocalContext.current
    return remember(context, state) {
        { source ->
            if (source.isAttachedToWindow) {
                val city = state.beginPicker()
                if (city != null) try {
                    context.openWindowContainer(FootprintPickerActivity.CONTAINER)
                    Log.i("WorldClockFootprints", "V101 opening planar picker host city=$city")
                } catch (e: Exception) {
                    state.fail("无法打开选图窗口，请重试", city)
                    Log.e("WorldClockFootprints", "V101 picker host failed", e)
                }
            }
        }
    }
}

@Composable internal fun FootprintPickerHost() {
    val activity = LocalContext.current as? FootprintPickerActivity
    val state = WorldClockSession.clock.footprints
    LaunchedEffect(activity) {
        withFrameNanos { }
        activity?.contentReady()
    }
    Column(Modifier.size(800.dp, 600.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
        Text(if (state.busy) state.progress.ifBlank { "正在保存照片…" } else "正在打开系统照片选择器…")
        Button(onClick = { activity?.cancelSelection() }, enabled = !state.busy) { Text("取消") }
    }
}
