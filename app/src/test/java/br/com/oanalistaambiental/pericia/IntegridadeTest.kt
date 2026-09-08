package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.captura.Integridade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegridadeTest {

    private fun h(s: String) = Integridade.sha256(s.toByteArray())

    @Test
    fun `sha256 confere com vetor conhecido`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Integridade.sha256(ByteArray(0))
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Integridade.sha256("abc".toByteArray())
        )
    }

    @Test
    fun `raiz de Merkle muda se qualquer foto mudar`() {
        val a = listOf(h("foto1"), h("foto2"), h("foto3"))
        val b = listOf(h("foto1"), h("foto2-alterada"), h("foto3"))
        assertNotEquals(Integridade.raizMerkle(a), Integridade.raizMerkle(b))
    }

    @Test
    fun `raiz de Merkle e estavel para a mesma sessao`() {
        val hashes = List(14) { h("foto$it") }
        assertEquals(Integridade.raizMerkle(hashes), Integridade.raizMerkle(hashes))
        assertEquals(64, Integridade.raizMerkle(hashes).length)
    }

    @Test
    fun `sessao com uma foto tem raiz igual ao proprio hash`() {
        val unico = h("unica")
        assertEquals(unico, Integridade.raizMerkle(listOf(unico)))
    }

    @Test
    fun `caminho de prova tem altura de arvore binaria`() {
        // 14 folhas -> 4 niveis de subida
        val hashes = List(14) { h("foto$it") }
        assertEquals(4, Integridade.caminhoMerkle(hashes, 0).size)
        assertTrue(Integridade.caminhoMerkle(hashes, 13).isNotEmpty())
    }

    @Test
    fun `sessao vazia nao produz raiz`() {
        assertEquals("", Integridade.raizMerkle(emptyList()))
    }

    /**
     * O teste que faltava, e que e a promessa central do produto: demonstrar UMA foto sem as
     * outras. Antes o caminho era gerado sem o lado do irmao e sem nenhuma funcao que o
     * conferisse — so se checava o TAMANHO da lista, entao um caminho impossivel de verificar
     * passava como se estivesse certo.
     */
    @Test
    fun `todo caminho de prova fecha na raiz da sessao`() {
        for (tamanho in listOf(1, 2, 3, 5, 8, 13, 14)) {
            val hashes = List(tamanho) { h("foto$it") }
            val raiz = Integridade.raizMerkle(hashes)
            for (i in hashes.indices) {
                assertTrue(
                    "folha $i de $tamanho nao fecha na raiz",
                    Integridade.verificarCaminho(hashes[i], Integridade.caminhoMerkle(hashes, i), raiz)
                )
            }
        }
    }

    /** Uma prova que fecha para qualquer foto nao prova nada. */
    @Test
    fun `caminho nao fecha para uma foto que nao esta na sessao`() {
        val hashes = List(9) { h("foto$it") }
        val raiz = Integridade.raizMerkle(hashes)
        val caminho = Integridade.caminhoMerkle(hashes, 4)
        assertFalse(Integridade.verificarCaminho(h("foto-de-fora"), caminho, raiz))
        assertFalse("caminho da folha errada não pode fechar",
            Integridade.verificarCaminho(hashes[5], caminho, raiz))
    }

    /**
     * O lado do irmao e o que torna a prova verificavel. Trocar os lados tem de quebrar —
     * se nao quebrasse, e porque a ordem da concatenacao nao estava sendo usada.
     */
    @Test
    fun `inverter o lado do irmao quebra a prova`() {
        val hashes = List(7) { h("foto$it") }
        val raiz = Integridade.raizMerkle(hashes)
        val invertido = Integridade.caminhoMerkle(hashes, 3)
            .map { Integridade.Passo(it.irmaoHex, !it.irmaoAEsquerda) }
        assertFalse(Integridade.verificarCaminho(hashes[3], invertido, raiz))
    }

    /**
     * Hash corrompido no banco tem de FALHAR, nao virar bytes plausiveis. `Character.digit`
     * devolve -1 em silencio para caractere invalido, e a conta antiga usava esse -1 direto.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `hash que nao e hexadecimal e recusado em vez de virar lixo`() {
        Integridade.raizMerkle(listOf(h("ok"), "zz" + h("ruim").drop(2)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hash truncado e recusado`() {
        Integridade.raizMerkle(listOf(h("ok"), h("ruim").dropLast(1)))
    }

    @Test
    fun `sem raiz carimbada nao ha o que verificar`() {
        val hashes = List(4) { h("foto$it") }
        assertFalse(Integridade.verificarCaminho(hashes[0], Integridade.caminhoMerkle(hashes, 0), ""))
    }
}
