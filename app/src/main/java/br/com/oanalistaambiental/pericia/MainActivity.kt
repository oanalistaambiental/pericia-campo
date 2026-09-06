package br.com.oanalistaambiental.pericia

import android.Manifest
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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

private enum class Rota { CAMERA, SESSOES, DETALHE, FERRAMENTAS, BUSSOLA, CONFIGURACOES }

@Composable
private fun App() {
    val vm: CapturaViewModel = viewModel()
    val contexto = LocalContext.current
    var rota by remember { mutableStateOf(Rota.CAMERA) }
    var sessaoAberta by remember { mutableStateOf<Sessao?>(null) }

    fun temPermissoes() =
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    var permissoesOk by remember { mutableStateOf(temPermissoes()) }
    var jaPediu by remember { mutableStateOf(false) }

    val pedir = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissoesOk = temPermissoes(); jaPediu = true }

    LaunchedEffect(Unit) {
        if (!permissoesOk) pedir.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        )
    }

    // BUG corrigido: o ViewModel iniciava o GNSS antes de existir permissao, a chamada era
    // recusada em silencio e nada religava depois do usuario conceder. Na primeira instalacao
    // isso deixava o selo em "SEM SINAL" e gravava toda foto sem coordenada.
    LaunchedEffect(permissoesOk) {
        if (permissoesOk) vm.estadoCampo.iniciar()
    }

    if (!permissoesOk) {
        TelaPermissoes(jaPediu) {
            pedir.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION))
        }
        return
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
                aoAbrir = { sessaoAberta = it; rota = Rota.DETALHE },
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
                irParaConfiguracoes = { rota = Rota.CONFIGURACOES },
                irParaSessoes = { rota = Rota.SESSOES },
                voltar = { rota = Rota.CAMERA }
            )
            Rota.BUSSOLA -> TelaBussola(vm) { rota = Rota.FERRAMENTAS }
            Rota.CONFIGURACOES -> TelaConfiguracoes(vm) { rota = Rota.FERRAMENTAS }
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
