package com.meusremedios.app.ui.receitas

import android.content.ActivityNotFoundException
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meusremedios.app.R
import com.meusremedios.app.RemediosApp
import com.meusremedios.app.data.ReceitaComRemedios
import com.meusremedios.app.domain.formatada
import com.meusremedios.app.fabricaComApp
import com.meusremedios.app.receitas.ArquivoReceita
import com.meusremedios.app.ui.componentes.MenuOpcoes
import com.meusremedios.app.ui.componentes.OpcaoMenu
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class VisualizarReceitaViewModel(app: RemediosApp, id: Long, larguraPx: Int) : ViewModel() {
    val antecedenciaDias: Int = app.preferencias.antecedenciaValidadeDias
    var receita by mutableStateOf<ReceitaComRemedios?>(null)
        private set
    var paginas by mutableStateOf<List<ImageBitmap>?>(null)
        private set
    var falhou by mutableStateOf(false)
        private set

    init {
        // Acompanha o banco para refletir edições; só renderiza de novo se o arquivo mudou
        viewModelScope.launch {
            app.receitas.observar()
                .map { lista -> lista.find { it.receita.id == id } }
                .collect { encontrada ->
                    val arquivoAnterior = receita?.receita?.arquivo
                    receita = encontrada
                    if (encontrada == null) {
                        falhou = true
                    } else if (encontrada.receita.arquivo != arquivoAnterior) {
                        renderizar(app, encontrada, larguraPx)
                    }
                }
        }
    }

    private suspend fun renderizar(app: RemediosApp, item: ReceitaComRemedios, larguraPx: Int) {
        try {
            paginas = app.receitas.arquivos.renderizar(item.receita, larguraPx).map { it.asImageBitmap() }
            falhou = false
        } catch (e: Exception) {
            Log.w("VisualizarReceita", "Falha ao abrir arquivo", e)
            falhou = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualizarReceitaScreen(
    id: Long,
    onVoltar: () -> Unit,
    onEditar: (Long) -> Unit,
) {
    val context = LocalContext.current
    val largura = context.resources.displayMetrics.widthPixels
    val viewModel: VisualizarReceitaViewModel = viewModel(
        factory = fabricaComApp { VisualizarReceitaViewModel(it, id, largura) },
    )
    val arquivos = (context.applicationContext as RemediosApp).receitas.arquivos
    val receita = viewModel.receita

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(receita?.receita?.titulo ?: "Receita") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    receita?.let { r ->
                        IconButton(onClick = { arquivos.compartilhar(context, r.receita) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Compartilhar")
                        }
                        MenuOpcoes(
                            listOf(
                                OpcaoMenu("Abrir com outro app", ImageVector.vectorResource(R.drawable.ic_receita)) {
                                    try {
                                        arquivos.abrirComOutroApp(context, r.receita)
                                    } catch (_: ActivityNotFoundException) {
                                        Toast.makeText(context, "Nenhum app para abrir este arquivo.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                OpcaoMenu("Editar", Icons.Filled.Edit) { onEditar(r.receita.id) },
                            ),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val paginas = viewModel.paginas
            when {
                viewModel.falhou -> Text(
                    "Não foi possível abrir o arquivo desta receita.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                receita == null || paginas == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                receita.receita.tipoArquivo == ArquivoReceita.TIPO_PDF -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Detalhes(receita, viewModel.antecedenciaDias) }
                    items(paginas) { pagina ->
                        Image(
                            pagina,
                            contentDescription = "Página da receita",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().background(Color.White),
                        )
                    }
                }
                else -> Column(Modifier.fillMaxSize()) {
                    Detalhes(receita, viewModel.antecedenciaDias)
                    paginas.firstOrNull()?.let { FotoComZoom(it, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun Detalhes(item: ReceitaComRemedios, antecedenciaDias: Int) {
    val r = item.receita
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            listOf(r.medico, r.dataEmissao.formatada()).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (item.medicamentos.isNotEmpty()) {
            Text("Remédios: ${item.medicamentos.joinToString(", ") { it.nome }}", style = MaterialTheme.typography.bodyMedium)
        }
        if (r.observacoes.isNotBlank()) {
            Text(r.observacoes, style = MaterialTheme.typography.bodySmall)
        }
        SeloValidade(r.validade, antecedenciaDias)
    }
}

/** Pinça para ampliar, arrastar para mover, toque duplo para voltar ao tamanho normal. */
@Composable
private fun FotoComZoom(foto: ImageBitmap, modifier: Modifier = Modifier) {
    var escala by remember { mutableFloatStateOf(1f) }
    var deslocamento by remember { mutableStateOf(Offset.Zero) }
    Image(
        foto,
        contentDescription = "Foto da receita",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    escala = 1f
                    deslocamento = Offset.Zero
                })
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, movimento, zoom, _ ->
                    escala = (escala * zoom).coerceIn(1f, 5f)
                    deslocamento = if (escala == 1f) Offset.Zero else deslocamento + movimento
                }
            }
            .graphicsLayer(
                scaleX = escala,
                scaleY = escala,
                translationX = deslocamento.x,
                translationY = deslocamento.y,
            ),
    )
}
