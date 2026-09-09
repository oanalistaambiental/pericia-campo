package br.com.oanalistaambiental.pericia.ferramentas

import androidx.compose.runtime.Composable

/**
 * O contrato de uma ferramenta da gaveta.
 *
 * O aplicativo deixou de ser uma camera com extras e passou a ser um conjunto de ferramentas
 * de campo. Este arquivo e o que torna isso barato: ferramenta nova e um arquivo mais uma
 * linha no [Registro], sem tocar em navegacao, em permissao nem na tela inicial.
 *
 * A DECISAO QUE MAIS IMPORTA AQUI: [limite] nao e opcional.
 *
 * O aplicativo inteiro se sustenta em dizer o que NAO faz — a dispensa do art. 10 que lista o
 * que ela nao afasta, o carimbo que avisa que nao valida a cadeia de certificados, a
 * conferencia que separa arquivo de arvore. Isso era disciplina de quem escrevia. Agora e
 * exigencia do compilador: nenhuma ferramenta entra na gaveta sem declarar o proprio limite.
 */
class Ferramenta(
    val id: String,
    val nome: String,
    /** Uma linha, lida na gaveta antes de abrir. */
    val resumo: String,
    val grupo: Grupo,
    /** O que a ferramenta NAO faz. Aparece na gaveta e dentro dela. */
    val limite: String,
    val exige: Set<Recurso> = emptySet(),
    val tela: @Composable (Navegacao) -> Unit
)

enum class Grupo(val titulo: String) {
    CAMPO("MEDIR E REGISTRAR"),
    ENQUADRAR("ENQUADRAR E CONSULTAR"),
    APOIO("APOIO")
}

/**
 * O que a ferramenta precisa do aparelho.
 *
 * Serve para duas coisas, e a segunda e a que faltava: a gaveta avisa ANTES de abrir, e a
 * permissao so e pedida por quem realmente vai usar. Antes o aplicativo travava na tela de
 * permissao de camera logo na abertura — quem so queria a bussola batia numa parede que nao
 * tinha nada a ver com o que veio fazer.
 */
enum class Recurso(val descricao: String) {
    CAMERA("câmera"),
    GNSS("localização"),
    REDE("conexão"),
    PACOTE_CAMADAS("pacote de camadas instalado")
}

/** O que uma ferramenta pode pedir a quem a hospeda. */
class Navegacao(
    val voltar: () -> Unit,
    val abrir: (String) -> Unit
)
