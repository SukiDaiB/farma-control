package com.meusremedios.app.ui.formulario

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meusremedios.app.data.FormaRemedio
import com.meusremedios.app.data.Medicamento
import com.meusremedios.app.data.RemediosRepositorio
import com.meusremedios.app.domain.Agenda
import com.meusremedios.app.domain.formatado
import com.meusremedios.app.domain.paraNumero
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** Campos numéricos ficam como texto para permitir edição livre (ex.: "0," enquanto digita). */
data class Formulario(
    val nome: String = "",
    val dosagem: String = "",
    val forma: FormaRemedio = FormaRemedio.COMPRIMIDO,
    val quantidadePorDose: String = "1",
    val horarios: List<LocalTime> = emptyList(),
    val diasSemana: Int = Agenda.TODOS_OS_DIAS,
    val dataInicio: LocalDate = LocalDate.now(),
    val dataFim: LocalDate? = null,
    val estoque: String = "",
    val alertaDias: String = "7",
    val observacoes: String = "",
)

class FormularioViewModel(
    private val repositorio: RemediosRepositorio,
    private val id: Long,
) : ViewModel() {
    val editando: Boolean = id != 0L

    var formulario by mutableStateOf(Formulario())
        private set
    var carregando by mutableStateOf(editando)
        private set
    var erro by mutableStateOf<String?>(null)
        private set
    var concluido by mutableStateOf(false)
        private set

    /** Preservado ao editar, para não perder campos que o formulário não mostra. */
    private var original: Medicamento? = null

    init {
        if (editando) {
            viewModelScope.launch {
                repositorio.buscar(id)?.let { (m, horarios) ->
                    original = m
                    formulario = Formulario(
                        nome = m.nome,
                        dosagem = m.dosagem,
                        forma = m.forma,
                        quantidadePorDose = m.quantidadePorDose.formatado(),
                        horarios = horarios.map { it.hora }.sorted(),
                        diasSemana = m.diasSemana,
                        dataInicio = m.dataInicio,
                        dataFim = m.dataFim,
                        estoque = m.estoque.coerceAtLeast(0.0).formatado(),
                        alertaDias = m.alertaDias.toString(),
                        observacoes = m.observacoes,
                    )
                }
                carregando = false
            }
        }
    }

    fun alterar(mudanca: (Formulario) -> Formulario) {
        formulario = mudanca(formulario)
        erro = null
    }

    fun adicionarHorario(hora: LocalTime) = alterar {
        it.copy(horarios = (it.horarios + hora).distinct().sorted())
    }

    fun removerHorario(hora: LocalTime) = alterar { it.copy(horarios = it.horarios - hora) }

    fun salvar() {
        val f = formulario
        val quantidade = f.quantidadePorDose.paraNumero()
        val estoque = if (f.estoque.isBlank()) 0.0 else f.estoque.paraNumero()
        val alerta = f.alertaDias.trim().toIntOrNull()

        erro = when {
            f.nome.isBlank() -> "Informe o nome do remédio."
            quantidade == null || quantidade <= 0 -> "Informe quanto tomar por dose."
            f.horarios.isEmpty() -> "Adicione pelo menos um horário."
            f.diasSemana == 0 -> "Escolha pelo menos um dia da semana."
            f.dataFim != null && f.dataFim.isBefore(f.dataInicio) -> "A data de término é anterior ao início."
            estoque == null || estoque < 0 -> "Estoque inválido."
            alerta == null || alerta < 0 -> "Dias para o aviso de estoque inválido."
            else -> null
        }
        if (erro != null) return

        val medicamento = (original ?: Medicamento(
            nome = "", dosagem = "", forma = f.forma, quantidadePorDose = 1.0, estoque = 0.0,
            alertaDias = 7, diasSemana = Agenda.TODOS_OS_DIAS, dataInicio = f.dataInicio,
            dataFim = null, observacoes = "",
        )).copy(
            nome = f.nome.trim(),
            dosagem = f.dosagem.trim(),
            forma = f.forma,
            quantidadePorDose = quantidade!!,
            estoque = estoque!!,
            alertaDias = alerta!!,
            diasSemana = f.diasSemana,
            dataInicio = f.dataInicio,
            dataFim = f.dataFim,
            observacoes = f.observacoes.trim(),
        )

        viewModelScope.launch {
            repositorio.salvar(medicamento, f.horarios)
            concluido = true
        }
    }

    fun excluir() {
        viewModelScope.launch {
            repositorio.arquivar(id)
            concluido = true
        }
    }
}
