package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.geo.AlturaTrigonometrica
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlturaTrigonometricaTest {

    @Test
    fun `45 graus a 10 m soma a altura do observador`() {
        // tan(45) = 1, entao a altura acima do observador = distancia.
        val r = AlturaTrigonometrica.calcular(distanciaHorizontalM = 10.0, anguloGraus = 45.0, alturaObservadorM = 1.5)
        assertEquals(11.5, r.alturaM, 0.01)
    }

    @Test
    fun `angulo zero da so a altura do observador`() {
        val r = AlturaTrigonometrica.calcular(distanciaHorizontalM = 20.0, anguloGraus = 0.0, alturaObservadorM = 1.6)
        assertEquals(1.6, r.alturaM, 0.001)
    }

    @Test
    fun `incerteza cresce com a distancia, mesmo angulo`() {
        val perto = AlturaTrigonometrica.calcular(distanciaHorizontalM = 5.0, anguloGraus = 30.0)
        val longe = AlturaTrigonometrica.calcular(distanciaHorizontalM = 50.0, anguloGraus = 30.0)
        assertTrue(longe.incertezaM > perto.incertezaM)
        // A conta e linear na distancia, entao 10x mais longe = 10x mais incerteza.
        assertEquals(perto.incertezaM * 10, longe.incertezaM, 0.001)
    }

    @Test
    fun `incerteza cresce perto de 90 graus`() {
        val moderado = AlturaTrigonometrica.calcular(distanciaHorizontalM = 10.0, anguloGraus = 45.0)
        val quaseVertical = AlturaTrigonometrica.calcular(distanciaHorizontalM = 10.0, anguloGraus = 80.0)
        assertTrue(quaseVertical.incertezaM > moderado.incertezaM)
    }

    // ---------------------------------------------------------- metodo dos dois angulos

    @Test
    fun `duplo angulo com base no horizonte da o mesmo resultado do metodo simples`() {
        // Base em 0 graus (no nivel do observador) equivale a somar a altura do observador —
        // os dois metodos tem que concordar neste caso particular.
        val simples = AlturaTrigonometrica.calcular(distanciaHorizontalM = 10.0, anguloGraus = 30.0, alturaObservadorM = 0.0)
        val duplo = AlturaTrigonometrica.calcularDuploAngulo(
            distanciaHorizontalM = 10.0, anguloBaseGraus = 0.0, anguloTopoGraus = 30.0
        )
        assertEquals(simples.alturaM, duplo.alturaM, 0.0001)
    }

    @Test
    fun `duplo angulo soma a base abaixo do horizonte, nao subtrai`() {
        // Base 10 m abaixo da linha do observador (angulo negativo) e topo 20 m acima: a altura
        // do objeto e a distancia TOTAL entre as duas alturas, nao a diferenca de angulo.
        val d = 10.0
        val anguloBase = -Math.toDegrees(kotlin.math.atan(1.0)) // -45 graus: base 10 m abaixo
        val anguloTopo = Math.toDegrees(kotlin.math.atan(2.0))  // topo 20 m acima
        val r = AlturaTrigonometrica.calcularDuploAngulo(d, anguloBase, anguloTopo)
        assertEquals(30.0, r.alturaM, 0.01)
    }

    @Test
    fun `duplo angulo com base e topo iguais da altura zero`() {
        val r = AlturaTrigonometrica.calcularDuploAngulo(15.0, anguloBaseGraus = 12.0, anguloTopoGraus = 12.0)
        assertEquals(0.0, r.alturaM, 0.0001)
    }

    @Test
    fun `incerteza do duplo angulo e maior que a de uma leitura so, mas nao o dobro`() {
        // Soma em quadratura (RSS): raiz de 2 vezes uma leitura, nao 2 vezes.
        val umaLeitura = AlturaTrigonometrica.calcular(distanciaHorizontalM = 10.0, anguloGraus = 20.0).incertezaM
        val duasLeituras = AlturaTrigonometrica.calcularDuploAngulo(10.0, 20.0, 20.0).incertezaM
        assertEquals(umaLeitura * kotlin.math.sqrt(2.0), duasLeituras, 0.001)
        assertTrue(duasLeituras < umaLeitura * 2)
    }
}
