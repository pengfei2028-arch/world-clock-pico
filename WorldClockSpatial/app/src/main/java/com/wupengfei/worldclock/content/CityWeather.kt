package com.wupengfei.worldclock.content

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/** Current conditions are independent of the globe's date/time preview. */
internal data class CityWeather(val celsius:Double,val code:Int,val observedAt:Instant,val fetchedAt:Instant){
    val condition get()=when(code){
        0->"晴";1->"晴间多云";2->"多云";3->"阴";45,48->"雾"
        51->"小毛毛雨";53->"毛毛雨";55->"密集毛毛雨";56,57->"冻毛毛雨"
        61->"小雨";63->"中雨";65->"大雨";66,67->"冻雨"
        71->"小雪";73->"中雪";75->"大雪";77->"米雪"
        80->"小阵雨";81->"阵雨";82->"强阵雨";85,86->"阵雪"
        95->"雷雨";96,99->"雷雨伴冰雹";else->"天气未知"
    }
    val brief get()=celsius.roundToInt().toString()+"°C · "+condition
    fun usable(now:Instant)=Duration.between(observedAt,now).seconds in -1800..21600
    fun fresh(now:Instant)=Duration.between(observedAt,now).seconds in -1800..2700
}

internal data class CityWeatherState(val reading:CityWeather?=null,val failed:Boolean=false){
    fun label(now:Instant,compact:Boolean=false):String {
        val value=reading?.takeIf{it.usable(now)}
        if(value==null)return if(failed)"天气暂不可用" else "天气更新中"
        val prefix=if(failed||!value.fresh(now))"缓存 " else if(compact)"" else "当前 "
        return prefix+value.brief
    }
}
