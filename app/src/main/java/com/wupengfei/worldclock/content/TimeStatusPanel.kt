package com.wupengfei.worldclock.content

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.annotation.ExperimentalSpatialUIApi
import com.pico.spatial.ui.foundation.layout.zOffset
import com.pico.spatial.ui.foundation.spatialhittest.enableSpatialHittestProvider

/** Display-only attachment, NEVER an ancestor of the Dock or any interactive control. */
@OptIn(ExperimentalSpatialUIApi::class)
@Composable internal fun TimeStatusPanel(state:ClockState){
    Column(Modifier.width(528.dp).height(80.dp).zOffset{0f}.enableSpatialHittestProvider(false),
        horizontalAlignment=Alignment.CenterHorizontally){
        Box(Modifier.fillMaxWidth().height(32.dp),contentAlignment=Alignment.Center){
            when{
                state.distance.enabled -> Text(state.distance.hint(state.cities),style=uiText(13))
                state.footprints.enabled -> if(state.footprints.albums.isEmpty()&&!state.footprints.albumOpen&&!state.footprints.placesOpen)
                    Text("先添加一个足迹地点",style=uiText(13))
                state.mode==EarthMode.EARTHQUAKE -> QuakeCaption(state,showAction=false)
                state.mode==EarthMode.TEMPERATURE -> if(state.temperature.manifest==null)Text("气温数据不可用",style=uiText(12))
                state.clockNotice.isNotBlank()&&!state.preview -> Text(state.clockNotice,style=uiText(12))
            }
        }
        TemperatureLegend(Modifier.fillMaxWidth().graphicsLayer{alpha=state.temperature.reveal})
    }
}

internal enum class TimeReturnAction { NONE, WORLD_NOW, TEMPERATURE_NOW, QUAKE_OVERVIEW }

internal fun ClockState.timeReturnAction():TimeReturnAction=when{
    distance.enabled||footprints.enabled -> TimeReturnAction.NONE
    mode==EarthMode.EARTHQUAKE -> if(earthquakes.replay)TimeReturnAction.QUAKE_OVERVIEW else TimeReturnAction.NONE
    mode==EarthMode.TEMPERATURE -> if(temperature.manifest!=null&&!temperature.autoNow)TimeReturnAction.TEMPERATURE_NOW else TimeReturnAction.NONE
    preview -> TimeReturnAction.WORLD_NOW
    else -> TimeReturnAction.NONE
}

internal fun ClockState.timeStatusVisible()=when{
    distance.enabled -> true
    footprints.enabled -> footprints.albums.isEmpty()&&!footprints.albumOpen&&!footprints.placesOpen
    mode==EarthMode.EARTHQUAKE||mode==EarthMode.TEMPERATURE -> true
    temperature.reveal>.001f -> true
    else -> clockNotice.isNotBlank()&&!preview
}

/** Independent actionable attachment: the read-only status cannot suppress its input. */
@Composable internal fun TimeReturnPanel(state:ClockState){
    Box(Modifier.height(32.dp),contentAlignment=Alignment.Center){
        when(state.timeReturnAction()){
            TimeReturnAction.WORLD_NOW -> GlassAction({state.live()},Modifier.height(28.dp)){Text("回到现在",style=uiText(13))}
            TimeReturnAction.TEMPERATURE_NOW -> GlassAction({state.useFeature();state.temperature.now(state.now)},Modifier.height(28.dp)){Text("现在",style=uiText(13))}
            TimeReturnAction.QUAKE_OVERVIEW -> GlassAction({state.useFeature();state.earthquakes.overview()},Modifier.height(28.dp)){Text("返回总览",style=uiText(12))}
            TimeReturnAction.NONE -> Spacer(Modifier.width(1.dp))
        }
    }
}
