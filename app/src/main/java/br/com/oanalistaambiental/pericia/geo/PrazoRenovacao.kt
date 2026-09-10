package br.com.oanalistaambiental.pericia.geo

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Prazo de protocolo da renovacao de licenca — art. 12 da DN COPAM 217/2017.
 *
 * A norma exige que o pedido de renovacao seja protocolado com antecedencia minima de 120 dias
 * do termino do prazo de validade da licenca. Passado esse prazo sem protocolo, a licenca vence
 * antes de a renovacao ser decidida — e e exatamente esse esquecimento que o calendario de
 * obrigacoes do projeto pretende evitar (a peca de maior retorno, segundo os proprios docs).
 *
 * Kotlin puro, testavel sem aparelho: e so aritmetica de data, mas e a aritmetica que decide se
 * um empreendimento fica sem licenca valida por atraso de protocolo.
 */
object PrazoRenovacao {

    const val ANTECEDENCIA_DIAS = 120L

    data class Resultado(
        /** Ultimo dia para protocolar a renovacao, sem estourar a antecedencia minima. */
        val dataLimiteProtocolo: LocalDate,
        /** Positivo: dias ate o limite. Negativo: dias desde que o limite passou. */
        val diasRestantes: Long
    ) {
        val prazoVencido: Boolean get() = diasRestantes < 0
        /** Janela de atencao: dentro de 30 dias do limite, sem ainda ter estourado. */
        val proximoDoLimite: Boolean get() = diasRestantes in 0..30
    }

    fun calcular(validadeLicenca: LocalDate, hoje: LocalDate = LocalDate.now()): Resultado {
        val limite = validadeLicenca.minusDays(ANTECEDENCIA_DIAS)
        return Resultado(limite, ChronoUnit.DAYS.between(hoje, limite))
    }
}
