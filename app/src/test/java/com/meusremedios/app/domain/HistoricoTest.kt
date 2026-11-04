package com.meusremedios.app.domain

import com.meusremedios.app.data.FormaRemedio
import com.meusremedios.app.data.Horario
import com.meusremedios.app.data.Medicamento
import com.meusremedios.app.data.MedicamentoComHorarios
import com.meusremedios.app.data.RegistroDose
import com.meusremedios.app.data.StatusDose
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HistoricoTest {
    private val segunda = LocalDate.of(2026, 11, 2)

    private fun remedio(ativo: Boolean = true, arquivadoEm: LocalDate? = null) = Medicamento(
        id = 1, nome = "Dipirona", dosagem = "", forma = FormaRemedio.COMPRIMIDO,
        quantidadePorDose = 1.0, estoque = 30.0, alertaDias = 7, diasSemana = Agenda.TODOS_OS_DIAS,
        dataInicio = segunda, dataFim = null, observacoes = "", ativo = ativo, arquivadoEm = arquivadoEm,
    )

    private fun item(m: Medicamento, vararg horas: Int) =
        MedicamentoComHorarios(m, horas.map { Horario(medicamentoId = m.id, hora = LocalTime.of(it, 0)) })

    private fun registro(dia: LocalDate, hora: Int, status: StatusDose, minutosDepois: Long = 5) =
        RegistroDose(
            medicamentoId = 1,
            previsto = dia.atTime(hora, 0),
            realizado = dia.atTime(hora, 0).plusMinutes(minutosDepois),
            status = status,
        )

    @Test
    fun classificaDosesDoPeriodo() {
        val itens = listOf(item(remedio(), 8, 20))
        val registros = listOf(
            registro(segunda, 8, StatusDose.TOMADA),
            registro(segunda, 20, StatusDose.PULADA),
            registro(segunda.plusDays(1), 8, StatusDose.TOMADA, minutosDepois = 120),
        )
        // Terça às 21h: a dose das 20h de terça ainda está dentro da tolerância
        val doses = Historico.doses(itens, registros, segunda, segunda.plusDays(1).atTime(21, 0))
        val resumo = Historico.resumir(doses)

        assertEquals(3, resumo.total)
        assertEquals(2, resumo.tomadas)
        assertEquals(1, resumo.puladas)
        assertEquals(0, resumo.esquecidas)
        assertEquals(1, resumo.noHorario) // a de terça foi tomada 2h depois
    }

    @Test
    fun doseSemRegistroViraEsquecidaDepoisDaTolerancia() {
        val itens = listOf(item(remedio(), 8))
        assertEquals(0, Historico.doses(itens, emptyList(), segunda, segunda.atTime(8, 30)).size)
        val doses = Historico.doses(itens, emptyList(), segunda, segunda.atTime(9, 30))
        assertEquals(listOf(SituacaoDose.ESQUECIDA), doses.map { it.situacao })
    }

    @Test
    fun remedioExcluidoContaAteAVespera() {
        val itens = listOf(item(remedio(ativo = false, arquivadoEm = segunda.plusDays(2)), 8))
        val doses = Historico.doses(itens, emptyList(), segunda, segunda.plusDays(5).atTime(12, 0))
        assertEquals(2, doses.size) // segunda e terça
    }

    @Test
    fun registroEmHorarioQueMudouContinuaContando() {
        // O remédio era às 7h e passou para 8h; a dose das 7h já registrada não pode sumir
        val itens = listOf(item(remedio(), 8))
        val registros = listOf(registro(segunda, 7, StatusDose.TOMADA))
        val doses = Historico.doses(itens, registros, segunda, segunda.atTime(12, 0))
        assertEquals(2, doses.size)
        assertEquals(1, Historico.resumir(doses).tomadas)
        assertEquals(1, Historico.resumir(doses).esquecidas)
    }

    @Test
    fun adesaoEPontualidade() {
        val resumo = ResumoAdesao(tomadas = 3, puladas = 0, esquecidas = 1, noHorario = 3)
        assertEquals(0.75, resumo.adesao!!, 0.0001)
        assertEquals(1.0, resumo.pontualidade!!, 0.0001)
    }
}
