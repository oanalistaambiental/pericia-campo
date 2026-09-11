package br.com.oanalistaambiental.pericia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.captura.EstadoCampo
import br.com.oanalistaambiental.pericia.dados.RegistroFicha
import br.com.oanalistaambiental.pericia.exportacao.Exportador
import br.com.oanalistaambiental.pericia.fichas.ModeloFicha
import br.com.oanalistaambiental.pericia.fichas.Resposta
import br.com.oanalistaambiental.pericia.fichas.ValorResposta
import br.com.oanalistaambiental.pericia.fichas.parseRespostas
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ficha de vistoria configurável: escolhe o tipo de empreendimento, responde o checklist daquele
 * catálogo (`assets/fichas/fichas.json` — dado, não código, mesma razão de `enquadramento/norma`)
 * e salva com a coordenada de quem preencheu. É roteiro de apoio, nunca a vistoria em si — cada
 * modelo já diz isso no próprio [br.com.oanalistaambiental.pericia.ferramentas.Ferramenta.limite].
 */
@Composable
fun TelaFichaVistoria(vm: CapturaViewModel, voltar: () -> Unit) {
    val modelos by vm.modelosFicha.collectAsState()
    val registros by vm.registrosFicha.collectAsState()
    val p by vm.estadoCampo.posicao.collectAsState()
    var modeloId by rememberSaveable { mutableStateOf<String?>(null) }
    val modelo = modelos.firstOrNull { it.id == modeloId }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Ficha de vistoria", voltar)

        if (modelo == null) {
            EscolhaModelo(modelos, registros, vm, aoEscolher = { modeloId = it.id })
        } else {
            PreenchimentoFicha(vm, modelo, p, aoTrocarModelo = { modeloId = null })
        }
    }
}

