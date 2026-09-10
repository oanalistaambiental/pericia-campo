package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.dados.PontoCaminhamento
import br.com.oanalistaambiental.pericia.geo.Caminhamento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaminhamentoTest {

    private fun ponto(lat: Double, lon: Double, instanteMs: Long) =
        PontoCaminhamento(sessaoId = 1, lat = lat, lon = lon, precisaoM = 5f, instante = instanteMs)

    @Test
    fun `sem pontos ou um so ponto, distancia e duracao sao zero`() {
        assertEquals(0.0, Caminhamento.distanciaTotalM(emptyList()), 0.001)
        assertEquals(0.0, Caminhamento.distanciaTotalM(listOf(ponto(-19.0, -44.0, 0))), 0.001)
        assertEquals(0L, Caminhamento.duracaoSegundos(listOf(ponto(-19.0, -44.0, 0))))
    }

    @Test
    fun `soma os trechos, sem fechar de volta ao inicio`() {
        // Tres pontos em linha, cada trecho ~ mesma distancia — se fechasse o poligono, a volta
        // de volta ao primeiro ponto somaria mais um trecho igual aos dois primeiros juntos.
        val pontos = listOf(
            ponto(-19.000, -44.000, 0),
            ponto(-19.010, -44.000, 60_000),
            ponto(-19.020, -44.000, 120_000)
        )
        val trecho1 = br.com.oanalistaambiental.pericia.geo.Medicao.distanciaM(-19.000, -44.000, -19.010, -44.000)
        val trecho2 = br.com.oanalistaambiental.pericia.geo.Medicao.distanciaM(-19.010, -44.000, -19.020, -44.000)
        assertEquals(trecho1 + trecho2, Caminhamento.distanciaTotalM(pontos), 0.01)
    }

    @Test
    fun `duracao e a diferenca entre primeiro e ultimo ponto, nao a soma dos intervalos`() {
        val pontos = listOf(
            ponto(-19.0, -44.0, 10_000),
            ponto(-19.001, -44.0, 40_000),
            ponto(-19.002, -44.0, 100_000)
        )
        assertEquals(90L, Caminhamento.duracaoSegundos(pontos))
    }

    @Test
    fun `formatacao de distancia troca para km acima de mil metros`() {
        // Sem Locale fixo de proposito (mesma escolha de Medicao.Poligono.areaFormatada): e
        // exibicao na tela em pt-BR, nao exportacao — por isso nao fixa o separador decimal
        // aqui, so confere qual unidade cada faixa usa.
        assertEquals("500 m", Caminhamento.distanciaFormatada(500.0))
        assertTrue(Caminhamento.distanciaFormatada(1500.0).endsWith("km"))
        assertTrue(Caminhamento.distanciaFormatada(1500.0).startsWith("1"))
    }

    @Test
    fun `formatacao de duracao mostra horas so quando passa de uma`() {
        assertEquals("5:09", Caminhamento.duracaoFormatada(309))
        assertEquals("1:00:00", Caminhamento.duracaoFormatada(3600))
        assertTrue(Caminhamento.duracaoFormatada(3661).startsWith("1:01:0"))
    }
}
