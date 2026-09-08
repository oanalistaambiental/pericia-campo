package br.com.oanalistaambiental.pericia.carimbo

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Carimbo do tempo RFC 3161 sobre a raiz de Merkle da vistoria.
 *
 * O QUE ISTO RESOLVE, e o que nao resolve. O SHA-256 prova que nada mudou desde o calculo; nao
 * prova QUANDO o calculo foi feito, porque o relogio do aparelho e ajustavel pelo proprio
 * usuario. O carimbo de uma Autoridade de Carimbo do Tempo assina "este hash existia em tal
 * instante", com o relogio dela e a chave dela. Uma requisicao por vistoria, sobre a raiz.
 *
 * TRES CUIDADOS QUE DEFINEM O DESENHO:
 *
 * 1. O carimbo recebido e CONFERIDO antes de ser guardado. Uma TSA (ou algo no meio do
 *    caminho) pode devolver um token bem formado que carimba OUTRO hash; guardar isso daria ao
 *    laudo um selo que nao se refere a esta vistoria. Aqui o token so e aceito se o
 *    messageImprint dele for exatamente a nossa raiz e o nonce for o que enviamos.
 *
 * 2. Este modulo NAO valida a cadeia de certificados da TSA nem verifica a assinatura
 *    criptografica do token. Fazer isso a mao seria reimplementar CMS e X.509, e um verificador
 *    pela metade e pior que nenhum — daria uma confianca que nao existe. O aplicativo guarda o
 *    token inteiro, em base64, para que a validacao completa seja feita por quem tem ferramenta
 *    propria (openssl ts -verify, por exemplo), e DIZ isso no laudo. Ver [RESSALVA_VALIDACAO].
 *
 * 3. Credenciamento na ICP-Brasil e questao juridica, nao tecnica: nao ha nada no protocolo que
 *    permita ao aplicativo descobrir sozinho se a TSA e credenciada. Por isso o endereco e
 *    configuravel e vem acompanhado de uma marcacao explicita, feita por quem configura. O
 *    laudo repete essa marcacao com todas as letras em vez de deixar a impressao de que todo
 *    carimbo tem fe publica.
 *
 * Kotlin puro: sem Android e sem rede aqui dentro. A chamada HTTP fica na camada que pode
 * fazer I/O, e este modulo so monta e confere bytes — o que torna tudo testavel.
 */
object CarimboTempo {

    /** id-sha256, do RFC 8017. */
    private const val OID_SHA256 = "2.16.840.1.101.3.4.2.1"
    /** id-ct-TSTInfo, do RFC 3161. */
    private const val OID_TST_INFO = "1.2.840.113549.1.9.16.1.4"
    /** id-signedData, do RFC 5652 — o envelope do token. */
    private const val OID_SIGNED_DATA = "1.2.840.113549.1.7.2"

    const val TIPO_CONTEUDO = "application/timestamp-query"
    const val TIPO_RESPOSTA = "application/timestamp-reply"

    const val RESSALVA_VALIDACAO =
        "Este aplicativo confere que o carimbo se refere exatamente à raiz desta vistoria e ao " +
            "número aleatório enviado no pedido, e guarda o token inteiro. Ele NÃO valida a " +
            "assinatura criptográfica nem a cadeia de certificados da Autoridade — essa " +
            "validação deve ser feita com ferramenta própria (por exemplo, openssl ts -verify), " +
            "a partir do token anexado."

    class Recusado(mensagem: String) : Exception(mensagem)

    /** Um pedido pronto para ir na rede, com o nonce que a resposta tem de devolver. */
    class Pedido(val bytes: ByteArray, val nonce: BigInteger, val imprint: ByteArray)

    /**
     * Monta a TimeStampReq sobre [raizHex].
     *
     * O nonce nao e enfeite: e o que impede que uma resposta antiga, gravada por alguem, seja
     * reapresentada como se fosse desta vistoria.
     */
    fun pedido(raizHex: String, aleatorio: SecureRandom = SecureRandom()): Pedido {
        val imprint = Der.deHex(raizHex)
        if (imprint.size != 32) throw Recusado("A raiz precisa ser um SHA-256 (32 bytes).")
        val nonce = BigInteger(1, ByteArray(8).also { aleatorio.nextBytes(it) })
            .let { if (it.signum() == 0) BigInteger.ONE else it }

        val messageImprint = Der.sequencia(
            Der.sequencia(Der.oid(OID_SHA256), Der.nulo()),
            Der.octeto(imprint)
        )
        val req = Der.sequencia(
            Der.inteiro(1L),          // version = v1
            messageImprint,
            Der.inteiro(nonce),
            Der.booleano(true)        // certReq: pedir o certificado da TSA dentro do token
        )
        return Pedido(req, nonce, imprint)
    }

