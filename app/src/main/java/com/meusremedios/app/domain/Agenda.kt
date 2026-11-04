package com.meusremedios.app.domain

import com.meusremedios.app.data.Medicamento
import com.meusremedios.app.data.MedicamentoComHorarios
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.floor

data class DosePrevista(val medicamento: Medicamento, val horario: LocalDateTime)

enum class AvisoEstoque { ACABANDO, ACABOU }

object Agenda {
    const val TODOS_OS_DIAS = 0b111_1111

    fun bitDoDia(dia: DayOfWeek): Int = 1 shl (dia.value - 1)

    fun tomaNoDia(medicamento: Medicamento, data: LocalDate): Boolean =
        medicamento.ativo && previstoPeloPeriodo(medicamento, data)

    /** Se a data cai no período e nos dias da semana do remédio (sem olhar se está ativo). */
    fun previstoPeloPeriodo(medicamento: Medicamento, data: LocalDate): Boolean {
        val fim = medicamento.dataFim
        return !data.isBefore(medicamento.dataInicio) &&
            (fim == null || !data.isAfter(fim)) &&
            (medicamento.diasSemana and bitDoDia(data.dayOfWeek)) != 0
    }

    fun dosesDoDia(itens: List<MedicamentoComHorarios>, data: LocalDate): List<DosePrevista> =
        itens
            .filter { tomaNoDia(it.medicamento, data) }
            .flatMap { item -> item.horarios.map { DosePrevista(item.medicamento, data.atTime(it.hora)) } }
            .sortedWith(compareBy({ it.horario }, { it.medicamento.nome.lowercase() }))

    /** Consumo médio diário, considerando os dias da semana em que o remédio é tomado. */
    fun consumoDiario(medicamento: Medicamento, horariosPorDia: Int): Double =
        horariosPorDia * medicamento.quantidadePorDose * Integer.bitCount(medicamento.diasSemana) / 7.0

    /** Quantos dias o estoque atual ainda dura, ou null se não há consumo previsto. */
    fun diasRestantes(medicamento: Medicamento, horariosPorDia: Int): Int? {
        val consumo = consumoDiario(medicamento, horariosPorDia)
        if (consumo <= 0.0) return null
        return floor(medicamento.estoque.coerceAtLeast(0.0) / consumo).toInt()
    }

    fun estoqueBaixo(medicamento: Medicamento, horariosPorDia: Int): Boolean {
        val dias = diasRestantes(medicamento, horariosPorDia) ?: return false
        return dias <= medicamento.alertaDias
    }

    /**
     * Depois de consumir [consumido] (o estoque do [medicamento] já descontado), diz se
     * vale avisar: só no momento em que o estoque cruza o limite ou acaba, para não repetir a cada dose.
     */
    fun avisoAposConsumo(medicamento: Medicamento, horariosPorDia: Int, consumido: Double): AvisoEstoque? {
        val antes = medicamento.copy(estoque = medicamento.estoque + consumido)
        return when {
            medicamento.estoque <= 0.0 && antes.estoque > 0.0 -> AvisoEstoque.ACABOU
            estoqueBaixo(medicamento, horariosPorDia) && !estoqueBaixo(antes, horariosPorDia) -> AvisoEstoque.ACABANDO
            else -> null
        }
    }
}
