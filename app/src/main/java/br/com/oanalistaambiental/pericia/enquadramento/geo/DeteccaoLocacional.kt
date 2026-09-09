package br.com.oanalistaambiental.pericia.enquadramento.geo

import br.com.oanalistaambiental.pericia.geo.Utm
import br.com.oanalistaambiental.pericia.geo.GeoPacote
import br.com.oanalistaambiental.pericia.enquadramento.norma.CriterioLocacional
import br.com.oanalistaambiental.pericia.enquadramento.norma.FiltroAtributo
import br.com.oanalistaambiental.pericia.enquadramento.norma.FatorRestricao
import br.com.oanalistaambiental.pericia.enquadramento.norma.Regras
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateFilter
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import java.io.File
import kotlin.math.cos

/**
 * Sugestão automática dos critérios locacionais a partir de um ponto.
 *
 * O art. 6º, §5º da própria DN 217 manda o empreendedor consultar o IDE-Sisema para verificar
 * a incidência dos critérios das Tabelas 4 e 5. É exatamente o mesmo pacote GeoPackage que o
 * aplicativo de campo usa — um único pacote serve aos dois.
 *
 * A palavra "sugestão" é literal: o app aponta, quem decide é quem assina.
 */
class DeteccaoLocacional(private val pacote: GeoPacote) : AutoCloseable {

    private val gf = GeometryFactory()

    data class Incidencia(
        val criterios: List<CriterioLocacional>,
        val fatores: List<FatorRestricao>,
        val camadasNaoInstaladas: List<String>,
        val versaoPacote: String,
        /**
         * A geometria bate, mas o pacote nao traz o atributo que separa este criterio de
         * outro que usa a mesma camada. O app nao pode afirmar nem descartar: marca A
         * CONFERIR e devolve a decisao a quem assina, que e a unica resposta honesta.
         */
        val aConferir: List<CriterioLocacional> = emptyList(),
        /**
         * Camadas que existem no pacote mas nao puderam ser consultadas — arquivo truncado,
         * indice R-tree ausente, esquema mudado. NAO e o mesmo que "nao incide", e antes as
         * duas coisas chegavam ao usuario como a mesma coisa: silencio.
         */
        val camadasComFalha: List<String> = emptyList()
    )

    /** Resultado de testar uma camada contra o ponto. */
    private enum class Toque { NAO, SIM, INDEFINIDO }

    /**
     * [margemM] amplia a busca. Diferente do aplicativo de campo, aqui não há GNSS ao vivo:
     * o ponto costuma vir de coordenada digitada, então a margem representa a incerteza
     * daquela coordenada, e o padrão é conservador.
     */
    fun verificar(regras: Regras, lat: Double, lon: Double, margemM: Double = 30.0): Incidencia {
        val zona = Utm.zonaDe(lon)
        val sul = lat < 0
        val p = Utm.projetar(lat, lon, zona, sul)
        val ponto = gf.createPoint(Coordinate(p.easting, p.northing))

        val infos = runCatching { pacote.camadas().associateBy { it.tabela } }.getOrDefault(emptyMap())
        val ausentes = mutableListOf<String>()
        val comFalha = mutableListOf<String>()

        fun intercepta(camada: String?, filtro: FiltroAtributo?): Toque {
            if (camada == null) return Toque.NAO
            val info = infos[camada] ?: run { ausentes += camada; return Toque.NAO }

            // BUG corrigido: o alcance era fixo em 30 m e ignorava o raio gravado na camada.
            // Cavidade, por exemplo, é guardada como PONTO com raio de influência de 250 m —
            // com o alcance fixo, o critério deixaria de ser detectado. Subdetectar critério
            // locacional reduz a modalidade, que é o lado perigoso do erro.
            val alcance = margemM + info.toleranciaM + (info.raioM ?: 0.0)
            val dLat = alcance / 111_320.0
            val dLon = alcance / (111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.1))

            // Falha de consulta deixa de ser lida como "nao incide". Antes o runCatching
            // devolvia lista vazia e o criterio era reportado como ausente — a direcao
            // perigosa do erro, porque subdetectar criterio locacional REDUZ a modalidade.
            val candidatas = runCatching {
                pacote.candidatas(camada, lon - dLon, lat - dLat, lon + dLon, lat + dLat)
            }.getOrElse { comFalha += camada; return Toque.INDEFINIDO }

            val tocadas = candidatas.filter { f ->
                val g = projetar(f.geometria, zona, sul)
                g.contains(ponto) || g.distance(ponto) <= alcance
            }
            if (tocadas.isEmpty()) return Toque.NAO
            if (filtro == null) return Toque.SIM

            var houveIndefinido = false
            for (f in tocadas) {
                when (avaliarFiltro(f.atributos, filtro)) {
                    Toque.SIM -> return Toque.SIM
                    Toque.INDEFINIDO -> houveIndefinido = true
                    Toque.NAO -> Unit
                }
            }
            return if (houveIndefinido) Toque.INDEFINIDO else Toque.NAO
        }

