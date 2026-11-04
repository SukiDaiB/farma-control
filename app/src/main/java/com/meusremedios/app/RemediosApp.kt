package com.meusremedios.app

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meusremedios.app.alarme.Agendador
import com.meusremedios.app.alarme.Notificacoes
import com.meusremedios.app.data.BancoDados
import com.meusremedios.app.data.Preferencias
import com.meusremedios.app.data.RemediosRepositorio
import com.meusremedios.app.estoque.AvisosEstoque
import com.meusremedios.app.receitas.ArquivosReceita
import com.meusremedios.app.receitas.AvisosReceita
import com.meusremedios.app.receitas.ReceitasRepositorio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RemediosApp : Application() {
    private val banco: BancoDados by lazy { BancoDados.criar(this) }
    val preferencias: Preferencias by lazy { Preferencias(this) }
    val agendador: Agendador by lazy { Agendador(this, banco, preferencias) }
    val avisosEstoque: AvisosEstoque by lazy { AvisosEstoque(this, banco, preferencias) }
    val repositorio: RemediosRepositorio by lazy { RemediosRepositorio(banco, agendador, avisosEstoque) }
    val receitas: ReceitasRepositorio by lazy { ReceitasRepositorio(banco, ArquivosReceita(this)) }
    val avisosReceita: AvisosReceita by lazy { AvisosReceita(this, banco, preferencias) }

    override fun onCreate() {
        super.onCreate()
        Notificacoes.criarCanais(this)
        avisosEstoque.agendarResumoDiario()
        // Garante o alarme armado sempre que o processo do app inicia
        CoroutineScope(Dispatchers.IO).launch { agendador.reagendar() }
    }
}

/** Fábrica de ViewModel com acesso às dependências do app. */
inline fun <reified VM : ViewModel> fabricaComApp(
    crossinline criar: (RemediosApp) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        criar(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RemediosApp)
    }
}

/** Fábrica de ViewModel que injeta o repositório do app. */
inline fun <reified VM : ViewModel> fabricaComRepositorio(
    crossinline criar: (RemediosRepositorio) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RemediosApp
        criar(app.repositorio)
    }
}
