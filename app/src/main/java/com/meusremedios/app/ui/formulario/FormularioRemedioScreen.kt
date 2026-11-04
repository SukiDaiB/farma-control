package com.meusremedios.app.ui.formulario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meusremedios.app.data.FormaRemedio
import com.meusremedios.app.domain.Agenda
import com.meusremedios.app.domain.formatada
import com.meusremedios.app.domain.siglasDias
import com.meusremedios.app.fabricaComRepositorio
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormularioRemedioScreen(
    id: Long,
    onVoltar: () -> Unit,
    viewModel: FormularioViewModel = viewModel(factory = fabricaComRepositorio { FormularioViewModel(it, id) }),
) {
    var confirmarExclusao by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.concluido) {
        if (viewModel.concluido) onVoltar()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.editando) "Editar remédio" else "Novo remédio") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (viewModel.editando) {
                        IconButton(onClick = { confirmarExclusao = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Excluir")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (viewModel.carregando) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Campos(viewModel)
            }
        }
    }

    if (confirmarExclusao) {
        AlertDialog(
            onDismissRequest = { confirmarExclusao = false },
            title = { Text("Excluir remédio?") },
            text = { Text("Ele sai da lista e da agenda. O histórico de doses já registradas é mantido.") },
            confirmButton = {
                TextButton(onClick = { confirmarExclusao = false; viewModel.excluir() }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarExclusao = false }) { Text("Cancelar") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Campos(viewModel: FormularioViewModel) {
    val f = viewModel.formulario
    var escolhendoHora by remember { mutableStateOf(false) }
    var escolhendoInicio by remember { mutableStateOf(false) }
    var escolhendoFim by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = f.nome,
            onValueChange = { v -> viewModel.alterar { it.copy(nome = v) } },
            label = { Text("Nome do remédio") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = f.dosagem,
            onValueChange = { v -> viewModel.alterar { it.copy(dosagem = v) } },
            label = { Text("Dosagem (ex.: 500 mg)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Secao("Forma")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormaRemedio.entries.forEach { forma ->
                ChipSelecao(
                    selecionado = f.forma == forma,
                    onClick = { viewModel.alterar { it.copy(forma = forma) } },
                    rotulo = forma.rotulo,
                )
            }
        }
        OutlinedTextField(
            value = f.quantidadePorDose,
            onValueChange = { v -> viewModel.alterar { it.copy(quantidadePorDose = v) } },
            label = { Text("Quantidade por dose") },
            suffix = { Text(f.forma.unidade) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Secao("Horários")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            f.horarios.forEach { hora ->
                InputChip(
                    selected = false,
                    onClick = { viewModel.removerHorario(hora) },
                    label = { Text(hora.formatada()) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remover ${hora.formatada()}") },
                )
            }
            AssistChip(
                onClick = { escolhendoHora = true },
                label = { Text("Adicionar horário") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            )
        }

        Secao("Dias da semana")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipSelecao(
                selecionado = f.diasSemana == Agenda.TODOS_OS_DIAS,
                onClick = { viewModel.alterar { it.copy(diasSemana = Agenda.TODOS_OS_DIAS) } },
                rotulo = "Todos",
            )
            siglasDias.forEach { (dia, sigla) ->
                val bit = Agenda.bitDoDia(dia)
                ChipSelecao(
                    selecionado = f.diasSemana and bit != 0,
                    onClick = { viewModel.alterar { it.copy(diasSemana = it.diasSemana xor bit) } },
                    rotulo = sigla,
                )
            }
        }

        Secao("Período")
        OutlinedButton(onClick = { escolhendoInicio = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Início: ${f.dataInicio.formatada()}")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Uso contínuo", modifier = Modifier.weight(1f))
            Switch(
                checked = f.dataFim == null,
                onCheckedChange = { continuo ->
                    viewModel.alterar { it.copy(dataFim = if (continuo) null else it.dataInicio.plusDays(6)) }
                },
            )
        }
        f.dataFim?.let { fim ->
            OutlinedButton(onClick = { escolhendoFim = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Término: ${fim.formatada()}")
            }
        }

        Secao("Estoque")
        OutlinedTextField(
            value = f.estoque,
            onValueChange = { v -> viewModel.alterar { it.copy(estoque = v) } },
            label = { Text("Quantidade que você tem agora") },
            suffix = { Text(f.forma.unidade) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = f.alertaDias,
            onValueChange = { v -> viewModel.alterar { it.copy(alertaDias = v) } },
            label = { Text("Avisar quando durar menos de") },
            suffix = { Text("dias") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = f.observacoes,
            onValueChange = { v -> viewModel.alterar { it.copy(observacoes = v) } },
            label = { Text("Observações (ex.: tomar em jejum)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        viewModel.erro?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = viewModel::salvar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Salvar")
        }
        Spacer(Modifier.height(16.dp))
    }

    if (escolhendoHora) {
        SeletorHora(
            onConfirmar = { viewModel.adicionarHorario(it); escolhendoHora = false },
            onCancelar = { escolhendoHora = false },
        )
    }
    if (escolhendoInicio) {
        SeletorData(
            inicial = f.dataInicio,
            onConfirmar = { data -> viewModel.alterar { it.copy(dataInicio = data) }; escolhendoInicio = false },
            onCancelar = { escolhendoInicio = false },
        )
    }
    if (escolhendoFim) {
        SeletorData(
            inicial = f.dataFim ?: f.dataInicio,
            onConfirmar = { data -> viewModel.alterar { it.copy(dataFim = data) }; escolhendoFim = false },
            onCancelar = { escolhendoFim = false },
        )
    }
}

/** Chip com contraste forte quando selecionado: fundo na cor principal e ícone de check. */
@Composable
fun ChipSelecao(selecionado: Boolean, onClick: () -> Unit, rotulo: String) {
    FilterChip(
        selected = selecionado,
        onClick = onClick,
        label = { Text(rotulo) },
        leadingIcon = if (selecionado) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun Secao(titulo: String) {
    Text(titulo, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorHora(onConfirmar: (LocalTime) -> Unit, onCancelar: () -> Unit) {
    val estado = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Horário da dose") },
        text = { TimePicker(state = estado) },
        confirmButton = {
            TextButton(onClick = { onConfirmar(LocalTime.of(estado.hour, estado.minute)) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeletorData(inicial: LocalDate, onConfirmar: (LocalDate) -> Unit, onCancelar: () -> Unit) {
    // O DatePicker trabalha com milissegundos em UTC
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = inicial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onCancelar,
        confirmButton = {
            TextButton(onClick = {
                estado.selectedDateMillis?.let {
                    onConfirmar(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                } ?: onCancelar()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    ) {
        DatePicker(state = estado)
    }
}
