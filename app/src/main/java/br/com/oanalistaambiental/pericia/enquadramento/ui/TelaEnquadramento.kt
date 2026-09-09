package br.com.oanalistaambiental.pericia.enquadramento.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

/**
 * O simulador de enquadramento da DN 217, inteiro, como UMA ferramenta da gaveta.
 *
 * Era um aplicativo separado, com Activity propria. Aqui ele vira uma tela so, com a mesma
 * navegacao interna de antes — nada do fluxo foi reescrito, so realojado.
 *
 * SOBRE O TEMA: esta ferramenta se veste de claro, enquanto o resto do aplicativo e escuro. E
 * deliberado por ora e nao definitivo. A camera de pericia e usada ao sol, com a tela no
 * maximo, e o escuro existe para isso; o simulador e leitura de tabela. Unificar as duas
 * paletas e passo a parte, e mexer nisso junto com a fusao misturaria dois tipos de erro.
 *
 * O `voltar` recebido devolve para a gaveta; o Voltar do sistema navega dentro do fluxo e so
 * sai da ferramenta quando ja esta na tela inicial dela.
 */
@Composable
fun TelaEnquadramento(voltar: () -> Unit) {
    val vm: SimulacaoViewModel = viewModel()
    val contexto = LocalContext.current
    var rota by rememberSaveable { mutableStateOf(Rota.INICIO) }

    BackHandler(enabled = true) {
        if (rota == Rota.INICIO) voltar()
        else rota = when (rota) {
            Rota.ATIVIDADE, Rota.NORMA -> Rota.INICIO
            Rota.PORTE -> Rota.ATIVIDADE
            Rota.LOCACIONAL -> Rota.PORTE
            Rota.RESULTADO -> Rota.LOCACIONAL
            Rota.DISPENSA -> Rota.PORTE
            Rota.INICIO -> Rota.INICIO
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Cores.acento,
            background = Cores.fundo,
            surface = Cores.superficie,
            onBackground = Cores.texto,
            onSurface = Cores.texto
        )
    ) {
        Surface(color = Cores.fundo) {
            Box(Modifier.fillMaxSize()) {
                when (rota) {
                    Rota.INICIO -> TelaInicio(
                        vm,
                        irParaSimulacao = { rota = Rota.ATIVIDADE },
                        irParaNorma = { rota = Rota.NORMA }
                    )
                    Rota.ATIVIDADE -> TelaAtividade(
                        vm,
                        avancar = { rota = Rota.PORTE },
                        voltar = { rota = Rota.INICIO }
                    )
                    Rota.PORTE -> TelaPorte(
                        vm,
                        avancar = { rota = Rota.LOCACIONAL },
                        // Porte inferior nao segue para criterio locacional: ja e resultado.
                        avancarDispensa = { rota = Rota.DISPENSA },
                        voltar = { rota = Rota.ATIVIDADE }
                    )
                    Rota.LOCACIONAL -> TelaLocacional(
                        vm,
                        avancar = { rota = Rota.RESULTADO },
                        voltar = { rota = Rota.PORTE }
                    )
                    Rota.RESULTADO -> TelaResultado(
                        vm,
                        exportar = { vm.exportarPdf(contexto) },
                        novaSimulacao = { vm.novaSimulacao(); rota = Rota.ATIVIDADE },
                        voltar = { rota = Rota.LOCACIONAL }
                    )
                    Rota.DISPENSA -> TelaDispensa(
                        vm,
                        exportar = { vm.exportarDispensaPdf(contexto) },
                        novaSimulacao = { vm.novaSimulacao(); rota = Rota.ATIVIDADE },
                        voltar = { rota = Rota.PORTE }
                    )
                    Rota.NORMA -> TelaNorma(vm) { rota = Rota.INICIO }
                }
                MensagemEnquadramento(vm, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

private enum class Rota { INICIO, ATIVIDADE, PORTE, LOCACIONAL, RESULTADO, DISPENSA, NORMA }

/** Aviso curto e efemero da simulacao. */
@Composable
private fun MensagemEnquadramento(vm: SimulacaoViewModel, modifier: Modifier) {
    val msg by vm.mensagem.collectAsState()
    LaunchedEffect(msg) {
        if (msg != null) { delay(4000); vm.limparMensagem() }
    }
    msg?.let {
        Text(
            it,
            color = Color.White, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = modifier.fillMaxWidth()
                .background(Cores.acento)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
