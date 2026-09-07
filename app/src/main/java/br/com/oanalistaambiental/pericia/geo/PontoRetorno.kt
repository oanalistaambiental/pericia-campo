package br.com.oanalistaambiental.pericia.geo

import br.com.oanalistaambiental.pericia.dados.Foto
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ponto de retorno — repeticao fotografica.
 *
 * Guia o perito de volta ao MESMO enquadramento de uma foto anterior: mesma posicao e mesma
 * direcao de camera. Serve para acompanhar recuperacao de area degradada, verificar cumprimento
 * de TAC ou de embargo e comparar estacao seca com chuvosa.
 *
 * O par antes/depois no mesmo enquadramento e uma das imagens mais persuasivas que existem em
 * processo ambiental, e hoje se consegue isso na sorte.
 */
object PontoRetorno {

    data class Orientacao(
        /** Distancia geodesica ate o ponto alvo, em metros. */
        val distanciaM: Float,
        /** Azimute para onde CAMINHAR, 0..360. */
        val rumoGraus: Float,
        /** Quanto girar a camera para reproduzir o enquadramento: negativo = esquerda. */
        val ajusteCameraGraus: Float?,
        val chegou: Boolean,
        val enquadrado: Boolean
    ) {
        fun instrucao(): String = when {
            !chegou -> "Caminhe %.0f m no rumo %.0f°".format(distanciaM, rumoGraus)
            ajusteCameraGraus == null -> "No ponto. Sem bússola para conferir o enquadramento."
            enquadrado -> "No ponto e no enquadramento — pode fotografar."
            ajusteCameraGraus < 0 -> "No ponto. Gire %.0f° para a esquerda.".format(-ajusteCameraGraus)
            else -> "No ponto. Gire %.0f° para a direita.".format(ajusteCameraGraus)
        }
    }

    /**
     * Haversine mais rumo inicial, em Kotlin puro.
     *
     * Substitui Location.distanceBetween de proposito: aquela funcao nao existe na JVM dos
     * testes unitarios, e este calculo e o coracao do ponto de retorno — precisa ser testavel
     * sem aparelho. Erro abaixo de 0,3% nas distancias de campo.
     */
    private fun distanciaERumo(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Pair<Float, Float> {
        val r = 6_371_008.8
        val f1 = Math.toRadians(lat1)
        val f2 = Math.toRadians(lat2)
        val df = f2 - f1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(df / 2) * sin(df / 2) + cos(f1) * cos(f2) * sin(dl / 2) * sin(dl / 2)
        val d = 2 * r * asin(min(1.0, sqrt(a)))
        val y = sin(dl) * cos(f2)
        val x = cos(f1) * sin(f2) - sin(f1) * cos(f2) * cos(dl)
        val rumo = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        return d.toFloat() to rumo.toFloat()
    }

    /** Tolerancias: chega-se ao ponto dentro da precisao do GNSS; enquadra-se com 8°. */
    fun orientar(
        alvo: Foto,
        latAtual: Double,
        lonAtual: Double,
        precisaoM: Float,
        azimuteAtual: Float?
    ): Orientacao = orientar(alvo.lat, alvo.lon, alvo.azimuteGraus, latAtual, lonAtual, precisaoM, azimuteAtual)

    /**
     * Mesma orientacao para um alvo qualquer, nao so para uma foto anterior.
     *
     * Existe porque o perito tambem precisa chegar a uma coordenada que veio de fora — de um
     * auto de infracao, de uma planta, de um memorial descritivo. Nesse caso nao ha
     * enquadramento a reproduzir, so um ponto a alcancar, e [alvoAzimute] vem nulo.
     */
    fun orientar(
        alvoLat: Double,
        alvoLon: Double,
        alvoAzimute: Float?,
        latAtual: Double,
        lonAtual: Double,
        precisaoM: Float,
        azimuteAtual: Float?
    ): Orientacao {
        val (distancia, rumo) = distanciaERumo(latAtual, lonAtual, alvoLat, alvoLon)

        val toleranciaChegada = maxOf(precisaoM, 5f)
        val chegou = distancia <= toleranciaChegada

        val ajuste = if (azimuteAtual != null && alvoAzimute != null) {
            var d = alvoAzimute - azimuteAtual
            while (d > 180f) d -= 360f
            while (d < -180f) d += 360f
            d
        } else null

        return Orientacao(
            distanciaM = distancia,
            rumoGraus = rumo,
            ajusteCameraGraus = ajuste,
            chegou = chegou,
            enquadrado = chegou && ajuste != null && kotlin.math.abs(ajuste) <= 8f
        )
    }
}
