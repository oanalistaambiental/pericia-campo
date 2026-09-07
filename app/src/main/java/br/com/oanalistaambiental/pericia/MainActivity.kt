package br.com.oanalistaambiental.pericia

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.oanalistaambiental.pericia.dados.Sessao
import br.com.oanalistaambiental.pericia.ui.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 35+ impoe edge-to-edge: sem isto o conteudo desenha sob a barra de status.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Cores.fundo,
                    surface = Cores.superficie,
                    onBackground = Cores.texto,
                    onSurface = Cores.texto
                )
            ) {
                Surface(color = Cores.fundo) { App() }
            }
        }
    }
}

private enum class Rota { CAMERA, SESSOES, DETALHE, FERRAMENTAS, BUSSOLA, CLINOMETRO, MEDICAO, COORDENADA, CONFIGURACOES }

@Composable
private fun App() {
    val vm: CapturaViewModel = viewModel()
    val contexto = LocalContext.current
    /**
     * `rememberSaveable`, nao `remember`. Com `remember`, girar o aparelho recriava a Activity
     * e jogava o perito de volta na camera no meio do que estivesse fazendo.
     */
    var rota by rememberSaveable { mutableStateOf(Rota.CAMERA) }

    /**
     * Guarda o ID, nao o objeto.
     *
     * BUG grave que isto corrige. A tela de detalhe recebia uma copia congelada da sessao,
     * tirada no instante do clique. Depois de "Fechar sessão e selar integridade" o banco
     * ficava selado, mas o objeto na tela continuava com `raizMerkle = null` — e o botao
     * "Laudo fotografico (PDF)", ali do lado, gerava o laudo A PARTIR DESSA COPIA. Resultado:
     * o PDF entregue ao processo saia sem a raiz de Merkle, em silencio, com a sessao selada
     * no banco. Lendo pelo ID a tela reobserva a sessao de verdade.
     */
    var sessaoAbertaId by rememberSaveable { mutableStateOf<Long?>(null) }
    val listaSessoes by vm.sessoes.collectAsState()
    val sessaoAberta = remember(sessaoAbertaId, listaSessoes) {
        sessaoAbertaId?.let { id -> listaSessoes.firstOrNull { it.id == id } }
    }

    fun concedida(p: String) =
        ContextCompat.checkSelfPermission(contexto, p) == PackageManager.PERMISSION_GRANTED

    fun temCamera() = concedida(Manifest.permission.CAMERA)

    // ACCESS_FINE_LOCATION sozinho nao basta como teste: quando o usuario escolhe "Aproximada"
    // na caixa do Android 12+, so a COARSE e concedida. Aceitamos as duas — a precisao real
    // aparece no selo do GNSS, que e onde ela importa.
    fun temLocal() =
        concedida(Manifest.permission.ACCESS_FINE_LOCATION) ||
            concedida(Manifest.permission.ACCESS_COARSE_LOCATION)

    var cameraOk by remember { mutableStateOf(temCamera()) }
    var localOk by remember { mutableStateOf(temLocal()) }
    var jaPediu by remember { mutableStateOf(false) }

    val pedir = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { cameraOk = temCamera(); localOk = temLocal(); jaPediu = true }

    val permissoes = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    LaunchedEffect(Unit) { if (!cameraOk || !localOk) pedir.launch(permissoes) }

    /**
     * BUG corrigido: quem concedia a permissao pelas Configuracoes do Android voltava para um
     * app que continuava dizendo que faltava permissao — o estado so era lido uma vez, na
     * primeira composicao. Agora e reconferido a cada retorno ao primeiro plano.
     */
    val dono = LocalLifecycleOwner.current
    DisposableEffect(dono) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) {
                cameraOk = temCamera()
                localOk = temLocal()
            }
        }
        dono.lifecycle.addObserver(observador)
        onDispose { dono.lifecycle.removeObserver(observador) }
    }

    // BUG corrigido: o ViewModel iniciava o GNSS antes de existir permissao, a chamada era
    // recusada em silencio e nada religava depois do usuario conceder. Na primeira instalacao
    // isso deixava o selo em "SEM SINAL" e gravava toda foto sem coordenada.
    // Quem liga e desliga de fato e o observador de ciclo de vida mais abaixo; este efeito
    // cobre o caso de a permissao ser concedida com o app ja em primeiro plano.
    LaunchedEffect(localOk) {
        if (localOk) vm.estadoCampo.iniciar()
    }

    // Falha de GNSS deixa de ser silenciosa.
    val falhaGnss by vm.estadoCampo.falha.collectAsState()
    LaunchedEffect(falhaGnss) { falhaGnss?.let { vm.avisar(it) } }

    /**
     * BUG corrigido — este e o outro "nao tira foto".
     *
     * O app exigia camera E localizacao precisa para deixar entrar. Quem concedesse a camera
     * e recusasse (ou marcasse "Aproximada") a localizacao ficava preso para sempre na tela de
     * permissoes, sem conseguir fotografar nada. Agora so a camera e obrigatoria: sem
     * localizacao o app funciona e avisa, em letras grandes, que a foto sai sem coordenada.
     */
    if (!cameraOk) {
        TelaPermissoes(jaPediu) { pedir.launch(permissoes) }
        return
    }

    /**
     * Primeiro uso.
     *
     * Nao e tela de boas-vindas: e a unica chance de ensinar as quatro coisas sem as quais o
     * app vira uma camera comum com coordenada. Quem pula, pula uma vez; quem le, para de
     * cometer os erros que invalidam registro em campo.
     */
    val prefs = remember { contexto.getSharedPreferences("pericia", Context.MODE_PRIVATE) }
    var viuIntro by remember { mutableStateOf(prefs.getBoolean("viu_intro_1", false)) }
    if (!viuIntro) {
        TelaPrimeiroUso {
            prefs.edit().putBoolean("viu_intro_1", true).apply()
            viuIntro = true
        }
        return
    }

    /**
     * O botao/gesto Voltar do Android.
     *
     * BUG grave que isto corrige. Nao havia BackHandler nenhum. Em qualquer tela interna o
     * Voltar — que e o gesto natural, e nao o "‹" pequeno do cabecalho — encerrava a Activity.
     * Encerrar a Activity limpa o ViewModel, e os vertices da medicao so existiam em memoria:
     * quarenta minutos caminhando um perimetro, 26 vertices marcados, e o app fechava sem
     * pergunta nenhuma. Agora o Voltar navega, e so sai do app quando ja esta na camera.
     */
    BackHandler(enabled = rota != Rota.CAMERA) {
        rota = when (rota) {
            Rota.DETALHE -> Rota.SESSOES
            Rota.SESSOES, Rota.FERRAMENTAS -> Rota.CAMERA
            Rota.BUSSOLA, Rota.CLINOMETRO, Rota.MEDICAO,
            Rota.COORDENADA, Rota.CONFIGURACOES -> Rota.FERRAMENTAS
            Rota.CAMERA -> Rota.CAMERA
        }
    }

    /**
     * Sensores e GNSS param quando o app sai da tela.
     *
     * Antes so paravam em `onCleared`, ou seja, quase nunca. Quem abrisse o app as 8h e
     * guardasse o celular no bolso para dirigir entre pontos deixava o GNSS a 1 Hz e o vetor
     * de rotacao a ~60 Hz ligados a manha inteira, com a orientacao sendo recalculada a cada
     * evento. Chegava no ponto critico da vistoria com a bateria no fim — em campo, sem
     * tomada, isso e vistoria perdida.
     */
    DisposableEffect(dono, localOk) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_START -> if (localOk) vm.estadoCampo.iniciar()
                Lifecycle.Event.ON_STOP -> vm.estadoCampo.parar()
                else -> Unit
            }
        }
        dono.lifecycle.addObserver(observador)
        onDispose { dono.lifecycle.removeObserver(observador) }
    }

    Box(Modifier.fillMaxSize()) {
        when (rota) {
            Rota.CAMERA -> TelaCamera(
                vm,
                irParaSessoes = { rota = Rota.SESSOES },
                irParaFerramentas = { rota = Rota.FERRAMENTAS }
            )
            Rota.SESSOES -> TelaSessoes(
                vm,
                aoAbrir = { sessaoAbertaId = it.id; rota = Rota.DETALHE },
                voltar = { rota = Rota.CAMERA }
            )
            Rota.DETALHE -> sessaoAberta?.let {
                TelaDetalheSessao(
                    vm, it,
                    irParaCamera = { rota = Rota.CAMERA },
                    voltar = { rota = Rota.SESSOES }
                )
            } ?: LaunchedEffect(Unit) { rota = Rota.SESSOES }
            Rota.FERRAMENTAS -> TelaFerramentas(
                vm,
                irParaBussola = { rota = Rota.BUSSOLA },
                irParaClinometro = { rota = Rota.CLINOMETRO },
                irParaMedicao = { rota = Rota.MEDICAO },
                irParaCoordenada = { rota = Rota.COORDENADA },
                irParaConfiguracoes = { rota = Rota.CONFIGURACOES },
                irParaSessoes = { rota = Rota.SESSOES },
                voltar = { rota = Rota.CAMERA }
            )
            Rota.BUSSOLA -> TelaBussola(vm) { rota = Rota.FERRAMENTAS }
            Rota.CLINOMETRO -> TelaClinometro(vm) { rota = Rota.FERRAMENTAS }
            Rota.MEDICAO -> TelaMedicao(vm) { rota = Rota.FERRAMENTAS }
            Rota.COORDENADA -> TelaIrParaCoordenada(
                vm,
                aoDefinir = { rota = Rota.CAMERA },
                voltar = { rota = Rota.FERRAMENTAS }
            )
            Rota.CONFIGURACOES -> TelaConfiguracoes(vm) { rota = Rota.FERRAMENTAS }
        }

        if (!localOk) {
            Text(
                "SEM PERMISSÃO DE LOCALIZAÇÃO — as fotos serão gravadas sem coordenada. " +
                    "Toque para conceder.",
                // 11sp era pequeno demais para o aviso mais consequente do app, numa tela lida
                // ao sol. Se a foto vai sair sem coordenada, isso tem que ser impossivel de
                // nao ver.
                color = Color.White, fontSize = 13.sp, lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .fillMaxWidth()
                    .background(Cores.alerta)
                    .clickable { pedir.launch(permissoes) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        Mensagem(vm, Modifier.align(Alignment.BottomCenter))
    }
}

/** Aviso curto e efemero, sem cobrir o obturador. */
@Composable
private fun Mensagem(vm: CapturaViewModel, modifier: Modifier) {
    val mensagem by vm.mensagem.collectAsState()
    mensagem?.let { texto ->
        LaunchedEffect(texto) { delay(4500); vm.limparMensagem() }
        Text(
            texto, color = Color.White, fontSize = 12.sp, lineHeight = 16.sp,
            modifier = modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(12.dp).background(Color(0xF21F2429)).padding(14.dp)
        )
    }
}

@Composable
private fun TelaPermissoes(jaPediu: Boolean, aoPedir: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Permissões necessárias", color = Cores.texto, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text(
            "Este aplicativo precisa da câmera e da localização precisa para produzir um registro " +
                "com valor probatório: sem a coordenada e sem a precisão do GNSS, a foto vira " +
                "apenas uma foto.\n\nNada é enviado para servidor nenhum. Tudo fica no aparelho.",
            color = Cores.textoFraco, fontSize = 13.sp, lineHeight = 20.sp
        )
        Spacer(Modifier.height(24.dp))
        BotaoLargo("Conceder permissões", principal = true) { aoPedir() }
        if (jaPediu) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Se a caixa não aparecer mais, abra Configurações do Android › Aplicativos › " +
                    "Perícia Campo › Permissões e conceda Câmera e Local (precisão exata ligada).",
                color = Cores.atencaoClaro, fontSize = 11.5.sp, lineHeight = 16.sp
            )
        }
    }
}

