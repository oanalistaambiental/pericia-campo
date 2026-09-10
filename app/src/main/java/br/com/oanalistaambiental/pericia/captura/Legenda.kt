package br.com.oanalistaambiental.pericia.captura

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.ExifInterface
import br.com.oanalistaambiental.pericia.dados.Foto
import br.com.oanalistaambiental.pericia.geo.Utm
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Canto onde a marca d'água entra na cópia com legenda — e no visor da câmera, ao vivo. */
enum class PosicaoMarcaDagua { SUPERIOR_ESQUERDA, SUPERIOR_DIREITA, INFERIOR_ESQUERDA, INFERIOR_DIREITA }

/**
 * Grava a legenda tecnica sobre uma CÓPIA da imagem.
 *
 * Regra que nao se quebra: o arquivo original, ja com hash calculado, nunca e tocado. O que
 * recebe legenda e uma copia. Estilo "documento tecnico", nao "turismo" — e o que separa este
 * app dos genericos de camera com GPS.
 */
object Legenda {

    /** Maior lado da COPIA com legenda. O original nunca e redimensionado. */
    private const val LADO_MAXIMO = 4000

    // SimpleDateFormat NAO e thread-safe, e uma instancia de `object` e compartilhada por todo
    // o app. A legenda e gerada no fluxo de captura enquanto um export pode estar rodando em
    // paralelo; o resultado de uma corrida em SimpleDateFormat nao e excecao — e uma data
    // silenciosamente errada, carimbada na imagem que vai para o processo. ThreadLocal da uma
    // instancia por thread e resolve sem trocar a API.
    private val fmtData = ThreadLocal.withInitial {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
    }

    /**
     * Maior lado da marca d'água, como fração da largura da cópia — pequena de propósito: é
     * identificação (brasão do órgão, logo da consultoria), não uma segunda legenda competindo
     * com a técnica.
     */
    private const val FRACAO_MARCA_DAGUA = 0.16f

