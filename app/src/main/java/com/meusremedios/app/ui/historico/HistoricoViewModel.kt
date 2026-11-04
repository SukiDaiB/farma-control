package com.meusremedios.app.ui.historico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meusremedios.app.data.Medicamento
import com.meusremedios.app.data.RemediosRepositorio
import com.meusremedios.app.domain.DoseHistorico
import com.meusremedios.app.domain.Historico
import com.meusremedios.app.domain.ResumoAdesao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class Periodo(val dias: Int, val rotulo: String) {
    SEMANA(7, "7 dias"),
    MES(30, "30 dias"),
    TRIMESTRE(90, "90 dias"),
}

/** Uma barra do gráfico: um dia, ou uma semana no período de 90 dias. */
data class Barra(val inicio: LocalDate, val fim: LocalDate, val resumo: ResumoAdesao)

data class DiaHistorico(val dia: LocalDate, val resumo: ResumoAdesao, val doses: List<DoseHistorico>)

data class RemedioHistorico(val medicamento: Medicamento, val resumo: ResumoAdesao)

data class HistoricoEstado(
    val periodo: Periodo,
    val inicio: LocalDate,
    val fim: LocalDate,
    val resumo: ResumoAdesao,
    val barras: List<Barra>,
    val remedios: List<RemedioHistorico>,
    val dias: List<DiaHistorico>,
    val carregando: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoricoViewModel(private val repositorio: RemediosRepositorio) : ViewModel() {
    private val periodo = MutableStateFlow(Periodo.SEMANA)

    /** Muda ao voltar para a tela, para recalcular com a hora atual. */
    private val atualizacao = MutableStateFlow(0)

    val estado: StateFlow<HistoricoEstado> = combine(periodo, atualizacao) { p, _ -> p }
        .flatMapLatest { p ->
            val hoje = LocalDate.now()
            val inicio = hoje.minusDays(p.dias - 1L)
            repositorio.observarHistorico(inicio).map { doses -> montar(p, inicio, hoje, doses) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), vazio(Periodo.SEMANA))

    fun escolher(p: Periodo) {
        periodo.value = p
    }

    fun atualizar() {
        atualizacao.value++
    }

    private fun montar(p: Periodo, inicio: LocalDate, fim: LocalDate, doses: List<DoseHistorico>): HistoricoEstado {
        val porDia = doses.groupBy { it.previsto.toLocalDate() }
        val dias = generateSequence(inicio) { it.plusDays(1) }.takeWhile { !it.isAfter(fim) }.toList()

        // Em 90 dias, uma barra por dia fica fina demais: agrupa por semana
        val tamanhoBarra = if (p == Periodo.TRIMESTRE) 7 else 1
        val barras = dias.chunked(tamanhoBarra).map { bloco ->
            Barra(bloco.first(), bloco.last(), Historico.resumir(bloco.flatMap { porDia[it].orEmpty() }))
        }

        val remedios = doses.groupBy { it.medicamento.id }.values
            .map { RemedioHistorico(it.first().medicamento, Historico.resumir(it)) }
            .sortedWith(compareBy({ it.resumo.adesao ?: 1.0 }, { it.medicamento.nome.lowercase() }))

        return HistoricoEstado(
            periodo = p,
            inicio = inicio,
            fim = fim,
            resumo = Historico.resumir(doses),
            barras = barras,
            remedios = remedios,
            dias = dias.reversed()
                .mapNotNull { dia -> porDia[dia]?.let { DiaHistorico(dia, Historico.resumir(it), it.sortedBy { d -> d.previsto }) } },
        )
    }

    private fun vazio(p: Periodo): HistoricoEstado {
        val hoje = LocalDate.now()
        return HistoricoEstado(
            p, hoje, hoje, ResumoAdesao(0, 0, 0, 0), emptyList(), emptyList(), emptyList(), carregando = true,
        )
    }
}
