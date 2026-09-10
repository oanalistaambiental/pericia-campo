package br.com.oanalistaambiental.pericia.dados

import org.json.JSONObject
import java.io.InputStream

/**
 * Canais oficiais de denúncia ambiental em MG — dado real, das páginas oficiais (SEMAD, FEAM,
 * PMMG, Ouvidoria-Geral), citadas em `fonte`/`aviso` dentro do JSON. Puramente informativo: o
 * app não envia denúncia nenhuma por conta própria, só mostra para onde ela vai.
 */
data class ItemCanal(
    val nome: String,
    val detalhe: String?,
    val telefone: String?,
    val horario: String?,
    val link: String?,
    val endereco: String?
)

data class GrupoCanal(
    val id: String,
    val titulo: String,
    val descricao: String,
    val itens: List<ItemCanal>
)

data class UraRegional(val nome: String, val endereco: String, val telefone: String?)

data class UrasInfo(val titulo: String, val descricao: String, val fonte: String, val regionais: List<UraRegional>)

data class CanaisDenuncia(
    val titulo: String,
    val aviso: String,
    val grupos: List<GrupoCanal>,
    val uras: UrasInfo
)

object CanaisDenunciaCarregador {
    fun carregar(abrir: () -> InputStream): CanaisDenuncia {
        val json = JSONObject(abrir().bufferedReader().use { it.readText() })

        val grupos = json.getJSONArray("grupos").let { arr ->
            (0 until arr.length()).map { i ->
                val g = arr.getJSONObject(i)
                val itens = g.getJSONArray("itens").let { iarr ->
                    (0 until iarr.length()).map { j ->
                        val it = iarr.getJSONObject(j)
                        ItemCanal(
                            nome = it.getString("nome"),
                            detalhe = it.optString("detalhe", "").ifBlank { null },
                            telefone = it.optString("telefone", "").ifBlank { null },
                            horario = it.optString("horario", "").ifBlank { null },
                            link = it.optString("link", "").ifBlank { null },
                            endereco = it.optString("endereco", "").ifBlank { null }
                        )
                    }
                }
                GrupoCanal(
                    id = g.getString("id"), titulo = g.getString("titulo"),
                    descricao = g.getString("descricao"), itens = itens
                )
            }
        }

        val urasJson = json.getJSONObject("uras")
        val regionais = urasJson.getJSONArray("regionais").let { arr ->
            (0 until arr.length()).map { i ->
                val r = arr.getJSONObject(i)
                UraRegional(
                    nome = r.getString("nome"), endereco = r.getString("endereco"),
                    telefone = if (r.isNull("telefone")) null else r.optString("telefone", "").ifBlank { null }
                )
            }
        }
        val uras = UrasInfo(
            titulo = urasJson.getString("titulo"), descricao = urasJson.getString("descricao"),
            fonte = urasJson.getString("fonte"), regionais = regionais
        )

        return CanaisDenuncia(
            titulo = json.getString("titulo"), aviso = json.getString("aviso"),
            grupos = grupos, uras = uras
        )
    }
}
