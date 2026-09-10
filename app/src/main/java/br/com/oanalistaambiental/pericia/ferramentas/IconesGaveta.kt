package br.com.oanalistaambiental.pericia.ferramentas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Os icones dos botoes da gaveta.
 *
 * DECISAO: desenhados aqui, e nao importados de um pacote de icones prontos. A gaveta so
 * precisa de oito simbolos, um pacote de icones inteiro (material-icons-extended) custaria
 * megabytes no APK so para usar oito deles — e o app ja mede o proprio tamanho a cada etapa.
 * Cada icone e um Canvas simples, na mesma cor do texto do botao.
 *
 * Toda ferramenta da gaveta declara o [IconeGaveta] que usa, do mesmo jeito que declara
 * [Ferramenta.limite] — nao e opcional, e nao ha "sem icone" na grade.
 */
enum class IconeGaveta {
    CAMERA,
    PASTA,
    BUSSOLA,
    CLINOMETRO,
    REGUA,
    ALVO,
    ENQUADRAMENTO,
    CAMADAS,
    CALENDARIO,
    BACIA,
    GLOSSARIO,
    ALTURA,
    PINO
}

@Composable
fun DesenharIcone(icone: IconeGaveta, cor: Color, tamanho: Dp = 30.dp) {
    val traco = Stroke(width = with(androidx.compose.ui.platform.LocalDensity.current) { 2.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    Canvas(Modifier.size(tamanho)) {
        val w = size.width
        val h = size.height
        when (icone) {
            IconeGaveta.CAMERA -> {
                // Corpo com bossa do visor em cima e lente circular no centro.
                drawRoundRect(
                    color = cor,
                    topLeft = Offset(0f, h * 0.28f),
                    size = Size(w, h * 0.64f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    style = traco
                )
                drawLine(cor, Offset(w * 0.34f, h * 0.28f), Offset(w * 0.34f, h * 0.12f), traco.width, StrokeCap.Round)
                drawLine(cor, Offset(w * 0.34f, h * 0.12f), Offset(w * 0.66f, h * 0.12f), traco.width, StrokeCap.Round)
                drawLine(cor, Offset(w * 0.66f, h * 0.12f), Offset(w * 0.66f, h * 0.28f), traco.width, StrokeCap.Round)
                drawCircle(cor, radius = w * 0.17f, center = Offset(w * 0.5f, h * 0.6f), style = traco)
            }

            IconeGaveta.PASTA -> {
                val p = Path().apply {
                    moveTo(w * 0.08f, h * 0.28f)
                    lineTo(w * 0.38f, h * 0.28f)
                    lineTo(w * 0.46f, h * 0.4f)
                    lineTo(w * 0.92f, h * 0.4f)
                    lineTo(w * 0.92f, h * 0.84f)
                    lineTo(w * 0.08f, h * 0.84f)
                    close()
                }
                drawPath(p, cor, style = traco)
            }

            IconeGaveta.BUSSOLA -> {
                val c = Offset(w * 0.5f, h * 0.5f)
                val r = w * 0.4f
                drawCircle(cor, r, c, style = traco)
                // Ponteiro: duas pontas triangulares, uma para cada lado do centro.
                val agulha = Path().apply {
                    moveTo(c.x, c.y - r * 0.72f)
                    lineTo(c.x + r * 0.22f, c.y)
                    lineTo(c.x, c.y + r * 0.72f)
                    lineTo(c.x - r * 0.22f, c.y)
                    close()
                }
                drawPath(agulha, cor, style = Fill)
                drawCircle(cor, r * 0.06f, c, style = Fill)
            }

            IconeGaveta.CLINOMETRO -> {
                // Triangulo retangulo com a hipotenusa inclinada e o arco do angulo na base.
                val base = Offset(w * 0.12f, h * 0.82f)
                val topo = Offset(w * 0.12f, h * 0.18f)
                val ponta = Offset(w * 0.88f, h * 0.82f)
                drawLine(cor, base, topo, traco.width, StrokeCap.Round)
                drawLine(cor, base, ponta, traco.width, StrokeCap.Round)
                drawLine(cor, topo, ponta, traco.width, StrokeCap.Round)
                drawArc(
                    cor, startAngle = 0f, sweepAngle = -35f, useCenter = false,
                    topLeft = Offset(base.x - w * 0.16f, base.y - w * 0.16f),
                    size = Size(w * 0.32f, w * 0.32f), style = traco
                )
            }

            IconeGaveta.REGUA -> {
                // Retangulo pontilhado: o perimetro caminhado, nao uma regua fisica.
                val margem = w * 0.12f
                val passo = w * 0.14f
                var x = margem
                val yTopo = h * 0.2f
                val yBase = h * 0.8f
                val xFim = w - margem
                while (x < xFim) {
                    val fimSeg = kotlin.math.min(x + passo * 0.6f, xFim)
                    drawLine(cor, Offset(x, yTopo), Offset(fimSeg, yTopo), traco.width, StrokeCap.Round)
                    drawLine(cor, Offset(x, yBase), Offset(fimSeg, yBase), traco.width, StrokeCap.Round)
                    x += passo
                }
                var y = yTopo
                while (y < yBase) {
                    val fimSeg = kotlin.math.min(y + passo * 0.6f, yBase)
                    drawLine(cor, Offset(margem, y), Offset(margem, fimSeg), traco.width, StrokeCap.Round)
                    drawLine(cor, Offset(xFim, y), Offset(xFim, fimSeg), traco.width, StrokeCap.Round)
                    y += passo
                }
            }

            IconeGaveta.ALVO -> {
                val c = Offset(w * 0.5f, h * 0.5f)
                drawCircle(cor, w * 0.42f, c, style = traco)
                drawCircle(cor, w * 0.24f, c, style = traco)
                drawCircle(cor, w * 0.06f, c, style = Fill)
            }

            IconeGaveta.ENQUADRAMENTO -> {
                // Prancheta com uma marca de verificacao — enquadrar e conferir, nao medir.
                drawRoundRect(
                    color = cor,
                    topLeft = Offset(w * 0.16f, h * 0.14f),
                    size = Size(w * 0.68f, h * 0.76f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                    style = traco
                )
                drawLine(cor, Offset(w * 0.38f, h * 0.1f), Offset(w * 0.62f, h * 0.1f), traco.width * 1.4f, StrokeCap.Round)
                val check = Path().apply {
                    moveTo(w * 0.32f, h * 0.56f)
                    lineTo(w * 0.46f, h * 0.7f)
                    lineTo(w * 0.7f, h * 0.4f)
                }
                drawPath(check, cor, style = traco)
            }

            IconeGaveta.CALENDARIO -> {
                // Folhinha: retangulo com duas argolas no topo e uma linha separando o cabecalho.
                drawRoundRect(
                    color = cor,
                    topLeft = Offset(w * 0.12f, h * 0.18f),
                    size = Size(w * 0.76f, h * 0.68f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                    style = traco
                )
                drawLine(cor, Offset(w * 0.12f, h * 0.36f), Offset(w * 0.88f, h * 0.36f), traco.width)
                drawLine(cor, Offset(w * 0.30f, h * 0.1f), Offset(w * 0.30f, h * 0.24f), traco.width, StrokeCap.Round)
                drawLine(cor, Offset(w * 0.70f, h * 0.1f), Offset(w * 0.70f, h * 0.24f), traco.width, StrokeCap.Round)
                // Marca de um dia destacado, o prazo.
                drawCircle(cor, w * 0.07f, Offset(w * 0.6f, h * 0.6f), style = Fill)
            }

            IconeGaveta.BACIA -> {
                // Tres ondas empilhadas — agua, sem ser o alvo da regua nem o losango de camadas.
                for (i in 0..2) {
                    val cy = h * (0.32f + i * 0.22f)
                    val onda = Path().apply {
                        moveTo(w * 0.1f, cy)
                        var x = w * 0.1f
                        var lado = -1f
                        while (x < w * 0.9f) {
                            val fimSeg = kotlin.math.min(x + w * 0.2f, w * 0.9f)
                            quadraticTo(x + (fimSeg - x) / 2, cy + lado * h * 0.06f, fimSeg, cy)
                            x = fimSeg
                            lado = -lado
                        }
                    }
                    drawPath(onda, cor, style = traco)
                }
            }

            IconeGaveta.PINO -> {
                // Pino de mapa: circulo (a cabeca) com uma ponta triangular descendo ate o chao.
                val c = Offset(w * 0.5f, h * 0.34f)
                val r = w * 0.22f
                drawCircle(cor, r, c, style = traco)
                val ponta = Path().apply {
                    moveTo(c.x - r * 0.55f, c.y + r * 0.75f)
                    lineTo(c.x, h * 0.86f)
                    lineTo(c.x + r * 0.55f, c.y + r * 0.75f)
                }
                drawPath(ponta, cor, style = traco)
                drawCircle(cor, r * 0.34f, c, style = Fill)
            }

            IconeGaveta.ALTURA -> {
                // Arvore esquematica (tronco + copa triangular) com uma seta vertical do chao
                // ate o topo, e a base marcada — altura medida de baixo para cima.
                drawLine(cor, Offset(w * 0.5f, h * 0.85f), Offset(w * 0.5f, h * 0.18f), traco.width, StrokeCap.Round)
                val seta = Path().apply {
                    moveTo(w * 0.36f, h * 0.32f)
                    lineTo(w * 0.5f, h * 0.14f)
                    lineTo(w * 0.64f, h * 0.32f)
                }
                drawPath(seta, cor, style = traco)
                drawLine(cor, Offset(w * 0.28f, h * 0.85f), Offset(w * 0.72f, h * 0.85f), traco.width, StrokeCap.Round)
            }

            IconeGaveta.GLOSSARIO -> {
                // Livro aberto: duas paginas, lombada ao centro.
                val lombada = Offset(w * 0.5f, h * 0.16f)
                val pE = Path().apply {
                    moveTo(lombada.x, lombada.y)
                    lineTo(w * 0.12f, h * 0.24f)
                    lineTo(w * 0.12f, h * 0.82f)
                    lineTo(lombada.x, h * 0.74f)
                    close()
                }
                val pD = Path().apply {
                    moveTo(lombada.x, lombada.y)
                    lineTo(w * 0.88f, h * 0.24f)
                    lineTo(w * 0.88f, h * 0.82f)
                    lineTo(lombada.x, h * 0.74f)
                    close()
                }
                drawPath(pE, cor, style = traco)
                drawPath(pD, cor, style = traco)
                drawLine(cor, lombada, Offset(lombada.x, h * 0.74f), traco.width * 0.8f)
            }

            IconeGaveta.CAMADAS -> {
                // Tres losangos empilhados, o classico icone de camadas.
                for (i in 0..2) {
                    val cy = h * (0.28f + i * 0.24f)
                    val p = Path().apply {
                        moveTo(w * 0.5f, cy - h * 0.12f)
                        lineTo(w * 0.88f, cy)
                        lineTo(w * 0.5f, cy + h * 0.12f)
                        lineTo(w * 0.12f, cy)
                        close()
                    }
                    drawPath(p, cor, style = traco)
                }
            }
        }
    }
}
