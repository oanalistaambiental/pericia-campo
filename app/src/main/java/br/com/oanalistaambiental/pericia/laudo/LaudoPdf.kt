package br.com.oanalistaambiental.pericia.laudo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import br.com.oanalistaambiental.pericia.carimbo.CarimboTempo
import br.com.oanalistaambiental.pericia.captura.ConferenciaSessao
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

    /**
     * Quebra o texto em linhas que cabem em [larguraMax], medindo com a fonte real.
     *
     * Quebra por espaco; palavra unica maior que a coluna (um caminho de arquivo, um hash) e
     * partida por caractere, porque deixar vazar pela margem seria trocar um defeito por
     * outro. Preserva as quebras que o proprio perito digitou.
     */
    internal fun quebrar(texto: String, p: Paint, larguraMax: Float): List<String> {
        if (larguraMax <= 0f) return listOf(texto)
        val saida = mutableListOf<String>()
        for (paragrafo in texto.split("\n")) {
            if (p.measureText(paragrafo) <= larguraMax) { saida += paragrafo; continue }
            var atual = StringBuilder()
            for (palavra in paragrafo.split(" ")) {
                val tentativa = if (atual.isEmpty()) palavra else "$atual $palavra"
                if (p.measureText(tentativa) <= larguraMax) {
                    atual = StringBuilder(tentativa)
                    continue
                }
                if (atual.isNotEmpty()) { saida += atual.toString(); atual = StringBuilder() }
                if (p.measureText(palavra) <= larguraMax) {
                    atual = StringBuilder(palavra)
                } else {
                    var pedaco = StringBuilder()
                    for (ch in palavra) {
                        if (p.measureText(pedaco.toString() + ch) > larguraMax && pedaco.isNotEmpty()) {
                            saida += pedaco.toString(); pedaco = StringBuilder()
                        }
                        pedaco.append(ch)
                    }
                    atual = pedaco
                }
            }
            if (atual.isNotEmpty()) saida += atual.toString()
        }
        return if (saida.isEmpty()) listOf("") else saida
    }


    private const val LARGURA = 595   // A4 72dpi
    private const val ALTURA = 842
    private const val MARGEM = 40f
    /** O carimbo e mostrado em UTC, como a Autoridade o declarou — nao no fuso do aparelho. */
    private val fmtUtc = ThreadLocal.withInitial {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
    }

    private val fmt = ThreadLocal.withInitial {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
    }

    fun gerar(banco: Banco, sessao: Sessao, fotos: List<Foto>, destino: File): File {
        val doc = PdfDocument()
        // O `finally` nao e zelo: sem ele, um OutOfMemoryError decodificando uma foto de
        // 50 MP, ou o armazenamento enchendo no meio do writeTo, deixava a memoria nativa do
        // PdfDocument retida E um PDF pela metade no disco — arquivo que depois podia ser
        // compartilhado como se fosse o laudo. Agora o parcial e apagado.
        try {
            capa(doc, sessao, fotos)
            fotos.forEachIndexed { i, f -> paginaFoto(doc, banco, f, i + 1, fotos.size) }
            integridade(doc, sessao, fotos)
            // Observacao: o numero de pagina do PdfDocument e apenas um indice interno;
            // paginas que transbordam recebem indices adicionais e a ordem de escrita e
            // preservada.
            FileOutputStream(destino).use { doc.writeTo(it) }
        } catch (e: Throwable) {
            runCatching { destino.delete() }
            throw e
        } finally {
            runCatching { doc.close() }
        }
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
        c.drawText("Início: ${fmt.get()!!.format(Date(s.criadaEm))}", MARGEM, y, titulo(11f, false)); y += 20f
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

    private fun paginaFoto(doc: PdfDocument, banco: Banco, f: Foto, n: Int, total: Int) {
        var pagina = novaPagina(doc, n + 1)
        var c = pagina.canvas
        var y = MARGEM + 14f
        var extras = 0
        c.drawText("Registro $n de $total", MARGEM, y, titulo(12f)); y += 20f

        val arq = File(f.arquivoComLegenda ?: f.arquivoOriginal)
        if (arq.exists()) {
            decodificarOrientado(arq, 2)?.let { bmp ->
                val larguraMax = LARGURA - 2 * MARGEM
                // Nao deixa a imagem sozinha ocupar a pagina inteira: sobra espaco para os dados.
                val alturaMax = ALTURA * 0.52f
                var largura = larguraMax
                var altura = larguraMax * bmp.height / bmp.width
                if (altura > alturaMax) {
                    largura = alturaMax * bmp.width / bmp.height
                    altura = alturaMax
                }
                val esquerda = MARGEM + (larguraMax - largura) / 2f
                c.drawBitmap(bmp, null, Rect(
                    esquerda.toInt(), y.toInt(), (esquerda + largura).toInt(), (y + altura).toInt()
                ), null)
                y += altura + 16f
                bmp.recycle()
            }
        }

        // BUG corrigido: as linhas abaixo eram desenhadas sem checar o fim da pagina, entao em
        // foto em retrato o hash e as ultimas restricoes sumiam do laudo, em silencio.
        fun linhaCrua(texto: String, p: Paint, recuo: Float = 0f) {
            if (y > ALTURA - MARGEM - 14f) {
                doc.finishPage(pagina)
                extras += 1
                pagina = novaPagina(doc, n + 1 + extras)
                c = pagina.canvas
                y = MARGEM + 14f
                c.drawText("Registro $n de $total (continuação)", MARGEM, y, titulo(10f))
                y += 20f
            }
            c.drawText(texto, MARGEM + recuo, y, p)
            y += 13f
        }

        /**
         * BUG corrigido: transbordo HORIZONTAL.
         *
         * O transbordo vertical ja era tratado (é o comentario acima), mas `drawText` desenha
         * uma linha so e o que passa da largura da pagina simplesmente nao aparece — sem
         * reticencias, sem aviso. Uma observacao de 170 caracteres, dessas que o perito
         * escreve de verdade ("Corte raso de vegetacao nativa em estagio medio..."), saia
         * cortada nos primeiros ~95 e o resto desaparecia do laudo. Agora quebra em quantas
         * linhas precisar, medindo com a propria fonte.
         */
        fun linha(texto: String, p: Paint, recuo: Float = 0f) {
            val disponivel = LARGURA - 2 * MARGEM - recuo
            for (parte in quebrar(texto, p, disponivel)) linhaCrua(parte, p, recuo)
        }

        val semPosicao = f.lat == 0.0 && f.lon == 0.0
        if (semPosicao) {
            linha("SEM POSIÇÃO GNSS NO MOMENTO DA CAPTURA", titulo(9f))
        } else {
            val utm = Utm.projetar(f.lat, f.lon)
            linha("UTM SIRGAS 2000: ${utm.formatado()}", titulo(9f, false))
            // Mesma razao da legenda: coordenada e dado tecnico e leva ponto decimal.
            linha("Geográfica: %.6f, %.6f".format(Locale.US, f.lat, f.lon), titulo(9f, false))
            linha("Precisão do GNSS: ±%.0f m".format(f.precisaoM), titulo(9f, false))
        }
        linha("Data/hora: ${fmt.get()!!.format(Date(f.instante))}", titulo(9f, false))
        f.altitudeM?.let { linha("Altitude: %.0f m".format(it), titulo(9f, false)) }
        f.azimuteGraus?.let { az ->
            val elev = f.inclinacaoGraus?.let { ", elevação %.0f°".format(it) } ?: ""
            linha("Azimute da câmera: %.0f°%s".format(az, elev), titulo(9f, false))
        }
        f.endereco?.let { linha("Endereço: $it", titulo(9f, false)) }
        f.tipoOcorrencia?.let { linha("Ocorrência: $it", titulo(9f, false)) }
        f.observacao?.takeIf { it.isNotBlank() }?.let { linha("Observação: $it", titulo(9f, false)) }

        val restr = banco.restricoesDaFoto(f.id)
        if (restr.isNotEmpty()) {
            y += 6f
            linha("Indícios de restrição ambiental:", titulo(9f))
            restr.forEach { r ->
                linha(
                    "• " + r.camada + " (" + r.fonte + ") — " + legivel(r.situacao) + ", " +
                        "%.0f".format(Locale.US, r.distanciaM) + " m",
                    titulo(8.5f, false), 8f
                )
                linha("  base de ${r.dataExtracao}, pacote ${r.pacoteVersao}, simplificação ${r.toleranciaM} m", mono(7.5f), 8f)
            }
        }
        y += 4f
        linha("SHA-256: ${f.sha256}", mono(7f))
        doc.finishPage(pagina)
    }

    private fun legivel(situacao: String) = when (situacao) {
        "DENTRO" -> "indício de ponto interno"
        "PROXIMO_AO_LIMITE" -> "próximo ao limite, indefinido"
        else -> "fora"
    }

    /**
     * BitmapFactory ignora a orientacao EXIF, e o CameraX grava os pixels na orientacao do
     * sensor. Sem isto, toda foto em retrato sai deitada no laudo.
     */
    private fun decodificarOrientado(arquivo: File, amostragem: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inSampleSize = amostragem }
        val bmp = BitmapFactory.decodeFile(arquivo.absolutePath, opts) ?: return null
        val graus = when (
            runCatching {
                ExifInterface(arquivo.absolutePath)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (graus == 0f) return bmp
        val girado = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height,
            Matrix().apply { postRotate(graus) }, true)
        if (girado !== bmp) bmp.recycle()
        return girado
    }

    private fun integridade(doc: PdfDocument, s: Sessao, fotos: List<Foto>) {
        val p = novaPagina(doc, fotos.size + 2); val c = p.canvas
        var y = MARGEM + 14f
        c.drawText("RELATÓRIO DE INTEGRIDADE", MARGEM, y, titulo(14f)); y += 26f

        listOf(
            "Cada arquivo original desta vistoria teve seu código hash SHA-256 calculado no",
            "instante da captura, antes de qualquer processamento. Conferir os códigos abaixo",
            "contra os arquivos entregues demonstra que o ARQUIVO não foi alterado.",
            "",
            "Essa é uma das duas perguntas. A outra é se o registro pertence ao conjunto que foi",
            "selado ao encerrar a vistoria — respondida pela raiz de Merkle, e não pelo hash de",
            "um arquivo isolado. As duas são conferidas separadamente logo abaixo.",
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
        // Escreve texto corrido dentro da margem, quebrando na largura util. As funcoes
        // `escrever`/`cinza` existem no OUTRO gerador de PDF deste projeto, nao aqui — a
        // primeira versao deste bloco as chamou por engano e so o CI teria reclamado.
        fun paragrafo(texto: String, tamanho: Float = 9f, recuo: Float = 0f) {
            quebrar(texto, titulo(tamanho, false), LARGURA - 2 * MARGEM - recuo).forEach {
                c.drawText(it, MARGEM + recuo, y, titulo(tamanho, false)); y += tamanho + 4f
            }
        }

        if (s.carimboTempo == null) {
            paragrafo(
                "Carimbo do tempo (RFC 3161): NÃO aplicado. Sem ele, o conjunto está provado " +
                    "contra alteração, mas não datado por terceiro."
            )
            y += 8f
        } else {
            c.drawText("Carimbo do tempo (RFC 3161) aplicado sobre a raiz:", MARGEM, y, titulo(9f)); y += 13f
            s.carimboInstante?.let {
                paragrafo("Instante declarado pela Autoridade: ${fmtUtc.get()!!.format(Date(it))} UTC.", recuo = 8f)
            }
            paragrafo("Autoridade: ${s.carimboAutoridade ?: "não registrada"}.", recuo = 8f)
            paragrafo(
                if (s.carimboCredenciado)
                    "Declarada, por quem a configurou, como credenciada na ICP-Brasil."
                else
                    "ATENÇÃO: esta Autoridade NÃO foi declarada credenciada na ICP-Brasil. O " +
                        "carimbo serve como controle, mas não tem a fé pública que um processo " +
                        "costuma exigir.",
                recuo = 8f
            )
            paragrafo(CarimboTempo.RESSALVA_VALIDACAO, tamanho = 8.5f, recuo = 8f)
            y += 8f
        }

        // Conferencia FEITA AGORA, na geracao do laudo, e datada.
        //
        // O relatorio antigo listava os hashes e mandava o leitor conferir por conta propria.
        // Isso continua valendo — uma prova que qualquer pessoa confere sem o app vale mais
        // que uma que depende do app. Mas o laudo tambem precisa dizer o que o proprio
        // aplicativo encontrou no momento em que o documento foi emitido: se um arquivo ja
        // estava alterado na exportacao, quem recebe o PDF tem de saber disso pelo PDF, e nao
        // descobrir sozinho rodando sha256sum em vinte arquivos.
        val conf = ConferenciaSessao.conferir(
            s.raizMerkle,
            fotos.mapIndexed { i, f ->
                ConferenciaSessao.Folha(f.arquivoOriginal, f.sha256, "Registro ${i + 1}")
            }
        )
        c.drawText(
            "Conferência automática em ${fmt.get()!!.format(Date())}:",
            MARGEM, y, titulo(9f)
        ); y += 13f
        quebrar(conf.resumo(), titulo(9f, false), LARGURA - 2 * MARGEM).forEach {
            c.drawText(it, MARGEM, y, titulo(9f, false)); y += 12f
        }
        y += 4f
        c.drawText(
            "ARQUIVOS: ${conf.arquivosIntegros} de ${conf.itens.size} conferem com o hash da captura.",
            MARGEM + 10f, y, titulo(9f, false)
        ); y += 12f
        c.drawText(
            when {
                !conf.selada -> "ÁRVORE: sessão não foi encerrada — não há raiz selada."
                conf.arvoreConfere -> "ÁRVORE: os ${conf.itens.size} registros fecham na raiz selada."
                else -> "ÁRVORE: a raiz recalculada NÃO bate com a raiz selada."
            },
            MARGEM + 10f, y, titulo(9f, false)
        ); y += 16f
        quebrar(conf.RESSALVA_TEMPO, titulo(8.5f, false), LARGURA - 2 * MARGEM).forEach {
            c.drawText(it, MARGEM, y, titulo(8.5f, false)); y += 11f
        }
        y += 10f

        val estados = conf.itens.associateBy({ it.arquivo }, { it.estadoArquivo })

        // BUG corrigido: a lista era cortada em silencio quando a sessao tinha muitas fotos.
        // Agora o relatorio pagina, e nenhum hash fica de fora do laudo.
        var pagina = p
        var canvas = c
        var numero = fotos.size + 2

        // O bloco de conferencia acima tem altura variavel (o resumo muda de tamanho conforme
        // o que foi encontrado). Se ele empurrou o cursor para perto do rodape, a lista comeca
        // em pagina nova — escrever por cima da margem seria a mesma falha silenciosa que a
        // paginacao da lista ja corrigiu uma vez.
        if (y > ALTURA - MARGEM - 60f) {
            doc.finishPage(pagina)
            numero += 1
            pagina = novaPagina(doc, numero)
            canvas = pagina.canvas
            y = MARGEM + 14f
        }
        canvas.drawText("Arquivos e respectivos hashes:", MARGEM, y, titulo(9f)); y += 14f
        fotos.forEach { f ->
            if (y > ALTURA - MARGEM - 24f) {
                doc.finishPage(pagina)
                numero += 1
                pagina = novaPagina(doc, numero)
                canvas = pagina.canvas
                y = MARGEM + 14f
                canvas.drawText("Relatório de integridade (continuação)", MARGEM, y, titulo(10f))
                y += 20f
            }
            // O estado vai ao lado do nome: uma lista de hashes sem dizer quais bateram
            // obriga o leitor a refazer a conferencia inteira para achar o problema.
            val marca = when (estados[f.arquivoOriginal]) {
                ConferenciaSessao.EstadoArquivo.INTEGRO -> "confere"
                ConferenciaSessao.EstadoArquivo.ALTERADO -> "ALTERADO"
                ConferenciaSessao.EstadoArquivo.AUSENTE -> "AUSENTE"
                else -> "não conferido"
            }
            canvas.drawText("${File(f.arquivoOriginal).name}  [$marca]", MARGEM, y, mono(7.5f)); y += 10f
            canvas.drawText(f.sha256, MARGEM + 10f, y, mono(7f)); y += 14f
        }
        doc.finishPage(pagina)
    }
}