/**
 * O que o perito precisa saber antes da primeira foto.
 *
 * Quatro licoes, na ordem em que os erros acontecem em campo. Texto curto de proposito: a tela
 * que ninguem le nao ensina nada, e este app e usado no sol, com pressa.
 */
@Composable
private fun TelaPrimeiroUso(aoComecar: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Text(
            "Antes da primeira foto",
            color = Cores.texto, fontSize = 24.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Quatro coisas separam um registro que sustenta laudo de uma foto bonita.",
            color = Cores.textoFraco, fontSize = 13.sp, lineHeight = 19.sp
        )

        Licao(
            "1",
            "Espere o selo ficar verde",
            "A faixa no topo mostra a precisão do GNSS. Vermelha, a coordenada pode errar " +
                "dezenas de metros — o bastante para colocar a ocorrência dentro ou fora de uma " +
                "área protegida. Pare, fique a céu aberto e espere. Costuma levar segundos."
        )
        Licao(
            "2",
            "Toda foto pertence a uma sessão",
            "A sessão é a vistoria. É ela que vira laudo, que recebe o selo de integridade ao " +
                "ser fechada e que agrupa as fotos numa sequência com começo e fim. Foto solta " +
                "não vira nada."
        )
        Licao(
            "3",
            "O arquivo original nunca é tocado",
            "No instante da captura o app calcula o SHA-256 do arquivo e guarda. A legenda " +
                "técnica é queimada numa CÓPIA. É a cópia que você compartilha; o original fica " +
                "no aparelho, íntegro e conferível."
        )
        Licao(
            "4",
            "Não mande o original por aplicativo de mensagem",
            "Aplicativos de mensagem recomprimem a imagem. O arquivo muda, o hash deixa de " +
                "bater e a cadeia de custódia se quebra em silêncio. Exporte pela própria tela " +
                "de sessões, que preserva o arquivo. O verificador do app mostra na hora " +
                "quando um arquivo foi alterado."
        )

        Spacer(Modifier.height(28.dp))
        BotaoLargo("Começar", principal = true) { aoComecar() }
        Spacer(Modifier.height(10.dp))
        Text(
            "Isto reaparece só se você reinstalar o aplicativo.",
            color = Cores.textoFraco, fontSize = 10.5.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Licao(numero: String, titulo: String, texto: String) {
    Row(Modifier.fillMaxWidth().padding(top = 26.dp)) {
        Text(
            numero,
            color = Cores.bomClaro, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(30.dp)
        )
        Column {
            Text(titulo, color = Cores.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(texto, color = Cores.textoFraco, fontSize = 12.5.sp, lineHeight = 19.sp)
        }
    }
}
