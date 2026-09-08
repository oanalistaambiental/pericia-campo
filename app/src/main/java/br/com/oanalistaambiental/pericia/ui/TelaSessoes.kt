package br.com.oanalistaambiental.pericia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.captura.ConferenciaSessao
import br.com.oanalistaambiental.pericia.captura.Integridade
import br.com.oanalistaambiental.pericia.dados.Foto
import br.com.oanalistaambiental.pericia.dados.Sessao
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fmt = SimpleDateFormat("dd/MM/yy HH:mm", Locale("pt", "BR"))

@Composable
fun TelaSessoes(
    vm: CapturaViewModel,
    aoAbrir: (Sessao) -> Unit,
    voltar: () -> Unit
) {
    val sessoes by vm.sessoes.collectAsState()
    val atual by vm.sessaoAtual.collectAsState()
    var criando by remember { mutableStateOf(false) }
    var titulo by remember { mutableStateOf("") }
    var processo by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Sessões de vistoria", voltar)

        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            BotaoLargo("+  Nova sessão", principal = true) { criando = true }
        }

        if (sessoes.isEmpty()) {
            Vazio(
                "Nenhuma sessão ainda",
                "A sessão é a vistoria: agrupa as fotos, recebe o selo de integridade ao ser " +
                    "fechada e é ela que vira o laudo. Dê o nome do local ou do processo.",
                acao = "Criar a primeira sessão"
            ) { criando = true }
        }

        LazyColumn(Modifier.weight(1f)) {
            items(sessoes) { s ->
                val emUso = s.id == atual?.id
                Column(
                    Modifier.fillMaxWidth()
                        .background(if (emUso) Cores.superficie else Color.Transparent)
                        .clickable { aoAbrir(s) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (emUso) {
                            Box(Modifier.size(7.dp).background(Cores.bomClaro, RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(s.titulo, color = Cores.texto, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(5.dp))
                    Mono(
                        listOfNotNull(
                            "${s.qtdFotos} fotos",
                            fmt.format(Date(s.criadaEm)),
                            s.processo
                        ).joinToString(" · ")
                    )
                    if (s.fechadaEm != null) {
                        Spacer(Modifier.height(4.dp))
                        Text("Fechada · integridade selada", color = Cores.bomClaro, fontSize = 11.sp)
                    }
                    if (!emUso && s.fechadaEm == null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Usar nesta captura",
                            color = Cores.bomClaro, fontSize = 12.sp,
                            modifier = Modifier.clickable { vm.selecionarSessao(s) }
                        )
                    }
                }
                HorizontalDivider(color = Cores.linha)
            }
        }
    }

    if (criando) {
        AlertDialog(
            onDismissRequest = { criando = false },
            title = { Text("Nova sessão") },
            text = {
                Column {
                    OutlinedTextField(titulo, { titulo = it }, label = { Text("Título / local") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(processo, { processo = it }, label = { Text("Processo / auto de infração") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (titulo.isNotBlank()) vm.novaSessao(titulo.trim(), processo.trim().ifBlank { null })
                    criando = false; titulo = ""; processo = ""
                }) { Text("Criar") }
            },
            dismissButton = { TextButton(onClick = { criando = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun TelaDetalheSessao(
    vm: CapturaViewModel,
    sessao: Sessao,
    irParaCamera: () -> Unit,
    voltar: () -> Unit
) {
    val fotos by vm.fotosDaSessao.collectAsState()
    val restricoes by vm.restricoesPorFoto.collectAsState()
    var conferencia by remember { mutableStateOf<List<Integridade.Conferencia>?>(null) }
    var conferenciaCompleta by remember { mutableStateOf<ConferenciaSessao.Resultado?>(null) }

    LaunchedEffect(sessao.id) { vm.carregarFotos(sessao.id) }

    val comRestricao = remember(restricoes) {
        restricoes.values.count { lista -> lista.any { it.situacao == "DENTRO" } }
    }
    val indefinidos = remember(restricoes) {
        restricoes.values.count { lista -> lista.any { it.situacao == "PROXIMO_AO_LIMITE" } }
    }
    /**
     * Media so das fotos QUE TEM coordenada.
     *
     * Foto sem GNSS grava precisao 999 como sentinela. Antes essas entravam na media: 19 fotos
     * com +-5 m mais uma sem sinal davam "+-55 m" no indicador da sessao — numero que, copiado
     * para o laudo, desqualifica a vistoria inteira sem que nada de errado tenha acontecido.
     */
    val comPosicao = remember(fotos) { fotos.filter { it.lat != 0.0 || it.lon != 0.0 } }
    val precisaoMedia = remember(comPosicao) {
        if (comPosicao.isEmpty()) 0f else comPosicao.map { it.precisaoM }.average().toFloat()
    }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho(sessao.titulo, voltar)

        LazyColumn(Modifier.weight(1f)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Indicador("${fotos.size}", "REGISTROS", Cores.texto)
                    Spacer(Modifier.width(22.dp))
                    Indicador("$comRestricao", "EM RESTRIÇÃO", Cores.alertaClaro)
                    Spacer(Modifier.width(22.dp))
                    Indicador("$indefinidos", "INDEFINIDOS", Cores.atencaoClaro)
                    Spacer(Modifier.width(22.dp))
                    Indicador(
                        if (comPosicao.isEmpty()) "—" else "±%.0f m".format(precisaoMedia),
                        if (comPosicao.size == fotos.size) "PRECISÃO MÉD."
                        else "PREC. MÉD. (${comPosicao.size}/${fotos.size})",
                        Cores.texto
                    )
                }

                Rotulo("EXPORTAR")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotaoLargo("Laudo fotográfico (PDF)", principal = true) { vm.exportar(sessao, "pdf") }
                    BotaoLargo("Metadados (CSV)") { vm.exportar(sessao, "csv") }
                    BotaoLargo("Pontos (KMZ — Google Earth / QGIS)") { vm.exportar(sessao, "kmz") }
                    BotaoLargo("Arquivos originais") { vm.compartilharOriginais(sessao) }
                }

                Rotulo("INTEGRIDADE")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sessao.fechadaEm == null) {
                        BotaoLargo("Fechar sessão e selar integridade") { vm.fecharSessao(sessao) }
                    }
                    BotaoLargo("Conferir arquivos agora") {
                        vm.conferirIntegridade(sessao.id) { conferencia = it; conferenciaCompleta = null }
                    }
                    // Conferencia completa: arquivo E arvore. Sao perguntas diferentes, e a
                    // de cima sozinha nao pega foto acrescentada depois do fechamento.
                    BotaoLargo("Conferir a prova completa (arquivo e árvore)") {
                        vm.conferirSessaoCompleta(sessao.id) { conferenciaCompleta = it; conferencia = null }
                    }
                }

                sessao.raizMerkle?.let { raiz ->
                    Column(
                        Modifier.padding(16.dp).fillMaxWidth()
                            .background(Cores.superficie, RoundedCornerShape(4.dp)).padding(12.dp)
                    ) {
                        Text("RAIZ DE MERKLE DA SESSÃO", color = Cores.textoFraco, fontSize = 10.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(5.dp))
                        Mono(raiz.chunked(32).joinToString("\n"), Cores.texto, 10)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (sessao.carimboTempo == null)
                                "Carimbo do tempo pendente — o hash prova que nada mudou, o carimbo prova desde quando."
                            else "Carimbo do tempo aplicado sobre a raiz.",
                            color = if (sessao.carimboTempo == null) Cores.atencaoClaro else Cores.bomClaro,
                            fontSize = 10.5.sp, lineHeight = 14.sp
                        )
                    }
                }

                conferenciaCompleta?.let { r ->
                    val tudoBem = r.arvoreConfere && r.arquivosComProblema == 0
                    Column(
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                            .background(if (tudoBem) Cores.bom else Cores.alerta, RoundedCornerShape(4.dp))
                            .padding(12.dp)
                    ) {
                        Text(r.resumo(), color = Color.White, fontSize = 12.5.sp,
                            lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        // As duas perguntas ficam SEPARADAS na tela, e nao fundidas num
                        // unico "ok" — e a distincao que sustenta o laudo.
                        Text(
                            "ARQUIVOS: ${r.arquivosIntegros} de ${r.itens.size} conferem com o " +
                                "hash gravado na captura.",
                            color = Color.White, fontSize = 11.5.sp, lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            when {
                                !r.selada -> "ÁRVORE: sessão ainda aberta, sem raiz selada."
                                r.arvoreConfere ->
                                    "ÁRVORE: os ${r.itens.size} registros fecham na raiz selada."
                                else ->
                                    "ÁRVORE: a raiz recalculada é ${r.raizRecalculada.take(16)}… e " +
                                        "não bate com a selada."
                            },
                            color = Color.White, fontSize = 11.5.sp, lineHeight = 16.sp
                        )
                        val forasteiros = r.itens.filter { !it.pertenceAArvore }
                        if (r.selada && forasteiros.isNotEmpty() && r.arvoreConfere) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${forasteiros.size} registro(s) não fecham na raiz.",
                                color = Color.White, fontSize = 11.5.sp, lineHeight = 16.sp
                            )
                        }
                        val problemas = r.itens.filter {
                            it.estadoArquivo != ConferenciaSessao.EstadoArquivo.INTEGRO
                        }
                        if (problemas.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            problemas.take(8).forEach {
                                Text(
                                    "• ${it.rotulo}: ${it.estadoArquivo.name.lowercase()}",
                                    color = Color.White, fontSize = 11.sp, lineHeight = 15.sp
                                )
                            }
                            if (problemas.size > 8) {
                                Text("• e mais ${problemas.size - 8}…",
                                    color = Color.White, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(r.RESSALVA_TEMPO, color = Color(0xE6FFFFFF),
                            fontSize = 10.5.sp, lineHeight = 14.sp)
                    }
                }

                conferencia?.let { lista ->
                    val alterados = lista.count { it.estado != Integridade.Estado.INTEGRO }
                    Column(
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                            .background(if (alterados == 0) Cores.bom else Cores.alerta, RoundedCornerShape(4.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            if (alterados == 0) "Todos os ${lista.size} arquivos conferem."
                            else "$alterados de ${lista.size} arquivos NÃO conferem.",
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                        if (alterados > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Arquivo alterado ou ausente perde o valor probatório. A causa mais comum " +
                                    "é ter passado por aplicativo de mensagem, que recomprime a imagem.",
                                color = Color.White, fontSize = 11.sp, lineHeight = 15.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Ajuda(
                    "O que cada exportação serve para",
                    "PDF é o laudo fotográfico, com a memória de integridade paginada — é o que " +
                        "se anexa ao processo. CSV traz os metadados de cada registro para " +
                        "planilha. KMZ abre no Google Earth e no QGIS, para mostrar os pontos " +
                        "sobre imagem de satélite. “Arquivos originais” envia as fotos sem " +
                        "legenda, que são as que conferem com o hash."
                )
                Ajuda(
                    "Por que fechar a sessão",
                    "Ao fechar, o app monta uma árvore de Merkle com os hashes de todas as fotos " +
                        "e grava a raiz. A partir daí, qualquer alteração em qualquer foto muda a " +
                        "raiz — e cada foto continua demonstrável isoladamente pelo caminho até " +
                        "ela. Uma sessão aberta ainda aceita fotos novas e por isso não tem selo."
                )
                Ajuda(
                    "O hash prova o quê, exatamente",
                    "Prova que o arquivo não mudou desde o cálculo. Não prova QUANDO isso " +
                        "aconteceu, porque o relógio do aparelho é ajustável pelo próprio " +
                        "usuário. Quem resolve a data é o carimbo do tempo (RFC 3161) de uma " +
                        "autoridade credenciada — por isso ele aparece como pendente aqui."
                )

                Rotulo("REGISTROS")
            }

            items(fotos) { f ->
                LinhaFoto(
                    vm, sessao, !sessao.raizMerkle.isNullOrBlank(),
                    f, restricoes[f.id].orEmpty(), irParaCamera
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Indicador(valor: String, rotulo: String, cor: Color) {
    Column {
        Text(valor, color = cor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(rotulo, color = Cores.textoFraco, fontSize = 10.sp, letterSpacing = 0.6.sp)
    }
}

@Composable
private fun LinhaFoto(
    vm: CapturaViewModel,
    sessao: Sessao,
    sessaoSelada: Boolean,
    f: Foto,
    restricoes: List<br.com.oanalistaambiental.pericia.dados.RegistroRestricao>,
    irParaCamera: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(f.tipoOcorrencia ?: "Registro", color = Cores.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        if (f.lat == 0.0 && f.lon == 0.0) {
            Mono("sem posição GNSS · ${fmt.format(Date(f.instante))}", Cores.atencaoClaro, 10)
        } else {
            // DESEMPENHO: sem o remember, a projecao UTM roda de novo para CADA foto a cada
            // recomposicao da lista. Numa sessao longa isso trava a rolagem.
            val utm = remember(f.lat, f.lon) { br.com.oanalistaambiental.pericia.geo.Utm.projetar(f.lat, f.lon) }
            Mono("${utm.formatado()} · ±%.0f m · %s".format(f.precisaoM, fmt.format(Date(f.instante))), Cores.textoFraco, 10)
        }
        f.endereco?.let { Spacer(Modifier.height(3.dp)); Mono(it, Cores.textoFraco, 10) }
        restricoes.forEach { r ->
            Spacer(Modifier.height(3.dp))
            Text(
                // O `.format` ficava DEPOIS da interpolacao, entao o nome da camada entrava
                // dentro da string de formato. Uma camada chamada "APP - faixa 100% marginal"
                // fazia String.format achar um especificador "% m" e lancar excecao — aqui,
                // dentro de um composable, durante a rolagem: o app fechava. Formatar o numero
                // primeiro e concatenar depois resolve de vez.
                (if (r.situacao == "DENTRO") "▲ " else "◆ ") + r.camada + " — " +
                    if (r.situacao == "DENTRO") "interno"
                    else "a " + "%.0f".format(Locale.US, r.distanciaM) + " m, indefinido",
                color = if (r.situacao == "DENTRO") Cores.alertaClaro else Cores.atencaoClaro,
                fontSize = 10.5.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mono(f.sha256.take(12), Cores.textoFraco, 10)
            Spacer(Modifier.weight(1f))
            // So aparece com a sessao selada: sem raiz nao ha prova individual a emitir, e um
            // botao que sempre falha e pior que botao nenhum.
            if (sessaoSelada) {
                Text(
                    "Prova desta foto",
                    color = Cores.textoFraco, fontSize = 11.5.sp,
                    modifier = Modifier.clickable { vm.exportarProvaDaFoto(sessao, f) }
                )
                Spacer(Modifier.width(14.dp))
            }
            Text(
                "Voltar a este ponto",
                color = Cores.bomClaro, fontSize = 11.5.sp,
                modifier = Modifier.clickable { vm.definirAlvoRetorno(f); irParaCamera() }
            )
        }
    }
    HorizontalDivider(color = Cores.linha)
}
