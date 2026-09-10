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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.ui.Cores
import java.text.Normalizer

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
 * botao, no lugar da lista de cartoes — e depois, por pedido explicito, sem a folha de
 * confirmacao no meio: o toque no icone abre a ferramenta direto, sem caixa de aviso antes.
 *
 * BUSCA E RECENTES entraram depois, com a gaveta já em ~20 ferramentas: quem não sabe em qual
 * seção uma ferramenta cai não devia precisar aprender a categorização só para achá-la, e quem
 * usa sempre as mesmas duas ou três não devia rolar a tela toda vez.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaGaveta(
    disponivel: (Recurso) -> Boolean,
    irParaCamera: () -> Unit,
    irParaVistorias: () -> Unit,
    abrir: (String) -> Unit
) {
    val contexto = LocalContext.current
    var busca by remember { mutableStateOf("") }

    fun aoAbrirItem(id: String, aoAbrir: () -> Unit) {
        HistoricoGaveta.registrarUso(contexto, id)
        aoAbrir()
    }

    // A camera e as vistorias vem primeiro, dentro da propria secao de campo: dependem de
    // estado que vive na Activity e nao passam pelo registro, mas sao trabalho de campo que
    // uma pessoa faz tanto quanto medir ou fotografar um ponto.
    val essenciaisDeCampo = listOf(
        ItemGaveta(
            id = "camera",
            nome = "Câmera de perícia",
            icone = IconeGaveta.CAMERA,
            destaque = true,
            faltando = if (disponivel(Recurso.CAMERA)) emptyList() else listOf(Recurso.CAMERA),
            aoAbrir = { aoAbrirItem("camera", irParaCamera) }
        ),
        ItemGaveta(
            id = "vistorias",
            nome = "Vistorias e laudos",
            icone = IconeGaveta.PASTA,
            faltando = emptyList(),
            aoAbrir = { aoAbrirItem("vistorias", irParaVistorias) }
        )
    )

    val itensRegistro = Registro.ferramentas.map { f ->
        ItemGaveta(
            id = f.id,
            nome = f.nome,
            icone = f.icone,
            faltando = f.exige.filterNot(disponivel),
            aoAbrir = { aoAbrirItem(f.id) { abrir(f.id) } }
        )
    }
    val todosItens = essenciaisDeCampo + itensRegistro

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
                "Toque num botão para abrir a ferramenta.",
                color = Cores.textoFraco, fontSize = 12.5.sp, lineHeight = 17.sp
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = busca, onValueChange = { busca = it },
                placeholder = { Text("Buscar ferramenta") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (busca.isNotBlank()) {
            val encontrados = remember(busca, todosItens) {
                val alvo = normalizar(busca)
                todosItens.filter { normalizar(it.nome).contains(alvo) }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (encontrados.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Nenhuma ferramenta com esse nome.",
                            color = Cores.textoFraco, fontSize = 12.5.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(encontrados) { item -> BotaoGaveta(item, aoTocar = { item.aoAbrir() }) }
                }
            }
        } else {
            // Agrupado por Grupo — nao pelo nome "campo"/"escritorio", mas pelo que a pessoa vai
            // fazer ali: medir e registrar no local, ou consultar e calcular sentada. A mesma
            // distincao que ja orientou a aba de recursos hidricos, agora valendo para a gaveta
            // inteira, nao so para uma ferramenta.
            val porGrupo = Registro.ferramentas.groupBy { it.grupo }.mapValues { (_, lista) ->
                lista.map { f -> itensRegistro.first { it.id == f.id } }
            }
            val idsRecentes = HistoricoGaveta.recentes(contexto)
            val itensRecentes = idsRecentes.mapNotNull { id -> todosItens.firstOrNull { it.id == id } }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (itensRecentes.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { CabecalhoSecao("USADOS RECENTEMENTE") }
                    items(itensRecentes) { item -> BotaoGaveta(item, aoTocar = { item.aoAbrir() }) }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { CabecalhoSecao(Grupo.CAMPO.titulo) }
                items(essenciaisDeCampo + porGrupo[Grupo.CAMPO].orEmpty()) { item ->
                    BotaoGaveta(item, aoTocar = { item.aoAbrir() })
                }
                for (grupo in Grupo.entries) {
                    if (grupo == Grupo.CAMPO) continue
                    val doGrupo = porGrupo[grupo].orEmpty()
                    if (doGrupo.isEmpty()) continue
                    item(span = { GridItemSpan(maxLineSpan) }) { CabecalhoSecao(grupo.titulo) }
                    items(doGrupo) { item ->
                        BotaoGaveta(item, aoTocar = { item.aoAbrir() })
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** Sem acento e minúsculo — "água" e "agua" devem achar a mesma coisa na busca. */
private fun normalizar(texto: String): String {
    val semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return semAcento.lowercase()
}

@Composable
private fun CabecalhoSecao(titulo: String) {
    Text(
        titulo,
        color = Cores.textoFraco, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

/** Um botao da grade. */
private class ItemGaveta(
    val id: String,
    val nome: String,
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
