package br.com.oanalistaambiental.pericia.ferramentas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.ui.Cores

/**
 * A tela inicial: a gaveta, em grade de botoes com icone.
 *
 * MUDANCA DE PRODUTO, e vale dizer por que. A camera era a tela de abertura, e o aplicativo
 * bloqueava tudo enquanto a permissao de camera nao fosse concedida. Isso fazia sentido quando
 * o aplicativo era uma camera. Nao faz mais: quem abre para usar a bussola batia numa parede
 * que nao tinha nada a ver com o que veio fazer.
 *
 * Agora a gaveta abre sem exigir permissao nenhuma, cada ferramenta declara o que precisa, e a
 * permissao e pedida por quem vai usar — no momento em que vai usar.
 *
 * A GRADE, e o que ela preserva do desenho anterior. O pedido foi por icones grandes, tipo
 * botao, no lugar da lista de cartoes. O que a lista de cartoes garantia — o [Ferramenta.limite]
 * lido ANTES de abrir — nao pode se perder so porque o botao ficou menor: por isso o toque no
 * icone abre uma folha com resumo, limite e o que falta, e so dali se abre a ferramenta. Um
 * toque a mais do que antes, pelo icone permanecer legivel e o limite continuar aparecendo
 * sempre, sem exceção.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaGaveta(
    disponivel: (Recurso) -> Boolean,
    irParaCamera: () -> Unit,
    irParaVistorias: () -> Unit,
    abrir: (String) -> Unit
) {
    var selecionado by remember { mutableStateOf<ItemGaveta?>(null) }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
            Text(
                "KIT DE PERÍCIA AMBIENTAL",
                color = Cores.texto, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Toque num botão para ver o que ele faz e o que não faz, antes de abrir.",
                color = Cores.textoFraco, fontSize = 12.5.sp, lineHeight = 17.sp
            )
        }

        // A camera e as vistorias vem primeiro: dependem de estado que vive na Activity e nao
        // passam pelo registro, mas entram na mesma grade dos itens que passam.
        val itens = buildList {
            add(
                ItemGaveta(
                    nome = "Câmera de perícia",
                    resumo = "Foto com coordenada, legenda queimada na imagem e cadeia de custódia.",
                    limite = "O hash prova que o arquivo não mudou desde a captura; quem prova " +
                        "DESDE QUANDO é o carimbo do tempo, aplicado ao fechar a vistoria.",
                    icone = IconeGaveta.CAMERA,
                    destaque = true,
                    faltando = if (disponivel(Recurso.CAMERA)) emptyList() else listOf(Recurso.CAMERA),
                    aoAbrir = irParaCamera
                )
            )
            add(
                ItemGaveta(
                    nome = "Vistorias e laudos",
                    resumo = "Sessões gravadas, conferência da prova, laudo em PDF e exportações.",
                    limite = "A conferência responde duas perguntas separadas: se o arquivo mudou " +
                        "e se ele pertence ao conjunto selado. Uma não supre a outra.",
                    icone = IconeGaveta.PASTA,
                    faltando = emptyList(),
                    aoAbrir = irParaVistorias
                )
            )
            for (f in Registro.ferramentas) {
                add(
                    ItemGaveta(
                        nome = f.nome,
                        resumo = f.resumo,
                        limite = f.limite,
                        icone = f.icone,
                        faltando = f.exige.filterNot(disponivel),
                        aoAbrir = { abrir(f.id) }
                    )
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(itens) { item ->
                BotaoGaveta(item, aoTocar = { selecionado = item })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    val item = selecionado
    if (item != null) {
        val estadoFolha = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { selecionado = null }, sheetState = estadoFolha) {
            DetalheFerramenta(
                item = item,
                aoAbrir = {
                    selecionado = null
                    item.aoAbrir()
                }
            )
        }
    }
}

/** Um botao da grade e o que ele precisa para abrir a folha de detalhe. */
private class ItemGaveta(
    val nome: String,
    val resumo: String,
    val limite: String,
    val icone: IconeGaveta,
    val faltando: List<Recurso>,
    val destaque: Boolean = false,
    val aoAbrir: () -> Unit
)

@Composable
private fun BotaoGaveta(item: ItemGaveta, aoTocar: () -> Unit) {
    val corFundo = if (item.destaque) Cores.bom else Cores.superficie
    val corTexto = if (item.destaque) Color.White else Cores.texto
    Column(
        Modifier
            .aspectRatio(0.92f)
            .background(corFundo, RoundedCornerShape(14.dp))
            .clickable(onClick = aoTocar)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        DesenharIcone(item.icone, corTexto)
        Spacer(Modifier.height(8.dp))
        Text(
            item.nome,
            color = corTexto, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp, textAlign = TextAlign.Center, maxLines = 3
        )
        if (item.faltando.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "precisa de " + item.faltando.joinToString(" e ") { it.descricao },
                color = if (item.destaque) Color(0xE6FFFFFF) else Cores.atencaoClaro,
                fontSize = 9.5.sp, lineHeight = 12.sp, textAlign = TextAlign.Center, maxLines = 2
            )
        }
    }
}

/**
 * O que aparece ao tocar num botao da grade: o mesmo texto que a lista de cartoes mostrava,
 * antes de abrir. E aqui, nao no icone, que o [ItemGaveta.limite] continua obrigatorio.
 */
@Composable
private fun DetalheFerramenta(item: ItemGaveta, aoAbrir: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(item.nome, color = Cores.texto, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(item.resumo, color = Cores.texto, fontSize = 14.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "NÃO FAZ: ${item.limite}",
            color = Cores.textoFraco, fontSize = 12.5.sp, lineHeight = 17.sp
        )
        if (item.faltando.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Precisa de " + item.faltando.joinToString(" e ") { it.descricao } +
                    " — será pedido ao abrir.",
                color = Cores.atencaoClaro, fontSize = 12.5.sp, lineHeight = 17.sp
            )
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = aoAbrir,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Cores.bom)
        ) {
            Text("Abrir")
        }
        Spacer(Modifier.height(24.dp))
    }
}
