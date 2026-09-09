package br.com.oanalistaambiental.pericia.enquadramento

import br.com.oanalistaambiental.pericia.enquadramento.norma.Enquadramento
import br.com.oanalistaambiental.pericia.enquadramento.norma.Grau
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A lacuna de redação da DN 217 em C-04-21-9, e os casos reais que a cercam.
 *
 * As três faixas desta atividade foram escritas com desigualdade estrita — "< 2 ha : Pequeno",
 * "2 ha < Área Útil < 5 ha : Médio", "> 5 ha : Grande" — então 2 ha e 5 ha não pertencem a
 * faixa nenhuma. Estes testes travam a decisão de projeto de NÃO escolher um lado em silêncio:
 * escolher Médio subestima a modalidade, escolher Grande a superestima, e as duas leituras
 * saem do mesmo texto.
 */
class LacunaDeFaixaTest {

    private val r = Base.regras
    private val quimicaGenerica = Base.regras.atividade("C-04-21-9")!!

    @Test(expected = Enquadramento.LacunaDeFaixa::class)
    fun `exatamente no limite de pequeno a norma nao define faixa`() {
        Enquadramento.porteDe(quimicaGenerica, 2.0)
    }

    @Test(expected = Enquadramento.LacunaDeFaixa::class)
    fun `exatamente no limite de medio a norma nao define faixa`() {
        Enquadramento.porteDe(quimicaGenerica, 5.0)
    }

    /** Fora do ponto exato o cálculo segue normalmente — a lacuna é pontual, não geral. */
    @Test
    fun `fora do ponto exato o porte sai sem hesitacao`() {
        assertEquals(Grau.P, Enquadramento.porteDe(quimicaGenerica, 1.99))
        assertEquals(Grau.M, Enquadramento.porteDe(quimicaGenerica, 3.0))
        assertEquals(Grau.G, Enquadramento.porteDe(quimicaGenerica, 5.01))
    }

    /**
     * O valor da exceção está em mostrar as DUAS leituras. Uma exceção que só dissesse "não
     * sei" deixaria o usuário sem saída; o que ele precisa é ver os dois enquadramentos
     * possíveis e levar isso à URA.
     */
    @Test
    fun `a excecao entrega as duas leituras possiveis, na ordem certa`() {
        val e = runCatching { Enquadramento.porteDe(quimicaGenerica, 5.0) }
            .exceptionOrNull() as Enquadramento.LacunaDeFaixa
        val leituras = e.leituras()
        assertEquals(2, leituras.size)
        assertEquals(Grau.M, leituras[0].porte)
        assertEquals(Grau.G, leituras[1].porte)
        assertTrue("cada leitura precisa dizer de onde sai", leituras.all { it.fundamento.length > 20 })

        val noPiso = runCatching { Enquadramento.porteDe(quimicaGenerica, 2.0) }
            .exceptionOrNull() as Enquadramento.LacunaDeFaixa
        assertEquals(Grau.P, noPiso.leituras()[0].porte)
        assertEquals(Grau.M, noPiso.leituras()[1].porte)
    }

    /**
     * A orientação cita prática observada da Administração e precisa deixar claro que é
     * prática, não regra — o app não pode transformar um caso concreto em norma.
     */
    @Test
    fun `a orientacao separa pratica observada de regra escrita`() {
        val t = Enquadramento.LacunaDeFaixa.ORIENTACAO
        assertTrue(t.contains("URA"))
        assertTrue("precisa dizer que não vincula", t.contains("não vincula"))
        assertTrue("precisa dizer que é prática, não regra", t.contains("não regra escrita"))
    }

    /**
     * A lacuna é de UMA atividade. Se aparecesse em outras sem estar documentada, seria sinal
     * de que a extração assumiu convenção onde a norma não tem — o erro que já custou caro
     * neste catálogo.
     */
    @Test
    fun `so as atividades documentadas tem lacuna`() {
        val comLacuna = r.atividades.filter { it.valoresSemFaixa.isNotEmpty() }
        assertEquals(listOf("C-04-21-9"), comLacuna.map { it.codigo })
        assertTrue("a lacuna precisa estar explicada na nota da atividade",
            comLacuna.first().nota?.contains("lacuna") == true)
    }

    /**
     * Casos reais de um mesmo parecer deferido sob a DN 217 (complexo de fertilizantes
     * fosfatados), usados como âncora da Tabela 2: as três atividades foram enquadradas juntas
     * e as classes precisam bater. Anonimizado — o que importa aqui é o número, não o processo.
     */
    @Test
    fun `o parecer real do complexo de fertilizantes bate com a Tabela 2`() {
        fun classe(codigo: String, valor: Double): Int {
            val a = r.atividade(codigo)!!
            return Enquadramento.classeDe(r, Enquadramento.porteDe(a, valor), a.pp.geral)
        }
        assertEquals("ácido sulfúrico de enxofre, 1.000.000 t/ano", 6, classe("C-04-16-2", 1_000_000.0))
        assertEquals("ácido fosfórico, 250.000 t/ano", 5, classe("C-04-17-0", 250_000.0))
        assertEquals("intermediários para fertilizantes, 950.000 t/ano", 4, classe("C-04-18-9", 950_000.0))
    }

    /**
     * O parâmetro de porte de C-04-08-1 é "Área Construída", não "Área útil" como na maioria
     * dos vizinhos de C-04. Trocar o campo na tela faria o usuário informar o número errado
     * sem nenhum aviso.
     */
    @Test
    fun `explosivos medem area construida, nao area util`() {
        assertEquals("Área Construída", r.atividade("C-04-08-1")!!.parametro)
        assertEquals("Área útil", r.atividade("C-04-21-9")!!.parametro)
    }
}
