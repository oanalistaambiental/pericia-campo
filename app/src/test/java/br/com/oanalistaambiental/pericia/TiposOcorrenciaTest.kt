package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.dados.TiposOcorrencia
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

/**
 * O formulário rápido de ocorrência nunca pode abrir sem opção nenhuma — por isso `carregar`
 * cai no padrão embutido sempre que o asset falta, vem vazio ou vem corrompido.
 */
class TiposOcorrenciaTest {

    @Test
    fun `asset ausente cai no padrao embutido`() {
        val r = TiposOcorrencia.carregar { throw FileNotFoundException("tipos_ocorrencia.json") }
        assertEquals(TiposOcorrencia.padrao, r)
    }

    @Test
    fun `json invalido cai no padrao embutido`() {
        val r = TiposOcorrencia.carregar { ByteArrayInputStream("{ nao e uma lista".toByteArray()) }
        assertEquals(TiposOcorrencia.padrao, r)
    }

    @Test
    fun `lista vazia cai no padrao embutido`() {
        val r = TiposOcorrencia.carregar { ByteArrayInputStream("[]".toByteArray()) }
        assertEquals(TiposOcorrencia.padrao, r)
    }

    @Test
    fun `json valido substitui o padrao`() {
        val r = TiposOcorrencia.carregar {
            ByteArrayInputStream("""["Categoria A", "Categoria B"]""".toByteArray())
        }
        assertEquals(listOf("Categoria A", "Categoria B"), r)
    }
}
