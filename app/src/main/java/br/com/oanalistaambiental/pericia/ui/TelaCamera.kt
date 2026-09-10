package br.com.oanalistaambiental.pericia.ui

import android.view.OrientationEventListener
import android.view.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.oanalistaambiental.pericia.geo.Utm
import kotlinx.coroutines.delay
import java.io.File

/**
 * Tela de camera — a que abre por padrao.
 *
 * Hierarquia deliberada: o que decide a qualidade da prova (sessao, precisao do GNSS,
 * restricao, rumo) fica no topo, sempre visivel e sem exigir toque. O que e opcional fica ao
 * alcance do polegar.
 *
 * DESEMPENHO — por que a tela e picada em pedacos pequenos.
 *
 * Cada bloco assina o fluxo que lhe diz respeito e nada mais. A fita da bussola redesenha ate
 * cinco vezes por segundo; o bloco de coordenada, uma vez por segundo; o selo de precisao,
 * quase nunca. Antes tudo isso era um objeto so, e a tela inteira — inclusive a projecao UTM —
 * era refeita a cada tremida do aparelho. E o que fazia a previa engasgar e o aparelho esquentar.
 */
@Composable
fun TelaCamera(
    vm: CapturaViewModel,
    irParaSessoes: () -> Unit,
    irParaFerramentas: () -> Unit
) {
    val contexto = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sessao by vm.sessaoAtual.collectAsState()
    var formularioAberto by remember { mutableStateOf(false) }
    var capturando by remember { mutableStateOf(false) }
    var clarao by remember { mutableStateOf(false) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val previewView = remember {
        PreviewView(contexto).apply {
            // COMPATIBLE usa TextureView: mais tolerante em aparelhos antigos, onde o
            // SurfaceView do modo PERFORMANCE as vezes fica preto sem dar erro.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    /**
     * BUG corrigido — este era o "nao tira foto".
     *
     * A ligacao da camera acontecia dentro do `factory` do AndroidView, envolvida num
     * `runCatching {}` vazio. O `factory` roda UMA unica vez, e o `runCatching` engolia
     * qualquer falha sem deixar rastro: se a ligacao falhasse, a tela ficava preta, o
     * obturador nao respondia e o app nao dizia por que. Agora a ligacao vive num
     * DisposableEffect, o erro aparece na tela, e o obturador so fica ativo quando a camera
     * realmente esta ligada.
     */
    var cameraPronta by remember { mutableStateOf(false) }
    var erroCamera by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner, previewView) {
        val futuro = ProcessCameraProvider.getInstance(contexto)
        var provedor: ProcessCameraProvider? = null
        // CORRIDA corrigida: quem saisse da tela antes de o provedor ficar pronto — abrir o
        // app e tocar "Ferramentas" em menos de ~300 ms, coisa banal em aparelho lento —
        // rodava o onDispose com `provedor` ainda null (nao desligava nada) e SO DEPOIS o
        // listener disparava o bindToLifecycle. A camera ficava aberta com a previa ja
        // descartada: bateria, aquecimento, e camera indisponivel para outros apps ate o
        // processo morrer. Esta bandeira faz o listener desistir se a tela ja saiu.
        var descartado = false
        futuro.addListener({
            try {
                val p = futuro.get()
                provedor = p
                if (descartado) { runCatching { p.unbindAll() }; return@addListener }
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                p.unbindAll()
                p.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
                cameraPronta = true
                erroCamera = null
            } catch (e: Throwable) {
                cameraPronta = false
                erroCamera = e.message ?: e.javaClass.simpleName
            }
        }, ContextCompat.getMainExecutor(contexto))

        onDispose {
            descartado = true
            runCatching { provedor?.unbindAll() }
        }
    }

    /**
     * BUG corrigido: a Activity e travada em retrato, entao o CameraX gravava TODA foto como
     * retrato. Foto tirada com o aparelho deitado — enquadramento normal numa vistoria de
     * talude, margem ou estrada — saia com o EXIF errado e a legenda ia parar de lado.
     */
    DisposableEffect(imageCapture) {
        val ouvinte = object : OrientationEventListener(contexto) {
            override fun onOrientationChanged(graus: Int) {
                if (graus == ORIENTATION_UNKNOWN) return
                imageCapture.targetRotation = when {
                    graus >= 315 || graus < 45 -> Surface.ROTATION_0
                    graus < 135 -> Surface.ROTATION_270
                    graus < 225 -> Surface.ROTATION_180
                    else -> Surface.ROTATION_90
                }
            }
        }
        if (ouvinte.canDetectOrientation()) ouvinte.enable()
        onDispose { ouvinte.disable() }
    }

    // Clarao curto ao gravar: confirmacao imediata de que a foto saiu, sem esperar o disco.
    LaunchedEffect(clarao) {
        if (clarao) { delay(110); clarao = false }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })

        if (!cameraPronta) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (erroCamera == null) "Ligando a câmera…" else "Câmera indisponível",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                erroCamera?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Cores.atencaoClaro, fontSize = 11.5.sp, lineHeight = 16.sp,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Confira se a permissão de câmera está concedida e se nenhum outro " +
                            "aplicativo está usando a câmera.",
                        color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (clarao) Box(Modifier.fillMaxSize().background(Color(0x88FFFFFF)))

        // ---------------- faixa superior ----------------
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            BarraSessao(vm, irParaSessoes)
            BlocoPrecisao(vm)
            BlocoRestricoes(vm)
            BlocoGuia(vm)
        }

        // ---------------- faixa inferior ----------------
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (formularioAberto) FormularioRapido(vm) { formularioAberto = false }

            BlocoBussola(vm)
            BlocoCoordenada(vm)

            Row(
                Modifier.fillMaxWidth().background(Color.Black)
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BotaoOcorrencia(vm) { formularioAberto = !formularioAberto }

                Spacer(Modifier.weight(1f))

                Box(
                    Modifier.size(84.dp)
                        .background(
                            when {
                                !cameraPronta -> Color(0xFF3A3F46)
                                capturando -> Cores.textoFraco
                                else -> Color.White
                            },
                            CircleShape
                        )
                        .clickable(enabled = cameraPronta && !capturando) {
                            val id = sessao?.id
                            if (id == null) {
                                vm.avisar("Crie ou selecione uma sessão antes de fotografar.")
                                irParaSessoes()
                                return@clickable
                            }
                            capturando = true
                            val destino = File(vm.pastaDaSessao(id), "IMG_${System.currentTimeMillis()}.jpg")
                            val opcoes = ImageCapture.OutputFileOptions.Builder(destino).build()
                            // BUG corrigido: takePicture pode lancar de forma sincrona quando a
                            // camera nao esta ligada. Sem este runCatching o app fechava no toque.
                            runCatching {
                                imageCapture.takePicture(
                                    opcoes, ContextCompat.getMainExecutor(contexto),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                                            capturando = false
                                            clarao = true
                                            vm.registrarCaptura(destino)
                                        }

                                        // BUG corrigido: o erro era descartado em silencio. A foto
                                        // nao saia e o app nao dizia nada.
                                        override fun onError(e: ImageCaptureException) {
                                            capturando = false
                                            vm.avisar("Falha ao capturar: ${e.message ?: "erro desconhecido"}")
                                        }
                                    }
                                )
                            }.onFailure {
                                capturando = false
                                vm.avisar("Câmera não está pronta: ${it.message ?: "erro desconhecido"}")
                            }
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

// ---------------------------------------------------------------- blocos isolados

@Composable
private fun BarraSessao(vm: CapturaViewModel, irParaSessoes: () -> Unit) {
    val sessao by vm.sessaoAtual.collectAsState()
    Column(
        Modifier.fillMaxWidth().background(Color(0xF2000000))
            .clickable { irParaSessoes() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            sessao?.let { "SESSÃO: ${it.titulo}" } ?: "SEM SESSÃO — toque para criar",
            color = if (sessao == null) Cores.atencaoClaro else Color.White,
            fontSize = 13.sp, fontWeight = FontWeight.Bold
        )
        sessao?.let {
            Mono(
                listOfNotNull(it.processo, "${it.qtdFotos} registros").joinToString(" · "),
                Cores.textoFraco, 11
            )
        } ?: Text(
            "Toda foto precisa pertencer a uma vistoria para virar laudo.",
            color = Cores.textoFraco, fontSize = 10.5.sp
        )
    }
}

@Composable
private fun BlocoPrecisao(vm: CapturaViewModel) {
    val posicao by vm.estadoCampo.posicao.collectAsState()
    SeloPrecisao(posicao)
}

@Composable
private fun BlocoRestricoes(vm: CapturaViewModel) {
    val restricoes by vm.ultimasRestricoes.collectAsState()
    restricoes.take(3).forEach { AvisoRestricao(it.frase(), it.situacao) }
}

/** Guia ativo: ponto de retorno ou coordenada digitada. */
@Composable
private fun BlocoGuia(vm: CapturaViewModel) {
    val alvo by vm.alvo.collectAsState()
    val guia by vm.guia.collectAsState()
    val a = alvo ?: return
    val o = guia

    if (o != null) {
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp)
                .background(if (o.enquadrado) Cores.bom else Cores.atencao)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(a.rotulo, color = Color(0xCCFFFFFF), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                Text(o.instrucao(), color = Color.White, fontSize = 11.5.sp, lineHeight = 15.sp)
            }
            Text(
                "✕", color = Color.White, fontSize = 15.sp,
                modifier = Modifier.clickable { vm.limparAlvo() }.padding(start = 10.dp)
            )
        }
    } else {
        AvisoRestricao(
            "${a.rotulo} — aguardando posição do GNSS.",
            br.com.oanalistaambiental.pericia.geo.Situacao.PROXIMO_AO_LIMITE
        )
    }
}

