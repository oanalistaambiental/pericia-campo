package br.com.oanalistaambiental.pericia.enquadramento.laudo

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import br.com.oanalistaambiental.pericia.enquadramento.norma.Dispensa
import br.com.oanalistaambiental.pericia.enquadramento.norma.Enquadramento
import br.com.oanalistaambiental.pericia.enquadramento.norma.Regras
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Relatório da simulação em PDF.
 *
 * O documento leva a memória de cálculo inteira e o aviso de independência — é o que permite
 * anexar a simulação a um parecer sem que ela seja lida como decisão do órgão.
 */
object SimulacaoPdf {

    private const val LARGURA = 595
    private const val ALTURA = 842
    private const val MARGEM = 44f
    private val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

    /**
     * PDF do resultado de DISPENSA (art. 10).
     *
     * É o documento que banco e instituição financeira costumam pedir como "declaração de
     * dispensa de licenciamento". Por isso a estrutura é deliberada: o resultado aparece uma
     * vez, e os três deveres do parágrafo único ocupam a maior parte da folha. Um PDF em que
     * "dispensado" aparece grande e as obrigações aparecem em rodapé seria pior que nenhum.
     */
    fun gerarDispensa(regras: Regras, d: Dispensa.Resultado, destino: File): File {
        val doc = PdfDocument()
        var numero = 1
        var pagina = doc.startPage(PdfDocument.PageInfo.Builder(LARGURA, ALTURA, numero).create())
        var c: Canvas = pagina.canvas
        var y = MARGEM + 12f

        fun titulo(size: Float, bold: Boolean = true) = Paint().apply {
            color = Color.BLACK; textSize = size; isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        fun cinza(size: Float) = Paint().apply {
            color = Color.DKGRAY; textSize = size; isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        fun quebrar(texto: String, p: Paint, largura: Float): List<String> {
            val linhas = mutableListOf<String>()
            for (paragrafo in texto.split("\n")) {
                var atual = StringBuilder()
                for (palavra in paragrafo.split(" ")) {
                    val teste = if (atual.isEmpty()) palavra else "$atual $palavra"
                    when {
                        p.measureText(teste) <= largura -> atual = StringBuilder(teste)
                        atual.isNotEmpty() -> { linhas += atual.toString(); atual = StringBuilder(palavra) }
                        else -> {
                            var pedaco = StringBuilder()
                            for (ch in palavra) {
                                if (p.measureText(pedaco.toString() + ch) > largura && pedaco.isNotEmpty()) {
                                    linhas += pedaco.toString(); pedaco = StringBuilder()
                                }
                                pedaco.append(ch)
                            }
                            atual = pedaco
                        }
                    }
                }
                if (atual.isNotEmpty()) linhas += atual.toString()
            }
            return if (linhas.isEmpty()) listOf("") else linhas
        }
        fun escrever(texto: String, p: Paint, recuo: Float = 0f, espaco: Float = 14f) {
            quebrar(texto, p, LARGURA - 2 * MARGEM - recuo).forEach { linha ->
                if (y > ALTURA - MARGEM) {
                    doc.finishPage(pagina)
                    numero += 1
                    pagina = doc.startPage(PdfDocument.PageInfo.Builder(LARGURA, ALTURA, numero).create())
                    c = pagina.canvas
                    y = MARGEM + 12f
                }
                c.drawText(linha, MARGEM + recuo, y, p)
                y += espaco
            }
        }

        try {
            escrever("SIMULAÇÃO DE ENQUADRAMENTO AMBIENTAL", titulo(16f), espaco = 24f)
            escrever(regras.procedencia["norma"] ?: "", cinza(10f))
            escrever("Gerada em ${fmt.format(Date())}", cinza(10f), espaco = 22f)

            escrever("RESULTADO", titulo(12f), espaco = 18f)
            escrever(d.titulo, titulo(12f, false), espaco = 18f)
            escrever(d.fundamento, cinza(10f), espaco = 14f)
            d.atividade?.let {
                escrever("Atividade: ${it.codigo} — ${it.descricao}", cinza(10f), espaco = 13f)
            }
            d.valorInformado?.let { escrever("Informado: ${it.descricao()}", cinza(10f), espaco = 13f) }
            y += 10f

            escrever("A DISPENSA NÃO EXIME O EMPREENDEDOR DO DEVER DE:", titulo(12f), espaco = 16f)
            escrever(
                "DN COPAM 217/2017, art. 10, parágrafo único. A dispensa é do processo de " +
                    "licenciamento ambiental no âmbito estadual — e de mais nada.",
                cinza(9.5f), espaco = 13f
            )
            y += 8f
            d.deveres.forEach { dev ->
                escrever("${dev.inciso} — ${dev.titulo}", titulo(10.5f, false), espaco = 14f)
                escrever(dev.texto, cinza(9.5f), recuo = 10f, espaco = 12f)
                escrever("Exemplos do que costuma incidir:", cinza(9f), recuo = 10f, espaco = 12f)
                dev.exemplos.forEach { escrever("• $it", cinza(9.5f), recuo = 18f, espaco = 12f) }
                y += 8f
            }
            escrever(
                "A lista de exemplos não é exaustiva e não consta da norma: traduz o que " +
                    "costuma incidir em Minas Gerais. Confira o caso concreto.",
                cinza(9f), espaco = 12f
            )
            y += 10f

            escrever("ATENÇÃO", titulo(12f), espaco = 16f)
            d.avisos.forEach { escrever("• $it", cinza(9.5f), recuo = 8f, espaco = 12f); y += 4f }

            FileOutputStream(destino).use { doc.writeTo(it) }
        } catch (e: Throwable) {
            runCatching { destino.delete() }
            throw e
        } finally {
            runCatching { doc.close() }
        }
        return destino
    }

    fun gerar(regras: Regras, r: Enquadramento.Resultado, destino: File): File {
        val doc = PdfDocument()
        var numero = 1
        var pagina = doc.startPage(PdfDocument.PageInfo.Builder(LARGURA, ALTURA, numero).create())
        var c: Canvas = pagina.canvas
        var y = MARGEM + 12f

        fun titulo(size: Float, bold: Boolean = true) = Paint().apply {
            color = Color.BLACK; textSize = size; isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        fun cinza(size: Float) = Paint().apply {
            color = Color.DKGRAY; textSize = size; isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        // Quebra so no espaco deixava vazar pela margem direita qualquer palavra maior que a
        // coluna — um caminho de arquivo, um codigo longo colado numa nota — e ignorava as
        // quebras de linha que a propria base traz.
        fun quebrar(texto: String, p: Paint, largura: Float): List<String> {
            val linhas = mutableListOf<String>()
            for (paragrafo in texto.split("\n")) {
                var atual = StringBuilder()
                for (palavra in paragrafo.split(" ")) {
                    val teste = if (atual.isEmpty()) palavra else "$atual $palavra"
                    when {
                        p.measureText(teste) <= largura -> atual = StringBuilder(teste)
                        atual.isNotEmpty() -> { linhas += atual.toString(); atual = StringBuilder(palavra) }
                        else -> {
                            var pedaco = StringBuilder()
                            for (ch in palavra) {
                                if (p.measureText(pedaco.toString() + ch) > largura && pedaco.isNotEmpty()) {
                                    linhas += pedaco.toString(); pedaco = StringBuilder()
                                }
                                pedaco.append(ch)
                            }
                            atual = pedaco
                        }
                    }
                }
                if (atual.isNotEmpty()) linhas += atual.toString()
            }
            return if (linhas.isEmpty()) listOf("") else linhas
        }

        fun escrever(texto: String, p: Paint, recuo: Float = 0f, espaco: Float = 14f) {
            quebrar(texto, p, LARGURA - 2 * MARGEM - recuo).forEach { linha ->
                if (y > ALTURA - MARGEM) {
                    doc.finishPage(pagina)
                    numero += 1
                    pagina = doc.startPage(PdfDocument.PageInfo.Builder(LARGURA, ALTURA, numero).create())
                    c = pagina.canvas
                    y = MARGEM + 12f
                }
                c.drawText(linha, MARGEM + recuo, y, p)
                y += espaco
            }
        }

        escrever("SIMULAÇÃO DE ENQUADRAMENTO AMBIENTAL", titulo(16f), espaco = 24f)
        escrever(regras.procedencia["norma"] ?: "", cinza(10f))
        escrever("Gerada em ${fmt.format(Date())}", cinza(10f), espaco = 22f)

        escrever("RESULTADO", titulo(12f), espaco = 18f)
        escrever("Modalidade: ${r.modalidade.sigla} — ${r.modalidade.nome}", titulo(11f, false))
        escrever("Classe ${r.classe} · porte ${r.porte.extenso} · potencial poluidor geral " +
            "${r.potencialGeral.extenso} · critério locacional peso ${r.fatorLocacional}", cinza(10f))
        escrever("Prazo de análise: ${r.prazoAnaliseDias} dias. ${r.prazoAnaliseTexto}", cinza(10f))
        escrever(
            "Validade: " + if (r.modalidade.validadePorLicenca.isEmpty())
                "${r.modalidade.validadeAnos} anos"
            else r.modalidade.validadePorLicenca.entries.sortedBy { it.value }
                .joinToString(", ") { "${it.key} ${it.value} anos" },
            cinza(10f)
        )
        escrever(r.modalidade.validadeTexto, cinza(9.5f), espaco = 20f)

        // BLOCO NOVO. Faltava por completo, e sem ele o PDF nao permitia refazer a conta nem
        // dizia de onde vieram os criterios — que e justamente o que o art. 6o, par. 5o manda
        // documentar. Um parecer que nao mostra a entrada nao instrui processo.
        escrever("DADOS INFORMADOS", titulo(12f), espaco = 16f)
        r.valorInformado?.let { escrever("• ${it.descricao()}", cinza(10f), recuo = 8f, espaco = 12f) }
        r.coordenadaConsultada?.let { (lat, lon) ->
            escrever(
                "• Coordenada consultada: %.6f, %.6f (SIRGAS 2000)".format(Locale.US, lat, lon),
                cinza(10f), recuo = 8f, espaco = 12f
            )
        }
        r.versaoPacote?.let {
            escrever("• Pacote de camadas: versão $it", cinza(10f), recuo = 8f, espaco = 12f)
        }
        if (r.criteriosAutomaticos.isEmpty()) {
            escrever(
                "• Todos os critérios locacionais foram marcados manualmente por quem simulou.",
                cinza(10f), recuo = 8f, espaco = 12f
            )
        } else {
            escrever(
                "• Critérios sugeridos pela consulta de camadas (confirmados por quem simulou): " +
                    r.criteriosAutomaticos.joinToString(", "),
                cinza(10f), recuo = 8f, espaco = 12f
            )
        }
        y += 8f

        escrever("ESTUDOS EXIGIDOS", titulo(12f), espaco = 16f)
        r.modalidade.estudos.forEach { escrever("• $it", cinza(10f), recuo = 8f) }
        y += 8f

        if (r.criteriosIncidentes.isNotEmpty()) {
            escrever("CRITÉRIOS LOCACIONAIS INCIDENTES", titulo(12f), espaco = 16f)
            r.criteriosIncidentes.forEach {
                escrever("• (peso ${it.peso}) ${it.texto}", cinza(9.5f), recuo = 8f, espaco = 12f)
            }
            escrever("Prevalece o de maior peso — DN 217/2017, art. 6º, §3º. Os pesos não se somam.",
                cinza(9.5f), espaco = 18f)
        }

        if (r.fatoresRestricao.isNotEmpty()) {
            escrever("FATORES DE RESTRIÇÃO OU VEDAÇÃO", titulo(12f), espaco = 16f)
            r.fatoresRestricao.forEach {
                escrever("• ${it.nome}: ${it.texto}", cinza(9.5f), recuo = 8f, espaco = 12f)
            }
            escrever("Não conferem peso ao enquadramento (art. 6º, §4º), mas devem ser tratados nos estudos.",
                cinza(9.5f), espaco = 18f)
        }

        escrever("MEMÓRIA DE CÁLCULO", titulo(12f), espaco = 16f)
        r.passos.forEachIndexed { i, p ->
            escrever("${i + 1}. ${p.rotulo}: ${p.valor}", titulo(10f, false), espaco = 13f)
            escrever(p.fundamento, cinza(9f), recuo = 12f, espaco = 12f)
            y += 3f
        }

        y += 10f
        if (r.avisos.isNotEmpty()) {
            escrever("AVISOS", titulo(12f), espaco = 16f)
            r.avisos.forEach { escrever("• $it", cinza(9.5f), recuo = 8f, espaco = 12f) }
            y += 8f
        }

        // Art. 18 — bloco proprio no laudo, com a condicao inteira. Quem le o parecer precisa
        // saber que existe uma hipotese de modalidade diferente E que ninguem a aplicou aqui.
        r.casoArt18?.let { caso ->
            escrever("CASO ESPECIAL DA NORMA — ${caso.referencia}", titulo(12f), espaco = 16f)
            escrever(caso.resumo, titulo(10f, false), espaco = 13f)
            escrever("Só vale se for verdade que: ${caso.condicao}", cinza(9.5f), recuo = 8f, espaco = 12f)
            caso.condicaoExtra?.let { escrever(it, cinza(9.5f), recuo = 8f, espaco = 12f) }
            caso.segundaHipotese?.let { escrever(it, cinza(9.5f), recuo = 8f, espaco = 12f) }
            escrever(
                "Esta simulação NÃO aplicou o caso. A modalidade indicada acima é a da Tabela 3. " +
                    "Confirmar a condição de fato é do empreendedor.",
                cinza(9.5f), recuo = 8f, espaco = 12f
            )
            escrever(caso.texto, cinza(8.5f), recuo = 8f, espaco = 11f)
            y += 8f
        }

        escrever("PROCEDÊNCIA E LIMITES", titulo(12f), espaco = 16f)
        escrever(regras.procedencia["aviso"] ?: "", cinza(9.5f), espaco = 12f)
        escrever("Cobertura do catálogo: ${regras.procedencia["cobertura"] ?: "—"}", cinza(9.5f), espaco = 12f)
        y += 6f
        escrever(
            "Este documento é uma SIMULAÇÃO produzida por ferramenta independente, sem vínculo " +
                "com o SISEMA/SEMAD/FEAM. Não substitui o enquadramento realizado pelo órgão " +
                "ambiental competente e não vincula a Administração Pública.",
            cinza(9.5f), espaco = 12f
        )

        doc.finishPage(pagina)
        try {
            FileOutputStream(destino).use { doc.writeTo(it) }
        } finally {
            doc.close()   // segura memória nativa: precisa fechar mesmo se a escrita falhar
        }
        return destino
    }

    fun compartilhar(context: Context, arquivo: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", arquivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Simulação de enquadramento ambiental")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
