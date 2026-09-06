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
            Text(
                "Nenhuma sessão ainda. Uma sessão agrupa as fotos de uma mesma vistoria, " +
                    "e é ela que vira o laudo.",
                color = Cores.textoFraco, fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.padding(24.dp)
            )
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

    LaunchedEffect(sessao.id) { vm.carregarFotos(sessao.id) }

    val comRestricao = remember(restricoes) {
        restricoes.values.count { lista -> lista.any { it.situacao == "DENTRO" } }
    }
    val indefinidos = remember(restricoes) {
        restricoes.values.count { lista -> lista.any { it.situacao == "PROXIMO_AO_LIMITE" } }
    }
    val precisaoMedia = remember(fotos) {
        if (fotos.isEmpty()) 0f else fotos.map { it.precisaoM }.average().toFloat()
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
                    Indicador("±%.0f m".format(precisaoMedia), "PRECISÃO MÉD.", Cores.texto)
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
                        vm.conferirIntegridade(sessao.id) { conferencia = it }
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

                Rotulo("REGISTROS")
            }

            items(fotos) { f -> LinhaFoto(vm, f, restricoes[f.id].orEmpty(), irParaCamera) }

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
            val utm = br.com.oanalistaambiental.pericia.geo.Utm.projetar(f.lat, f.lon)
            Mono("${utm.formatado()} · ±%.0f m · %s".format(f.precisaoM, fmt.format(Date(f.instante))), Cores.textoFraco, 10)
        }
        f.endereco?.let { Spacer(Modifier.height(3.dp)); Mono(it, Cores.textoFraco, 10) }
        restricoes.forEach { r ->
            Spacer(Modifier.height(3.dp))
            Text(
                "${if (r.situacao == "DENTRO") "▲" else "◆"} ${r.camada} — ${
                    if (r.situacao == "DENTRO") "interno" else "a %.0f m, indefinido".format(r.distanciaM)
                }",
                color = if (r.situacao == "DENTRO") Cores.alertaClaro else Cores.atencaoClaro,
                fontSize = 10.5.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mono(f.sha256.take(12), Cores.textoFraco, 10)
            Spacer(Modifier.weight(1f))
            Text(
                "Voltar a este ponto",
                color = Cores.bomClaro, fontSize = 11.5.sp,
                modifier = Modifier.clickable { vm.definirAlvoRetorno(f); irParaCamera() }
            )
        }
    }
    HorizontalDivider(color = Cores.linha)
}
