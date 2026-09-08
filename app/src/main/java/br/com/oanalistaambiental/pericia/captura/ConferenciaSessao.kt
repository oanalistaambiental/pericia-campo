package br.com.oanalistaambiental.pericia.captura

import java.io.File

/**
 * Conferencia completa de uma sessao selada: arquivo, hash e arvore.
 *
 * POR QUE ISTO E UM MODULO SEPARADO. O aplicativo ja conferia arquivo contra hash gravado
 * (`Integridade.conferir`). Isso responde UMA pergunta — "o arquivo mudou desde a captura?" —
 * e deixa a outra sem resposta: "este hash pertence mesmo a raiz que foi selada?". Sao coisas
 * independentes, e confundi-las e como um laudo se desfaz:
 *
 *  - um arquivo pode bater com o hash gravado e mesmo assim NAO pertencer a arvore selada,
 *    porque a foto foi inserida no banco depois do fechamento da sessao;
 *  - e um arquivo pode ter sido recomprimido por aplicativo de mensagem (hash nao bate) sem
 *    que a arvore tenha qualquer problema — a arvore e sobre os hashes gravados, nao sobre os
 *    arquivos de hoje.
 *
 * Por isso aqui as duas perguntas sao respondidas SEPARADAMENTE, e o resumo nunca funde as
 * duas numa unica palavra tranquilizadora.
 *
 * Kotlin puro: da para testar a logica de conferencia inteira sem aparelho.
 */
object ConferenciaSessao {

    private val HEX = Regex("[0-9a-fA-F]+")

    /** Hash gravado que nao e hexadecimal de tamanho par nao pertence a arvore nenhuma. */
    private fun hexValido(h: String): Boolean =
        h.isNotEmpty() && h.length % 2 == 0 && HEX.matches(h)

    /** Uma foto como o banco a guarda: onde esta o arquivo e qual hash foi gravado na captura. */
    data class Folha(val arquivo: String, val hashGravado: String, val rotulo: String)

    enum class EstadoArquivo { INTEGRO, ALTERADO, AUSENTE, HASH_INVALIDO }

    data class Item(
        val rotulo: String,
        val arquivo: String,
        val estadoArquivo: EstadoArquivo,
        val hashGravado: String,
        val hashAtual: String?,
        /** O hash gravado pertence a arvore que produziu a raiz selada? */
        val pertenceAArvore: Boolean
    )

    /**
     * Por que nao existe um unico booleano "sessao ok".
     *
     * Um so booleano obrigaria a escolher o que fazer quando o arquivo mudou mas a arvore
     * fecha, ou quando a arvore nao fecha mas os arquivos batem — e qualquer escolha esconde
     * metade do problema de quem vai assinar. Os dois estados ficam lado a lado.
     */
    data class Resultado(
        val selada: Boolean,
        val raizSelada: String?,
        val raizRecalculada: String,
        val itens: List<Item>
    ) {
        val arvoreConfere: Boolean get() = selada && raizSelada.equals(raizRecalculada, ignoreCase = true)
        val arquivosIntegros: Int get() = itens.count { it.estadoArquivo == EstadoArquivo.INTEGRO }
        val arquivosComProblema: Int get() = itens.size - arquivosIntegros

        /**
         * Frase principal. Nao existe caso em que ela diga so "tudo certo" sem que as DUAS
         * conferencias tenham passado — e, se a sessao nem foi selada, ela diz isso primeiro,
         * porque aqui ausencia de selo nao pode ser lida como selo em ordem.
         */
        fun resumo(): String = when {
            !selada ->
                "Sessão ainda ABERTA: não há raiz selada, então não há o que conferir na árvore. " +
                    "Dos ${itens.size} arquivos, $arquivosIntegros conferem com o hash gravado na captura. " +
                    "Feche a sessão para selar a integridade do conjunto."
            !arvoreConfere ->
                "A raiz recalculada NÃO bate com a raiz selada. O conjunto de registros mudou " +
                    "depois do fechamento — foto acrescentada, removida ou reordenada. A conferência " +
                    "arquivo por arquivo abaixo não supre isso."
            arquivosComProblema > 0 ->
                "A árvore fecha na raiz selada, mas $arquivosComProblema de ${itens.size} arquivo(s) " +
                    "não conferem com o hash gravado. O conjunto de registros está íntegro; os " +
                    "arquivos dessas fotos foram alterados ou não estão mais no aparelho."
            else ->
                "Conferido: os ${itens.size} arquivos batem com o hash gravado na captura, e todos " +
                    "os hashes fecham na raiz selada da sessão."
        }

        /**
         * O que o hash NAO prova. Vai junto do resultado de proposito: e a ressalva que o
         * laudo precisa carregar, e que some se so aparecer na documentacao do codigo.
         */
        val RESSALVA_TEMPO: String get() =
            "O hash prova que nada mudou desde o cálculo; não prova QUANDO o cálculo foi feito, " +
                "porque o relógio do aparelho é ajustável. Quem data é o carimbo do tempo " +
                "(RFC 3161) de Autoridade credenciada na ICP-Brasil, aplicado sobre a raiz."
    }

    /**
     * [abrir] existe para o teste: em producao e `::File`, no teste e um mapa em memoria.
     * Sem isso a unica forma de exercitar a conferencia seria escrevendo arquivo em disco.
     */
    fun conferir(
        raizSelada: String?,
        folhas: List<Folha>,
        abrir: (String) -> File? = { File(it) }
    ): Resultado {
        val hashes = folhas.map { it.hashGravado }
        val raizRecalculada = runCatching { Integridade.raizMerkle(hashes) }.getOrDefault("")
        val selada = !raizSelada.isNullOrBlank()
        val arvoreFecha = selada && raizSelada.equals(raizRecalculada, ignoreCase = true)

        val itens = folhas.mapIndexed { i, folha ->
            // Um unico open e um unico hash por arquivo. Numa vistoria de 50 fotos de 8 MP,
            // ler tudo duas vezes e a diferenca entre exportar o laudo e o usuario achar que
            // o aplicativo travou.
            val f = abrir(folha.arquivo)?.takeIf { it.exists() }
            val hashAtual = f?.let { runCatching { Integridade.sha256(it) }.getOrNull() }
            val estado = when {
                // Hash gravado invalido vem PRIMEIRO: sem ele nao ha contra o que comparar, e
                // chamar isso de "ALTERADO" acusaria o arquivo por um defeito do banco.
                !hexValido(folha.hashGravado) -> EstadoArquivo.HASH_INVALIDO
                hashAtual == null -> EstadoArquivo.AUSENTE
                hashAtual.equals(folha.hashGravado, ignoreCase = true) -> EstadoArquivo.INTEGRO
                else -> EstadoArquivo.ALTERADO
            }

            // A prova individual so faz sentido se a raiz selada existe E o hash gravado e
            // hexadecimal valido — hash corrompido nao pertence a arvore nenhuma.
            val pertence = arvoreFecha && runCatching {
                Integridade.verificarCaminho(
                    folha.hashGravado,
                    Integridade.caminhoMerkle(hashes, i),
                    raizSelada!!
                )
            }.getOrDefault(false)

            Item(folha.rotulo, folha.arquivo, estado, folha.hashGravado, hashAtual, pertence)
        }

        return Resultado(selada, raizSelada, raizRecalculada, itens)
    }
}
