package com.wupengfei.worldclock.content

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Open-Meteo free endpoint is for non-commercial builds; attribution is in the city menu. */
internal class WeatherRepository(context:Context){
    private val appContext=context.applicationContext
    private data class Cached(val state:CityWeatherState,val attemptedAt:Instant)
    private val entries=ConcurrentHashMap<String,Cached>()
    suspend fun current(city:ClockCity,now:Instant):CityWeatherState=withContext(Dispatchers.IO){
        val key=city.id+"_"+city.latitude+"_"+city.longitude
        val preferences=appContext.getSharedPreferences("weather_open_meteo_v1",0)
        var cached=entries[key]
        if(cached==null){
            val saved=preferences.getString(key,null)
            val reading=try{saved?.let{decode(JSONObject(it))}}catch(_:Exception){null}
            if(reading!=null){cached=Cached(CityWeatherState(reading),reading.fetchedAt);entries[key]=cached}
        }
        val age=cached?.let{Duration.between(it.attemptedAt,now).seconds}
        val interval=if(cached?.state?.failed==true)300L else 900L
        if(age!=null&&age in 0 until interval)return@withContext cached!!.state
        try {
            val location=String.format(Locale.ROOT,"latitude=%.5f&longitude=%.5f",city.latitude,city.longitude)
            val connection=URL("https://api.open-meteo.com/v1/forecast?"+location+
                "&current=temperature_2m,weather_code&temperature_unit=celsius&timeformat=unixtime&timezone=GMT&forecast_days=1")
                .openConnection() as HttpURLConnection
            connection.connectTimeout=8000;connection.readTimeout=8000
            connection.setRequestProperty("Accept","application/json")
            val json=try {
                check(connection.responseCode==200){"HTTP "+connection.responseCode}
                connection.inputStream.bufferedReader().use{JSONObject(it.readText())}
            } finally {connection.disconnect()}
            val value=json.getJSONObject("current")
            check(!value.isNull("temperature_2m")&&!value.isNull("weather_code")&&!value.isNull("time"))
            val reading=CityWeather(value.getDouble("temperature_2m"),value.getInt("weather_code"),
                Instant.ofEpochSecond(value.getLong("time")),now)
            check(reading.celsius.isFinite()&&reading.celsius in -100.0..70.0&&reading.usable(now)){"Invalid current weather"}
            val state=CityWeatherState(reading)
            entries[key]=Cached(state,now)
            preferences.edit().putString(key,encode(reading).toString()).apply()
            Log.i("WorldClockWeather",city.name+": "+reading.brief+"; observed="+reading.observedAt)
            state
        }catch(cancel:CancellationException){throw cancel}
        catch(error:Exception){
            val state=CityWeatherState(cached?.state?.reading,true)
            entries[key]=Cached(state,now)
            Log.w("WorldClockWeather",city.name+": weather unavailable ("+error.javaClass.simpleName+")")
            state
        }
    }
    private fun encode(w:CityWeather)=JSONObject().put("celsius",w.celsius).put("code",w.code)
        .put("observedAt",w.observedAt.epochSecond).put("fetchedAt",w.fetchedAt.epochSecond)
    private fun decode(json:JSONObject)=CityWeather(json.getDouble("celsius"),json.getInt("code"),
        Instant.ofEpochSecond(json.getLong("observedAt")),Instant.ofEpochSecond(json.getLong("fetchedAt")))
}
