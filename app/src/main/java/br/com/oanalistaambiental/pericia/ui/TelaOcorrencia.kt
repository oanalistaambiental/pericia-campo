package br.com.oanalistaambiental.pericia.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.oanalistaambiental.pericia.captura.Transcricao
import br.com.oanalistaambiental.pericia.dados.GrupoCanal
import br.com.oanalistaambiental.pericia.dados.ItemCanal
import br.com.oanalistaambiental.pericia.dados.OcorrenciaAmbiental
import br.com.oanalistaambiental.pericia.geo.Utm
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fmtOcorrencia = SimpleDateFormat("dd/MM/yy HH:mm", Locale("pt", "BR"))

/**
 * Ocorrência ambiental: registrar o que se observou em campo (coordenada + foto + descrição,
 * com transcrição de voz opcional) e, à parte, os canais oficiais de denúncia — o app NUNCA
 * envia nada sozinho, só documenta e mostra para onde essa documentação pode ir.
 *
 * Pensado para quem já usa o kit (analista, consultor, perito), não para o cidadão em geral:
 * é registro técnico de campo, não um portal público de denúncia.
 */
@Composable
fun TelaOcorrenciaAmbiental(vm: CapturaViewModel, voltar: () -> Unit) {
    var aba by rememberSaveable { mutableStateOf(0) }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Ocorrência ambiental", voltar)

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { BotaoLargo("Registrar", principal = aba == 0) { aba = 0 } }
            Box(Modifier.weight(1f)) { BotaoLargo("Minhas", principal = aba == 1) { aba = 1 } }
            Box(Modifier.weight(1f)) { BotaoLargo("Canais oficiais", principal = aba == 2) { aba = 2 } }
        }

        when (aba) {
            0 -> NovaOcorrencia(vm) { aba = 1 }
            1 -> MinhasOcorrencias(vm)
            else -> CanaisOficiais(vm)
        }
    }
}

// ---------------------------------------------------------------- registrar

