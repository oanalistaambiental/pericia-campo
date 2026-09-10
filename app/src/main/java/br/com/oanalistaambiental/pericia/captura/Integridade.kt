package br.com.oanalistaambiental.pericia.captura

import java.io.File
import java.io.InputStream
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

    /** Mesma conta, para um arquivo escolhido de fora (SAF/Uri) — quem confere não precisa copiar antes. */
    fun sha256(entrada: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val lidos = entrada.read(buffer)
            if (lidos <= 0) break
            md.update(buffer, 0, lidos)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

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

    /**
     * Um passo do caminho de prova: o hash do irmao E DE QUE LADO ele fica.
     *
     * O LADO NAO E DETALHE — sem ele a prova nao fecha. Quem confere precisa saber se
     * concatena irmao+no ou no+irmao antes de aplicar o SHA-256; trocar a ordem da um hash
     * completamente diferente. A versao anterior devolvia so a lista de hashes irmaos, o que
     * tornava o caminho impossivel de verificar — e nao havia nenhuma funcao de verificacao no
     * aplicativo, entao ninguem tropecava nisso. Era a promessa central do produto ("cada foto
     * continua individualmente demonstravel pelo caminho ate a raiz") sem nada que a cumprisse.
     */
    data class Passo(val irmaoHex: String, val irmaoAEsquerda: Boolean)

    /** Caminho de prova de uma folha ate a raiz, para demonstrar UMA foto isoladamente. */
    fun caminhoMerkle(hashesHex: List<String>, indice: Int): List<Passo> {
        if (indice !in hashesHex.indices) return emptyList()
        var nivel = hashesHex.map { hexParaBytes(it) }
        var pos = indice
        val caminho = mutableListOf<Passo>()
        while (nivel.size > 1) {
            // Nivel impar duplica o ultimo: ai o no e irmao de si mesmo, e o lado e a direita.
            val irmao = if (pos % 2 == 0) minOf(pos + 1, nivel.size - 1) else pos - 1
            caminho += Passo(nivel[irmao].hex(), irmaoAEsquerda = irmao < pos)
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

    /**
     * Refaz o caminho e confere se chega na raiz. E o outro lado da prova.
     *
     * Sem esta funcao o caminho era um dado que ninguem sabia usar. Com ela, o perito (ou quem
     * contesta o laudo) consegue demonstrar UMA foto sem precisar das outras da sessao: basta
     * o hash da foto, o caminho gravado e a raiz carimbada.
     */
    fun verificarCaminho(folhaHex: String, caminho: List<Passo>, raizEsperadaHex: String): Boolean {
        if (raizEsperadaHex.isBlank()) return false
        var atual = folhaHex
        for (passo in caminho) {
            atual = runCatching { aplicar(atual, passo) }.getOrNull() ?: return false
        }
        return atual.equals(raizEsperadaHex, ignoreCase = true)
    }

    /**
     * Um passo do caminho: junta o valor corrente com o irmao, na ordem certa, e devolve o
     * hash do no pai. Publico porque quem MOSTRA a prova precisa refazer os mesmos passos que
     * quem a VERIFICA — se fossem duas implementacoes, uma poderia divergir da outra sem que
     * nenhum teste notasse.
     */
    fun aplicar(atualHex: String, passo: Passo): String {
        val atual = hexParaBytes(atualHex)
        val irmao = hexParaBytes(passo.irmaoHex)
        val juntos = if (passo.irmaoAEsquerda) irmao + atual else atual + irmao
        return MessageDigest.getInstance("SHA-256").digest(juntos).hex()
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    /**
     * Hex para bytes, RECUSANDO o que nao for hex.
     *
     * `Character.digit` devolve -1 para caractere invalido, sem lancar nada. A versao anterior
     * usava esse retorno direto na conta: um hash truncado ou corrompido no banco virava um
     * array de bytes plausivel, a raiz saia com cara de raiz, e nada no aplicativo indicava
     * que a prova estava construida sobre lixo. Numa ferramenta de cadeia de custodia isso e
     * pior que falhar.
     */
    private fun hexParaBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0 && hex.isNotEmpty()) {
            "hash com tamanho invalido (${hex.length} caracteres)"
        }
        return ByteArray(hex.length / 2) {
            val alto = Character.digit(hex[it * 2], 16)
            val baixo = Character.digit(hex[it * 2 + 1], 16)
            require(alto >= 0 && baixo >= 0) { "hash com caractere que nao e hexadecimal" }
            ((alto shl 4) + baixo).toByte()
        }
    }

    // ---- verificacao ----

    enum class Estado { INTEGRO, ALTERADO, AUSENTE }

    data class Conferencia(val arquivo: String, val estado: Estado, val hashAtual: String?)

    /**
     * Reconfere os arquivos contra os hashes gravados na captura.
     *
     * Este e o recurso que fecha o argumento do produto: nao basta calcular o hash, e preciso
     * poder demonstrar depois que ele ainda bate. Um arquivo que passou por aplicativo de
     * mensagem aparece aqui como ALTERADO — que e exatamente a licao que o perito precisa ver
     * uma vez para nunca mais repetir.
     */
    fun conferir(pares: List<Pair<String, String>>): List<Conferencia> = pares.map { (caminho, esperado) ->
        val f = File(caminho)
        when {
            !f.exists() -> Conferencia(caminho, Estado.AUSENTE, null)
            else -> {
                val atual = sha256(f)
                Conferencia(caminho, if (atual == esperado) Estado.INTEGRO else Estado.ALTERADO, atual)
            }
        }
    }
}
