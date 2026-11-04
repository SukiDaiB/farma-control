package com.meusremedios.app.data

import android.content.Context

class Preferencias(context: Context) {
    private val prefs = context.getSharedPreferences("preferencias", Context.MODE_PRIVATE)

    /** Por quanto tempo o alarme toca antes de contar como "sem resposta". */
    var duracaoToqueSegundos: Int
        get() = prefs.getInt("duracao_toque", 60)
        set(valor) = prefs.edit().putInt("duracao_toque", valor).apply()

    /** Intervalo até tocar de novo quando ninguém responde. */
    var intervaloRepeticaoMinutos: Int
        get() = prefs.getInt("intervalo_repeticao", 5)
        set(valor) = prefs.edit().putInt("intervalo_repeticao", valor).apply()

    /** Quantas vezes tocar de novo depois do primeiro toque sem resposta. */
    var repeticoes: Int
        get() = prefs.getInt("repeticoes", 3)
        set(valor) = prefs.edit().putInt("repeticoes", valor).apply()

    var adiarMinutos: Int
        get() = prefs.getInt("adiar", 10)
        set(valor) = prefs.edit().putInt("adiar", valor).apply()

    /** Com quantos dias de antecedência avisar que a receita vai vencer. */
    var antecedenciaValidadeDias: Int
        get() = prefs.getInt("antecedencia_validade", 7)
        set(valor) = prefs.edit().putInt("antecedencia_validade", valor).apply()

    var ultimoResumoReceitas: String?
        get() = prefs.getString("ultimo_resumo_receitas", null)
        set(valor) = prefs.edit().putString("ultimo_resumo_receitas", valor).apply()

    /** Hora do dia do resumo diário (estoque acabando e receitas vencendo). */
    var horaResumoEstoque: Int
        get() = prefs.getInt("hora_resumo_estoque", 9)
        set(valor) = prefs.edit().putInt("hora_resumo_estoque", valor).apply()

    /** Data (ISO) do último resumo enviado, para não repetir no mesmo dia. */
    var ultimoResumoEstoque: String?
        get() = prefs.getString("ultimo_resumo_estoque", null)
        set(valor) = prefs.edit().putString("ultimo_resumo_estoque", valor).apply()
}
