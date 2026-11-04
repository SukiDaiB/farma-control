package com.meusremedios.app.ui.ajustes

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.meusremedios.app.RemediosApp
import com.meusremedios.app.alarme.Permissoes

private data class ItemAjuste(
    val titulo: String,
    val descricao: String,
    /** null = o sistema não informa; mostramos só o botão. */
    val ok: Boolean?,
    val abrir: (Context) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as RemediosApp
    val prefs = app.preferencias

    // Reconfere as permissões sempre que a pessoa volta dos ajustes do sistema
    var verificacao by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        verificacao++
        onPauseOrDispose { }
    }
    val permissoes = remember(verificacao) {
        listOf(
            ItemAjuste(
                "Notificações",
                "Necessário para o alarme aparecer.",
                Permissoes.notificacoes(context),
                Permissoes::abrirNotificacoes,
            ),
            ItemAjuste(
                "Alarmes e lembretes",
                "Permite tocar no horário exato.",
                Permissoes.alarmesExatos(context),
                Permissoes::abrirAlarmesExatos,
            ),
            ItemAjuste(
                "Tela cheia",
                "Abre o alarme por cima da tela de bloqueio.",
                Permissoes.telaCheia(context),
                Permissoes::abrirTelaCheia,
            ),
            ItemAjuste(
                "Bateria sem restrições",
                "Impede que o sistema feche o app e atrase o alarme.",
                Permissoes.bateriaLiberada(context),
                Permissoes::abrirBateria,
            ),
        )
    }

    var duracao by remember { mutableIntStateOf(prefs.duracaoToqueSegundos) }
    var intervalo by remember { mutableIntStateOf(prefs.intervaloRepeticaoMinutos) }
    var repeticoes by remember { mutableIntStateOf(prefs.repeticoes) }
    var adiar by remember { mutableIntStateOf(prefs.adiarMinutos) }
    var horaResumo by remember { mutableIntStateOf(prefs.horaResumoEstoque) }
    var antecedencia by remember { mutableIntStateOf(prefs.antecedenciaValidadeDias) }

    Scaffold(topBar = { TopAppBar(title = { Text("Ajustes") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Secao("Permissões do alarme")
            permissoes.forEach { LinhaAjuste(it) }

            if (Permissoes.ehXiaomi) {
                Secao("Ajustes do Xiaomi")
                Text(
                    "O HyperOS fecha apps em segundo plano. O sistema não informa se estes ajustes estão ativos, então confira manualmente.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinhaAjuste(
                    ItemAjuste(
                        "Início automático",
                        "Ative para o alarme voltar a funcionar depois de reiniciar o celular.",
                        null,
                        Permissoes::abrirInicioAutomaticoXiaomi,
                    ),
                )
                LinhaAjuste(
                    ItemAjuste(
                        "Tela de bloqueio e pop-ups",
                        "Ative \"Mostrar na tela de bloqueio\" e \"Abrir novas janelas em segundo plano\".",
                        null,
                        Permissoes::abrirOutrasPermissoesXiaomi,
                    ),
                )
            }

            Secao("Alarme")
            Ajustador("Tocar durante", duracao, "s", 15, 300, 15) { duracao = it; prefs.duracaoToqueSegundos = it }
            Ajustador("Sem resposta, repetir a cada", intervalo, "min", 1, 30, 1) { intervalo = it; prefs.intervaloRepeticaoMinutos = it }
            Ajustador("Número de repetições", repeticoes, "", 0, 10, 1) { repeticoes = it; prefs.repeticoes = it }
            Ajustador("Botão Adiar", adiar, "min", 5, 60, 5) { adiar = it; prefs.adiarMinutos = it }

            Secao("Estoque e receitas")
            Ajustador("Resumo diário às", horaResumo, "h", 6, 22, 1) {
                horaResumo = it
                prefs.horaResumoEstoque = it
                app.avisosEstoque.agendarResumoDiario(substituir = true)
            }
            Ajustador("Avisar vencimento da receita com", antecedencia, "dias", 1, 30, 1) {
                antecedencia = it
                prefs.antecedenciaValidadeDias = it
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    app.agendador.agendarTeste(10)
                    Toast.makeText(context, "O alarme vai tocar em 10 segundos. Pode bloquear a tela.", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Testar alarme")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Secao(titulo: String) {
    Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun LinhaAjuste(item: ItemAjuste) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            when (item.ok) {
                true -> Icon(Icons.Filled.CheckCircle, contentDescription = "Ativo", tint = MaterialTheme.colorScheme.primary)
                false -> Icon(Icons.Filled.Warning, contentDescription = "Pendente", tint = MaterialTheme.colorScheme.error)
                null -> {}
            }
            if (item.ok != null) Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.titulo, style = MaterialTheme.typography.titleSmall)
                Text(item.descricao, style = MaterialTheme.typography.bodySmall)
            }
            if (item.ok != true) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { item.abrir(context) }) { Text("Ajustar") }
            }
        }
    }
}

@Composable
private fun Ajustador(
    rotulo: String,
    valor: Int,
    unidade: String,
    minimo: Int,
    maximo: Int,
    passo: Int,
    onMudar: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedIconButton(onClick = { onMudar((valor - passo).coerceAtLeast(minimo)) }, enabled = valor > minimo) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "$valor $unidade".trim(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.Center,
        )
        OutlinedIconButton(onClick = { onMudar((valor + passo).coerceAtMost(maximo)) }, enabled = valor < maximo) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}
