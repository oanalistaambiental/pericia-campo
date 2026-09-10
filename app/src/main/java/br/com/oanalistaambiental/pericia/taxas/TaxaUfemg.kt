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

/**
 * Taxa de entrada no processo de licenciamento (LAS/LAT/LAC), tabelada pela FEAM ano a ano —
 * valores em R$, ja calculados pelo orgao (nao e formula simples de UFEMG x multiplicador, por
 * isso ficam gravados diretos, como a propria FEAM publica).
 *
 * As duas listagens (industrial/minerario/infraestrutura x agrosilvipastoril) tem precos bem
 * diferentes para a mesma classe — dai a fonte ser sempre a Listagem da atividade (o primeiro
 * caractere do codigo do Anexo Unico da DN 217: 'G' e agrosilvipastoril, as demais letras
 * entram na tabela industrial/minerario/infraestrutura).
 */
data class TabelaLicenciamento(
    val fonte: String,
    val urlAf: String,
    val urlG: String,
    /** linha (ex.: "lat_lp") -> classe (ex.: "4") -> valor em R$. */
    val industrialMineraroInfra: Map<String, Map<String, Double>>,
    val agrosilvipastoril: Map<String, Map<String, Double>>,
    val diversos: Map<String, Double>
) {
    fun valor(linha: String, classe: Int, listagemG: Boolean): Double? {
        val tabela = if (listagemG) agrosilvipastoril else industrialMineraroInfra
        return tabela[linha]?.get(classe.toString())
    }
}

data class TabelaTaxas(
    val exercicio: Int,
    val valorUfemg: Double,
    val fonte: String,
    val taxaExpedienteIntervencao: List<ItemTaxa>,
    val taxaFlorestal: List<ItemTaxa>,
    val licenciamento: TabelaLicenciamento
)

/**
 * As linhas de licenciamento disponiveis para cada sigla de modalidade que
 * `enquadramento/norma/Enquadramento.kt` pode calcular — e o rotulo que a tela mostra para cada
 * uma. LAS tem uma linha so; LAT e LAC2 tem varias, porque a taxa varia pela FASE pedida
 * (LP, LI, LO...), informacao que o enquadramento nao decide sozinho — e do processo, nao da
 * simulacao.
 */
object FasesLicenciamento {
    data class Opcao(val chave: String, val rotulo: String)

    fun opcoes(modalidadeSigla: String): List<Opcao> = when (modalidadeSigla) {
        "LAS/Cadastro" -> listOf(Opcao("las_cadastro", "Cadastro"))
        "LAS/RAS" -> listOf(Opcao("las_ras", "RAS"))
        "LAT" -> listOf(
            Opcao("lat_lp", "LP"), Opcao("lat_li", "LI"), Opcao("lat_lic", "LI complementar (LIC)"),
            Opcao("lat_lo", "LO"), Opcao("lat_loc", "LO complementar (LOC)")
        )
        "LAC1" -> listOf(
            Opcao("lac1_lp_li_lo", "LP + LI + LO (fase única)"), Opcao("lac1_loc", "LO complementar (LOC)")
        )
        "LAC2" -> listOf(
            Opcao("lac2_lp", "LP"), Opcao("lac2_lp_li", "LP + LI"), Opcao("lac2_li_lo", "LI + LO"),
            Opcao("lac2_lic", "LI complementar (LIC)"), Opcao("lac2_lic_lo", "LIC + LO"),
            Opcao("lac2_lo", "LO"), Opcao("lac2_loc", "LO complementar (LOC)")
        )
        else -> emptyList()
    }
}

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

        fun lerTabelaLinhas(o: JSONObject): Map<String, Map<String, Double>> =
            o.keys().asSequence().associateWith { linha ->
                val linhaJson = o.getJSONObject(linha)
                linhaJson.keys().asSequence().associateWith { classe -> linhaJson.getDouble(classe) }
            }

        val licJson = json.getJSONObject("taxa_licenciamento")
        val licenciamento = TabelaLicenciamento(
            fonte = licJson.getString("fonte"),
            urlAf = licJson.getString("url_af"),
            urlG = licJson.getString("url_g"),
            industrialMineraroInfra = lerTabelaLinhas(licJson.getJSONObject("industrial_minerario_infra")),
            agrosilvipastoril = lerTabelaLinhas(licJson.getJSONObject("agrosilvipastoril")),
            diversos = licJson.getJSONObject("diversos").let { d ->
                d.keys().asSequence().associateWith { d.getDouble(it) }
            }
        )

        return TabelaTaxas(
            exercicio = json.getInt("exercicio"),
            valorUfemg = json.getDouble("valor_ufemg"),
            fonte = json.getString("fonte"),
            taxaExpedienteIntervencao = lerItens("taxa_expediente_intervencao", temFixo = true),
            taxaFlorestal = lerItens("taxa_florestal", temFixo = false),
            licenciamento = licenciamento
        )
    }

    /** valor = (fixo + variavel * quantidade) * valor da UFEMG. Fixo e zero na taxa florestal. */
    fun calcular(item: ItemTaxa, quantidade: Double, valorUfemg: Double): Double =
        (item.fixoUfemg + item.variavelUfemgPorUnidade * quantidade) * valorUfemg
}