@Composable
private fun ColumnScope.EscolhaModelo(
    modelos: List<ModeloFicha>,
    registros: List<RegistroFicha>,
    vm: CapturaViewModel,
    aoEscolher: (ModeloFicha) -> Unit
) {
    LazyColumn(Modifier.weight(1f)) {
        item { Rotulo("ESCOLHA O TIPO DE EMPREENDIMENTO") }
        items(modelos) { m ->
            Column(
                Modifier.fillMaxWidth().clickable { aoEscolher(m) }
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Text(m.nome, color = Cores.texto, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Mono("${m.totalItens} item(ns) de checklist")
            }
            HorizontalDivider(color = Cores.linha)
        }
        if (modelos.isEmpty()) {
            item {
                Text(
                    "Catálogo de fichas não carregou.",
                    color = Cores.textoFraco, fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        if (registros.isNotEmpty()) {
            item { Rotulo("FICHAS SALVAS") }
            items(registros) { r -> LinhaRegistroFicha(vm, r) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LinhaRegistroFicha(vm: CapturaViewModel, r: RegistroFicha) {
    var confirmarExclusao by remember { mutableStateOf(false) }
    val contexto = LocalContext.current
    val respostas = remember(r.respostasJson) { runCatching { parseRespostas(r.respostasJson) }.getOrDefault(emptyList()) }
    val naoConformes = respostas.count { it.valor == ValorResposta.NAO_CONFORME }
    CartaoItem {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.modeloNome, color = Cores.texto, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                if (naoConformes > 0) "$naoConformes não conforme(s)" else "sem não conformidade",
                color = if (naoConformes > 0) Cores.alertaClaro else Cores.bomClaro,
                fontSize = 10.5.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(2.dp))
        Mono("${respostas.size} item(ns) respondido(s)")
        Spacer(Modifier.height(6.dp))
        Row {
            AcaoTexto("compartilhar") {
                Exportador.compartilhar(
                    contexto, emptyList(),
                    "Ficha de vistoria — ${r.modeloNome}", textoFicha(r, respostas)
                )
            }
            Spacer(Modifier.width(16.dp))
            AcaoTexto(
                if (confirmarExclusao) "confirmar exclusão?" else "excluir",
                cor = Cores.alertaClaro
            ) {
                if (confirmarExclusao) { vm.excluirRegistroFicha(r); confirmarExclusao = false }
                else confirmarExclusao = true
            }
        }
    }
}

/** Texto simples pronto para WhatsApp/e-mail — mesmo raciocínio do corpo usado em Ocorrência. */
private fun textoFicha(r: RegistroFicha, respostas: List<Resposta>): String = buildString {
    appendLine("FICHA DE VISTORIA — ${r.modeloNome.uppercase()}")
    appendLine(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(r.instante)))
    appendLine("Coordenada: %.6f, %.6f".format(r.lat, r.lon))
    appendLine()
    respostas.groupBy { it.secao }.forEach { (secao, itens) ->
        appendLine(secao.uppercase())
        itens.forEach { resp ->
            val marca = when (resp.valor) {
                ValorResposta.CONFORME -> "[CONFORME]"
                ValorResposta.NAO_CONFORME -> "[NÃO CONFORME]"
                ValorResposta.NAO_SE_APLICA -> "[N/A]"
                ValorResposta.NAO_RESPONDIDO -> "[NÃO RESPONDIDO]"
            }
            appendLine("$marca ${resp.item}")
            resp.observacao?.let { appendLine("   obs.: $it") }
        }
        appendLine()
    }
    append("Roteiro de apoio à vistoria, gerado pelo app — não substitui o registro formal.")
}

@Composable
private fun ColumnScope.PreenchimentoFicha(
    vm: CapturaViewModel,
    modelo: ModeloFicha,
    p: EstadoCampo.Posicao,
    aoTrocarModelo: () -> Unit
) {
    val valores = remember(modelo.id) { mutableStateMapOf<String, ValorResposta>() }
    val observacoes = remember(modelo.id) { mutableStateMapOf<String, String>() }
    fun chave(secao: String, item: String) = "$secao||$item"

    val totalItens = modelo.totalItens
    val respondidos = valores.values.count { it != ValorResposta.NAO_RESPONDIDO }
    val naoConformes = valores.values.count { it == ValorResposta.NAO_CONFORME }

    Row(
        Modifier.fillMaxWidth().clickable { aoTrocarModelo() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("‹ trocar tipo de empreendimento", color = Cores.bomClaro, fontSize = 13.sp)
    }
    Text(
        modelo.nome, color = Cores.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    Spacer(Modifier.height(4.dp))
    Mono("$respondidos de $totalItens respondido(s) · $naoConformes não conforme(s)", modifier = Modifier.padding(horizontal = 16.dp))

    LazyColumn(Modifier.weight(1f)) {
        modelo.secoes.forEach { secao ->
            item { Rotulo(secao.titulo.uppercase()) }
            items(secao.itens) { item ->
                val k = chave(secao.titulo, item.texto)
                ItemChecklist(
                    texto = item.texto,
                    valor = valores[k] ?: ValorResposta.NAO_RESPONDIDO,
                    observacao = observacoes[k] ?: "",
                    aoMudarValor = { valores[k] = it },
                    aoMudarObservacao = { observacoes[k] = it }
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    Box(Modifier.padding(16.dp)) {
        BotaoLargo("Salvar ficha", principal = true, habilitado = p.lat != null && p.lon != null) {
            val lat = p.lat
            val lon = p.lon
            if (lat != null && lon != null) {
                val respostas = modelo.secoes.flatMap { secao ->
                    secao.itens.map { item ->
                        val k = chave(secao.titulo, item.texto)
                        Resposta(
                            secao = secao.titulo, item = item.texto,
                            valor = valores[k] ?: ValorResposta.NAO_RESPONDIDO,
                            observacao = observacoes[k]?.ifBlank { null }
                        )
                    }
                }
                vm.salvarRegistroFicha(modelo, respostas, lat, lon, p.precisaoM)
                aoTrocarModelo()
            }
        }
    }
}

@Composable
private fun ItemChecklist(
    texto: String,
    valor: ValorResposta,
    observacao: String,
    aoMudarValor: (ValorResposta) -> Unit,
    aoMudarObservacao: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(texto, color = Cores.texto, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(6.dp))
        SeletorResposta(valor, aoMudarValor)
        if (valor == ValorResposta.NAO_CONFORME) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = observacao, onValueChange = aoMudarObservacao,
                label = { Text("Observação (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SeletorResposta(valor: ValorResposta, aoEscolher: (ValorResposta) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(
            "Conforme", valor == ValorResposta.CONFORME,
            corSelecionado = Cores.bom, modifier = Modifier.weight(1f)
        ) { aoEscolher(ValorResposta.CONFORME) }
        Chip(
            "Não conforme", valor == ValorResposta.NAO_CONFORME,
            corSelecionado = Cores.alerta, modifier = Modifier.weight(1f)
        ) { aoEscolher(ValorResposta.NAO_CONFORME) }
        Chip(
            "N/A", valor == ValorResposta.NAO_SE_APLICA,
            corSelecionado = Cores.neutro, modifier = Modifier.weight(1f)
        ) { aoEscolher(ValorResposta.NAO_SE_APLICA) }
    }
}
