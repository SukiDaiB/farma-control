package com.meusremedios.app.data

import androidx.room.withTransaction
import com.meusremedios.app.alarme.Agendador
import com.meusremedios.app.domain.DoseHistorico
import com.meusremedios.app.domain.DosePrevista
import com.meusremedios.app.domain.Historico
import com.meusremedios.app.estoque.AvisosEstoque
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class RemediosRepositorio(
    private val banco: BancoDados,
    private val agendador: Agendador,
    private val avisosEstoque: AvisosEstoque,
) {
    private val medicamentos = banco.medicamentoDao()
    private val registros = banco.registroDoseDao()

    fun observarMedicamentos(): Flow<List<MedicamentoComHorarios>> = medicamentos.observarAtivos()

    suspend fun buscar(id: Long): MedicamentoComHorarios? = medicamentos.buscar(id)

    suspend fun salvar(medicamento: Medicamento, horas: List<LocalTime>): Long =
        medicamentos.salvar(medicamento, horas).also { agendador.aoMudar() }

    suspend fun arquivar(id: Long) {
        medicamentos.arquivar(id, LocalDate.now())
        avisosEstoque.aposCompra(id) // some com avisos de estoque de um remédio excluído
        agendador.aoMudar()
    }

    suspend fun registrarCompra(id: Long, quantidade: Double) {
        medicamentos.registrarCompra(id, quantidade)
        avisosEstoque.aposCompra(id)
    }

    fun observarRegistrosDoDia(data: LocalDate): Flow<List<RegistroDose>> =
        registros.observarEntre(data.atStartOfDay(), data.plusDays(1).atStartOfDay())

    /** Doses previstas desde [inicio] até agora, com o que aconteceu com cada uma. */
    fun observarHistorico(inicio: LocalDate): Flow<List<DoseHistorico>> = combine(
        medicamentos.observarTodos(),
        registros.observarEntre(inicio.atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay()),
    ) { itens, lista -> Historico.doses(itens, lista, inicio, LocalDateTime.now()) }

    /** Registra a dose e ajusta o estoque conforme a mudança de status. */
    suspend fun registrar(dose: DosePrevista, status: StatusDose) {
        val consumido = gravarRegistro(dose, status)
        if (consumido > 0) avisosEstoque.aposConsumo(dose.medicamento.id, consumido)
        agendador.aoMudar()
    }

    /** Desfaz o registro da dose, devolvendo ao estoque o que tinha sido descontado. */
    suspend fun desfazer(dose: DosePrevista) {
        apagarRegistro(dose)
        agendador.aoMudar()
    }

    /** Retorna quanto saiu do estoque (negativo se voltou). */
    private suspend fun gravarRegistro(dose: DosePrevista, status: StatusDose): Double = banco.withTransaction {
        val medicamento = dose.medicamento
        val anterior = registros.buscar(medicamento.id, dose.horario)
        val consumoAnterior = if (anterior?.status == StatusDose.TOMADA) medicamento.quantidadePorDose else 0.0
        val consumoNovo = if (status == StatusDose.TOMADA) medicamento.quantidadePorDose else 0.0
        if (consumoAnterior != consumoNovo) {
            medicamentos.ajustarEstoque(medicamento.id, consumoAnterior - consumoNovo)
        }
        registros.salvar(
            RegistroDose(
                id = anterior?.id ?: 0,
                medicamentoId = medicamento.id,
                previsto = dose.horario,
                realizado = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
                status = status,
            ),
        )
        consumoNovo - consumoAnterior
    }

    private suspend fun apagarRegistro(dose: DosePrevista) = banco.withTransaction {
        val anterior = registros.buscar(dose.medicamento.id, dose.horario) ?: return@withTransaction
        if (anterior.status == StatusDose.TOMADA) {
            medicamentos.ajustarEstoque(dose.medicamento.id, dose.medicamento.quantidadePorDose)
        }
        registros.apagar(anterior)
    }
}
