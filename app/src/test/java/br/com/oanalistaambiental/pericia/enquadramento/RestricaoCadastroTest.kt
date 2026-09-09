package br.com.oanalistaambiental.pericia.enquadramento

import br.com.oanalistaambiental.pericia.enquadramento.norma.Enquadramento
import br.com.oanalistaambiental.pericia.enquadramento.norma.Grau
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Arts. 19 e 20 da DN 217 — as atividades que NÃO podem usar LAS/Cadastro nas classes 1 e 2.
 *
 * O app rodou sem estes dois artigos até 07/09/2026: a Tabela 3 dizia Cadastro e o resultado
 * saía Cadastro, mesmo para aterro sanitário, suinocultura ou lavra de minério de ferro — todas
 * proibidas de usar essa modalidade. É erro na direção perigosa, porque REDUZ a exigência, e
 * quem seguisse o resultado protocolaria a modalidade errada.
 *
 * As duas regras têm sentidos opostos e é fácil inverter:
 *   art. 19 → lista do que é PROIBIDO (14 códigos nominais)
 *   art. 20 → toda a Listagem A é proibida, MENOS cinco códigos permitidos
 */
class RestricaoCadastroTest {

    private val r = Base.regras
    private val restr = r.restricoesCadastro

    @Test
    fun `a base carrega os dois artigos`() {
        assertEquals("art. 19 tem 14 códigos nominais", 14, restr.art19.size)
        assertEquals("art. 20 tem 5 exceções no parágrafo único", 5, restr.art20Excecoes.size)
        assertEquals(setOf(1, 2), restr.classesAtingidas)
        assertEquals("LAS/RAS", restr.modalidadeSubstituta)
        assertNotNull("a modalidade substituta precisa existir na base", r.modalidade("LAS/RAS"))
    }

    @Test
    fun `art 19 - codigo listado nao pode cadastro nas classes 1 e 2`() {
        // Aterro sanitário, alínea II.a — o caso mais conhecido.
        assertNotNull(restr.cadastroProibido("E-03-07-7", 1))
        assertNotNull(restr.cadastroProibido("E-03-07-7", 2))
        // Suinocultura, alínea IV.a.
        assertNotNull(restr.cadastroProibido("G-02-04-6", 1))
        // Crematório e lavanderia industrial entraram pela DN 240/2021.
        assertNotNull(restr.cadastroProibido("E-05-06-1", 2))
        assertNotNull(restr.cadastroProibido("F-06-02-5", 2))
    }

    @Test
    fun `art 19 - a partir da classe 3 o artigo nao se aplica`() {
        // A Tabela 3 já não daria Cadastro na classe 3, mas a regra precisa ser fiel ao caput.
        assertNull(restr.cadastroProibido("E-03-07-7", 3))
        assertNull(restr.cadastroProibido("G-02-04-6", 4))
    }

    @Test
    fun `art 19 - atividade fora da lista nao e atingida`() {
        assertNull(restr.cadastroProibido("D-01-02-5", 1))
        assertNull(restr.cadastroProibido("C-10-02-2", 2))
    }

    /** O art. 20 é o inverso: proíbe a Listagem A inteira e abre cinco exceções. */
    @Test
    fun `art 20 - mineraria fora das excecoes nao pode cadastro`() {
        assertNotNull("minério de ferro não é exceção", restr.cadastroProibido("A-02-03-8", 1))
        assertNotNull("minerais não metálicos não é exceção", restr.cadastroProibido("A-02-07-0", 2))
        assertNotNull("lavra subterrânea não é exceção", restr.cadastroProibido("A-01-03-1", 1))
    }

    @Test
    fun `art 20 - as cinco excecoes do paragrafo unico mantem o cadastro`() {
        listOf("A-03-01-8", "A-03-01-9", "A-03-02-6", "A-04-01-4", "A-06-01-1").forEach {
            assertNull("$it é exceção do parágrafo único e mantém Cadastro",
                restr.cadastroProibido(it, 1))
            assertNull(restr.cadastroProibido(it, 2))
        }
    }

    /** O efeito de ponta a ponta: a simulação inteira precisa mudar de modalidade. */
    @Test
    fun `a simulacao troca cadastro por RAS e explica por que`() {
        val a = r.atividade("G-02-04-6")!!   // suinocultura, art. 19, IV.a
        val res = Enquadramento.simular(r, a, Grau.P, emptyList())

        // Só faz sentido testar se a Tabela 3 realmente daria Cadastro aqui.
        val daTabela = Enquadramento.modalidadeDe(r, res.classe, res.fatorLocacional)
        if (daTabela.sigla.contains("CADASTRO", ignoreCase = true)) {
            assertEquals("LAS/RAS", res.modalidade.sigla)
            assertEquals(daTabela.sigla, res.modalidadeDaTabela3)
            assertTrue("precisa citar o artigo", res.restricaoCadastro!!.contains("art. 19"))
            assertTrue("o aviso precisa explicar a troca",
                res.avisos.any { it.contains("não admite LAS/Cadastro") })
            // A memória de cálculo precisa mostrar as duas etapas, não só o resultado final.
            assertTrue(res.passos.any { it.rotulo == "Modalidade pela Tabela 3" })
            assertTrue(res.passos.any { it.rotulo == "Modalidade aplicada" })
        }
    }

    /** E a atividade que é exceção continua saindo como a Tabela 3 mandou. */
    @Test
    fun `atividade excecao do art 20 nao tem a modalidade trocada`() {
        val a = r.atividade("A-03-01-8")!!   // areia e cascalho, exceção I
        val res = Enquadramento.simular(r, a, Grau.P, emptyList())
        assertNull("não houve substituição", res.modalidadeDaTabela3)
        assertNull(res.restricaoCadastro)
        assertEquals(
            Enquadramento.modalidadeDe(r, res.classe, res.fatorLocacional).sigla,
            res.modalidade.sigla
        )
    }
}
