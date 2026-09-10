package br.com.oanalistaambiental.pericia.captura

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Ditado por voz -> texto, usando o reconhecedor do PRÓPRIO Android — sem mandar áudio para
 * nuvem nenhuma além da que o próprio aparelho já usa para isso (pedido explícito: nada de
 * serviço externo complexo).
 *
 * `EXTRA_PREFER_OFFLINE` pede ao Android para preferir o reconhecimento local quando o aparelho
 * suportar — não é garantia: a API do Android não promete isso para todo aparelho/idioma
 * baixado, então a tela precisa dizer isso com todas as letras, não fingir uma certeza que a
 * plataforma não dá.
 *
 * O reconhecedor do Android entende UMA fala por vez e para sozinho no silêncio — para virar
 * ditado contínuo (um relato inteiro, não uma frase), esta classe reinicia a escuta sozinha a
 * cada resultado, até [parar] ser chamado.
 */
class Transcricao(private val contexto: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var ativo = false
    private val acumulado = StringBuilder()

    fun disponivel(): Boolean = SpeechRecognizer.isRecognitionAvailable(contexto)

    fun iniciar(aoAtualizar: (String) -> Unit, aoErro: (String) -> Unit) {
        if (ativo) return
        ativo = true
        acumulado.clear()

        val r = SpeechRecognizer.createSpeechRecognizer(contexto)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val texto = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!texto.isNullOrBlank()) {
                    if (acumulado.isNotEmpty()) acumulado.append(" ")
                    acumulado.append(texto)
                    aoAtualizar(acumulado.toString())
                }
                if (ativo) reiniciarEscuta(r)
            }

            // Silencio e "nao entendi" nao sao erro de verdade aqui — sao o intervalo normal
            // entre uma frase e outra de quem esta narrando um relato. So um erro de verdade
            // (sem permissao, sem servico) encerra o ditado.
            override fun onError(codigo: Int) {
                val recuperavel = codigo == SpeechRecognizer.ERROR_NO_MATCH ||
                    codigo == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (!ativo) return
                if (recuperavel) reiniciarEscuta(r)
                else { aoErro(mensagemErro(codigo)); parar() }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        reiniciarEscuta(r)
    }

    private fun reiniciarEscuta(r: SpeechRecognizer) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        runCatching { r.startListening(intent) }
    }

    fun parar() {
        ativo = false
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun mensagemErro(codigo: Int): String = when (codigo) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sem permissão de microfone."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Falha de rede — o reconhecimento de voz deste aparelho pode depender de conexão."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconhecimento de voz ocupado — tente de novo."
        else -> "Reconhecimento de voz indisponível neste aparelho (código $codigo)."
    }
}
