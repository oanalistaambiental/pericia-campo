package br.com.oanalistaambiental.pericia.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR local do parecer fotografado (modelo embarcado no APK, sem rede — mesmo espírito
 * offline-first do resto do app). Devolve só as linhas de texto reconhecidas: dizer qual delas é
 * uma condicionante, e qual é o prazo, continua sendo decisão de quem cadastra — o app localiza
 * texto, não interpreta o parecer.
 */
object LeitorDeTexto {
    suspend fun reconhecerLinhas(bitmap: Bitmap): List<String> {
        val imagem = InputImage.fromBitmap(bitmap, 0)
        val reconhecedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return suspendCancellableCoroutine { continuacao ->
            reconhecedor.process(imagem)
                .addOnSuccessListener { texto ->
                    val linhas = texto.textBlocks
                        .flatMap { bloco -> bloco.lines.map { it.text.trim() } }
                        .filter { it.isNotBlank() }
                    continuacao.resume(linhas)
                }
                .addOnFailureListener { erro -> continuacao.resumeWithException(erro) }
        }
    }

    /** Primeira data em dd/mm/aaaa (ou dd/mm/aa) encontrada no texto — sugestão, nunca decisão. */
    fun sugerirData(linhas: List<String>): String? {
        val padrao = Regex("""\b(\d{2}/\d{2}/\d{2,4})\b""")
        for (linha in linhas) {
            padrao.find(linha)?.let { return it.groupValues[1] }
        }
        return null
    }
}
