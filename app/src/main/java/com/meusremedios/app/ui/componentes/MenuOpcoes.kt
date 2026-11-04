package com.meusremedios.app.ui.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

data class OpcaoMenu(val rotulo: String, val icone: ImageVector, val acao: () -> Unit)

/** Botão ⋮ que abre um menu com as opções informadas. */
@Composable
fun MenuOpcoes(opcoes: List<OpcaoMenu>, descricao: String = "Mais opções") {
    var aberto by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { aberto = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = descricao)
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            opcoes.forEach { opcao ->
                DropdownMenuItem(
                    text = { Text(opcao.rotulo) },
                    leadingIcon = { Icon(opcao.icone, contentDescription = null) },
                    onClick = {
                        aberto = false
                        opcao.acao()
                    },
                )
            }
        }
    }
}
