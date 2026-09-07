package br.com.oanalistaambiental.pericia.geo

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Medicao de campo: area por caminhamento e leitura de coordenada digitada.
 *
 * Kotlin puro de proposito. Estas duas contas entram em laudo, entao precisam ser testaveis
 * sem aparelho: uma area errada num auto de infracao e um problema serio, e um erro de leitura
 * de coordenada manda o perito para o lugar errado.
 */
object Medicao {

    private const val RAIO_TERRA_M = 6_371_008.8

    data class Vertice(
        val lat: Double,
        val lon: Double,
        val precisaoM: Float,
        val instante: Long
    )

    data class Poligono(
        val vertices: List<Vertice>,
        /** Area do poligono em metros quadrados. Zero com menos de tres vertices. */
        val areaM2: Double,
        /** Perimetro fechado, em metros. */
        val perimetroM: Double,
        /**
         * Incerteza da area, estimada como perimetro x precisao media do GNSS.
         *
         * E a aproximacao classica por faixa: cada lado pode estar deslocado ate a precisao do
         * receptor, entao a area varia com a area dessa faixa ao redor do contorno. Num laudo,
         * declarar a area sem declarar a incerteza e o tipo de coisa que a defesa desmonta.
         */
        val incertezaAreaM2: Double,
        val zonaUtm: Int
    ) {
        val areaHa: Double get() = areaM2 / 10_000.0
        val incertezaHa: Double get() = incertezaAreaM2 / 10_000.0

        /**
         * A unidade e escolhida UMA vez, pela area, e vale para os dois numeros.
         *
         * Antes cada formatador decidia sozinho e a tela mostrava "1,20 ha" em corpo 44 com
         * "± 2100 m²" logo abaixo — dois numeros da mesma medida em unidades diferentes,
         * lado a lado. E `usarMedicaoComoObservacao` copiava essa mistura para dentro da
         * observacao, que vai queimada na foto, no CSV e no laudo.
         */
        private val emHectares: Boolean get() = areaM2 >= 10_000

        fun areaFormatada(): String = when {
            vertices.size < 3 -> "—"
            emHectares -> "%.2f ha".format(areaHa)
            else -> "%.0f m²".format(areaM2)
        }

        fun incertezaFormatada(): String = when {
            vertices.size < 3 -> "—"
            emHectares -> "± %.2f ha".format(incertezaHa)
            else -> "± %.0f m²".format(incertezaAreaM2)
        }

        fun perimetroFormatado(): String =
            if (perimetroM < 1000) "%.0f m".format(perimetroM) else "%.2f km".format(perimetroM / 1000)

        /** Quanto a incerteza representa da area. Acima de ~20% o numero nao serve para laudo. */
        val incertezaRelativa: Double get() = if (areaM2 > 0) incertezaAreaM2 / areaM2 else 0.0

        fun confiavel(): Boolean = vertices.size >= 3 && incertezaRelativa <= 0.20
    }

    /**
     * Area pela formula do agrimensor (shoelace) sobre coordenadas UTM.
     *
     * Projetar antes de calcular e o que torna a conta correta em metros: sobre lat/lon cru,
     * um grau de longitude vale menos que um grau de latitude e a area sai deformada. Todos os
     * vertices sao projetados na MESMA zona E no MESMO hemisferio — os do primeiro ponto —
     * para que um caminhamento que cruze a divisa de fuso nao produza um salto de 500 km no
     * meio do poligono, nem um que cruze o Equador produza um salto de 10.000 km.
     *
     * O hemisferio forcado corrige um erro de cem mil vezes: sem ele, cada vertice somava (ou
     * nao) o falso-norte conforme o proprio sinal da latitude, e um quadrado de 100 m sobre a
     * linha do Equador saia com 100.146 hectares.
     */
    fun medir(vertices: List<Vertice>): Poligono {
        if (vertices.isEmpty()) return Poligono(vertices, 0.0, 0.0, 0.0, 0)

        val zona = Utm.zonaDe(vertices.first().lon)
        val sul = vertices.first().lat < 0
        val pontos = vertices.map { Utm.projetar(it.lat, it.lon, zona, sul) }

        var soma = 0.0
        for (i in pontos.indices) {
            val a = pontos[i]
            val b = pontos[(i + 1) % pontos.size]
            soma += a.easting * b.northing - b.easting * a.northing
        }
        val area = if (pontos.size >= 3) abs(soma) / 2.0 else 0.0

        var perimetro = 0.0
        if (vertices.size >= 2) {
            for (i in vertices.indices) {
                val a = vertices[i]
                val b = vertices[(i + 1) % vertices.size]
                // Com 2 vertices nao ha volta: o "perimetro" e so a ida.
                if (vertices.size == 2 && i == 1) break
                perimetro += distanciaM(a.lat, a.lon, b.lat, b.lon)
            }
        }

        val precisaoMedia = vertices.map { it.precisaoM.toDouble() }.average()
        val incerteza = if (pontos.size >= 3) perimetro * precisaoMedia else 0.0

        return Poligono(vertices, area, perimetro, incerteza, zona)
    }

