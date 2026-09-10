package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.unidades.ConversorUnidades
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversorUnidadesTest {

    private fun unidade(lista: List<br.com.oanalistaambiental.pericia.unidades.UnidadeConversao>, id: String) =
        lista.first { it.id == id }

    @Test
    fun `1 m3 por hora e aproximadamente 0,2778 L por segundo`() {
        val m3h = unidade(ConversorUnidades.vazao, "m3_h")
        val ls = unidade(ConversorUnidades.vazao, "l_s")
        assertEquals(0.27778, ConversorUnidades.converter(1.0, m3h, ls), 0.0001)
    }

    @Test
    fun `1 L por segundo vira 86,4 m3 por dia`() {
        val ls = unidade(ConversorUnidades.vazao, "l_s")
        val m3dia = unidade(ConversorUnidades.vazao, "m3_dia")
        assertEquals(86.4, ConversorUnidades.converter(1.0, ls, m3dia), 0.001)
    }

    @Test
    fun `1 hectare e 10 mil metros quadrados`() {
        val ha = unidade(ConversorUnidades.area, "ha")
        val m2 = unidade(ConversorUnidades.area, "m2")
        assertEquals(10_000.0, ConversorUnidades.converter(1.0, ha, m2), 0.001)
    }

    @Test
    fun `1 km2 e 100 hectares`() {
        val km2 = unidade(ConversorUnidades.area, "km2")
        val ha = unidade(ConversorUnidades.area, "ha")
        assertEquals(100.0, ConversorUnidades.converter(1.0, km2, ha), 0.001)
    }

    @Test
    fun `1 m3 e mil litros`() {
        val m3 = unidade(ConversorUnidades.volume, "m3")
        val l = unidade(ConversorUnidades.volume, "l")
        assertEquals(1000.0, ConversorUnidades.converter(1.0, m3, l), 0.001)
    }

    @Test
    fun `1 tonelada e mil quilos`() {
        val t = unidade(ConversorUnidades.massa, "t")
        val kg = unidade(ConversorUnidades.massa, "kg")
        assertEquals(1000.0, ConversorUnidades.converter(1.0, t, kg), 0.001)
    }

    @Test
    fun `30 t por mes equivale a 1 t por dia, convencao de mes de 30 dias`() {
        val tMes = unidade(ConversorUnidades.taxaMassa, "t_mes")
        val tDia = unidade(ConversorUnidades.taxaMassa, "t_dia")
        assertEquals(1.0, ConversorUnidades.converter(30.0, tMes, tDia), 0.001)
    }

    @Test
    fun `1 t por dia e mil kg por dia`() {
        val tDia = unidade(ConversorUnidades.taxaMassa, "t_dia")
        val kgDia = unidade(ConversorUnidades.taxaMassa, "kg_dia")
        assertEquals(1000.0, ConversorUnidades.converter(1.0, tDia, kgDia), 0.001)
    }

    @Test
    fun `converter para a mesma unidade devolve o mesmo valor`() {
        val ha = unidade(ConversorUnidades.area, "ha")
        assertEquals(42.0, ConversorUnidades.converter(42.0, ha, ha), 0.0001)
    }
}
