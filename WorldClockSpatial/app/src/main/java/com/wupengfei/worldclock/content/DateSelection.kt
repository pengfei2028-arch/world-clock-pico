package com.wupengfei.worldclock.content
import java.time.LocalDate
internal object DateSelection {
    fun withYear(date:LocalDate,year:Int):LocalDate = date.withYear(year.coerceIn(1900,2100))
    fun withMonth(date:LocalDate,month:Int):LocalDate = date.withMonth(month.coerceIn(1,12))
}
