package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.dados.CanaisDenunciaCarregador
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Lê o MESMO arquivo que vai dentro do aplicativo — telefone ou endereço quebrado no JSON para
 * antes de virar um número errado na mão de quem for ligar. */
class CanaisDenunciaTest {

    private val c by lazy {
        CanaisDenunciaCarregador.carregar { File("src/main/assets/ocorrencia/canais.json").inputStream() }
    }

    @Test
    fun `carrega os tres grupos de canais`() {
        assertEquals(3, c.grupos.size)
        assertTrue(c.grupos.any { it.id == "principal" })
        assertTrue(c.grupos.any { it.id == "urgente" })
        assertTrue(c.grupos.any { it.id == "ouvidoria" })
    }

    @Test
    fun `as dez URAs estao presentes, cada uma com endereco`() {
        assertEquals(10, c.uras.regionais.size)
        c.uras.regionais.forEach { r ->
            assertTrue("${r.nome} sem endereço", r.endereco.isNotBlank())
        }
    }

    @Test
    fun `canal principal cita o LigMinas 155`() {
        val principal = c.grupos.first { it.id == "principal" }
        assertTrue(principal.itens.any { it.telefone == "155" })
    }

    @Test
    fun `canal urgente cita o 190`() {
        val urgente = c.grupos.first { it.id == "urgente" }
        assertTrue(urgente.itens.any { it.telefone == "190" })
    }

    @Test
    fun `todo grupo tem titulo e descricao`() {
        c.grupos.forEach { g ->
            assertTrue("${g.id} sem título", g.titulo.isNotBlank())
            assertTrue("${g.id} sem descrição", g.descricao.isNotBlank())
        }
    }
}
