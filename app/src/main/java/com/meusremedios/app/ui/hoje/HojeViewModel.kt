package com.meusremedios.app.ui.hoje

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meusremedios.app.data.Preferencias
import com.meusremedios.app.data.Receita
import com.meusremedios.app.data.RegistroDose
import com.meusremedios.app.data.RemediosRepositorio
import com.meusremedios.app.data.StatusDose
import com.meusremedios.app.domain.Agenda
import com.meusremedios.app.domain.DosePrevista
import com.meusremedios.app.domain.SituacaoValidade
import com.meusremedios.app.domain.Validade
import com.meusremedios.app.estoque.RemedioAcabando
import com.meusremedios.app.receitas.ReceitasRepositorio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DoseDoDia(val dose: DosePrevista, val registro: RegistroDose?)

data class HojeEstado(
    val data: LocalDate,
    val doses: List<DoseDoDia> = emptyList(),
    val acabando: List<RemedioAcabando> = emptyList(),
    val receitasVencendo: List<Receita> = emptyList(),
    val carregando: Boolean = true,
) {
    val tomadas: Int get() = doses.count { it.registro?.status == StatusDose.TOMADA }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HojeViewModel(
    private val repositorio: RemediosRepositorio,
    receitas: ReceitasRepositorio,
    private val preferencias: Preferencias,
) : ViewModel() {
    private val data = MutableStateFlow(LocalDate.now())

    val estado: StateFlow<HojeEstado> = data
        .flatMapLatest { dia ->
            combine(
                repositorio.observarMedicamentos(),
                repositorio.observarRegistrosDoDia(dia),
                receitas.observar(),
            ) { medicamentos, registros, listaReceitas ->
                val porDose = registros.associateBy { it.medicamentoId to it.previsto }
                HojeEstado(
                    data = dia,
                    doses = Agenda.dosesDoDia(medicamentos, dia).map {
                        DoseDoDia(it, porDose[it.medicamento.id to it.horario])
                    },
                    acabando = medicamentos
                        .filter { (m, horarios) -> Agenda.estoqueBaixo(m, horarios.size) }
                        .map { (m, horarios) -> RemedioAcabando(m, Agenda.diasRestantes(m, horarios.size)) }
                        .sortedBy { it.diasRestantes ?: Int.MAX_VALUE },
                    receitasVencendo = listaReceitas.map { it.receita }.filter { r ->
                        // Vencendo, ou vencida há menos de uma semana
                        val situacao = Validade.situacao(r.validade, dia, preferencias.antecedenciaValidadeDias)
                        situacao == SituacaoValidade.VENCENDO ||
                            (situacao == SituacaoValidade.VENCIDA && r.validade!!.isAfter(dia.minusDays(7)))
                    }.sortedBy { it.validade },
                    carregando = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HojeEstado(LocalDate.now()))

    /** Chamado ao voltar para o app, para virar o dia depois da meia-noite. */
    fun atualizarData() {
        data.value = LocalDate.now()
    }

    fun marcar(dose: DosePrevista, status: StatusDose) {
        viewModelScope.launch { repositorio.registrar(dose, status) }
    }

    fun desfazer(dose: DosePrevista) {
        viewModelScope.launch { repositorio.desfazer(dose) }
    }
}
