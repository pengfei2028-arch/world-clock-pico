package com.wupengfei.worldclock.content

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text

@Composable internal fun CoordinateIcon(modifier:Modifier=Modifier){
    Canvas(modifier.size(18.dp)){
        val center=Offset(size.width/2,size.height/2);val r=size.width*.34f
        drawCircle(Color.White,r,center,style=Stroke(1.2.dp.toPx()))
        drawLine(Color.White,Offset(center.x-r*1.5f,center.y),Offset(center.x+r*1.5f,center.y),1.dp.toPx())
        drawLine(Color.White,Offset(center.x,center.y-r*1.5f),Offset(center.x,center.y+r*1.5f),1.dp.toPx())
    }
}
@Composable internal fun CoordinateToggle(state:ClockState,compact:Boolean=false){
    val active=state.coordinates.enabled
    GlassAction({state.toggleCoordinates()},
        (if(compact)Modifier.size(32.dp) else Modifier.fillMaxWidth().height(40.dp))
            .semantics{contentDescription="经纬";selected=active},
        enabled=(state.ready||WorldClockSession.desktopResources)&&!WorldClockSession.busy&&state.coordinates.failure.isEmpty(),
        horizontalPadding=if(compact)0.dp else 14.dp,
        containerTint=if(active)Color.White.copy(alpha=.18f) else Color.Transparent){
        CoordinateIcon()
        if(!compact){Spacer(Modifier.width(10.dp));Text("经纬",style=uiText(15),modifier=Modifier.weight(1f));Text(if(state.coordinates.failure.isNotEmpty())"加载失败" else if(active)"已开启" else "开启",style=uiText(12))}
    }
}
@Composable internal fun CoordinateRulerLabel(state:CoordinateState,index:Int){
    val slot=CoordinateRuler.slots[index]
    val tint=Color(0xFF000000.toInt() or slot.rgb)
    Column(Modifier.width(slot.widthDp.dp).height(slot.heightDp.dp).alpha(state.rulerAlpha[index]),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        if(slot.reference)Text(slot.title,style=uiText(CoordinateStyle.LABEL_TEXT_SP).copy(color=tint),maxLines=1)
        Text(slot.text,style=uiText(CoordinateStyle.LABEL_TEXT_SP).copy(fontFeatureSettings="tnum",color=tint),maxLines=1)
    }
}
/** Shared dimensions with the existing city card/leader and avoidance solver. */
@Composable internal fun CoordinateCard(selection:CoordinateSelection?,modifier:Modifier=Modifier){
    if(selection==null){Spacer(Modifier.size(1.dp));return}
    Row(modifier.width(CityLabelLayout.WIDTH_DP.dp).height(CityLabelLayout.HEIGHT_DP.dp),verticalAlignment=Alignment.Bottom){
        Box(Modifier.weight(1f).fillMaxHeight().drawBehind{
            val y=CityLabelLayout.LINE_Y_DP.dp.toPx();drawLine(Color.White.copy(alpha=.65f),Offset(0f,y),Offset(size.width,y),CoordinateStyle.UI_LINE_DP.dp.toPx())
        }){
            Text(selection.title,style=uiText(17),modifier=Modifier.align(Alignment.TopCenter).padding(horizontal=8.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        GlassAction({},Modifier.width(166.dp).height(40.dp),horizontalPadding=10.dp){
            Column(horizontalAlignment=Alignment.Start){
                Text(selection.coordinate.latitudeText(),style=uiText(17).copy(fontFeatureSettings="tnum",lineHeight=19.sp))
                Text(selection.coordinate.longitudeText(),style=uiText(17).copy(fontFeatureSettings="tnum",lineHeight=19.sp))
            }
        }
    }
}
