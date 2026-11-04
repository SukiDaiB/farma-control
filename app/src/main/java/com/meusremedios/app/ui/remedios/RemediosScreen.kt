package com.meusremedios.app.ui.remedios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meusremedios.app.domain.descreverDias
import com.meusremedios.app.domain.descreverDuracao
import com.meusremedios.app.domain.paraNumero
import com.meusremedios.app.domain.formatada
import com.meusremedios.app.domain.formatado
import com.meusremedios.app.fabricaComRepositorio
import com.meusremedios.app.ui.componentes.MenuOpcoes
import com.meusremedios.app.ui.componentes.OpcaoMenu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemediosScreen(
    onNovo: () -> Unit,
    onEditar: (Long) -> Unit,
    viewModel: RemediosViewModel = viewModel(factory = fabricaComRepositorio { RemediosViewModel(it) }),
) {
    val itens by viewModel.itens.collectAsStateWithLifecycle()
    var excluindo by remember { mutableStateOf<ItemRemedio?>(null) }
    var comprando by remember { mutableStateOf<ItemRemedio?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Meus remédios") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNovo,
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
                    "Nenhum remédio cadastrado.\nToque em Adicionar para começar.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                else -> LazyColumn(
                    // Espaço extra no fim para o botão flutuante não cobrir o último item
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(lista, key = { it.medicamento.id }) { item ->
                        CartaoRemedio(
                            item,
                            onEditar = { onEditar(item.medicamento.id) },
                            onExcluir = { excluindo = item },
                            onComprar = { comprando = item },
                        )
                    }
                }
            }
        }
    }

    excluindo?.let { item ->
        AlertDialog(
            onDismissRequest = { excluindo = null },
            title = { Text("Excluir ${item.medicamento.nome}?") },
            text = { Text("Ele sai da lista e da agenda. O histórico de doses já registradas é mantido.") },
            confirmButton = {
                TextButton(onClick = { viewModel.excluir(item.medicamento.id); excluindo = null }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { excluindo = null }) { Text("Cancelar") }
            },
        )
    }

    comprando?.let { item ->
        DialogoCompra(
            item = item,
            onConfirmar = { quantidade ->
                viewModel.registrarCompra(item.medicamento.id, quantidade)
                comprando = null
            },
            onCancelar = { comprando = null },
        )
    }
}

@Composable
private fun DialogoCompra(item: ItemRemedio, onConfirmar: (Double) -> Unit, onCancelar: () -> Unit) {
    val m = item.medicamento
    var texto by remember { mutableStateOf(m.ultimaCompra?.formatado().orEmpty()) }
    val quantidade = texto.paraNumero()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Comprei mais ${m.nome}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quantos ${m.forma.unidade} você comprou? Isso será somado ao estoque.")
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    label = { Text("Quantidade") },
                    suffix = { Text(m.forma.unidade) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                quantidade?.let {
                    Text(
                        "Estoque fica com ${(m.estoque.coerceAtLeast(0.0) + it).formatado()} ${m.forma.unidade}.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { quantidade?.let(onConfirmar) }, enabled = quantidade != null) { Text("Somar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

@Composable
private fun CartaoRemedio(item: ItemRemedio, onEditar: () -> Unit, onExcluir: () -> Unit, onComprar: () -> Unit) {
    val m = item.medicamento
    Card(Modifier.fillMaxWidth().clickable(onClick = onEditar)) {
        Column(Modifier.padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOf(m.nome, m.dosagem).filter { it.isNotBlank() }.joinToString(" "),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                MenuOpcoes(
                    listOf(
                        OpcaoMenu("Comprei mais", Icons.Filled.ShoppingCart, onComprar),
                        OpcaoMenu("Editar", Icons.Filled.Edit, onEditar),
                        OpcaoMenu("Excluir", Icons.Filled.Delete, onExcluir),
                    ),
                )
            }
            Text(
                "${m.quantidadePorDose.formatado()} ${m.forma.unidade} · ${item.horarios.joinToString(" · ") { it.formatada() }}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(descreverDias(m.diasSemana), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))

            val cor = if (item.estoqueBaixo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            val duracao = descreverDuracao(item.diasRestantes).let { if (it.isEmpty()) "" else " · $it" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.estoqueBaixo) {
                    Icon(Icons.Filled.Warning, contentDescription = "Estoque baixo", tint = cor)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    "Estoque: ${m.estoque.coerceAtLeast(0.0).formatado()} ${m.forma.unidade}$duracao",
                    style = MaterialTheme.typography.labelLarge,
                    color = cor,
                    modifier = Modifier.weight(1f),
                )
            }
            if (item.estoqueBaixo) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onComprar) {
                    Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Comprei mais")
                }
            }
        }
    }
}
