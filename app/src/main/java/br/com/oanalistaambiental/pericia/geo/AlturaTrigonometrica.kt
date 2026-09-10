package br.com.oanalistaambiental.pericia.geo

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.tan

/**
 * Altura de arvore, talude, chamine ou marca de cheia por trigonometria: distancia horizontal
 * ate a base + angulo de elevacao ate o topo. O motor do angulo ja existe
 * (`captura/Orientacoes.elevacaoCameraGraus`) — faltava so esta conta e a tela.
 *
 * Kotlin puro, testavel sem aparelho — a mesma razao de [Medicao] e [Utm].
 */
object AlturaTrigonometrica {

    /** Incerteza padrao de uma leitura de angulo por sensor de celular, em graus. */
    const val INCERTEZA_ANGULO_GRAUS = 1.0

    data class Resultado(val alturaM: Double, val incertezaM: Double)

    /**
     * [distanciaHorizontalM] do observador ate a base do objeto medido.
     * [anguloGraus] elevacao ate o topo, medida a partir de onde o observador segura o aparelho.
     * [alturaObservadorM] soma-se ao resultado — a tangente mede acima do instrumento, nao do
     * chao onde o observador pisa.
     *
     * A INCERTEZA cresce com a distancia: um grau de erro no angulo vira pouco a 5 m e muito a
     * 50 m — o mesmo aviso que a medicao de area ja da sobre a precisao do GNSS, agora para o
     * clinometro. Omitir isso e o tipo de coisa que a defesa desmonta num laudo.
     */
    fun calcular(
        distanciaHorizontalM: Double,
        anguloGraus: Double,
        alturaObservadorM: Double = 1.5
    ): Resultado {
        val anguloRad = Math.toRadians(anguloGraus)
        val alturaAcimaDoObservador = distanciaHorizontalM * tan(anguloRad)
        val altura = alturaAcimaDoObservador + alturaObservadorM

        // Propagacao de incerteza de primeira ordem: d(altura)/d(angulo) = distancia / cos^2(angulo).
        val incertezaRad = Math.toRadians(INCERTEZA_ANGULO_GRAUS)
        val cosAngulo = cos(anguloRad)
        val incerteza = distanciaHorizontalM * incertezaRad / (cosAngulo * cosAngulo)

        return Resultado(altura, abs(incerteza))
    }

    /**
     * Metodo dos DOIS angulos: mira-se a BASE do objeto (zera ali) e depois o TOPO, e a altura
     * sai da diferenca entre as duas tangentes — sem precisar estimar a altura de onde o
     * observador segura o aparelho.
     *
     * Por que isto e melhor que somar 1,5 m: a aproximacao de [calcular] so vale quando a base
     * do objeto esta no mesmo nivel dos pes do observador. Numa encosta, numa vala ou olhando de
     * cima de um talude, a base fica acima ou abaixo do observador, e a soma fixa erra pelo
     * desnivel inteiro. Mirar a base de verdade elimina esse erro, ao custo de uma segunda
     * leitura de angulo.
     *
     * [anguloBaseGraus] pode ser negativo (base abaixo da linha do horizonte, olhando para
     * baixo) — a formula nao assume sinal.
     */
    fun calcularDuploAngulo(
        distanciaHorizontalM: Double,
        anguloBaseGraus: Double,
        anguloTopoGraus: Double
    ): Resultado {
        val baseRad = Math.toRadians(anguloBaseGraus)
        val topoRad = Math.toRadians(anguloTopoGraus)
        val altura = distanciaHorizontalM * (tan(topoRad) - tan(baseRad))

        // Duas leituras independentes, cada uma com a mesma incerteza de sensor — soma-se em
        // quadratura (RSS), nao direto: a chance de as duas leituras errarem juntas NO MESMO
        // sentido e menor que a de uma so errar.
        val incertezaRad = Math.toRadians(INCERTEZA_ANGULO_GRAUS)
        val cosBase = cos(baseRad)
        val cosTopo = cos(topoRad)
        val dBase = distanciaHorizontalM * incertezaRad / (cosBase * cosBase)
        val dTopo = distanciaHorizontalM * incertezaRad / (cosTopo * cosTopo)
        val incerteza = kotlin.math.sqrt(dBase * dBase + dTopo * dTopo)

        return Resultado(altura, abs(incerteza))
    }
}
