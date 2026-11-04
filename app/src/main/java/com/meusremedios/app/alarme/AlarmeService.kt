package com.meusremedios.app.alarme

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.meusremedios.app.RemediosApp
import com.meusremedios.app.data.StatusDose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Toca o alarme (som de despertador em loop + vibração) e trata as respostas
 * Tomei / Adiar / Pular, vindas da notificação ou da tela de alarme.
 */
class AlarmeService : Service() {

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val app get() = application as RemediosApp

    /** Horários de dose tocando agora (pode haver mais de um se um adiamento coincidir). */
    private val horarios = mutableSetOf<LocalDateTime>()
    private var emTeste = false
    private var tocandoSom = false
    private var player: MediaPlayer? = null
    private var vibrador: Vibrator? = null
    private var limite: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recebidos = intent?.getStringArrayExtra(EXTRA_HORARIOS).orEmpty().map(LocalDateTime::parse)
        when (intent?.action) {
            ACAO_TOCAR -> {
                entrarEmPrimeiroPlano()
                horarios += recebidos
                escopo.launch { atualizar() }
            }
            ACAO_TESTE -> {
                entrarEmPrimeiroPlano()
                emTeste = true
                mostrar(Alarme(emptyList(), emptyList(), teste = true))
                iniciarToque()
            }
            ACAO_TOMEI -> resolver(recebidos) { alvo ->
                app.agendador.dosesPendentes(alvo).forEach { app.repositorio.registrar(it, StatusDose.TOMADA) }
            }
            ACAO_PULAR -> resolver(recebidos) { alvo ->
                app.agendador.dosesPendentes(alvo).forEach { app.repositorio.registrar(it, StatusDose.PULADA) }
            }
            ACAO_ADIAR -> resolver(recebidos) { alvo -> app.agendador.adiar(alvo) }
            ACAO_PARAR -> encerrar()
            ACAO_VERIFICAR -> if (!emTeste) escopo.launch { atualizar() }
            else -> if (!ativo) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun entrarEmPrimeiroPlano() {
        ativo = true
        val notificacao = Notificacoes.alarme(this, _tocando.value ?: Alarme(emptyList(), emptyList()))
        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else {
            0
        }
        ServiceCompat.startForeground(this, Notificacoes.ID_ALARME, notificacao, tipo)
    }

    /** Recarrega as doses pendentes; encerra se não sobrou nenhuma. */
    private suspend fun atualizar() {
        val doses = app.agendador.dosesPendentes(horarios)
        if (doses.isEmpty()) {
            encerrar()
            return
        }
        mostrar(Alarme(doses, horarios.sorted()))
        iniciarToque()
    }

    private fun mostrar(alarme: Alarme) {
        _tocando.value = alarme
        getSystemService(NotificationManager::class.java)
            .notify(Notificacoes.ID_ALARME, Notificacoes.alarme(this, alarme))
    }

    private fun resolver(recebidos: List<LocalDateTime>, acao: suspend (List<LocalDateTime>) -> Unit) {
        if (emTeste) {
            encerrar()
            return
        }
        val alvo = recebidos.ifEmpty { horarios.toList() }
        pararToque()
        Notificacoes.cancelarPendente(this)
        escopo.launch {
            acao(alvo)
            horarios -= alvo.toSet()
            if (horarios.isEmpty()) encerrar() else atualizar()
        }
    }

    private fun iniciarToque() {
        if (tocandoSom) return
        tocandoSom = true
        Notificacoes.cancelarPendente(this)
        player = criarPlayer()
        vibrar()
        limite = escopo.launch {
            delay(app.preferencias.duracaoToqueSegundos * 1000L)
            semResposta()
        }
    }

    private suspend fun semResposta() {
        limite = null // este coroutine é o próprio limite; não pode se cancelar
        val alvo = horarios.toList()
        pararToque()
        if (!emTeste && alvo.isNotEmpty()) {
            val doses = app.agendador.dosesPendentes(alvo)
            app.agendador.semResposta(alvo)
            if (doses.isNotEmpty()) Notificacoes.mostrarPendente(this, Alarme(doses, alvo))
        }
        encerrar()
    }

    private fun criarPlayer(): MediaPlayer? {
        val atributos = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM) // usa o volume de alarme: toca mesmo no silencioso
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sons = listOfNotNull<Uri>(
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (som in sons) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(atributos)
                mp.setDataSource(this, som)
                mp.isLooping = true
                mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
                mp.prepare()
                mp.start()
                return mp
            } catch (e: Exception) {
                Log.w(TAG, "Não foi possível tocar $som", e)
                mp.release()
            }
        }
        return null
    }

    private fun vibrar() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        val padrao = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(padrao, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(padrao, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
        vibrador = v
    }

    private fun pararToque() {
        tocandoSom = false
        limite?.cancel()
        limite = null
        player?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        player = null
        vibrador?.cancel()
        vibrador = null
    }

    private fun encerrar() {
        pararToque()
        emTeste = false
        horarios.clear()
        _tocando.value = null
        ativo = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        pararToque()
        escopo.cancel()
        _tocando.value = null
        ativo = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmeService"
        private const val EXTRA_HORARIOS = "horarios"
        const val ACAO_TOCAR = "com.meusremedios.app.alarme.TOCAR"
        const val ACAO_TESTE = "com.meusremedios.app.alarme.TESTE"
        const val ACAO_TOMEI = "com.meusremedios.app.alarme.TOMEI"
        const val ACAO_ADIAR = "com.meusremedios.app.alarme.ADIAR"
        const val ACAO_PULAR = "com.meusremedios.app.alarme.PULAR"
        const val ACAO_PARAR = "com.meusremedios.app.alarme.PARAR"
        const val ACAO_VERIFICAR = "com.meusremedios.app.alarme.VERIFICAR"

        private val _tocando = MutableStateFlow<Alarme?>(null)

        /** O alarme tocando agora, para a tela de alarme acompanhar. */
        val tocando: StateFlow<Alarme?> = _tocando

        @Volatile
        private var ativo = false

        fun intent(context: Context, acao: String, horarios: Collection<LocalDateTime> = emptyList()): Intent =
            Intent(context, AlarmeService::class.java)
                .setAction(acao)
                .putExtra(EXTRA_HORARIOS, horarios.map { it.toString() }.toTypedArray())

        fun tocar(context: Context, horarios: Collection<LocalDateTime>) {
            ContextCompat.startForegroundService(context, intent(context, ACAO_TOCAR, horarios))
        }

        fun tocarTeste(context: Context) {
            ContextCompat.startForegroundService(context, intent(context, ACAO_TESTE))
        }

        /** Se estiver tocando, confere se a dose foi resolvida pelo app e para o alarme. */
        fun verificar(context: Context) {
            if (!ativo) return
            try {
                context.startService(intent(context, ACAO_VERIFICAR))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Não foi possível verificar o alarme", e)
            }
        }
    }
}
