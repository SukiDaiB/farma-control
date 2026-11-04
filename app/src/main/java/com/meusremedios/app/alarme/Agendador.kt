package com.meusremedios.app.alarme

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.meusremedios.app.MainActivity
import com.meusremedios.app.data.BancoDados
import com.meusremedios.app.data.MedicamentoComHorarios
import com.meusremedios.app.data.Preferencias
import com.meusremedios.app.data.ToquePendente
import com.meusremedios.app.domain.Agenda
import com.meusremedios.app.domain.DosePrevista
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Mantém sempre um único alarme armado no sistema: o próximo horário de dose ainda não
 * registrada, ou a próxima repetição/adiamento, o que vier primeiro.
 */
class Agendador(
    private val context: Context,
    banco: BancoDados,
    private val preferencias: Preferencias,
) {
    private val medicamentos = banco.medicamentoDao()
    private val registros = banco.registroDoseDao()
    private val toques = banco.toqueDao()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val trava = Mutex()

    /** Chamado depois de qualquer mudança em remédios ou doses. */
    suspend fun aoMudar() {
        reagendar()
        AlarmeService.verificar(context)
    }

    suspend fun reagendar() = trava.withLock {
        val agora = LocalDateTime.now()
        toques.apagarAnterioresA(agora.minusDays(2))
        // Uma repetição atrasada (ex.: celular estava desligado) toca assim que possível
        val repeticao = toques.proximoToque()?.let { maxOf(it, agora.plusSeconds(1)) }
        val proximo = listOfNotNull(proximaDose(agora), repeticao).minOrNull()
        if (proximo == null) {
            alarmManager.cancel(intentDisparo())
        } else {
            armar(proximo, intentDisparo())
        }
    }

    /**
     * Chamado quando o alarme dispara. Retorna os horários que devem tocar agora
     * e os marca como "tocando" para não dispararem de novo.
     */
    suspend fun horariosDevidos(): List<LocalDateTime> = trava.withLock {
        val agora = LocalDateTime.now()
        val inicioJanela = agora.minusMinutes(JANELA_MINUTOS)
        val itens = medicamentos.listarAtivos()
        val registrados = chavesRegistradas(inicioJanela.toLocalDate(), agora.toLocalDate())
        val existentes = toques.listar().associateBy { it.horario }

        val novos = listOf(inicioJanela.toLocalDate(), agora.toLocalDate()).distinct()
            .flatMap { Agenda.dosesDoDia(itens, it) }
            .map { it.horario }
            .filter { it.isAfter(inicioJanela) && !it.isAfter(agora) && it !in existentes }
        val repeticoes = existentes.values
            .filter { t -> t.proximoToque?.let { !it.isAfter(agora) } == true }
            .map { it.horario }

        val devidos = (novos + repeticoes).distinct().filter { horario ->
            dosesNoHorario(itens, horario).any { (it.medicamento.id to horario) !in registrados }
        }
        // Repetições de doses que já foram resolvidas no app não precisam mais tocar
        val resolvidas = repeticoes - devidos.toSet()
        if (resolvidas.isNotEmpty()) toques.apagar(resolvidas)

        devidos.forEach { horario ->
            toques.salvar(ToquePendente(horario, proximoToque = null, tentativas = existentes[horario]?.tentativas ?: 0))
        }
        devidos.sorted()
    }

    suspend fun dosesPendentes(horarios: Collection<LocalDateTime>): List<DosePrevista> {
        if (horarios.isEmpty()) return emptyList()
        val itens = medicamentos.listarAtivos()
        return horarios.sorted().flatMap { horario ->
            dosesNoHorario(itens, horario).filter { registros.buscar(it.medicamento.id, horario) == null }
        }
    }

    suspend fun adiar(horarios: Collection<LocalDateTime>) {
        val quando = LocalDateTime.now().plusMinutes(preferencias.adiarMinutos.toLong())
        trava.withLock {
            horarios.forEach { horario ->
                val toque = toques.buscar(horario) ?: ToquePendente(horario, null, 0)
                toques.salvar(toque.copy(proximoToque = quando))
            }
        }
        reagendar()
    }

    /** O alarme tocou até o fim sem resposta: agenda nova tentativa, se ainda houver. */
    suspend fun semResposta(horarios: Collection<LocalDateTime>) {
        val agora = LocalDateTime.now()
        trava.withLock {
            horarios.forEach { horario ->
                val toque = toques.buscar(horario) ?: ToquePendente(horario, null, 0)
                val tentativas = toque.tentativas + 1
                val proximo = if (tentativas <= preferencias.repeticoes) {
                    agora.plusMinutes(preferencias.intervaloRepeticaoMinutos.toLong())
                } else {
                    null
                }
                toques.salvar(toque.copy(tentativas = tentativas, proximoToque = proximo))
            }
        }
        reagendar()
    }

    fun agendarTeste(segundos: Long) {
        val intent = PendingIntent.getBroadcast(
            context,
            REQ_TESTE,
            Intent(context, AlarmeReceiver::class.java).setAction(AlarmeReceiver.ACAO_TESTE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        armar(LocalDateTime.now().plusSeconds(segundos), intent)
    }

    private suspend fun proximaDose(agora: LocalDateTime): LocalDateTime? {
        val itens = medicamentos.listarAtivos()
        val hoje = agora.toLocalDate()
        val registrados = chavesRegistradas(hoje, hoje.plusDays(DIAS_A_FRENTE))
        return (0L..DIAS_A_FRENTE).asSequence()
            .flatMap { Agenda.dosesDoDia(itens, hoje.plusDays(it)) }
            .firstOrNull { it.horario.isAfter(agora) && (it.medicamento.id to it.horario) !in registrados }
            ?.horario
    }

    private suspend fun chavesRegistradas(de: LocalDate, ate: LocalDate): Set<Pair<Long, LocalDateTime>> =
        registros.listarEntre(de.atStartOfDay(), ate.plusDays(1).atStartOfDay())
            .map { it.medicamentoId to it.previsto }
            .toSet()

    private fun dosesNoHorario(itens: List<MedicamentoComHorarios>, horario: LocalDateTime): List<DosePrevista> =
        Agenda.dosesDoDia(itens, horario.toLocalDate()).filter { it.horario == horario }

    private fun armar(horario: LocalDateTime, operacao: PendingIntent) {
        val millis = horario.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val abrirApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            // Mesmo mecanismo do despertador: o sistema não atrasa nem em economia de bateria
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(millis, abrirApp), operacao)
        } catch (e: SecurityException) {
            Log.w(TAG, "Sem permissão de alarme exato; usando alarme aproximado", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operacao)
        }
    }

    private fun intentDisparo(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQ_DISPARO,
        Intent(context, AlarmeReceiver::class.java).setAction(AlarmeReceiver.ACAO_DISPARAR),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val TAG = "Agendador"
        const val REQ_DISPARO = 1
        const val REQ_TESTE = 2
        /** Tolerância para um disparo que chegou um pouco atrasado. */
        const val JANELA_MINUTOS = 15L
        const val DIAS_A_FRENTE = 7L
    }
}
