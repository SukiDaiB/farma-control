package com.meusremedios.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class SituacaoValidade { SEM_VALIDADE, VALIDA, VENCENDO, VENCIDA }

object Validade {
    fun situacao(validade: LocalDate?, hoje: LocalDate, antecedenciaDias: Int): SituacaoValidade = when {
        validade == null -> SituacaoValidade.SEM_VALIDADE
        hoje.isAfter(validade) -> SituacaoValidade.VENCIDA
        ChronoUnit.DAYS.between(hoje, validade) <= antecedenciaDias -> SituacaoValidade.VENCENDO
        else -> SituacaoValidade.VALIDA
    }

    /** Avisa todo dia enquanto está vencendo e uma última vez no dia seguinte ao vencimento. */
    fun deveAvisar(validade: LocalDate?, hoje: LocalDate, antecedenciaDias: Int): Boolean =
        situacao(validade, hoje, antecedenciaDias) == SituacaoValidade.VENCENDO || validade == hoje.minusDays(1)

    fun descrever(validade: LocalDate?, hoje: LocalDate): String {
        if (validade == null) return "Sem validade"
        val dias = ChronoUnit.DAYS.between(hoje, validade)
        return when {
            dias < 0 -> "Vencida em ${validade.formatada()}"
            dias == 0L -> "Vence hoje"
            dias == 1L -> "Vence amanhã"
            else -> "Vence em $dias dias (${validade.formatada()})"
        }
    }
}
