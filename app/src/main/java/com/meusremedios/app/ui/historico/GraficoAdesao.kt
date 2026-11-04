package com.meusremedios.app.ui.historico

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val formatoDiaMes = DateTimeFormatter.ofPattern("dd/MM")
private val formatoSemana = DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("pt-BR"))

fun Barra.rotulo(): String =
    if (inicio == fim) "${inicio.format(formatoSemana).trimEnd('.').replaceFirstChar { it.uppercase() }} ${inicio.format(formatoDiaMes)}"
    else "${inicio.format(formatoDiaMes)} a ${fim.format(formatoDiaMes)}"

/**
 * Barras de adesão (0–100%) por dia ou semana. Toque numa barra para ver o valor.
 * Uma série só: cor principal; grade discreta em 50% e 100%; períodos sem doses ficam sem barra.
 */
@Composable
fun GraficoAdesao(barras: List<Barra>, modifier: Modifier = Modifier) {
    var selecionada by remember(barras) { mutableStateOf<Int?>(null) }
    val corBarra = MaterialTheme.colorScheme.primary
    val corGrade = MaterialTheme.colorScheme.outlineVariant
    val corSemDados = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier) {
        // Linha de leitura: mostra a barra tocada, ou uma dica
        val texto = selecionada?.let { barras.getOrNull(it) }?.let { b ->
            val adesao = b.resumo.adesao
            if (adesao == null) "${b.rotulo()}: sem doses previstas"
            else "${b.rotulo()}: ${(adesao * 100).roundToInt()}% (${b.resumo.tomadas} de ${b.resumo.total})"
        } ?: "Toque numa barra para ver o valor"
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        Row {
            // Eixo Y: só as duas referências
            Box(Modifier.width(40.dp).height(160.dp)) {
                Text("100%", style = MaterialTheme.typography.labelSmall, color = corSemDados)
                Text(
                    "50%",
                    style = MaterialTheme.typography.labelSmall,
                    color = corSemDados,
                    modifier = Modifier.padding(top = 72.dp),
                )
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(160.dp)
                    .pointerInput(barras) {
                        detectTapGestures { toque ->
                            if (barras.isEmpty()) return@detectTapGestures
                            val indice = (toque.x / (size.width.toFloat() / barras.size)).toInt()
                            selecionada = indice.coerceIn(0, barras.lastIndex).takeIf { it != selecionada }
                        }
                    },
            ) {
                val topo = 8.dp.toPx()
                val alturaUtil = size.height - topo
                listOf(1f, 0.5f).forEach { fracao ->
                    val y = topo + alturaUtil * (1 - fracao)
                    drawLine(
                        corGrade,
                        Offset(0f, y),
                        Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    )
                }
                drawLine(corGrade, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())

                if (barras.isEmpty()) return@Canvas
                val faixa = size.width / barras.size
                val vao = 2.dp.toPx()
                val largura = (faixa - vao).coerceAtMost(28.dp.toPx())
                barras.forEachIndexed { i, barra ->
                    val x = i * faixa + (faixa - largura) / 2
                    val adesao = barra.resumo.adesao
                    if (adesao == null) {
                        // Sem doses previstas: só um traço discreto na base
                        drawLine(corSemDados.copy(alpha = 0.4f), Offset(x, size.height - 2f), Offset(x + largura, size.height - 2f), 2.dp.toPx())
                    } else {
                        val altura = (alturaUtil * adesao.toFloat()).coerceAtLeast(2.dp.toPx())
                        barraArredondadaNoTopo(
                            cor = if (selecionada == null || selecionada == i) corBarra else corBarra.copy(alpha = 0.45f),
                            x = x,
                            y = size.height - altura,
                            largura = largura,
                            altura = altura,
                            raio = minOf(4.dp.toPx(), largura / 2),
                        )
                    }
                }
            }
        }

        // Eixo X: só o primeiro e o último rótulos, para não amontoar
        if (barras.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 40.dp, top = 4.dp)) {
                Text(barras.first().rotuloCurto(), style = MaterialTheme.typography.labelSmall, color = corSemDados, modifier = Modifier.weight(1f))
                if (barras.size > 1) {
                    Text(
                        barras.last().rotuloCurto(),
                        style = MaterialTheme.typography.labelSmall,
                        color = corSemDados,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun Barra.rotuloCurto(): String = inicio.format(formatoDiaMes)

/** Retângulo com só os cantos de cima arredondados (a ponta da barra), apoiado na base. */
private fun DrawScope.barraArredondadaNoTopo(
    cor: Color,
    x: Float,
    y: Float,
    largura: Float,
    altura: Float,
    raio: Float,
) {
    val caminho = Path().apply {
        addRoundRect(
            RoundRect(
                left = x,
                top = y,
                right = x + largura,
                bottom = y + altura,
                topLeftCornerRadius = CornerRadius(raio),
                topRightCornerRadius = CornerRadius(raio),
                bottomLeftCornerRadius = CornerRadius.Zero,
                bottomRightCornerRadius = CornerRadius.Zero,
            ),
        )
    }
    drawPath(caminho, cor)
}
