package br.com.oanalistaambiental.pericia.geo

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateFilter
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import java.io.File
import kotlin.math.cos

enum class Situacao { DENTRO, PROXIMO_AO_LIMITE, FORA }

data class Proveniencia(
    val pacoteVersao: String,
    val uuidMetadado: String?,
    val dataExtracao: String,
    val toleranciaSimplificacaoM: Double
)

data class Restricao(
    val camadaNome: String,
    val fonte: String,
    val situacao: Situacao,
    /** Negativo = dentro do poligono. Em metros. */
    val distanciaBordaM: Double,
    val atributos: Map<String, String>,
    val proveniencia: Proveniencia
) {
    /**
     * Texto para tela e para o laudo. A escolha das palavras nao e estilo: o app LOCALIZA,
     * o perito CONCLUI. Por isso "indicio", nunca "constatado".
     */
    fun frase(): String = when (situacao) {
        Situacao.DENTRO ->
            "Indício de ponto INTERNO a $camadaNome ($fonte)."
        Situacao.PROXIMO_AO_LIMITE ->
            "Ponto a %.0f m do limite de %s (%s) — dentro da margem de erro do GPS; indefinido."
                .format(kotlin.math.abs(distanciaBordaM), camadaNome, fonte)
        Situacao.FORA ->
            "Fora de $camadaNome ($fonte), a %.0f m.".format(distanciaBordaM)
    }
}

data class PontoConsulta(
    val lat: Double,
    val lon: Double,
    val precisaoM: Float,
    val instanteMillis: Long
)

/**
 * Consulta de restricao locacional 100% offline, contra o pacote GeoPackage embarcado.
 *
 * Regra de ouro do desenho: isto NUNCA entra no caminho critico do obturador. A foto e
 * capturada, gravada e "hasheada" primeiro; esta consulta roda depois, em background, e se
 * anexa ao registro da sessao. Se falhar, a foto continua valida.
 */
class ConsultaRestricao(
    private val pacote: GeoPacote,
    /** Folga alem da precisao do GPS para ainda avisar "proximo ao limite". */
    private val folgaAvisoM: Double = 50.0
) {

    private val gf = GeometryFactory()

    fun consultar(ponto: PontoConsulta): List<Restricao> {
        val versao = pacote.versaoPacote()
        val margemM = ponto.precisaoM + folgaAvisoM

        // bbox em graus, expandido pela margem de erro (nao pelo ponto puro)
        val dLat = margemM / 111_320.0
        val dLon = margemM / (111_320.0 * cos(Math.toRadians(ponto.lat)).coerceAtLeast(0.1))

        val zona = Utm.zonaDe(ponto.lon)
        val pontoUtm = Utm.projetar(ponto.lat, ponto.lon, zona)
        val pUtm = gf.createPoint(Coordinate(pontoUtm.easting, pontoUtm.northing))

        val achados = mutableListOf<Restricao>()

        for (camada in pacote.camadas()) {
            val candidatas = try {
                pacote.candidatas(
                    camada.tabela,
                    ponto.lon - dLon, ponto.lat - dLat,
                    ponto.lon + dLon, ponto.lat + dLat
                )
            } catch (e: Exception) {
                continue // camada ausente no pacote regional: nao e erro fatal
            }

            var melhor: Pair<Double, Feicao>? = null
            for (f in candidatas) {
                val geomUtm = projetarParaUtm(f.geometria, zona)
                val d = distanciaAssinada(geomUtm, pUtm, camada)
                if (melhor == null || d < melhor!!.first) melhor = d to f
            }

            val (dist, feicao) = melhor ?: continue
            val situacao = classificar(dist, ponto.precisaoM)
            if (situacao == Situacao.FORA && dist > folgaAvisoM) continue // longe demais: nao polui a tela

            achados += Restricao(
                camadaNome = camada.nome,
                fonte = camada.fonte,
                situacao = situacao,
                distanciaBordaM = dist,
                atributos = feicao.atributos,
                proveniencia = Proveniencia(versao, camada.uuid, camada.dataExtracao, camada.toleranciaM)
            )
        }
        return achados.sortedBy { it.distanciaBordaM }
    }

    /**
     * A classificacao em tres estados e o que separa instrumento de pericia de app generico.
     *
     * Um app comum diz "voce esta dentro da UC" com 15 m de erro e 12 m de borda.
     * Aqui isso vira "a 12 m do limite, precisao de 15 m - indefinido", e os dois numeros vao
     * para o registro. E a diferenca entre uma afirmacao que cai em audiencia e um registro
     * que se sustenta.
     */
    fun classificar(distanciaM: Double, precisaoM: Float): Situacao = when {
        distanciaM < -precisaoM -> Situacao.DENTRO
        distanciaM > precisaoM + folgaAvisoM -> Situacao.FORA
        else -> Situacao.PROXIMO_AO_LIMITE
    }

    /** Negativo dentro, positivo fora. Camada de pontos usa o raio de influencia declarado. */
    private fun distanciaAssinada(geom: Geometry, ponto: org.locationtech.jts.geom.Point, camada: CamadaInfo): Double {
        if (camada.tipo == "ponto") {
            val raio = camada.raioM ?: 0.0
            return geom.distance(ponto) - raio
        }
        val bruta = geom.boundary.distance(ponto)
        return if (geom.contains(ponto)) -bruta else bruta
    }

    private fun projetarParaUtm(geom: Geometry, zona: Int): Geometry {
        val copia = geom.copy()
        copia.apply(CoordinateFilter { c ->
            val p = Utm.projetar(c.y, c.x, zona)   // GeoPackage guarda x=lon, y=lat
            c.x = p.easting
            c.y = p.northing
        })
        copia.geometryChanged()
        return copia
    }

    companion object {
        fun abrir(arquivoGpkg: File, folgaAvisoM: Double = 50.0): ConsultaRestricao =
            ConsultaRestricao(GeoPacote(arquivoGpkg), folgaAvisoM)
    }
}
