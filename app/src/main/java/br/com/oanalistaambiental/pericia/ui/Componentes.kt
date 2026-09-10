package br.com.oanalistaambiental.pericia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.captura.EstadoCampo
import br.com.oanalistaambiental.pericia.captura.Orientacoes
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

    /**
     * Clareado de #9AA0A6 para #B9BFC6.
     *
     * Este app foi feito para ser usado ao sol, de pe, com pressa — e usava #9AA0A6 em 9,5 a
     * 11 sp para informacao OPERACIONAL: a dica do selo de GNSS, o rotulo do alvo na fita da
     * bussola, o aviso de calibrar, a faixa de "sem permissao de localizacao". Sobre a previa
     * da camera, com veu de 70% de preto, em tela a 100% de brilho debaixo de sol de meio-dia,
     * aquilo era praticamente invisivel — ou seja, os avisos que mais importam eram os menos
     * legiveis. #B9BFC6 sobe o contraste sobre o fundo #0C0D0F de cerca de 6,6:1 para 10,5:1,
     * sem virar branco e sem competir com o texto principal.
     */
    val textoFraco = Color(0xFFB9BFC6)
    val bom = Color(0xFF1B7F3B)
    val bomClaro = Color(0xFF7FD18F)
    val atencao = Color(0xFF8A6100)
    val atencaoClaro = Color(0xFFD8A93A)
    val alerta = Color(0xFFB91C1C)
    val alertaClaro = Color(0xFFE4736F)
    val neutro = Color(0xFF2A2F36)
    // Veu mais denso (0xB3 -> 0xCC, 70% -> 80%). O texto sobre a previa da camera concorre com
    // o que a lente estiver vendo; num barranco claro ao sol, 70% nao bastava.
    val veuEscuro = Color(0xCC000000)
}

/**
 * Selo de qualidade do ponto.
 *
 * Mudou de "mostrar o numero" para "dizer o que fazer com ele": um perito que ve
 * "GNSS RUIM" sem saber que basta esperar a ceu aberto vai fotografar assim mesmo, e a foto
 * nasce sem valor. A linha de acao so aparece quando ha acao a tomar.
 */
@Composable
fun SeloPrecisao(p: EstadoCampo.Posicao, modifier: Modifier = Modifier, comDica: Boolean = true) {
    val (cor, rotulo) = when (p.qualidade) {
        EstadoCampo.Qualidade.BOA -> Cores.bom to "GNSS BOM"
        EstadoCampo.Qualidade.ACEITAVEL -> Cores.atencao to "GNSS ACEITÁVEL"
        EstadoCampo.Qualidade.RUIM -> Cores.alerta to "GNSS RUIM"
        EstadoCampo.Qualidade.VENCIDA -> Cores.alerta to "SINAL PARADO"
        EstadoCampo.Qualidade.SEM_SINAL -> Color(0xFF3A3F46) to "SEM SINAL GNSS"
    }
    val dica = when {
        // O aviso mais importante da tela: enquanto o selo estiver assim, foto e vertice saem
        // SEM coordenada. Dizer ha quantos segundos parou e o que fazer, nao so que parou.
        p.qualidade == EstadoCampo.Qualidade.VENCIDA ->
            "Última leitura tem ${p.idadeSegundos()} s. Enquanto isso, a foto sai sem coordenada. " +
                "Vá para céu aberto ou confira se a localização continua ligada."
        p.aproximada -> "Posição vinda da rede, não do satélite. Aguarde o GNSS fixar."
        p.qualidade == EstadoCampo.Qualidade.SEM_SINAL ->
            "Vá para céu aberto e aguarde. Sem coordenada, a foto vale como imagem, não como prova."
        p.qualidade == EstadoCampo.Qualidade.RUIM ->
            "Aguarde alguns segundos parado. A precisão costuma cair para menos de 10 m."
        else -> null
    }

    Column(modifier.fillMaxWidth().background(cor)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(rotulo, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (p.aproximada) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "APROXIMADA",
                    color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color(0x33FFFFFF), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                // Exibir "+-4 m" ao lado de "SINAL PARADO" seria repetir a mentira que o selo
                // acabou de desmentir: aquela precisao e da leitura velha.
                if (p.qualidade == EstadoCampo.Qualidade.VENCIDA) "há ${p.idadeSegundos()} s"
                else p.precisaoM?.let { "±%.0f m".format(it) } ?: "—",
                color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (comDica && dica != null) {
            Text(
                dica,
                color = Color(0xE6FFFFFF), fontSize = 12.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 7.dp)
            )
        }
    }
}

/**
 * Fita de bussola, no estilo das miras de navegacao.
 *
 * Fica sobre a previa da camera porque a direcao do registro so importa no instante do
 * enquadramento — obrigar o perito a sair da camera para conferir o rumo e pedir para ele nao
 * conferir. Mostra tambem, quando ha, o rumo do alvo (ponto de retorno ou coordenada
 * digitada), para caminhar olhando uma coisa so.
 *
 * Assina apenas o fluxo de orientacao: quando a bussola muda, nada mais na tela redesenha.
 */
