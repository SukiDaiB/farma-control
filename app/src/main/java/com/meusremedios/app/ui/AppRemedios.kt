package com.meusremedios.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.core.content.ContextCompat
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meusremedios.app.R
import com.meusremedios.app.alarme.Notificacoes
import com.meusremedios.app.ui.ajustes.AjustesScreen
import com.meusremedios.app.ui.formulario.FormularioRemedioScreen
import com.meusremedios.app.ui.historico.HistoricoScreen
import com.meusremedios.app.ui.hoje.HojeScreen
import com.meusremedios.app.ui.receitas.FormularioReceitaScreen
import com.meusremedios.app.ui.receitas.ReceitasScreen
import com.meusremedios.app.ui.receitas.VisualizarReceitaScreen
import com.meusremedios.app.ui.remedios.RemediosScreen

private object Rotas {
    const val HOJE = "hoje"
    const val REMEDIOS = Notificacoes.ABA_REMEDIOS
    const val RECEITAS = Notificacoes.ABA_RECEITAS
    const val HISTORICO = "historico"
    const val AJUSTES = "ajustes"
    const val FORMULARIO = "remedio/{id}"
    const val RECEITA = "receita/{id}"
    const val FORMULARIO_RECEITA = "receita/{id}/editar"
    fun formulario(id: Long = 0) = "remedio/$id"
    fun receita(id: Long) = "receita/$id"
    fun formularioReceita(id: Long = 0) = "receita/$id/editar"
}

private data class Aba(val rota: String, val rotulo: String, val icone: @Composable () -> ImageVector)

private val abas = listOf(
    Aba(Rotas.HOJE, "Hoje") { Icons.Filled.Home },
    Aba(Rotas.REMEDIOS, "Remédios") { Icons.AutoMirrored.Filled.List },
    Aba(Rotas.RECEITAS, "Receitas") { ImageVector.vectorResource(R.drawable.ic_receita) },
    Aba(Rotas.HISTORICO, "Histórico") { Icons.Filled.DateRange },
    Aba(Rotas.AJUSTES, "Ajustes") { Icons.Filled.Settings },
)

private val argumentoId = listOf(navArgument("id") { type = NavType.LongType })

private fun NavBackStackEntry.id(): Long = arguments?.getLong("id") ?: 0

@Composable
fun AppRemedios(abrirAba: String? = null) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val pedirNotificacoes = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pedirNotificacoes.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Veio de um aviso (estoque ou receita): abre direto na aba correspondente
        abrirAba?.takeIf { rota -> abas.any { it.rota == rota } }?.let { irParaAba(nav, it) }
    }
    val entrada by nav.currentBackStackEntryAsState()
    val rotaAtual = entrada?.destination?.route

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (abas.any { it.rota == rotaAtual }) {
                BarraAbas(nav, rotaAtual)
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Rotas.HOJE,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Rotas.HOJE) {
                HojeScreen(
                    onCadastrar = { nav.navigate(Rotas.formulario()) },
                    onEditar = { nav.navigate(Rotas.formulario(it)) },
                    onAjustes = { irParaAba(nav, Rotas.AJUSTES) },
                    onVerRemedios = { irParaAba(nav, Rotas.REMEDIOS) },
                    onVerReceitas = { irParaAba(nav, Rotas.RECEITAS) },
                )
            }
            composable(Rotas.REMEDIOS) {
                RemediosScreen(
                    onNovo = { nav.navigate(Rotas.formulario()) },
                    onEditar = { nav.navigate(Rotas.formulario(it)) },
                )
            }
            composable(Rotas.RECEITAS) {
                ReceitasScreen(
                    onNova = { nav.navigate(Rotas.formularioReceita()) },
                    onAbrir = { nav.navigate(Rotas.receita(it)) },
                    onEditar = { nav.navigate(Rotas.formularioReceita(it)) },
                )
            }
            composable(Rotas.HISTORICO) { HistoricoScreen() }
            composable(Rotas.AJUSTES) { AjustesScreen() }
            composable(Rotas.FORMULARIO, arguments = argumentoId) { entry ->
                FormularioRemedioScreen(id = entry.id(), onVoltar = { nav.popBackStack() })
            }
            composable(Rotas.RECEITA, arguments = argumentoId) { entry ->
                VisualizarReceitaScreen(
                    id = entry.id(),
                    onVoltar = { nav.popBackStack() },
                    onEditar = { nav.navigate(Rotas.formularioReceita(it)) },
                )
            }
            composable(Rotas.FORMULARIO_RECEITA, arguments = argumentoId) { entry ->
                FormularioReceitaScreen(id = entry.id(), onVoltar = { nav.popBackStack() })
            }
        }
    }
}

@Composable
private fun BarraAbas(nav: NavHostController, rotaAtual: String?) {
    NavigationBar {
        abas.forEach { aba ->
            NavigationBarItem(
                selected = rotaAtual == aba.rota,
                onClick = { irParaAba(nav, aba.rota) },
                icon = { Icon(aba.icone(), contentDescription = null) },
                label = { Text(aba.rotulo) },
            )
        }
    }
}

private fun irParaAba(nav: NavHostController, rota: String) {
    nav.navigate(rota) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
