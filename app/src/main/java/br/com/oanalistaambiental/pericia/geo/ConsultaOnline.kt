package br.com.oanalistaambiental.pericia.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale
import kotlin.math.cos

/**
 * Consulta de restricao locacional AO VIVO, direto no WFS publico do IDE-Sisema — complementa
 * [ConsultaRestricao] (100% offline, contra o pacote embarcado), nunca a substitui.
 *
 * POR QUE EXISTE. Algumas camadas mudam de posicao entre uma atualizacao do pacote e outra —
 * nova Terra Indigena em fase de reestudo, por exemplo. Outras raramente mudam (a area da Lei
 * da Mata Atlantica e a mesma ha anos). Quando ha sinal, a consulta ao vivo pega o dado do
 * momento; sem sinal — o caso normal em campo rural —, so o pacote offline responde. A tela
 * precisa dizer QUAL dos dois respondeu, nunca misturar os dois como se fossem a mesma coisa.
 *
 * Pede so um retangulo pequeno ao redor do ponto (parametro `bbox` do WFS): mesmo numa camada
 * de dezenas de megabytes no todo (rios de preservacao permanente, p.ex.), a resposta para um
 * ponto so e alguns KB — e o que torna viavel consultar ao vivo uma camada pesada demais para
 * embarcar inteira no APK.
 *
 * Regra de ouro identica a [ConsultaRestricao]: NUNCA entra no caminho critico do obturador, e
 * falha em silencio (sem sinal nao e erro, e o esperado).
 */
object ConsultaOnline {

    private const val WFS = "https://geoserver.meioambiente.mg.gov.br/ows"

    data class CamadaOnline(
        val tabela: String,
        val nome: String,
        val fonte: String,
        val tecnico: String,
        val tipo: String = "poligono",
        val raioM: Double? = null
    )

    /**
     * So as camadas com nome tecnico do WFS ja confirmado (verificado direto no
     * GetCapabilities em 09/09/2026 — se o IDE-Sisema renomear a camada, a consulta passa a
     * falhar em silencio para ela, do mesmo jeito que uma camada offline ausente).
     */
    val camadas = listOf(
        CamadaOnline(
            "terras_indigenas", "Terras indígenas", "Funai/IDE-Sisema",
            "IDE:ide_2003_mg_terras_indigenas_pol"
        ),
        CamadaOnline(
            "raios_terras_indigenas", "Raios de restrição a terras indígenas", "Feam/Funai/IDE-Sisema",
            "IDE:ide_2004_mg_raio_rest_terras_indigenas_pol"
        ),
        CamadaOnline(
            "terras_quilombolas", "Terras quilombolas", "Incra/IDE-Sisema",
            "IDE:ide_2005_mg_terras_quilombolas_pol"
        ),
        CamadaOnline(
            "raios_terras_quilombolas", "Raios de restrição a terras quilombolas", "Feam/Incra/IDE-Sisema",
            "IDE:ide_2006_mg_raio_rest_terras_quilombolas_pol"
        ),
        CamadaOnline(
            "mata_atlantica", "Área de aplicação da Lei da Mata Atlântica (11.428/2006)", "MMA/IDE-Sisema",
            "IDE:ide_2020_mg_area_lei_mata_atlantica_pol"
        ),
        CamadaOnline(
            "rios_preservacao_permanente", "Rios de Preservação Permanente — Lei 15.082/2004", "Igam/IDE-Sisema",
            "IDE:ide_2009_mg_rios_preservacao_permanente_pol"
        ),
        CamadaOnline(
            "influencia_cavidades", "Áreas de influência de cavidades — raio de 250 m", "Cecav/Feam/IDE-Sisema",
            "IDE:ide_2001_mg_raio_protecao_cavidades_pol"
        ),
    )

    /**
     * Consulta todas as [camadas] para o ponto informado, EM PARALELO — sem isso, sem sinal
     * (o caso normal em campo) somaria o timeout de cada camada uma apos a outra, e a consulta
     * "em segundo plano" passaria quase um minuto tentando. Cada camada falha de forma
     * independente: uma sem resposta nao atrasa nem derruba as demais.
     */
    suspend fun consultar(
        lat: Double, lon: Double, precisaoM: Float, folgaAvisoM: Double = 50.0
    ): List<Restricao> = coroutineScope {
        val margemM = precisaoM + folgaAvisoM
        val dLat = margemM / 111_320.0
        val dLon = margemM / (111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.1))

        val zona = Utm.zonaDe(lon)
        val sul = lat < 0
        val pontoUtm = Utm.projetar(lat, lon, zona, sul)
        val gf = org.locationtech.jts.geom.GeometryFactory()
        val pUtm = gf.createPoint(org.locationtech.jts.geom.Coordinate(pontoUtm.easting, pontoUtm.northing))
        val hoje = LocalDate.now().toString()

        val tarefas = camadas.map { camada ->
            async(Dispatchers.IO) {
                val feicoes = buscar(camada.tecnico, lon - dLon, lat - dLat, lon + dLon, lat + dLat)
                    ?: return@async null

                var melhor: Pair<Double, Map<String, String>>? = null
                for ((geom, atributos) in feicoes) {
                    val geomUtm = projetarParaUtm(geom, zona, sul)
                    val d = distanciaAssinadaGeom(geomUtm, pUtm, camada.tipo, camada.raioM)
                    val atual = melhor
                    if (atual == null || d < atual.first) melhor = d to atributos
                }
                val (dist, atributos) = melhor ?: return@async null
                Restricao(
                    camadaNome = camada.nome,
                    fonte = camada.fonte,
                    situacao = ConsultaRestricao.classificar(dist, precisaoM, folgaAvisoM),
                    distanciaBordaM = dist,
                    atributos = atributos,
                    proveniencia = Proveniencia(
                        pacoteVersao = "online",
                        uuidMetadado = null,
                        dataExtracao = hoje,
                        toleranciaSimplificacaoM = 0.0
                    )
                )
            }
        }
        tarefas.mapNotNull { it.await() }.sortedBy { it.distanciaBordaM }
    }

    /** Null em qualquer falha — sem sinal, timeout ou resposta que nao parseia. Caso normal em campo. */
    private fun buscar(
        tecnico: String, minLon: Double, minLat: Double, maxLon: Double, maxLat: Double
    ): List<Pair<org.locationtech.jts.geom.Geometry, Map<String, String>>>? = runCatching {
        val bbox = "%.6f,%.6f,%.6f,%.6f".format(Locale.US, minLon, minLat, maxLon, maxLat)
        val url = URL(
            "$WFS?service=WFS&version=2.0.0&request=GetFeature&typeNames=$tecnico" +
                "&outputFormat=application/json&srsName=EPSG:4674&bbox=$bbox,EPSG:4674"
        )
        val conexao = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 8_000
        }
        val texto = conexao.inputStream.use { it.bufferedReader().readText() }
        GeoJson.paraFeicoes(JSONObject(texto))
    }.getOrNull()
}
