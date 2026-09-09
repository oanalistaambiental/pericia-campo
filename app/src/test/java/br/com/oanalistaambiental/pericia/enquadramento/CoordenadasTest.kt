package br.com.oanalistaambiental.pericia.enquadramento

import br.com.oanalistaambiental.pericia.geo.Coordenadas
import br.com.oanalistaambiental.pericia.geo.Utm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Leitura da coordenada que o usuario cola.
 *
 * Erro aqui muda o critério locacional e, com ele, a classe e a modalidade — ou seja, muda o
 * resultado da simulação inteira. Por isso o teste cobre também o que precisa ser RECUSADO.
 */
class CoordenadasTest {

    private fun distanciaAproxM(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
        val a = Utm.projetar(la1, lo1)
        val b = Utm.projetar(la2, lo2, a.zona)
        val de = a.easting - b.easting
        val dn = a.northing - b.northing
        return kotlin.math.sqrt(de * de + dn * dn)
    }

    @Test
    fun `a projecao UTM volta ao mesmo ponto`() {
        for (la in listOf(-22.9, -19.9167, -14.2)) {
            for (lo in listOf(-51.0, -45.0, -40.0)) {
                val c = Utm.projetar(la, lo)
                val (la2, lo2) = Utm.inverter(c.easting, c.northing, c.zona, c.hemisferioSul)
                assertTrue("ida e volta em $la,$lo", abs(la - la2) < 1e-7 && abs(lo - lo2) < 1e-7)
            }
        }
    }

    @Test
    fun `os tres formatos chegam ao mesmo ponto`() {
        val lat = -19.9167
        val lon = -43.9345
        val c = Utm.projetar(lat, lon)
        val entradas = listOf(
            "-19.9167, -43.9345",
            "-19,9167; -43,9345",
            "-19.9167 -43.9345",
            Coordenadas.formatarGms(lat, lon),
            "19°55'0.12\"S 43°56'4.20\"W",
            "%dS %.0fE %.0fN".format(c.zona, c.easting, c.northing)
        )
        for (texto in entradas) {
            val r = Coordenadas.interpretar(texto)
            assertNotNull("nao interpretou: $texto", r)
            assertTrue("\"$texto\" caiu longe demais", distanciaAproxM(lat, lon, r!!.lat, r.lon) < 30)
            assertNull("aviso indevido em $texto", r.aviso)
        }
    }

    @Test
    fun `texto que nao e coordenada e recusado`() {
        for (lixo in listOf("", "abc", "12", "999, 999", "-19.9167", "sem coordenada", "23X 1 2")) {
            assertNull("interpretou lixo: \"$lixo\"", Coordenadas.interpretar(lixo))
        }
    }

    @Test
    fun `sinal trocado dispara aviso em vez de passar batido`() {
        val positiva = Coordenadas.interpretar("19.9167, -43.9345")
        assertNotNull(positiva)
        assertNotNull("latitude positiva no Brasil deveria avisar", positiva!!.aviso)
        val paris = Coordenadas.interpretar("48.8566, 2.3522")
        assertNotNull(paris)
        assertNotNull("ponto fora do Brasil deveria avisar", paris!!.aviso)
    }

    @Test
    fun `minas gerais cai nas zonas 22 23 e 24`() {
        assertEquals(23, Utm.zonaDe(-43.9345))
        assertEquals(22, Utm.zonaDe(-50.0))
        assertEquals(24, Utm.zonaDe(-40.0))
    }
}
