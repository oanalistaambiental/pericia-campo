package br.com.oanalistaambiental.pericia.ferramentas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.ui.Cores

/**
 * A tela inicial: a gaveta.
 *
 * MUDANCA DE PRODUTO, e vale dizer por que. A camera era a tela de abertura, e o aplicativo
 * bloqueava tudo enquanto a permissao de camera nao fosse concedida. Isso fazia sentido quando
 * o aplicativo era uma camera. Nao faz mais: quem abre para usar a bussola batia numa parede
 * que nao tinha nada a ver com o que veio fazer.
 *
 * Agora a gaveta abre sem exigir permissao nenhuma, cada ferramenta declara o que precisa, e a
 * permissao e pedida por quem vai usar — no momento em que vai usar.
 *
 * A camera continua sendo o primeiro item, e grande: continua sendo a razao pela qual a maioria
 * abre o aplicativo.
 */
@Composable
fun TelaGaveta(
    disponivel: (Recurso) -> Boolean,
    irParaCamera: () -> Unit,
    irParaVistorias: () -> Unit,
    abrir: (String) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
                Text(
                    "KIT DE PERÍCIA AMBIENTAL",
                    color = Cores.texto, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ferramentas de campo e de consulta. Cada uma diz o que faz e o que não faz.",
                    color = Cores.textoFraco, fontSize = 12.5.sp, lineHeight = 17.sp
                )
            }
        }

        // A camera e as vistorias vem primeiro e por caminho proprio: dependem de estado que
        // vive na Activity e nao passam pelo registro.
        item {
            Rotulo(Grupo.CAMPO.titulo)
            Cartao(
                nome = "Câmera de perícia",
                resumo = "Foto com coordenada, legenda queimada na imagem e cadeia de custódia.",
                limite = "O hash prova que o arquivo não mudou desde a captura; quem prova " +
                    "DESDE QUANDO é o carimbo do tempo, aplicado ao fechar a vistoria.",
                faltando = if (disponivel(Recurso.CAMERA)) emptyList() else listOf(Recurso.CAMERA),
                destaque = true,
                aoClicar = irParaCamera
            )
            Cartao(
                nome = "Vistorias e laudos",
                resumo = "Sessões gravadas, conferência da prova, laudo em PDF e exportações.",
                limite = "A conferência responde duas perguntas separadas: se o arquivo mudou " +
                    "e se ele pertence ao conjunto selado. Uma não supre a outra.",
                faltando = emptyList(),
                aoClicar = irParaVistorias
            )
        }

        for (grupo in Grupo.entries) {
            val doGrupo = Registro.ferramentas.filter { it.grupo == grupo }
            if (doGrupo.isEmpty()) continue
            // O grupo CAMPO ja teve seu rotulo escrito acima, junto da camera.
            if (grupo != Grupo.CAMPO) item { Rotulo(grupo.titulo) }
            items@ for (f in doGrupo) {
                item(key = f.id) {
                    Cartao(
                        nome = f.nome,
                        resumo = f.resumo,
                        limite = f.limite,
                        faltando = f.exige.filterNot(disponivel),
                        aoClicar = { abrir(f.id) }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun Rotulo(texto: String) {
    Text(
        texto,
        color = Cores.textoFraco, fontSize = 10.5.sp, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp)
    )
}

/**
 * Um item da gaveta.
 *
 * O [limite] aparece AQUI, antes de abrir, e nao so dentro da ferramenta. Quem escolhe a
 * ferramenta certa e quem sabe o que ela nao faz — e essa e a informacao que costuma chegar
 * tarde demais.
 */
@Composable
private fun Cartao(
    nome: String,
    resumo: String,
    limite: String,
    faltando: List<Recurso>,
    destaque: Boolean = false,
    aoClicar: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .background(
                if (destaque) Cores.bom else Cores.superficie,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = aoClicar)
            .padding(14.dp)
    ) {
        Text(
            nome,
            color = if (destaque) androidx.compose.ui.graphics.Color.White else Cores.texto,
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(3.dp))
        Text(
            resumo,
            color = if (destaque) androidx.compose.ui.graphics.Color(0xE6FFFFFF) else Cores.textoFraco,
            fontSize = 12.5.sp, lineHeight = 17.sp
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "NÃO FAZ: $limite",
            color = if (destaque) androidx.compose.ui.graphics.Color(0xCCFFFFFF) else Cores.textoFraco,
            fontSize = 11.sp, lineHeight = 15.sp
        )
        if (faltando.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Text(
                "Precisa de " + faltando.joinToString(" e ") { it.descricao } +
                    " — será pedido ao abrir.",
                color = Cores.atencaoClaro, fontSize = 11.sp, lineHeight = 15.sp
            )
        }
    }
}
