package com.meusremedios.app.ui.hoje

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meusremedios.app.alarme.Permissoes
import com.meusremedios.app.data.StatusDose
import com.meusremedios.app.domain.descreverDuracao
import com.meusremedios.app.domain.formatada
import com.meusremedios.app.estoque.RemedioAcabando
import com.meusremedios.app.domain.formatado
import com.meusremedios.app.domain.porExtenso
import com.meusremedios.app.R
import com.meusremedios.app.data.Receita
import com.meusremedios.app.domain.Validade
import com.meusremedios.app.fabricaComApp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import java.time.LocalDate
import com.meusremedios.app.ui.componentes.MenuOpcoes
import com.meusremedios.app.ui.componentes.OpcaoMenu
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojeScreen(
    onCadastrar: () -> Unit,
    onEditar: (Long) -> Unit,
    onAjustes: () -> Unit,
    onVerRemedios: () -> Unit,
    onVerReceitas: () -> Unit,
    viewModel: HojeViewModel = viewModel(
        factory = fabricaComApp { HojeViewModel(it.repositorio, it.receitas, it.preferencias) },
    ),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var alarmesOk by remember { mutableStateOf(true) }

    LifecycleResumeEffect(Unit) {
        viewModel.atualizarData()
        alarmesOk = Permissoes.tudoCerto(context)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Hoje")
                        Text(estado.data.porExtenso(), style = MaterialTheme.typography.bodyMedium)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!alarmesOk) AvisoPermissoes(onAjustes)
            if (estado.acabando.isNotEmpty()) AvisoAcabando(estado.acabando, onVerRemedios)
            if (estado.receitasVencendo.isNotEmpty()) AvisoReceitas(estado.receitasVencendo, onVerReceitas)
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    estado.carregando -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    estado.doses.isEmpty() -> SemDoses(onCadastrar, Modifier.align(Alignment.Center))
                    else -> ListaDoses(estado, viewModel, onEditar)
                }
            }
        }
    }
}

@Composable
private fun AvisoAcabando(acabando: List<RemedioAcabando>, onVerRemedios: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Acabando",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                acabando.forEach {
                    Text(
                        "${it.medicamento.nome}: ${descreverDuracao(it.diasRestantes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            TextButton(onClick = onVerRemedios) { Text("Ver") }
        }
    }
}

@Composable
private fun AvisoReceitas(receitas: List<Receita>, onVerReceitas: () -> Unit) {
    val hoje = LocalDate.now()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                ImageVector.vectorResource(R.drawable.ic_receita),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Receitas",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                receitas.forEach {
                    Text(
                        "${it.titulo}: ${Validade.descrever(it.validade, hoje)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            TextButton(onClick = onVerReceitas) { Text("Ver") }
        }
    }
}

@Composable
private fun AvisoPermissoes(onAjustes: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                "Falta liberar permissões para o alarme tocar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAjustes) { Text("Ajustar") }
        }
    }
}

@Composable
private fun ListaDoses(estado: HojeEstado, viewModel: HojeViewModel, onEditar: (Long) -> Unit) {
    val agora = LocalDateTime.now()
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 4.dp)) {
                Text(
                    "${estado.tomadas} de ${estado.doses.size} doses tomadas",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { estado.tomadas.toFloat() / estado.doses.size },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        items(estado.doses, key = { "${it.dose.medicamento.id}-${it.dose.horario}" }) { item ->

            CartaoDose(
                item = item,
                atrasada = item.registro == null && item.dose.horario.isBefore(agora),
                onTomei = { viewModel.marcar(item.dose, StatusDose.TOMADA) },
                onPular = { viewModel.marcar(item.dose, StatusDose.PULADA) },
                onDesfazer = { viewModel.desfazer(item.dose) },
                onEditar = { onEditar(item.dose.medicamento.id) },
            )
        }
    }
}

@Composable
private fun CartaoDose(
    item: DoseDoDia,
    atrasada: Boolean,
    onTomei: () -> Unit,
    onPular: () -> Unit,
    onDesfazer: () -> Unit,
    onEditar: () -> Unit,
) {
    val medicamento = item.dose.medicamento
    val registro = item.registro
    val cores = when (registro?.status) {
        StatusDose.TOMADA -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        StatusDose.PULADA -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        null -> CardDefaults.cardColors()
    }

    Card(colors = cores, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 16.dp, end = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.Top) {
            Text(
                item.dose.horario.toLocalTime().formatada(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOf(medicamento.nome, medicamento.dosagem).filter { it.isNotBlank() }.joinToString(" "),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${medicamento.quantidadePorDose.formatado()} ${medicamento.forma.unidade}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (medicamento.observacoes.isNotBlank()) {
                    Text(medicamento.observacoes, style = MaterialTheme.typography.bodySmall)
                }
                when {
                    registro?.status == StatusDose.TOMADA -> Text(
                        "Tomada às ${registro.realizado?.toLocalTime()?.formatada() ?: "--:--"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    registro?.status == StatusDose.PULADA -> Text("Pulada", style = MaterialTheme.typography.labelLarge)
                    atrasada -> Text(
                        "Atrasada",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            MenuOpcoes(listOf(OpcaoMenu("Editar remédio", Icons.Filled.Edit, onEditar)))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (registro == null) {
                TextButton(onClick = onPular) { Text("Pular") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onTomei) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Tomei")
                }
            } else {
                TextButton(onClick = onDesfazer) { Text("Desfazer") }
            }
        }
    }
}

@Composable
private fun SemDoses(onCadastrar: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Nenhuma dose para hoje",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Cadastre seus remédios e horários para vê-los aqui.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onCadastrar) { Text("Cadastrar remédio") }
    }
}
