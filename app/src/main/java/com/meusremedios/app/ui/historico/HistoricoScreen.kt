package com.meusremedios.app.ui.historico

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meusremedios.app.domain.DoseHistorico
import com.meusremedios.app.domain.ResumoAdesao
import com.meusremedios.app.domain.SituacaoDose
import com.meusremedios.app.domain.formatada
import com.meusremedios.app.domain.porExtenso
import com.meusremedios.app.fabricaComRepositorio
import com.meusremedios.app.ui.formulario.ChipSelecao
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricoScreen(
    viewModel: HistoricoViewModel = viewModel(factory = fabricaComRepositorio { HistoricoViewModel(it) }),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.atualizar()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico") },
                actions = {
                    if (estado.resumo.total > 0) {
                        IconButton(onClick = { compartilharRelatorio(context, estado) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Compartilhar relatório")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Periodo.entries.forEach { p ->
                        ChipSelecao(selecionado = estado.periodo == p, onClick = { viewModel.escolher(p) }, rotulo = p.rotulo)
                    }
                }
            }

            if (!estado.carregando && estado.resumo.total == 0) {
                item {
                    Text(
                        "Ainda não há doses neste período. O histórico aparece conforme você registra as doses.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
                return@LazyColumn
            }

            item { CartaoResumo(estado.resumo) }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (estado.periodo == Periodo.TRIMESTRE) "Adesão por semana" else "Adesão por dia",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        GraficoAdesao(estado.barras)
                    }
                }
            }

            if (estado.remedios.isNotEmpty()) {
                item { Titulo("Por remédio") }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            estado.remedios.forEach { LinhaRemedio(it) }
                        }
                    }
                }
            }

            item { Titulo("Dia a dia") }
            items(estado.dias, key = { it.dia.toEpochDay() }) { CartaoDia(it) }
        }
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun CartaoResumo(resumo: ResumoAdesao) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Adesão", style = MaterialTheme.typography.titleMedium)
            Text(
                porcentagem(resumo.adesao),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${resumo.tomadas} de ${resumo.total} doses tomadas",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Contador(Icons.Filled.Check, "Tomadas", resumo.tomadas)
                Contador(Icons.AutoMirrored.Filled.ArrowForward, "Puladas", resumo.puladas)
                Contador(Icons.Filled.Warning, "Esquecidas", resumo.esquecidas)
            }
            resumo.pontualidade?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${porcentagem(it)} das doses tomadas no horário (até 1h de diferença)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Contador(icone: ImageVector, rotulo: String, valor: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icone, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text("$valor $rotulo", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun LinhaRemedio(item: RemedioHistorico) {
    val m = item.medicamento
    val r = item.resumo
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOf(m.nome, m.dosagem).filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(porcentagem(r.adesao), style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (r.adesao ?: 0.0).toFloat() },
            modifier = Modifier.fillMaxWidth(),
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(4.dp))
        Text(
            buildString {
                append("${r.tomadas} de ${r.total} tomadas")
                if (r.puladas > 0) append(" · ${r.puladas} pulada(s)")
                if (r.esquecidas > 0) append(" · ${r.esquecidas} esquecida(s)")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CartaoDia(dia: DiaHistorico) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dia.dia.porExtenso(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${dia.resumo.tomadas} de ${dia.resumo.total}", style = MaterialTheme.typography.labelLarge)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            dia.doses.forEach { LinhaDose(it) }
        }
    }
}

@Composable
private fun LinhaDose(dose: DoseHistorico) {
    val (icone, texto) = when (dose.situacao) {
        SituacaoDose.TOMADA -> Icons.Filled.Check to "Tomada às ${dose.realizado?.toLocalTime()?.formatada() ?: "--:--"}"
        SituacaoDose.PULADA -> Icons.AutoMirrored.Filled.ArrowForward to "Pulada"
        SituacaoDose.ESQUECIDA -> Icons.Filled.Warning to "Esquecida"
    }
    val cor = if (dose.situacao == SituacaoDose.ESQUECIDA) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            dose.previsto.toLocalTime().formatada(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(52.dp),
        )
        Text(dose.medicamento.nome, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall, color = cor)
    }
}

private fun porcentagem(fracao: Double?): String = fracao?.let { "${(it * 100).roundToInt()}%" } ?: "—"

/** Texto simples para mandar ao médico por WhatsApp, e-mail etc. */
private fun compartilharRelatorio(context: Context, estado: HistoricoEstado) {
    val r = estado.resumo
    val texto = buildString {
        appendLine("Relatório de adesão — ${estado.periodo.rotulo} (${estado.inicio.formatada()} a ${estado.fim.formatada()})")
        appendLine()
        appendLine("Geral: ${porcentagem(r.adesao)} — ${r.tomadas} de ${r.total} doses tomadas, ${r.puladas} pulada(s), ${r.esquecidas} esquecida(s).")
        r.pontualidade?.let { appendLine("No horário (até 1h): ${porcentagem(it)} das doses tomadas.") }
        appendLine()
        appendLine("Por remédio:")
        estado.remedios.forEach { (m, res) ->
            val nome = listOf(m.nome, m.dosagem).filter { it.isNotBlank() }.joinToString(" ")
            appendLine("• $nome: ${porcentagem(res.adesao)} (${res.tomadas} de ${res.total})")
        }
    }.trim()
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Relatório de adesão aos remédios")
        .putExtra(Intent.EXTRA_TEXT, texto)
    context.startActivity(Intent.createChooser(intent, "Compartilhar relatório"))
}
