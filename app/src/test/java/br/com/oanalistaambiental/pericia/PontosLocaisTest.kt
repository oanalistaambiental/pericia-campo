package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.geo.EscalaMapa
import br.com.oanalistaambiental.pericia.geo.PontosLocais
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mapinha em escala do polígono e da coordenada-alvo: a projeção para metros locais e a escolha
 * do passo da barra de escala são as duas contas que decidem o que o mapa afirma sobre distância.
 */
class PontosLocaisTest {

    @Test
    fun `primeiro ponto e sempre a origem`() {
        val r = PontosLocais.relativos(listOf(-19.9167 to -43.9345, -19.9257 to -43.9345))
        assertEquals(0.0, r[0].lesteM, 0.001)
        assertEquals(0.0, r[0].norteM, 0.001)
    }

    @Test
    fun `ponto ao sul fica com norte negativo, na ordem de 1 km`() {
        val r = PontosLocais.relativos(listOf(-19.9167 to -43.9345, -19.9257 to -43.9345))
        assertTrue(r[1].norteM < 0)
        assertEquals(1000.0, kotlin.math.abs(r[1].norteM), 15.0)
    }

    @Test
    fun `lista vazia nao quebra`() {
        assertEquals(emptyList<PontosLocais.PontoLocal>(), PontosLocais.relativos(emptyList()))
    }

    @Test
    fun `passo de escala nunca ultrapassa a largura maxima em pixels`() {
        // 1 pixel por metro: o maior passo redondo que cabe em 40 px e 25 (50 ja passa de 40).
        assertEquals(25.0, EscalaMapa.passoMetros(metrosPorPixel = 1.0, larguraMaximaPx = 40.0), 0.0)
        assertEquals(50.0, EscalaMapa.passoMetros(metrosPorPixel = 1.0, larguraMaximaPx = 60.0), 0.0)
    }

    @Test
    fun `passo de escala acompanha o zoom`() {
        // Mapa bem aberto: 100 m por pixel, 80 px disponiveis -> maior passo que cabe em 8000 m.
        val passo = EscalaMapa.passoMetros(metrosPorPixel = 100.0, larguraMaximaPx = 80.0)
        assertEquals(5000.0, passo, 0.0)
    }

    @Test
    fun `rotulo troca para km a partir de mil metros`() {
        assertEquals("500 m", EscalaMapa.rotulo(500.0))
        assertEquals("1 km", EscalaMapa.rotulo(1000.0))
        assertEquals("2 km", EscalaMapa.rotulo(2000.0))
    }
}
