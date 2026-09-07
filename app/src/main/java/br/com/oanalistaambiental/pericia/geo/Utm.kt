package br.com.oanalistaambiental.pericia.geo

import kotlin.math.*

/**
 * Conversao geografica -> UTM sobre o elipsoide GRS80, que e o elipsoide do SIRGAS 2000.
 *
 * Feita localmente, sem servico externo e sem biblioteca de projecao, conforme a decisao
 * tecnica do projeto. Serve a dois propositos no app:
 *   1. Exibir a coordenada em UTM SIRGAS2000 na legenda da foto (padrao oficial brasileiro).
 *   2. Dar uma base metrica local para medir distancia ate a borda de um poligono de restricao.
 */
object Utm {

    private const val A = 6378137.0                 // semieixo maior GRS80
    private const val F = 1.0 / 298.257222101       // achatamento GRS80
    private const val K0 = 0.9996
    private const val FALSE_EASTING = 500000.0
    private const val FALSE_NORTHING = 10000000.0   // hemisferio sul

    private val E2 = 2 * F - F * F
    private val EP2 = E2 / (1 - E2)

    data class Coordenada(
        val easting: Double,
        val northing: Double,
        val zona: Int,
        val hemisferioSul: Boolean
    ) {
        /** Ex.: "23S 612345E 7801234N" — zona, hemisferio, easting, northing. */
        fun formatado(): String {
            val h = if (hemisferioSul) "S" else "N"
            // Locale.US: coordenada UTM com virgula decimal, ou com ponto de milhar herdado do
            // locale do aparelho, deixa de ser copiavel para qualquer outro sistema.
            return "%d%s %.0fE %.0fN".format(java.util.Locale.US, zona, h, easting, northing)
        }
    }

    /**
     * Zona de 1 a 60. O `coerceIn` nao e paranoia: `zonaDe(180.0)` dava 61, uma zona que nao
     * existe, com meridiano central em 183 graus — e nada validava o resultado antes de
     * exibi-lo como se fosse coordenada legitima.
     */
    fun zonaDe(lonGraus: Double): Int =
        (floor((lonGraus + 180.0) / 6.0).toInt() + 1).coerceIn(1, 60)

    /** Longitude do meridiano central da zona, em graus. */
    fun meridianoCentral(zona: Int): Double = (zona - 1) * 6.0 - 180.0 + 3.0

    /**
     * Projeta lat/lon (graus, SIRGAS 2000) para UTM.
     *
     * [zonaForcada] permite manter varios pontos na MESMA zona, o que e necessario quando se
     * mede distancia entre geometrias proximas a uma divisa de fuso.
     *
     * [hemisferioSulForcado] existe pelo mesmo motivo, e a falta dele era um erro grave.
     * O falso-norte de 10.000.000 m e somado quando a latitude e negativa. Forcando so a zona,
     * cada vertice de um mesmo poligono decidia o hemisferio pelo proprio sinal: um poligono
     * cruzando o Equador ficava com metade dos vertices deslocados 10.000 km em relacao a
     * outra metade. Um quadrado de 100 m x 100 m sobre a linha, em Macapa, devolvia
     * 100.146 hectares em vez de 0,98 — cem mil vezes maior, sem nenhum aviso. Num auto de
     * infracao isso e a diferenca entre um hectare e um estado.
     */
    fun projetar(
        latGraus: Double,
        lonGraus: Double,
        zonaForcada: Int? = null,
        hemisferioSulForcado: Boolean? = null
    ): Coordenada {
        val zona = zonaForcada ?: zonaDe(lonGraus)
        val lat = Math.toRadians(latGraus)
        val lon = Math.toRadians(lonGraus)
        val lon0 = Math.toRadians(meridianoCentral(zona))

        val sinLat = sin(lat)
        val cosLat = cos(lat)
        val tanLat = tan(lat)

        val n = A / sqrt(1 - E2 * sinLat * sinLat)
        val t = tanLat * tanLat
        val c = EP2 * cosLat * cosLat
        val a1 = (lon - lon0) * cosLat

        val m = A * (
            (1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2 * E2 * E2 / 256) * lat -
            (3 * E2 / 8 + 3 * E2 * E2 / 32 + 45 * E2 * E2 * E2 / 1024) * sin(2 * lat) +
            (15 * E2 * E2 / 256 + 45 * E2 * E2 * E2 / 1024) * sin(4 * lat) -
            (35 * E2 * E2 * E2 / 3072) * sin(6 * lat)
        )

        val easting = FALSE_EASTING + K0 * n * (
            a1 + (1 - t + c) * a1.pow(3) / 6 +
            (5 - 18 * t + t * t + 72 * c - 58 * EP2) * a1.pow(5) / 120
        )

        var northing = K0 * (
            m + n * tanLat * (
                a1 * a1 / 2 + (5 - t + 9 * c + 4 * c * c) * a1.pow(4) / 24 +
                (61 - 58 * t + t * t + 600 * c - 330 * EP2) * a1.pow(6) / 720
            )
        )

        val sul = hemisferioSulForcado ?: (latGraus < 0)
        if (sul) northing += FALSE_NORTHING

        return Coordenada(easting, northing, zona, sul)
    }

    /**
     * Caminho inverso: UTM -> lat/lon em graus.
     *
     * Existe por dois motivos de campo. Primeiro, o perito recebe coordenada em UTM com muito
     * mais frequencia do que em grau decimal — vem assim em auto de infracao, em planta e em
     * memorial descritivo — e precisa navegar ate ela. Segundo, permite conferir a ida contra a
     * volta: se projetar e desprojetar nao devolve o mesmo ponto, ha erro no calculo.
     */
    fun inverter(easting: Double, northing: Double, zona: Int, hemisferioSul: Boolean): Pair<Double, Double> {
        val x = easting - FALSE_EASTING
        val y = northing - (if (hemisferioSul) FALSE_NORTHING else 0.0)

        val e1 = (1 - sqrt(1 - E2)) / (1 + sqrt(1 - E2))
        val m = y / K0
        val mu = m / (A * (1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2 * E2 * E2 / 256))

        val phi1 = mu +
            (3 * e1 / 2 - 27 * e1.pow(3) / 32) * sin(2 * mu) +
            (21 * e1 * e1 / 16 - 55 * e1.pow(4) / 32) * sin(4 * mu) +
            (151 * e1.pow(3) / 96) * sin(6 * mu) +
            (1097 * e1.pow(4) / 512) * sin(8 * mu)

        val sinP = sin(phi1)
        val cosP = cos(phi1)
        val tanP = tan(phi1)

        val c1 = EP2 * cosP * cosP
        val t1 = tanP * tanP
        val n1 = A / sqrt(1 - E2 * sinP * sinP)
        val r1 = A * (1 - E2) / (1 - E2 * sinP * sinP).pow(1.5)
        val d = x / (n1 * K0)

        val lat = phi1 - (n1 * tanP / r1) * (
            d * d / 2 -
            (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * EP2) * d.pow(4) / 24 +
            (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * EP2 - 3 * c1 * c1) * d.pow(6) / 720
        )

        val lon0 = Math.toRadians(meridianoCentral(zona))
        val lon = lon0 + (
            d -
            (1 + 2 * t1 + c1) * d.pow(3) / 6 +
            (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * EP2 + 24 * t1 * t1) * d.pow(5) / 120
        ) / cosP

        return Math.toDegrees(lat) to Math.toDegrees(lon)
    }
}
