package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.geo.ClassificacaoUso
import br.com.oanalistaambiental.pericia.geo.TipoCaptacao
import br.com.oanalistaambiental.pericia.geo.UsoInsignificante
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsoInsignificanteTest {

    @Test
    fun `superficial no limiar padrao entra e sai do insignificante`() {
        assertEquals(
            ClassificacaoUso.INSIGNIFICANTE,
            UsoInsignificante.classificarSuperficial(1.0, 5_000.0, "SF5").classificacao
        )
        assertEquals(
            ClassificacaoUso.ACIMA_DO_LIMIAR,
            UsoInsignificante.classificarSuperficial(1.01, 5_000.0, "SF5").classificacao
        )
        assertEquals(
            ClassificacaoUso.ACIMA_DO_LIMIAR,
            UsoInsignificante.classificarSuperficial(1.0, 5_001.0, "SF5").classificacao
        )
    }

    @Test
    fun `UPGRH do norte usa limiar reduzido de vazao e ampliado de acumulacao`() {
        // 0,6 L/s passa no padrao mas NAO passa na UPGRH restrita.
        assertEquals(
            ClassificacaoUso.INSIGNIFICANTE,
            UsoInsignificante.classificarSuperficial(0.6, null, "SF5").classificacao
        )
        assertEquals(
            ClassificacaoUso.ACIMA_DO_LIMIAR,
            UsoInsignificante.classificarSuperficial(0.6, null, "SF7").classificacao
        )
        // 40.000 m3 estoura o padrao mas cabe na UPGRH restrita.
        assertEquals(
            ClassificacaoUso.ACIMA_DO_LIMIAR,
            UsoInsignificante.classificarSuperficial(null, 40_000.0, "SF5").classificacao
        )
        assertEquals(
            ClassificacaoUso.INSIGNIFICANTE,
            UsoInsignificante.classificarSuperficial(null, 40_000.0, "JQ2").classificacao
        )
    }

    @Test
    fun `sigla de UPGRH e case-insensitive e sigla nula usa o padrao`() {
        assertEquals(
            ClassificacaoUso.ACIMA_DO_LIMIAR,
            UsoInsignificante.classificarSuperficial(0.6, null, "sf9").classificacao
        )
        assertEquals(
            ClassificacaoUso.INSIGNIFICANTE,
            UsoInsignificante.classificarSuperficial(0.6, null, null).classificacao
        )
    }

    @Test
    fun `subterranea poco tubular tem limiar maior que poco escavado e vem com condicoes`() {
        val tubular = UsoInsignificante.classificarSubterranea(TipoCaptacao.SUBTERRANEA_POCO_TUBULAR, 14_000.0)
        assertEquals(ClassificacaoUso.INSIGNIFICANTE, tubular.classificacao)
        assertEquals(4, tubular.condicoesAdicionais.size)

        val tubularAcima = UsoInsignificante.classificarSubterranea(TipoCaptacao.SUBTERRANEA_POCO_TUBULAR, 14_000.1)
        assertEquals(ClassificacaoUso.ACIMA_DO_LIMIAR, tubularAcima.classificacao)

        val outra = UsoInsignificante.classificarSubterranea(TipoCaptacao.SUBTERRANEA_OUTRA, 10_000.0)
        assertEquals(ClassificacaoUso.INSIGNIFICANTE, outra.classificacao)
        assertTrue(outra.condicoesAdicionais.isEmpty())

        val outraAcima = UsoInsignificante.classificarSubterranea(TipoCaptacao.SUBTERRANEA_OUTRA, 10_000.1)
        assertEquals(ClassificacaoUso.ACIMA_DO_LIMIAR, outraAcima.classificacao)
    }

    @Test
    fun `subterranea nao aceita tipo superficial`() {
        var lancou = false
        try {
            UsoInsignificante.classificarSubterranea(TipoCaptacao.SUPERFICIAL, 100.0)
        } catch (e: IllegalArgumentException) {
            lancou = true
        }
        assertTrue(lancou)
    }
}
