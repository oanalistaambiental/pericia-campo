package br.com.oanalistaambiental.pericia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.dados.Condicionante
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
    var novaAberta by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Condicionantes e prazos", voltar)

        if (novaAberta) {
            NovaCondicionante(vm, aoTerminar = { novaAberta = false })
        } else {
            Box(Modifier.padding(16.dp)) {
                BotaoLargo("+ Nova condicionante", principal = true) { novaAberta = true }
            }
            ListaCondicionantes(vm, condicionantes)
        }
    }
}

@Composable
private fun ColumnScope.ListaCondicionantes(vm: CapturaViewModel, condicionantes: List<Condicionante>) {
    if (condicionantes.isEmpty()) {
        Vazio(
            "Nenhuma condicionante",
            "Cadastre um prazo — renovação de licença, DMR mensal, qualquer condicionante com data."
        )
        return
    }
    val hoje = LocalDate.now()
    LazyColumn(Modifier.weight(1f)) {
        items(condicionantes) { c -> LinhaCondicionante(vm, c, hoje) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LinhaCondicionante(vm: CapturaViewModel, c: Condicionante, hoje: LocalDate) {
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

@Composable
private fun ColumnScope.NovaCondicionante(vm: CapturaViewModel, aoTerminar: () -> Unit) {
    var descricao by rememberSaveable { mutableStateOf("") }
    var formaCumprimento by rememberSaveable { mutableStateOf("") }
    var dataTexto by rememberSaveable { mutableStateOf("") }
    var fotoParecer by remember { mutableStateOf<File?>(null) }
    var capturando by remember { mutableStateOf(false) }
    val data = remember(dataTexto) { interpretarDataCondicionante(dataTexto) }

    if (capturando) {
        CapturaFotoMinima(
            prefixoArquivo = "condicionante_temp",
            aoCapturar = { f -> fotoParecer = f; capturando = false },
            aoCancelar = { capturando = false }
        )
        return
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

                Rotulo("FOTO DO PARECER (OPCIONAL)")
                if (fotoParecer != null) {
                    Text("Foto anexada.", color = Cores.bomClaro, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                }
                BotaoLargo(if (fotoParecer == null) "Fotografar o parecer" else "Trocar foto") {
                    capturando = true
                }
            }
        }
    }

    Box(Modifier.padding(16.dp)) {
        BotaoLargo(
            "Salvar condicionante", principal = true,
            habilitado = descricao.isNotBlank() && data != null
        ) {
            val d = data ?: return@BotaoLargo
            val prazoMillis = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            vm.salvarCondicionante(
                descricao = descricao.trim(),
                formaCumprimento = formaCumprimento.trim().ifBlank { null },
                prazoData = prazoMillis,
                fotoOriginal = fotoParecer
            )
            aoTerminar()
        }
    }
}
