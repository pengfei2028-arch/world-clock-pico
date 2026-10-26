package com.wupengfei.worldclock

import com.wupengfei.worldclock.content.*
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.*
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.resize.CaptionBarType
import com.pico.spatial.ui.platform.resize.VolumeBasePanelType
import androidx.compose.ui.unit.dp
import com.wupengfei.worldclock.platform.FootprintPickerActivity

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme {
                ImmersiveHost()
            }
        }
        WindowContainer(
            id = FootprintPickerActivity.CONTAINER,
            form = Form.Planar,
            defaultSize = WindowContainerSize(800.dp, 600.dp),
            targetActivity = FootprintPickerActivity::class.java,
            enableMaterialBackground = true,
            defaultCaptionBarType = CaptionBarType.Default,
        ) { PicoTheme { FootprintPickerHost() } }
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

