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
}
