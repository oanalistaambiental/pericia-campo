package br.com.oanalistaambiental.pericia.laudo

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import br.com.oanalistaambiental.pericia.dados.Banco
import br.com.oanalistaambiental.pericia.dados.Foto
import br.com.oanalistaambiental.pericia.dados.Sessao
import br.com.oanalistaambiental.pericia.geo.Utm
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Laudo fotografico em PDF, gerado com a API nativa do Android (sem dependencia externa).
 *
 * Estrutura: capa -> galeria com legenda e restricoes -> relatorio de integridade.
 *
 * O relatorio de integridade traz o comando de verificacao escrito por extenso, de proposito:
 * uma prova que qualquer pessoa consegue conferir SEM o app vale mais que uma prova que
 * depende do app que a gerou.
 */
object LaudoPdf {

    private const val LARGURA = 595   // A4 72dpi
    private const val ALTURA = 842
    private const val MARGEM = 40f
    private val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))

    fun gerar(banco: Banco, sessao: Sessao, fotos: List<Foto>, destino: File): File {
        val doc = PdfDocument()
        capa(doc, sessao, fotos)
        fotos.forEachIndexed { i, f -> paginaFoto(doc, banco, sessao, f, i + 1, fotos.size) }
        integridade(doc, sessao, fotos)
        FileOutputStream(destino).use { doc.writeTo(it) }
        doc.close()
        return destino
    }

    private fun novaPagina(doc: PdfDocument, numero: Int): PdfDocument.Page =
        doc.startPage(PdfDocument.PageInfo.Builder(LARGURA, ALTURA, numero).create())

    private fun titulo(size: Float, bold: Boolean = true) = Paint().apply {
        color = Color.BLACK; textSize = size; isAntiAlias = true
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun mono(size: Float) = Paint().apply {
        color = Color.DKGRAY; textSize = size; isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }

    private fun capa(doc: PdfDocument, s: Sessao, fotos: List<Foto>) {
        val p = novaPagina(doc, 1); val c = p.canvas
        var y = 120f
        c.drawText("LAUDO FOTOGRÁFICO DE VISTORIA", MARGEM, y, titulo(18f)); y += 40f
        c.drawText(s.titulo, MARGEM, y, titulo(14f, false)); y += 28f
        s.processo?.let { c.drawText("Processo/Auto: $it", MARGEM, y, titulo(11f, false)); y += 20f }
        c.drawText("Início: ${fmt.format(Date(s.criadaEm))}", MARGEM, y, titulo(11f, false)); y += 20f
        c.drawText("Registros fotográficos: ${fotos.size}", MARGEM, y, titulo(11f, false)); y += 20f
        c.drawText("Datum de referência: SIRGAS 2000 (EPSG:4674)", MARGEM, y, titulo(11f, false)); y += 40f

        val aviso = listOf(
            "Este documento reúne registros fotográficos georreferenciados produzidos em campo.",
            "Cada imagem possui código hash SHA-256 calculado no momento da captura, permitindo",
            "verificar posteriormente se o arquivo foi alterado. As indicações de restrição",
            "ambiental são INDÍCIOS obtidos por consulta a bases públicas, sujeitos à precisão do",
            "receptor GNSS e à data de extração das camadas — não substituem a análise técnica."
        )
        aviso.forEach { c.drawText(it, MARGEM, y, titulo(9f, false)); y += 14f }
        doc.finishPage(p)
    }

    private fun paginaFoto(doc: PdfDocument, banco: Banco, s: Sessao, f: Foto, n: Int, total: Int) {
        val p = novaPagina(doc, n + 1); val c = p.canvas
        var y = MARGEM + 14f
        c.drawText("Registro $n de $total", MARGEM, y, titulo(12f)); y += 20f

        val arq = File(f.arquivoComLegenda ?: f.arquivoOriginal)
        if (arq.exists()) {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(arq.absolutePath, opts)?.let { bmp ->
                val larguraMax = LARGURA - 2 * MARGEM
                val altura = larguraMax * bmp.height / bmp.width
                c.drawBitmap(bmp, null, Rect(MARGEM.toInt(), y.toInt(),
                    (MARGEM + larguraMax).toInt(), (y + altura).toInt()), null)
                y += altura + 16f
                bmp.recycle()
            }
        }

        val utm = Utm.projetar(f.lat, f.lon)
        val dados = mutableListOf(
            "UTM SIRGAS2000: ${utm.formatado()}",
            "Geográfica: %.6f, %.6f".format(f.lat, f.lon),
            "Precisão do GNSS: ±%.0f m".format(f.precisaoM),
            "Data/hora: ${fmt.format(Date(f.instante))}"
        )
        f.altitudeM?.let { dados += "Altitude: %.0f m".format(it) }
        f.azimuteGraus?.let { dados += "Azimute da câmera: %.0f°".format(it) }
        f.tipoOcorrencia?.let { dados += "Ocorrência: $it" }
        f.observacao?.takeIf { it.isNotBlank() }?.let { dados += "Observação: $it" }
        dados.forEach { c.drawText(it, MARGEM, y, titulo(9f, false)); y += 13f }

        val restr = banco.restricoesDaFoto(f.id)
        if (restr.isNotEmpty()) {
            y += 8f
            c.drawText("Indícios de restrição ambiental:", MARGEM, y, titulo(9f)); y += 13f
            restr.forEach { r ->
                val txt = "• ${r.camada} (${r.fonte}) — ${r.situacao}, %.0f m".format(r.distanciaM)
                c.drawText(txt, MARGEM + 8f, y, titulo(8.5f, false)); y += 12f
                c.drawText("  base de ${r.dataExtracao}, pacote ${r.pacoteVersao}, simplificação ${r.toleranciaM} m",
                    MARGEM + 8f, y, mono(7.5f)); y += 12f
            }
        }
        y += 6f
        c.drawText("SHA-256: ${f.sha256}", MARGEM, y, mono(7f))
        doc.finishPage(p)
    }

    private fun integridade(doc: PdfDocument, s: Sessao, fotos: List<Foto>) {
        val p = novaPagina(doc, fotos.size + 2); val c = p.canvas
        var y = MARGEM + 14f
        c.drawText("RELATÓRIO DE INTEGRIDADE", MARGEM, y, titulo(14f)); y += 26f

        listOf(
            "Cada arquivo original desta vistoria teve seu código hash SHA-256 calculado no",
            "instante da captura, antes de qualquer processamento. Conferir os códigos abaixo",
            "contra os arquivos entregues demonstra que não houve alteração.",
            "",
            "Como verificar (não é preciso ter o aplicativo):",
            "   Linux/macOS:  sha256sum ARQUIVO.jpg",
            "   Windows:      certutil -hashfile ARQUIVO.jpg SHA256",
            "",
            "ATENÇÃO: aplicativos de mensagem e clientes de e-mail recomprimem imagens e removem",
            "metadados. Um arquivo trafegado por esses canais NÃO confere mais com o hash. Os",
            "originais devem ser entregues em mídia ou canal que não reprocesse o arquivo."
        ).forEach { c.drawText(it, MARGEM, y, titulo(9f, false)); y += 13f }

        y += 10f
        s.raizMerkle?.let {
            c.drawText("Raiz de Merkle da sessão:", MARGEM, y, titulo(9f)); y += 12f
            c.drawText(it, MARGEM, y, mono(7f)); y += 18f
        }
        s.carimboTempo?.let {
            c.drawText("Carimbo do tempo (RFC 3161) aplicado sobre a raiz.", MARGEM, y, titulo(9f)); y += 18f
        }

        c.drawText("Arquivos e respectivos hashes:", MARGEM, y, titulo(9f)); y += 14f
        fotos.forEach { f ->
            if (y > ALTURA - MARGEM) return@forEach
            c.drawText(File(f.arquivoOriginal).name, MARGEM, y, mono(7.5f)); y += 10f
            c.drawText(f.sha256, MARGEM + 10f, y, mono(7f)); y += 14f
        }
        doc.finishPage(p)
    }
}
