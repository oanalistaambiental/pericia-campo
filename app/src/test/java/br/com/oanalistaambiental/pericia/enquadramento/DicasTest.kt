package br.com.oanalistaambiental.pericia.enquadramento

import br.com.oanalistaambiental.pericia.enquadramento.norma.Dicas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

/**
 * Nenhum arquivo real de dica existe ainda em `assets/norma/dicas/` — a leitura de pareceres é
 * trabalho futuro (skill `dn217-ler-pareceres`). Estes testes cobrem só o mecanismo: a ausência
 * do arquivo devolve null sem lançar, e um arquivo presente é lido campo a campo.
 */
class DicasTest {

    @Test
    fun `atividade sem arquivo de dica devolve null, nao lanca`() {
        val d = Dicas.carregar("NAO-EXISTE") { throw FileNotFoundException(it) }
        assertNull(d)
    }

    @Test
    fun `dica existente traz texto, quantidade de pareceres e data`() {
        val json = """{"texto":"Dica de teste.","pareceres_consultados":3,"data_extracao":"2026-09-09"}"""
        val d = Dicas.carregar("X-01-01-1") { ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)) }
        assertEquals("Dica de teste.", d?.texto)
        assertEquals(3, d?.pareceresConsultados)
        assertEquals("2026-09-09", d?.dataExtracao)
    }
}
