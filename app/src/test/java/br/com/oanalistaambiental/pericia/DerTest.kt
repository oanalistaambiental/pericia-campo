package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.carimbo.Der
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/**
 * O DER escrito a mão. Testado byte a byte contra valores conhecidos, porque aqui um erro não
 * aparece como exceção: aparece como uma Autoridade de Carimbo do Tempo recusando o pedido sem
 * explicar por quê, do outro lado da rede, em campo.
 */
class DerTest {

    @Test
    fun `comprimento usa forma curta ate 127 e longa acima`() {
        assertEquals("00", Der.hex(Der.comprimento(0)))
        assertEquals("7f", Der.hex(Der.comprimento(127)))
        assertEquals("8180", Der.hex(Der.comprimento(128)))
        assertEquals("820101", Der.hex(Der.comprimento(257)))
    }

    /** Valores canônicos, dos exemplos do próprio X.690 e dos OIDs que usamos. */
    @Test
    fun `oids conhecidos batem byte a byte`() {
        assertEquals("06062a864886f70d", Der.hex(Der.oid("1.2.840.113549")))
        // id-sha256
        assertEquals("0609608648016503040201", Der.hex(Der.oid("2.16.840.1.101.3.4.2.1")))
        // id-ct-TSTInfo
        assertEquals("060b2a864886f70d0109100104", Der.hex(Der.oid("1.2.840.113549.1.9.16.1.4")))
    }

    @Test
    fun `oid vai e volta`() {
        for (o in listOf("1.2.840.113549.1.7.2", "2.16.840.1.101.3.4.2.1", "1.3.6.1.4.1.311.2.1.4")) {
            assertEquals(o, Der.oidDe(Der.ler(Der.oid(o))))
        }
    }

    /**
     * INTEGER positivo com o bit alto ligado precisa do zero à esquerda. Sem isso um nonce
     * vira negativo do outro lado — e algumas Autoridades simplesmente recusam o pedido.
     */
    @Test
    fun `inteiro positivo com bit alto ganha zero a esquerda`() {
        assertEquals("020200ff", Der.hex(Der.inteiro(255L)))
        assertEquals("02017f", Der.hex(Der.inteiro(127L)))
        val grande = BigInteger("ffffffffffffffff", 16)
        assertEquals(grande, Der.inteiroDe(Der.ler(Der.inteiro(grande))))
    }

    @Test
    fun `sequencia aninhada volta com os mesmos filhos`() {
        val s = Der.sequencia(Der.inteiro(1L), Der.octeto(byteArrayOf(1, 2, 3)), Der.nulo())
        val filhos = Der.filhos(Der.ler(s).conteudo)
        assertEquals(3, filhos.size)
        assertEquals(BigInteger.ONE, Der.inteiroDe(filhos[0]))
        assertEquals("010203", Der.hex(filhos[1].conteudo))
        assertEquals(Der.NULO, filhos[2].tag)
    }

    @Test
    fun `conteudo grande usa forma longa e volta inteiro`() {
        val grande = ByteArray(1000) { (it % 251).toByte() }
        val e = Der.ler(Der.octeto(grande))
        assertTrue(grande.contentEquals(e.conteudo))
    }

    // ---- o que precisa falhar em vez de estourar índice ----

    @Test(expected = Der.Malformado::class)
    fun `elemento truncado e recusado`() {
        Der.ler(byteArrayOf(0x30, 0x05, 0x02, 0x01))
    }

    @Test(expected = Der.Malformado::class)
    fun `comprimento indefinido do BER nao e DER`() {
        Der.ler(byteArrayOf(0x30, 0x80.toByte(), 0x00, 0x00))
    }

    @Test(expected = Der.Malformado::class)
    fun `hexadecimal impar e recusado`() {
        Der.deHex("abc")
    }

    @Test(expected = Der.Malformado::class)
    fun `caractere nao hexadecimal e recusado`() {
        Der.deHex("zz00")
    }

    @Test(expected = Der.Malformado::class)
    fun `oid com um arco so e recusado`() {
        Der.oid("1")
    }

    @Test
    fun `hex e deHex sao inversos`() {
        val b = ByteArray(64) { (it * 7).toByte() }
        assertTrue(b.contentEquals(Der.deHex(Der.hex(b))))
    }
}