@Composable
fun FitaBussola(
    orientacao: EstadoCampo.Orientacao,
    modifier: Modifier = Modifier,
    alvoGraus: Float? = null,
    rotuloAlvo: String? = null
) {
    val azimute = orientacao.azimuteGraus
    val janela = 55f   // graus visiveis de cada lado do centro

    BoxWithConstraints(
        modifier.fillMaxWidth().height(56.dp).background(Cores.veuEscuro).clipToBounds()
    ) {
        val meia = maxWidth / 2f

        if (azimute == null) {
            Text(
                "Aponte a câmera para o horizonte para ler o rumo",
                color = Cores.textoFraco, fontSize = 11.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp)
            )
            return@BoxWithConstraints
        }

        // Marcas de 15 em 15 graus; as de 45 recebem nome.
        var marca = 0
        while (marca < 360) {
            val d = diferencaComSinal(azimute, marca.toFloat())
            if (kotlin.math.abs(d) <= janela) {
                val x = meia * (d / janela)
                val cardinal = marca % 45 == 0
                if (cardinal) {
                    Box(
                        Modifier.align(Alignment.TopCenter).offset(x = x).width(46.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            Orientacoes.rosa(marca.toFloat()),
                            color = if (marca == 0) Cores.alertaClaro else Color.White,
                            fontSize = if (marca % 90 == 0) 14.sp else 11.5.sp,
                            fontWeight = if (marca == 0) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Box(
                    Modifier.align(Alignment.TopCenter).offset(x = x)
                        .padding(top = if (cardinal) 24.dp else 26.dp)
                        .width(1.dp).height(if (cardinal) 10.dp else 6.dp)
                        .background(if (cardinal) Color.White else Cores.textoFraco)
                )
            }
            marca += 15
        }

        // Marca do alvo, quando ha para onde ir.
        if (alvoGraus != null) {
            val d = diferencaComSinal(azimute, alvoGraus)
            val dentro = kotlin.math.abs(d) <= janela
            val x = if (dentro) meia * (d / janela) else meia * (if (d > 0) 0.97f else -0.97f)
            Box(
                Modifier.align(Alignment.TopCenter).offset(x = x).width(60.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    if (dentro) "▼" else if (d > 0) "▶" else "◀",
                    color = Cores.bomClaro, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 21.dp)
                )
            }
            if (rotuloAlvo != null) {
                Text(
                    rotuloAlvo,
                    color = Cores.bomClaro, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 4.dp)
                )
            }
        }

        // Mira central e leitura numerica.
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 22.dp)
                .width(2.dp).height(14.dp).background(Cores.alertaClaro)
        )
        Text(
            "%03.0f° %s".format(azimute, Orientacoes.rosa(azimute)),
            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)
        )

        if (orientacao.precisaoBussola.precisaCalibrar) {
            Text(
                "calibrar: faça um 8 no ar",
                color = Cores.atencaoClaro, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 4.dp)
            )
        }
    }
}

/** Diferenca de [de] para [para], no intervalo -180..180. Positivo = alvo a direita. */
private fun diferencaComSinal(de: Float, para: Float): Float {
    var d = para - de
    while (d > 180f) d -= 360f
    while (d < -180f) d += 360f
    return d
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

/**
 * Ajuda que se abre no lugar.
 *
 * O app precisa ensinar perícia, nao so registrar: quem esta comecando nao sabe por que a
 * precisao do GNSS importa nem o que o hash prova. Texto longo fixo na tela vira ruido e passa
 * a ser ignorado; escondido atras de um toque, fica disponivel para quem quer e sai da frente
 * de quem ja sabe.
 */
@Composable
fun Ajuda(titulo: String, texto: String, modifier: Modifier = Modifier) {
    var aberto by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().clickable { aberto = !aberto }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (aberto) "▾" else "▸",
                color = Cores.textoFraco, fontSize = 11.sp,
                modifier = Modifier.padding(end = 7.dp)
            )
            Text(titulo, color = Cores.textoFraco, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
        }
        if (aberto) {
            Text(
                texto,
                color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(start = 18.dp, top = 7.dp)
            )
        }
    }
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
        else -> Color(0xFF171A1E)
    }
    val cor = when {
        !habilitado -> Cores.textoFraco
        principal -> Cores.fundo
        else -> Cores.texto
    }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).background(fundo)
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

/** Estado vazio com instrucao — em vez de uma tela em branco que nao ensina nada. */
@Composable
fun Vazio(titulo: String, texto: String, acao: String? = null, aoAgir: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(titulo, color = Cores.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            texto, color = Cores.textoFraco, fontSize = 12.5.sp, lineHeight = 19.sp,
            textAlign = TextAlign.Center
        )
        if (acao != null && aoAgir != null) {
            Spacer(Modifier.height(22.dp))
            BotaoLargo(acao, principal = true) { aoAgir() }
        }
    }
}
