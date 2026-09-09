package br.com.oanalistaambiental.pericia.enquadramento.norma

import java.text.NumberFormat
import java.util.Locale

/**
 * Leitura do numero que o usuario digita no campo de porte.
 *
 * BUG GRAVE que este arquivo existe para corrigir. O codigo antigo fazia:
 *
 *     valor.replace(".", "").replace(',', '.').toDoubleOrNull()
 *
 * O ponto era removido SEMPRE, tratado como separador de milhar. Quem digitasse `3.5` para
 * dizer 3,5 hectares obtinha 35. Na atividade A-03-01-9 (limites 3,0 / 5,0 ha) isso levava o
 * empreendimento de porte MEDIO — classe 3, LAS/RAS — para porte GRANDE — classe 4, LAC1.
 * Dois degraus de modalidade, para cima, sem nada na tela indicando que houve leitura errada.
 *
 * Pior: o campo usava `KeyboardType.Number`, que na maioria dos teclados Android nao oferece
 * virgula. O unico caractere decimal alcancavel era justamente o que o codigo destruia.
 *
 * A solucao aqui NAO e adivinhar. `1.500` pode ser mil e quinhentos ou um e meio, e nenhuma
 * heuristica acerta sempre — errar em silencio num numero que decide modalidade de
 * licenciamento e inaceitavel. Entao o campo passa a aceitar SO digitos e UM separador
 * decimal, sem separador de milhar, e a tela devolve o numero por extenso para conferencia.
 * Quem digita ve o que o app entendeu antes de seguir.
 */
object Numeros {

    /**
     * Filtra o que o usuario digitou, mantendo digitos e no maximo um separador decimal.
     * O que sobra e sempre legivel por [ler] ou e vazio.
     */
    fun filtrar(bruto: String): String {
        val sb = StringBuilder()
        var jaTemSeparador = false
        for (ch in bruto) {
            when {
                ch.isDigit() -> sb.append(ch)
                (ch == ',' || ch == '.') && !jaTemSeparador && sb.isNotEmpty() -> {
                    jaTemSeparador = true
                    sb.append(',')
                }
                else -> Unit   // milhar, espaco, letra, sinal: nao entram
            }
        }
        return sb.toString()
    }

    /**
     * Le o texto ja filtrado. Devolve null para vazio, para separador solto e para zero —
     * producao de 0 t/ano nao e empreendimento, e aceitar isso em silencio produzia porte
     * pequeno para quem so encostou no campo.
     */
    fun ler(texto: String): Double? {
        val limpo = filtrar(texto).replace(',', '.')
        if (limpo.isEmpty() || limpo == ".") return null
        val n = limpo.toDoubleOrNull() ?: return null
        if (!n.isFinite() || n <= 0.0) return null
        return n
    }

    private val fmt: NumberFormat = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
        maximumFractionDigits = 4
    }

    /** Numero por extenso, com separador de milhar, para o usuario conferir o que o app leu. */
    fun porExtenso(n: Double): String = fmt.format(n)
}
