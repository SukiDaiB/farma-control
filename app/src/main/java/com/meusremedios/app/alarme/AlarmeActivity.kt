package com.meusremedios.app.alarme

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meusremedios.app.RemediosApp
import com.meusremedios.app.domain.formatada
import com.meusremedios.app.domain.formatado
import com.meusremedios.app.ui.theme.TemaRemedios
import java.time.LocalTime

/** Tela que abre por cima da tela de bloqueio quando o alarme toca. */
class AlarmeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        val adiarMinutos = (application as RemediosApp).preferencias.adiarMinutos
        setContent {
            TemaRemedios {
                val alarme by AlarmeService.tocando.collectAsStateWithLifecycle()
                var jaMostrou by remember { mutableStateOf(false) }
                // Fecha sozinha quando o alarme for resolvido (aqui, na notificação ou no app)
                LaunchedEffect(alarme) {
                    if (alarme != null) jaMostrou = true else if (jaMostrou) finish()
                }
                TelaAlarme(
                    alarme = alarme,
                    adiarMinutos = adiarMinutos,
                    onAcao = { acao -> startService(AlarmeService.intent(this, acao, alarme?.horarios.orEmpty())) },
                    onFechar = ::finish,
                )
            }
        }
    }
}

@Composable
private fun TelaAlarme(
    alarme: Alarme?,
    adiarMinutos: Int,
    onAcao: (String) -> Unit,
    onFechar: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                LocalTime.now().formatada(),
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                if (alarme?.teste == true) "Alarme de teste" else "Hora do remédio",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(24.dp))

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    alarme == null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    alarme.teste -> Text(
                        "Se você está ouvindo o som, o alarme está funcionando.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    else -> alarme.doses.forEach { dose ->
                        val m = dose.medicamento
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    listOf(m.nome, m.dosagem).filter { it.isNotBlank() }.joinToString(" "),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    "${m.quantidadePorDose.formatado()} ${m.forma.unidade} · ${dose.horario.toLocalTime().formatada()}",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                if (m.observacoes.isNotBlank()) {
                                    Text(m.observacoes, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(
                                    "Restam ${m.estoque.coerceAtLeast(0.0).formatado()} ${m.forma.unidade}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (m.estoque <= m.quantidadePorDose) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (alarme == null || alarme.teste) {
                Button(
                    onClick = { if (alarme == null) onFechar() else onAcao(AlarmeService.ACAO_PARAR) },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) {
                    Text(if (alarme == null) "Fechar" else "Parar", style = MaterialTheme.typography.titleLarge)
                }
            } else {
                Button(
                    onClick = { onAcao(AlarmeService.ACAO_TOMEI) },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) {
                    Text("Tomei", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { onAcao(AlarmeService.ACAO_ADIAR) },
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text("Adiar $adiarMinutos min") }
                    OutlinedButton(
                        onClick = { onAcao(AlarmeService.ACAO_PULAR) },
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text("Pular") }
                }
            }
        }
    }
}
