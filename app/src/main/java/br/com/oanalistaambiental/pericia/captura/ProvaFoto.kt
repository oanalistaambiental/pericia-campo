package br.com.oanalistaambiental.pericia.captura

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Prova de UMA fotografia, para entregar sozinha.
 *
 * O caso que faltava. O laudo cobre a vistoria inteira; a conferencia da tela tambem. Mas o que
 * acontece no dia a dia e outra coisa: junta-se UMA fotografia a um processo, e e preciso
 * demonstrar que ela pertence a vistoria selada — sem anexar as outras quarenta, que muitas
 * vezes nem sao do interesse daquele processo, e as vezes nem podem ser juntadas.
 *
 * A arvore de Merkle existe exatamente para isso. Este modulo escreve, em texto puro, o que a
 * outra parte precisa para refazer a conta: o hash da foto, o caminho ate a raiz com o lado de
 * cada irmao, e a raiz selada.
 *
 * DUAS DECISOES DE PROJETO, e as duas sao sobre nao enganar quem le:
 *
 * 1. Sem raiz selada nao se emite documento nenhum. Um "comprovante" de sessao aberta pareceria
 *    prova e nao seria — e um papel com cara de prova e pior que papel nenhum.
 * 2. As instrucoes de conferencia vao POR EXTENSO, e nao pedem este aplicativo. Uma prova que
 *    so o programa que a gerou consegue verificar nao e prova, e circularidade.
 */
object ProvaFoto {

    class SessaoNaoSelada(mensagem: String) : Exception(mensagem)

    private val fmt = ThreadLocal.withInitial {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
    }

