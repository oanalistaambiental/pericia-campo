package br.com.oanalistaambiental.pericia

import br.com.oanalistaambiental.pericia.carimbo.CarimboTempo
import br.com.oanalistaambiental.pericia.carimbo.Der
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.SecureRandom

/**
 * O carimbo do tempo, do pedido a conferencia.
 *
 * Não há TSA nesta bancada, então a resposta é construída aqui — o que é bom: permite
 * construir também as respostas MALICIOSAS, que são o que realmente importa testar. Uma
 * Autoridade honesta é o caso fácil; o caso que arruína um laudo é o token bem formado que
 * carimba outro hash.
 */
class CarimboTempoTest {

    private val OID_SHA256 = "2.16.840.1.101.3.4.2.1"
    private val OID_TST_INFO = "1.2.840.113549.1.9.16.1.4"
    private val OID_SIGNED_DATA = "1.2.840.113549.1.7.2"

    private val raiz = "a".repeat(64)

    private fun contexto(n: Int, conteudo: ByteArray) = Der.tlv(0xA0 or n, conteudo)

    /** Constrói uma TimeStampResp com a estrutura que o RFC 3161 manda. */
    private fun resposta(
        imprintHex: String = raiz,
        nonce: BigInteger,
        status: Int = 0,
        genTime: String = "20260908012345Z",
        comNonce: Boolean = true,
        comToken: Boolean = true
    ): ByteArray {
        val tstInfo = Der.sequencia(
            Der.inteiro(1L),
            Der.oid("1.2.3.4.1"),
            Der.sequencia(
                Der.sequencia(Der.oid(OID_SHA256), Der.nulo()),
                Der.octeto(Der.deHex(imprintHex))
            ),
            Der.inteiro(42L),
            Der.tlv(Der.GENERALIZED_TIME, genTime.toByteArray(Charsets.US_ASCII))
        ).let { base ->
            if (!comNonce) base
            // reabre a SEQUENCE para acrescentar o nonce depois do genTime
            else Der.tlv(Der.SEQUENCE, Der.ler(base).conteudo + Der.inteiro(nonce))
        }

        val signedData = Der.sequencia(
            Der.inteiro(3L),
            Der.tlv(Der.SET, Der.sequencia(Der.oid(OID_SHA256), Der.nulo())),
            Der.sequencia(Der.oid(OID_TST_INFO), contexto(0, Der.octeto(tstInfo)))
        )
        val token = Der.sequencia(Der.oid(OID_SIGNED_DATA), contexto(0, signedData))
        val statusInfo = Der.sequencia(Der.inteiro(status.toLong()))
        return if (comToken) Der.sequencia(statusInfo, token) else Der.sequencia(statusInfo)
    }

    private fun pedido() = CarimboTempo.pedido(raiz, SecureRandom())

    // ------------------------------------------------------------------ o caso honesto

    @Test
    fun `pedido tem a estrutura da TimeStampReq e carrega o nonce`() {
        val p = pedido()
        val campos = Der.filhos(Der.ler(p.bytes).conteudo)
        assertEquals("version deve ser v1", BigInteger.ONE, Der.inteiroDe(campos[0]))
        assertEquals(Der.SEQUENCE, campos[1].tag)
        assertEquals("o nonce vai no pedido", p.nonce, Der.inteiroDe(campos[2]))
        // o hash pedido é a raiz, e o algoritmo é SHA-256
        val imprint = Der.filhos(campos[1].conteudo)
        assertEquals(OID_SHA256, Der.oidDe(Der.filhos(imprint[0].conteudo)[0]))
        assertEquals(raiz, Der.hex(imprint[1].conteudo))
    }

    @Test
    fun `resposta honesta e aceita e traz o instante declarado`() {
        val p = pedido()
        val c = CarimboTempo.conferirResposta(resposta(nonce = p.nonce), p, "TSA de teste", false)
        assertEquals("20260908012345Z", c.genTimeBruto)
        assertTrue("o token vai inteiro, em base64", c.tokenBase64.length > 40)
        assertEquals("TSA de teste", c.autoridade)
        // 08/09/2026 01:23:45 UTC, em milissegundos. Valor fixo de propósito: a primeira
        // versão deste teste comparava a expressão consigo mesma e passava sempre.
        assertEquals(1_788_830_625_000L, c.instante)
    }

    /** status 1 é "granted with mods": também é concessão, e o token vale. */
    @Test
    fun `status de concessao com modificacoes tambem e aceito`() {
        val p = pedido()
        assertNotNull(
            CarimboTempo.conferirResposta(resposta(nonce = p.nonce, status = 1), p, "T", false)
        )
    }

