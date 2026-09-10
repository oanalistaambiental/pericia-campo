package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.taxas.FasesLicenciamento
import br.com.oanalistaambiental.pericia.taxas.TaxaUfemg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Le o MESMO arquivo que vai dentro do aplicativo — se alguem editar a tabela de taxas e
 * quebrar uma celula, o build para aqui, nao no celular de quem esta calculando uma taxa.
 */
class TaxaUfemgTest {

    private val tabela by lazy {
        TaxaUfemg.carregar { File("src/main/assets/taxas/ufemg_2026.json").inputStream() }
    }

    @Test
    fun `a tabela carrega inteira, com a fonte declarada`() {
        assertEquals(2026, tabela.exercicio)
        assertEquals(5.7899, tabela.valorUfemg, 0.0001)
        assertTrue("precisa citar a resolucao que fixa o valor", tabela.fonte.contains("5.969"))
        assertEquals(15, tabela.taxaExpedienteIntervencao.size)
        assertEquals(12, tabela.taxaFlorestal.size)
    }

    @Test
    fun `taxa de licenciamento cobre as duas listagens, com precos diferentes`() {
        val lic = tabela.licenciamento
        // LAT-LP, classe 4: industrial cobra mais que agrosilvipastoril.
        val industrial = lic.valor("lat_lp", 4, listagemG = false)
        val agro = lic.valor("lat_lp", 4, listagemG = true)
        assertEquals(22366.38, industrial!!, 0.001)
        assertEquals(8516.94, agro!!, 0.001)
        assertTrue(industrial > agro)
    }

    @Test
    fun `classe sem linha aplicavel devolve null, nao um numero inventado`() {
        // LAT nao se aplica a classe 2 na tabela oficial — a celula e "-".
        assertNull(tabela.licenciamento.valor("lat_lp", 2, listagemG = false))
    }

    @Test
    fun `opcoes de fase batem com a modalidade`() {
        assertEquals(1, FasesLicenciamento.opcoes("LAS/Cadastro").size)
        assertEquals(5, FasesLicenciamento.opcoes("LAT").size)
        assertEquals(2, FasesLicenciamento.opcoes("LAC1").size)
        assertEquals(7, FasesLicenciamento.opcoes("LAC2").size)
    }

    @Test
    fun `todo item da taxa florestal tem so a parte variavel`() {
        tabela.taxaFlorestal.forEach { assertEquals(0.0, it.fixoUfemg, 0.0) }
    }

    @Test
    fun `7-24-1 supressao para uso alternativo do solo — 2 hectares`() {
        val item = tabela.taxaExpedienteIntervencao.first { it.codigo == "7.24.1" }
        // (124 + 1*2) * 5,7899
        val esperado = (124 + 1 * 2) * 5.7899
        assertEquals(esperado, TaxaUfemg.calcular(item, 2.0, tabela.valorUfemg), 0.001)
    }

    @Test
    fun `7-24-6 intervencao em APP sem supressao — variavel de 30, nao 1`() {
        val item = tabela.taxaExpedienteIntervencao.first { it.codigo == "7.24.6" }
        assertEquals(30.0, item.variavelUfemgPorUnidade, 0.0)
        val esperado = (124 + 30 * 0.5) * 5.7899
        assertEquals(esperado, TaxaUfemg.calcular(item, 0.5, tabela.valorUfemg), 0.001)
    }

    @Test
    fun `madeira de floresta nativa custa mais que a plantada, mesma quantidade`() {
        val plantada = tabela.taxaFlorestal.first { it.codigo == "2.00" }
        val nativa = tabela.taxaFlorestal.first { it.codigo == "2.02" }
        val a = TaxaUfemg.calcular(plantada, 10.0, tabela.valorUfemg)
        val b = TaxaUfemg.calcular(nativa, 10.0, tabela.valorUfemg)
        assertTrue(b > a)
    }
}
