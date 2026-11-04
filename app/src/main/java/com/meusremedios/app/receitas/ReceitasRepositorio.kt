package com.meusremedios.app.receitas

import com.meusremedios.app.data.BancoDados
import com.meusremedios.app.data.Receita
import com.meusremedios.app.data.ReceitaComRemedios
import kotlinx.coroutines.flow.Flow

class ReceitasRepositorio(banco: BancoDados, val arquivos: ArquivosReceita) {
    private val receitas = banco.receitaDao()

    fun observar(): Flow<List<ReceitaComRemedios>> = receitas.observar()

    suspend fun buscar(id: Long): ReceitaComRemedios? = receitas.buscar(id)

    suspend fun salvar(receita: Receita, medicamentoIds: Collection<Long>): Long =
        receitas.salvar(receita, medicamentoIds)

    suspend fun excluir(receita: Receita) {
        receitas.apagar(receita.id)
        arquivos.apagar(receita.arquivo)
    }
}
