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
            defaultSize = WindowContainerSize(.45f, .5f, .45f, LengthUnit.Meters),
            enableMaterialBackground = false,
            worldScale = WorldScale.Fixed,
            volumeAlignment = VolumeAlignment.Gravity,
            defaultVolumeBasePanelType = VolumeBasePanelType.Default,
            defaultCaptionBarType = CaptionBarType.Default,
        ) { PicoTheme { DesktopEarth() } }
    }

