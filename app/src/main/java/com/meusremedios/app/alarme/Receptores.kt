package com.meusremedios.app.alarme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meusremedios.app.RemediosApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Recebe o disparo do AlarmManager. */
class AlarmeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as RemediosApp
        when (intent.action) {
            ACAO_TESTE -> AlarmeService.tocarTeste(context)
            ACAO_DISPARAR -> executarAssincrono {
                val devidos = app.agendador.horariosDevidos()
                if (devidos.isNotEmpty()) AlarmeService.tocar(context, devidos)
                app.agendador.reagendar()
            }
        }
    }

    companion object {
        const val ACAO_DISPARAR = "com.meusremedios.app.DISPARAR"
        const val ACAO_TESTE = "com.meusremedios.app.TESTE"
    }
}

/** Rearma o alarme depois de reiniciar, atualizar o app ou mudar a hora do celular. */
class ReagendarReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as RemediosApp
        executarAssincrono { app.agendador.reagendar() }
    }
}

private fun BroadcastReceiver.executarAssincrono(bloco: suspend () -> Unit) {
    val pendente = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            bloco()
        } finally {
            pendente.finish()
        }
    }
}