/** So a fita da bussola redesenha quando o aparelho gira. */
@Composable
private fun BlocoBussola(vm: CapturaViewModel) {
    val sensor by vm.estadoCampo.orientacao.collectAsState()
    val guia by vm.guia.collectAsState()
    val g = guia
    FitaBussola(
        orientacao = sensor,
        alvoGraus = g?.rumoGraus,
        rotuloAlvo = g?.let { "alvo a %.0f m".format(it.distanciaM) }
    )
}

/** Coordenada ao vivo, no formato que vai para a legenda. Redesenha so com posicao nova. */
@Composable
private fun BlocoCoordenada(vm: CapturaViewModel) {
    val posicao by vm.estadoCampo.posicao.collectAsState()
    val lat = posicao.lat
    val lon = posicao.lon
    if (lat == null || lon == null) return
    val utm = remember(lat, lon) { Utm.projetar(lat, lon) }
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
                posicao.altitudeM?.let { "alt %.0f m".format(it) }
            ).joinToString(" · "), Cores.textoFraco, 10
        )
    }
}

/**
 * O tipo de ocorrencia VALE PARA AS PROXIMAS FOTOS, e isso precisa ser obvio na tela.
 *
 * O comportamento pegajoso e proposital — quem registra oito angulos do mesmo dano nao quer
 * escolher oito vezes. Mas o risco e assimetrico: marcar "Foco de queimada" no ponto 3 e
 * esquecer faz as fotos 4 a 20, sem relacao nenhuma, herdarem o mesmo tipo no banco, na
 * legenda queimada, no CSV, no KML e no laudo. Um laudo que classifica dezessete registros com
 * uma ocorrencia que nao ocorreu e atacavel — e o erro nasce de um texto de 12,5sp num canto.
 *
 * Entao, quando ha tipo ativo, o rotulo vira "ATIVA", ganha destaque e o app oferece limpar
 * ali mesmo, num toque.
 */
