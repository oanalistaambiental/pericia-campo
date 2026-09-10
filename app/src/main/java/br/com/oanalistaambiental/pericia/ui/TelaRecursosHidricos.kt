package br.com.oanalistaambiental.pericia.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.dados.RegistroCaptacao
import br.com.oanalistaambiental.pericia.geo.ClassificacaoUso
import br.com.oanalistaambiental.pericia.geo.ResultadoUsoInsignificante
import br.com.oanalistaambiental.pericia.geo.TipoCaptacao
import br.com.oanalistaambiental.pericia.geo.UsoInsignificante
import java.io.File

/**
 * Recursos hídricos: bacia/CH e PGRH pela coordenada, e o classificador de Cadastro de Uso
 * Insignificante × Outorga (DN CERH-MG 09/2004, 62/2019 e 76/2022). Dividida em CAMPO (o que
 * se usa parado no ponto, decidindo ali) e ESCRITÓRIO (a leitura tranquila da norma, com as
 * fontes oficiais) — pedido explícito, para não misturar "decidir agora" com "estudar depois".
 *
 * NÃO cobre (e diz isso claramente na tela, em vez de fingir cobrir): o regime de outorga acima
 * do limiar insignificante nas UPGRHs do norte, que usa o cálculo de Recurso Potencial Explotável
 * por bacia — não é um número simples, e não há tabela pública para embutir. Também não cobre
 * valores específicos de Área de Restrição e Controle por superexplotação, que o IGAM pode fixar
 * à parte — sem lista pública por Ottobacia.
 */
@Composable
fun TelaRecursosHidricos(vm: CapturaViewModel, voltar: () -> Unit) {
    var aba by rememberSaveable { mutableStateOf(0) }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Recursos hídricos", voltar)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.weight(1f)) { BotaoLargo("Campo", principal = aba == 0) { aba = 0 } }
            Box(Modifier.weight(1f)) { BotaoLargo("Escritório", principal = aba == 1) { aba = 1 } }
        }

        if (aba == 0) AbaCampo(vm) else AbaEscritorio()
    }
}

// ---------------------------------------------------------------- campo

