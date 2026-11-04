package com.meusremedios.app.ui.remedios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meusremedios.app.data.Medicamento
import com.meusremedios.app.data.RemediosRepositorio
import com.meusremedios.app.domain.Agenda
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

data class ItemRemedio(
    val medicamento: Medicamento,
    val horarios: List<LocalTime>,
    val diasRestantes: Int?,
    val estoqueBaixo: Boolean,
)

class RemediosViewModel(private val repositorio: RemediosRepositorio) : ViewModel() {
    fun excluir(id: Long) {
        viewModelScope.launch { repositorio.arquivar(id) }
    }

    fun registrarCompra(id: Long, quantidade: Double) {
        viewModelScope.launch { repositorio.registrarCompra(id, quantidade) }
    }

    val itens: StateFlow<List<ItemRemedio>?> = repositorio.observarMedicamentos()
        .map { lista ->
            lista.map { (medicamento, horarios) ->
                ItemRemedio(
                    medicamento = medicamento,
                    horarios = horarios.map { it.hora }.sorted(),
                    diasRestantes = Agenda.diasRestantes(medicamento, horarios.size),
                    estoqueBaixo = Agenda.estoqueBaixo(medicamento, horarios.size),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
