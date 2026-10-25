package com.wupengfei.worldclock.content

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.*
import com.pico.spatial.core.container.getSelfSpaceState
import com.pico.spatial.core.container.SpaceState
import com.pico.spatial.ui.platform.containers.*
import kotlinx.coroutines.*

/** CPU-only session state. Renderers and their native resources belong to one container. */
internal object WorldClockSession {
    const val STAGE = "WorldClockEarthMixedV08"
    const val DESKTOP = "WorldClockDesktopV58"
    const val DESKTOP_TAG = "earth"
    val clock = ClockState()
    val motion = GlobeMotion()
    val entrance = GlobeEntrance()
    var geographicBase: GlobeQuaternion? = null
    var applicationContext: android.content.Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var busy by mutableStateOf(false); private set
    var desktopRequested by mutableStateOf(false); private set
    var immersiveRendering by mutableStateOf(true); private set
    var desktopRendering by mutableStateOf(false); private set
    var stageMounted = false
    var immersiveResources = false
    var desktopMounted = false
    var desktopResources = false
    var desktopFrames = 0L
    var diameter by mutableFloatStateOf(DesktopEarthLayout.DEFAULT_DIAMETER); private set
    var immersiveVisibility by mutableFloatStateOf(1f); private set
    var desktopVisibility by mutableFloatStateOf(1f); private set
    var loadFailure: Throwable? = null
    internal var rejectNextExpandForTest = false
    var error by mutableStateOf(""); private set
    var stageNavigator: SpatialNavigator? = null
    var desktopNavigator: SpatialNavigator? = null
    val keepTaskAlive get() = desktopRequested || busy

    fun setDesktopDiameter(value:Float) { if(!busy && value.isFinite())diameter=value.coerceIn(DesktopEarthLayout.MIN_DIAMETER,DesktopEarthLayout.MAX_DIAMETER) }
    fun resizeDesktop(delta:Float) { if(delta.isFinite())setDesktopDiameter(diameter+delta) }
    fun onLaunch() { if(!stageMounted&&!desktopMounted&&!busy){immersiveRendering=true;desktopRendering=false;desktopRequested=false;immersiveVisibility=1f;error=""} }
    fun desktopClosed() { if(!busy&&desktopRequested){desktopRequested=false;desktopRendering=false;immersiveRendering=true} }
    fun failedToLoad(e:Throwable) { loadFailure=e;Log.e("WorldClockSpace","Scene load failed",e) }
    private suspend fun fade(from:Float,to:Float,desktop:Boolean) {
        val start=android.os.SystemClock.elapsedRealtimeNanos()
        do {
            val t=((android.os.SystemClock.elapsedRealtimeNanos()-start)/350_000_000.0).coerceIn(0.0,1.0).toFloat()
            val value=from+(to-from)*(t*t*(3-2*t))
            if(desktop)desktopVisibility=value else immersiveVisibility=value
            if(t>=1f)break
            delay(16)
        } while(true)
    }

    private suspend fun awaitState(label: String, predicate: () -> Boolean) {
        withTimeout(25_000) { while (!predicate()) { loadFailure?.let { throw IllegalStateException(label,it) };delay(40) } }
        Log.i("WorldClockSpace", "V58 confirmed $label")
    }

