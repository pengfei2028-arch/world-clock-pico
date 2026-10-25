package com.wupengfei.worldclock.content

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import java.util.Locale
import kotlin.math.roundToLong

@Composable
internal fun DistanceToggle(clock:ClockState){
    val active=clock.distance.enabled
    GlassAction({clock.toggleDistance()},Modifier.size(48.dp).semantics{contentDescription="距离 · 依次选择两座城市";selected=active},
        enabled=clock.ready,containerTint=if(active)Color(AppSelectionStyle.COLOR_ARGB).copy(alpha=AppSelectionStyle.CONTAINER_ALPHA)else Color.Transparent){
        Image(painterResource(com.wupengfei.worldclock.R.drawable.ic_distance),null,Modifier.size(20.dp))
    }
}

@Composable
internal fun DistanceReadout(clock:ClockState){
    val pair=clock.distance.pair(clock.cities)
    Row(Modifier.width(DistanceStyle.PANEL_WIDTH.dp).height(DistanceStyle.PANEL_HEIGHT.dp)
        .alpha(clock.distance.panelAlpha).clip(CircleShape).backgroundMaterial()
        .semantics{contentDescription=if(pair==null)"" else "${pair.first.name}到${pair.second.name}的地表最短距离"},
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
        val number=pair?.let{String.format(Locale.US,"%,d",GreatCircle.km(it.first,it.second).roundToLong())}.orEmpty()
        Text(number,style=uiText(24,FontWeight.Medium).copy(fontFeatureSettings="tnum"),maxLines=1)
        Spacer(Modifier.width(6.dp));Text("km",style=uiText(14),maxLines=1)
    }
}
