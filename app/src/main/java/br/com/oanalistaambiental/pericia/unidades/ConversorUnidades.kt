package br.com.oanalistaambiental.pericia.unidades

/**
 * Conversor de unidades comuns no licenciamento ambiental — vazão, área, volume, massa e taxa de
 * produção. Só aritmética de conversão de unidade (fatos matemáticos exatos, verificáveis por
 * qualquer um), nunca interpretação de norma: por isso não precisa da mesma pesquisa de fonte
 * oficial que o catálogo da DN 217 exige. "Alqueire" foi deixado de fora de propósito — varia de
 * tamanho por região (paulista, mineiro, goiano) e incluir um valor único seria alegar precisão
 * que não existe.
 *
 * Taxa de produção (kg/dia, t/mês, t/ano) assume mês de 30 dias e ano de 365 dias — convenção
 * declarada, não descoberta pelo app: qualquer contrato ou condicionante pode definir diferente.
 */
data class UnidadeConversao(val id: String, val rotulo: String, val fatorParaBase: Double)

enum class CategoriaUnidade(val titulo: String, val unidadeBase: String) {
    VAZAO("Vazão", "L/s"),
    AREA("Área", "m²"),
    VOLUME("Volume", "L"),
    MASSA("Massa", "kg"),
    TAXA_MASSA("Taxa de produção/consumo", "kg/dia")
}

object ConversorUnidades {
    private const val SEGUNDOS_POR_HORA = 3600.0
    private const val SEGUNDOS_POR_DIA = 86400.0
    private const val DIAS_POR_MES = 30.0
    private const val DIAS_POR_ANO = 365.0

    val vazao = listOf(
        UnidadeConversao("l_s", "L/s", 1.0),
        UnidadeConversao("m3_h", "m³/h", 1000.0 / SEGUNDOS_POR_HORA),
        UnidadeConversao("m3_dia", "m³/dia", 1000.0 / SEGUNDOS_POR_DIA),
        UnidadeConversao("m3_mes", "m³/mês (30 dias)", 1000.0 / (SEGUNDOS_POR_DIA * DIAS_POR_MES)),
        UnidadeConversao("m3_ano", "m³/ano (365 dias)", 1000.0 / (SEGUNDOS_POR_DIA * DIAS_POR_ANO))
    )

    val area = listOf(
        UnidadeConversao("m2", "m²", 1.0),
        UnidadeConversao("ha", "hectare (ha)", 10_000.0),
        UnidadeConversao("km2", "km²", 1_000_000.0)
    )

    val volume = listOf(
        UnidadeConversao("l", "L", 1.0),
        UnidadeConversao("m3", "m³", 1_000.0),
        UnidadeConversao("milhao_l", "milhão de litros", 1_000_000.0)
    )

    val massa = listOf(
        UnidadeConversao("kg", "kg", 1.0),
        UnidadeConversao("t", "tonelada (t)", 1_000.0)
    )

    val taxaMassa = listOf(
        UnidadeConversao("kg_dia", "kg/dia", 1.0),
        UnidadeConversao("t_dia", "t/dia", 1_000.0),
        UnidadeConversao("t_mes", "t/mês (30 dias)", 1_000.0 / DIAS_POR_MES),
        UnidadeConversao("t_ano", "t/ano (365 dias)", 1_000.0 / DIAS_POR_ANO)
    )

    fun unidadesDe(categoria: CategoriaUnidade): List<UnidadeConversao> = when (categoria) {
        CategoriaUnidade.VAZAO -> vazao
        CategoriaUnidade.AREA -> area
        CategoriaUnidade.VOLUME -> volume
        CategoriaUnidade.MASSA -> massa
        CategoriaUnidade.TAXA_MASSA -> taxaMassa
    }

    /** Converte [valor] da unidade [de] para a unidade [para] — ambas da mesma categoria. */
    fun converter(valor: Double, de: UnidadeConversao, para: UnidadeConversao): Double =
        valor * de.fatorParaBase / para.fatorParaBase
}
