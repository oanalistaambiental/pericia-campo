package br.com.oanalistaambiental.pericia.captura

import android.content.Context
import android.location.Geocoder
import android.os.Build
import br.com.oanalistaambiental.pericia.dados.Banco
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

    suspend fun resolverPendentes(context: Context, banco: Banco): Int = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext 0
        val geocoder = Geocoder(context, Locale("pt", "BR"))
        var resolvidos = 0
        for (foto in banco.fotosComEnderecoPendente()) {
            val texto = buscar(geocoder, foto.lat, foto.lon) ?: continue
            banco.atualizarEndereco(foto.id, texto)
            resolvidos++
        }
        resolvidos
    }

    private suspend fun buscar(geocoder: Geocoder, lat: Double, lon: Double): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(enderecos: MutableList<android.location.Address>) {
                        cont.resume(formatar(enderecos.firstOrNull()))
                    }
                    override fun onError(mensagem: String?) { cont.resume(null) }
                })
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
