package br.com.oanalistaambiental.pericia.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import br.com.oanalistaambiental.pericia.captura.EstadoCampo
import br.com.oanalistaambiental.pericia.dados.Sessao
import br.com.oanalistaambiental.pericia.dados.TiposOcorrencia
import br.com.oanalistaambiental.pericia.geo.Situacao
import br.com.oanalistaambiental.pericia.laudo.LaudoPdf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fmtData = SimpleDateFormat("dd/MM/yy HH:mm", Locale("pt", "BR"))

/* ------------------------------------------------------------------ *
 *  TELA 1 — CÂMERA (abre por padrão)
 *  Desenhada para uso de campo: alvo de toque grande, contraste alto,
 *  informação crítica (precisão do GNSS) sempre visível sem toque.
 * ------------------------------------------------------------------ */
@Composable
fun TelaCamera(vm: CapturaViewModel, irParaSessoes: () -> Unit) {
    val contexto = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val leitura by vm.estadoCampo.leitura.collectAsState()
    val sessao by vm.sessaoAtual.collectAsState()
    val restricoes by vm.ultimasRestricoes.collectAsState()
    var formularioAberto by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    val preview = androidx.camera.core.Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // Faixa superior: sessão + qualidade do ponto
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            BarraSessao(sessao, irParaSessoes)
            SeloPrecisao(leitura)
            restricoes.take(3).forEach { AvisoRestricao(it.frase(), it.situacao) }
        }

        // Faixa inferior: formulário rápido + obturador
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (formularioAberto) FormularioRapido(vm) { formularioAberto = false }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { formularioAberto = !formularioAberto }) {
                    Text(
                        if (vm.tipoOcorrencia == null) "Ocorrência" else vm.tipoOcorrencia!!,
                        color = Color.White
                    )
                }
                Spacer(Modifier.width(24.dp))
                Obturador {
                    val pasta = vm.pastaDaSessao(sessao?.id ?: 0L)
                    val destino = File(pasta, "IMG_${System.currentTimeMillis()}.jpg")
                    val opcoes = ImageCapture.OutputFileOptions.Builder(destino).build()
                    imageCapture.takePicture(
                        opcoes, ContextCompat.getMainExecutor(contexto),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                                vm.registrarCaptura(destino)
                            }
                            override fun onError(e: ImageCaptureException) {}
                        }
                    )
                }
                Spacer(Modifier.width(24.dp))
                TextButton(onClick = irParaSessoes) { Text("Sessões", color = Color.White) }
            }
        }
    }
}

