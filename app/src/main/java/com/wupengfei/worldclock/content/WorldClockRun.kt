package com.wupengfei.worldclock.content

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One user-visible run. Container transitions share it; a completed app exit does not. */
internal class WorldClockRun {
    val clock = ClockState()
    val motion = GlobeMotion()
    val entrance = GlobeEntrance()
    var geographicBase: GlobeQuaternion? = null
}

/** Android may retain the process after removing every task. Never use that as our session lifetime. */
internal class WorldClockRuns {
    var current by mutableStateOf(WorldClockRun()); private set
    var generation = 0; private set

    fun launch(stageMounted: Boolean, desktopMounted: Boolean, transitioning: Boolean,
               immersiveResources: Boolean, desktopResources: Boolean): Boolean {
        // Includes loading/teardown gaps and the minimized Shared container during expansion.
        // A launcher intent while an app is still open must not replay its entrance.
        if (stageMounted || desktopMounted || transitioning || immersiveResources || desktopResources) return false
        current.clock.footprints.close()
        current = WorldClockRun()
        generation++
        return true
    }
}
