package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.dados.CadastrosIefCarregador
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Lê o MESMO arquivo que vai dentro do aplicativo — se a lista de categorias ou a base legal
 * for editada e quebrar uma célula, o build para aqui, não na mão de quem for se cadastrar.
 */
class CadastrosIefTest {

    private val c by lazy {
        CadastrosIefCarregador.carregar { File("src/main/assets/ief/cadastros.json").inputStream() }
    }

    @Test
    fun `carrega os dois grupos, flora e fauna aquatica`() {
        assertEquals(2, c.grupos.size)
        assertTrue(c.grupos.any { it.id == "flora" })
        assertTrue(c.grupos.any { it.id == "fauna_aquatica" })
    }

    @Test
    fun `todo grupo cita base legal e pelo menos uma categoria`() {
        c.grupos.forEach { g ->
            assertTrue("${g.id} sem base legal", g.baseLegal.isNotBlank())
            assertTrue("${g.id} sem categoria", g.categorias.isNotEmpty())
            assertTrue("${g.id} sem regra de renovação", g.renovacao.isNotBlank())
            assertTrue("${g.id} sem nota de taxa", g.taxaNota.isNotBlank())
        }
    }

    /**
     * A fauna aquática tem 16 categorias distintas (aquicultura, comércio, associações etc.) —
     * é o grupo mais numeroso e o mais fácil de perder item numa edição futura do JSON.
     */
    @Test
    fun `fauna aquatica lista as dezesseis categorias`() {
        val fauna = c.grupos.first { it.id == "fauna_aquatica" }
        assertEquals(16, fauna.categorias.size)
        assertTrue(fauna.categorias.any { it.nome.contains("Colônia de pescador") })
        assertNotNull("isenção de bar/restaurante precisa estar registrada", fauna.isencao)
    }

    /**
     * Único valor de taxa que a pesquisa confirmou em texto oficial — não pode virar um chute
     * silencioso numa edição futura. O cadastro inicial continua sem valor confirmado.
     */
    @Test
    fun `so a flora tem taxa de alteracao confirmada, em UFEMG`() {
        val flora = c.grupos.first { it.id == "flora" }
        assertEquals(15.0, flora.taxaAlteracaoUfemg!!, 0.001)

        val fauna = c.grupos.first { it.id == "fauna_aquatica" }
        assertEquals(null, fauna.taxaAlteracaoUfemg)
    }
}
