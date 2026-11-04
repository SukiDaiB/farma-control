package com.meusremedios.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Dao
abstract class MedicamentoDao {
    @Transaction
    @Query("SELECT * FROM medicamentos WHERE ativo = 1 ORDER BY nome COLLATE NOCASE")
    abstract fun observarAtivos(): Flow<List<MedicamentoComHorarios>>

    @Transaction
    @Query("SELECT * FROM medicamentos WHERE ativo = 1")
    abstract suspend fun listarAtivos(): List<MedicamentoComHorarios>

    @Transaction
    @Query("SELECT * FROM medicamentos WHERE id = :id")
    abstract suspend fun buscar(id: Long): MedicamentoComHorarios?

    @Insert
    protected abstract suspend fun inserir(medicamento: Medicamento): Long

    @Update
    protected abstract suspend fun atualizar(medicamento: Medicamento)

    @Insert
    protected abstract suspend fun inserirHorarios(horarios: List<Horario>)

    @Query("DELETE FROM horarios WHERE medicamentoId = :medicamentoId")
    protected abstract suspend fun apagarHorarios(medicamentoId: Long)

    @Transaction
    open suspend fun salvar(medicamento: Medicamento, horas: List<LocalTime>): Long {
        val id = if (medicamento.id == 0L) {
            inserir(medicamento)
        } else {
            atualizar(medicamento)
            medicamento.id
        }
        apagarHorarios(id)
        inserirHorarios(horas.map { Horario(medicamentoId = id, hora = it) })
        return id
    }

    @Query("UPDATE medicamentos SET ativo = 0, arquivadoEm = :data WHERE id = :id")
    abstract suspend fun arquivar(id: Long, data: LocalDate)

    /** Inclui os excluídos, para o histórico. */
    @Transaction
    @Query("SELECT * FROM medicamentos")
    abstract fun observarTodos(): Flow<List<MedicamentoComHorarios>>

    @Query("UPDATE medicamentos SET estoque = estoque + :delta WHERE id = :id")
    abstract suspend fun ajustarEstoque(id: Long, delta: Double)

    /** Soma a compra ao estoque; se estava negativo (doses sem estoque registrado), parte de zero. */
    @Query("UPDATE medicamentos SET estoque = MAX(estoque, 0) + :quantidade, ultimaCompra = :quantidade WHERE id = :id")
    abstract suspend fun registrarCompra(id: Long, quantidade: Double)
}

@Dao
abstract class RegistroDoseDao {
    @Query("SELECT * FROM registros_dose WHERE previsto >= :inicio AND previsto < :fim")
    abstract fun observarEntre(inicio: LocalDateTime, fim: LocalDateTime): Flow<List<RegistroDose>>

    @Query("SELECT * FROM registros_dose WHERE previsto >= :inicio AND previsto < :fim")
    abstract suspend fun listarEntre(inicio: LocalDateTime, fim: LocalDateTime): List<RegistroDose>

    @Query("SELECT * FROM registros_dose WHERE medicamentoId = :medicamentoId AND previsto = :previsto")
    abstract suspend fun buscar(medicamentoId: Long, previsto: LocalDateTime): RegistroDose?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvar(registro: RegistroDose)

    @Delete
    abstract suspend fun apagar(registro: RegistroDose)
}

@Dao
abstract class ReceitaDao {
    @Transaction
    @Query("SELECT * FROM receitas ORDER BY dataEmissao DESC, id DESC")
    abstract fun observar(): Flow<List<ReceitaComRemedios>>

    @Transaction
    @Query("SELECT * FROM receitas")
    abstract suspend fun listar(): List<ReceitaComRemedios>

    @Transaction
    @Query("SELECT * FROM receitas WHERE id = :id")
    abstract suspend fun buscar(id: Long): ReceitaComRemedios?

    @Insert
    protected abstract suspend fun inserir(receita: Receita): Long

    @Update
    protected abstract suspend fun atualizar(receita: Receita)

    @Query("DELETE FROM receita_medicamento WHERE receitaId = :receitaId")
    protected abstract suspend fun apagarVinculos(receitaId: Long)

    @Insert
    protected abstract suspend fun inserirVinculos(vinculos: List<ReceitaMedicamento>)

    @Transaction
    open suspend fun salvar(receita: Receita, medicamentoIds: Collection<Long>): Long {
        val id = if (receita.id == 0L) {
            inserir(receita)
        } else {
            atualizar(receita)
            receita.id
        }
        apagarVinculos(id)
        inserirVinculos(medicamentoIds.map { ReceitaMedicamento(id, it) })
        return id
    }

    @Query("DELETE FROM receitas WHERE id = :id")
    abstract suspend fun apagar(id: Long)
}

@Dao
abstract class ToqueDao {
    @Query("SELECT * FROM toques_pendentes")
    abstract suspend fun listar(): List<ToquePendente>

    @Query("SELECT * FROM toques_pendentes WHERE horario = :horario")
    abstract suspend fun buscar(horario: LocalDateTime): ToquePendente?

    @Query("SELECT MIN(proximoToque) FROM toques_pendentes")
    abstract suspend fun proximoToque(): LocalDateTime?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvar(toque: ToquePendente)

    @Query("DELETE FROM toques_pendentes WHERE horario IN (:horarios)")
    abstract suspend fun apagar(horarios: List<LocalDateTime>)

    @Query("DELETE FROM toques_pendentes WHERE horario < :limite")
    abstract suspend fun apagarAnterioresA(limite: LocalDateTime)
}
