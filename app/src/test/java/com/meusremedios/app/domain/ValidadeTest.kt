package com.meusremedios.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ValidadeTest {
    private val hoje = LocalDate.of(2026, 11, 3)

    @Test
    fun classificaSituacao() {
        assertEquals(SituacaoValidade.SEM_VALIDADE, Validade.situacao(null, hoje, 7))
        assertEquals(SituacaoValidade.VALIDA, Validade.situacao(hoje.plusDays(8), hoje, 7))
        assertEquals(SituacaoValidade.VENCENDO, Validade.situacao(hoje.plusDays(7), hoje, 7))
        assertEquals(SituacaoValidade.VENCENDO, Validade.situacao(hoje, hoje, 7))
        assertEquals(SituacaoValidade.VENCIDA, Validade.situacao(hoje.minusDays(1), hoje, 7))
    }

    @Test
    fun avisaEnquantoVenceEUmaVezDepois() {
        assertTrue(Validade.deveAvisar(hoje.plusDays(3), hoje, 7))
        assertTrue(Validade.deveAvisar(hoje.minusDays(1), hoje, 7)) // venceu ontem: último aviso
        assertFalse(Validade.deveAvisar(hoje.minusDays(2), hoje, 7))
        assertFalse(Validade.deveAvisar(hoje.plusDays(30), hoje, 7))
        assertFalse(Validade.deveAvisar(null, hoje, 7))
    }

    @Test
    fun descreveVencimento() {
        assertEquals("Vence hoje", Validade.descrever(hoje, hoje))
        assertEquals("Vence amanhã", Validade.descrever(hoje.plusDays(1), hoje))
        assertEquals("Vence em 5 dias (08/11/2026)", Validade.descrever(hoje.plusDays(5), hoje))
        assertEquals("Vencida em 02/11/2026", Validade.descrever(hoje.minusDays(1), hoje))
    }
}
