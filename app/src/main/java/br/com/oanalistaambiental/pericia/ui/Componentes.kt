package br.com.oanalistaambiental.pericia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.captura.EstadoCampo
import br.com.oanalistaambiental.pericia.geo.Situacao

/**
 * Paleta de estado, uma so para o app inteiro.
 *
 * Verde = leitura confiavel. Vermelho = indicio de ponto interno a restricao.
 * Ambar = a margem de erro cruza o limite, ou algo esta pendente: indefinido.
 * O ambar e o estado que os concorrentes nao tem, e por isso ele nunca vira vermelho.
 */
object Cores {
    val fundo = Color(0xFF0C0D0F)
    val superficie = Color(0xFF121418)
    val linha = Color(0xFF1E2227)
    val texto = Color(0xFFE8EAED)
    val textoFraco = Color(0xFF9AA0A6)
    val bom = Color(0xFF1B7F3B)
    val bomClaro = Color(0xFF7FD18F)
    val atencao = Color(0xFF8A6100)
    val atencaoClaro = Color(0xFFD8A93A)
    val alerta = Color(0xFFB91C1C)
    val alertaClaro = Color(0xFFE4736F)
    val neutro = Color(0xFF2A2F36)
}

@Composable
fun SeloPrecisao(l: EstadoCampo.Leitura, modifier: Modifier = Modifier) {
    val (cor, texto) = when (l.qualidade) {
        EstadoCampo.Qualidade.BOA -> Cores.bom to "GNSS BOM"
        EstadoCampo.Qualidade.ACEITAVEL -> Cores.atencao to "GNSS ACEITÁVEL"
        EstadoCampo.Qualidade.RUIM -> Cores.alerta to "GNSS RUIM — aguarde"
        EstadoCampo.Qualidade.SEM_SINAL -> Color(0xFF3A3F46) to "SEM SINAL GNSS"
    }
    Row(
        modifier.fillMaxWidth().background(cor).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(texto, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(
            l.precisaoM?.let { "±%.0f m".format(it) } ?: "—",
            color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun AvisoRestricao(texto: String, situacao: Situacao) {
    val cor = when (situacao) {
        Situacao.DENTRO -> Cores.alerta
        Situacao.PROXIMO_AO_LIMITE -> Cores.atencao
        Situacao.FORA -> Cores.neutro
    }
    Text(
        texto, color = Color.White, fontSize = 11.5.sp, lineHeight = 15.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp).background(cor)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

/** Botao com alvo de toque grande — a mao esta com luva, no sol, com pressa. */
@Composable
fun BotaoLargo(
    rotulo: String,
    principal: Boolean = false,
    habilitado: Boolean = true,
    aoClicar: () -> Unit
) {
    val fundo = when {
        !habilitado -> Cores.superficie
        principal -> Cores.texto
        else -> Color.Transparent
    }
    val cor = when {
        !habilitado -> Cores.textoFraco
        principal -> Cores.fundo
        else -> Cores.texto
    }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).background(fundo)
            .then(if (principal || !habilitado) Modifier else Modifier.background(Color(0xFF171A1E)))
            .clickable(enabled = habilitado) { aoClicar() }
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(rotulo, color = cor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Cabecalho(titulo: String, voltar: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().background(Cores.fundo).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (voltar != null) {
            Text(
                "‹", color = Cores.texto, fontSize = 30.sp,
                modifier = Modifier.clickable { voltar() }.padding(end = 14.dp)
            )
        }
        Text(titulo, color = Cores.texto, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Rotulo(texto: String) {
    Text(
        texto, color = Cores.textoFraco, fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
fun Mono(texto: String, cor: Color = Cores.textoFraco, tamanho: Int = 11) {
    Text(texto, color = cor, fontSize = tamanho.sp, fontFamily = FontFamily.Monospace, lineHeight = (tamanho + 5).sp)
}
