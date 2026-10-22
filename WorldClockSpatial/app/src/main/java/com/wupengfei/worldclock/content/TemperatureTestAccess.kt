package com.wupengfei.worldclock.content
/** In-process instrumentation access; registered only in debug builds. No exported endpoint. */
internal object TemperatureTestAccess {
 @Volatile var state:ClockState?=null
 @Volatile var renderer:TemperatureRenderer?=null
}
