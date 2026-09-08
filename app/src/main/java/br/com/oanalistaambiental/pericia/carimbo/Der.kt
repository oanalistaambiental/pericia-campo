package br.com.oanalistaambiental.pericia.carimbo

/**
 * DER minimo: so o que o RFC 3161 exige, escrito a mao.
 *
 * POR QUE NAO UMA BIBLIOTECA. A escolha obvia seria BouncyCastle. Ela traz alguns megabytes
 * para um aplicativo que se instala por APK baixado no celular em campo, e — o que pesa mais
 * aqui — nao da para verificar nada dela no ambiente onde este codigo e conferido, que compila
 * Kotlin puro sem SDK do Android. Um carimbo do tempo que ninguem consegue testar antes de
 * publicar e exatamente o tipo de coisa que nao pode entrar num aplicativo de pericia.
 *
 * O que se escreve e le aqui e um subconjunto pequeno e fechado: SEQUENCE, INTEGER, OID,
 * OCTET STRING, NULL, GeneralizedTime e comprimentos em forma curta e longa. Nada de BER
 * indefinido, nada de tipos que a TimeStampReq/Resp nao usa.
 *
 * Tudo aqui e Kotlin puro, sem Android: da para testar byte a byte.
 */
object Der {

    const val INTEGER = 0x02
    const val BIT_STRING = 0x03
    const val OCTET_STRING = 0x04
    const val NULO = 0x05
    const val OID = 0x06
    const val UTF8 = 0x0C
    const val GENERALIZED_TIME = 0x18
    const val SEQUENCE = 0x30
    const val SET = 0x31

    class Malformado(mensagem: String) : Exception(mensagem)

    // ------------------------------------------------------------------ escrita

    /** Comprimento em DER: curta ate 127, longa acima — nunca a forma indefinida do BER. */
    fun comprimento(n: Int): ByteArray {
        if (n < 0) throw Malformado("comprimento negativo")
        if (n < 0x80) return byteArrayOf(n.toByte())
        var restante = n
        val bytes = mutableListOf<Byte>()
        while (restante > 0) {
            bytes.add(0, (restante and 0xFF).toByte())
            restante = restante ushr 8
        }
        return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
    }

    fun tlv(tag: Int, conteudo: ByteArray): ByteArray =
        byteArrayOf(tag.toByte()) + comprimento(conteudo.size) + conteudo

    fun sequencia(vararg partes: ByteArray): ByteArray =
        tlv(SEQUENCE, partes.fold(ByteArray(0)) { a, b -> a + b })

    fun octeto(bytes: ByteArray): ByteArray = tlv(OCTET_STRING, bytes)

    fun nulo(): ByteArray = byteArrayOf(NULO.toByte(), 0)

    fun booleano(v: Boolean): ByteArray =
        byteArrayOf(0x01, 0x01, if (v) 0xFF.toByte() else 0x00)

    /**
     * INTEGER em complemento de dois, com o zero a esquerda quando o bit mais alto esta
     * ligado — sem isso um nonce positivo vira negativo do outro lado, e algumas TSAs
     * simplesmente recusam a requisicao.
     */
    fun inteiro(valor: java.math.BigInteger): ByteArray = tlv(INTEGER, valor.toByteArray())

    fun inteiro(valor: Long): ByteArray = inteiro(java.math.BigInteger.valueOf(valor))

    /** OID no formato pontilhado: "1.2.840.113549.1.1.11". */
    fun oid(pontilhado: String): ByteArray {
        val partes = pontilhado.split('.').map { it.toLongOrNull() ?: throw Malformado("OID inválido: $pontilhado") }
        if (partes.size < 2) throw Malformado("OID precisa de ao menos dois arcos: $pontilhado")
        val saida = mutableListOf<Byte>()
        saida.add((partes[0] * 40 + partes[1]).toByte())
        for (i in 2 until partes.size) saida.addAll(base128(partes[i]).toList())
        return tlv(OID, saida.toByteArray())
    }