@Composable
private fun BotaoOcorrencia(vm: CapturaViewModel, aoTocar: () -> Unit) {
    val tipo by vm.tipoOcorrencia.collectAsState()
    val ativo = tipo != null
    Column(
        Modifier.width(104.dp)
            .background(
                if (ativo) Cores.bom.copy(alpha = 0.22f) else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable { aoTocar() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            if (ativo) "OCORRÊNCIA ATIVA" else "OCORRÊNCIA",
            color = if (ativo) Cores.bomClaro else Cores.textoFraco,
            fontSize = 10.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold
        )
        Text(
            tipo ?: "definir",
            color = if (ativo) Cores.bomClaro else Cores.textoFraco,
            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.sp
        )
        if (ativo) {
            Text(
                "vale para as próximas · toque p/ trocar",
                color = Cores.bomClaro.copy(alpha = 0.85f),
                fontSize = 9.5.sp, lineHeight = 12.sp
            )
        }
    }
}

@Composable
private fun FormularioRapido(vm: CapturaViewModel, aoFechar: () -> Unit) {
    val selecionado by vm.tipoOcorrencia.collectAsState()
    val obs by vm.observacao.collectAsState()
    val tipos by vm.tiposOcorrencia.collectAsState()

    Column(
        Modifier.fillMaxWidth().padding(12.dp)
            .background(Color(0xF21A1D21), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text("TIPO DE OCORRÊNCIA", color = Cores.textoFraco, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.heightIn(max = 200.dp)) {
            items(tipos) { tipo ->
                Text(
                    tipo,
                    color = if (selecionado == tipo) Cores.bomClaro else Cores.texto,
                    fontSize = 13.5.sp,
                    fontWeight = if (selecionado == tipo) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth()
                        // Tocar de novo no tipo ja escolhido desmarca — antes nao havia como
                        // voltar atras sem fechar a sessao.
                        .clickable { vm.definirTipoOcorrencia(if (selecionado == tipo) null else tipo) }
                        .padding(vertical = 13.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = obs,
            onValueChange = { vm.definirObservacao(it) },
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