@Composable
private fun ColumnScope.NovaOcorrencia(vm: CapturaViewModel, aoSalvar: () -> Unit) {
    val contexto = LocalContext.current
    val p by vm.estadoCampo.posicao.collectAsState()

    var descricao by rememberSaveable { mutableStateOf("") }
    var transcricao by rememberSaveable { mutableStateOf("") }
    var fotoCapturada by remember { mutableStateOf<File?>(null) }
    var capturandoFoto by remember { mutableStateOf(false) }
    var ditando by remember { mutableStateOf(false) }
    var erroDitado by remember { mutableStateOf<String?>(null) }

    val transcritor = remember(contexto) { Transcricao(contexto) }
    DisposableEffect(Unit) { onDispose { transcritor.parar() } }

    var temPermissaoMic by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val pedirMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        temPermissaoMic = concedida
        if (concedida) {
            ditando = true
            transcritor.iniciar(
                aoAtualizar = { transcricao = it },
                aoErro = { erroDitado = it; ditando = false }
            )
        }
    }

    var temPermissaoCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val pedirCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        temPermissaoCamera = concedida
        if (concedida) capturandoFoto = true
    }

    if (capturandoFoto) {
        CapturaFotoOcorrencia(
            aoCapturar = { arquivo -> fotoCapturada = arquivo; capturandoFoto = false },
            aoCancelar = { capturandoFoto = false }
        )
        return
    }

    LazyColumn(Modifier.weight(1f)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Rotulo("COORDENADA")
                if (p.temPosicao) {
                    Mono("%.6f, %.6f".format(Locale.US, p.lat, p.lon), Cores.texto, 12)
                    Mono("±%.0f m".format(p.precisaoM ?: 0f), Cores.textoFraco, 10)
                } else {
                    Text("Aguardando GNSS…", color = Cores.atencaoClaro, fontSize = 12.sp)
                }

                Rotulo("FOTO")
                if (fotoCapturada != null) {
                    val bitmap = remember(fotoCapturada) {
                        runCatching {
                            android.graphics.BitmapFactory.decodeFile(fotoCapturada!!.absolutePath)?.asImageBitmap()
                        }.getOrNull()
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap, contentDescription = "Foto da ocorrência",
                            modifier = Modifier.fillMaxWidth().height(200.dp).background(Cores.superficie, RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            BotaoLargo("Tirar outra") {
                                if (temPermissaoCamera) capturandoFoto = true else pedirCamera.launch(Manifest.permission.CAMERA)
                            }
                        }
                        Box(Modifier.weight(1f)) { BotaoLargo("Remover foto") { fotoCapturada = null } }
                    }
                } else {
                    BotaoLargo("Tirar foto", principal = true) {
                        if (temPermissaoCamera) capturandoFoto = true else pedirCamera.launch(Manifest.permission.CAMERA)
                    }
                }

                Rotulo("DESCRIÇÃO")
                OutlinedTextField(
                    value = descricao, onValueChange = { descricao = it },
                    label = { Text("O que foi observado") },
                    minLines = 3, modifier = Modifier.fillMaxWidth()
                )

                Rotulo("DITAR POR VOZ (OPCIONAL)")
                Text(
                    "Transcrito pelo reconhecimento de voz do próprio aparelho — não sai áudio " +
                        "para nenhum servidor além do que o Android já usa para isso. Revise antes " +
                        "de salvar: transcrição erra.",
                    color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                if (!transcritor.disponivel()) {
                    Text(
                        "Reconhecimento de voz indisponível neste aparelho.",
                        color = Cores.atencaoClaro, fontSize = 12.sp
                    )
                } else {
                    BotaoLargo(if (ditando) "Parar ditado" else "Começar a ditar", principal = ditando) {
                        if (ditando) {
                            transcritor.parar(); ditando = false
                        } else if (temPermissaoMic) {
                            erroDitado = null; ditando = true
                            transcritor.iniciar(
                                aoAtualizar = { transcricao = it },
                                aoErro = { erroDitado = it; ditando = false }
                            )
                        } else {
                            pedirMic.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
                erroDitado?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Cores.alertaClaro, fontSize = 11.5.sp)
                }
                if (transcricao.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = transcricao, onValueChange = { transcricao = it },
                        label = { Text("Transcrição (edite se precisar)") },
                        minLines = 3, modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(20.dp))
                BotaoLargo(
                    "Salvar ocorrência", principal = true,
                    habilitado = p.temPosicao && (descricao.isNotBlank() || transcricao.isNotBlank())
                ) {
                    vm.salvarOcorrencia(p.lat!!, p.lon!!, p.precisaoM, descricao, transcricao, fotoCapturada)
                    descricao = ""; transcricao = ""; fotoCapturada = null
                    aoSalvar()
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Câmera mínima — só tira uma foto e devolve o arquivo. Nada de sessão, legenda ou hash aqui: quem grava o hash é o ViewModel, ao salvar a ocorrência. */
@Composable
private fun CapturaFotoOcorrencia(aoCapturar: (File) -> Unit, aoCancelar: () -> Unit) {
    val contexto = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember {
        PreviewView(contexto).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var capturando by remember { mutableStateOf(false) }
    var pronta by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, previewView) {
        val futuro = ProcessCameraProvider.getInstance(contexto)
        var provedor: ProcessCameraProvider? = null
        var descartado = false
        futuro.addListener({
            try {
                val p = futuro.get()
                provedor = p
                if (descartado) { runCatching { p.unbindAll() }; return@addListener }
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                pronta = true
            } catch (_: Throwable) { pronta = false }
        }, ContextCompat.getMainExecutor(contexto))
        onDispose { descartado = true; runCatching { provedor?.unbindAll() } }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })

        Text(
            "‹ Cancelar", color = Color.White, fontSize = 14.sp,
            modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars)
                .clickable { aoCancelar() }.padding(16.dp)
        )

        Box(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 28.dp)
                .size(78.dp)
                .background(if (capturando || !pronta) Cores.textoFraco else Color.White, CircleShape)
                .clickable(enabled = pronta && !capturando) {
                    capturando = true
                    val destino = File(contexto.cacheDir, "ocorrencia_temp_${System.currentTimeMillis()}.jpg")
                    val opcoes = ImageCapture.OutputFileOptions.Builder(destino).build()
                    runCatching {
                        imageCapture.takePicture(
                            opcoes, ContextCompat.getMainExecutor(contexto),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                                    capturando = false
                                    aoCapturar(destino)
                                }
                                override fun onError(e: ImageCaptureException) { capturando = false }
                            }
                        )
                    }.onFailure { capturando = false }
                }
        )
    }
}

