package br.com.oanalistaambiental.pericia.geo

import br.com.oanalistaambiental.pericia.dados.PontoCaminhamento

/**
 * Distância do trajeto percorrido no MODO VISTORIA — soma dos trechos entre pontos consecutivos,
 * NUNCA fecha de volta ao início (diferença central para [Medicao.medir], que mede uma área
 * fechada). Um caminhamento é um caminho aberto: início e fim são lugares diferentes na maioria
 * das vistorias.
 *
 * Kotlin puro, mesma razão de todo o resto do pacote `geo`: testável sem aparelho.
 */
object Caminhamento {

    /** Soma das distâncias entre pontos consecutivos, na ordem em que foram gravados. */
    fun distanciaTotalM(pontos: List<PontoCaminhamento>): Double {
        if (pontos.size < 2) return 0.0
        var soma = 0.0
        for (i in 0 until pontos.size - 1) {
            val a = pontos[i]
            val b = pontos[i + 1]
            soma += Medicao.distanciaM(a.lat, a.lon, b.lat, b.lon)
        }
        return soma
    }

    /** Duração entre o primeiro e o último ponto, em segundos. Zero com menos de dois pontos. */
    fun duracaoSegundos(pontos: List<PontoCaminhamento>): Long {
        if (pontos.size < 2) return 0
        return (pontos.last().instante - pontos.first().instante) / 1000
    }

    fun distanciaFormatada(metros: Double): String =
        if (metros < 1000) "%.0f m".format(metros) else "%.2f km".format(metros / 1000)

    fun duracaoFormatada(segundos: Long): String {
        val h = segundos / 3600
        val m = (segundos % 3600) / 60
        val s = segundos % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
