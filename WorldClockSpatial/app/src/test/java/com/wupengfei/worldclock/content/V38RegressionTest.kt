package com.wupengfei.worldclock.content
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class V38RegressionTest {
 @Test fun draggingMovesPreviewButKeepsNowAtItsActualTime(){
  val s=ClockState();s.now=Instant.parse("2026-10-21T09:30:00Z")
  s.beginTimeDrag();s.dragTimeBy(90f);s.finishTimeDrag()
  assertEquals(0f,s.nowMarkerMinutes,0.0001f)
  assertEquals(90f,s.previewMarkerMinutes,0.0001f)
  s.beginTimeDrag();s.dragTimeBy(-120f);s.finishTimeDrag()
  assertEquals(0f,s.nowMarkerMinutes,0.0001f)
  assertEquals(-30f,s.previewMarkerMinutes,0.0001f)
 }
 @Test fun liveMarkerKeepsTickingWhilePreviewIsFrozen(){
  val s=ClockState();s.now=Instant.parse("2026-10-21T09:30:00Z")
  s.beginTimeDrag();s.dragTimeBy(60f);s.finishTimeDrag();val selected=s.instant
  s.now=s.now.plusSeconds(120)
  assertEquals(selected,s.instant)
  assertEquals(2f,s.nowMarkerMinutes,0.0001f)
  assertEquals(60f,s.previewMarkerMinutes,0.0001f)
 }
 @Test fun panningAndHistoricalDatesDoNotMislabelPreviewAsNow(){
  val s=ClockState();s.now=Instant.parse("2026-10-21T09:30:00Z")
  s.beginTimeDrag();s.dragTimeBy(400f);s.finishTimeDrag()
  assertEquals(400f,s.previewMarkerMinutes-s.nowMarkerMinutes,0.0001f)
  assertTrue(s.nowMarkerMinutes < -180f)
  s.shiftDay(1);assertTrue(s.nowMarkerMinutes < -1000f)
  assertEquals(0f,s.previewMarkerMinutes,0.0001f)
  s.live();assertEquals(0f,s.nowMarkerMinutes,0.0001f)
 }
}
