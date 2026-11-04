package com.meusremedios.app.receitas

import android.content.Context
import com.meusremedios.app.alarme.Notificacoes
import com.meusremedios.app.data.BancoDados
import com.meusremedios.app.data.Preferencias
import com.meusremedios.app.domain.Validade
import java.time.LocalDate

/** Resumo diário das receitas perto de vencer (roda junto com o resumo de estoque). */
class AvisosReceita(
    private val context: Context,
    banco: BancoDados,
    private val preferencias: Preferencias,
) {
    private val receitas = banco.receitaDao()

    suspend fun enviarResumoDiario() {
        val hoje = LocalDate.now()
        if (preferencias.ultimoResumoReceitas == hoje.toString()) return
        val avisar = receitas.listar()
            .map { it.receita }
            .filter { Validade.deveAvisar(it.validade, hoje, preferencias.antecedenciaValidadeDias) }
            .sortedBy { it.validade }
        if (avisar.isNotEmpty()) {
            Notificacoes.mostrarResumoReceitas(
                context,
                avisar.joinToString("\n") { "${it.titulo}: ${Validade.descrever(it.validade, hoje)}" },
                avisar.size,
            )
        }
        preferencias.ultimoResumoReceitas = hoje.toString()
    }
}
