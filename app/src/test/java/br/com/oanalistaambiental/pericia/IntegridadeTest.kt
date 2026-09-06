package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.captura.Integridade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
}
