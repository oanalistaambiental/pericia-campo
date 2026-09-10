package br.com.oanalistaambiental.pericia.enquadramento.norma

import org.json.JSONObject
import java.io.InputStream

/**
 * Dica de uma atividade, extraida de pareceres publicos ja deferidos — nunca de memoria, nunca
 * de resumo de IA. A mesma regra de exatidao regulatoria de [BaseNormativa] vale aqui: uma
 * "dica" que nao vem do texto de decisoes de verdade e so uma opiniao com roupa de dado.
 *
 * [pareceresConsultados] e [dataExtracao] existem porque uma dica sem eles vira afirmacao sem
 * lastro — o mesmo raciocinio da anonimizacao das decisoes: generaliza o padrao recorrente e
 * declara de onde veio, nunca cita o caso concreto.
 */
data class DicaAtividade(
    val texto: String,
    val pareceresConsultados: Int,
    val dataExtracao: String
)

/**
 * Carrega a dica de uma atividade. Dado, nao codigo: cada arquivo fica em
 * `assets/norma/dicas/<codigo>.json`, e uma dica nova nao exige recompilar o app.
 *
 * A AUSENCIA de arquivo e o caso normal hoje — a leitura de pareceres (skill futura
 * `dn217-ler-pareceres`) ainda nao cobriu a maioria das atividades. [carregar] devolve null
 * nesse caso, de proposito, e quem exibe precisa dizer isso explicitamente ("ainda nao lemos
 * pareceres desta atividade"): confundir "sem dica" com "sem problema" e o mesmo erro que o
 * app ja recusa em toda consulta de restricao locacional.
 */
object Dicas {

    fun carregar(codigo: String, abrir: (String) -> InputStream): DicaAtividade? {
        val bytes = runCatching { abrir("$codigo.json").use { it.readBytes() } }.getOrNull()
            ?: return null
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        return DicaAtividade(
            texto = json.getString("texto"),
            pareceresConsultados = json.getInt("pareceres_consultados"),
            dataExtracao = json.getString("data_extracao")
        )
    }
}