        val criterios = mutableListOf<CriterioLocacional>()
        val aConferir = mutableListOf<CriterioLocacional>()
        for (c in regras.criterios.filter { it.automatico }) {
            when (intercepta(c.camada, c.filtro)) {
                Toque.SIM -> criterios += c
                Toque.INDEFINIDO -> aConferir += c
                Toque.NAO -> Unit
            }
        }
        val fatores = regras.fatores.filter {
            it.automatico && intercepta(it.camada, null) == Toque.SIM
        }

        return Incidencia(
            criterios = criterios,
            fatores = fatores,
            camadasNaoInstaladas = ausentes.distinct(),
            versaoPacote = runCatching { pacote.versaoPacote() }.getOrDefault("desconhecida"),
            aConferir = aConferir,
            camadasComFalha = comFalha.distinct()
        )
    }

    /**
     * Aplica o filtro aos atributos da feicao.
     *
     * INDEFINIDO quando nenhuma das colunas esperadas existe no pacote. Nao da para escolher
     * entre SIM e NAO sem inventar: SIM aplicaria a APA um criterio que a norma exclui, NAO
     * deixaria passar uma UC de protecao integral. O app diz que nao sabe.
     */
    private fun avaliarFiltro(atributos: Map<String, String>, filtro: FiltroAtributo): Toque {
        val porChave = atributos.entries.associate { normalizar(it.key) to normalizar(it.value) }
        val valores = filtro.campos.mapNotNull { porChave[normalizar(it)] }.filter { it.isNotBlank() }
        if (valores.isEmpty()) return Toque.INDEFINIDO

        val excluido = valores.any { v ->
            filtro.naoContem.any { v.contains(normalizar(it)) } ||
                filtro.naoIgual.any { v == normalizar(it) }
        }
        if (excluido) return Toque.NAO

        if (filtro.contem.isEmpty()) return Toque.SIM
        val aceito = valores.any { v -> filtro.contem.any { v.contains(normalizar(it)) } }
        return if (aceito) Toque.SIM else Toque.NAO
    }

    /** Minuscula, sem acento e com espacos colapsados: o pacote nao padroniza nada disso. */
    internal fun normalizar(s: String): String =
        java.text.Normalizer.normalize(s.trim().lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\s+"), " ")

    private fun projetar(geom: Geometry, zona: Int, sul: Boolean): Geometry {
        val copia = geom.copy()
        copia.apply(CoordinateFilter { c ->
            val q = Utm.projetar(c.y, c.x, zona, sul)   // GeoPackage guarda x=lon, y=lat
            c.x = q.easting
            c.y = q.northing
        })
        copia.geometryChanged()
        return copia
    }

    /** Camadas declaradas no pacote — usado para conferir um arquivo antes de instala-lo. */
    fun camadasDoPacote(): List<String> =
        runCatching { pacote.camadas().map { it.tabela } }.getOrDefault(emptyList())

    override fun close() = pacote.close()

    companion object {
        fun abrir(arquivo: File): DeteccaoLocacional = DeteccaoLocacional(GeoPacote(arquivo))
    }
}
