package br.com.oanalistaambiental.pericia.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import br.com.oanalistaambiental.pericia.dados.Condicionante
import br.com.oanalistaambiental.pericia.dados.Empreendimento
import br.com.oanalistaambiental.pericia.ocr.LeitorDeTexto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val formatoDataCondicionante = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun interpretarDataCondicionante(texto: String): LocalDate? =
    runCatching { LocalDate.parse(texto.trim(), formatoDataCondicionante) }.getOrNull()

/**
 * Condicionantes e prazos — a razão de o app ser aberto fora de uma vistoria: renovação de
 * licença, DMR mensal, qualquer condicionante com data. Cada uma pode nascer de uma foto do
 * parecer que a originou (prova de onde veio o prazo), mas o prazo em si é sempre confirmado por
 * quem preenche — o app não decide sozinho o que uma linha de parecer significa.
 */
@Composable
fun TelaCondicionantes(vm: CapturaViewModel, voltar: () -> Unit) {
    val condicionantes by vm.condicionantes.collectAsState()
    val empreendimentos by vm.empreendimentos.collectAsState()
    var novaAberta by rememberSaveable { mutableStateOf(false) }
    var editandoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var filtroEmpreendimentoId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editando = remember(editandoId, condicionantes) {
        editandoId?.let { id -> condicionantes.firstOrNull { it.id == id } }
    }
    val condicionantesFiltradas = remember(condicionantes, filtroEmpreendimentoId) {
        filtroEmpreendimentoId?.let { id -> condicionantes.filter { it.empreendimentoId == id } } ?: condicionantes
    }
    val contexto = LocalContext.current

    fun notificacoesOk() = ContextCompat.checkSelfPermission(
        contexto, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
    var permitido by remember { mutableStateOf(notificacoesOk()) }
    val pedirNotificacao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permitido = notificacoesOk() }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Condicionantes e prazos", voltar)

        if (!permitido) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(Cores.superficie, RoundedCornerShape(8.dp)).padding(12.dp)
            ) {
                Text(
                    "Sem aviso de prazo. Ative as notificações para ser lembrado antes de vencer.",
                    color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ativar notificações", color = Cores.bomClaro, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
            }
        }

        if (novaAberta || editando != null) {
            FormularioCondicionante(
                vm, editando = editando, empreendimentos = empreendimentos,
                aoTerminar = { novaAberta = false; editandoId = null }
            )
        } else {
            Box(Modifier.padding(16.dp)) {
                BotaoLargo("+ Nova condicionante", principal = true) { novaAberta = true }
            }
            if (empreendimentos.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChipFiltro("Todas", filtroEmpreendimentoId == null) { filtroEmpreendimentoId = null }
                    empreendimentos.forEach { e ->
                        ChipFiltro(e.nome, filtroEmpreendimentoId == e.id) { filtroEmpreendimentoId = e.id }
                    }
                }
            }
            ListaCondicionantes(vm, condicionantesFiltradas, empreendimentos, aoEditar = { editandoId = it.id })
        }
    }
}