    fun collapse(navigator: SpatialNavigator) {
        if (busy || desktopRequested || !clock.ready) return
        busy = true; error = ""; loadFailure=null; desktopRequested = true
        scope.launch {
            try {
                clock.dismissPanels()
                if(desktopMounted)navigator.restoreWindowContainer(DESKTOP, DESKTOP_TAG)
                else navigator.openWindowContainer(DESKTOP, DESKTOP_TAG)
                awaitState("desktop container mounted") { desktopMounted }
                fade(1f,0f,false)
                // Release high-resolution scene before allocating desktop textures.
                immersiveRendering = false
                awaitState("immersive resources released") { !immersiveResources }
                navigator.closeStage()
                awaitState("Stage disposed") { !stageMounted }
                awaitState("system SHARED_SPACE") { applicationContext?.getSelfSpaceState()==SpaceState.SHARED_SPACE }
                motion.enterDesktop()
                desktopVisibility=0f
                desktopRendering = true
                awaitState("desktop scene ready") { desktopResources }
                fade(0f,1f,true)
                Log.i("WorldClockSpace", "V58 SHARED desktop ready; Stage disposed=true")
            } catch (e: Exception) {
                Log.e("WorldClockSpace", "Collapse failed", e)
                loadFailure=null;desktopRendering = false
                runCatching { awaitState("release failed desktop") { !desktopResources } }
                immersiveVisibility=1f;immersiveRendering = true
                val recovered=runCatching {
                    if(!stageMounted)check(withTimeout(25_000){navigator.openStage(STAGE, StageStyle.Mixed)}==OpenStageResult.Allowed)
                    awaitState("restored immersive") { clock.ready&&stageMounted }
                }.isSuccess
                if(recovered) {
                    desktopRequested=false
                    runCatching { navigator.minimizeWindowContainer(DESKTOP,DESKTOP_TAG) }
                    error="收起失败，已恢复地球；请重试";clock.notice=error
                } else recoverDesktop(navigator,"暂时无法展开；可重试或关闭软件")
            } finally { busy = false }
        }
    }

    fun expand(navigator: SpatialNavigator) {
        if (busy || !desktopRequested) return
        busy = true; error = ""; loadFailure=null
        scope.launch {
            try {
                fade(1f,0f,true)
                desktopRendering = false
                awaitState("desktop resources released") { !desktopResources }
                immersiveVisibility=0f;immersiveRendering = true
                if(com.wupengfei.worldclock.BuildConfig.DEBUG&&rejectNextExpandForTest){rejectNextExpandForTest=false;error("Injected Stage denial")}
                val result = withTimeout(25_000){navigator.openStage(STAGE, StageStyle.Mixed)}
                check(result == OpenStageResult.Allowed) { "openStage: $result" }
                awaitState("immersive ready") { stageMounted && immersiveResources && clock.ready }
                navigator.minimizeWindowContainer(DESKTOP, DESKTOP_TAG)
                desktopRequested = false
                fade(0f,1f,false)
                Log.i("WorldClockSpace", "V58 FULL restored")
            } catch (e: Exception) {
                Log.e("WorldClockSpace", "Expand failed", e)
                recoverDesktop(navigator,"展开失败，已返回桌面；可重试")
            } finally { busy = false }
        }
    }

    private suspend fun recoverDesktop(navigator:SpatialNavigator,message:String) {
        loadFailure=null;immersiveRendering=false
        desktopRequested=true;desktopVisibility=1f;desktopRendering=false
        try {
            navigator.closeStage()
            awaitState("failed Stage released") { !immersiveResources&&!stageMounted }
            awaitState("recovery SHARED_SPACE") { applicationContext?.getSelfSpaceState()==SpaceState.SHARED_SPACE }
            navigator.openWindowContainer(DESKTOP,DESKTOP_TAG)
            awaitState("recovery container") { desktopMounted }
            desktopRendering=true
            awaitState("recovery lightweight scene") { desktopResources }
            error=message
        } catch (failure:Exception) {
            Log.e("WorldClockSpace","Desktop recovery incomplete",failure)
            // Keep a small actionable container even if either scene cannot load.
            // Never describe an unconfirmed Stage close as a return to Shared Space.
            desktopRendering=false
            runCatching { navigator.openWindowContainer(DESKTOP,DESKTOP_TAG) }
            error="切换未完成，请重试展开或关闭软件"
        }
    }

    fun closeSoftware(navigator: SpatialNavigator, activity: Activity?) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                desktopRequested = false; desktopRendering = false; immersiveRendering = false
                runCatching { navigator.closeWindowContainer(DESKTOP, DESKTOP_TAG) }
                runCatching { navigator.closeStage() }
            } finally {
                // A container may supply a wrapped Context or a different stub Activity.
                // Explicit software close removes all of this app's own tasks, including
                // the stopped launch Activity retained during desktop mode.
                activity?.finishAndRemoveTask()
                val manager=applicationContext?.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                manager?.appTasks?.forEach { it.finishAndRemoveTask() }
                busy = false
            }
        }
    }
}