    /**
     * @param hashAtual hash do arquivo AGORA, quando foi possivel calcular; null se o arquivo
     *   nao esta mais acessivel. Serve para dizer se o arquivo em maos e mesmo aquele — que e
     *   pergunta diferente de "este registro pertence a vistoria selada".
     */
    fun gerar(
        tituloSessao: String,
        processo: String?,
        fechadaEm: Long?,
        raizSelada: String?,
        comCarimbo: Boolean,
        indice: Int,
        total: Int,
        nomeArquivo: String,
        hashGravado: String,
        instanteCaptura: Long,
        caminho: List<Integridade.Passo>,
        hashAtual: String?,
        emitidoEm: Long = System.currentTimeMillis()
    ): String {
        if (raizSelada.isNullOrBlank()) {
            throw SessaoNaoSelada(
                "A vistoria não foi encerrada, então não existe raiz selada e não há prova " +
                    "individual a emitir. Encerre a sessão antes."
            )
        }
        if (!Integridade.verificarCaminho(hashGravado, caminho, raizSelada)) {
            throw SessaoNaoSelada(
                "O caminho desta fotografia não fecha na raiz selada da vistoria. Nenhum " +
                    "documento foi emitido: o registro não pertence ao conjunto selado, ou o " +
                    "conjunto mudou depois do fechamento."
            )
        }

        val d = fmt.get()!!
        val sb = StringBuilder()
        fun l(t: String = "") = sb.append(t).append('\n')

        l("PROVA DE INTEGRIDADE DE FOTOGRAFIA ISOLADA")
        l("=".repeat(72))
        l()
        l("Vistoria .......... $tituloSessao")
        processo?.takeIf { it.isNotBlank() }?.let { l("Processo/Auto ..... $it") }
        fechadaEm?.let { l("Encerrada em ...... ${d.format(Date(it))}") }
        l("Fotografia ........ $nomeArquivo (registro $indice de $total)")
        l("Capturada em ...... ${d.format(Date(instanteCaptura))}")
        l("Documento emitido . ${d.format(Date(emitidoEm))}")
        l()
        l("O QUE ESTE DOCUMENTO DEMONSTRA")
        l("-".repeat(72))
        l("Que o código hash da fotografia acima integra o conjunto de registros selado ao")
        l("encerrar esta vistoria, representado pela raiz de Merkle abaixo. A demonstração usa")
        l("apenas os hashes listados — as demais fotografias não precisam ser entregues nem")
        l("abertas para que a conta feche.")
        l()
        l("O que os hashes do caminho revelam, com precisão: são códigos de 64 caracteres, dos")
        l("quais NÃO se extrai imagem, coordenada nem qualquer conteúdo. O primeiro passo do")
        l("caminho é o hash de uma fotografia vizinha da mesma vistoria; os demais são valores")
        l("intermediários da árvore, que não correspondem a fotografia alguma. Quem já tiver em")
        l("mãos essa fotografia vizinha poderá concluir que ela pertence à mesma vistoria — e")
        l("nada além disso.")
        l()
        l("O QUE ELE NÃO DEMONSTRA")
        l("-".repeat(72))
        l("Não demonstra QUANDO o cálculo foi feito. O relógio do aparelho é ajustável pelo")
        l("próprio usuário. Quem data com fé pública é o carimbo do tempo (RFC 3161) de")
        l("Autoridade credenciada na ICP-Brasil, aplicado sobre a raiz.")
        l(
            if (comCarimbo) "Nesta vistoria o carimbo do tempo FOI aplicado sobre a raiz."
            else "Nesta vistoria o carimbo do tempo AINDA NÃO foi aplicado sobre a raiz."
        )
        l()
        l("ESTADO DO ARQUIVO ENTREGUE")
        l("-".repeat(72))
        when {
            hashAtual == null ->
                l("Não foi possível ler o arquivo para conferir. Confira você mesmo, com o comando")
                    .also { l("da seção seguinte.") }
            hashAtual.equals(hashGravado, ignoreCase = true) ->
                l("O arquivo confere: seu hash de hoje é igual ao gravado na captura.")
            else -> {
                l("ATENÇÃO: o arquivo entregue NÃO confere com o hash gravado na captura.")
                l("Hash de hoje: $hashAtual")
                l("A prova de pertencimento abaixo continua válida para o hash ORIGINAL, mas o")
                l("arquivo em mãos não é aquele. A causa mais comum é ter trafegado por aplicativo")
                l("de mensagem ou cliente de e-mail, que recomprimem a imagem.")
            }
        }
        l()
        l("DADOS DA PROVA")
        l("-".repeat(72))
        l("Hash SHA-256 da fotografia (folha):")
        l("  $hashGravado")
        l()
        l("Caminho até a raiz (${caminho.size} passo(s)):")
        if (caminho.isEmpty()) {
            l("  (nenhum — a vistoria tem um único registro, e a folha é a própria raiz)")
        } else {
            // Percorre o caminho para saber o valor corrente em cada passo. Isso permite
            // EXPLICAR o passo em que o irmao e o proprio valor — que acontece de verdade e,
            // sem explicacao, e a primeira coisa que alguem aponta ao contestar o documento:
            // "por que o irmao desta foto e ela mesma?".
            var atual = hashGravado
            caminho.forEachIndexed { i, passo ->
                val duplicado = passo.irmaoHex.equals(atual, ignoreCase = true)
                l("  ${i + 1}. irmão à ${if (passo.irmaoAEsquerda) "ESQUERDA" else "DIREITA"}")
                l("     ${passo.irmaoHex}")
                if (duplicado) {
                    l("     (neste nível a árvore tinha número ímpar de elementos, e o último é")
                    l("      combinado consigo mesmo — por isso o irmão repete o valor anterior.")
                    l("      É a regra usada para montar a árvore, e vale também na conferência.)")
                }
                atual = runCatching { Integridade.aplicar(atual, passo) }.getOrDefault(atual)
            }
        }
        l()
        l("Raiz de Merkle selada da vistoria:")
        l("  $raizSelada")
        l()
        l("COMO CONFERIR, SEM ESTE APLICATIVO")
        l("-".repeat(72))
        l("1) Confira o hash do arquivo recebido:")
        l("     Linux/macOS:  sha256sum \"$nomeArquivo\"")
        l("     Windows:      certutil -hashfile \"$nomeArquivo\" SHA256")
        l("   O resultado deve ser igual ao hash da folha, acima.")
        l()
        l("2) Refaça o caminho até a raiz. A cada passo, junte os DOIS hashes na ordem indicada")
        l("   (irmão à esquerda: irmão primeiro; à direita: o valor corrente primeiro), converta")
        l("   de hexadecimal para bytes e aplique SHA-256. Ao fim, o valor tem de ser a raiz.")
        l()
        l("   Script equivalente, em Python 3 (copie e execute):")
        l()
        l("     import hashlib")
        l("     atual = bytes.fromhex(\"$hashGravado\")")
        caminho.forEach { passo ->
            val irmao = "bytes.fromhex(\"${passo.irmaoHex}\")"
            val juntos = if (passo.irmaoAEsquerda) "$irmao + atual" else "atual + $irmao"
            l("     atual = hashlib.sha256($juntos).digest()")
        }
        l("     print(atual.hex())")
        l("     # deve imprimir: $raizSelada")
        l()
        l("-".repeat(72))
        l("Documento gerado por ferramenta independente, a partir de dados do próprio aparelho.")
        l("Não substitui perícia oficial nem vincula a Administração Pública.")
        return sb.toString()
    }

    /**
     * Nome de arquivo previsivel e sem caractere problematico.
     *
     * Colapsa sequencias de substituicao e apara as pontas: "foto(1).png" tem de virar
     * "prova-foto-1.txt", e nao "prova-foto-1-.txt" — nome de arquivo terminando em traco
     * parece truncado para quem recebe, e este documento vai anexado a processo.
     */
    fun nomeSugerido(nomeArquivo: String): String {
        val base = nomeArquivo.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9_-]+"), "-")
            .trim('-')
            .ifBlank { "foto" }
        return "prova-$base.txt"
    }
}