// ---------------------------------------------------------------- minhas ocorrencias

@Composable
private fun ColumnScope.MinhasOcorrencias(vm: CapturaViewModel) {
    val ocorrencias by vm.ocorrencias.collectAsState()
    var confirmarExclusao by remember { mutableStateOf<Long?>(null) }

    if (ocorrencias.isEmpty()) {
        Vazio(
            "Nenhuma ocorrência registrada",
            "Use a aba \"Registrar\" para documentar o que observou em campo."
        )
        return
    }

    LazyColumn(Modifier.weight(1f)) {
        items(ocorrencias) { o ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(fmtOcorrencia.format(Date(o.instante)), color = Cores.texto, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                val utm = remember(o.lat, o.lon) { Utm.projetar(o.lat, o.lon) }
                Mono(utm.formatado(), Cores.textoFraco, 10)
                o.descricao?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = Cores.texto, fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 3)
                }
                if (o.fotoArquivo != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("com foto", color = Cores.bomClaro, fontSize = 10.5.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Text(
                        "compartilhar", color = Cores.bomClaro, fontSize = 12.sp,
                        modifier = Modifier.clickable { vm.compartilharOcorrencia(o) }.padding(end = 20.dp, top = 4.dp, bottom = 4.dp)
                    )
                    Text(
                        if (confirmarExclusao == o.id) "confirmar exclusão?" else "excluir",
                        color = Cores.alertaClaro, fontSize = 12.sp,
                        modifier = Modifier.clickable {
                            if (confirmarExclusao == o.id) { vm.excluirOcorrencia(o); confirmarExclusao = null }
                            else confirmarExclusao = o.id
                        }.padding(vertical = 4.dp)
                    )
                }
            }
            HorizontalDivider(color = Cores.linha)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ---------------------------------------------------------------- canais oficiais

@Composable
private fun ColumnScope.CanaisOficiais(vm: CapturaViewModel) {
    val canais by vm.canaisDenuncia.collectAsState()
    val c = canais
    if (c == null) {
        Vazio("Carregando…", "")
        return
    }

    LazyColumn(Modifier.weight(1f)) {
        item {
            Text(
                c.aviso, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        c.grupos.forEach { grupo ->
            item { GrupoCanalBloco(grupo) }
        }
        item {
            Rotulo(c.uras.titulo.uppercase())
            Text(
                c.uras.descricao, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        items(c.uras.regionais) { r ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(r.nome, color = Cores.texto, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(r.endereco, color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 15.sp)
                r.telefone?.let {
                    Spacer(Modifier.height(2.dp))
                    Mono(it, Cores.bomClaro, 11)
                }
            }
            HorizontalDivider(color = Cores.linha)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun GrupoCanalBloco(grupo: GrupoCanal) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Rotulo(grupo.titulo.uppercase())
        Text(
            grupo.descricao, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        grupo.itens.forEach { item -> ItemCanalLinha(item) }
    }
}

@Composable
private fun ItemCanalLinha(item: ItemCanal) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .background(Cores.superficie, RoundedCornerShape(6.dp)).padding(12.dp)
    ) {
        Text(item.nome, color = Cores.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        item.detalhe?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 15.sp)
        }
        item.telefone?.let {
            Spacer(Modifier.height(4.dp))
            Mono(it, Cores.bomClaro, 13)
        }
        item.horario?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, color = Cores.textoFraco, fontSize = 10.5.sp)
        }
        item.endereco?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, color = Cores.textoFraco, fontSize = 10.5.sp, lineHeight = 14.sp)
        }
        item.link?.let {
            Spacer(Modifier.height(4.dp))
            Mono(it, Cores.bomClaro, 10)
        }
    }
}
