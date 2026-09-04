package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.geo.Utm
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A projecao UTM foi escrita a mao para nao depender de biblioteca de projecao.
 * Codigo assim erra em silencio: entrega um numero plausivel e errado. Estes testes
 * existem para que ele nao possa mais errar sem avisar.
 */
class UtmTest {

    @Test
    fun `no meridiano central o easting e exatamente 500000`() {
        val c = Utm.projetar(-19.5, Utm.meridianoCentral(23), 23)
        assertEquals(500_000.0, c.easting, 0.001)
    }

    @Test
    fun `zonas de Minas Gerais sao identificadas corretamente`() {
        assertEquals(23, Utm.zonaDe(-43.9345))   // Belo Horizonte
        assertEquals(22, Utm.zonaDe(-48.2772))   // Uberlandia
        assertEquals(24, Utm.zonaDe(-41.9494))   // Governador Valadares
    }

    /** Valores conferidos contra implementacao independente da serie de Snyder. */
    @Test
    fun `pontos conhecidos de Minas Gerais`() {
        val bh = Utm.projetar(-19.9167, -43.9345)
        assertEquals(611_520.35, bh.easting, 0.5)
        assertEquals(7_797_383.36, bh.northing, 0.5)
        assertEquals(23, bh.zona)

        val udi = Utm.projetar(-18.9186, -48.2772)
        assertEquals(786_799.30, udi.easting, 0.5)
        assertEquals(7_905_968.60, udi.northing, 0.5)
        assertEquals(22, udi.zona)

        val gv = Utm.projetar(-18.8511, -41.9494)
        assertEquals(189_191.55, gv.easting, 0.5)
        assertEquals(7_913_061.47, gv.northing, 0.5)
        assertEquals(24, gv.zona)
    }

    @Test
    fun `pontos proximos tem distancia metrica coerente na mesma zona`() {
        val a = Utm.projetar(-19.9167, -43.9345, 23)
        val b = Utm.projetar(-19.9257, -43.9345, 23)   // ~1 km ao sul
        val d = kotlin.math.hypot(a.easting - b.easting, a.northing - b.northing)
        assertEquals(1000.0, d, 15.0)
    }
}
