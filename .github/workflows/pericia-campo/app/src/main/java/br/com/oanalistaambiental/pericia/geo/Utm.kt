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
        /** Ex.: "23K 612345E 7801234N" */
        fun formatado(): String {
            val h = if (hemisferioSul) "S" else "N"
            return "%dZ%s %.0fE %.0fN".format(zona, h, easting, northing)
        }
    }

    fun zonaDe(lonGraus: Double): Int = floor((lonGraus + 180.0) / 6.0).toInt() + 1

    /** Longitude do meridiano central da zona, em graus. */
    fun meridianoCentral(zona: Int): Double = (zona - 1) * 6.0 - 180.0 + 3.0

    /**
     * Projeta lat/lon (graus, SIRGAS 2000) para UTM.
     * [zonaForcada] permite manter varios pontos na MESMA zona, o que e necessario quando se
     * mede distancia entre geometrias proximas a uma divisa de fuso.
     */
    fun projetar(latGraus: Double, lonGraus: Double, zonaForcada: Int? = null): Coordenada {
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

        val sul = latGraus < 0
        if (sul) northing += FALSE_NORTHING

        return Coordenada(easting, northing, zona, sul)
    }
}
