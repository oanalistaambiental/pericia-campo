package br.com.oanalistaambiental.pericia.dados

data class Sessao(
    val id: Long = 0,
    val titulo: String,
    val processo: String?,
    val criadaEm: Long,
    val fechadaEm: Long? = null,
    val raizMerkle: String? = null,
    val carimboTempo: String? = null,   // token RFC 3161 (base64), quando obtido
    val qtdFotos: Int = 0
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
}
