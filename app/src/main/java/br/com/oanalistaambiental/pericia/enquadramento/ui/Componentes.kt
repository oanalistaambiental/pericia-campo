package br.com.oanalistaambiental.pericia.enquadramento.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tema claro, de propósito.
 *
 * O aplicativo de campo é escuro porque se usa no sol, com uma mão, com pressa. Este se usa
 * sentado, lendo tabela e comparando norma. São momentos opostos, e a diferença visual também
 * serve para o usuário nunca confundir um com o outro.
 */
object Cores {
    val fundo = Color(0xFFF7F7F5)
    val superficie = Color(0xFFFFFFFF)
    val linha = Color(0xFFE2E2DE)
    val texto = Color(0xFF16181A)
    val textoFraco = Color(0xFF5F6469)
    val acento = Color(0xFF1B5E38)
    val acentoClaro = Color(0xFFE6F0E9)
    val atencao = Color(0xFF8A5A00)
    val atencaoClaro = Color(0xFFFBF0DC)
    val alerta = Color(0xFF9B1C1C)
    val alertaClaro = Color(0xFFFAE9E9)
}

@Composable
fun Cabecalho(titulo: String, subtitulo: String? = null, voltar: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().background(Cores.superficie).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (voltar != null) {
                Text("‹", color = Cores.texto, fontSize = 30.sp,
                    modifier = Modifier.clickable { voltar() }.padding(end = 14.dp))
            }
            Text(titulo, color = Cores.texto, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        subtitulo?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = Cores.textoFraco, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
    }
    HorizontalDivider(color = Cores.linha)
}

@Composable
fun Rotulo(texto: String) {
    Text(texto, color = Cores.textoFraco, fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
fun Botao(rotulo: String, principal: Boolean = false, habilitado: Boolean = true, aoClicar: () -> Unit) {
    val fundo = when { !habilitado -> Cores.linha; principal -> Cores.acento; else -> Cores.superficie }
    val cor = when { !habilitado -> Cores.textoFraco; principal -> Color.White; else -> Cores.texto }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .background(fundo, RoundedCornerShape(6.dp))
            .clickable(enabled = habilitado) { aoClicar() }
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) { Text(rotulo, color = cor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun Cartao(conteudo: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .background(Cores.superficie, RoundedCornerShape(8.dp))
            .padding(14.dp),
        content = conteudo
    )
}

@Composable
fun Aviso(texto: String, tipo: TipoAviso = TipoAviso.ATENCAO) {
    val (fundo, cor) = when (tipo) {
        TipoAviso.ATENCAO -> Cores.atencaoClaro to Cores.atencao
        TipoAviso.ALERTA -> Cores.alertaClaro to Cores.alerta
        TipoAviso.INFO -> Cores.acentoClaro to Cores.acento
    }
    Text(texto, color = cor, fontSize = 12.sp, lineHeight = 17.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .background(fundo, RoundedCornerShape(6.dp)).padding(12.dp))
}

enum class TipoAviso { INFO, ATENCAO, ALERTA }

@Composable
fun Mono(texto: String, cor: Color = Cores.textoFraco, tamanho: Int = 11) {
    Text(texto, color = cor, fontSize = tamanho.sp, fontFamily = FontFamily.Monospace,
        lineHeight = (tamanho + 6).sp)
}

@Composable
fun LinhaDado(rotulo: String, valor: String, destaque: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Text(rotulo, color = Cores.textoFraco, fontSize = 12.5.sp, modifier = Modifier.width(132.dp))
        Text(valor, color = if (destaque) Cores.acento else Cores.texto,
            fontSize = 13.sp, lineHeight = 18.sp,
            fontWeight = if (destaque) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/**
 * Tela de espera com saida.
 *
 * BUG corrigido: quatro telas faziam `val r = regras ?: return`, e `return` cru num Composable
 * desenha NADA — sem cabecalho, sem botao voltar, sem indicacao de que algo esta carregando.
 * Combinado com a ausencia de BackHandler, quem caisse nisso ficava preso numa tela branca sem
 * saida a nao ser matar o app. Agora ha titulo, explicacao e caminho de volta.
 */
@Composable
fun Carregando(titulo: String, voltar: (() -> Unit)? = null, erro: String? = null) {
    Column(
        Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho(titulo, voltar = voltar)
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text(
                erro ?: "Carregando a base normativa…",
                color = if (erro != null) Cores.alerta else Cores.textoFraco,
                fontSize = 14.sp, lineHeight = 20.sp
            )
            if (erro != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "A base fica em app/src/main/assets/norma. Se o erro persistir, o arquivo " +
                        "citado acima está malformado.",
                    color = Cores.textoFraco, fontSize = 12.sp, lineHeight = 17.sp
                )
            }
        }
    }
}
