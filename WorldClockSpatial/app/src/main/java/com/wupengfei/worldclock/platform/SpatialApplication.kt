package com.wupengfei.worldclock.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.wupengfei.worldclock.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
