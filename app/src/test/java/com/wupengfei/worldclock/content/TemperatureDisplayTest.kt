package com.wupengfei.worldclock.content
import org.junit.Assert.*
import org.junit.Test

class TemperatureDisplayTest {
 @Test fun constantFieldRemainsConstantAndRawDataIsUntouched(){
  val grid=TemperatureGrid(24,13,ShortArray(24*13){2345});val filtered=TemperatureDisplay.smooth(grid)
  assertTrue(filtered.all{kotlin.math.abs(it-23.45f)<.001f});assertEquals(23.45,grid.sample(0.0,0.0)!!,.0001)
 }
 @Test fun missingRegionStaysMissingRatherThanCold(){
  val values=ShortArray(24*13){1000};values[6*24+12]=Short.MIN_VALUE
  val grid=TemperatureGrid(24,13,values);val filtered=TemperatureDisplay.smooth(grid)
  assertTrue(filtered[6*24+12].isNaN());assertEquals(10f,filtered[6*24+11],.001f)
  val pixels=TemperatureDisplay.pixels(grid,filtered);assertEquals(0,pixels[7*26+13] and 255)
 }
 @Test fun longitudeFilterWrapsAndTextureGuttersMatch(){
  val values=ShortArray(24*13);values[6*24]=3000
  val grid=TemperatureGrid(24,13,values);val filtered=TemperatureDisplay.smooth(grid)
  assertEquals(filtered[6*24+1],filtered[6*24+23],.0001f);assertTrue(filtered[6*24+23]>0f)
  val pixels=TemperatureDisplay.pixels(grid,filtered)
  for(y in 0 until 15){assertEquals(pixels[y*26+24],pixels[y*26]);assertEquals(pixels[y*26+1],pixels[y*26+25])}
  for(x in 0 until 26){assertEquals(pixels[x],pixels[26+x]);assertEquals(pixels[14*26+x],pixels[13*26+x])}
 }
}