@Composable
private fun BarraSessao(sessao: Sessao?, aoTocar: () -> Unit) {
    Surface(color = Color(0xCC000000), onClick = aoTocar, modifier = Modifier.fillMaxWidth()) {
        Text(
            sessao?.let { "SESSÃO: ${it.titulo}" + (it.processo?.let { p -> "  ·  $p" } ?: "") }
                ?: "SEM SESSÃO — toque para criar",
            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

/**
 * Selo de qualidade do ponto. Existe para ENSINAR: quando está vermelho, vale esperar mais
 * dez segundos antes de clicar. Melhorar a prova na origem é onde melhorar prova é barato.
 */
@Composable
private fun SeloPrecisao(l: EstadoCampo.Leitura) {
    val (cor, texto) = when (l.qualidade) {
        EstadoCampo.Qualidade.BOA -> Color(0xFF1B7F3B) to "GNSS BOM"
        EstadoCampo.Qualidade.ACEITAVEL -> Color(0xFF9A6B00) to "GNSS ACEITÁVEL"
        EstadoCampo.Qualidade.RUIM -> Color(0xFF9B1C1C) to "GNSS RUIM — aguarde"
        EstadoCampo.Qualidade.SEM_SINAL -> Color(0xFF444444) to "SEM SINAL GNSS"
    }
    Surface(color = cor, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text(texto, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                l.precisaoM?.let { "±%.0f m".format(it) } ?: "—",
                color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun AvisoRestricao(texto: String, situacao: Situacao) {
    val cor = when (situacao) {
        Situacao.DENTRO -> Color(0xE6B91C1C)
        Situacao.PROXIMO_AO_LIMITE -> Color(0xE6B07A00)
        Situacao.FORA -> Color(0xE6333333)
    }
    Surface(color = cor, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text(texto, color = Color.White, fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

@Composable
private fun Obturador(aoClicar: () -> Unit) {
    Surface(
        onClick = aoClicar, shape = CircleShape, color = Color.White,
        modifier = Modifier.size(84.dp)   // alvo grande: uso com luva, sol, pressa
    ) { Box(Modifier.fillMaxSize()) }
}

@Composable
private fun FormularioRapido(vm: CapturaViewModel, aoFechar: () -> Unit) {
    var obs by remember { mutableStateOf(vm.observacao) }
    Surface(
        color = Color(0xE6000000), shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Tipo de ocorrência", color = Color.White, fontSize = 12.sp)
            LazyColumn(Modifier.heightIn(max = 190.dp)) {
                items(TiposOcorrencia.padrao) { tipo ->
                    TextButton(onClick = { vm.tipoOcorrencia = tipo; aoFechar() }) {
                        Text(tipo, color = if (vm.tipoOcorrencia == tipo) Color(0xFF7FD18F) else Color.White)
                    }
                }
            }
            OutlinedTextField(
                value = obs,
                onValueChange = { obs = it; vm.observacao = it },
                label = { Text("Observação") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/* ------------------------------------------------------------------ *
 *  TELA 2 — SESSÕES
 * ------------------------------------------------------------------ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaSessoes(vm: CapturaViewModel, aoAbrir: (Sessao) -> Unit, voltar: () -> Unit) {
    val sessoes by vm.sessoes.collectAsState()
    var criando by remember { mutableStateOf(false) }
    var titulo by remember { mutableStateOf("") }
    var processo by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sessões de vistoria") },
            navigationIcon = { TextButton(onClick = voltar) { Text("Câmera") } }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { criando = true }) { Text("Nova sessão") }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(sessoes) { s ->
                ListItem(
                    headlineContent = { Text(s.titulo) },
                    supportingContent = {
                        Text("${s.qtdFotos} foto(s) · ${fmtData.format(Date(s.criadaEm))}" +
                            (s.processo?.let { " · $it" } ?: "") +
                            (if (s.fechadaEm != null) " · FECHADA" else ""))
                    },
                    modifier = Modifier.padding(4.dp)
                )
                Row(Modifier.padding(start = 16.dp, bottom = 8.dp)) {
                    TextButton(onClick = { vm.selecionarSessao(s) }) { Text("Usar") }
                    TextButton(onClick = { aoAbrir(s) }) { Text("Abrir") }
                }
                HorizontalDivider()
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
                    OutlinedTextField(processo, { processo = it },
                        label = { Text("Processo / auto de infração") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (titulo.isNotBlank()) vm.novaSessao(titulo, processo.ifBlank { null })
                    criando = false; titulo = ""; processo = ""
                }) { Text("Criar") }
            },
            dismissButton = { TextButton(onClick = { criando = false }) { Text("Cancelar") } }
        )
    }
}

/* ------------------------------------------------------------------ *
 *  TELA 3 — DETALHE DA SESSÃO
 * ------------------------------------------------------------------ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDetalheSessao(vm: CapturaViewModel, sessao: Sessao, voltar: () -> Unit) {
    val contexto = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    val fotos = remember(sessao.id) { vm.banco.fotosDaSessao(sessao.id) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(sessao.titulo) },
            navigationIcon = { TextButton(onClick = voltar) { Text("Voltar") } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp)) {
            Text("${fotos.size} registro(s)", fontWeight = FontWeight.Bold)
            sessao.processo?.let { Text("Processo: $it") }
            Spacer(Modifier.height(12.dp))

            Button(onClick = {
                val destino = File(contexto.filesDir, "sessoes/${sessao.id}/laudo.pdf")
                runCatching { LaudoPdf.gerar(vm.banco, sessao, fotos, destino) }
                    .onSuccess { status = "Laudo gerado em ${it.name}" }
                    .onFailure { status = "Falha ao gerar laudo: ${it.message}" }
            }, modifier = Modifier.fillMaxWidth()) { Text("Gerar laudo fotográfico (PDF)") }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.fecharSessao(sessao) }, modifier = Modifier.fillMaxWidth()) {
                Text("Fechar sessão e calcular raiz de Merkle")
            }

            status?.let { Spacer(Modifier.height(12.dp)); Text(it) }

            Spacer(Modifier.height(16.dp))
            LazyColumn {
                items(fotos) { f ->
                    ListItem(
                        headlineContent = { Text(f.tipoOcorrencia ?: "Registro") },
                        supportingContent = {
                            Text("%.6f, %.6f · ±%.0f m · %s".format(
                                f.lat, f.lon, f.precisaoM, fmtData.format(Date(f.instante))))
                        },
                        trailingContent = {
                            Text(f.sha256.take(8), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
