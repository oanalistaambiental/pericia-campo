package br.com.oanalistaambiental.pericia.captura

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2

/**
 * Geometria da orientacao do aparelho, separada do sensor.
 *
 * Kotlin puro, recebendo a matriz de rotacao 3x3 ja pronta. Fica assim para ser testavel sem
 * aparelho: sao tres contas curtas das quais dependem o azimute queimado na foto, a bussola da
 * tela e o clinometro — e um erro de sinal aqui vira um laudo com a direcao errada.
 *
 * Convencao da matriz (a mesma do SensorManager.getRotationMatrix): R leva coordenadas do
 * aparelho para o mundo, com X leste, Y norte e Z para cima. As colunas de R sao os eixos do
 * aparelho escritos no mundo, entao a terceira coluna — R[2], R[5], R[8] — e o eixo Z do
 * aparelho, que sai pela TELA. A camera traseira aponta para o oposto disso.
 */
object Orientacoes {

    /**
     * Azimute para onde a CAMERA aponta, 0..360, ou null quando o aparelho esta quase deitado.
     *
     * BUG corrigido — e dos graves, porque contamina a prova.
     *
     * O codigo anterior usava direto o azimute de `getOrientation`, que e a direcao do TOPO do
     * aparelho. Isso so coincide com a direcao da camera quando o celular esta deitado com a
     * tela para cima. Fotografando em pe, que e como se fotografa, o topo aponta para o ceu:
     * o azimute fica instavel, gira sozinho e vai errado para a legenda queimada na foto, para
     * o laudo, para o CSV e para o KML. Aqui a direcao e tirada do eixo da camera.
     *
     * Devolve null quando a camera aponta para muito perto do zenite ou do nadir — nesse caso
     * nao existe direcao horizontal definida, e inventar um numero seria pior que admitir.
     */
    fun azimuteCameraGraus(r: FloatArray): Float? {
        // Eixo da camera no mundo: oposto do eixo Z do aparelho.
        val x = -r[2].toDouble()
        val y = -r[5].toDouble()
        val z = -r[8].toDouble()

        val horizontal = kotlin.math.sqrt(x * x + y * y)
        if (horizontal < 0.10) return null   // ~84 graus de elevacao: sem rumo utilizavel

        // atan2(leste, norte) da o azimute medido do norte, no sentido horario.
        return ((Math.toDegrees(atan2(x, y)) + 360.0) % 360.0).toFloat()
    }

    /**
     * Elevacao do eixo da camera: 0 na horizontal, positivo apontando para cima,
     * -90 com o aparelho deitado de tela para cima (camera olhando o chao).
     */
    fun elevacaoCameraGraus(r: FloatArray): Float {
        val z = (-r[8]).toDouble().coerceIn(-1.0, 1.0)
        return Math.toDegrees(asin(z)).toFloat()
    }

    /**
     * Inclinacao do PLANO das costas do aparelho em relacao ao horizonte, de 0 a 90 graus.
     *
     * E a leitura do clinometro: encosta-se as costas do celular no talude e le-se aqui.
     * Nao tem sinal, entao nao ha como inverter por engano — 0 e piso, 90 e parede.
     */
    fun inclinacaoSuperficieGraus(r: FloatArray): Float {
        val cos = abs(r[8]).toDouble().coerceIn(0.0, 1.0)
        return Math.toDegrees(acos(cos)).toFloat()
    }

    /** Declividade em porcentagem, que e como talude e rampa aparecem em laudo. */
    fun declividadePercent(graus: Float): Float =
        (kotlin.math.tan(Math.toRadians(abs(graus).toDouble())) * 100).toFloat()

    /** Menor angulo entre dois azimutes, cuidando da volta em 360 graus. */
    fun diferencaAngular(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    /** Rosa dos ventos de 8 pontas. */
    fun rosa(azimute: Float): String {
        val dir = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        return dir[(((azimute + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)]
    }
}
