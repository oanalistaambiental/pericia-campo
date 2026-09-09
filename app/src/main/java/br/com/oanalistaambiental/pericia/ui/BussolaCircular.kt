package br.com.oanalistaambiental.pericia.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rosa dos ventos circular de verdade, com ponteiro girando — no lugar da leitura de azimute
 * pura que a bussola tinha antes.
 *
 * O MOSTRADOR NAO GIRA: N fica sempre no topo, E na direita, S embaixo, O na esquerda, do jeito
 * que qualquer perito reconhece numa bussola de bolso. Quem gira e o PONTEIRO, no mesmo eixo do
 * azimute da CAMERA, ja calculado em captura/Orientacoes, nao do topo do aparelho. Quando o
 * ponteiro aponta para a letra N do mostrador, a camera esta apontando para o norte geografico.
 *
 * [alvoGraus], quando informado, marca no mostrador FIXO o rumo a seguir (ex.: para uma
 * coordenada digitada, ou para reproduzir um enquadramento anterior). Como o mostrador nao gira,
 * a marca fica parada no rumo certo, e o ponteiro gira ate coincidir com ela — o mesmo principio
 * de uma bussola de orienteering.
 */
@Composable
fun BussolaCircular(
    azimuteGraus: Float?,
    modifier: Modifier = Modifier,
    tamanho: Dp = 220.dp,
    alvoGraus: Float? = null
) {
    val corTexto = Cores.texto.toArgb()
    val corTextoFraco = Cores.textoFraco.toArgb()
    val corNorte = Cores.alertaClaro
    val corSul = Cores.textoFraco
    val corAlvo = Cores.bomClaro

    Box(modifier.size(tamanho), contentAlignment = Alignment.Center) {
        // Mostrador: aro, marcas de grau, letras cardeais e a marca do alvo. Fixo na tela.
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f * 0.9f

            drawCircle(Cores.linha, r, c, style = Stroke(width = 1.5.dp.toPx()))
            drawCircle(Cores.superficie, r - 1.dp.toPx(), c)

            for (grau in 0 until 360 step 10) {
                val cardinal = grau % 90 == 0
                val meio = grau % 30 == 0 && !cardinal
                val comprimento = when {
                    cardinal -> r * 0.16f
                    meio -> r * 0.10f
                    else -> r * 0.05f
                }
                val rad = Math.toRadians((grau - 90).toDouble())
                val cosG = cos(rad).toFloat()
                val sinG = sin(rad).toFloat()
                drawLine(
                    color = if (cardinal) Cores.texto else Cores.textoFraco,
                    start = Offset(c.x + (r - comprimento) * cosG, c.y + (r - comprimento) * sinG),
                    end = Offset(c.x + r * cosG, c.y + r * sinG),
                    strokeWidth = if (cardinal) 2.5.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Marca do rumo-alvo: um traco curto por fora do aro, na cor de "bom". O mostrador
            // nao gira, entao a marca fica parada no rumo certo o tempo todo.
            if (alvoGraus != null) {
                val rad = Math.toRadians((alvoGraus - 90).toDouble())
                val cosG = cos(rad).toFloat()
                val sinG = sin(rad).toFloat()
                drawLine(
                    color = corAlvo,
                    start = Offset(c.x + r * 1.02f * cosG, c.y + r * 1.02f * sinG),
                    end = Offset(c.x + r * 1.22f * cosG, c.y + r * 1.22f * sinG),
                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
                )
            }

            val raioLetra = r * 0.72f
            val paintCardinal = Paint().apply {
                color = corTexto
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                textSize = 15.sp.toPx()
            }
            val paintNorte = Paint(paintCardinal).apply { color = corNorte.toArgb() }
            listOf("N" to -90.0, "E" to 0.0, "S" to 90.0, "O" to 180.0).forEach { (letra, grau) ->
                val rad = Math.toRadians(grau)
                val x = c.x + (raioLetra * cos(rad)).toFloat()
                // Ajuste vertical: Paint.drawText posiciona a BASE do texto, nao o centro.
                val y = c.y + (raioLetra * sin(rad)).toFloat() + paintCardinal.textSize * 0.35f
                drawContext.canvas.nativeCanvas.drawText(
                    letra, x, y, if (letra == "N") paintNorte else paintCardinal
                )
            }
        }

        // Ponteiro: gira sozinho por cima do mostrador fixo.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = azimuteGraus ?: 0f }
        ) {
            val c = center
            val r = size.minDimension / 2f * 0.9f

            val norte = Path().apply {
                moveTo(c.x, c.y - r * 0.62f)
                lineTo(c.x + r * 0.08f, c.y)
                lineTo(c.x, c.y + r * 0.04f)
                lineTo(c.x - r * 0.08f, c.y)
                close()
            }
            val sul = Path().apply {
                moveTo(c.x, c.y + r * 0.5f)
                lineTo(c.x + r * 0.08f, c.y)
                lineTo(c.x, c.y - r * 0.04f)
                lineTo(c.x - r * 0.08f, c.y)
                close()
            }
            drawPath(sul, corSul, style = Fill)
            drawPath(norte, corNorte, style = Fill)
            drawCircle(androidx.compose.ui.graphics.Color.White, r * 0.045f, c, style = Fill)
        }

        if (azimuteGraus == null) {
            Canvas(Modifier.fillMaxSize()) {
                val paint = Paint().apply {
                    color = corTextoFraco
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                    textSize = 12.sp.toPx()
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "sem leitura", center.x, center.y + size.minDimension * 0.30f, paint
                )
            }
        }
    }
}
