package com.meusremedios.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val localeBr: Locale = Locale.forLanguageTag("pt-BR")
private val formatoHora = DateTimeFormatter.ofPattern("HH:mm")
private val formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val formatoDiaExtenso = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", localeBr)

fun LocalTime.formatada(): String = format(formatoHora)

fun LocalDate.formatada(): String = format(formatoData)

fun LocalDate.porExtenso(): String = format(formatoDiaExtenso).replaceFirstChar { it.titlecase(localeBr) }

/** 1.0 -> "1", 0.5 -> "0,5" */
fun Double.formatado(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString().replace('.', ',')

/** Aceita vírgula ou ponto como separador decimal. */
fun String.paraNumero(): Double? = trim().replace(',', '.').toDoubleOrNull()

/** "acabou", "dura ~1 dia", "dura ~5 dias" */
fun descreverDuracao(dias: Int?): String = when {
    dias == null -> ""
    dias <= 0 -> "acabou"
    dias == 1 -> "dura ~1 dia"
    else -> "dura ~$dias dias"
}

val siglasDias: List<Pair<DayOfWeek, String>> = listOf(
    DayOfWeek.MONDAY to "Seg",
    DayOfWeek.TUESDAY to "Ter",
    DayOfWeek.WEDNESDAY to "Qua",
    DayOfWeek.THURSDAY to "Qui",
    DayOfWeek.FRIDAY to "Sex",
    DayOfWeek.SATURDAY to "Sáb",
    DayOfWeek.SUNDAY to "Dom",
)

fun descreverDias(diasSemana: Int): String = when (diasSemana) {
    Agenda.TODOS_OS_DIAS -> "Todos os dias"
    0 -> "Nenhum dia"
    else -> siglasDias.filter { (dia, _) -> diasSemana and Agenda.bitDoDia(dia) != 0 }.joinToString(", ") { it.second }
}
