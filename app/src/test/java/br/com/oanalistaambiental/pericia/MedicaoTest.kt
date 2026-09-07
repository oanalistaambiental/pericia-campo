package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.geo.Medicao
import br.com.oanalistaambiental.pericia.geo.Utm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Medicao de area e leitura de coordenada.
 *
 * Estes numeros vao para laudo. Uma area errada num auto de infracao e um problema serio, e
 * uma coordenada mal lida manda o perito para o lugar errado — por isso o teste cobre tambem
 * o que o app deve RECUSAR, nao so o que ele deve aceitar.
 */
class MedicaoTest {

    private fun v(lat: Double, lon: Double, p: Float = 5f) = Medicao.Vertice(lat, lon, p, 0L)

    private val base = Utm.projetar(-19.9167, -43.9345)

    /** Gera um ponto deslocado em metros sobre a grade UTM, para montar figuras exatas. */
    private fun deLocal(dE: Double, dN: Double): Pair<Double, Double> =
        Utm.inverter(base.easting + dE, base.northing + dN, base.zona, base.hemisferioSul)

    private fun quadrado(lado: Double, precisao: Float = 5f) = listOf(
        0.0 to 0.0, lado to 0.0, lado to lado, 0.0 to lado
    ).map { (e, n) -> deLocal(e, n) }.map { v(it.first, it.second, precisao) }

    @Test
    fun `a projecao UTM volta ao mesmo ponto`() {
        // Se projetar e desprojetar nao devolve o mesmo lugar, ha erro no calculo.
        for (la in listOf(-33.7, -25.0, -19.9167, -10.0, -3.1, 2.0)) {
            for (lo in listOf(-72.0, -60.0, -51.0, -43.9345, -35.0)) {
                val c = Utm.projetar(la, lo)
                val (la2, lo2) = Utm.inverter(c.easting, c.northing, c.zona, c.hemisferioSul)
                val erroM = Medicao.distanciaM(la, lo, la2, lo2)
                assertTrue("ida e volta em $la,$lo errou $erroM m", erroM < 0.01)
            }
        }
    }

    @Test
    fun `quadrado de cem metros da um hectare por dez`() {
        val q = Medicao.medir(quadrado(100.0))
        assertEquals(10_000.0, q.areaM2, 1.0)
        assertEquals(400.0, q.perimetroM, 2.0)
        assertEquals(1.0, q.areaHa, 0.001)
    }

    @Test
    fun `o sentido do caminhamento nao muda a area`() {
        val cantos = quadrado(100.0)
        assertEquals(
            Medicao.medir(cantos).areaM2,
            Medicao.medir(cantos.reversed()).areaM2,
            0.001
        )
    }

    @Test
    fun `menos de tres vertices nao formam area`() {
        assertEquals(0.0, Medicao.medir(emptyList()).areaM2, 0.0)
        assertEquals(0.0, Medicao.medir(listOf(v(-19.0, -43.0))).areaM2, 0.0)
        val dois = Medicao.medir(listOf(v(-19.0, -43.0), v(-19.001, -43.0)))
        assertEquals(0.0, dois.areaM2, 0.0)
        // Mas a distancia entre eles continua valendo.
        assertTrue("distancia entre dois pontos", dois.perimetroM > 100)
        assertEquals("—", Medicao.medir(emptyList()).areaFormatada())
    }

    @Test
    fun `a incerteza cresce com a precisao pior e barra area que nao serve para laudo`() {
        val bom = Medicao.medir(quadrado(100.0, precisao = 5f))
        val ruim = Medicao.medir(quadrado(100.0, precisao = 30f))
        assertTrue("incerteza deveria crescer", ruim.incertezaAreaM2 > bom.incertezaAreaM2)
        // 1 ha medido com +-30 m e dominado pela incerteza.
        assertFalse("1 ha com +-30 m nao deveria passar", ruim.confiavel())
        // 100 ha com +-5 m e util.
        assertTrue("100 ha com +-5 m deveria passar", Medicao.medir(quadrado(1000.0, 5f)).confiavel())
    }

    @Test
    fun `os tres formatos de coordenada chegam ao mesmo ponto`() {
        val lat = -19.9167
        val lon = -43.9345
        val c = Utm.projetar(lat, lon)
        val entradas = listOf(
            "-19.9167, -43.9345",
            "-19,9167; -43,9345",
            "-19.9167 -43.9345",
            Medicao.formatarGms(lat, lon),
            "19°55'0.12\"S 43°56'4.20\"W",
            "%dS %.0fE %.0fN".format(c.zona, c.easting, c.northing)
        )
        for (texto in entradas) {
            val r = Medicao.interpretar(texto)
            assertNotNull("nao interpretou: $texto", r)
            val erro = Medicao.distanciaM(lat, lon, r!!.lat, r.lon)
            assertTrue("\"$texto\" caiu a $erro m do esperado", erro < 30)
            assertNull("aviso indevido em $texto", r.aviso)
        }
    }

    @Test
    fun `texto que nao e coordenada e recusado em vez de virar palpite`() {
        for (lixo in listOf("", "abc", "12", "999, 999", "-19.9167", "clique aqui", "23X 1 2")) {
            assertNull("interpretou lixo: \"$lixo\"", Medicao.interpretar(lixo))
        }
    }

    @Test
    fun `ponto fora do Brasil avisa sobre o sinal trocado`() {
        val paris = Medicao.interpretar("48.8566, 2.3522")
        assertNotNull(paris)
        assertNotNull("Paris deveria avisar", paris!!.aviso)
        // Latitude positiva no Brasil e quase sempre sinal esquecido.
        val trocado = Medicao.interpretar("19.9167, -43.9345")
        assertNotNull(trocado)
        assertNotNull("latitude positiva deveria avisar", trocado!!.aviso)
    }

    @Test
    fun `grau minuto e segundo e reversivel`() {
        val lat = -19.9167
        val lon = -43.9345
        val gms = Medicao.formatarGms(lat, lon)
        val volta = Medicao.interpretar(gms)
        assertNotNull("nao releu o proprio GMS: $gms", volta)
        assertTrue(Medicao.distanciaM(lat, lon, volta!!.lat, volta.lon) < 1.0)
    }

    @Test
    fun `distancia e rumo conferem em direcoes conhecidas`() {
        // Um ponto ao norte tem rumo 0; a leste, 90.
        val norte = Medicao.rumoGraus(-19.9167, -43.9345, -19.9100, -43.9345)
        assertEquals(0.0, norte, 0.5)
        val leste = Medicao.rumoGraus(-19.9167, -43.9345, -19.9167, -43.9280)
        assertEquals(90.0, leste, 0.5)
        // Um grau de latitude tem cerca de 111 km.
        assertEquals(111_195.0, Medicao.distanciaM(0.0, 0.0, 1.0, 0.0), 200.0)
    }
}
