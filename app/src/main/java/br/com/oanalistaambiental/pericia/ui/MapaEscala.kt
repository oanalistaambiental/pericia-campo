package br.com.oanalistaambiental.pericia.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.geo.EscalaMapa
import br.com.oanalistaambiental.pericia.geo.PontosLocais

/** Um ponto do mapinha: posicao local em metros, cor e se e o ponto "atual" (desenhado maior). */
data class PontoMapa(
    val local: PontosLocais.PontoLocal,
    val cor: Color,
    val rotulo: String? = null,
    val destaque: Boolean = false
)

/**
 * Mapa ao vivo, em escala, sem depender de nenhum servico de mapa — o app funciona offline por
 * requisito de projeto, e isto e desenho vetorial local, nao uma imagem de satelite baixada.
 *
 * Mostra os pontos em [pontos] na projecao local de [PontosLocais] (metros, norte para cima),
 * liga-os na ordem em que foram dados e desenha uma barra de escala que se ajusta sozinha ao
 * quanto a area cabe no espaco disponivel — e a peca que faltava tanto na medicao de area (ver o
 * poligono se formando) quanto em "ir para uma coordenada" (ver a distancia encolhendo).
 */
@Composable
fun MapaEscala(
    pontos: List<PontoMapa>,
    modifier: Modifier = Modifier,
    fecharPoligono: Boolean = false,
    tracejado: Boolean = false,
    altura: Dp = 200.dp,
    vazio: String = "Nenhum ponto ainda"
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(altura)
            .clip(RoundedCornerShape(10.dp))
            .background(Cores.superficie)
    ) {
        if (pontos.isEmpty()) {
            Text(
                vazio, color = Cores.textoFraco, fontSize = 12.5.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            return@BoxWithConstraints
        }

        val margemDp = 22.dp
        Canvas(Modifier.fillMaxWidth().height(altura)) {
            val margemPx = margemDp.toPx()
            val larguraUtil = (size.width - 2 * margemPx).coerceAtLeast(1f)
            val alturaUtil = (size.height - 2 * margemPx).coerceAtLeast(1f)

            val lestes = pontos.map { it.local.lesteM }
            val nortes = pontos.map { it.local.norteM }
            // Vao minimo de 2 m: um unico ponto (ou dois no mesmo lugar) nao pode gerar
            // divisao por zero nem um zoom infinito.
            val vaoLeste = ((lestes.max() - lestes.min())).coerceAtLeast(2.0)
            val vaoNorte = ((nortes.max() - nortes.min())).coerceAtLeast(2.0)
            val centroLeste = (lestes.max() + lestes.min()) / 2.0
            val centroNorte = (nortes.max() + nortes.min()) / 2.0

            val escala = minOf(larguraUtil / vaoLeste, alturaUtil / vaoNorte).toFloat()
                .coerceAtMost(200f) // px por metro — teto para um unico ponto nao virar um borrao

            fun tela(p: PontosLocais.PontoLocal): Offset = Offset(
                center.x + ((p.lesteM - centroLeste) * escala).toFloat(),
                center.y - ((p.norteM - centroNorte) * escala).toFloat() // norte para cima
            )

            val pontosTela = pontos.map { tela(it.local) }

            if (pontosTela.size >= 2) {
                val caminho = Path().apply {
                    moveTo(pontosTela[0].x, pontosTela[0].y)
                    for (i in 1 until pontosTela.size) lineTo(pontosTela[i].x, pontosTela[i].y)
                    if (fecharPoligono && pontosTela.size >= 3) close()
                }
                if (fecharPoligono && pontosTela.size >= 3) {
                    drawPath(caminho, Cores.bom.copy(alpha = 0.22f), style = Fill)
                }
                drawPath(
                    caminho, Cores.bomClaro,
                    style = Stroke(
                        width = 2.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = if (tracejado) {
                            PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        } else null
                    )
                )
            }

            pontosTela.forEachIndexed { i, p ->
                val item = pontos[i]
                val raio = if (item.destaque) 7.dp.toPx() else 5.dp.toPx()
                drawCircle(Cores.fundo, raio + 2.dp.toPx(), p, style = Fill)
                drawCircle(item.cor, raio, p, style = Fill)
            }

            // Rotulos dos pontos.
            val paintRotulo = Paint().apply {
                isAntiAlias = true
                textSize = 11.sp.toPx()
            }
            pontosTela.forEachIndexed { i, p ->
                val rotulo = pontos[i].rotulo ?: return@forEachIndexed
                paintRotulo.color = corArgb(pontos[i].cor)
                drawContext.canvas.nativeCanvas.drawText(rotulo, p.x + 10.dp.toPx(), p.y + 4.dp.toPx(), paintRotulo)
            }

            // Barra de escala: o maior passo redondo que cabe num terco da largura do mapa.
            val metrosPorPixel = 1.0 / escala
            val larguraMaxBarraPx = size.width * 0.30
            val passoM = EscalaMapa.passoMetros(metrosPorPixel, larguraMaxBarraPx)
            val passoPx = (passoM / metrosPorPixel).toFloat()
            val baseX = margemPx
            val baseY = size.height - margemPx * 0.6f
            drawLine(Cores.texto, Offset(baseX, baseY), Offset(baseX + passoPx, baseY), strokeWidth = 2.dp.toPx())
            drawLine(Cores.texto, Offset(baseX, baseY - 4.dp.toPx()), Offset(baseX, baseY + 4.dp.toPx()), strokeWidth = 2.dp.toPx())
            drawLine(
                Cores.texto, Offset(baseX + passoPx, baseY - 4.dp.toPx()), Offset(baseX + passoPx, baseY + 4.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
            val paintEscala = Paint().apply {
                color = corArgb(Cores.texto)
                isAntiAlias = true
                textSize = 10.sp.toPx()
            }
            drawContext.canvas.nativeCanvas.drawText(
                EscalaMapa.rotulo(passoM), baseX, baseY - 7.dp.toPx(), paintEscala
            )

            // N fixo: e mapa em planta, projetado com norte para cima — nao ha bussola girando.
            val paintNorte = Paint().apply {
                color = corArgb(Cores.textoFraco)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = 11.sp.toPx()
            }
            drawContext.canvas.nativeCanvas.drawText(
                "N", size.width - margemPx, margemPx * 0.7f, paintNorte
            )
        }
    }
}

private fun corArgb(c: Color): Int =
    android.graphics.Color.argb(
        (c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()
    )