@Composable
private fun ColumnScope.AbaCampo(vm: CapturaViewModel) {
    val p by vm.estadoCampo.posicao.collectAsState()
    val bacia by vm.bacia.collectAsState()
    val consultandoBacia by vm.consultandoBacia.collectAsState()
    val registros by vm.registrosCaptacao.collectAsState()

    LaunchedEffect(p.lat, p.lon) {
        val lat = p.lat
        val lon = p.lon
        if (lat != null && lon != null) vm.consultarBaciaHidrografica(lat, lon)
    }

    var tipo by rememberSaveable { mutableStateOf(TipoCaptacao.SUPERFICIAL) }
    var vazaoTexto by rememberSaveable { mutableStateOf("") }
    var acumulacaoTexto by rememberSaveable { mutableStateOf("") }
    var comBomba by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var fotoRegistro by remember { mutableStateOf<File?>(null) }
    var capturandoFoto by remember { mutableStateOf(false) }

    if (capturandoFoto) {
        CapturaFotoMinima(
            prefixoArquivo = "captacao_temp",
            aoCapturar = { arquivo -> fotoRegistro = arquivo; capturandoFoto = false },
            aoCancelar = { capturandoFoto = false }
        )
        return
    }

    LazyColumn(Modifier.weight(1f)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Rotulo("BACIA / CIRCUNSCRIÇÃO HIDROGRÁFICA")
                when {
                    p.lat == null -> Text(
                        "Aguardando GNSS…", color = Cores.atencaoClaro, fontSize = 12.sp
                    )
                    consultandoBacia && bacia == null -> Text(
                        "Consultando…", color = Cores.textoFraco, fontSize = 12.sp
                    )
                    bacia == null -> Text(
                        "Fora de qualquer CH mapeada (comum perto da divisa com outro estado).",
                        color = Cores.textoFraco, fontSize = 12.sp, lineHeight = 16.sp
                    )
                    else -> {
                        val b = bacia!!
                        Text(
                            "${b.sigla} — ${b.nome.substringAfter(": ").ifBlank { b.nome }}",
                            color = Cores.bomClaro, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Mono(
                            "Comitê de bacia: " + if (b.temComiteDeBacia) (b.situacaoComite ?: "sim") else "não instalado",
                            Cores.textoFraco, 10
                        )
                        Text(
                            "Plano de Gestão (PGRH) e regime de outorga acompanham o comitê de " +
                                "bacia desta CH — confirme com a Unidade Regional ou o IGAM os " +
                                "detalhes vigentes para esta bacia especificamente.",
                            color = Cores.textoFraco, fontSize = 10.5.sp, lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }

                Rotulo("TIPO DE CAPTAÇÃO")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OpcaoTipo("Superficial (rio, córrego, açude)", tipo == TipoCaptacao.SUPERFICIAL) {
                        tipo = TipoCaptacao.SUPERFICIAL
                    }
                    OpcaoTipo(
                        "Subterrânea — poço tubular", tipo == TipoCaptacao.SUBTERRANEA_POCO_TUBULAR
                    ) { tipo = TipoCaptacao.SUBTERRANEA_POCO_TUBULAR }
                    OpcaoTipo(
                        "Subterrânea — poço escavado, manual ou nascente",
                        tipo == TipoCaptacao.SUBTERRANEA_OUTRA
                    ) { tipo = TipoCaptacao.SUBTERRANEA_OUTRA }
                }

                Spacer(Modifier.height(14.dp))
                if (tipo == TipoCaptacao.SUPERFICIAL) {
                    OutlinedTextField(
                        value = vazaoTexto, onValueChange = { vazaoTexto = it },
                        label = { Text("Vazão de captação pretendida (L/s)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = acumulacaoTexto, onValueChange = { acumulacaoTexto = it },
                        label = { Text("Volume de barramento/açude (m³) — se houver") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = vazaoTexto, onValueChange = { vazaoTexto = it },
                        label = { Text("Captação pretendida (L/dia)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val vazao = vazaoTexto.replace(',', '.').toDoubleOrNull()
                val acumulacao = acumulacaoTexto.replace(',', '.').toDoubleOrNull()
                val resultado = if (vazao == null && acumulacao == null) null
                    else if (tipo == TipoCaptacao.SUPERFICIAL) {
                        UsoInsignificante.classificarSuperficial(vazao, acumulacao, bacia?.sigla)
                    } else {
                        UsoInsignificante.classificarSubterranea(tipo, vazao)
                    }
                if (resultado != null) {
                    Spacer(Modifier.height(14.dp))
                    CartaoResultado(resultado)

                    Rotulo("BOMBEAMENTO")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.weight(1f)) {
                            OpcaoTipo("Por gravidade", comBomba == false) { comBomba = false }
                        }
                        Box(Modifier.weight(1f)) {
                            OpcaoTipo("Com bomba", comBomba == true) { comBomba = true }
                        }
                    }

                    Rotulo("FOTO (OPCIONAL)")
                    val foto = fotoRegistro
                    if (foto != null) {
                        val bitmap = remember(foto) {
                            runCatching {
                                android.graphics.BitmapFactory.decodeFile(foto.absolutePath)
                                    ?.asImageBitmap()
                            }.getOrNull()
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap, contentDescription = "Foto da captação",
                                modifier = Modifier.fillMaxWidth().height(180.dp)
                                    .background(Cores.superficie, RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        BotaoLargo("Remover foto") { fotoRegistro = null }
                    } else {
                        BotaoLargo("Tirar foto") { capturandoFoto = true }
                    }

                    Spacer(Modifier.height(14.dp))
                    BotaoLargo("Salvar registro", principal = true, habilitado = p.temPosicao) {
                        val unidade = when {
                            tipo == TipoCaptacao.SUPERFICIAL && vazao != null -> "L/s"
                            tipo == TipoCaptacao.SUPERFICIAL -> "m³"
                            else -> "L/dia"
                        }
                        vm.salvarRegistroCaptacao(
                            p.lat!!, p.lon!!, p.precisaoM, tipo.name,
                            vazao ?: acumulacao, unidade, comBomba,
                            resultado.classificacao.name, resultado.baseLegal, fotoRegistro
                        )
                        vazaoTexto = ""; acumulacaoTexto = ""; comBomba = null; fotoRegistro = null
                    }
                }

                if (registros.isNotEmpty()) {
                    Rotulo("MEUS REGISTROS")
                    registros.forEach { r -> LinhaRegistroCaptacao(vm, r) }
                }

                Ajuda(
                    "O que isso não cobre",
                    "Acima do limiar insignificante, nas UPGRHs do norte (SF6 a SF10, JQ1 a " +
                        "JQ3, PA1, MU1 e bacias do Jucuruçu/Itanhém) o regime de outorga segue o " +
                        "cálculo de Recurso Potencial Explotável por bacia — não é um número " +
                        "simples, precisa de consulta ao IGAM. Em Área de Restrição e Controle " +
                        "por superexplotação, o IGAM também pode fixar valores próprios, " +
                        "diferentes dos usados aqui, sem lista pública por Ottobacia."
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LinhaRegistroCaptacao(vm: CapturaViewModel, r: RegistroCaptacao) {
    var confirmarExclusao by remember { mutableStateOf(false) }
    val insignificante = r.classificacao == ClassificacaoUso.INSIGNIFICANTE.name
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .background(Cores.superficie, RoundedCornerShape(6.dp)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                rotuloTipoCaptacao(r.tipoCaptacao),
                color = Cores.texto, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (insignificante) "CADASTRO" else "OUTORGA",
                color = if (insignificante) Cores.bomClaro else Cores.alertaClaro,
                fontSize = 10.5.sp, fontWeight = FontWeight.Bold
            )
        }
        r.vazaoOuVolume?.let {
            Spacer(Modifier.height(2.dp))
            Mono("%.2f %s".format(it, r.unidade), Cores.textoFraco, 10)
        }
        r.comBomba?.let {
            Spacer(Modifier.height(2.dp))
            Text(if (it) "Com bomba" else "Por gravidade", color = Cores.textoFraco, fontSize = 10.5.sp)
        }
        if (r.fotoArquivo != null) {
            Spacer(Modifier.height(2.dp))
            Text("com foto", color = Cores.bomClaro, fontSize = 10.5.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (confirmarExclusao) "confirmar exclusão?" else "excluir",
            color = Cores.alertaClaro, fontSize = 11.5.sp,
            modifier = Modifier.clickable {
                if (confirmarExclusao) { vm.excluirRegistroCaptacao(r); confirmarExclusao = false }
                else confirmarExclusao = true
            }
        )
    }
}

private fun rotuloTipoCaptacao(nome: String): String = when (nome) {
    TipoCaptacao.SUPERFICIAL.name -> "Superficial"
    TipoCaptacao.SUBTERRANEA_POCO_TUBULAR.name -> "Subterrânea — poço tubular"
    TipoCaptacao.SUBTERRANEA_OUTRA.name -> "Subterrânea — poço escavado/manual/nascente"
    else -> nome
}

@Composable
private fun OpcaoTipo(rotulo: String, selecionado: Boolean, aoEscolher: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selecionado) Cores.bom.copy(alpha = 0.18f) else Cores.superficie, RoundedCornerShape(6.dp))
            .clickable { aoEscolher() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            rotulo,
            color = if (selecionado) Cores.bomClaro else Cores.texto,
            fontSize = 13.sp, fontWeight = if (selecionado) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun CartaoResultado(r: ResultadoUsoInsignificante) {
    val insignificante = r.classificacao == ClassificacaoUso.INSIGNIFICANTE
    Column(
        Modifier.fillMaxWidth()
            .background(if (insignificante) Cores.bom else Cores.alerta, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Text(
            if (insignificante) "CADASTRO DE USO INSIGNIFICANTE" else "REQUER OUTORGA",
            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Limiar aplicado: ${r.limiar}",
            color = Color(0xE6FFFFFF), fontSize = 12.sp, lineHeight = 16.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Base legal: ${r.baseLegal}",
            color = Color(0xCCFFFFFF), fontSize = 11.sp, lineHeight = 15.sp
        )
        if (r.condicoesAdicionais.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Também precisa, cumulativamente:",
                color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold
            )
            r.condicoesAdicionais.forEach {
                Text("• $it", color = Color(0xE6FFFFFF), fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        Text(
            "Sugestão do app a partir do valor informado — confirme o enquadramento final com " +
                "o IGAM antes de protocolar.",
            color = Color(0xB3FFFFFF), fontSize = 10.sp, lineHeight = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// ---------------------------------------------------------------- escritorio

private fun abrirLinkNorma(contexto: Context, url: String) {
    runCatching { contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
private fun ColumnScope.AbaEscritorio() {
    val contexto = LocalContext.current
    LazyColumn(Modifier.weight(1f)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Rotulo("NORMAS")
                Text(
                    "Uso insignificante de recursos hídricos em Minas Gerais é definido pelo " +
                        "Conselho Estadual de Recursos Hídricos (CERH-MG). Abaixo do limiar: " +
                        "Cadastro de Uso Insignificante (hoje pelo módulo SOUT, com certidão " +
                        "online). Acima: Outorga de Direito de Uso de Recursos Hídricos.",
                    color = Cores.textoFraco, fontSize = 12.5.sp, lineHeight = 17.sp
                )
                Spacer(Modifier.height(12.dp))
                NormaLinha(
                    contexto, "DN CERH-MG 09/2004",
                    "Uso insignificante de águas SUPERFICIAIS (captação/derivação e acumulação).",
                    "http://www.siam.mg.gov.br/sla/download.pdf?idNorma=209"
                )
                NormaLinha(
                    contexto, "DN CERH 62/2019",
                    "Altera o art. 2º §1º da DN 09/2004 — amplia a acumulação superficial " +
                        "permitida nas UPGRHs do norte.",
                    "http://www.siam.mg.gov.br/sla/download.pdf?idNorma=49178"
                )
                NormaLinha(
                    contexto, "DN CERH-MG 76/2022",
                    "Revoga e recria do zero os critérios de uso insignificante e regularização " +
                        "de águas SUBTERRÂNEAS.",
                    "https://www.siam.mg.gov.br/sla/download.pdf?idNorma=56002"
                )
                NormaLinha(
                    contexto, "IGAM — Cadastro de Uso Insignificante",
                    "Página institucional consolidada, com os mesmos valores usados na aba Campo.",
                    "https://igam.mg.gov.br/cadastro-de-uso-insignificante-de-recurso-hidrico"
                )

                Rotulo("CAMADAS RELACIONADAS")
                Text(
                    "A câmera de perícia já consulta, automaticamente a cada foto, as camadas " +
                        "de restrição ligadas a recursos hídricos que o pacote traz — área de " +
                        "conflito por recursos hídricos e rios de preservação permanente, entre " +
                        "outras. O alerta aparece na hora, sem precisar abrir esta aba.",
                    color = Cores.textoFraco, fontSize = 12.5.sp, lineHeight = 17.sp
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun NormaLinha(contexto: Context, titulo: String, descricao: String, url: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .background(Cores.superficie, RoundedCornerShape(6.dp)).padding(12.dp)
    ) {
        Text(titulo, color = Cores.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(descricao, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            url, color = Cores.bomClaro, fontSize = 10.sp, textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { abrirLinkNorma(contexto, url) }
        )
    }
}
