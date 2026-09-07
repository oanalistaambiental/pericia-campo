package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.captura.Orientacoes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Geometria da orientacao do aparelho.
 *
 * Estes testes existem porque o azimute sai daqui e vai para a legenda queimada na foto, para
 * o laudo, para o CSV e para o KML. Um erro de sinal ou de eixo aqui produz um laudo com a
 * direcao errada, e nada na tela denunciaria isso.
 *
 * A matriz de rotacao e montada a mao a partir dos tres eixos do aparelho escritos no mundo
 * (X leste, Y norte, Z para cima), que e a mesma convencao do SensorManager. Cada eixo e uma
 * COLUNA da matriz.
 */
class OrientacaoTest {

    private val g = Math.PI / 180

    private fun matriz(
        eixoX: Triple<Double, Double, Double>,
        eixoY: Triple<Double, Double, Double>,
        eixoZ: Triple<Double, Double, Double>
    ) = floatArrayOf(
        eixoX.first.toFloat(), eixoY.first.toFloat(), eixoZ.first.toFloat(),
        eixoX.second.toFloat(), eixoY.second.toFloat(), eixoZ.second.toFloat(),
        eixoX.third.toFloat(), eixoY.third.toFloat(), eixoZ.third.toFloat()
    )

    // Aparelho em pe, com a camera apontada para cada ponto cardeal.
    private val emPeNorte = matriz(Triple(1.0, 0.0, 0.0), Triple(0.0, 0.0, 1.0), Triple(0.0, -1.0, 0.0))
    private val emPeLeste = matriz(Triple(0.0, -1.0, 0.0), Triple(0.0, 0.0, 1.0), Triple(-1.0, 0.0, 0.0))
    private val emPeSul = matriz(Triple(-1.0, 0.0, 0.0), Triple(0.0, 0.0, 1.0), Triple(0.0, 1.0, 0.0))
    private val emPeOeste = matriz(Triple(0.0, 1.0, 0.0), Triple(0.0, 0.0, 1.0), Triple(1.0, 0.0, 0.0))
    private val deitadoTelaParaCima = matriz(Triple(1.0, 0.0, 0.0), Triple(0.0, 1.0, 0.0), Triple(0.0, 0.0, 1.0))

    @Test
    fun `o rumo e o da camera, nao o do topo do aparelho`() {
        assertEquals(0.0, Orientacoes.azimuteCameraGraus(emPeNorte)!!.toDouble(), 0.5)
        assertEquals(90.0, Orientacoes.azimuteCameraGraus(emPeLeste)!!.toDouble(), 0.5)
        assertEquals(180.0, Orientacoes.azimuteCameraGraus(emPeSul)!!.toDouble(), 0.5)
        assertEquals(270.0, Orientacoes.azimuteCameraGraus(emPeOeste)!!.toDouble(), 0.5)
    }

    @Test
    fun `sem direcao horizontal o app admite em vez de inventar`() {
        // Deitado com a tela para cima, a camera olha o chao: nao existe rumo.
        assertNull(Orientacoes.azimuteCameraGraus(deitadoTelaParaCima))
        val zenite = matriz(Triple(1.0, 0.0, 0.0), Triple(0.0, 1.0, 0.0), Triple(0.0, 0.0, -1.0))
        assertNull(Orientacoes.azimuteCameraGraus(zenite))
    }

    @Test
    fun `elevacao da camera e zero na horizontal e menos noventa apontando para o chao`() {
        assertEquals(0.0, Orientacoes.elevacaoCameraGraus(emPeNorte).toDouble(), 0.5)
        assertEquals(-90.0, Orientacoes.elevacaoCameraGraus(deitadoTelaParaCima).toDouble(), 0.5)
    }

    @Test
    fun `inclinar a camera nao muda o rumo`() {
        val z = Triple(0.0, -cos(30 * g), -sin(30 * g))
        val y = Triple(0.0, sin(30 * g) * -1.0, cos(30 * g))
        val incl = matriz(Triple(1.0, 0.0, 0.0), y, z)
        assertEquals(0.0, Orientacoes.azimuteCameraGraus(incl)!!.toDouble(), 0.5)
        assertEquals(30.0, Orientacoes.elevacaoCameraGraus(incl).toDouble(), 0.5)
    }

    @Test
    fun `clinometro le o angulo da superficie de zero a noventa`() {
        for (talude in listOf(0, 5, 10, 20, 30, 45, 60, 75, 90)) {
            val a = talude * g
            val m = matriz(
                Triple(1.0, 0.0, 0.0),
                Triple(0.0, cos(a), -sin(a)),
                Triple(0.0, sin(a), cos(a))
            )
            assertEquals(
                "talude de $talude graus",
                talude.toDouble(), Orientacoes.inclinacaoSuperficieGraus(m).toDouble(), 0.5
            )
        }
    }

    @Test
    fun `declividade em porcentagem nao e o angulo`() {
        // Erro comum de laudo: 45 graus nao e 50%, e 100%.
        // declividadePercent passou a devolver null acima de 80 graus, onde a tangente
        // explode; abaixo disso continua sendo o mesmo numero de sempre.
        assertEquals(100.0, Orientacoes.declividadePercent(45f)!!.toDouble(), 0.1)
        assertEquals(50.0, Orientacoes.declividadePercent(26.565f)!!.toDouble(), 0.1)
        assertEquals(0.0, Orientacoes.declividadePercent(0f)!!.toDouble(), 0.01)
        // O sinal nao importa: subida e descida tem a mesma declividade.
        assertEquals(
            Orientacoes.declividadePercent(30f)!!.toDouble(),
            Orientacoes.declividadePercent(-30f)!!.toDouble(), 0.01
        )
    }

    @Test
    fun `rosa dos ventos e diferenca angular cruzam o norte sem erro`() {
        assertEquals("N", Orientacoes.rosa(0f))
        assertEquals("N", Orientacoes.rosa(359f))
        assertEquals("NE", Orientacoes.rosa(45f))
        assertEquals("E", Orientacoes.rosa(90f))
        assertEquals("S", Orientacoes.rosa(180f))
        assertEquals("O", Orientacoes.rosa(270f))
        assertEquals(20.0, Orientacoes.diferencaAngular(350f, 10f).toDouble(), 0.01)
        assertEquals(20.0, Orientacoes.diferencaAngular(10f, 350f).toDouble(), 0.01)
        assertEquals(180.0, Orientacoes.diferencaAngular(0f, 180f).toDouble(), 0.01)
    }

    @Test
    fun `a leitura do clinometro nunca sai do intervalo util`() {
        for (talude in 0..90 step 3) {
            val a = talude * g
            val m = matriz(
                Triple(1.0, 0.0, 0.0),
                Triple(0.0, cos(a), -sin(a)),
                Triple(0.0, sin(a), cos(a))
            )
            val v = Orientacoes.inclinacaoSuperficieGraus(m)
            assertTrue("leitura fora de 0..90: $v", v >= -0.01f && v <= 90.01f)
        }
        assertNotNull(Orientacoes.inclinacaoSuperficieGraus(deitadoTelaParaCima))
    }
}
