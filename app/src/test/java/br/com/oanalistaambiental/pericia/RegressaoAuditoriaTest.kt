package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.captura.EstadoCampo
import br.com.oanalistaambiental.pericia.captura.Orientacoes
import br.com.oanalistaambiental.pericia.geo.Medicao
import br.com.oanalistaambiental.pericia.geo.Utm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rede de seguranca dos defeitos encontrados na auditoria de 07/09/2026.
 *
 * Cada teste aqui existe porque um bug real passou por baixo dos testes anteriores. O valor
 * deles nao e provar que o codigo funciona — e travar a porta por onde cada um entrou.
 */
class RegressaoAuditoriaTest {

    // ---------------------------------------------------------------- Equador

    /**
     * O defeito de cem mil vezes. Um quadrado de ~100 m sobre a linha do Equador tinha metade
     * dos vertices somando o falso-norte de 10.000.000 m e a outra metade nao, porque cada
     * ponto decidia o hemisferio pelo proprio sinal da latitude.
     */
    @Test
    fun `poligono sobre o Equador tem area realista`() {
        // ~50 m para cada lado do Equador, em Macapa
        val d = 0.00045
        val lon0 = -51.0
        val dLon = 0.00045
        val v = listOf(
            Medicao.Vertice(-d, lon0 - dLon, 3f, 0),
            Medicao.Vertice(-d, lon0 + dLon, 3f, 0),
            Medicao.Vertice(d, lon0 + dLon, 3f, 0),
            Medicao.Vertice(d, lon0 - dLon, 3f, 0)
        )
        val p = Medicao.medir(v)
        // Cerca de 100 m x 100 m. Antes da correcao isto devolvia ~1.001.464.704 m2.
        assertTrue("area fora de faixa: ${p.areaM2}", p.areaM2 in 8_000.0..12_000.0)
    }

    @Test
    fun `hemisferio forcado muda o northing em dez milhoes`() {
        val norte = Utm.projetar(0.001, -51.0, 22, hemisferioSulForcado = false)
        val sulForcado = Utm.projetar(0.001, -51.0, 22, hemisferioSulForcado = true)
        assertEquals(10_000_000.0, sulForcado.northing - norte.northing, 0.001)
        assertTrue(sulForcado.hemisferioSul)
        assertFalse(norte.hemisferioSul)
    }

    /** Sem o forcado, a latitude continua mandando: nao mudamos o comportamento normal. */
    @Test
    fun `sem hemisferio forcado o sinal da latitude continua decidindo`() {
        assertTrue(Utm.projetar(-19.9227, -43.9451).hemisferioSul)
        assertFalse(Utm.projetar(4.5, -61.0).hemisferioSul)
    }

    // ---------------------------------------------------------------- zona UTM

    /** `zonaDe(180.0)` devolvia 61, uma zona que nao existe, e nada validava. */
    @Test
    fun `zona nunca sai da faixa de 1 a 60`() {
        assertEquals(60, Utm.zonaDe(180.0))
        assertEquals(60, Utm.zonaDe(179.999))
        assertEquals(1, Utm.zonaDe(-180.0))
        assertEquals(23, Utm.zonaDe(-45.0))
        assertEquals(22, Utm.zonaDe(-51.0))
    }

    // ---------------------------------------------------------------- clinometro

    /**
     * `tan(90 graus)` vale ~1,6e16. A tela imprimia esse numero, em verde e negrito, logo
     * abaixo de uma ajuda que manda encostar o celular numa parede.
     */
    @Test
    fun `declividade tem teto em vez de estourar perto da vertical`() {
        assertNull(Orientacoes.declividadePercent(90f))
        assertNull(Orientacoes.declividadePercent(85f))
        assertNull(Orientacoes.declividadePercent(80f))
        assertNotNull(Orientacoes.declividadePercent(79.9f))
        assertEquals(100.0, Orientacoes.declividadePercent(45f)!!.toDouble(), 0.01)
        assertEquals(0.0, Orientacoes.declividadePercent(0f)!!.toDouble(), 0.001)
    }

    // ---------------------------------------------------------------- validade do fix

    /**
     * O bug mais grave da auditoria: coordenada velha gravada com a hora de agora e a precisao
     * antiga. Coordenada de um lugar, hora de outro, e o selo verde afirmando confianca.
     */
    @Test
    fun `posicao vencida nao pode passar por leitura boa`() {
        val viva = EstadoCampo.Posicao(
            lat = -19.9, lon = -43.9, precisaoM = 4f,
            provedor = "gps", vencida = false
        )
        assertEquals(EstadoCampo.Qualidade.BOA, viva.qualidade)
        assertTrue(viva.serveParaProva)

        val morta = viva.copy(vencida = true)
        assertEquals(EstadoCampo.Qualidade.VENCIDA, morta.qualidade)
        assertFalse("fix vencido nao serve para prova", morta.serveParaProva)
    }

    /** Ponto de rede tambem nao serve de prova, mesmo no prazo e com precisao boa. */
    @Test
    fun `posicao de rede nao serve para prova`() {
        val rede = EstadoCampo.Posicao(
            lat = -19.9, lon = -43.9, precisaoM = 3f, provedor = "network"
        )
        assertTrue(rede.aproximada)
        assertFalse(rede.serveParaProva)
    }

    /** Sem lat/lon, `precisaoM` sozinha nao pode fazer o selo ficar verde. */
    @Test
    fun `precisao sem coordenada continua sendo sem sinal`() {
        val p = EstadoCampo.Posicao(lat = null, lon = null, precisaoM = 2f)
        assertEquals(EstadoCampo.Qualidade.SEM_SINAL, p.qualidade)
        assertFalse(p.temPosicao)
    }

    @Test
    fun `idade em segundos conta do instante da leitura`() {
        val agora = 1_000_000L
        val p = EstadoCampo.Posicao(lat = -19.9, lon = -43.9, precisaoM = 4f, instante = agora - 42_000L)
        assertEquals(42L, p.idadeSegundos(agora))
        // Relogio recuado nao pode virar idade negativa.
        assertEquals(0L, p.idadeSegundos(agora - 90_000L))
    }
}
