package br.com.oanalistaambiental.pericia.dados

import org.json.JSONObject
import java.io.InputStream

/**
 * Cadastros e registros do IEF por categoria — flora e fauna aquática (pesca/aquicultura).
 *
 * Dado real, não fabricado: categorias, base legal e regra de renovação vêm das páginas
 * oficiais citadas em `fonte`, dentro do JSON. O único valor de taxa em R$/UFEMG que aparece
 * aqui (`taxaAlteracaoUfemg` da flora, 15 UFEMG) é o único que a pesquisa achou confirmado em
 * texto oficial — para o resto, `taxaNota` diz explicitamente que o valor não foi confirmado,
 * em vez de inventar um número.
 */
data class CategoriaCadastro(
    val nome: String,
    val quemPrecisa: String?,
    val baseLegalEspecifica: String?
)

data class GrupoCadastro(
    val id: String,
    val titulo: String,
    val baseLegal: String,
    val categorias: List<CategoriaCadastro>,
    val quemPrecisaGeral: String?,
    val isencao: String?,
    val documentos: List<String>,
    val renovacao: String,
    val taxaNota: String,
    val taxaAlteracaoUfemg: Double?
)

data class CadastrosIef(
    val titulo: String,
    val fonte: String,
    val sistema: String,
    val aviso: String,
    val grupos: List<GrupoCadastro>
)

object CadastrosIefCarregador {
    fun carregar(abrir: () -> InputStream): CadastrosIef {
        val texto = abrir().bufferedReader().use { it.readText() }
        val json = JSONObject(texto)

        val grupos = json.getJSONArray("grupos").let { arr ->
            (0 until arr.length()).map { i ->
                val g = arr.getJSONObject(i)
                val categorias = g.getJSONArray("categorias").let { carr ->
                    (0 until carr.length()).map { j ->
                        val c = carr.getJSONObject(j)
                        CategoriaCadastro(
                            nome = c.getString("nome"),
                            quemPrecisa = c.optString("quemPrecisa", "").ifBlank { null },
                            baseLegalEspecifica = c.optString("baseLegalEspecifica", "").ifBlank { null }
                        )
                    }
                }
                GrupoCadastro(
                    id = g.getString("id"),
                    titulo = g.getString("titulo"),
                    baseLegal = g.getString("baseLegal"),
                    categorias = categorias,
                    quemPrecisaGeral = g.optString("quemPrecisa", "").ifBlank { null },
                    isencao = g.optString("isencao", "").ifBlank { null },
                    documentos = g.optJSONArray("documentos")?.let { darr ->
                        (0 until darr.length()).map { darr.getString(it) }
                    } ?: emptyList(),
                    renovacao = g.getString("renovacao"),
                    taxaNota = g.getString("taxaNota"),
                    taxaAlteracaoUfemg = if (g.isNull("taxaAlteracaoUfemg")) null else g.optDouble("taxaAlteracaoUfemg")
                )
            }
        }

        return CadastrosIef(
            titulo = json.getString("titulo"),
            fonte = json.getString("fonte"),
            sistema = json.getString("sistema"),
            aviso = json.getString("aviso"),
            grupos = grupos
        )
    }
}
