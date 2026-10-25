package com.wupengfei.worldclock.content

import androidx.compose.runtime.*
import java.time.Instant

@Stable internal class EarthquakeState {
    var period by mutableStateOf(QuakePeriod.WEEK);private set
    var minimum by mutableDoubleStateOf(4.0);private set
    var catalog by mutableStateOf<QuakeCatalog?>(null);private set
    var pending by mutableStateOf<QuakeCatalog?>(null);private set
    var loading by mutableStateOf(false)
    var status by mutableStateOf("数据尚未加载")
    var refreshRequest by mutableIntStateOf(0)
    var replay by mutableStateOf(false);private set
    var playing by mutableStateOf(false);private set
    var replayTime by mutableStateOf<Instant?>(null);private set
    var selectedId by mutableStateOf<String?>(null);private set
    var groupIds by mutableStateOf<List<String>>(emptyList());private set
    var groupPage by mutableIntStateOf(0)
    var reveal by mutableFloatStateOf(0f)
    var panelOpen by mutableStateOf(true)
    var panelReveal by mutableFloatStateOf(0f)
    var selectionUsesCityFocus=false;private set
    var selectedFront by mutableStateOf(true)
    var renderFailure by mutableStateOf("")
    var returnMode=EarthMode.WORLD_TIME
    var focusRequest=0
    var revision=0;private set
    var replayElapsed=0.0;private set
    val arrivals=mutableMapOf<String,Double>()
    var clusterGroups by mutableStateOf<List<List<QuakeEvent>>>(emptyList())
    var filtered by mutableStateOf<List<QuakeEvent>>(emptyList());private set
    val selected get()=filtered.firstOrNull{it.id==selectedId}
    val group get()=filtered.filter{it.id in groupIds}.sortedWith(compareByDescending<QuakeEvent>{it.magnitude?:0.0}.thenByDescending{it.occurredAt})
    val visible get()=QuakeRules.visible(filtered,if(replay)replayTime else null)
    fun choosePeriod(value:QuakePeriod){if(value==period)return;period=value;catalog=null;pending=null;filtered=emptyList();overview();clearSelection();revision++;status="正在加载地震目录"}
    fun chooseMinimum(value:Double){if(value !in listOf(4.0,5.0,7.0))return;minimum=value;refilter();clearSelection()}
    fun accept(value:QuakeCatalog){
        if(value.period!=period)return
        if(replay){pending=value;return}
        val old=selected
        catalog=value;pending=null;refilter()
        if(old!=null&&selected==null){
            selectedId=filtered.firstOrNull{e->e.id in old.aliases||old.id in e.aliases||e.aliases.intersect(old.aliases).isNotEmpty()}?.id
            if(selectedId!=null)status="所选事件记录已修订"
        }
    }
    private fun refilter(){filtered=catalog?.filtered(minimum).orEmpty();revision++
        if(selectedId!=null&&selected==null){selectedId=null;status="所选事件已修订、撤回或不符合筛选"}
        groupIds=groupIds.filter{id->filtered.any{it.id==id}};groupPage=groupPage.coerceAtMost(((groupIds.size-1)/4).coerceAtLeast(0))}
    fun recentRegions(now:Instant)=QuakeRules.recentRegions(pending?:catalog,now)
    fun selectRecent(id:String,now:Instant):Boolean {
        val event=recentRegions(now).firstOrNull{it.id==id}?:run{status="事件已过期或记录已更新，请刷新列表";return false}
        overview() // Apply a newer snapshot before looking up the stable ID.
        if(minimum>(event.magnitude?:0.0)){minimum=5.0;refilter()}
        val current=filtered.firstOrNull{it.id==id}?:run{status="事件记录已更新，请刷新列表";return false}
        select(current,focus=true);return true
    }
    fun closePanel(){panelOpen=false;clearSelection()}
    fun clearSelection(){selectedId=null;groupIds=emptyList();groupPage=0;selectionUsesCityFocus=false}
    fun select(event:QuakeEvent,focus:Boolean=false){pause();selectedId=event.id;groupIds=emptyList();groupPage=0;panelOpen=true
        selectionUsesCityFocus=focus;if(focus)focusRequest++}
    fun selectGroup(events:List<QuakeEvent>){pause();clearSelection();groupIds=events.map{it.id};panelOpen=true}
    fun overview(){playing=false;replay=false;replayTime=null;arrivals.clear();pending?.let{catalog=it;pending=null;refilter()};revision++}
    fun togglePlayback(){
        val c=catalog?:return
        if(!replay||replayTime==c.end){replay=true;replayTime=c.start;replayElapsed=0.0;arrivals.clear();clearSelection()}
        playing=!playing
    }
    fun pause(){playing=false}
    fun scrub(fraction:Double){
        if(!fraction.isFinite())return
        val c=catalog?:return
        playing=false;replay=true;replayTime=QuakeRules.atFraction(fraction,c.start,c.end);arrivals.clear()
        if(selected?.occurredAt?.isAfter(replayTime)==true)clearSelection()
    }
    fun advance(seconds:Double) {
        if(!playing||!seconds.isFinite())return
        val c=catalog?:return;val old=replayTime?:c.start
        replayElapsed+=seconds.coerceIn(0.0,.1)
        val next=old.plusMillis((seconds.coerceIn(0.0,.1)*c.period.days*86400000/QuakeStyle.REPLAY_SECONDS).toLong()).coerceAtMost(c.end)
        filtered.filter{it.occurredAt>old&&it.occurredAt<=next}.forEach{arrivals[it.id]=replayElapsed}
        replayTime=next;if(next>=c.end)playing=false
    }
    fun eventAlpha(event:QuakeEvent):Float=arrivals[event.id]?.let{((replayElapsed-it)/.18).coerceIn(0.0,1.0).toFloat()}?:1f
    fun recent(event:QuakeEvent)=arrivals[event.id]?.let{replayElapsed-it<5.4}?:false
}
