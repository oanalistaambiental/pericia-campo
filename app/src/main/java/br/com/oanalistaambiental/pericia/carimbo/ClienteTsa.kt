package br.com.oanalistaambiental.pericia.carimbo

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * A unica parte do carimbo que toca a rede. Separada de proposito: tudo o que decide alguma
 * coisa esta em [CarimboTempo], que e Kotlin puro e testavel; aqui so se abre a conexao.
 *
 * Uma requisicao por vistoria, sobre a raiz. Sem retentativa automatica: se a Autoridade
 * respondeu errado ou nao respondeu, quem decide tentar de novo e o usuario — repetir sozinho
 * gastaria a franquia de uma TSA paga sem que ninguem soubesse.
 */
object ClienteTsa {

    /** Endereco padrao, e a declaracao honesta do que ele e. */
    const val URL_PADRAO = "https://freetsa.org/tsr"
    const val NOME_PADRAO = "FreeTSA (freetsa.org)"

    /**
     * O padrao NAO e credenciado na ICP-Brasil, e isso e dito em todo lugar onde o carimbo
     * aparece. Serve para demonstrar o mecanismo e para uso interno; um laudo que va a processo
     * pede Autoridade credenciada, configurada aqui pelo proprio usuario.
     */
    const val PADRAO_E_CREDENCIADA = false

    const val AVISO_PADRAO =
        "A Autoridade configurada NÃO é credenciada na ICP-Brasil. O carimbo demonstra o " +
            "mecanismo e serve para controle interno, mas não tem a fé pública que um processo " +
            "costuma exigir. Configure uma Autoridade credenciada antes de usar em laudo oficial."

    class FalhaDeRede(mensagem: String, causa: Throwable? = null) : Exception(mensagem, causa)

    /**
     * Envia a TimeStampReq e devolve os bytes crus da resposta.
     *
     * Nao interpreta nada: quem confere e [CarimboTempo.conferirResposta]. Isso mantem a
     * decisao de aceitar ou recusar num lugar so, testavel sem rede.
     */
    fun enviar(url: String, pedido: ByteArray, tempoLimiteMs: Int = 20_000): ByteArray {
        val alvo = runCatching { URL(url) }.getOrElse {
            throw FalhaDeRede("Endereço da Autoridade inválido: $url")
        }
        if (alvo.protocol != "https") {
            // HTTP simples deixaria a resposta trocavel no caminho. O nonce ainda protegeria
            // contra reapresentacao, mas nao ha razao para abrir mao do que e gratuito.
            throw FalhaDeRede("A Autoridade precisa ser acessada por HTTPS.")
        }
        val con = (alvo.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = tempoLimiteMs
            readTimeout = tempoLimiteMs
            setRequestProperty("Content-Type", CarimboTempo.TIPO_CONTEUDO)
            setRequestProperty("Accept", CarimboTempo.TIPO_RESPOSTA)
        }
        try {
            con.outputStream.use { it.write(pedido) }
            val codigo = con.responseCode
            if (codigo !in 200..299) {
                throw FalhaDeRede("A Autoridade respondeu HTTP $codigo.")
            }
            val bytes = con.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) throw FalhaDeRede("A Autoridade respondeu vazio.")
            return bytes
        } catch (e: IOException) {
            throw FalhaDeRede("Não foi possível falar com a Autoridade: ${e.message}", e)
        } finally {
            runCatching { con.disconnect() }
        }
    }
}
