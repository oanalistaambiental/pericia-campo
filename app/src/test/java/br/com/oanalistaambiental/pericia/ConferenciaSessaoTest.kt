package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.captura.ConferenciaSessao
import br.com.oanalistaambiental.pericia.captura.Integridade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * As duas perguntas que um laudo precisa responder SEPARADAMENTE:
 * o arquivo mudou? e este registro pertence ao conjunto que foi selado?
 *
 * Os testes abaixo existem porque juntar as duas numa palavra só é como um laudo se desfaz.
 */
class ConferenciaSessaoTest {

    private val disco = mutableMapOf<String, ByteArray>()

    private fun abrir(caminho: String): File? = disco[caminho]?.let { conteudo ->
        val f = File.createTempFile("conf", ".bin")
        f.deleteOnExit()
        f.writeBytes(conteudo)
        f
    }

    private fun gravar(nome: String, conteudo: String): ConferenciaSessao.Folha {
        val bytes = conteudo.toByteArray()
        disco[nome] = bytes
        return ConferenciaSessao.Folha(nome, Integridade.sha256(bytes), nome)
    }

    private fun selar(folhas: List<ConferenciaSessao.Folha>) =
        Integridade.raizMerkle(folhas.map { it.hashGravado })

    @Test
    fun `sessao intacta confere nos dois lados`() {
        val folhas = (1..5).map { gravar("foto$it.jpg", "conteudo $it") }
        val r = ConferenciaSessao.conferir(selar(folhas), folhas, ::abrir)

        assertTrue(r.arvoreConfere)
        assertEquals(5, r.arquivosIntegros)
        assertTrue("toda folha precisa fechar na raiz", r.itens.all { it.pertenceAArvore })
        assertTrue(r.resumo().contains("fecham na raiz selada"))
    }

    /**
     * O caso mais comum em campo: a foto passou por aplicativo de mensagem e foi recomprimida.
     * O ARQUIVO mudou, mas a árvore continua íntegra — ela é sobre os hashes gravados, não
     * sobre os arquivos de hoje. O resumo tem de dizer as duas coisas, sem esconder nenhuma.
     */
    @Test
    fun `arquivo recomprimido nao derruba a arvore, e o resumo diz as duas coisas`() {
        val folhas = (1..4).map { gravar("foto$it.jpg", "conteudo $it") }
        val raiz = selar(folhas)
        disco["foto2.jpg"] = "recomprimido pelo mensageiro".toByteArray()

        val r = ConferenciaSessao.conferir(raiz, folhas, ::abrir)
        assertTrue("a árvore não depende do arquivo de hoje", r.arvoreConfere)
        assertEquals(3, r.arquivosIntegros)
        assertEquals(
            ConferenciaSessao.EstadoArquivo.ALTERADO,
            r.itens[1].estadoArquivo
        )
        assertTrue("a folha alterada continua pertencendo à árvore", r.itens[1].pertenceAArvore)
        assertTrue(r.resumo().contains("árvore fecha"))
        assertTrue(r.resumo().contains("não conferem"))
    }

    @Test
    fun `arquivo apagado aparece como ausente`() {
        val folhas = (1..3).map { gravar("foto$it.jpg", "conteudo $it") }
        val raiz = selar(folhas)
        disco.remove("foto3.jpg")

        val r = ConferenciaSessao.conferir(raiz, folhas, ::abrir)
        assertEquals(ConferenciaSessao.EstadoArquivo.AUSENTE, r.itens[2].estadoArquivo)
        assertEquals(2, r.arquivosIntegros)
    }

    /**
     * O caso grave, e o que a conferência antiga (arquivo × hash) não pegava de jeito nenhum:
     * uma foto acrescentada ao banco DEPOIS do fechamento. Todos os arquivos batem com seus
     * hashes — e mesmo assim o conjunto não é o que foi selado.
     */
    @Test
    fun `foto acrescentada depois do fechamento quebra a raiz mesmo com todos os arquivos ok`() {
        val originais = (1..4).map { gravar("foto$it.jpg", "conteudo $it") }
        val raiz = selar(originais)
        val intrusa = gravar("foto5.jpg", "inserida depois")

        val r = ConferenciaSessao.conferir(raiz, originais + intrusa, ::abrir)
        assertEquals("todos os arquivos batem com seu hash", 5, r.arquivosIntegros)
        assertFalse("mas o conjunto não é o selado", r.arvoreConfere)
        assertTrue(r.itens.none { it.pertenceAArvore })
        assertTrue(r.resumo().contains("NÃO bate"))
        assertTrue("o resumo precisa dizer que a conferência por arquivo não supre isso",
            r.resumo().contains("não supre"))
    }

    /** Reordenar também muda a raiz — por isso o banco lê as fotos com ordem estável. */
    @Test
    fun `reordenar as fotos quebra a raiz`() {
        val folhas = (1..6).map { gravar("foto$it.jpg", "conteudo $it") }
        val raiz = selar(folhas)
        val trocadas = folhas.toMutableList().also { it[0] = folhas[1]; it[1] = folhas[0] }

        assertFalse(ConferenciaSessao.conferir(raiz, trocadas, ::abrir).arvoreConfere)
    }

    /**
     * Sessão aberta não tem raiz. O resumo tem de dizer isso PRIMEIRO — ausência de selo não
     * pode ser lida como selo em ordem, que é o mesmo princípio de "ausência de alerta nunca
     * equivale a consulta sem problema".
     */
    @Test
    fun `sessao aberta diz que nao ha o que conferir na arvore`() {
        val folhas = (1..3).map { gravar("foto$it.jpg", "conteudo $it") }
        val r = ConferenciaSessao.conferir(null, folhas, ::abrir)

        assertFalse(r.selada)
        assertFalse(r.arvoreConfere)
        assertTrue(r.resumo().startsWith("Sessão ainda ABERTA"))
        assertEquals("os arquivos ainda podem ser conferidos", 3, r.arquivosIntegros)
        assertTrue("sem raiz, nenhuma folha pode se dizer provada", r.itens.none { it.pertenceAArvore })
    }

    @Test
    fun `raiz em branco conta como sessao aberta`() {
        val folhas = listOf(gravar("a.jpg", "a"))
        assertFalse(ConferenciaSessao.conferir("", folhas, ::abrir).selada)
    }

    /** Hash corrompido no banco não pode derrubar a conferência inteira com uma exceção. */
    @Test
    fun `hash corrompido no banco nao explode a conferencia`() {
        val boa = gravar("boa.jpg", "conteudo")
        disco["ruim.jpg"] = "conteudo ruim".toByteArray()
        val ruim = ConferenciaSessao.Folha("ruim.jpg", "nao-e-hexadecimal", "ruim.jpg")

        val r = ConferenciaSessao.conferir("qualquer-raiz", listOf(boa, ruim), ::abrir)
        assertFalse(r.arvoreConfere)
        assertEquals("", r.raizRecalculada)
        assertEquals(2, r.itens.size)
    }

    @Test
    fun `a ressalva de tempo acompanha o resultado`() {
        val folhas = listOf(gravar("a.jpg", "a"))
        val t = ConferenciaSessao.conferir(selar(folhas), folhas, ::abrir).RESSALVA_TEMPO
        assertTrue(t.contains("RFC 3161"))
        assertTrue("precisa dizer o que o hash NÃO prova", t.contains("não prova QUANDO"))
    }
}
