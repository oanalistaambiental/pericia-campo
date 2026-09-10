package br.com.oanalistaambiental.pericia.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Só o visor da câmera traseira — sem obturador, sem [androidx.camera.core.ImageCapture].
 *
 * Para ferramentas que precisam VER o alvo para mirar (altura por trigonometria: apontar para a
 * base, depois para o topo) sem fazer parte do fluxo de captura com cadeia de custódia — o que
 * aparece aqui nunca vira arquivo, nunca tem hash, nunca entra em sessão. É janela, não prova.
 */
@Composable
fun VisorCamera(modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(contexto).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var erro by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner, previewView) {
        val futuro = ProcessCameraProvider.getInstance(contexto)
        var provedor: ProcessCameraProvider? = null
        // Mesma corrida corrigida em TelaCamera.kt: sair da tela antes do provedor ficar
        // pronto nao pode deixar a camera ligada depois que a previa ja foi descartada.
        var descartado = false
        futuro.addListener({
            try {
                val p = futuro.get()
                provedor = p
                if (descartado) { runCatching { p.unbindAll() }; return@addListener }
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                erro = null
            } catch (e: Throwable) {
                erro = e.message ?: e.javaClass.simpleName
            }
        }, ContextCompat.getMainExecutor(contexto))
        onDispose {
            descartado = true
            runCatching { provedor?.unbindAll() }
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })
        if (erro != null) {
            Text(
                "Câmera indisponível: $erro", color = Color.White, fontSize = 12.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
