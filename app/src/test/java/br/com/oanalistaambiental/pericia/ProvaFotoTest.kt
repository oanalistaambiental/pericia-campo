package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.captura.Integridade
import br.com.oanalistaambiental.pericia.captura.ProvaFoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A prova de uma fotografia isolada.
 *
 * O teste central não é de formatação: é que o documento emitido contenha TUDO o que a outra
 * parte precisa para refazer a conta sozinha — e que essa conta feche. Um documento bonito que
 * não permite refazer a verificação não prova nada.
 */
class ProvaFotoTest {

    private fun h(s: String) = Integridade.sha256(s.toByteArray())

    private fun documento(
        qtd: Int,
        indice: Int,
        raiz: String? = null,
        hashAtual: String? = null,
        comCarimbo: Boolean = false
    ): String {
        val hashes = List(qtd) { h("foto$it") }
        return ProvaFoto.gerar(
            tituloSessao = "Vistoria de teste",
            processo = "AI-123/2026",
            fechadaEm = 1_700_000_000_000L,
            raizSelada = raiz ?: Integridade.raizMerkle(hashes),
            comCarimbo = comCarimbo,
            indice = indice + 1,
            total = qtd,
            nomeArquivo = "IMG_$indice.jpg",
            hashGravado = hashes[indice],
            instanteCaptura = 1_699_999_000_000L,
            caminho = Integridade.caminhoMerkle(hashes, indice),
            hashAtual = hashAtual ?: hashes[indice],
            emitidoEm = 1_700_000_100_000L
        )
    }

    /**
     * O que importa: extrair do TEXTO os dados da prova e conferir que fecham. Se este teste
     * passa, quem receber o documento consegue verificar sem o aplicativo.
     */
    @Test
    fun `os dados impressos no documento fecham na raiz`() {
        for (qtd in listOf(1, 2, 3, 7, 12)) {
            for (i in 0 until qtd) {
                val doc = documento(qtd, i)

                val folha = Regex("\\(folha\\):\\s*\\n\\s*([0-9a-f]{64})").find(doc)!!.groupValues[1]
                val raiz = Regex("selada da vistoria:\\s*\\n\\s*([0-9a-f]{64})").find(doc)!!.groupValues[1]
                val passos = Regex("irmão à (ESQUERDA|DIREITA)\\s*\\n\\s*([0-9a-f]{64})")
                    .findAll(doc)
                    .map { Integridade.Passo(it.groupValues[2], it.groupValues[1] == "ESQUERDA") }
                    .toList()

                assertTrue(
                    "documento de $qtd fotos, registro $i, não fecha na raiz",
                    Integridade.verificarCaminho(folha, passos, raiz)
                )
            }
        }
    }

    /** O script embutido tem de ter um passo por nível — senão não reproduz a conta. */
    @Test
    fun `o script em Python tem um passo por nivel da arvore`() {
        val doc = documento(12, 5)
        val passosNoTexto = Regex("irmão à (ESQUERDA|DIREITA)").findAll(doc).count()
        val linhasSha = Regex("atual = hashlib\\.sha256").findAll(doc).count()
        assertEquals(passosNoTexto, linhasSha)
        assertTrue(doc.contains("# deve imprimir:"))
        assertTrue("a ordem da concatenação precisa aparecer no script",
            doc.contains("+ atual") || doc.contains("atual +"))
    }

    /**
     * Sessão aberta não gera documento. Um "comprovante" sem raiz selada pareceria prova e não
     * seria — e papel com cara de prova é pior que papel nenhum.
     */
    @Test(expected = ProvaFoto.SessaoNaoSelada::class)
    fun `sem raiz selada nao se emite documento`() {
        documento(4, 1, raiz = "")
    }

    /** Se o caminho não fecha, não se emite nada — nem com ressalva. */
    @Test(expected = ProvaFoto.SessaoNaoSelada::class)
    fun `caminho que nao fecha na raiz nao vira documento`() {
        documento(4, 1, raiz = h("raiz de outra vistoria"))
    }

