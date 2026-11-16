package com.wupengfei.worldclock

import android.app.Activity
import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pico.spatial.ui.platform.containers.closeStage
import kotlinx.coroutines.runBlocking
import com.wupengfei.worldclock.platform.LaunchActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StageExitTest {
 private fun verifyExit(exit: (Activity)->Unit) {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val context=instrumentation.targetContext
  val app=context.applicationContext as Application
  val resumed=CountDownLatch(1); val destroyed=CountDownLatch(1)
  var activity: Activity?=null
  val callbacks=object: Application.ActivityLifecycleCallbacks {
   override fun onActivityResumed(a:Activity){if(a is LaunchActivity){activity=a;resumed.countDown()}}
   override fun onActivityDestroyed(a:Activity){if(a===activity)destroyed.countDown()}
   override fun onActivityCreated(a:Activity,b:Bundle?){}
   override fun onActivityStarted(a:Activity){}
   override fun onActivityPaused(a:Activity){}
   override fun onActivityStopped(a:Activity){}
   override fun onActivitySaveInstanceState(a:Activity,b:Bundle){}
  }
  instrumentation.runOnMainSync{app.registerActivityLifecycleCallbacks(callbacks)}
  try {
   context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
   assertTrue("Stage must resume",resumed.await(20,TimeUnit.SECONDS))
   SystemClock.sleep(12000) // Let real resource loading and the entry animation complete.
   instrumentation.runOnMainSync{exit(requireNotNull(activity))}
   assertTrue("Leaving Stage must destroy the Activity",destroyed.await(12,TimeUnit.SECONDS))
   SystemClock.sleep(600)
   val tasks=(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).appTasks
   assertFalse("Closed Stage must not remain in app tasks",tasks.any{it.taskInfo.baseIntent.component?.className==LaunchActivity::class.java.name})
  } finally {instrumentation.runOnMainSync{app.unregisterActivityLifecycleCallbacks(callbacks)}}
 }
 @Test fun backgroundingEndsTheStageTask()=verifyExit { assertTrue(it.moveTaskToBack(true)) }
 @Test fun sdkStageDismissEndsTheTask()=verifyExit { runBlocking { closeStage() } }
}