    private fun base128(v: Long): ByteArray {
        if (v == 0L) return byteArrayOf(0)
        var restante = v
        val bytes = mutableListOf<Byte>()
        var primeiro = true
        while (restante > 0) {
            val b = (restante and 0x7F).toInt()
            bytes.add(0, (if (primeiro) b else (b or 0x80)).toByte())
            primeiro = false
            restante = restante ushr 7
        }
        return bytes.toByteArray()
    }

    // ------------------------------------------------------------------ leitura

    /** Um elemento lido: a tag, o conteudo, e onde termina no buffer. */
    data class Elemento(val tag: Int, val conteudo: ByteArray, val fim: Int) {
        override fun equals(other: Any?) = other is Elemento && tag == other.tag &&
            conteudo.contentEquals(other.conteudo) && fim == other.fim
        override fun hashCode() = (tag * 31 + conteudo.contentHashCode()) * 31 + fim
    }

    /**
     * Le UM elemento a partir de [de].
     *
     * Recusa comprimento maior que o buffer em vez de estourar indice: resposta truncada de
     * rede e o caso comum, e uma excecao com mensagem vale mais que um IndexOutOfBounds.
     */
    fun ler(bytes: ByteArray, de: Int = 0): Elemento {
        if (de + 2 > bytes.size) throw Malformado("elemento truncado na posição $de")
        val tag = bytes[de].toInt() and 0xFF
        val primeiro = bytes[de + 1].toInt() and 0xFF
        var i = de + 2
        val tamanho: Int
        if (primeiro < 0x80) {
            tamanho = primeiro
        } else {
            val n = primeiro and 0x7F
            if (n == 0) throw Malformado("comprimento indefinido não é DER")
            if (n > 4) throw Malformado("comprimento longo demais ($n bytes)")
            if (i + n > bytes.size) throw Malformado("comprimento truncado")
            var acc = 0L
            for (k in 0 until n) { acc = (acc shl 8) or (bytes[i + k].toLong() and 0xFF); }
            i += n
            if (acc > Int.MAX_VALUE.toLong()) throw Malformado("elemento grande demais")
            tamanho = acc.toInt()
        }
        if (i + tamanho > bytes.size) {
            throw Malformado("elemento diz ter $tamanho bytes, mas só há ${bytes.size - i}")
        }
        return Elemento(tag, bytes.copyOfRange(i, i + tamanho), i + tamanho)
    }

    /** Todos os elementos de um conteudo construido (SEQUENCE, SET, contexto explicito). */
    fun filhos(conteudo: ByteArray): List<Elemento> {
        val saida = mutableListOf<Elemento>()
        var i = 0
        while (i < conteudo.size) {
            val e = ler(conteudo, i)
            saida += e
            if (e.fim <= i) throw Malformado("elemento sem avanço na posição $i")
            i = e.fim
        }
        return saida
    }

    fun inteiroDe(e: Elemento): java.math.BigInteger {
        if (e.tag != INTEGER) throw Malformado("esperava INTEGER, veio tag 0x%02x".format(e.tag))
        if (e.conteudo.isEmpty()) throw Malformado("INTEGER vazio")
        return java.math.BigInteger(e.conteudo)
    }

    fun oidDe(e: Elemento): String {
        if (e.tag != OID) throw Malformado("esperava OID, veio tag 0x%02x".format(e.tag))
        val c = e.conteudo
        if (c.isEmpty()) throw Malformado("OID vazio")
        val primeiro = c[0].toInt() and 0xFF
        val sb = StringBuilder("${primeiro / 40}.${primeiro % 40}")
        var acc = 0L
        for (i in 1 until c.size) {
            val b = c[i].toInt() and 0xFF
            acc = (acc shl 7) or (b and 0x7F).toLong()
            if (b < 0x80) { sb.append('.').append(acc); acc = 0 }
        }
        if (acc != 0L) throw Malformado("OID termina no meio de um arco")
        return sb.toString()
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun deHex(hex: String): ByteArray {
        if (hex.length % 2 != 0) throw Malformado("hexadecimal de tamanho ímpar")
        return ByteArray(hex.length / 2) {
            val a = Character.digit(hex[it * 2], 16)
            val b = Character.digit(hex[it * 2 + 1], 16)
            if (a < 0 || b < 0) throw Malformado("caractere não hexadecimal")
            ((a shl 4) + b).toByte()
        }
    }
}
