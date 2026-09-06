package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.dados.Foto
import br.com.oanalistaambiental.pericia.geo.PontoRetorno
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O ponto de retorno so vale se ele for honesto sobre chegada e enquadramento.
 * Estes testes travam esse comportamento.
 */
class PontoRetornoTest {

    private fun alvo(lat: Double, lon: Double, azimute: Float?) = Foto(
        id = 1, sessaoId = 1, arquivoOriginal = "/tmp/a.jpg", arquivoComLegenda = null,
        sha256 = "x", lat = lat, lon = lon, precisaoM = 5f, altitudeM = null,
        azimuteGraus = azimute, inclinacaoGraus = null, instante = 0L,
        tipoOcorrencia = null, observacao = null
    )

    @Test
    fun `no mesmo ponto e mesma direcao esta enquadrado`() {
        val o = PontoRetorno.orientar(alvo(-19.9167, -43.9345, 210f), -19.9167, -43.9345, 4f, 208f)
        assertTrue(o.chegou)
        assertTrue(o.enquadrado)
    }

    @Test
    fun `no ponto mas de costas nao esta enquadrado`() {
        val o = PontoRetorno.orientar(alvo(-19.9167, -43.9345, 210f), -19.9167, -43.9345, 4f, 30f)
        assertTrue(o.chegou)
        assertFalse(o.enquadrado)
        assertEquals(180.0, Math.abs(o.ajusteCameraGraus!!.toDouble()), 1.0)
    }

    @Test
    fun `giro escolhe o lado mais curto atravessando o norte`() {
        // alvo 350°, atual 10° -> deve pedir 20° para a ESQUERDA, nao 340° para a direita
        val o = PontoRetorno.orientar(alvo(-19.9167, -43.9345, 350f), -19.9167, -43.9345, 4f, 10f)
        assertEquals(-20.0, o.ajusteCameraGraus!!.toDouble(), 0.5)
    }

    @Test
    fun `longe do alvo informa distancia e rumo`() {
        // ~1 km ao norte do alvo: precisa caminhar para o sul (rumo perto de 180)
        val o = PontoRetorno.orientar(alvo(-19.9167, -43.9345, null), -19.9077, -43.9345, 5f, null)
        assertFalse(o.chegou)
        assertEquals(1000.0, o.distanciaM.toDouble(), 60.0)
        assertEquals(180.0, o.rumoGraus.toDouble(), 3.0)
    }

    @Test
    fun `tolerancia de chegada acompanha a precisao do GNSS`() {
        val a = alvo(-19.9167, -43.9345, null)
        // ~20 m de distancia, com precisao ruim de 30 m: considera-se chegado
        val ruim = PontoRetorno.orientar(a, -19.91652, -43.9345, 30f, null)
        assertTrue(ruim.chegou)
        // mesma distancia com GNSS bom: ainda nao chegou
        val bom = PontoRetorno.orientar(a, -19.91652, -43.9345, 4f, null)
        assertFalse(bom.chegou)
    }

    @Test
    fun `sem bussola nao inventa enquadramento`() {
        val o = PontoRetorno.orientar(alvo(-19.9167, -43.9345, null), -19.9167, -43.9345, 4f, 90f)
        assertTrue(o.chegou)
        assertFalse(o.enquadrado)
        assertEquals(null, o.ajusteCameraGraus)
    }
}
