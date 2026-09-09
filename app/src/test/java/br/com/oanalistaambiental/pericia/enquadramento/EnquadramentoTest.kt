package br.com.oanalistaambiental.pericia.enquadramento

import br.com.oanalistaambiental.pericia.enquadramento.norma.Conferencia
import br.com.oanalistaambiental.pericia.enquadramento.norma.Enquadramento
import br.com.oanalistaambiental.pericia.enquadramento.norma.Grau
import org.junit.Assert.*
import org.junit.Test

class PorteTest {

    private val r = Base.regras

    @Test
    fun `o limite da faixa pertence à faixa menor`() {
        val a = r.atividade("A-02-03-8")!!   // minério de ferro: P ate 300.000, M ate 1.500.000
        assertEquals(Grau.P, Enquadramento.porteDe(a, 300_000.0))
        assertEquals(Grau.M, Enquadramento.porteDe(a, 300_000.1))
        assertEquals(Grau.M, Enquadramento.porteDe(a, 1_500_000.0))
        assertEquals(Grau.G, Enquadramento.porteDe(a, 1_500_000.1))
    }

    @Test
    fun `a atividade com faixa estrita trata o limite ao contrário`() {
        // A-03-01-8: pequeno e ESTRITAMENTE menor que 10.000; 10.000 exatos ja e medio.
        val a = r.atividade("A-03-01-8")!!
        assertTrue("esta atividade depende do limite exclusivo", a.limitePExclusivo)
        assertEquals(Grau.P, Enquadramento.porteDe(a, 9_999.0))
        assertEquals(Grau.M, Enquadramento.porteDe(a, 10_000.0))
    }

    @Test
    fun `a unidade alternativa usa as faixas dela`() {
        val a = r.atividade("A-02-09-7")!!   // britas: t/ano ou m3/ano
        assertEquals(Grau.M, Enquadramento.porteDe(a, 100_000.0, usarAlternativa = false))
        assertEquals(Grau.G, Enquadramento.porteDe(a, 100_000.0, usarAlternativa = true))
    }

    @Test
    fun `atividade categórica usa o rótulo, não número`() {
        val a = r.atividade("A-05-03-7")!!   // barragem: Classe I, II, III
        assertEquals(Grau.P, Enquadramento.porteDe(a, "Classe I"))
        assertEquals(Grau.G, Enquadramento.porteDe(a, "Classe III"))
    }

    @Test(expected = Enquadramento.DadoFaltante::class)
    fun `categoria inexistente não vira palpite`() {
        Enquadramento.porteDe(r.atividade("A-05-03-7")!!, "Classe IV")
    }

    @Test(expected = Enquadramento.DadoFaltante::class)
    fun `atividade categórica recusa valor numérico`() {
        Enquadramento.porteDe(r.atividade("A-05-03-7")!!, 12.0)
    }
}

class CriterioLocacionalTest {

    private val r = Base.regras

    @Test
    fun `sem critério nenhum o fator é zero`() {
        assertEquals(0, Enquadramento.fatorLocacional(emptyList()))
    }

    /**
     * O erro mais comum de quem faz o enquadramento na mão: somar os pesos.
     * O art. 6º, §3º manda considerar o de MAIOR peso.
     */
    @Test
    fun `os pesos não se somam - prevalece o maior`() {
        val doisDePesoUm = listOf(r.criterio("zona_amortecimento")!!, r.criterio("corredor_ecologico")!!)
        assertEquals(1, Enquadramento.fatorLocacional(doisDePesoUm))

        val comUmDePesoDois = doisDePesoUm + r.criterio("uc_protecao_integral")!!
        assertEquals(2, Enquadramento.fatorLocacional(comUmDePesoDois))
    }

    @Test
    fun `o critério determinante é o de maior peso`() {
        val lista = listOf(r.criterio("corredor_ecologico")!!, r.criterio("sitio_ramsar")!!)
        assertEquals("sitio_ramsar", Enquadramento.criterioDeterminante(lista)!!.id)
    }
}

class ClasseEModalidadeTest {

    private val r = Base.regras

    @Test
    fun `potencial poluidor pequeno resulta classe 1 em qualquer porte`() {
        Grau.entries.forEach { porte ->
            assertEquals("porte $porte com potencial P", 1, Enquadramento.classeDe(r, porte, Grau.P))
        }
    }

    @Test
    fun `grande porte com grande potencial é classe 6`() {
        assertEquals(6, Enquadramento.classeDe(r, Grau.G, Grau.G))
    }

    @Test
    fun `conjunto de atividades vale pela de maior classe`() {
        assertEquals(5, Enquadramento.classeDoConjunto(listOf(1, 3, 5, 2)))
    }

