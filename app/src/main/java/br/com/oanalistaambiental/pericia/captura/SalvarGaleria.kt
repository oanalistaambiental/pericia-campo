package br.com.oanalistaambiental.pericia.captura

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Copia a foto (com legenda e marca d'água) para a galeria PÚBLICA do aparelho, num álbum
 * próprio — pedido de Francisco: achar a foto direto pelo app de Galeria, sem precisar exportar
 * pelo Perícia Campo toda vez.
 *
 * NUNCA o original: só a cópia já com legenda entra aqui. O arquivo com o hash calculado
 * continua só na pasta interna do app, fora do alcance de qualquer app de galeria que
 * recomprima ou reprocesse imagem — a mesma regra de sempre, agora valendo também pra isto.
 *
 * Falha aqui é silenciosa de propósito (devolve `false`, não lança): a foto já está salva e
 * segura na sessão de qualquer jeito, a cópia na galeria é conveniência, não prova.
 */
object SalvarGaleria {

    fun salvar(contexto: Context, arquivo: File, album: String): Boolean = runCatching {
        val resolver = contexto.contentResolver
        val valores = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, arquivo.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$album")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valores)
            ?: return false

        resolver.openOutputStream(uri)?.use { saida ->
            arquivo.inputStream().use { it.copyTo(saida) }
        } ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
        true
    }.getOrDefault(false)
}