    /** O que se guarda depois de conferir. */
    data class Carimbo(
        /** Token CMS completo, em base64 — e o que permite a validacao por terceiros. */
        val tokenBase64: String,
        /** Instante declarado pela TSA (genTime), em milissegundos. */
        val instante: Long,
        /** Texto do genTime como veio, para quem quiser conferir sem reinterpretar. */
        val genTimeBruto: String,
        /** Nome/endereco da TSA como configurado, para constar do laudo. */
        val autoridade: String,
        /** Marcado por quem configurou: esta TSA e credenciada na ICP-Brasil? */
        val credenciadaIcpBrasil: Boolean
    )

    /**
     * Le a TimeStampResp, confere e devolve o carimbo — ou recusa dizendo por que.
     *
     * Nunca devolve carimbo "com ressalva": ou o token se refere a esta raiz e a este nonce, ou
     * nao serve, e guardar um selo que nao se refere a esta vistoria seria pior que nao ter selo.
     */
    fun conferirResposta(
        resposta: ByteArray,
        pedido: Pedido,
        autoridade: String,
        credenciadaIcpBrasil: Boolean
    ): Carimbo {
        val raiz = runCatching { Der.ler(resposta) }.getOrElse {
            throw Recusado("Resposta da Autoridade não é DER válido: ${it.message}")
        }
        if (raiz.tag != Der.SEQUENCE) throw Recusado("Resposta não é uma TimeStampResp.")
        val partes = Der.filhos(raiz.conteudo)
        if (partes.isEmpty()) throw Recusado("Resposta vazia.")

        // PKIStatusInfo ::= SEQUENCE { status INTEGER, statusString?, failInfo? }
        val statusInfo = partes[0]
        if (statusInfo.tag != Der.SEQUENCE) throw Recusado("PKIStatusInfo ausente.")
        val status = Der.inteiroDe(Der.filhos(statusInfo.conteudo).first()).toInt()
        if (status != 0 && status != 1) {
            throw Recusado("A Autoridade recusou o pedido (status $status: ${textoStatus(status)}).")
        }
        if (partes.size < 2) throw Recusado("A resposta não trouxe o token.")

        val token = partes[1]
        val tokenBytes = byteArrayOf(token.tag.toByte()) +
            Der.comprimento(token.conteudo.size) + token.conteudo

        val tst = extrairTstInfo(token)
        conferirImprint(tst, pedido)

        val gen = genTime(tst)
        return Carimbo(
            tokenBase64 = base64(tokenBytes),
            instante = gen.first,
            genTimeBruto = gen.second,
            autoridade = autoridade,
            credenciadaIcpBrasil = credenciadaIcpBrasil
        )
    }

    private fun textoStatus(s: Int) = when (s) {
        2 -> "rejeitado"
        3 -> "esperando"
        4 -> "revogação iminente"
        5 -> "revogado"
        else -> "desconhecido"
    }

