package br.com.oanalistaambiental.pericia.captura

import android.content.Context
import android.location.Geocoder
import android.os.Build
import br.com.oanalistaambiental.pericia.dados.Banco
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Geocodificacao reversa com fila offline.
 *
 * Conforme o desenho do projeto, endereco e o UNICO dado da legenda que depende de conexao.
 * Em campo a foto e gravada com o endereco pendente; quando houver rede, esta fila completa os
 * registros em aberto. Usa o Geocoder do proprio Android — sem chave, sem servico externo.
 */
object Enderecos {

    /** Distingue "nao ha servico", "nao havia nada pendente" e "tentei e nao consegui". */
    sealed class Resultado {
        object SemServico : Resultado()
        object NadaPendente : Resultado()
        data class Concluido(val resolvidos: Int, val tentados: Int) : Resultado()
    }

    /**
     * Antes devolvia so um Int, e tres causas diferentes chegavam ao perito como a MESMA frase
     * ("Nenhum endereço pendente foi resolvido"): aparelho sem servico de geocodificacao, fila
     * vazia, e sem internet. Saber qual dos tres muda o que ele faz a seguir.
     */
    suspend fun resolver(context: Context, banco: Banco): Resultado = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext Resultado.SemServico
        val pendentes = banco.fotosComEnderecoPendente()
        if (pendentes.isEmpty()) return@withContext Resultado.NadaPendente
        val geocoder = Geocoder(context, Locale("pt", "BR"))
        var resolvidos = 0
        for (foto in pendentes) {
            val texto = buscar(geocoder, foto.lat, foto.lon) ?: continue
            banco.atualizarEndereco(foto.id, texto)
            resolvidos++
        }
        Resultado.Concluido(resolvidos, pendentes.size)
    }

    suspend fun resolverPendentes(context: Context, banco: Banco): Int =
        when (val r = resolver(context, banco)) {
            is Resultado.Concluido -> r.resolvidos
            else -> 0
        }

    private suspend fun buscar(geocoder: Geocoder, lat: Double, lon: Double): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // withTimeoutOrNull e a rede de seguranca: em ROM sem servicos Google o fornecedor
            // pode nunca chamar onGeocode NEM onError, e a corrotina ficaria suspensa para
            // sempre — o perito toca em "resolver enderecos" e nunca recebe resposta nenhuma.
            withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine<String?> { cont ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    // O Geocoder pode chamar de volta depois de a corrotina ser cancelada, e
                    // retomar duas vezes derruba o app. O runCatching absorve isso.
                    override fun onGeocode(enderecos: MutableList<android.location.Address>) {
                        runCatching { cont.resume(formatar(enderecos.firstOrNull())) }
                    }
                    override fun onError(mensagem: String?) { runCatching { cont.resume(null) } }
                })
            }
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching { formatar(geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()) }.getOrNull()
        }

    private fun formatar(a: android.location.Address?): String? {
        if (a == null) return null
        val partes = listOfNotNull(
            a.thoroughfare,
            a.subLocality,
            a.locality ?: a.subAdminArea,
            a.adminArea
        ).distinct()
        return partes.joinToString(", ").ifBlank { null }
    }
}
