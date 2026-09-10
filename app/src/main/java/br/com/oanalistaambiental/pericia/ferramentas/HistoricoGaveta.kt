package br.com.oanalistaambiental.pericia.ferramentas

import android.content.Context

private const val CHAVE_RECENTES = "gaveta_recentes"
private const val MAXIMO_RECENTES = 6

/**
 * "Usados recentemente" da gaveta — puro conforto de navegação, não prova de perícia, por isso
 * `SharedPreferences` direto (mesmo arquivo `pericia_prefs` que já guarda modo sol forte/economia
 * de bateria), não uma tabela no banco.
 */
object HistoricoGaveta {
    private fun prefs(context: Context) =
        context.getSharedPreferences("pericia_prefs", Context.MODE_PRIVATE)

    fun registrarUso(context: Context, id: String) {
        val atuais = recentes(context).toMutableList()
        atuais.remove(id)
        atuais.add(0, id)
        prefs(context).edit().putString(CHAVE_RECENTES, atuais.take(MAXIMO_RECENTES).joinToString(",")).apply()
    }

    fun recentes(context: Context): List<String> {
        val bruto = prefs(context).getString(CHAVE_RECENTES, null) ?: return emptyList()
        return bruto.split(",").filter { it.isNotBlank() }
    }
}
