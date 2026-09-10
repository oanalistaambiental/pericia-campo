package br.com.oanalistaambiental.pericia.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.captura.Integridade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A única ferramenta do kit pensada para quem RECEBE o laudo, não para quem o produz — um
 * advogado, um fiscal, um juiz, com o PDF numa mão e o arquivo de imagem na outra, conferindo se
 * bate com o SHA-256 escrito no laudo. Não precisa de sessão, GNSS nem câmera: um arquivo e um
 * texto colado, nada mais.
 */
@Composable
fun TelaConferidorProva(voltar: () -> Unit) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()

    var nomeArquivo by rememberSaveable { mutableStateOf<String?>(null) }
    var hashCalculado by rememberSaveable { mutableStateOf<String?>(null) }
    var calculando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var hashEsperado by rememberSaveable { mutableStateOf("") }

    val escolherArquivo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        erro = null
        hashCalculado = null
        calculando = true
        nomeArquivo = uri.lastPathSegment
        contexto.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) nomeArquivo = cursor.getString(idx) ?: nomeArquivo
        }
        escopo.launch(Dispatchers.IO) {
            val resultado = runCatching {
                val entrada = contexto.contentResolver.openInputStream(uri)
                    ?: error("Não foi possível abrir o arquivo escolhido.")
                entrada.use { Integridade.sha256(it) }
            }
            hashCalculado = resultado.getOrNull()
            erro = resultado.exceptionOrNull()?.message
            calculando = false
        }
    }

    val hashLimpo = remember(hashEsperado) {
        hashEsperado.trim().lowercase().filter { it.isLetterOrDigit() }
    }
    val confere = remember(hashCalculado, hashLimpo) {
        hashCalculado != null && hashLimpo.isNotBlank() && hashCalculado == hashLimpo
    }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Conferidor de prova", voltar)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                "Confere se um arquivo bate com o SHA-256 escrito num laudo — sem precisar de " +
                    "terminal nem instalar mais nada. Escolha o arquivo (a foto, por exemplo) e " +
                    "cole o hash que está no laudo.",
                color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp
            )
            Spacer(Modifier.height(14.dp))

            BotaoLargo(if (nomeArquivo == null) "Escolher arquivo" else "Trocar arquivo") {
                escolherArquivo.launch(arrayOf("*/*"))
            }

            nomeArquivo?.let {
                Spacer(Modifier.height(10.dp))
                Rotulo("ARQUIVO ESCOLHIDO")
                Text(it, color = Cores.texto, fontSize = 13.sp)
            }

            if (calculando) {
                Spacer(Modifier.height(8.dp))
                Text("Calculando SHA-256…", color = Cores.textoFraco, fontSize = 11.5.sp)
            }
            erro?.let {
                Spacer(Modifier.height(8.dp))
                Text("Falha ao ler o arquivo: $it", color = Cores.atencaoClaro, fontSize = 11.5.sp)
            }
            hashCalculado?.let {
                Spacer(Modifier.height(8.dp))
                Rotulo("SHA-256 DO ARQUIVO ESCOLHIDO")
                Mono(it, Cores.texto, 11)
            }

            Spacer(Modifier.height(14.dp))
            Rotulo("HASH ESPERADO (COLE O QUE ESTÁ NO LAUDO)")
            OutlinedTextField(
                value = hashEsperado, onValueChange = { hashEsperado = it },
                placeholder = { Text("64 caracteres em hexadecimal") },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth()
            )

            if (hashCalculado != null && hashLimpo.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.fillMaxWidth()
                        .background(if (confere) Cores.bom else Cores.alerta, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        if (confere) "CONFERE" else "NÃO CONFERE",
                        color = Color.White,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (confere) "O arquivo é bit a bit o mesmo que gerou esse hash."
                        else "O arquivo NÃO é o mesmo que gerou esse hash — foi alterado, " +
                            "recomprimido, ou o hash colado não é deste arquivo.",
                        color = Color(0xE6FFFFFF),
                        fontSize = 12.5.sp, lineHeight = 17.sp
                    )
                }
            }
        }
    }
}