@Composable
private fun ChipFiltro(rotulo: String, selecionado: Boolean, aoEscolher: () -> Unit) {
    Box(
        Modifier
            .background(if (selecionado) Cores.bom else Cores.superficie, RoundedCornerShape(14.dp))
            .clickable { aoEscolher() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            rotulo, color = if (selecionado) Color.White else Cores.textoFraco,
            fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ColumnScope.ListaCondicionantes(
    vm: CapturaViewModel, condicionantes: List<Condicionante>, empreendimentos: List<Empreendimento>,
    aoEditar: (Condicionante) -> Unit
) {
    if (condicionantes.isEmpty()) {
        Vazio(
            "Nenhuma condicionante",
            "Cadastre um prazo — renovação de licença, DMR mensal, qualquer condicionante com data."
        )
        return
    }
    val hoje = LocalDate.now()
    val nomesPorId = remember(empreendimentos) { empreendimentos.associate { it.id to it.nome } }
    LazyColumn(Modifier.weight(1f)) {
        items(condicionantes) { c -> LinhaCondicionante(vm, c, hoje, nomesPorId[c.empreendimentoId], aoEditar) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LinhaCondicionante(
    vm: CapturaViewModel, c: Condicionante, hoje: LocalDate, nomeEmpreendimento: String?,
    aoEditar: (Condicionante) -> Unit
) {
    var confirmarExclusao by remember { mutableStateOf(false) }
    val prazo = remember(c.prazoData) {
        java.time.Instant.ofEpochMilli(c.prazoData).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    val diasRestantes = remember(prazo, hoje) { prazo.toEpochDay() - hoje.toEpochDay() }
    val cor = when {
        c.cumprida -> Cores.textoFraco
        diasRestantes < 0 -> Cores.alertaClaro
        diasRestantes <= 30 -> Cores.atencaoClaro
        else -> Cores.bomClaro
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .background(Cores.superficie, RoundedCornerShape(6.dp)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                prazo.format(formatoDataCondicionante), color = cor,
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { vm.marcarCondicionanteCumprida(c, !c.cumprida) }
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    c.cumprida -> "cumprida"
                    diasRestantes < 0 -> "vencida há ${-diasRestantes} dia(s)"
                    diasRestantes == 0L -> "vence hoje"
                    else -> "faltam $diasRestantes dia(s)"
                },
                color = cor, fontSize = 10.5.sp
            )
        }
        nomeEmpreendimento?.let {
            Spacer(Modifier.height(2.dp))
            Mono(it.uppercase(), Cores.bomClaro, 10)
        }
        Spacer(Modifier.height(4.dp))
        Text(c.descricao, color = Cores.texto, fontSize = 13.sp, lineHeight = 18.sp)
        c.formaCumprimento?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text("Como cumprir: $it", color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp)
        }
        if (c.fotoArquivo != null) {
            Spacer(Modifier.height(4.dp))
            Text("com foto do parecer", color = Cores.bomClaro, fontSize = 10.5.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Text(
                if (c.cumprida) "reabrir" else "marcar cumprida",
                color = Cores.bomClaro, fontSize = 11.5.sp,
                modifier = Modifier.clickable { vm.marcarCondicionanteCumprida(c, !c.cumprida) }
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "editar", color = Cores.bomClaro, fontSize = 11.5.sp,
                modifier = Modifier.clickable { aoEditar(c) }
            )
            Spacer(Modifier.width(16.dp))
            Text(
                if (confirmarExclusao) "confirmar exclusão?" else "excluir",
                color = Cores.alertaClaro, fontSize = 11.5.sp,
                modifier = Modifier.clickable {
                    if (confirmarExclusao) { vm.excluirCondicionante(c); confirmarExclusao = false }
                    else confirmarExclusao = true
                }
            )
        }
    }
}

private val DIAS_ANTECEDENCIA_OPCOES = listOf(7, 15, 30)

@Composable
private fun ColumnScope.FormularioCondicionante(
    vm: CapturaViewModel, editando: Condicionante?, empreendimentos: List<Empreendimento>,
    aoTerminar: () -> Unit
) {
    val chave = editando?.id ?: -1L
    var descricao by rememberSaveable(chave) { mutableStateOf(editando?.descricao ?: "") }
    var formaCumprimento by rememberSaveable(chave) { mutableStateOf(editando?.formaCumprimento ?: "") }
    var dataTexto by rememberSaveable(chave) {
        mutableStateOf(
            editando?.let {
                java.time.Instant.ofEpochMilli(it.prazoData).atZone(ZoneId.systemDefault())
                    .toLocalDate().format(formatoDataCondicionante)
            } ?: ""
        )
    }
    var diasAntecedencia by rememberSaveable(chave) { mutableStateOf(editando?.diasAntecedencia ?: 15) }
    var empreendimentoId by rememberSaveable(chave) { mutableStateOf(editando?.empreendimentoId) }
    var novoEmpreendimentoNome by rememberSaveable(chave) { mutableStateOf("") }
    var criandoEmpreendimento by remember { mutableStateOf(false) }
    var fotoParecer by remember(chave) { mutableStateOf<File?>(null) }
    var capturando by remember { mutableStateOf(false) }
    var linhasReconhecidas by remember { mutableStateOf<List<String>>(emptyList()) }
    var lendoFoto by remember { mutableStateOf(false) }
    var falhaLeitura by remember { mutableStateOf(false) }
    val data = remember(dataTexto) { interpretarDataCondicionante(dataTexto) }
    val escopo = rememberCoroutineScope()

    if (capturando) {
        CapturaFotoMinima(
            prefixoArquivo = "condicionante_temp",
            aoCapturar = { f ->
                fotoParecer = f
                capturando = false
                linhasReconhecidas = emptyList()
                falhaLeitura = false
                lendoFoto = true
                escopo.launch(Dispatchers.Default) {
                    val bitmap = BitmapFactory.decodeFile(f.absolutePath)
                    val linhas = if (bitmap != null) {
                        runCatching { LeitorDeTexto.reconhecerLinhas(bitmap) }.getOrNull()
                    } else null
                    linhasReconhecidas = linhas ?: emptyList()
                    falhaLeitura = linhas == null
                    lendoFoto = false
                    if (dataTexto.isBlank()) {
                        LeitorDeTexto.sugerirData(linhasReconhecidas)?.let { dataTexto = it }
                    }
                }
            },
            aoCancelar = { capturando = false }
        )
        return
    }

    Row(
        Modifier.fillMaxWidth().clickable { aoTerminar() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (editando == null) "‹ cancelar" else "‹ cancelar edição",
            color = Cores.bomClaro, fontSize = 13.sp
        )
    }

    LazyColumn(Modifier.weight(1f)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Rotulo("DESCRIÇÃO DA CONDICIONANTE")
                OutlinedTextField(
                    value = descricao, onValueChange = { descricao = it },
                    placeholder = { Text("Ex.: apresentar relatório de monitoramento trimestral") },
                    modifier = Modifier.fillMaxWidth()
                )

                Rotulo("PRAZO (DD/MM/AAAA)")
                OutlinedTextField(
                    value = dataTexto, onValueChange = { dataTexto = it },
                    placeholder = { Text("15/03/2027") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (dataTexto.isNotBlank() && data == null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Não reconheci essa data. Use dd/mm/aaaa.",
                        color = Cores.atencaoClaro, fontSize = 11.5.sp
                    )
                }

                Rotulo("COMO CUMPRIR (OPCIONAL)")
                OutlinedTextField(
                    value = formaCumprimento, onValueChange = { formaCumprimento = it },
                    placeholder = { Text("Ex.: protocolar no SEI, anexando laudo e ART") },
                    modifier = Modifier.fillMaxWidth()
                )

                Rotulo("AVISAR COM QUANTOS DIAS DE ANTECEDÊNCIA")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DIAS_ANTECEDENCIA_OPCOES.forEach { dias ->
                        OpcaoDiasAntecedencia(
                            dias, selecionado = diasAntecedencia == dias,
                            aoEscolher = { diasAntecedencia = dias }
                        )
                    }
                }

                Rotulo("EMPREENDIMENTO (OPCIONAL) — PARA AGRUPAR PRAZOS DA MESMA LICENÇA")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChipFiltro("Nenhum", empreendimentoId == null) { empreendimentoId = null }
                    empreendimentos.forEach { e ->
                        ChipFiltro(e.nome, empreendimentoId == e.id) { empreendimentoId = e.id }
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (criandoEmpreendimento) {
                    OutlinedTextField(
                        value = novoEmpreendimentoNome, onValueChange = { novoEmpreendimentoNome = it },
                        placeholder = { Text("Ex.: Mineradora X — LO 2024 (nome genérico, sem CNPJ)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text(
                            "criar", color = Cores.bomClaro, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                val nome = novoEmpreendimentoNome.trim()
                                if (nome.isNotBlank()) {
                                    vm.criarEmpreendimento(nome) { id -> empreendimentoId = id }
                                    novoEmpreendimentoNome = ""
                                    criandoEmpreendimento = false
                                }
                            }
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            "cancelar", color = Cores.textoFraco, fontSize = 12.5.sp,
                            modifier = Modifier.clickable { criandoEmpreendimento = false; novoEmpreendimentoNome = "" }
                        )
                    }
                } else {
                    Text(
                        "+ novo empreendimento", color = Cores.bomClaro, fontSize = 12.5.sp,
                        modifier = Modifier.clickable { criandoEmpreendimento = true }
                    )
                }

                Rotulo("FOTO DO PARECER (OPCIONAL)")
                if (fotoParecer != null) {
                    Text("Foto nova anexada.", color = Cores.bomClaro, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                } else if (editando?.fotoArquivo != null) {
                    Text("Já tem a foto do cadastro original.", color = Cores.textoFraco, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                }
                BotaoLargo(if (fotoParecer == null && editando?.fotoArquivo == null) "Fotografar o parecer" else "Trocar foto") {
                    capturando = true
                }

                if (lendoFoto) {
                    Spacer(Modifier.height(8.dp))
                    Text("Lendo texto da foto…", color = Cores.textoFraco, fontSize = 11.5.sp)
                }
                if (falhaLeitura) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Não consegui ler texto nessa foto — preencha à mão.",
                        color = Cores.atencaoClaro, fontSize = 11.5.sp
                    )
                }
                if (linhasReconhecidas.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Rotulo("TEXTO RECONHECIDO — TOQUE PARA USAR COMO DESCRIÇÃO")
                    linhasReconhecidas.forEach { linha ->
                        Text(
                            linha, color = Cores.texto, fontSize = 12.sp, lineHeight = 17.sp,
                            modifier = Modifier.fillMaxWidth()
                                .background(Cores.superficie, RoundedCornerShape(6.dp))
                                .clickable { descricao = linha }
                                .padding(10.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }

    Box(Modifier.padding(16.dp)) {
        BotaoLargo(
            if (editando == null) "Salvar condicionante" else "Salvar alterações", principal = true,
            habilitado = descricao.isNotBlank() && data != null
        ) {
            val d = data ?: return@BotaoLargo
            val prazoMillis = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (editando == null) {
                vm.salvarCondicionante(
                    descricao = descricao.trim(),
                    formaCumprimento = formaCumprimento.trim().ifBlank { null },
                    prazoData = prazoMillis,
                    fotoOriginal = fotoParecer,
                    diasAntecedencia = diasAntecedencia,
                    empreendimentoId = empreendimentoId
                )
            } else {
                vm.atualizarCondicionante(
                    existente = editando,
                    descricao = descricao.trim(),
                    formaCumprimento = formaCumprimento.trim().ifBlank { null },
                    prazoData = prazoMillis,
                    diasAntecedencia = diasAntecedencia,
                    novaFotoOriginal = fotoParecer,
                    empreendimentoId = empreendimentoId
                )
            }
            aoTerminar()
        }
    }
}

@Composable
private fun RowScope.OpcaoDiasAntecedencia(dias: Int, selecionado: Boolean, aoEscolher: () -> Unit) {
    Box(
        Modifier.weight(1f)
            .background(if (selecionado) Cores.bom else Cores.superficie, RoundedCornerShape(6.dp))
            .clickable { aoEscolher() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$dias dias",
            color = if (selecionado) Color.White else Cores.textoFraco,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )
    }
}