    @Test
    fun `o critério locacional pode mudar a modalidade`() {
        assertEquals("LAS/RAS", Enquadramento.modalidadeDe(r, 3, 0).sigla)
        assertEquals("LAC1", Enquadramento.modalidadeDe(r, 3, 1).sigla)
        assertEquals("LAC2", Enquadramento.modalidadeDe(r, 3, 2).sigla)
    }

    @Test
    fun `classe 1 nunca sai do simplificado`() {
        (0..2).forEach { fator ->
            assertTrue(Enquadramento.modalidadeDe(r, 1, fator).sigla.startsWith("LAS"))
        }
    }
}

class SimulacaoTest {

    private val r = Base.regras

    @Test
    fun `caso real - lavra de minério de ferro de 500 mil toneladas por ano, sem restrição`() {
        val a = r.atividade("A-02-03-8")!!
        val porte = Enquadramento.porteDe(a, 500_000.0)
        val res = Enquadramento.simular(r, a, porte, emptyList())

        assertEquals(Grau.M, res.porte)
        assertEquals(Grau.M, res.potencialGeral)
        assertEquals(3, res.classe)
        assertEquals(0, res.fatorLocacional)
        assertEquals("LAS/RAS", res.modalidade.sigla)
        assertEquals(180, res.prazoAnaliseDias)
        assertEquals(10, res.modalidade.validadeAnos)
    }

    @Test
    fun `a mesma lavra dentro de unidade de conservação de proteção integral muda de modalidade`() {
        val a = r.atividade("A-02-03-8")!!
        val porte = Enquadramento.porteDe(a, 500_000.0)
        val res = Enquadramento.simular(r, a, porte, listOf(r.criterio("uc_protecao_integral")!!))

        assertEquals(3, res.classe)
        assertEquals(2, res.fatorLocacional)
        assertEquals("LAC2", res.modalidade.sigla)
    }

    @Test
    fun `a memória de cálculo acompanha todo resultado`() {
        val a = r.atividade("A-05-02-0")!!
        val res = Enquadramento.simular(r, a, Enquadramento.porteDe(a, 2_000_000.0), emptyList())

        assertEquals(Grau.G, res.porte)
        assertEquals(6, res.classe)
        assertEquals("LAC2", res.modalidade.sigla)
        assertTrue("todo passo precisa citar o fundamento",
            res.passos.isNotEmpty() && res.passos.all { it.fundamento.isNotBlank() })
        assertTrue(res.passos.any { it.rotulo == "Classe" })
        assertTrue(res.passos.any { it.rotulo == "Modalidade" })
    }

    @Test
    fun `havendo EIA ou audiência o prazo de análise dobra`() {
        val a = r.atividade("A-05-02-0")!!
        val res = Enquadramento.simular(r, a, Grau.G, emptyList(), comEiaOuAudiencia = true)
        assertEquals(365, res.prazoAnaliseDias)
    }

    /**
     * Este teste apontava para A-01-01-5, que era "divergente" porque duas cópias da norma
     * discordavam sobre o potencial poluidor do Ar. Em 07/09/2026 o texto oficial resolveu a
     * dúvida (Ar = P) e a atividade passou a OFICIAL — então o teste precisou de outro caso.
     *
     * O novo caso é uma divergência de outra natureza, e mais interessante: a redação da
     * PRÓPRIA NORMA define a faixa Pequeno com piso ("2.400 t/ano < Matéria Prima Processada
     * < 12.000 t/ano") e não diz o que vale abaixo dele. Não há resposta certa a dar — só o
     * aviso. São 70 atividades nessa situação.
     */
    @Test
    fun `atividade com redação problemática na norma avisa quem está usando`() {
        val a = r.atividade("B-01-03-1")!!
        val res = Enquadramento.simular(r, a, Grau.P, emptyList())
        assertTrue("o aviso sobre a redação da norma precisa chegar ao usuário", res.avisos.isNotEmpty())
    }

    /** E a atividade que o texto oficial resolveu não avisa mais nada. */
    @Test
    fun `dado resolvido pelo texto oficial nao avisa mais`() {
        val a = r.atividade("A-01-01-5")!!
        assertEquals(Conferencia.OFICIAL, a.conferencia)
        assertEquals("o texto oficial diz Ar = P", Grau.P, a.pp.ar)
    }

    @Test
    fun `fator de restrição não muda o enquadramento, mas é avisado`() {
        val a = r.atividade("A-02-03-8")!!
        val semFator = Enquadramento.simular(r, a, Grau.M, emptyList())
        val comFator = Enquadramento.simular(r, a, Grau.M, emptyList(),
            fatoresIncidentes = listOf(r.fatores.first { it.id == "mata_atlantica" }))

        assertEquals("art. 6º, §4º: fator de restrição não confere peso",
            semFator.modalidade.sigla, comFator.modalidade.sigla)
        assertTrue(comFator.avisos.any { it.contains("não mudam o enquadramento") })
    }
}
