package com.meusremedios.app.alarme

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.meusremedios.app.MainActivity
import com.meusremedios.app.R
import com.meusremedios.app.RemediosApp
import com.meusremedios.app.domain.DosePrevista
import com.meusremedios.app.domain.formatada
import com.meusremedios.app.domain.formatado
import java.time.LocalDateTime

object Notificacoes {
    const val ID_ALARME = 1
    private const val ID_PENDENTE = 2
    private const val ID_RESUMO_ESTOQUE = 3
    private const val ID_RESUMO_RECEITAS = 4
    /** Avisos de estoque usam um id por remédio, a partir deste. */
    private const val BASE_ID_ESTOQUE = 1000
    private const val CANAL_ALARME = "alarme"
    private const val CANAL_PENDENTES = "pendentes"
    private const val CANAL_ESTOQUE = "estoque"
    private const val CANAL_RECEITAS = "receitas"

    /** Aba que o app abre ao tocar na notificação. */
    const val EXTRA_ABRIR_ABA = "abrir_aba"
    const val ABA_REMEDIOS = "remedios"
    const val ABA_RECEITAS = "receitas"

    fun criarCanais(context: Context) {
        val alarme = NotificationChannel(CANAL_ALARME, "Alarme dos remédios", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Toca na hora de tomar o remédio"
            // Som e vibração são feitos pelo serviço, com volume de alarme
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        val pendentes = NotificationChannel(CANAL_PENDENTES, "Doses não confirmadas", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Doses em que o alarme tocou e ninguém respondeu"
        }
        val estoque = NotificationChannel(CANAL_ESTOQUE, "Estoque acabando", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avisa quando um remédio está acabando"
        }
        val receitas = NotificationChannel(CANAL_RECEITAS, "Receitas vencendo", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avisa quando uma receita está perto de vencer"
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(alarme, pendentes, estoque, receitas))
    }

    fun mostrarEstoque(context: Context, medicamentoId: Long, titulo: String, texto: String) =
        notificarAviso(context, CANAL_ESTOQUE, BASE_ID_ESTOQUE + medicamentoId.toInt(), titulo, texto, ABA_REMEDIOS)

    fun mostrarResumoReceitas(context: Context, texto: String, quantidade: Int) = notificarAviso(
        context,
        CANAL_RECEITAS,
        ID_RESUMO_RECEITAS,
        if (quantidade == 1) "1 receita vencendo" else "$quantidade receitas vencendo",
        texto,
        ABA_RECEITAS,
    )

    fun cancelarEstoque(context: Context, medicamentoId: Long) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(BASE_ID_ESTOQUE + medicamentoId.toInt())
        nm.cancel(ID_RESUMO_ESTOQUE)
    }

    fun mostrarResumoEstoque(context: Context, texto: String, quantidade: Int) = notificarAviso(
        context,
        CANAL_ESTOQUE,
        ID_RESUMO_ESTOQUE,
        if (quantidade == 1) "1 remédio acabando" else "$quantidade remédios acabando",
        texto,
        ABA_REMEDIOS,
    )

    private fun notificarAviso(context: Context, canal: String, id: Int, titulo: String, texto: String, aba: String) {
        val abrirAba = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ABRIR_ABA, aba)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificacao = NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrirAba)
            .setAutoCancel(true)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(id, notificacao)
        } catch (e: SecurityException) {
            Log.w("Notificacoes", "Sem permissão de notificação", e)
        }
    }

    fun alarme(context: Context, alarme: Alarme): Notification {
        val abrirTela = PendingIntent.getActivity(
            context,
            10,
            Intent(context, AlarmeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val texto = if (alarme.teste) "Se você está ouvindo, o alarme funciona." else descrever(alarme.doses)
        val construtor = NotificationCompat.Builder(context, CANAL_ALARME)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(if (alarme.teste) "Alarme de teste" else "Hora do remédio")
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(abrirTela)
            .setFullScreenIntent(abrirTela, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (alarme.teste) {
            construtor.addAction(0, "Parar", acao(context, AlarmeService.ACAO_PARAR, emptyList(), 20))
        } else {
            val adiar = (context.applicationContext as RemediosApp).preferencias.adiarMinutos
            construtor
                .addAction(0, "Tomei", acao(context, AlarmeService.ACAO_TOMEI, alarme.horarios, 21))
                .addAction(0, "Adiar $adiar min", acao(context, AlarmeService.ACAO_ADIAR, alarme.horarios, 22))
                .addAction(0, "Pular", acao(context, AlarmeService.ACAO_PULAR, alarme.horarios, 23))
        }
        return construtor.build()
    }

    /** Fica na bandeja depois que o alarme parou de tocar sem resposta. */
    fun mostrarPendente(context: Context, alarme: Alarme) {
        val abrirApp = PendingIntent.getActivity(
            context,
            30,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val texto = descrever(alarme.doses)
        val notificacao = NotificationCompat.Builder(context, CANAL_PENDENTES)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle("Dose não confirmada")
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrirApp)
            .setAutoCancel(true)
            .addAction(0, "Tomei", acao(context, AlarmeService.ACAO_TOMEI, alarme.horarios, 31))
            .addAction(0, "Pular", acao(context, AlarmeService.ACAO_PULAR, alarme.horarios, 33))
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(ID_PENDENTE, notificacao)
        } catch (e: SecurityException) {
            Log.w("Notificacoes", "Sem permissão de notificação", e)
        }
    }

    fun cancelarPendente(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_PENDENTE)
    }

    fun descrever(doses: List<DosePrevista>): String = doses.joinToString("\n") { dose ->
        val m = dose.medicamento
        val nome = listOf(m.nome, m.dosagem).filter { it.isNotBlank() }.joinToString(" ")
        "${dose.horario.toLocalTime().formatada()} · $nome — ${m.quantidadePorDose.formatado()} ${m.forma.unidade}"
    }

    private fun acao(context: Context, acao: String, horarios: List<LocalDateTime>, codigo: Int): PendingIntent =
        PendingIntent.getService(
            context,
            codigo,
            AlarmeService.intent(context, acao, horarios),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
