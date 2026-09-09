package br.com.oanalistaambiental.pericia.geo

import kotlin.math.abs

/**
 * Leitura da coordenada digitada ou colada.
 *
 * A coordenada que chega ao perito quase nunca esta em grau decimal: vem em UTM no auto de
 * infracao e na planta, e em grau-minuto-segundo no memorial descritivo. Obrigar a converter
 * a mao antes de usar o app e onde se perde tempo e onde se erra.
 *
 * Mesma implementacao do aplicativo de campo, de proposito: os dois precisam concordar sobre
 * onde fica um ponto.
 */
object Coordenadas {

    data class Leitura(
        val lat: Double,
        val lon: Double,
        val formato: String,
        /** Aviso quando o ponto cai fora do Brasil — quase sempre sinal esquecido. */
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

    /** Devolve null quando nao reconhece. Nunca chuta: um palpite aqui muda o enquadramento. */
    fun interpretar(texto: String): Leitura? {
        val t = texto.trim()

        RE_DECIMAL.find(t)?.let { m ->
            val a = num(m.groupValues[1])
            val b = num(m.groupValues[2])
            val (lat, lon) = if (abs(a) <= 90) a to b else b to a
            if (abs(lat) > 90 || abs(lon) > 180) return null
            return Leitura(lat, lon, "grau decimal", aviso(lat, lon))
        }

        RE_GMS.find(t)?.let { m ->
            val g = m.groupValues
            var lat = g[1].toDouble() + g[2].toDouble() / 60 + num(g[3]) / 3600
            var lon = g[5].toDouble() + g[6].toDouble() / 60 + num(g[7]) / 3600
            if (g[4].uppercase() == "S") lat = -lat
            if (g[8].uppercase() in setOf("W", "O")) lon = -lon
            if (abs(lat) > 90 || abs(lon) > 180) return null
            return Leitura(lat, lon, "grau, minuto e segundo", aviso(lat, lon))
        }

        RE_UTM.find(t)?.let { m ->
            val zona = m.groupValues[1].toInt()
            if (zona !in 1..60) return null
            val sul = m.groupValues[2].uppercase() == "S"
            val (lat, lon) = Utm.inverter(num(m.groupValues[3]), num(m.groupValues[4]), zona, sul)
            if (abs(lat) > 90 || abs(lon) > 180) return null
            return Leitura(lat, lon, "UTM zona ${zona}${if (sul) "S" else "N"}", aviso(lat, lon))
        }

        return null
    }

    private fun aviso(lat: Double, lon: Double): String? =
        if (lat in -34.0..6.0 && lon in -74.5..-33.0) null
        else "Este ponto cai fora do Brasil. Confira o sinal: latitude ao sul e negativa, " +
            "longitude a oeste tambem."

    fun formatarGms(lat: Double, lon: Double): String {
        fun parte(v: Double, pos: String, neg: String): String {
            val h = if (v < 0) neg else pos
            val a = abs(v)
            val g = a.toInt()
            val mFloat = (a - g) * 60
            val mi = mFloat.toInt()
            return "%d°%02d'%05.2f\"%s".format(g, mi, (mFloat - mi) * 60, h)
        }
        return "${parte(lat, "N", "S")} ${parte(lon, "E", "W")}"
    }
}
