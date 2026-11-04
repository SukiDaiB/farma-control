package com.meusremedios.app.data

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class Conversores {
    // Data e hora "de parede", sem fuso: o remédio das 8h é às 8h onde você estiver.
    // Formato fixo para que a comparação de texto no SQLite ordene corretamente.
    private val formatoDataHora = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    @TypeConverter
    fun deLocalDate(valor: LocalDate?): Long? = valor?.toEpochDay()

    @TypeConverter
    fun paraLocalDate(valor: Long?): LocalDate? = valor?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun deLocalTime(valor: LocalTime?): Int? = valor?.toSecondOfDay()

    @TypeConverter
    fun paraLocalTime(valor: Int?): LocalTime? = valor?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter
    fun deLocalDateTime(valor: LocalDateTime?): String? = valor?.format(formatoDataHora)

    @TypeConverter
    fun paraLocalDateTime(valor: String?): LocalDateTime? = valor?.let { LocalDateTime.parse(it, formatoDataHora) }
}
