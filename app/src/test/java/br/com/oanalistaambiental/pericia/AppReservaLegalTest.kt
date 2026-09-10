package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.geo.AppReservaLegal
import br.com.oanalistaambiental.pericia.geo.AppReservaLegal.RegiaoReservaLegal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReservaLegalTest {

    @Test
    fun `curso d'agua com menos de 10 m pede faixa de 30 m`() {
        assertEquals(30.0, AppReservaLegal.faixaCursoDaguaM(5.0), 0.0)
        assertEquals(30.0, AppReservaLegal.faixaCursoDaguaM(9.99), 0.0)
    }

    @Test
    fun `curso d'agua de 10 a 50 m pede faixa de 50 m`() {
        assertEquals(50.0, AppReservaLegal.faixaCursoDaguaM(10.0), 0.0)
        assertEquals(50.0, AppReservaLegal.faixaCursoDaguaM(49.9), 0.0)
    }

    @Test
    fun `curso d'agua de 50 a 200 m pede faixa de 100 m`() {
        assertEquals(100.0, AppReservaLegal.faixaCursoDaguaM(50.0), 0.0)
        assertEquals(100.0, AppReservaLegal.faixaCursoDaguaM(199.9), 0.0)
    }

    @Test
    fun `curso d'agua de 200 a 600 m pede faixa de 200 m`() {
        assertEquals(200.0, AppReservaLegal.faixaCursoDaguaM(200.0), 0.0)
        assertEquals(200.0, AppReservaLegal.faixaCursoDaguaM(599.9), 0.0)
    }

    @Test
    fun `curso d'agua acima de 600 m pede faixa de 500 m`() {
        assertEquals(500.0, AppReservaLegal.faixaCursoDaguaM(600.0), 0.0)
        assertEquals(500.0, AppReservaLegal.faixaCursoDaguaM(10_000.0), 0.0)
    }

    @Test
    fun `raio de nascente e olho d'agua e fixo em 50 m`() {
        assertEquals(50.0, AppReservaLegal.RAIO_NASCENTE_OLHO_DAGUA_M, 0.0)
    }

    @Test
    fun `lago em zona urbana pede sempre 30 m, independente do tamanho`() {
        assertEquals(30.0, AppReservaLegal.faixaLagoLagoaNaturalM(zonaUrbana = true, areaCorpoDaguaHa = 500.0), 0.0)
    }

    @Test
    fun `lago rural pequeno (ate 20 ha) pede 50 m`() {
        assertEquals(50.0, AppReservaLegal.faixaLagoLagoaNaturalM(zonaUrbana = false, areaCorpoDaguaHa = 20.0), 0.0)
        assertEquals(50.0, AppReservaLegal.faixaLagoLagoaNaturalM(zonaUrbana = false, areaCorpoDaguaHa = 5.0), 0.0)
    }

    @Test
    fun `lago rural maior que 20 ha pede 100 m`() {
        assertEquals(100.0, AppReservaLegal.faixaLagoLagoaNaturalM(zonaUrbana = false, areaCorpoDaguaHa = 20.01), 0.0)
        assertEquals(100.0, AppReservaLegal.faixaLagoLagoaNaturalM(zonaUrbana = false, areaCorpoDaguaHa = null), 0.0)
    }

    @Test
    fun `corpo d'agua com menos de 1 ha e dispensado da faixa`() {
        assertTrue(AppReservaLegal.dispensaFaixaPorTamanho(0.5))
        assertFalse(AppReservaLegal.dispensaFaixaPorTamanho(1.0))
    }

    @Test
    fun `reserva legal na Amazonia Legal varia por tipo de vegetacao`() {
        assertEquals(80.0, AppReservaLegal.percentualReservaLegal(RegiaoReservaLegal.AMAZONIA_LEGAL_FLORESTA), 0.0)
        assertEquals(35.0, AppReservaLegal.percentualReservaLegal(RegiaoReservaLegal.AMAZONIA_LEGAL_CERRADO), 0.0)
        assertEquals(20.0, AppReservaLegal.percentualReservaLegal(RegiaoReservaLegal.AMAZONIA_LEGAL_CAMPOS_GERAIS), 0.0)
    }

    @Test
    fun `reserva legal fora da Amazonia Legal (inclui MG) e 20 por cento`() {
        assertEquals(20.0, AppReservaLegal.percentualReservaLegal(RegiaoReservaLegal.DEMAIS_REGIOES), 0.0)
    }
}
