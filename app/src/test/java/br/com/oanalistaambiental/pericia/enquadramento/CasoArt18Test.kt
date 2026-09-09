package br.com.oanalistaambiental.pericia.enquadramento

import br.com.oanalistaambiental.pericia.enquadramento.norma.CasoEspecial
import br.com.oanalistaambiental.pericia.enquadramento.norma.Enquadramento
import br.com.oanalistaambiental.pericia.enquadramento.norma.Grau
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Art. 18 da DN 217 — as quatro situações em que a Tabela 3 não é a palavra final.
 *
 * A regra de projeto que estes testes travam: o caso do art. 18 é ANEXADO ao resultado como
 * alerta e NUNCA troca a modalidade sozinho. Todas as quatro hipóteses dependem de um fato que
 * o aplicativo não pode conhecer (é ampliação? é repotenciação? a quantidade é limitada?), e
 * decidir por conta própria a partir de um fato presumido levaria o usuário a formalizar
 * processo na modalidade errada com a assinatura dele, não a nossa.
 */
class CasoArt18Test {

    private val r = Base.regras

    private val CODIGOS = listOf("F-02-01-1", "E-01-09-0", "E-02-01-1", "E-02-01-2")

    @Test
    fun `os quatro casos existem e apontam para atividades do catalogo`() {
        assertEquals("o art. 18 tem quatro casos", 4, r.casosArt18.porCodigo.size)
        for (c in CODIGOS) {
            assertNotNull("faltou o caso do $c", r.casosArt18.para(c))
            assertNotNull(
                "o caso do art. 18 aponta para $c, que precisa existir no catálogo — " +
                    "caso órfão é regra que nunca dispara",
                r.atividade(c)
            )
        }
    }

    @Test
    fun `todo caso traz condicao, referencia e o texto do paragrafo`() {
        for (c in CODIGOS) {
            val caso = r.casosArt18.para(c)!!
            assertTrue("$c sem referência", caso.referencia.contains("art. 18"))
            assertTrue("$c sem condição escrita", caso.condicao.length > 20)
            assertTrue("$c sem o texto literal do parágrafo", caso.texto.length > 40)
        }
    }

    /**
     * A modalidade alternativa precisa ser uma sigla que existe. Uma sigla inventada só
     * apareceria na tela, sem erro nenhum, e o usuário levaria para o processo um nome de
     * modalidade que a norma não usa — foi assim que "RAS" entrou no lugar de "LAS/RAS" nas
     * restrições do art. 19.
     */
    @Test
    fun `a modalidade alternativa e uma sigla real`() {
        for (c in CODIGOS) {
            val caso = r.casosArt18.para(c)!!
            if (caso.efeito != CasoEspecial.Efeito.MODALIDADE_ALTERNATIVA) continue
            val sigla = caso.modalidade
            assertNotNull("$c promete modalidade alternativa e não diz qual", sigla)
            assertNotNull("$c aponta para a modalidade inexistente '$sigla'", r.modalidade(sigla!!))
        }
    }

    /**
     * A armadilha do texto consolidado: o SIAM publica DOIS §3º seguidos, o vigente (DN
     * 240/2021, LAS Cadastro, PCH e CGH) e logo abaixo a redação original revogada (LAS/RAS, só
     * PCH). Extrair pegando a ÚLTIMA ocorrência aplica a regra revogada. Este teste trava a
     * redação certa.
     */
    @Test
    fun `o paragrafo 3o carregado e o da DN 240 de 2021, nao a redacao revogada`() {
        val pch = r.casosArt18.para("E-02-01-1")!!
        assertEquals(
            "o §3º vigente leva a LAS/Cadastro; LAS/RAS é a redação revogada",
            "LAS/Cadastro", pch.modalidade
        )
        assertTrue("o §3º vigente cita 30 MW para PCH", pch.condicao.contains("30 MW"))
        assertNotNull("a armadilha das duas redações precisa estar documentada", pch.armadilha)

        val cgh = r.casosArt18.para("E-02-01-2")!!
        assertEquals("LAS/Cadastro", cgh.modalidade)
        assertTrue("o §3º vigente cita 5 MW para CGH", cgh.condicao.contains("5 MW"))
    }

    @Test
    fun `o transporte de perigosos e exigencia adicional, nao modalidade alternativa`() {
        val caso = r.casosArt18.para("F-02-01-1")!!
        assertEquals(CasoEspecial.Efeito.EXIGENCIA_ADICIONAL, caso.efeito)
        assertTrue("o §1º exige o PEA", caso.resumo.contains("PEA"))
        assertNotNull(
            "a segunda hipótese do §1º (quantidade limitada, ANTT) não pode se perder",
            caso.segundaHipotese
        )
    }

    /**
     * O coração da regra: simular a atividade do §2º e conferir que a modalidade entregue
     * continua sendo a da Tabela 3 e que o alerta veio junto.
     */
    @Test
    fun `o caso entra como aviso e a modalidade continua sendo a da Tabela 3`() {
        val aeroporto = r.atividade("E-01-09-0")!!
        val res = Enquadramento.simular(
            regras = r,
            atividade = aeroporto,
            porte = Grau.M,
            criteriosIncidentes = emptyList(),
            fatoresIncidentes = emptyList(),
            comEiaOuAudiencia = false
        )
        val daTabela = Enquadramento.modalidadeDe(r, res.classe, res.fatorLocacional)
        assertEquals(
            "o art. 18 não pode trocar a modalidade sozinho",
            daTabela.sigla, res.modalidade.sigla
        )
        assertNotNull("o caso do art. 18 precisa chegar ao resultado", res.casoArt18)
        assertTrue(
            "o resultado tem de avisar sobre o §2º",
            res.avisos.any { it.contains("art. 18, §2º") }
        )
        assertTrue(
            "o aviso tem de dizer qual seria a modalidade alternativa",
            res.avisos.any { it.contains("LAS/RAS") }
        )
    }

    /** Atividade sem caso especial não pode ganhar aviso do art. 18 do nada. */
    @Test
    fun `atividade comum nao carrega caso do art 18`() {
        val comum = r.atividades.first { it.codigo !in CODIGOS && it.limiteP != null }
        val res = Enquadramento.simular(
            regras = r,
            atividade = comum,
            porte = Grau.P,
            criteriosIncidentes = emptyList(),
            fatoresIncidentes = emptyList(),
            comEiaOuAudiencia = false
        )
        assertNull(res.casoArt18)
        assertTrue(res.avisos.none { it.contains("art. 18") })
    }
}
