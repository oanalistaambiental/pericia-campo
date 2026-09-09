package br.com.oanalistaambiental.pericia.geo

/**
 * Converte lat/lon para metros locais, para desenhar um mapinha em escala sem depender de
 * nenhum servico de mapa (o app funciona offline por requisito de projeto).
 *
 * Kotlin puro, testavel sem aparelho — mesmo motivo de [Medicao] e [Utm] serem puros.
 */
object PontosLocais {

    data class PontoLocal(val lesteM: Double, val norteM: Double)

    /**
     * Projeta a lista inteira para UTM na MESMA zona e MESMO hemisferio — os do primeiro ponto —
     * e devolve as coordenadas relativas a esse primeiro ponto, em metros.
     *
     * O cuidado de forcar zona e hemisferio e o mesmo de [Medicao.medir]: sem isso, um
     * caminhamento perto de uma divisa de fuso ou do Equador rasga o desenho com um salto de
     * centenas de quilometros entre dois pontos vizinhos.
     */
    fun relativos(pontos: List<Pair<Double, Double>>): List<PontoLocal> {
        if (pontos.isEmpty()) return emptyList()
        val (latRef, lonRef) = pontos.first()
        val zona = Utm.zonaDe(lonRef)
        val sul = latRef < 0
        val ref = Utm.projetar(latRef, lonRef, zona, sul)
        return pontos.map { (lat, lon) ->
            val p = Utm.projetar(lat, lon, zona, sul)
            PontoLocal(p.easting - ref.easting, p.northing - ref.northing)
        }
    }
}

/**
 * Escolha do passo da barra de escala de um mapa: o maior numero "redondo" de metros cujo
 * comprimento em tela nao ultrapasse a largura maxima disponivel.
 *
 * Separado em Kotlin puro pelo mesmo motivo de todo o resto do pacote `geo`: e a conta que
 * decide o que o mapa afirma sobre distancia, e precisa ser testavel sem Compose nem aparelho.
 */
object EscalaMapa {

    private val PASSOS_METROS = doubleArrayOf(
        0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 25.0, 50.0,
        100.0, 200.0, 250.0, 500.0,
        1_000.0, 2_000.0, 5_000.0, 10_000.0, 20_000.0
    )

    /**
     * Maior passo redondo, em metros, tal que passo / [metrosPorPixel] <= [larguraMaximaPx].
     * Abaixo do menor passo da lista (mapa extremamente zoom), devolve o menor mesmo assim —
     * a barra fica menor que o ideal, mas nunca maior que o espaco disponivel.
     */
    fun passoMetros(metrosPorPixel: Double, larguraMaximaPx: Double): Double {
        if (metrosPorPixel <= 0.0 || larguraMaximaPx <= 0.0) return PASSOS_METROS.first()
        var escolhido = PASSOS_METROS.first()
        for (passo in PASSOS_METROS) {
            if (passo / metrosPorPixel <= larguraMaximaPx) escolhido = passo else break
        }
        return escolhido
    }

    /** Texto da barra: metros abaixo de 1 km, quilometros com uma casa acima disso. */
    fun rotulo(metros: Double): String =
        if (metros >= 1000.0) "%.0f km".format(metros / 1000.0) else formatarMetros(metros)

    private fun formatarMetros(m: Double): String =
        if (m == m.toLong().toDouble()) "%.0f m".format(m) else "%.1f m".format(m)
}
