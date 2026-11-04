package com.meusremedios.app.domain

import com.meusremedios.app.data.Medicamento
import com.meusremedios.app.data.MedicamentoComHorarios
import com.meusremedios.app.data.RegistroDose
import com.meusremedios.app.data.StatusDose
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

enum class SituacaoDose { TOMADA, PULADA, ESQUECIDA }

data class DoseHistorico(
    val medicamento: Medicamento,
    val previsto: LocalDateTime,
    val situacao: SituacaoDose,
    val realizado: LocalDateTime?,
)

data class ResumoAdesao(
    val tomadas: Int,
    val puladas: Int,
    val esquecidas: Int,
    val noHorario: Int,
) {
    val total: Int get() = tomadas + puladas + esquecidas

    /** Fração das doses previstas que foram tomadas; null se não havia nenhuma. */
    val adesao: Double? get() = if (total == 0) null else tomadas.toDouble() / total

    /** Fração das tomadas que foram dentro da tolerância de horário. */
    val pontualidade: Double? get() = if (tomadas == 0) null else noHorario.toDouble() / tomadas
}

object Historico {
    /** Sem registro, a dose só conta como esquecida depois deste tempo (o alarme ainda pode estar repetindo). */
    val TOLERANCIA_ESQUECIDA: Duration = Duration.ofHours(1)

    /** Tomada até este tempo antes ou depois do previsto conta como "no horário". */
    val TOLERANCIA_HORARIO: Duration = Duration.ofHours(1)

    fun doses(
        itens: List<MedicamentoComHorarios>,
        registros: List<RegistroDose>,
        inicio: LocalDate,
        agora: LocalDateTime,
    ): List<DoseHistorico> {
        val porId = itens.associate { it.medicamento.id to it.medicamento }
        val porDose = registros.associateBy { it.medicamentoId to it.previsto }
        val limiteEsquecida = agora.minus(TOLERANCIA_ESQUECIDA)
        val resultado = mutableListOf<DoseHistorico>()
        val usados = mutableSetOf<Pair<Long, LocalDateTime>>()

        var dia = inicio
        while (!dia.isAfter(agora.toLocalDate())) {
            for ((m, horarios) in itens) {
                if (!contaNoDia(m, dia)) continue
                for (h in horarios) {
                    val previsto = dia.atTime(h.hora)
                    val registro = porDose[m.id to previsto]
                    when {
                        registro != null -> {
                            usados += m.id to previsto
                            resultado += DoseHistorico(m, previsto, registro.status.paraSituacao(), registro.realizado)
                        }
                        previsto.isBefore(limiteEsquecida) ->
                            resultado += DoseHistorico(m, previsto, SituacaoDose.ESQUECIDA, null)
                    }
                }
            }
            dia = dia.plusDays(1)
        }

        // Doses registradas em horários que depois foram mudados no cadastro continuam contando
        registros
            .filter { (it.medicamentoId to it.previsto) !in usados && !it.previsto.toLocalDate().isBefore(inicio) }
            .forEach { r ->
                porId[r.medicamentoId]?.let { resultado += DoseHistorico(it, r.previsto, r.status.paraSituacao(), r.realizado) }
            }

        return resultado.sortedWith(compareByDescending<DoseHistorico> { it.previsto }.thenBy { it.medicamento.nome.lowercase() })
    }

    fun resumir(doses: List<DoseHistorico>): ResumoAdesao = ResumoAdesao(
        tomadas = doses.count { it.situacao == SituacaoDose.TOMADA },
        puladas = doses.count { it.situacao == SituacaoDose.PULADA },
        esquecidas = doses.count { it.situacao == SituacaoDose.ESQUECIDA },
        noHorario = doses.count { it.situacao == SituacaoDose.TOMADA && noHorario(it) },
    )

    fun noHorario(dose: DoseHistorico): Boolean {
        val realizado = dose.realizado ?: return false
        return abs(Duration.between(dose.previsto, realizado).toMinutes()) <= TOLERANCIA_HORARIO.toMinutes()
    }

    /** Remédios excluídos contam até a véspera da exclusão; os antigos sem data não contam. */
    private fun contaNoDia(m: Medicamento, dia: LocalDate): Boolean {
        if (!Agenda.previstoPeloPeriodo(m, dia)) return false
        if (m.ativo) return true
        val arquivadoEm = m.arquivadoEm ?: return false
        return dia.isBefore(arquivadoEm)
    }

    private fun StatusDose.paraSituacao(): SituacaoDose = when (this) {
        StatusDose.TOMADA -> SituacaoDose.TOMADA
        StatusDose.PULADA -> SituacaoDose.PULADA
    }
}
