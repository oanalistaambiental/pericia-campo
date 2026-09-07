package br.com.oanalistaambiental.pericia.exportacao

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import br.com.oanalistaambiental.pericia.dados.Banco
import br.com.oanalistaambiental.pericia.dados.Foto
import br.com.oanalistaambiental.pericia.dados.Sessao
import br.com.oanalistaambiental.pericia.geo.Utm
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exportacao: CSV, KMZ e compartilhamento.
 *
 * Tudo com biblioteca padrao — KMZ e apenas um zip contendo doc.kml, entao nao ha dependencia
 * externa para gerar arquivo que abre no Google Earth e no QGIS.
 */
object Exportador {

    private val fmtIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val fmtBr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))

    // ------------------------------------------------------------------ CSV

    fun csv(banco: Banco, fotos: List<Foto>, destino: File): File {
        val sb = StringBuilder()
        sb.append("arquivo;data_hora;latitude;longitude;datum;utm_zona;utm_e;utm_n;")
        sb.append("precisao_m;altitude_m;azimute_camera;elevacao_camera;tipo_ocorrencia;observacao;endereco;")
        sb.append("restricoes;sha256\n")

        fotos.forEach { f ->
            val semPosicao = f.lat == 0.0 && f.lon == 0.0
            val utm = if (semPosicao) null else Utm.projetar(f.lat, f.lon)
            val restr = banco.restricoesDaFoto(f.id).joinToString(" | ") {
                "${it.camada} (${it.fonte}): ${it.situacao} %.0fm".format(it.distanciaM)
            }
            sb.append(
                listOf(
                    File(f.arquivoOriginal).name,
                    fmtBr.format(Date(f.instante)),
                    if (semPosicao) "" else "%.7f".format(Locale.US, f.lat),
                    if (semPosicao) "" else "%.7f".format(Locale.US, f.lon),
                    if (semPosicao) "" else "SIRGAS 2000 (EPSG:4674)",
                    utm?.let { "${it.zona}${if (it.hemisferioSul) "S" else "N"}" } ?: "",
                    utm?.let { "%.2f".format(Locale.US, it.easting) } ?: "",
                    utm?.let { "%.2f".format(Locale.US, it.northing) } ?: "",
                    if (semPosicao) "" else "%.1f".format(Locale.US, f.precisaoM),
                    f.altitudeM?.let { "%.1f".format(Locale.US, it) } ?: "",
                    f.azimuteGraus?.let { "%.0f".format(Locale.US, it) } ?: "",
                    f.inclinacaoGraus?.let { "%.0f".format(Locale.US, it) } ?: "",
                    f.tipoOcorrencia ?: "",
                    f.observacao ?: "",
                    f.endereco ?: "",
                    restr,
                    f.sha256
                ).joinToString(";") { escapar(it) }
            )
            sb.append("\n")
        }
        // BOM para o Excel brasileiro abrir com acentuacao correta
        destino.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + sb.toString().toByteArray(Charsets.UTF_8))
        return destino
    }

    private fun escapar(v: String): String =
        if (v.contains(';') || v.contains('"') || v.contains('\n'))
            "\"" + v.replace("\"", "\"\"").replace("\n", " ") + "\""
        else v

    // ------------------------------------------------------------------ KMZ

    fun kmz(banco: Banco, sessao: Sessao, fotos: List<Foto>, destino: File): File {
        val kml = StringBuilder()
        kml.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        kml.append("""<kml xmlns="http://www.opengis.net/kml/2.2"><Document>""").append("\n")
        kml.append("<name>").append(xml(sessao.titulo)).append("</name>\n")
        sessao.processo?.let { kml.append("<description>").append(xml("Processo: $it")).append("</description>\n") }

        fotos.forEach { f ->
            // Ponto sem coordenada nao entra no KMZ: um marcador em 0,0 cairia no golfo da
            // Guine, o que e pior que a ausencia do ponto.
            if (f.lat == 0.0 && f.lon == 0.0) return@forEach
            val utm = Utm.projetar(f.lat, f.lon)
            val restr = banco.restricoesDaFoto(f.id)
            val desc = buildString {
                append("Data/hora: ").append(fmtBr.format(Date(f.instante))).append("\n")
                append("UTM SIRGAS 2000: ").append(utm.formatado()).append("\n")
                append("Precisao: ±").append("%.0f".format(f.precisaoM)).append(" m\n")
                f.altitudeM?.let { append("Altitude: ").append("%.0f".format(it)).append(" m\n") }
                f.azimuteGraus?.let { append("Azimute: ").append("%.0f".format(it)).append("°\n") }
                f.tipoOcorrencia?.let { append("Ocorrencia: ").append(it).append("\n") }
                f.observacao?.let { append("Observacao: ").append(it).append("\n") }
                if (restr.isNotEmpty()) {
                    append("\nIndicios de restricao:\n")
                    restr.forEach { r ->
                        append("- ").append(r.camada).append(" (").append(r.fonte).append("): ")
                        append(r.situacao).append(", ").append("%.0f".format(r.distanciaM)).append(" m")
                        append(" [base de ").append(r.dataExtracao).append("]\n")
                    }
                }
                append("\nSHA-256: ").append(f.sha256)
            }
            val nomeImg = File(f.arquivoComLegenda ?: f.arquivoOriginal).name

            kml.append("<Placemark>\n")
            kml.append("<name>").append(xml(f.tipoOcorrencia ?: "Registro")).append("</name>\n")
            kml.append("<description><![CDATA[<img src=\"").append(nomeImg)
               .append("\" width=\"480\"/><pre>").append(xml(desc)).append("</pre>]]></description>\n")
            kml.append("<TimeStamp><when>").append(fmtIso.format(Date(f.instante))).append("</when></TimeStamp>\n")
            kml.append("<Point><coordinates>")
               .append("%.7f,%.7f".format(Locale.US, f.lon, f.lat))
               .append(f.altitudeM?.let { ",%.1f".format(Locale.US, it) } ?: "")
               .append("</coordinates></Point>\n")
            kml.append("</Placemark>\n")
        }
        kml.append("</Document></kml>\n")

        ZipOutputStream(FileOutputStream(destino)).use { zip ->
            zip.putNextEntry(ZipEntry("doc.kml"))
            zip.write(kml.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            fotos.forEach { f ->
                val img = File(f.arquivoComLegenda ?: f.arquivoOriginal)
                if (img.exists()) {
                    zip.putNextEntry(ZipEntry(img.name))
                    img.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return destino
    }

    private fun xml(v: String): String =
        v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // ------------------------------------------------------- compartilhamento

    fun compartilhar(context: Context, arquivos: List<File>, assunto: String) {
        val uris = ArrayList<Uri>(arquivos.filter { it.exists() }.map {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
        })
        if (uris.isEmpty()) return

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = tipoDe(arquivos.first())
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, assunto)
        intent.putExtra(
            Intent.EXTRA_TEXT,
            "Enviado pelo Perícia Campo.\n\n" +
                "ATENÇÃO: aplicativos de mensagem recomprimem imagens e removem metadados. " +
                "Um arquivo enviado por esses canais deixa de conferir com o hash registrado. " +
                "Para preservar o valor probatório, entregue os originais em mídia ou por canal " +
                "que não reprocesse o arquivo."
        )
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Compartilhar").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun tipoDe(f: File) = when (f.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "csv" -> "text/csv"
        "kmz" -> "application/vnd.google-earth.kmz"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "*/*"
    }
}
