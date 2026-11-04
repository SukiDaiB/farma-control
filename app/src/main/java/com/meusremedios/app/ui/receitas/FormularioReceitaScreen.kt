package com.meusremedios.app.ui.receitas

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meusremedios.app.R
import com.meusremedios.app.domain.formatada
import com.meusremedios.app.fabricaComApp
import com.meusremedios.app.ui.formulario.ChipSelecao
import com.meusremedios.app.ui.formulario.SeletorData

/** Atalhos de validade, em dias a partir da data da receita. */
private val prazosValidade = listOf(30, 60, 90, 180)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormularioReceitaScreen(
    id: Long,
    onVoltar: () -> Unit,
    viewModel: FormularioReceitaViewModel = viewModel(factory = fabricaComApp { FormularioReceitaViewModel(it, id) }),
) {
    LaunchedEffect(viewModel.concluido) {
        if (viewModel.concluido) onVoltar()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.editando) "Editar receita" else "Nova receita") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Campos(viewModel: FormularioReceitaViewModel) {
    val f = viewModel.formulario
    val context = LocalContext.current
    val remedios by viewModel.remedios.collectAsStateWithLifecycle()
    var escolhendoEmissao by remember { mutableStateOf(false) }
    var escolhendoValidade by remember { mutableStateOf(false) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), viewModel::aoTirarFoto)
    val seletorArquivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::aoEscolherArquivo)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Secao("Arquivo")
        f.arquivo?.let { arquivo ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (arquivo.ehPdf) "PDF anexado" else "Foto anexada",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        if (viewModel.importando) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    try {
                        camera.launch(viewModel.uriParaCamera())
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "Nenhum app de câmera encontrado.", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !viewModel.importando,
                modifier = Modifier.weight(1f),
            ) {
                Icon(ImageVector.vectorResource(R.drawable.ic_camera), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (f.arquivo == null) "Tirar foto" else "Nova foto")
            }
            OutlinedButton(
                onClick = { seletorArquivo.launch(arrayOf("image/*", "application/pdf")) },
                enabled = !viewModel.importando,
                modifier = Modifier.weight(1f),
            ) {
                Icon(ImageVector.vectorResource(R.drawable.ic_anexo), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Arquivo")
            }
        }

        Secao("Dados")
        OutlinedTextField(
            value = f.titulo,
            onValueChange = { v -> viewModel.alterar { it.copy(titulo = v) } },
            label = { Text("Nome (ex.: Receita cardiologista)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = f.medico,
            onValueChange = { v -> viewModel.alterar { it.copy(medico = v) } },
            label = { Text("Médico(a)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = { escolhendoEmissao = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Data da receita: ${f.dataEmissao.formatada()}")
        }

        Secao("Validade")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipSelecao(
                selecionado = f.validade == null,
                onClick = { viewModel.alterar { it.copy(validade = null) } },
                rotulo = "Sem validade",
            )
            prazosValidade.forEach { dias ->
                val data = f.dataEmissao.plusDays(dias.toLong())
                ChipSelecao(
                    selecionado = f.validade == data,
                    onClick = { viewModel.alterar { it.copy(validade = data) } },
                    rotulo = "$dias dias",
                )
            }
        }
        OutlinedButton(onClick = { escolhendoValidade = true }, modifier = Modifier.fillMaxWidth()) {
            Text(f.validade?.let { "Válida até ${it.formatada()}" } ?: "Escolher data de validade")
        }

        if (remedios.isNotEmpty()) {
            Secao("Remédios desta receita")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                remedios.forEach { m ->
                    ChipSelecao(
                        selecionado = m.id in f.medicamentos,
                        onClick = { viewModel.alternarRemedio(m.id) },
                        rotulo = m.nome,
                    )
                }
            }
        }

        OutlinedTextField(
            value = f.observacoes,
            onValueChange = { v -> viewModel.alterar { it.copy(observacoes = v) } },
            label = { Text("Observações") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        viewModel.erro?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = viewModel::salvar,
            enabled = !viewModel.importando,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Salvar")
        }
        Spacer(Modifier.height(16.dp))
    }

    if (escolhendoEmissao) {
        SeletorData(
            inicial = f.dataEmissao,
            onConfirmar = { data -> viewModel.alterar { it.copy(dataEmissao = data) }; escolhendoEmissao = false },
            onCancelar = { escolhendoEmissao = false },
        )
    }
    if (escolhendoValidade) {
        SeletorData(
            inicial = f.validade ?: f.dataEmissao.plusDays(30),
            onConfirmar = { data -> viewModel.alterar { it.copy(validade = data) }; escolhendoValidade = false },
            onCancelar = { escolhendoValidade = false },
        )
    }
}

@Composable
private fun Secao(titulo: String) {
    Text(titulo, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}
