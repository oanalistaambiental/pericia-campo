package br.com.oanalistaambiental.pericia.geo

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateFilter
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon
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
    val proveniencia: Proveniencia,
    /**
     * Anel externo do polígono mais próximo, em (lat, lon) — só para desenhar no mapinha de
     * referência. Nulo para camada de ponto (aí [raioCirculoM] manda) e para toda situação FORA,
     * porque extrair e carregar a geometria inteira de uma camada que só serve de contexto
     * distante seria custo sem uso: ninguém desenha contorno de algo a quilômetros.
     */
    val contornoLatLon: List<DoubleArray>? = null,
    /** Raio de influência da camada de ponto, em metros — desenha um círculo em vez de contorno. */
    val raioCirculoM: Double? = null
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
) : AutoCloseable {

    private val gf = GeometryFactory()

    // BUG corrigido: o pacote era reaberto a cada foto e nunca fechado, vazando conexoes SQLite.
    // Agora a instancia e reaproveitada e os metadados sao lidos uma vez so.
    private val camadasCache by lazy { pacote.camadas() }
    private val versaoCache by lazy { pacote.versaoPacote() }

    /**
     * Camadas que existem no pacote mas nao puderam ser consultadas na ultima chamada.
     * Preenchida por `consultar` e lida logo depois: a consulta e sequencial e roda numa
     * corrotina de IO por vez.
     */
    private val camadasComFalha = mutableListOf<String>()

    /** Rotulos das camadas que falharam na ultima consulta, para o app avisar em vez de calar. */
    fun falhasDaUltimaConsulta(): List<String> = camadasComFalha.toList()

    override fun close() = pacote.close()

    fun consultar(ponto: PontoConsulta, incluirFora: Boolean = false): List<Restricao> {
        val versao = versaoCache
        val margemM = ponto.precisaoM + folgaAvisoM

        // bbox em graus, expandido pela margem de erro (nao pelo ponto puro)
        val dLat = margemM / 111_320.0
        val dLon = margemM / (111_320.0 * cos(Math.toRadians(ponto.lat)).coerceAtLeast(0.1))

        // Zona E hemisferio do ponto de consulta, aplicados a tudo que for comparado com ele.
        // Sem o hemisferio forcado, uma unidade de conservacao que cruze o Equador tinha
        // metade da fronteira deslocada 10.000 km: `contains` respondia qualquer coisa, e
        // DENTRO virava FORA sem que nada na tela indicasse problema.
        val zona = Utm.zonaDe(ponto.lon)
        val sul = ponto.lat < 0
        val pontoUtm = Utm.projetar(ponto.lat, ponto.lon, zona, sul)
        val pUtm = gf.createPoint(Coordinate(pontoUtm.easting, pontoUtm.northing))

        val achados = mutableListOf<Restricao>()

        camadasComFalha.clear()
        for (camada in camadasCache) {
            val candidatas = try {
                pacote.candidatas(
                    camada.tabela,
                    ponto.lon - dLon, ponto.lat - dLat,
                    ponto.lon + dLon, ponto.lat + dLat
                )
            } catch (e: Exception) {
                // ANTES: `continue` mudo. Um pacote truncado por copia interrompida abria
                // normalmente, a tela de configuracoes mostrava "7 camadas" em verde, e toda
                // consulta devolvia lista vazia. O perito fotografava dentro de uma APP e nao
                // via alerta nenhum — resultado identico a "fora de qualquer restricao".
                //
                // Ausencia de alerta e falha de consulta sao coisas diferentes e agora dizem
                // isso. Camada que simplesmente nao existe no pacote regional continua sendo
                // caso normal e nao entra na lista.
                if (pacote.temTabela(camada.tabela)) camadasComFalha += camada.nome
                continue
            }

            // Uma feicao so, com coordenada malformada na origem do dado (a projecao ou o
            // calculo de distancia do JTS reclama de coordenada invalida/NaN em vez de so
            // devolver um numero ruim), nao pode travar a consulta inteira e derrubar TODAS as
            // outras camadas que ainda nem foram processadas — mesmo raciocinio da falha de
            // leitura acima: falha localizada vira aviso, nao mata o resto.
            var melhor: Pair<Double, Feicao>? = null
            try {
                for (f in candidatas) {
                    val geomUtm = projetarParaUtm(f.geometria, zona, sul)
                    val d = distanciaAssinadaGeom(geomUtm, pUtm, camada.tipo, camada.raioM)
                    val atual = melhor
                    if (atual == null || d < atual.first) melhor = d to f
                }
            } catch (e: Exception) {
                camadasComFalha += camada.nome
                continue
            }

            val (dist, feicao) = melhor ?: continue
            val situacao = classificar(dist, ponto.precisaoM)
            // Fora e informacao de baixo valor em campo e polui a tela; fica disponivel para o
            // laudo, mas so entra na lista quando explicitamente pedido.
            if (situacao == Situacao.FORA && !incluirFora) continue

            // So vale desenhar contorno/circulo de quem esta perto o bastante para importar.
            val relevante = situacao != Situacao.FORA
            achados += Restricao(
                camadaNome = camada.nome,
                fonte = camada.fonte,
                situacao = situacao,
                distanciaBordaM = dist,
                atributos = feicao.atributos,
                proveniencia = Proveniencia(versao, camada.uuid, camada.dataExtracao, camada.toleranciaM),
                contornoLatLon = if (relevante && camada.tipo == "poligono") anelExternoLatLon(feicao.geometria) else null,
                raioCirculoM = if (relevante && camada.tipo == "ponto") camada.raioM else null
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
    fun classificar(distanciaM: Double, precisaoM: Float): Situacao =
        classificar(distanciaM, precisaoM, folgaAvisoM)

    /** Camadas presentes no pacote instalado — usado na tela de configuracoes. */
    fun camadasInstaladas(): List<CamadaInfo> = camadasCache

    fun versaoDoPacote(): String = versaoCache

    companion object {
        fun abrir(arquivoGpkg: File, folgaAvisoM: Double = 50.0): ConsultaRestricao =
            ConsultaRestricao(GeoPacote(arquivoGpkg), folgaAvisoM)

        /**
         * Funcao pura, sem banco: e o coracao do produto e precisa ser testavel sozinha.
         *
         * DENTRO exige que o ponto E toda a margem de erro estejam dentro do poligono.
         * FORA exige que estejam fora, com folga. Todo o resto e PROXIMO_AO_LIMITE — e
         * dizer "indefinido" quando a medida nao permite afirmar e justamente o que separa
         * um instrumento de pericia de um aplicativo de consumo.
         */
        fun classificar(distanciaM: Double, precisaoM: Float, folgaAvisoM: Double = 50.0): Situacao = when {
            distanciaM < -precisaoM -> Situacao.DENTRO
            distanciaM > precisaoM + folgaAvisoM -> Situacao.FORA
            else -> Situacao.PROXIMO_AO_LIMITE
        }
    }
}

/**
 * Negativo dentro, positivo fora. Camada de pontos usa o raio de influencia declarado.
 *
 * Extraida de [ConsultaRestricao] para [ConsultaOnline] reusar — a mesma conta vale para uma
 * geometria vinda do pacote offline ou de uma resposta do WFS ao vivo, e duplicar era o tipo de
 * coisa que ja divergiu neste projeto antes (ver `Utm.formatado()` do enquadra-mg antigo).
 */
internal fun distanciaAssinadaGeom(
    geom: Geometry, ponto: org.locationtech.jts.geom.Point, tipo: String, raioM: Double?
): Double {
    // Ponto e linha nao tem "dentro": so existe estar mais perto ou mais longe. Confundir os
    // dois com poligono faria uma linha (trecho de rio, p.ex.) usar `.boundary` — que para uma
    // linha sao so as DUAS pontas — e quase sempre reportar "fora" mesmo em cima do trecho.
    if (tipo == "ponto" || tipo == "linha") {
        return geom.distance(ponto) - (raioM ?: 0.0)
    }
    val bruta = geom.boundary.distance(ponto)
    return if (geom.contains(ponto)) -bruta else bruta
}

/**
 * Anel externo do poligono, em (lat, lon), pronto para um `Polygon` do osmdroid.
 *
 * A geometria de origem esta em lon/lat (x=lon, y=lat — convencao do GeoPackage e do GeoJSON),
 * por isso a troca de ordem aqui. MultiPoligono usa so a maior parte por area: e o desenho de
 * referencia visual do laudo, nao o poligono oficial para calculo, e mostrar so um fragmento
 * pequeno confundiria mais do que ajudaria.
 */
internal fun anelExternoLatLon(geom: Geometry): List<DoubleArray>? {
    val poligono = when (geom) {
        is Polygon -> geom
        is MultiPolygon -> (0 until geom.numGeometries)
            .map { geom.getGeometryN(it) as Polygon }
            .maxByOrNull { it.area }
        else -> null
    } ?: return null
    return poligono.exteriorRing.coordinates.map { doubleArrayOf(it.y, it.x) }
}

/** Reprojeta uma geometria em lon/lat (x=lon, y=lat, convencao do GeoPackage e do GeoJSON) para UTM. */
internal fun projetarParaUtm(geom: Geometry, zona: Int, sul: Boolean): Geometry {
    val copia = geom.copy()
    copia.apply(CoordinateFilter { c ->
        val p = Utm.projetar(c.y, c.x, zona, sul)
        c.x = p.easting
        c.y = p.northing
    })
    copia.geometryChanged()
    return copia
}