    /**
     * Desce pelo ContentInfo/SignedData ate o TSTInfo.
     *
     * Caminho: ContentInfo{ oid signedData, [0] SignedData{ version, digestAlgs, EncapContentInfo{
     * oid id-ct-TSTInfo, [0] OCTET STRING(TSTInfo) } ... } }. So se percorre o que interessa —
     * nada aqui pretende ser um leitor de CMS completo.
     */
    private fun extrairTstInfo(token: Der.Elemento): List<Der.Elemento> {
        val contentInfo = Der.filhos(token.conteudo)
        val tipo = contentInfo.firstOrNull()?.let { runCatching { Der.oidDe(it) }.getOrNull() }
        if (tipo != OID_SIGNED_DATA) throw Recusado("Token não é um SignedData do CMS.")
        val conteudo0 = contentInfo.getOrNull(1)
            ?: throw Recusado("SignedData sem conteúdo.")
        val signedData = Der.filhos(conteudo0.conteudo).firstOrNull()
            ?: throw Recusado("SignedData vazio.")
        val camposSd = Der.filhos(signedData.conteudo)

        val encap = camposSd.firstOrNull { e ->
            e.tag == Der.SEQUENCE && runCatching {
                Der.oidDe(Der.filhos(e.conteudo).first()) == OID_TST_INFO
            }.getOrDefault(false)
        } ?: throw Recusado("O token não encapsula um TSTInfo.")

        val octeto = Der.filhos(encap.conteudo).getOrNull(1)
            ?.let { Der.filhos(it.conteudo).firstOrNull() }
            ?: throw Recusado("TSTInfo ausente do token.")
        if (octeto.tag != Der.OCTET_STRING) throw Recusado("TSTInfo em formato inesperado.")

        val tst = Der.ler(octeto.conteudo)
        if (tst.tag != Der.SEQUENCE) throw Recusado("TSTInfo malformado.")
        return Der.filhos(tst.conteudo)
    }

    /**
     * TSTInfo ::= SEQUENCE { version, policy OID, messageImprint, serialNumber, genTime, ... }
     *
     * A conferencia que da sentido a tudo: o hash carimbado tem de ser o NOSSO, e o nonce tem
     * de ser o que enviamos.
     */
    private fun conferirImprint(tst: List<Der.Elemento>, pedido: Pedido) {
        val imprintSeq = tst.getOrNull(2)
            ?: throw Recusado("TSTInfo sem messageImprint.")
        val octeto = Der.filhos(imprintSeq.conteudo).getOrNull(1)
            ?: throw Recusado("messageImprint sem o hash.")
        if (!octeto.conteudo.contentEquals(pedido.imprint)) {
            throw Recusado(
                "O carimbo se refere a outro hash, não à raiz desta vistoria. " +
                    "Carimbado: ${Der.hex(octeto.conteudo).take(16)}…; " +
                    "esperado: ${Der.hex(pedido.imprint).take(16)}…"
            )
        }
        // O nonce vem depois do genTime, entre os campos opcionais. Se a TSA o devolveu, tem de
        // bater; se nao devolveu, a protecao contra reapresentacao se perde e isso e recusado.
        val nonceEncontrado = tst.drop(4).firstOrNull { it.tag == Der.INTEGER }
            ?: throw Recusado("A Autoridade não devolveu o número aleatório do pedido.")
        if (Der.inteiroDe(nonceEncontrado) != pedido.nonce) {
            throw Recusado("O número aleatório devolvido não é o do pedido — resposta possivelmente reapresentada.")
        }
    }

    /** genTime e um GeneralizedTime em UTC: "20260908T012345Z", com fracao opcional. */
    private fun genTime(tst: List<Der.Elemento>): Pair<Long, String> {
        val e = tst.firstOrNull { it.tag == Der.GENERALIZED_TIME }
            ?: throw Recusado("TSTInfo sem genTime.")
        val bruto = String(e.conteudo, Charsets.US_ASCII)
        val semFracao = bruto.substringBefore('.').removeSuffix("Z")
        if (semFracao.length < 14) throw Recusado("genTime em formato inesperado: $bruto")
        val f = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        val d = runCatching { f.parse(semFracao.take(14)) }.getOrNull()
            ?: throw Recusado("genTime não é uma data válida: $bruto")
        return d.time to bruto
    }

    /**
     * Base64 proprio, sem java.util.Base64 (API 26+) nem android.util.Base64 (nao existe fora
     * do aparelho, e este modulo precisa ser testavel sem Android). Publico porque e testado
     * contra valores conhecidos: um erro de padding aqui produz um token que nenhuma
     * ferramenta externa consegue abrir — e o token e justamente o que permite a validacao
     * por terceiros.
     */
    fun base64(bytes: ByteArray): String {
        val tabela = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
            sb.append(tabela[b0 ushr 2])
            sb.append(tabela[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            sb.append(if (i + 1 < bytes.size) tabela[((b1 and 0x0F) shl 2) or (b2 ushr 6)] else '=')
            sb.append(if (i + 2 < bytes.size) tabela[b2 and 0x3F] else '=')
            i += 3
        }
        return sb.toString()
    }

    /** SHA-256 de bytes, para quem monta pedido a partir de conteudo e nao de hash pronto. */
    fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
