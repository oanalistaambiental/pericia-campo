package br.com.oanalistaambiental.pericia.taxas

import org.json.JSONObject
import java.io.InputStream

/**
 * Taxa de expediente (análise de intervenção ambiental / DAIA) e taxa florestal, em UFEMG.
 *
 * Dado real, não fabricado: extraído das planilhas oficiais de estimativa de custo que
 * circulam no grupo de analistas ("Planilha de estimativa de custos Intervenção Ambiental -
 * UFEMG 2026.xlsx"), com a fonte legal do valor da UFEMG citada na própria planilha —
 * Resolução SEF-MG Nº 5.969, de 28/11/2025, para o exercício de 2026.
 *
 * O valor da UFEMG muda todo ano. `assets/taxas/ufemg_2026.json` traz o exercício e a fonte
 * junto do número, exatamente para a tela poder avisar quando o ano civil não bater mais —
 * mesma lógica de nunca deixar um dado datado se passar por atual.
 */
data class ItemTaxa(
    val codigo: String,
    val especificacao: String,
    val unidade: String,
    val fixoUfemg: Double,
    val variavelUfemgPorUnidade: Double
)

data class TabelaTaxas(
    val exercicio: Int,
    val valorUfemg: Double,
    val fonte: String,
    val taxaExpedienteIntervencao: List<ItemTaxa>,
    val taxaFlorestal: List<ItemTaxa>
)

object TaxaUfemg {

    fun carregar(abrir: () -> InputStream): TabelaTaxas {
        val texto = abrir().bufferedReader().use { it.readText() }
        val json = JSONObject(texto)

        fun lerItens(campo: String, temFixo: Boolean): List<ItemTaxa> {
            val arr = json.getJSONArray(campo)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ItemTaxa(
                    codigo = o.getString("codigo"),
                    especificacao = o.getString("especificacao"),
                    unidade = o.getString("unidade"),
                    fixoUfemg = if (temFixo) o.getDouble("fixo_ufemg") else 0.0,
                    variavelUfemgPorUnidade = o.getDouble(
                        if (temFixo) "variavel_ufemg_por_unidade" else "ufemg_por_unidade"
                    )
                )
            }
        }

        return TabelaTaxas(
            exercicio = json.getInt("exercicio"),
            valorUfemg = json.getDouble("valor_ufemg"),
            fonte = json.getString("fonte"),
            taxaExpedienteIntervencao = lerItens("taxa_expediente_intervencao", temFixo = true),
            taxaFlorestal = lerItens("taxa_florestal", temFixo = false)
        )
    }

    /** valor = (fixo + variavel * quantidade) * valor da UFEMG. Fixo e zero na taxa florestal. */
    fun calcular(item: ItemTaxa, quantidade: Double, valorUfemg: Double): Double =
        (item.fixoUfemg + item.variavelUfemgPorUnidade * quantidade) * valorUfemg
}
