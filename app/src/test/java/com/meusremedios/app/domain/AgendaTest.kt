package com.meusremedios.app.domain

import com.meusremedios.app.data.FormaRemedio
import com.meusremedios.app.data.Horario
import com.meusremedios.app.data.Medicamento
import com.meusremedios.app.data.MedicamentoComHorarios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class AgendaTest {
    // 2026-11-02 é uma segunda-feira
    private val segunda = LocalDate.of(2026, 11, 2)

    private fun remedio(
        id: Long = 1,
        nome: String = "Dipirona",
        diasSemana: Int = Agenda.TODOS_OS_DIAS,
        inicio: LocalDate = segunda,
        fim: LocalDate? = null,
        estoque: Double = 20.0,
        quantidade: Double = 1.0,
        alertaDias: Int = 7,
    ) = Medicamento(
        id = id, nome = nome, dosagem = "", forma = FormaRemedio.COMPRIMIDO,
        quantidadePorDose = quantidade, estoque = estoque, alertaDias = alertaDias,
        diasSemana = diasSemana, dataInicio = inicio, dataFim = fim, observacoes = "",
    )

    @Test
    fun respeitaPeriodoDeUso() {
        val m = remedio(inicio = segunda, fim = segunda.plusDays(6))
        assertFalse(Agenda.tomaNoDia(m, segunda.minusDays(1)))
        assertTrue(Agenda.tomaNoDia(m, segunda))
        assertTrue(Agenda.tomaNoDia(m, segunda.plusDays(6)))
        assertFalse(Agenda.tomaNoDia(m, segunda.plusDays(7)))
    }

    @Test
    fun respeitaDiasDaSemana() {
        val dias = Agenda.bitDoDia(DayOfWeek.MONDAY) or Agenda.bitDoDia(DayOfWeek.FRIDAY)
        val m = remedio(diasSemana = dias)
        assertTrue(Agenda.tomaNoDia(m, segunda))
        assertFalse(Agenda.tomaNoDia(m, segunda.plusDays(1)))
        assertTrue(Agenda.tomaNoDia(m, segunda.plusDays(4)))
    }

    @Test
    fun ignoraRemedioInativo() {
        assertFalse(Agenda.tomaNoDia(remedio().copy(ativo = false), segunda))
    }

    @Test
    fun dosesDoDiaOrdenadasPorHorario() {
        val itens = listOf(
            MedicamentoComHorarios(
                remedio(id = 1, nome = "B"),
                listOf(Horario(medicamentoId = 1, hora = LocalTime.of(20, 0)), Horario(medicamentoId = 1, hora = LocalTime.of(8, 0))),
            ),
            MedicamentoComHorarios(remedio(id = 2, nome = "A"), listOf(Horario(medicamentoId = 2, hora = LocalTime.of(8, 0)))),
        )
        val doses = Agenda.dosesDoDia(itens, segunda)
        assertEquals(listOf("A", "B", "B"), doses.map { it.medicamento.nome })
        assertEquals(listOf(8, 8, 20), doses.map { it.horario.hour })
    }

    @Test
    fun calculaDiasRestantes() {
        // 2 doses por dia, 1 comprimido cada, 20 em estoque -> 10 dias
        assertEquals(10, Agenda.diasRestantes(remedio(estoque = 20.0), horariosPorDia = 2))
        // Só às segundas: consumo de 1/7 por dia -> 7 comprimidos duram 49 dias
        val soSegunda = remedio(diasSemana = Agenda.bitDoDia(DayOfWeek.MONDAY), estoque = 7.0)
        assertEquals(49, Agenda.diasRestantes(soSegunda, horariosPorDia = 1))
        assertNull(Agenda.diasRestantes(remedio(), horariosPorDia = 0))
    }

    @Test
    fun avisaSoQuandoCruzaOLimite() {
        // 1 dose/dia, aviso com 7 dias: 8 -> 7 cruza o limite
        assertEquals(AvisoEstoque.ACABANDO, Agenda.avisoAposConsumo(remedio(estoque = 7.0), 1, consumido = 1.0))
        // 7 -> 6 já estava baixo: não repete
        assertNull(Agenda.avisoAposConsumo(remedio(estoque = 6.0), 1, consumido = 1.0))
        // 20 -> 19 ainda longe do limite
        assertNull(Agenda.avisoAposConsumo(remedio(estoque = 19.0), 1, consumido = 1.0))
    }

    @Test
    fun avisaQuandoAcaba() {
        assertEquals(AvisoEstoque.ACABOU, Agenda.avisoAposConsumo(remedio(estoque = 0.0), 1, consumido = 1.0))
        // Já estava zerado: não repete
        assertNull(Agenda.avisoAposConsumo(remedio(estoque = -1.0), 1, consumido = 1.0))
    }

    @Test
    fun detectaEstoqueBaixo() {
        assertTrue(Agenda.estoqueBaixo(remedio(estoque = 6.0, alertaDias = 7), horariosPorDia = 1))
        assertFalse(Agenda.estoqueBaixo(remedio(estoque = 30.0, alertaDias = 7), horariosPorDia = 1))
    }
}
