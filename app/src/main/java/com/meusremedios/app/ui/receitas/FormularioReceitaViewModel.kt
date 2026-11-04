package com.meusremedios.app.ui.receitas

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meusremedios.app.RemediosApp
import com.meusremedios.app.data.Medicamento
import com.meusremedios.app.data.Receita
import com.meusremedios.app.receitas.ArquivoReceita
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FormularioReceita(
    val titulo: String = "Receita",
    val medico: String = "",
    val dataEmissao: LocalDate = LocalDate.now(),
    val validade: LocalDate? = null,
    val observacoes: String = "",
    val medicamentos: Set<Long> = emptySet(),
    val arquivo: ArquivoReceita? = null,
)

class FormularioReceitaViewModel(app: RemediosApp, private val id: Long) : ViewModel() {
    private val receitas = app.receitas
    private val arquivos = app.receitas.arquivos
    val editando: Boolean = id != 0L

    var formulario by mutableStateOf(FormularioReceita())
        private set
    var carregando by mutableStateOf(editando)
        private set
    var importando by mutableStateOf(false)
        private set
    var erro by mutableStateOf<String?>(null)
        private set
    var concluido by mutableStateOf(false)
        private set

    val remedios: StateFlow<List<Medicamento>> = app.repositorio.observarMedicamentos()
        .map { lista -> lista.map { it.medicamento } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var original: Receita? = null

    /** Arquivos importados nesta edição que ainda não foram salvos (apagados se desistir). */
    private val importadosSemSalvar = mutableSetOf<String>()

    init {
        if (editando) {
            viewModelScope.launch {
                receitas.buscar(id)?.let { (r, meds) ->
                    original = r
                    formulario = FormularioReceita(
                        titulo = r.titulo,
                        medico = r.medico,
                        dataEmissao = r.dataEmissao,
                        validade = r.validade,
                        observacoes = r.observacoes,
                        medicamentos = meds.map { it.id }.toSet(),
                        arquivo = ArquivoReceita(r.arquivo, r.tipoArquivo),
                    )
                }
                carregando = false
            }
        }
    }

    fun alterar(mudanca: (FormularioReceita) -> FormularioReceita) {
        formulario = mudanca(formulario)
        erro = null
    }

    fun alternarRemedio(medicamentoId: Long) = alterar {
        it.copy(medicamentos = if (medicamentoId in it.medicamentos) it.medicamentos - medicamentoId else it.medicamentos + medicamentoId)
    }

    fun uriParaCamera(): Uri = arquivos.uriParaCamera()

    fun aoTirarFoto(sucesso: Boolean) {
        if (sucesso) importar { arquivos.importarFotoDaCamera() }
    }

    fun aoEscolherArquivo(uri: Uri?) {
        if (uri != null) importar { arquivos.importar(uri) }
    }

    private fun importar(bloco: suspend () -> ArquivoReceita) {
        importando = true
        erro = null
        viewModelScope.launch {
            try {
                val novo = bloco()
                importadosSemSalvar += novo.nome
                formulario = formulario.copy(arquivo = novo)
            } catch (e: Exception) {
                Log.w("FormularioReceita", "Falha ao importar arquivo", e)
                erro = "Não foi possível abrir esse arquivo. Tente uma foto ou um PDF."
            } finally {
                importando = false
            }
        }
    }

    fun salvar() {
        val f = formulario
        val arquivo = f.arquivo
        erro = when {
            arquivo == null -> "Tire uma foto ou escolha o arquivo da receita."
            f.titulo.isBlank() -> "Dê um nome para a receita."
            f.validade != null && f.validade.isBefore(f.dataEmissao) -> "A validade é anterior à data da receita."
            else -> null
        }
        if (erro != null || arquivo == null) return

        val receita = Receita(
            id = original?.id ?: 0,
            titulo = f.titulo.trim(),
            medico = f.medico.trim(),
            dataEmissao = f.dataEmissao,
            validade = f.validade,
            observacoes = f.observacoes.trim(),
            arquivo = arquivo.nome,
            tipoArquivo = arquivo.tipo,
        )
        viewModelScope.launch {
            receitas.salvar(receita, f.medicamentos)
            // Arquivo trocado: apaga o antigo e os importados que não foram usados
            original?.arquivo?.takeIf { it != arquivo.nome }?.let(arquivos::apagar)
            (importadosSemSalvar - arquivo.nome).forEach(arquivos::apagar)
            importadosSemSalvar.clear()
            concluido = true
        }
    }

    override fun onCleared() {
        importadosSemSalvar.forEach(arquivos::apagar)
    }
}
