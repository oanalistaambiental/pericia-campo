package br.com.oanalistaambiental.pericia.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.oanalistaambiental.pericia.dados.TiposOcorrencia
import br.com.oanalistaambiental.pericia.geo.Utm
import java.io.File

/**
 * Tela de camera — a que abre por padrao.
 *
 * Hierarquia deliberada: o que decide a qualidade da prova (sessao, precisao do GNSS,
 * restricao) fica no topo, sempre visivel e sem exigir toque. O que e opcional fica ao
 * alcance do polegar.
 */
@Composable
fun TelaCamera(
    vm: CapturaViewModel,
    irParaSessoes: () -> Unit,
    irParaFerramentas: () -> Unit
) {
    val contexto = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val leitura by vm.estadoCampo.leitura.collectAsState()
    val sessao by vm.sessaoAtual.collectAsState()
    val restricoes by vm.ultimasRestricoes.collectAsState()
    val alvo by vm.alvoRetorno.collectAsState()
    val orientacao by vm.orientacao.collectAsState()
    var formularioAberto by remember { mutableStateOf(false) }
    var capturando by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    runCatching {
                        val provider = future.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // ---------------- faixa superior ----------------
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Column(
                Modifier.fillMaxWidth().background(Color(0xF2000000))
                    .clickable { irParaSessoes() }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    sessao?.let { "SESSÃO: ${it.titulo}" } ?: "SEM SESSÃO — toque para criar",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
                sessao?.let {
                    Mono(
                        listOfNotNull(it.processo, "${it.qtdFotos} registros").joinToString(" · "),
                        Cores.textoFraco, 11
                    )
                }
            }

            SeloPrecisao(leitura)

            restricoes.take(3).forEach { AvisoRestricao(it.frase(), it.situacao) }

            // Guia do ponto de retorno, quando ha alvo escolhido
            orientacao?.let { o ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp)
                        .background(if (o.enquadrado) Cores.bom else Cores.atencao)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "RETORNO · ${o.instrucao()}",
                        color = Color.White, fontSize = 11.5.sp, lineHeight = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "✕", color = Color.White, fontSize = 15.sp,
                        modifier = Modifier.clickable { vm.definirAlvoRetorno(null) }.padding(start = 10.dp)
                    )
                }
            }
            if (alvo != null && orientacao == null) {
                AvisoRestricao("Ponto de retorno ativo — aguardando posição do GNSS.", br.com.oanalistaambiental.pericia.geo.Situacao.PROXIMO_AO_LIMITE)
            }
        }

        // ---------------- faixa inferior ----------------
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (formularioAberto) FormularioRapido(vm) { formularioAberto = false }

            // leitura ao vivo, no formato que vai para a legenda
            val posicao = leitura
            if (posicao.lat != null && posicao.lon != null) {
                val utm = Utm.projetar(posicao.lat, posicao.lon)
                Column(
                    Modifier.fillMaxWidth().background(Color(0xB8000000))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        utm.formatado(), color = Color.White, fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold
                    )
                    Mono(
                        listOfNotNull(
                            "SIRGAS 2000",
                            posicao.altitudeM?.let { "alt %.0f m".format(it) },
                            posicao.azimuteGraus?.let { "azimute %.0f° %s".format(it, rosa(it)) }
                        ).joinToString(" · "), Cores.textoFraco, 10
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().background(Color.Black)
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.width(94.dp).clickable { formularioAberto = !formularioAberto }
                ) {
                    Text("OCORRÊNCIA", color = Cores.textoFraco, fontSize = 10.sp, letterSpacing = 0.8.sp)
                    Text(
                        vm.tipoOcorrencia ?: "definir",
                        color = if (vm.tipoOcorrencia == null) Cores.textoFraco else Cores.bomClaro,
                        fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.sp
                    )
                }

                Spacer(Modifier.weight(1f))

                Box(
                    Modifier.size(84.dp)
                        .background(if (capturando) Cores.textoFraco else Color.White, CircleShape)
                        .clickable(enabled = !capturando) {
                            val id = sessao?.id
                            if (id == null) { irParaSessoes(); return@clickable }
                            capturando = true
                            val destino = File(vm.pastaDaSessao(id), "IMG_${System.currentTimeMillis()}.jpg")
                            val opcoes = ImageCapture.OutputFileOptions.Builder(destino).build()
                            imageCapture.takePicture(
                                opcoes, ContextCompat.getMainExecutor(contexto),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                                        capturando = false
                                        vm.registrarCaptura(destino)
                                    }
                                    override fun onError(e: ImageCaptureException) { capturando = false }
                                }
                            )
                        }
                )

                Spacer(Modifier.weight(1f))

                Column(
                    Modifier.width(94.dp).clickable { irParaFerramentas() },
                    horizontalAlignment = Alignment.End
                ) {
                    Text("≡", color = Color.White, fontSize = 24.sp)
                    Text("Ferramentas", color = Cores.textoFraco, fontSize = 10.5.sp)
                }
            }
        }
    }
}

@Composable
private fun FormularioRapido(vm: CapturaViewModel, aoFechar: () -> Unit) {
    var obs by remember { mutableStateOf(vm.observacao) }
    var selecionado by remember { mutableStateOf(vm.tipoOcorrencia) }

    Column(
        Modifier.fillMaxWidth().padding(12.dp)
            .background(Color(0xF21A1D21), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text("TIPO DE OCORRÊNCIA", color = Cores.textoFraco, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.heightIn(max = 200.dp)) {
            items(TiposOcorrencia.padrao) { tipo ->
                Text(
                    tipo,
                    color = if (selecionado == tipo) Cores.bomClaro else Cores.texto,
                    fontSize = 13.5.sp,
                    fontWeight = if (selecionado == tipo) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { selecionado = tipo; vm.tipoOcorrencia = tipo }
                        .padding(vertical = 13.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = obs,
            onValueChange = { obs = it; vm.observacao = it },
            label = { Text("Observação") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        BotaoLargo("Aplicar", principal = true) { aoFechar() }
        Spacer(Modifier.height(6.dp))
        Text(
            "Vale para as próximas fotos desta sessão, até você trocar.",
            color = Cores.textoFraco, fontSize = 10.5.sp
        )
    }
}

internal fun rosa(azimute: Float): String {
    val dir = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    return dir[(((azimute + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)]
}
