package br.com.oanalistaambiental.pericia.fichas

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/** Um item de checklist dentro de uma [SecaoFicha] — só o texto da pergunta. */
data class ItemFicha(val texto: String)

data class SecaoFicha(val titulo: String, val itens: List<ItemFicha>)

/**
 * Um modelo de ficha de vistoria por tipo de empreendimento — dado, não código, mesma razão de
 * `enquadramento/norma`: trocar ou ampliar o catálogo não deve pedir recompilar o app.
 */
data class ModeloFicha(val id: String, val nome: String, val secoes: List<SecaoFicha>) {
    val totalItens: Int get() = secoes.sumOf { it.itens.size }
}

/**
 * Resposta a um item — três estados, nunca um booleano: "não se aplica" é uma resposta real
 * (ex. "há outorga vigente?" numa atividade sem captação de água), e forçar sim/não aí
 * produziria uma ficha que mente por omissão.
 */
enum class ValorResposta { CONFORME, NAO_CONFORME, NAO_SE_APLICA, NAO_RESPONDIDO }

data class Resposta(val secao: String, val item: String, val valor: ValorResposta, val observacao: String?)

/** Carrega o catálogo de `assets/fichas/fichas.json`. */
object CatalogoFichas {
    fun carregar(abrir: () -> InputStream): List<ModeloFicha> {
        val texto = abrir().bufferedReader().use { it.readText() }
        val raiz = JSONObject(texto).getJSONArray("modelos")
        return (0 until raiz.length()).map { i ->
            val m = raiz.getJSONObject(i)
            val secoes = m.getJSONArray("secoes").let { arrSec ->
                (0 until arrSec.length()).map { j ->
                    val s = arrSec.getJSONObject(j)
                    val itens = s.getJSONArray("itens").let { arrItem ->
                        (0 until arrItem.length()).map { k -> ItemFicha(arrItem.getString(k)) }
                    }
                    SecaoFicha(titulo = s.getString("titulo"), itens = itens)
                }
            }
            ModeloFicha(id = m.getString("id"), nome = m.getString("nome"), secoes = secoes)
        }
    }
}

/** Serializa as respostas de uma ficha preenchida para gravar em [br.com.oanalistaambiental.pericia.dados.RegistroFicha.respostasJson]. */
fun serializarRespostas(respostas: List<Resposta>): String {
    val arr = JSONArray()
    respostas.forEach { r ->
        arr.put(JSONObject().apply {
            put("secao", r.secao)
            put("item", r.item)
            put("valor", r.valor.name)
            put("observacao", r.observacao ?: JSONObject.NULL)
        })
    }
    return arr.toString()
}

fun parseRespostas(json: String): List<Resposta> {
    val arr = JSONArray(json)
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Resposta(
            secao = o.getString("secao"),
            item = o.getString("item"),
            valor = runCatching { ValorResposta.valueOf(o.getString("valor")) }.getOrDefault(ValorResposta.NAO_RESPONDIDO),
            observacao = if (o.isNull("observacao")) null else o.getString("observacao")
        )
    }
}