    /** Haversine. Mesma conta do ponto de retorno, para os dois nao divergirem. */
    fun distanciaM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val f1 = Math.toRadians(lat1)
        val f2 = Math.toRadians(lat2)
        val df = f2 - f1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(df / 2) * sin(df / 2) + cos(f1) * cos(f2) * sin(dl / 2) * sin(dl / 2)
        return 2 * RAIO_TERRA_M * asin(min(1.0, sqrt(a)))
    }

    /** Rumo inicial de um ponto ao outro, 0..360. */
    fun rumoGraus(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val f1 = Math.toRadians(lat1)
        val f2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(f2)
        val x = cos(f1) * sin(f2) - sin(f1) * cos(f2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    // ------------------------------------------------------------------ coordenada digitada

    data class Leitura(
        val lat: Double,
        val lon: Double,
        val formato: String,
        /** Aviso quando o ponto cai fora do Brasil — quase sempre sinal de sinal trocado. */
        val aviso: String? = null
    )

    private val RE_DECIMAL = Regex(
        """^\s*(-?\d{1,3}(?:[.,]\d+)?)\s*[,;\s]\s*(-?\d{1,3}(?:[.,]\d+)?)\s*$"""
    )

    private val RE_GMS = Regex(
        """^\s*(\d{1,3})\s*[°º:\s]\s*(\d{1,2})\s*['´’:\s]\s*(\d{1,2}(?:[.,]\d+)?)\s*["”\s]*\s*([NSns])""" +
        """\s*[,;\s]\s*(\d{1,3})\s*[°º:\s]\s*(\d{1,2})\s*['´’:\s]\s*(\d{1,2}(?:[.,]\d+)?)\s*["”\s]*\s*([EWOewo])\s*$"""
    )

    private val RE_UTM = Regex(
        """^\s*(\d{1,2})\s*([NSns])\s*[,;\s]\s*(\d+(?:[.,]\d+)?)\s*[Ee]?\s*[,;\s]\s*(\d+(?:[.,]\d+)?)\s*[Nn]?\s*$"""
    )

    private fun num(s: String) = s.replace(',', '.').toDouble()

    /**
     * Interpreta uma coordenada colada pelo perito.
     *
     * Aceita os tres formatos que aparecem de verdade: grau decimal, grau-minuto-segundo com
     * hemisferio, e UTM com zona. Devolve null quando nao reconhece — nunca chuta, porque um
     * palpite aqui manda alguem para o lugar errado.
     */
    fun interpretar(texto: String): Leitura? {
        val t = texto.trim()

        RE_DECIMAL.find(t)?.let { m ->
            val a = num(m.groupValues[1])
            val b = num(m.groupValues[2])
            // Convencao: latitude primeiro. Se o primeiro nao couber em latitude, inverte.
            val (lat, lon) = if (abs(a) <= 90) a to b else b to a
            if (abs(lat) > 90 || abs(lon) > 180) return null
            return Leitura(lat, lon, "grau decimal", avisoForaDoBrasil(lat, lon))
        }

        RE_GMS.find(t)?.let { m ->
            val g = m.groupValues
            var lat = g[1].toDouble() + g[2].toDouble() / 60 + num(g[3]) / 3600
            var lon = g[5].toDouble() + g[6].toDouble() / 60 + num(g[7]) / 3600
            if (g[4].uppercase() == "S") lat = -lat
            if (g[8].uppercase() in setOf("W", "O")) lon = -lon
            if (abs(lat) > 90 || abs(lon) > 180) return null
            return Leitura(lat, lon, "grau, minuto e segundo", avisoForaDoBrasil(lat, lon))
        }

        RE_UTM.find(t)?.let { m ->
            val zona = m.groupValues[1].toInt()
            if (zona !in 1..60) return null
            val sul = m.groupValues[2].uppercase() == "S"
            val e = num(m.groupValues[3])
            val n = num(m.groupValues[4])
            val (lat, lon) = Utm.inverter(e, n, zona, sul)
            if (abs(lat) > 90 || abs(lon) > 180) return null
            return Leitura(lat, lon, "UTM zona ${zona}${if (sul) "S" else "N"}", avisoForaDoBrasil(lat, lon))
        }

        return null
    }

    /** Caixa generosa em volta do Brasil continental. So avisa, nao recusa. */
    private fun avisoForaDoBrasil(lat: Double, lon: Double): String? =
        if (lat in -34.0..6.0 && lon in -74.5..-33.0) null
        else "Este ponto cai fora do Brasil. Confira o sinal: latitude ao sul e negativa, " +
            "longitude a oeste tambem."

    /** Escreve em grau-minuto-segundo, que e como a coordenada vai para muitos laudos. */
    fun formatarGms(lat: Double, lon: Double): String {
        fun parte(v: Double, pos: String, neg: String): String {
            val h = if (v < 0) neg else pos
            val a = abs(v)
            val g = a.toInt()
            val mFloat = (a - g) * 60
            val mi = mFloat.toInt()
            val s = (mFloat - mi) * 60
            return "%d°%02d'%05.2f\"%s".format(g, mi, s, h)
        }
        return "${parte(lat, "N", "S")} ${parte(lon, "E", "W")}"
    }
}
