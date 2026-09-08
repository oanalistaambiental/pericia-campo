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

    // SimpleDateFormat NAO e thread-safe, e uma instancia de `object` e compartilhada por todo
    // o app. A legenda e gerada no fluxo de captura enquanto um export pode estar rodando em
    // paralelo; o resultado de uma corrida em SimpleDateFormat nao e excecao — e uma data
    // silenciosamente errada, carimbada na imagem que vai para o processo. ThreadLocal da uma
    // instancia por thread e resolve sem trocar a API.
    // O formato ISO ganhou o fuso (XXX -> "-03:00"). Sem ele, o <TimeStamp> do KML saia com a
    // hora local do aparelho e sem dizer de que fuso — num documento probatorio, data/hora sem
    // fuso e ambigua, e quem abrisse o KMZ em outro fuso via horarios deslocados.
    private val fmtIso = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    }
    private val fmtBr = ThreadLocal.withInitial {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
    }

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
                // Mesmo defeito de string de formato de TelaSessoes: aqui a excecao era
                // engolida pelo runCatching do export e virava "Falha ao exportar:
                // Conversion = ' '". O laudo nao saia e ninguem entendia por que.
                it.camada + " (" + it.fonte + "): " + it.situacao + " " +
                    "%.0f".format(Locale.US, it.distanciaM) + "m"
            }
            sb.append(
                listOf(
                    File(f.arquivoOriginal).name,
                    fmtBr.get()!!.format(Date(f.instante)),
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

    /**
     * Duas coisas alem do escape de CSV normal.
     *
     * 1. `\r` sozinho tambem quebra a linha. Uma observacao colada do Windows trazia o par
     *    `\r\n`, so o `\n` era tratado, e o `\r` restante partia o registro ao meio na
     *    planilha — a partir dali todas as colunas saem trocadas.
     * 2. Texto comecando por `=`, `+`, `-` ou `@` e interpretado como FORMULA pelo Excel e pelo
     *    LibreOffice. A observacao do perito vai inteira para esta coluna; um apostrofo na
     *    frente faz a celula ser lida como texto, que e o que ela e.
     */
    private fun escapar(v: String): String {
        val limpo = v.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ')
        val seguro = if (limpo.isNotEmpty() && limpo[0] in "=+-@") "'" + limpo else limpo
        return if (seguro.contains(';') || seguro.contains('"'))
            "\"" + seguro.replace("\"", "\"\"") + "\""
        else seguro
    }

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
                append("Data/hora: ").append(fmtBr.get()!!.format(Date(f.instante))).append("\n")
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
            // Dentro de CDATA nao se escapa XML: `xml(desc)` fazia o Google Earth exibir
            // "&amp;" literal onde o perito digitou "&". O que PRECISA ser tratado e a unica
            // sequencia que fecha o CDATA por engano — um "]]>" na observacao arrebentava o
            // KML no meio e o KMZ nao abria mais, sem erro nenhum que explicasse.
            kml.append("<description><![CDATA[<img src=\"").append(nomeImg)
               .append("\" width=\"480\"/><pre>").append(protegerCdata(desc)).append("</pre>]]></description>\n")
            kml.append("<TimeStamp><when>").append(fmtIso.get()!!.format(Date(f.instante))).append("</when></TimeStamp>\n")
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

    /** Quebra o unico fechamento possivel de CDATA, sem mexer no resto do texto. */
    private fun protegerCdata(v: String): String = v.replace("]]>", "]]]]><![CDATA[>")

    // ------------------------------------------------------- compartilhamento

    /**
     * @throws IllegalStateException quando nenhum dos arquivos existe mais.
     *
     * Antes era `return` mudo: o perito tocava em "compartilhar originais", nao acontecia
     * nada — nenhum seletor, nenhuma mensagem, nenhuma pista — e ele nao tinha como saber que
     * os arquivos tinham sumido. Falha silenciosa e pior que erro.
     */
    fun compartilhar(context: Context, arquivos: List<File>, assunto: String) {
        val existentes = arquivos.filter { it.exists() }
        check(existentes.isNotEmpty()) {
            "Nenhum dos ${arquivos.size} arquivos foi encontrado no aparelho."
        }
        val faltando = arquivos.size - existentes.size
        val uris = ArrayList<Uri>(existentes.map {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
        })

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
                (if (faltando > 0)
                    "AVISO: $faltando arquivo(s) desta sessão não foram encontrados no " +
                        "aparelho e NÃO estão neste envio.\n\n"
                else "") +
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
        // Sem isto a prova de integridade saia como "*/*", e varios aplicativos de e-mail
        // e mensagem recusam anexo de tipo desconhecido — o documento existia e nao chegava.
        "txt" -> "text/plain"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "*/*"
    }
}