    fun gerar(
        original: File,
        destino: File,
        foto: Foto,
        sessaoTitulo: String,
        marcaDagua: File? = null,
        posicaoMarcaDagua: PosicaoMarcaDagua = PosicaoMarcaDagua.SUPERIOR_DIREITA
    ): File {
        // BUG corrigido: a imagem era decodificada em tamanho cheio e mutavel (ARGB_8888).
        // Um sensor de 50 MP vira ~200 MB de bitmap, e ainda mais 200 MB quando ha rotacao a
        // aplicar — OutOfMemoryError na hora de gravar a legenda, num aparelho de campo.
        // A COPIA com legenda e a versao para leitura humana; o original, que e a prova, fica
        // intocado e ja com hash. Entao a copia pode ser reduzida sem prejuizo nenhum.
        val medida = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(original.absolutePath, medida)
        val maiorLado = maxOf(medida.outWidth, medida.outHeight)

        var amostra = 1
        while (maiorLado / amostra > LADO_MAXIMO) amostra *= 2

        // inMutable permite que Canvas() desenhe sobre o bitmap sem uma segunda copia no
        // caminho em que nao ha rotacao a aplicar.
        val opcoes = BitmapFactory.Options().apply {
            inMutable = true
            inSampleSize = amostra
        }
        val bruta = BitmapFactory.decodeFile(original.absolutePath, opcoes)
            ?: throw IllegalStateException("Não foi possível ler a imagem original")

        // O CameraX grava os pixels na orientacao do sensor e marca a rotacao no EXIF.
        // BitmapFactory ignora o EXIF, entao sem isto a copia com legenda sai deitada.
        val graus = when (
            ExifInterface(original.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val copia = if (graus == 0f) bruta else Bitmap.createBitmap(
            bruta, 0, 0, bruta.width, bruta.height,
            Matrix().apply { postRotate(graus) }, true
        ).also { if (it !== bruta) bruta.recycle() }

        val canvas = Canvas(copia)
        val largura = copia.width
        val escala = largura / 1080f

        val linhas = montarLinhas(foto, sessaoTitulo)
        val tamanhoTexto = 26f * escala
        val padding = 16f * escala
        val alturaLinha = tamanhoTexto * 1.35f
        val alturaBarra = alturaLinha * linhas.size + padding * 2

        val fundo = Paint().apply { color = Color.argb(190, 0, 0, 0) }
        canvas.drawRect(0f, copia.height - alturaBarra, largura.toFloat(), copia.height.toFloat(), fundo)

        val texto = Paint().apply {
            color = Color.WHITE
            textSize = tamanhoTexto
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val destaque = Paint(texto).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        var y = copia.height - alturaBarra + padding + tamanhoTexto
        linhas.forEachIndexed { i, linha ->
            canvas.drawText(linha, padding, y, if (i == 0) destaque else texto)
            y += alturaLinha
        }

        if (marcaDagua != null && marcaDagua.exists()) {
            // Nas posicoes INFERIOR, a marca entra ACIMA da barra da legenda tecnica — nunca
            // por cima do texto (coordenada, data, hash). E o "reajustado as outras
            // informacoes" pedido: as duas convivem, nenhuma cobre a outra.
            val limiteInferior = if (posicaoMarcaDagua == PosicaoMarcaDagua.INFERIOR_ESQUERDA ||
                posicaoMarcaDagua == PosicaoMarcaDagua.INFERIOR_DIREITA
            ) copia.height - alturaBarra else copia.height.toFloat()
            desenharMarcaDagua(canvas, marcaDagua, largura, padding, posicaoMarcaDagua, limiteInferior)
        }

        FileOutputStream(destino).use { copia.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        copia.recycle()
        return destino
    }

    /**
     * Desenha o brasão/logo no canto escolhido, em transparência de marca d'água — não some se
     * falhar: uma marca ilegível ou corrompida não pode derrubar a legenda técnica inteira, que
     * é o que importa para o laudo.
     *
     * [limiteInferiorY] é até onde a marca pode descer — a altura da cópia inteira nas posições
     * superiores, ou o topo da barra da legenda nas inferiores, para as duas nunca se
     * sobrepor.
     */
    private fun desenharMarcaDagua(
        canvas: Canvas, arquivo: File, larguraCopia: Int, margem: Float,
        posicao: PosicaoMarcaDagua, limiteInferiorY: Float
    ) {
        runCatching {
            val bruta = BitmapFactory.decodeFile(arquivo.absolutePath) ?: return@runCatching
            val ladoMaximo = larguraCopia * FRACAO_MARCA_DAGUA
            val fatorEscala = minOf(1f, ladoMaximo / maxOf(bruta.width, bruta.height))
            val larguraFinal = (bruta.width * fatorEscala).toInt().coerceAtLeast(1)
            val alturaFinal = (bruta.height * fatorEscala).toInt().coerceAtLeast(1)
            val redimensionada = if (fatorEscala < 1f) {
                Bitmap.createScaledBitmap(bruta, larguraFinal, alturaFinal, true)
                    .also { if (it !== bruta) bruta.recycle() }
            } else bruta

            val paint = Paint().apply { isAntiAlias = true; isFilterBitmap = true; alpha = 200 }
            val x = when (posicao) {
                PosicaoMarcaDagua.SUPERIOR_ESQUERDA, PosicaoMarcaDagua.INFERIOR_ESQUERDA -> margem
                PosicaoMarcaDagua.SUPERIOR_DIREITA, PosicaoMarcaDagua.INFERIOR_DIREITA ->
                    larguraCopia - redimensionada.width - margem
            }
            val y = when (posicao) {
                PosicaoMarcaDagua.SUPERIOR_ESQUERDA, PosicaoMarcaDagua.SUPERIOR_DIREITA -> margem
                PosicaoMarcaDagua.INFERIOR_ESQUERDA, PosicaoMarcaDagua.INFERIOR_DIREITA ->
                    limiteInferiorY - redimensionada.height - margem
            }
            canvas.drawBitmap(redimensionada, x, y, paint)
            redimensionada.recycle()
        }
    }

    private fun montarLinhas(foto: Foto, sessaoTitulo: String): List<String> {
        val linhas = mutableListOf<String>()
        linhas += sessaoTitulo.uppercase()

        // Num laudo, coordenada inventada e pior que coordenada nenhuma. Sem sinal, a foto
        // vale como registro visual e diz isso com todas as letras.
        val semPosicao = foto.lat == 0.0 && foto.lon == 0.0
        if (semPosicao) {
            linhas += "SEM POSIÇÃO GNSS NO MOMENTO DA CAPTURA"
        } else {
            val utm = Utm.projetar(foto.lat, foto.lon)
            linhas += "UTM SIRGAS 2000  ${utm.formatado()}"
            // Locale.US e obrigatorio aqui, nao preferencia. Em aparelho pt-BR o `.format`
            // sem locale escrevia "GEO  -19,922700, -43,945100": a virgula fazia papel de
            // separador decimal E de separador do par, na legenda queimada na imagem que vai
            // para o processo. Quem copiasse aquilo para outro sistema nao teria como saber
            // onde termina a latitude.
            linhas += "GEO  %.6f, %.6f  (SIRGAS 2000)".format(Locale.US, foto.lat, foto.lon)
        }

        val partes = mutableListOf<String>()
        if (!semPosicao) partes += "Precisao ±%.0f m".format(foto.precisaoM)
        foto.altitudeM?.let { partes += "Alt %.0f m".format(it) }
        foto.azimuteGraus?.let { partes += "Azimute %.0f° (%s)".format(it, rosa(it)) }
        if (partes.isNotEmpty()) linhas += partes.joinToString("  ")
        linhas += fmtData.get()!!.format(Date(foto.instante))
        foto.endereco?.let { linhas += it }
        foto.tipoOcorrencia?.let { linhas += "Ocorrência: $it" }
        linhas += "SHA-256 ${foto.sha256.take(32)}..."

        return linhas
    }

    private fun rosa(azimute: Float): String {
        val dir = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        return dir[(((azimute + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)]
    }
}
