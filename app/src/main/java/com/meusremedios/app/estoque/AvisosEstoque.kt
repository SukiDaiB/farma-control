package com.meusremedios.app.estoque

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.meusremedios.app.RemediosApp
import com.meusremedios.app.alarme.Notificacoes
import com.meusremedios.app.data.BancoDados
import com.meusremedios.app.data.Medicamento
import com.meusremedios.app.data.Preferencias
import com.meusremedios.app.domain.Agenda
import com.meusremedios.app.domain.AvisoEstoque
import com.meusremedios.app.domain.descreverDuracao
import com.meusremedios.app.domain.formatado
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

data class RemedioAcabando(val medicamento: Medicamento, val diasRestantes: Int?)

/** Avisos de estoque: na hora em que o remédio fica baixo e num resumo diário. */
class AvisosEstoque(
    private val context: Context,
    banco: BancoDados,
    private val preferencias: Preferencias,
) {
    private val medicamentos = banco.medicamentoDao()

    suspend fun listarAcabando(): List<RemedioAcabando> =
        medicamentos.listarAtivos()
            .filter { (m, horarios) -> Agenda.estoqueBaixo(m, horarios.size) }
            .map { (m, horarios) -> RemedioAcabando(m, Agenda.diasRestantes(m, horarios.size)) }
            .sortedBy { it.diasRestantes ?: Int.MAX_VALUE }

    /** Chamado depois de descontar uma dose do estoque. */
    suspend fun aposConsumo(medicamentoId: Long, consumido: Double) {
        val (m, horarios) = medicamentos.buscar(medicamentoId) ?: return
        when (Agenda.avisoAposConsumo(m, horarios.size, consumido)) {
            AvisoEstoque.ACABOU -> Notificacoes.mostrarEstoque(
                context,
                m.id,
                "${m.nome} acabou",
                "Registre a compra em \"Comprei mais\" para continuar acompanhando o estoque.",
            )
            AvisoEstoque.ACABANDO -> Notificacoes.mostrarEstoque(
                context,
                m.id,
                "${m.nome} está acabando",
                "Restam ${formatarQuantidade(m)} (${descreverDuracao(Agenda.diasRestantes(m, horarios.size))}).",
            )
            null -> {}
        }
    }

    fun aposCompra(medicamentoId: Long) = Notificacoes.cancelarEstoque(context, medicamentoId)

    suspend fun enviarResumoDiario() {
        val hoje = LocalDate.now().toString()
        if (preferencias.ultimoResumoEstoque == hoje) return
        val acabando = listarAcabando()
        if (acabando.isNotEmpty()) {
            Notificacoes.mostrarResumoEstoque(
                context,
                acabando.joinToString("\n") { "${it.medicamento.nome}: ${descreverDuracao(it.diasRestantes)}" },
                acabando.size,
            )
        }
        preferencias.ultimoResumoEstoque = hoje
    }

    /** Agenda o resumo diário para a hora escolhida nos ajustes. */
    fun agendarResumoDiario(substituir: Boolean = false) {
        val agora = LocalDateTime.now()
        var proximo = agora.toLocalDate().atTime(preferencias.horaResumoEstoque, 0)
        if (!proximo.isAfter(agora)) proximo = proximo.plusDays(1)

        val pedido = PeriodicWorkRequestBuilder<ResumoEstoqueWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(agora, proximo).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NOME_TRABALHO,
            if (substituir) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            pedido,
        )
    }

    private fun formatarQuantidade(m: Medicamento): String =
        "${m.estoque.coerceAtLeast(0.0).formatado()} ${m.forma.unidade}"

    private companion object {
        const val NOME_TRABALHO = "resumo_estoque"
    }
}

/** Resumo diário: estoque acabando e receitas vencendo. (Nome mantido por já estar agendado no WorkManager.) */
class ResumoEstoqueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as RemediosApp
        app.avisosEstoque.enviarResumoDiario()
        app.avisosReceita.enviarResumoDiario()
        return Result.success()
    }
}
