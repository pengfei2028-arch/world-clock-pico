package com.wupengfei.worldclock.content

import androidx.compose.runtime.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Text
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun magnitudeColor(magnitude:Double?)=when(QuakeRules.magnitudeBand(magnitude)){2->Color(0xFFFF5145);1->Color(0xFFFFD54F);else->Color.White}
private val quakeTime=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)
private val shortTime=DateTimeFormatter.ofPattern("MM/dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)
private fun time(value:Instant?)=value?.let{quakeTime.format(it)}?:"暂无"
private fun number(value:Double?,digits:Int=1)=value?.let{String.format(Locale.ROOT,"%."+digits+"f",it)}?:"暂无"

@Composable internal fun EarthquakeToggle(clock:ClockState){
    val active=clock.mode==EarthMode.EARTHQUAKE
    GlassAction({clock.toggleEarthquakePanel()},
        Modifier.size(48.dp).semantics{contentDescription="地球活动 · 地震";selected=active},clock.ready,
        containerTint=if(active)Color(AppSelectionStyle.COLOR_ARGB).copy(alpha=AppSelectionStyle.CONTAINER_ALPHA) else Color.Transparent,horizontalPadding=0.dp){
        Image(painterResource(com.wupengfei.worldclock.R.drawable.ic_earthquake),null,Modifier.size(20.dp))
    }
}
@Composable internal fun QuakeTimeline(clock:ClockState){
    val q=clock.earthquakes;val c=q.catalog
    Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically){
        GlassAction({clock.useFeature();q.togglePlayback()},Modifier.size(48.dp).semantics{contentDescription=if(q.playing)"暂停回放" else "播放地震历史"},
            enabled=clock.mode==EarthMode.EARTHQUAKE&&c!=null&&q.filtered.isNotEmpty(),horizontalPadding=0.dp){
            Canvas(Modifier.size(18.dp)){
                if(q.playing){drawLine(Color.White,Offset(size.width*.3f,0f),Offset(size.width*.3f,size.height),4.dp.toPx())
                    drawLine(Color.White,Offset(size.width*.75f,0f),Offset(size.width*.75f,size.height),4.dp.toPx())}
                else drawPath(Path().apply{moveTo(2f,0f);lineTo(size.width,size.height/2);lineTo(2f,size.height);close()},Color.White)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal=12.dp),contentAlignment=Alignment.Center){
            if(c==null)Text("—",style=uiText(13)) else {
                val days=remember(c.start,c.end){QuakeDayRuler.days(c.start,c.end)}
                val measurer=rememberTextMeasurer()
                val textStyle=uiText(13).copy(color=Color.White)
                val labels=remember(days,measurer,textStyle){days.map{measurer.measure(it.label,textStyle,maxLines=1)}}
                Canvas(Modifier.fillMaxSize()){
                    val stride=if(days.size>9)5 else 1
                    var lastRight=-8.dp.toPx()
                    days.forEachIndexed{i,day->
                        val left=(day.startFraction*size.width).toFloat()
                        if(i>0)drawLine(Color.White.copy(alpha=.35f),Offset(left,size.height-9.dp.toPx()),Offset(left,size.height-4.dp.toPx()),1.dp.toPx())
                        if(i%stride==0){
                            val layout=labels[i]
                            val center=((day.startFraction+day.endFraction)*.5*size.width).toFloat()
                            val x=(center-layout.size.width/2f).coerceIn(0f,(size.width-layout.size.width).coerceAtLeast(0f))
                            if(x>=lastRight+8.dp.toPx()){
                                drawText(layout,topLeft=Offset(x,(size.height-layout.size.height)/2f-2.dp.toPx()))
                                lastRight=x+layout.size.width
                            }
                        }
                    }
                }
            }
            if(q.replay&&c!=null)Canvas(Modifier.fillMaxSize()){
                val x=(QuakeRules.fraction(q.replayTime?:c.start,c.start,c.end)*size.width).toFloat()
                drawLine(Color(AppSelectionStyle.COLOR_ARGB),Offset(x,8.dp.toPx()),Offset(x,size.height-8.dp.toPx()),5.dp.toPx())
            }
        }
    }
}
@Composable internal fun QuakeCaption(clock:ClockState){
    val q=clock.earthquakes
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Text(if(q.replay)shortTime.format(q.replayTime?:q.catalog?.start?:clock.now) else q.catalog?.let{"整个周期 · ${shortTime.format(it.start).substringBefore(" ")}–${shortTime.format(it.end).substringBefore(" ")} · UTC"}?:"整个周期总览 · UTC",style=uiText(12))
        if(q.replay)GlassAction({clock.useFeature();q.overview()},Modifier.height(28.dp)){Text("返回总览",style=uiText(12))}
    }
}
@Composable private fun RegionRow(event:QuakeEvent,onClick:()->Unit){
    GlassAction(onClick,Modifier.fillMaxWidth().height(56.dp),horizontalPadding=10.dp){
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
            Text(QuakeRules.regionName(event.place),style=uiText(16),maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(shortTime.format(event.occurredAt),style=uiText(12),color=Color.White.copy(alpha=.68f),maxLines=1)
        }
        Spacer(Modifier.width(12.dp))
        Text("M ${event.magnitudeText}",style=uiText(16),color=magnitudeColor(event.magnitude),maxLines=1)
    }
}
@Composable internal fun EarthquakePanel(clock:ClockState){
    val q=clock.earthquakes;val event=q.selected;val group=q.group;val catalog=q.catalog
    val regions=remember(q.filtered,q.replayTime,q.sortOrder){q.listed}
    val context=LocalContext.current
    LaunchedEffect(catalog,q.minimum,q.sortOrder,q.replay){
        android.util.Log.i("WorldClockQuakes","V90 displayed list source=USGS period=${q.period} minimum=${q.minimum} sort=${q.sortOrder} records=${regions.size} globe=${q.visible.size} fetched=${catalog?.fetchedAt} first=${regions.take(3).joinToString{it.id+":M"+it.magnitudeText}}")
    }
    fun act(action:()->Unit){clock.touchUi();action()}
    Glass(Modifier.width(QuakeStyle.PANEL_DP.dp).alpha(q.panelReveal)){
        Column(Modifier.padding(2.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row(Modifier.fillMaxWidth().height(36.dp),verticalAlignment=Alignment.CenterVertically){
                Text(if(event!=null)"地震事件" else "地球活动 · 地震",style=uiText(18,FontWeight.Medium),modifier=Modifier.weight(1f))
                GlassAction({act{q.closePanel()}},Modifier.size(36.dp).semantics{contentDescription="收起地震面板"},horizontalPadding=0.dp){Text("×",style=uiText(22))}
            }
            val scroll=key(event?.id,q.groupIds,q.period,q.minimum,q.sortOrder){rememberLazyListState()}
            Column(Modifier.heightIn(max=QuakeStyle.PANEL_BODY_DP.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(event==null)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    QuakeSort.entries.forEach{order->GlassAction({act{q.chooseSort(order)}},
                        Modifier.weight(1f).height(36.dp).semantics{selected=q.sortOrder==order},
                        containerTint=if(q.sortOrder==order)Color(AppSelectionStyle.COLOR_ARGB).copy(alpha=AppSelectionStyle.CONTAINER_ALPHA) else Color.Transparent,horizontalPadding=8.dp){
                        Text(order.label,style=uiText(14))
                    }}
                }
                LazyColumn(Modifier.weight(1f,fill=false),state=scroll,verticalArrangement=Arrangement.spacedBy(10.dp)){
                if(event!=null){item{
                  Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                    GlassAction({act{q.clearSelection()}},Modifier.fillMaxWidth().height(40.dp)){Text("返回地区列表",style=uiText(16))}
                    Text(event.place,style=uiText(20,FontWeight.Medium))
                    Text("M ${event.magnitudeText}  ${event.magnitudeType?:"暂无"}",style=uiText(30,FontWeight.Medium))
                    Text("发生时间 · UTC",style=uiText(12),color=Color.White.copy(alpha=.68f))
                    Text(time(event.occurredAt),style=uiText(16))
                    Text("深度  ${number(event.depthKm)}${if(event.depthKm!=null)" km" else ""}",style=uiText(16))
                    Text("${number(kotlin.math.abs(event.latitude),2)}° ${if(event.latitude<0)"S" else "N"}    ${number(kotlin.math.abs(event.longitude),2)}° ${if(event.longitude<0)"W" else "E"}",style=uiText(14))
                    if(!q.selectedFront)GlassAction({act{q.select(event,focus=true)}},Modifier.fillMaxWidth().height(40.dp)){Text("查看震中",style=uiText(14))}
                    Text("记录更新  ${time(event.updatedAt)}",style=uiText(12),color=Color.White.copy(alpha=.68f))
                    Text("USGS · ${event.id}",style=uiText(12),color=Color.White.copy(alpha=.68f))
                    QuakeRules.sourceUrl(event)?.let{url->GlassAction({act{
                        runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
                            .onFailure{q.status="无法打开浏览器，可用上方事件 ID 在 USGS 查询"}
                    }},Modifier.fillMaxWidth().height(36.dp)){Text("查看官方记录",style=uiText(14))}}
                    Text("历史事件 · 波纹仅标示事件位置",style=uiText(12),color=Color.White.copy(alpha=.68f))
                  }
                }
                }else if(group.isNotEmpty()){
                  item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                    GlassAction({act{q.clearSelection()}},Modifier.fillMaxWidth().height(40.dp)){Text("返回地区列表",style=uiText(16))}
                    Text("密集区域 · ${group.size} 条记录",style=uiText(18))
                  }}
                  items(group,key={it.id}){e->RegionRow(e){act{q.selectListed(e.id)}}}
                }else{
                  item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        QuakePeriod.entries.forEach{period->GlassAction({act{q.choosePeriod(period)}},Modifier.weight(1f).height(40.dp),
                            containerTint=if(q.period==period)Color.White.copy(alpha=.18f) else Color.Transparent,horizontalPadding=8.dp){Text(period.label,style=uiText(16))}}
                    }
                    Text("地球与列表 · 最低震级",style=uiText(12),color=Color.White.copy(alpha=.68f))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        listOf(4.0,5.0,6.0,7.0).forEach{m->GlassAction({act{q.chooseMinimum(m)}},Modifier.weight(1f).height(40.dp),
                            containerTint=if(q.minimum==m)Color.White.copy(alpha=.18f) else Color.Transparent,horizontalPadding=8.dp){Text("M≥${m.toInt()}",style=uiText(14))}}
                    }
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text("${q.period.label} · M≥${q.minimum.toInt()}",style=uiText(16))
                        Text(if(catalog==null)if(q.loading)"正在加载事件…" else "数据尚未加载" else if(q.replay)"回放已出现 ${regions.size} / ${q.filtered.size} 条记录" else "${regions.size} 条事件记录 · 与地球一致",
                            style=uiText(12),color=Color.White.copy(alpha=.68f))
                    }
                    if(catalog!=null&&regions.isEmpty())Text(if(q.replay)"此时刻还没有符合条件的事件" else "暂无符合条件的地震",style=uiText(16))
                  }}
                  items(regions,key={it.id}){e->RegionRow(e){act{
                      val selected=q.selectListed(e.id)
                      android.util.Log.i("WorldClockQuakes","V90 list click id=${e.id} mag=${e.magnitude} type=${e.magnitudeType} selected=$selected count=${regions.size} sort=${q.sortOrder} minimum=${q.minimum} period=${q.period} replay=${q.replayTime}")
                  }}}
                }
                item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(q.renderFailure.isNotBlank())Text(q.renderFailure,style=uiText(13),color=Color(0xFFFFC58E))
                Text(q.status,style=uiText(12),color=Color.White.copy(alpha=.68f))
                if(q.pending!=null)Text("有更新，返回总览后应用",style=uiText(12))
                if(event==null)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("大小 / 颜色 · 震级",style=uiText(12))
                    listOf(4.0,5.0,6.0,7.0).forEach{m->Column(horizontalAlignment=Alignment.CenterHorizontally){
                        Canvas(Modifier.size(20.dp)){drawCircle(magnitudeColor(m),(QuakeRules.pointDiameter(m)/2).toFloat().dp.toPx(),style=Stroke(1.dp.toPx()))}
                        Text(if(m==4.0)"M <5" else "M ${m.toInt()}+",style=uiText(11))
                    }}
                }
                }}
                }
                Row(Modifier.fillMaxWidth().height(32.dp),verticalAlignment=Alignment.CenterVertically){
                    Text("USGS 获取 · ${catalog?.fetchedAt?.let{shortTime.format(it)}?:"暂无"}",modifier=Modifier.weight(1f),style=uiText(12),color=Color.White.copy(alpha=.68f),maxLines=1)
                    GlassAction({act{q.refreshRequest++}},Modifier.height(32.dp),!q.loading,horizontalPadding=10.dp){Text(if(q.loading)"更新中" else "刷新",style=uiText(12))}
                }
            }
        }
    }
}
