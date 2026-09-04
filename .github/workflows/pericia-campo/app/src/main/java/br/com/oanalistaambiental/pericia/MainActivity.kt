package br.com.oanalistaambiental.pericia

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.oanalistaambiental.pericia.dados.Sessao
import br.com.oanalistaambiental.pericia.ui.CapturaViewModel
import br.com.oanalistaambiental.pericia.ui.TelaCamera
import br.com.oanalistaambiental.pericia.ui.TelaDetalheSessao
import br.com.oanalistaambiental.pericia.ui.TelaSessoes

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Tema escuro por padrao: em campo, sob sol forte, tela escura com faixas de alto
            // contraste le melhor que fundo branco, e economiza bateria em vistoria longa.
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface { App() }
            }
        }
    }
}

private enum class Rota { CAMERA, SESSOES, DETALHE }

@Composable
private fun App() {
    val vm: CapturaViewModel = viewModel()
    var rota by remember { mutableStateOf(Rota.CAMERA) }
    var sessaoAberta by remember { mutableStateOf<Sessao?>(null) }
    var permissoesOk by remember { mutableStateOf(false) }

    val pedirPermissoes = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado -> permissoesOk = resultado.values.all { it } }

    LaunchedEffect(Unit) {
        pedirPermissoes.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        )
    }

    when (rota) {
        Rota.CAMERA -> TelaCamera(vm) { rota = Rota.SESSOES }
        Rota.SESSOES -> TelaSessoes(
            vm,
            aoAbrir = { sessaoAberta = it; rota = Rota.DETALHE },
            voltar = { rota = Rota.CAMERA }
        )
        Rota.DETALHE -> sessaoAberta?.let {
            TelaDetalheSessao(vm, it) { rota = Rota.SESSOES }
        } ?: run { rota = Rota.SESSOES }
    }
}
