package com.wupengfei.worldclock

import com.wupengfei.worldclock.content.*
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.*
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.resize.CaptionBarType
import com.pico.spatial.ui.platform.resize.VolumeBasePanelType

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme {
                ImmersiveHost()
            }
        }
        WindowContainer(
            id = WorldClockSession.DESKTOP,
            form = Form.Volumetric,
            defaultSize = WindowContainerSize(DesktopEarthLayout.WIDTH, DesktopEarthLayout.HEIGHT, DesktopEarthLayout.DEPTH, LengthUnit.Meters),
            enableMaterialBackground = false,
            worldScale = WorldScale.Fixed,
            volumeAlignment = VolumeAlignment.Gravity,
            defaultVolumeBasePanelType = VolumeBasePanelType.None,
            defaultCaptionBarType = CaptionBarType.Default,
        ) { PicoTheme { DesktopEarth() } }
    }

