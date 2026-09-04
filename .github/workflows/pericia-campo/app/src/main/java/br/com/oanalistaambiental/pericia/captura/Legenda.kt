package br.com.oanalistaambiental.pericia.captura

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import br.com.oanalistaambiental.pericia.dados.Foto
import br.com.oanalistaambiental.pericia.geo.Utm
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Grava a legenda tecnica sobre uma CÓPIA da imagem.
 *
 * Regra que nao se quebra: o arquivo original, ja com hash calculado, nunca e tocado. O que
 * recebe legenda e uma copia. Estilo "documento tecnico", nao "turismo" — e o que separa este
 * app dos genericos de camera com GPS.
 */
object Legenda {

    private val fmtData = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))

    fun gerar(original: File, destino: File, foto: Foto, sessaoTitulo: String): File {
        val bmp = BitmapFactory.decodeFile(original.absolutePath)
            ?: throw IllegalStateException("Não foi possível ler a imagem original")
        val copia = bmp.copy(Bitmap.Config.ARGB_8888, true)
        bmp.recycle()

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

        FileOutputStream(destino).use { copia.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        copia.recycle()
        return destino
    }

    private fun montarLinhas(foto: Foto, sessaoTitulo: String): List<String> {
        val utm = Utm.projetar(foto.lat, foto.lon)
        val linhas = mutableListOf<String>()

        linhas += sessaoTitulo.uppercase()
        linhas += "UTM SIRGAS2000  ${utm.formatado()}"
        linhas += "GEO  %.6f, %.6f  (SIRGAS 2000)".format(foto.lat, foto.lon)

        val partes = mutableListOf<String>()
        partes += "Precisao ±%.0f m".format(foto.precisaoM)
        foto.altitudeM?.let { partes += "Alt %.0f m".format(it) }
        foto.azimuteGraus?.let { partes += "Azimute %.0f° (%s)".format(it, rosa(it)) }
        linhas += partes.joinToString("  ")

        linhas += fmtData.format(Date(foto.instante))
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
