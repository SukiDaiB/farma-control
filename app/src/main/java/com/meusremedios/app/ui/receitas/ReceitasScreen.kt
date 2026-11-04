package com.meusremedios.app.ui.receitas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meusremedios.app.R
import com.meusremedios.app.RemediosApp
import com.meusremedios.app.data.Preferencias
import com.meusremedios.app.data.Receita
import com.meusremedios.app.data.ReceitaComRemedios
import com.meusremedios.app.domain.SituacaoValidade
import com.meusremedios.app.domain.Validade
import com.meusremedios.app.domain.formatada
import com.meusremedios.app.fabricaComApp
import com.meusremedios.app.receitas.ArquivoReceita
import com.meusremedios.app.receitas.ReceitasRepositorio
import com.meusremedios.app.ui.componentes.MenuOpcoes
import com.meusremedios.app.ui.componentes.OpcaoMenu
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReceitasViewModel(
    private val receitas: ReceitasRepositorio,
    preferencias: Preferencias,
) : ViewModel() {
    val antecedenciaDias: Int = preferencias.antecedenciaValidadeDias

    val itens: StateFlow<List<ReceitaComRemedios>?> = receitas.observar()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun excluir(receita: Receita) {
        viewModelScope.launch { receitas.excluir(receita) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceitasScreen(
    onNova: () -> Unit,
    onAbrir: (Long) -> Unit,
    onEditar: (Long) -> Unit,
    viewModel: ReceitasViewModel = viewModel(
        factory = fabricaComApp { ReceitasViewModel(it.receitas, it.preferencias) },
    ),
) {
    val itens by viewModel.itens.collectAsStateWithLifecycle()
    var excluindo by remember { mutableStateOf<Receita?>(null) }
    val context = LocalContext.current
    val arquivos = (context.applicationContext as RemediosApp).receitas.arquivos

    Scaffold(
        topBar = { TopAppBar(title = { Text("Receitas") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNova,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Adicionar") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val lista = itens
            when {
                lista == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                lista.isEmpty() -> Text(
                    "Nenhuma receita salva.\nToque em Adicionar para fotografar ou importar uma receita.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(lista, key = { it.receita.id }) { item ->
                        CartaoReceita(
                            item = item,
                            antecedenciaDias = viewModel.antecedenciaDias,
                            onAbrir = { onAbrir(item.receita.id) },
                            opcoes = listOf(
                                OpcaoMenu("Compartilhar", Icons.Filled.Share) { arquivos.compartilhar(context, item.receita) },
                                OpcaoMenu("Editar", Icons.Filled.Edit) { onEditar(item.receita.id) },
                                OpcaoMenu("Excluir", Icons.Filled.Delete) { excluindo = item.receita },
                            ),
                        )
                    }
                }
            }
        }
    }

    excluindo?.let { receita ->
        AlertDialog(
            onDismissRequest = { excluindo = null },
            title = { Text("Excluir ${receita.titulo}?") },
            text = { Text("O arquivo da receita será apagado do app.") },
            confirmButton = {
                TextButton(onClick = { viewModel.excluir(receita); excluindo = null }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { excluindo = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CartaoReceita(
    item: ReceitaComRemedios,
    antecedenciaDias: Int,
    onAbrir: () -> Unit,
    opcoes: List<OpcaoMenu>,
) {
    val r = item.receita
    Card(Modifier.fillMaxWidth().clickable(onClick = onAbrir)) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                ImageVector.vectorResource(R.drawable.ic_receita),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.titulo, style = MaterialTheme.typography.titleMedium)
                val tipo = if (r.tipoArquivo == ArquivoReceita.TIPO_PDF) "PDF" else "Foto"
                Text(
                    listOf(r.medico, r.dataEmissao.formatada(), tipo).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (item.medicamentos.isNotEmpty()) {
                    Text(item.medicamentos.joinToString(", ") { it.nome }, style = MaterialTheme.typography.bodySmall)
                }
                SeloValidade(r.validade, antecedenciaDias)
            }
            MenuOpcoes(opcoes)
        }
    }
}

@Composable
fun SeloValidade(validade: LocalDate?, antecedenciaDias: Int) {
    val hoje = LocalDate.now()
    val cor = when (Validade.situacao(validade, hoje, antecedenciaDias)) {
        SituacaoValidade.VENCIDA -> MaterialTheme.colorScheme.error
        SituacaoValidade.VENCENDO -> MaterialTheme.colorScheme.tertiary
        SituacaoValidade.VALIDA, SituacaoValidade.SEM_VALIDADE -> Color.Unspecified
    }
    Text(
        Validade.descrever(validade, hoje),
        style = MaterialTheme.typography.labelLarge,
        color = if (cor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else cor,
        modifier = Modifier.padding(top = 4.dp),
    )
}