    // ------------------------------------------------------------------ o que precisa falhar

    /**
     * O CASO QUE ARRUÍNA UM LAUDO: token perfeitamente bem formado, assinado, válido — e que
     * carimba OUTRO hash. Guardar isso daria à vistoria um selo que não se refere a ela.
     */
    @Test
    fun `token que carimba outro hash e recusado`() {
        val p = pedido()
        val e = runCatching {
            CarimboTempo.conferirResposta(resposta(imprintHex = "b".repeat(64), nonce = p.nonce), p, "T", false)
        }.exceptionOrNull()
        assertTrue(e is CarimboTempo.Recusado)
        assertTrue(e!!.message!!.contains("outro hash"))
    }

    /** Resposta antiga, gravada por alguém e reapresentada, tem nonce diferente. */
    @Test(expected = CarimboTempo.Recusado::class)
    fun `nonce diferente e recusado`() {
        CarimboTempo.conferirResposta(resposta(nonce = BigInteger.valueOf(999)), pedido(), "T", false)
    }

    /** Sem nonce devolvido não há proteção contra reapresentação — e isso não passa calado. */
    @Test(expected = CarimboTempo.Recusado::class)
    fun `resposta sem nonce e recusada`() {
        val p = pedido()
        CarimboTempo.conferirResposta(resposta(nonce = p.nonce, comNonce = false), p, "T", false)
    }

    @Test
    fun `recusa da autoridade vira mensagem legivel`() {
        val p = pedido()
        val e = runCatching {
            CarimboTempo.conferirResposta(resposta(nonce = p.nonce, status = 2, comToken = false), p, "T", false)
        }.exceptionOrNull()
        assertTrue(e is CarimboTempo.Recusado)
        assertTrue(e!!.message!!.contains("rejeitado"))
    }

    @Test(expected = CarimboTempo.Recusado::class)
    fun `resposta truncada nao explode com indice`() {
        val p = pedido()
        val r = resposta(nonce = p.nonce)
        CarimboTempo.conferirResposta(r.copyOfRange(0, r.size / 2), p, "T", false)
    }

    @Test(expected = CarimboTempo.Recusado::class)
    fun `lixo no lugar da resposta e recusado`() {
        CarimboTempo.conferirResposta("não é DER".toByteArray(), pedido(), "T", false)
    }

    @Test(expected = CarimboTempo.Recusado::class)
    fun `genTime invalido e recusado`() {
        val p = pedido()
        CarimboTempo.conferirResposta(resposta(nonce = p.nonce, genTime = "20261301999999Z"), p, "T", false)
    }

    @Test(expected = CarimboTempo.Recusado::class)
    fun `raiz que nao e sha256 nao vira pedido`() {
        CarimboTempo.pedido("abcd")
    }

    /**
     * O base64 é escrito à mão (o do java.util não existe em toda API mínima suportada). Um
     * erro de padding aqui produz um token que nenhuma ferramenta externa consegue abrir — e
     * o token é justamente o que permite a validação por terceiros.
     */
    @Test
    fun `o base64 do token bate com valores conhecidos`() {
        // Cada resto de divisão por 3 exercita um caso de padding diferente.
        assertEquals("YQ==", CarimboTempo.base64("a".toByteArray()))
        assertEquals("YWI=", CarimboTempo.base64("ab".toByteArray()))
        assertEquals("YWJj", CarimboTempo.base64("abc".toByteArray()))
        assertEquals("", CarimboTempo.base64(ByteArray(0)))
        assertEquals(
            "AAECAwQFBgcICQoLDA0ODxAREhM=",
            CarimboTempo.base64(ByteArray(20) { it.toByte() })
        )
    }

    /**
     * A ressalva sobre o que o aplicativo NÃO valida precisa existir e ser explícita — é ela
     * que impede o laudo de dar a impressão de uma verificação que não foi feita.
     */
    @Test
    fun `a ressalva diz o que nao e validado`() {
        val t = CarimboTempo.RESSALVA_VALIDACAO
        assertTrue(t.contains("NÃO valida"))
        assertTrue(t.contains("cadeia de certificados"))
        assertTrue("precisa indicar como validar de verdade", t.contains("openssl"))
    }

    @Test
    fun `o credenciamento na ICP-Brasil e declarado, nao adivinhado`() {
        val p = pedido()
        val c = CarimboTempo.conferirResposta(resposta(nonce = p.nonce), p, "ACT X", true)
        assertTrue(c.credenciadaIcpBrasil)
        val d = CarimboTempo.conferirResposta(resposta(nonce = p.nonce), p, "ACT X", false)
        assertTrue(!d.credenciadaIcpBrasil)
    }
}
