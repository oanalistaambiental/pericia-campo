package br.com.oanalistaambiental.pericia.captura

import java.io.File
import java.security.MessageDigest

/**
 * Integridade da prova.
 *
 * Ponto tecnico que precisa ficar claro no produto: o SHA-256 prova que o arquivo NAO MUDOU
 * desde o calculo. Ele nao prova QUANDO isso aconteceu, porque o relogio do aparelho e
 * ajustavel pelo proprio usuario. Quem resolve isso e o carimbo do tempo (RFC 3161) de uma
 * Autoridade de Carimbo do Tempo credenciada na ICP-Brasil.
 *
 * Desenho adotado: em campo, cada foto gera seu hash, sem rede. Ao fechar a sessao, monta-se
 * uma arvore de Merkle e carimba-se SO A RAIZ quando houver conexao. Uma requisicao por
 * vistoria, e cada foto continua individualmente demonstravel pelo caminho ate a raiz.
 */
object Integridade {

    fun sha256(arquivo: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        arquivo.inputStream().use { entrada ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val lidos = entrada.read(buffer)
                if (lidos <= 0) break
                md.update(buffer, 0, lidos)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * Raiz de Merkle dos hashes da sessao, na ordem de captura.
     * Nivel impar duplica o ultimo elemento (convencao simples e amplamente usada).
     */
    fun raizMerkle(hashesHex: List<String>): String {
        if (hashesHex.isEmpty()) return ""
        var nivel = hashesHex.map { hexParaBytes(it) }
        while (nivel.size > 1) {
            val proximo = mutableListOf<ByteArray>()
            var i = 0
            while (i < nivel.size) {
                val a = nivel[i]
                val b = if (i + 1 < nivel.size) nivel[i + 1] else nivel[i]
                proximo += MessageDigest.getInstance("SHA-256").digest(a + b)
                i += 2
            }
            nivel = proximo
        }
        return nivel[0].joinToString("") { "%02x".format(it) }
    }

    /** Caminho de prova de uma folha ate a raiz, para demonstrar UMA foto isoladamente. */
    fun caminhoMerkle(hashesHex: List<String>, indice: Int): List<String> {
        var nivel = hashesHex.map { hexParaBytes(it) }
        var pos = indice
        val caminho = mutableListOf<String>()
        while (nivel.size > 1) {
            val irmao = if (pos % 2 == 0) minOf(pos + 1, nivel.size - 1) else pos - 1
            caminho += nivel[irmao].joinToString("") { "%02x".format(it) }
            val proximo = mutableListOf<ByteArray>()
            var i = 0
            while (i < nivel.size) {
                val a = nivel[i]
                val b = if (i + 1 < nivel.size) nivel[i + 1] else nivel[i]
                proximo += MessageDigest.getInstance("SHA-256").digest(a + b)
                i += 2
            }
            nivel = proximo
            pos /= 2
        }
        return caminho
    }

    private fun hexParaBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { ((Character.digit(hex[it * 2], 16) shl 4) + Character.digit(hex[it * 2 + 1], 16)).toByte() }
}
