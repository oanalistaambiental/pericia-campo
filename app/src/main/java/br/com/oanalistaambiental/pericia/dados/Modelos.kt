package br.com.oanalistaambiental.pericia.dados

/**
 * Um ponto avulso, marcado a partir da posicao do GNSS ou de uma coordenada digitada — nao
 * preso a foto nem a sessao. Existe para o "marca e guarda" que faltava: hoje so se salva
 * coordenada junto de uma foto ou dentro de um caminhamento de medicao.
 */
data class PontoSalvo(
    val id: Long = 0,
    val nome: String,
    val lat: Double,
    val lon: Double,
    val precisaoM: Float?,
    val instante: Long
)

/**
 * Um ponto do caminhamento georreferenciado do MODO VISTORIA — o trajeto percorrido durante a
 * sessão, não um vértice marcado a mão como na medição de área. Marcado sozinho, por tempo, a
 * cada leitura de GNSS enquanto o caminhamento está ativo.
 */
data class PontoCaminhamento(
    val id: Long = 0,
    val sessaoId: Long,
    val lat: Double,
    val lon: Double,
    val precisaoM: Float,
    val instante: Long
)

/**
 * Um áudio gravado durante a sessão (MODO VISTORIA) — arquivo íntegro, com hash calculado na
 * hora de parar a gravação, mesma regra de cadeia de custódia da foto. Não tem transcrição nem
 * resumo automático: isso é responsabilidade de quem ouve, por enquanto.
 */
data class AudioGravado(
    val id: Long = 0,
    val sessaoId: Long,
    val arquivo: String,
    val duracaoSegundos: Int,
    val instanteInicio: Long,
    val sha256: String
)

/**
 * Registro de uma ocorrência ambiental observada em campo — coordenada, foto e descrição (com
 * transcrição de áudio opcional), pensado para quem já usa o app (analista, consultor, perito)
 * documentar algo e DEPOIS decidir, por conta própria, se e para onde encaminha.
 *
 * Não é uma denúncia enviada pelo app — o app nunca envia nada a lugar nenhum sozinho. É o
 * registro que a pessoa pode levar consigo até o canal que escolher (ver [CanaisDenuncia]).
 */
data class OcorrenciaAmbiental(
    val id: Long = 0,
    val lat: Double,
    val lon: Double,
    val precisaoM: Float?,
    val instante: Long,
    val descricao: String?,
    val transcricaoAudio: String?,
    val fotoArquivo: String?,
    val fotoSha256: String?
)

data class Sessao(
    val id: Long = 0,
    val titulo: String,
    val processo: String?,
    val criadaEm: Long,
    val fechadaEm: Long? = null,
    val raizMerkle: String? = null,
    val carimboTempo: String? = null,   // token RFC 3161 (base64), quando obtido
    val qtdFotos: Int = 0,
    /**
     * Instante DECLARADO PELA AUTORIDADE no carimbo (genTime), em milissegundos.
     *
     * Guardado separado do token de proposito: e o unico dado do carimbo que o laudo precisa
     * mostrar, e reabrir o ASN.1 a cada exibicao seria caro e fragil. O token continua sendo a
     * fonte da verdade para quem for validar por fora.
     *
     * CAMPOS NOVOS VAO NO FIM. A primeira versao os inseriu no meio da classe, e como o Banco
     * construia `Sessao` por POSICAO, `qtdFotos` passou a receber o instante do carimbo — erro
     * que so apareceria no CI. As chamadas passaram a usar argumentos NOMEADOS por isso.
     */
    val carimboInstante: Long? = null,
    /** Autoridade que carimbou, como configurada, para constar do laudo. */
    val carimboAutoridade: String? = null,
    /**
     * Declarado por quem configurou a Autoridade, nunca adivinhado: nao ha nada no protocolo
     * RFC 3161 que permita ao aplicativo descobrir sozinho se a TSA e credenciada.
     * Credenciamento e questao juridica, nao tecnica.
     */
    val carimboCredenciado: Boolean = false
)

data class Foto(
    val id: Long = 0,
    val sessaoId: Long,
    val arquivoOriginal: String,
    val arquivoComLegenda: String?,
    val sha256: String,
    val lat: Double,
    val lon: Double,
    val precisaoM: Float,
    val altitudeM: Double?,
    val azimuteGraus: Float?,
    val inclinacaoGraus: Float?,
    val instante: Long,
    /**
     * Quantos segundos a leitura de GNSS ja tinha quando o obturador disparou.
     *
     * Um laudo precisa poder responder "de quando e essa coordenada?". Zero ou poucos segundos
     * e leitura ao vivo; dezenas de segundos e um ponto que ficou para tras. Null quando a
     * foto nao tem coordenada.
     */
    val idadeFixSegundos: Long? = null,
    val tipoOcorrencia: String?,
    val observacao: String?,
    val enderecoPendente: Boolean = true,
    val endereco: String? = null
)

data class RegistroRestricao(
    val id: Long = 0,
    val fotoId: Long,
    val camada: String,
    val fonte: String,
    val situacao: String,
    val distanciaM: Double,
    val atributos: String,
    val pacoteVersao: String,
    val uuidMetadado: String?,
    val dataExtracao: String,
    val toleranciaM: Double
)

/** Tipos de ocorrencia do formulario rapido de pericia. */
object TiposOcorrencia {
    /**
     * Usado quando `assets/tipos_ocorrencia.json` falta ou vem corrompido — o app nunca abre o
     * formulário rápido sem opção nenhuma. Fora isso, quem manda é o arquivo: acrescentar uma
     * categoria passou a ser editar dado, não recompilar o app.
     */
    val padrao = listOf(
        "Dano à APP",
        "Corte irregular de vegetação",
        "Foco de queimada",
        "Assoreamento",
        "Disposição irregular de resíduos",
        "Intervenção em cavidade",
        "Lançamento de efluente",
        "Outro"
    )

    fun carregar(abrir: () -> java.io.InputStream): List<String> = runCatching {
        val texto = abrir().bufferedReader().use { it.readText() }
        val arr = org.json.JSONArray(texto)
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: padrao
}