    /**
     * Arquivo alterado NÃO impede a prova de pertencimento: são perguntas diferentes. Mas o
     * documento tem de dizer isso com todas as letras, e não deixar o leitor supor.
     */
    @Test
    fun `arquivo alterado gera documento com a ressalva explicita`() {
        val doc = documento(6, 2, hashAtual = h("arquivo recomprimido"))
        assertTrue(doc.contains("NÃO confere com o hash gravado"))
        assertTrue(doc.contains("continua válida para o hash ORIGINAL"))
        assertTrue("precisa nomear a causa mais comum", doc.contains("aplicativo"))
    }

    @Test
    fun `arquivo integro é dito integro`() {
        assertTrue(documento(6, 2).contains("O arquivo confere"))
    }

    @Test
    fun `arquivo ilegivel nao vira acusacao`() {
        val doc = ProvaFoto.gerar(
            "V", null, null, Integridade.raizMerkle(listOf(h("a"))), false,
            1, 1, "a.jpg", h("a"), 0L, emptyList(), hashAtual = null
        )
        assertTrue(doc.contains("Não foi possível ler o arquivo"))
        assertTrue(doc.contains("(nenhum — a vistoria tem um único registro"))
    }

    /** A ressalva de tempo e o estado do carimbo não podem faltar em nenhum documento. */
    @Test
    fun `toda prova carrega a ressalva de tempo e o estado do carimbo`() {
        val semCarimbo = documento(5, 0, comCarimbo = false)
        assertTrue(semCarimbo.contains("Não demonstra QUANDO"))
        assertTrue(semCarimbo.contains("AINDA NÃO foi aplicado"))
        assertTrue(documento(5, 0, comCarimbo = true).contains("FOI aplicado"))
    }

    /**
     * Quanto o documento revela das OUTRAS fotografias da vistoria.
     *
     * A primeira redação do documento afirmava que as demais não eram reveladas — e este teste
     * mostrou que era exagero: o primeiro passo do caminho é, por construção, o hash da folha
     * vizinha. É pouco (um SHA-256 não devolve imagem nem coordenada) mas não é nada, e o
     * texto foi corrigido para dizer exatamente isso.
     *
     * O teste trava o limite: no máximo a própria folha e UMA vizinha, nunca mais.
     */
    @Test
    fun `o documento revela no maximo o hash de uma fotografia vizinha`() {
        for (qtd in listOf(2, 7, 12, 20)) {
            val hashes = List(qtd) { h("foto$it") }
            for (i in 0 until qtd) {
                val doc = documento(qtd, i)
                val presentes = hashes.count { doc.contains(it) }
                assertTrue(
                    "com $qtd fotos, o registro $i expôs $presentes folhas",
                    presentes <= 2
                )
                assertTrue("a própria folha precisa estar lá", doc.contains(hashes[i]))
            }
        }
    }

    /** E o documento precisa DIZER isso, em vez de deixar o leitor supor. */
    @Test
    fun `o documento explica o que os hashes do caminho revelam`() {
        val doc = documento(8, 3)
        assertTrue(doc.contains("fotografia vizinha"))
        assertTrue(doc.contains("NÃO se extrai imagem"))
    }

    /**
     * O passo em que o irmão é o próprio valor acontece de verdade (nível com número ímpar de
     * elementos) e, sem explicação, é a primeira coisa que alguém aponta ao contestar o
     * documento: "por que o irmão desta foto é ela mesma?".
     */
    @Test
    fun `o passo duplicado vem explicado`() {
        // 11 folhas: o último registro cai na duplicação já no primeiro nível.
        val doc = documento(11, 10)
        assertTrue(doc.contains("número ímpar de elementos"))

        // E não aparece onde não deve.
        assertTrue("par e sem duplicação não deve trazer a nota",
            !documento(8, 3).contains("número ímpar de elementos"))
    }

    @Test
    fun `o nome sugerido nao carrega caractere problematico`() {
        assertEquals("prova-IMG-0007.txt", ProvaFoto.nomeSugerido("IMG 0007.jpg"))
        assertEquals("prova-foto-1.txt", ProvaFoto.nomeSugerido("foto(1).png"))
        assertEquals("prova-foto.txt", ProvaFoto.nomeSugerido("!!!.jpg"))
    }
}
