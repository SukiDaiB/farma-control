package com.meusremedios.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class FormaRemedio(val rotulo: String, val unidade: String) {
    COMPRIMIDO("Comprimido", "comprimido(s)"),
    CAPSULA("Cápsula", "cápsula(s)"),
    GOTAS("Gotas", "gota(s)"),
    LIQUIDO("Líquido", "ml"),
    INJECAO("Injeção", "aplicação(ões)"),
    POMADA("Pomada", "aplicação(ões)"),
    OUTRO("Outro", "unidade(s)"),
}

enum class StatusDose { TOMADA, PULADA }

@Entity(tableName = "medicamentos")
data class Medicamento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val dosagem: String,
    val forma: FormaRemedio,
    val quantidadePorDose: Double,
    val estoque: Double,
    /** Avisar quando o estoque durar menos que esta quantidade de dias. */
    val alertaDias: Int,
    /** Bitmask: bit 0 = segunda ... bit 6 = domingo. */
    val diasSemana: Int,
    val dataInicio: LocalDate,
    /** null = uso contínuo. */
    val dataFim: LocalDate?,
    val observacoes: String,
    /** Remédios excluídos ficam inativos para preservar o histórico de adesão. */
    val ativo: Boolean = true,
    /** Quantidade da última compra, sugerida no próximo "Comprei mais". */
    val ultimaCompra: Double? = null,
    /** Dia da exclusão: o histórico conta as doses até a véspera. */
    val arquivadoEm: LocalDate? = null,
)

@Entity(
    tableName = "horarios",
    foreignKeys = [
        ForeignKey(
            entity = Medicamento::class,
            parentColumns = ["id"],
            childColumns = ["medicamentoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("medicamentoId")],
)
data class Horario(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicamentoId: Long,
    val hora: LocalTime,
)

@Entity(
    tableName = "registros_dose",
    foreignKeys = [
        ForeignKey(
            entity = Medicamento::class,
            parentColumns = ["id"],
            childColumns = ["medicamentoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["medicamentoId", "previsto"], unique = true)],
)
data class RegistroDose(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicamentoId: Long,
    val previsto: LocalDateTime,
    val realizado: LocalDateTime?,
    val status: StatusDose,
)

/**
 * Estado do alarme de um horário de dose: quando deve tocar de novo (adiado ou sem resposta)
 * e quantas vezes já tocou sem resposta. proximoToque null = não tocar mais.
 */
@Entity(tableName = "toques_pendentes")
data class ToquePendente(
    @PrimaryKey val horario: LocalDateTime,
    val proximoToque: LocalDateTime?,
    val tentativas: Int,
)

@Entity(tableName = "receitas")
data class Receita(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titulo: String,
    val medico: String,
    val dataEmissao: LocalDate,
    /** null = sem validade. */
    val validade: LocalDate?,
    val observacoes: String,
    /** Nome do arquivo na pasta interna de receitas. */
    val arquivo: String,
    /** "image/jpeg" ou "application/pdf". */
    val tipoArquivo: String,
)

@Entity(
    tableName = "receita_medicamento",
    primaryKeys = ["receitaId", "medicamentoId"],
    foreignKeys = [
        ForeignKey(entity = Receita::class, parentColumns = ["id"], childColumns = ["receitaId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Medicamento::class, parentColumns = ["id"], childColumns = ["medicamentoId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("medicamentoId")],
)
data class ReceitaMedicamento(val receitaId: Long, val medicamentoId: Long)

data class ReceitaComRemedios(
    @Embedded val receita: Receita,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(ReceitaMedicamento::class, parentColumn = "receitaId", entityColumn = "medicamentoId"),
    )
    val medicamentos: List<Medicamento>,
)

data class MedicamentoComHorarios(
    @Embedded val medicamento: Medicamento,
    @Relation(parentColumn = "id", entityColumn = "medicamentoId")
    val horarios: List<Horario>,
)
