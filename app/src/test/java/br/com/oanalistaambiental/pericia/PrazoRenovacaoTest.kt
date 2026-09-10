package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.geo.PrazoRenovacao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PrazoRenovacaoTest {

    @Test
    fun `limite fica 120 dias antes da validade`() {
        val validade = LocalDate.of(2027, 1, 1)
        val r = PrazoRenovacao.calcular(validade, hoje = LocalDate.of(2026, 1, 1))
        assertEquals(validade.minusDays(120), r.dataLimiteProtocolo)
    }

    @Test
    fun `dentro do prazo nao esta vencido`() {
        val validade = LocalDate.of(2027, 1, 1)
        val r = PrazoRenovacao.calcular(validade, hoje = LocalDate.of(2026, 1, 1))
        assertFalse(r.prazoVencido)
        assertTrue(r.diasRestantes > 0)
    }

    @Test
    fun `depois do limite esta vencido, com dias negativos`() {
        val validade = LocalDate.of(2026, 1, 1)
        val r = PrazoRenovacao.calcular(validade, hoje = LocalDate.of(2026, 1, 1))
        assertTrue(r.prazoVencido)
        assertEquals(-120L, r.diasRestantes)
    }

    @Test
    fun `janela de atencao e so nos ultimos 30 dias, sem ainda ter vencido`() {
        val validade = LocalDate.of(2026, 12, 31)
        val limite = validade.minusDays(120)
        val r = PrazoRenovacao.calcular(validade, hoje = limite.minusDays(10))
        assertTrue(r.proximoDoLimite)
        val cedo = PrazoRenovacao.calcular(validade, hoje = limite.minusDays(40))
        assertFalse(cedo.proximoDoLimite)
    }
}
