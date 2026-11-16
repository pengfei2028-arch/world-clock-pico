package com.wupengfei.worldclock.content
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class V36RegressionTest {
 @Test fun scrollDateKeepsMonthEndsValid(){
  assertEquals(LocalDate.of(2027,2,28),DateSelection.withYear(LocalDate.of(2028,2,29),2027))
  assertEquals(LocalDate.of(2026,4,30),DateSelection.withMonth(LocalDate.of(2026,1,31),4))
 }
 @Test fun draggingPastVisibleSixHoursPansWithoutTimeJump(){
  val s=ClockState();s.now=Instant.parse("2026-10-21T09:30:00Z")
  s.beginTimeDrag();s.dragTimeBy(220f);s.finishTimeDrag()
  assertEquals(s.now.plusSeconds(220*60L),s.instant)
  assertTrue(Duration.between(s.rulerCenter,s.instant).toMinutes()<=170)
  val selected=s.instant;s.beginTimeDrag();assertEquals(selected,s.instant)
  s.dragTimeBy(-10f);s.finishTimeDrag();assertEquals(selected.minusSeconds(600),s.instant)
  s.live();assertNull(s.rulerCenter);assertEquals(s.now,s.instant)
 }
 @Test fun pinFadeIsContinuousAndFrameRateIndependent(){
  val a=PinVisibility();val b=PinVisibility()
  repeat(30){a.update(1f,1.0/60)};repeat(45){b.update(1f,1.0/90)}
  assertEquals(a.alpha.toDouble(),b.alpha.toDouble(),.00001)
  val before=a.alpha;a.update(0f,1.0/60);assertTrue(a.alpha>0f&&a.alpha<before)
  assertEquals(0f,PinVisibility.atLimb(0.0),.00001f)
  assertEquals(1f,PinVisibility.atLimb(.3),.00001f)
 }
}
