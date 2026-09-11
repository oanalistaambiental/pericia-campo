package br.com.oanalistaambiental.pericia.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.captura.Orientacoes
import br.com.oanalistaambiental.pericia.captura.PosicaoMarcaDagua
import br.com.oanalistaambiental.pericia.dados.GlossarioSisema
import br.com.oanalistaambiental.pericia.exportacao.ResultadoRestauracao
import br.com.oanalistaambiental.pericia.geo.AlturaTrigonometrica
import br.com.oanalistaambiental.pericia.geo.FormatoCoordenada
import br.com.oanalistaambiental.pericia.geo.ImportadorCoordenada
import br.com.oanalistaambiental.pericia.geo.Medicao
import br.com.oanalistaambiental.pericia.geo.PontosLocais
import br.com.oanalistaambiental.pericia.geo.PrazoRenovacao
import br.com.oanalistaambiental.pericia.geo.Utm
import br.com.oanalistaambiental.pericia.taxas.TaxaUfemg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Menu de ferramentas — o que existe fora do ato de fotografar. */
/*
 * A antiga TelaFerramentas foi substituida pela gaveta (ferramentas/TelaGaveta.kt),
 * que se monta a partir do Registro em vez de listar as ferramentas a mao. Este arquivo
 * segue sendo a casa das TELAS de cada ferramenta.
 */

/**
 * Escala de fonte do modo sol forte, e a cor de maximo contraste que o acompanha.
 *
 * So os numeros grandes (o que se le de relance, de pe, ao sol) usam isto — texto de apoio ja
 * usa `Cores.textoFraco`, que foi clareado por este mesmo motivo (ver o comentario em
 * `Componentes.kt`).
 */
private fun escalaSolForte(ligado: Boolean) = if (ligado) 1.18f else 1f
private fun corSolForte(ligado: Boolean, normal: Color) = if (ligado) Color.White else normal

@Composable
fun TelaBussola(vm: CapturaViewModel, voltar: () -> Unit) {
    val p by vm.estadoCampo.posicao.collectAsState()
    val o by vm.estadoCampo.orientacao.collectAsState()
    val b by vm.estadoCampo.barometro.collectAsState()
    val solForte by vm.modoSolForte.collectAsState()
    val escala = escalaSolForte(solForte)

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Bússola e altímetro", voltar)
        SeloPrecisao(p)

        Column(
            Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BussolaCircular(o.azimuteGraus, tamanho = (220 * escala).dp)
            Spacer(Modifier.height(14.dp))
            Text(
                o.azimuteGraus?.let { "%.0f°".format(it) } ?: "—",
                color = corSolForte(solForte, Cores.texto), fontSize = (40 * escala).sp, fontWeight = FontWeight.Bold
            )
            Text(
                o.azimuteGraus?.let { "${Orientacoes.rosa(it)} · direção da câmera" }
                    ?: "aponte a câmera para o horizonte",
                color = Cores.textoFraco, fontSize = 13.sp
            )
            if (o.precisaoBussola.precisaCalibrar) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "calibrar: faça um 8 no ar",
                    color = Cores.atencaoClaro, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(26.dp))

            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                val lat = p.lat
                val lon = p.lon
                if (lat != null && lon != null) {
                    val utm = remember(lat, lon) { Utm.projetar(lat, lon) }
                    Campo("UTM SIRGAS 2000", utm.formatado())
                    Campo("Geográfica", "%.6f, %.6f".format(lat, lon))
                    Campo("Grau, minuto, segundo", Medicao.formatarGms(lat, lon))
                }
                Campo("Altitude GNSS", p.altitudeM?.let { "%.0f m".format(it) } ?: "—")
                Campo(
                    "Altitude barométrica",
                    b.altitudeBarometricaM?.let { "%.0f m".format(it) } ?: "sem barômetro"
                )
                b.pressaoHpa?.let { Campo("Pressão", "%.1f hPa".format(it)) }
                Campo("Elevação da câmera", o.elevacaoGraus?.let { "%.0f°".format(it) } ?: "—")
            }

            Ajuda(
                "Por que a bússola aponta pela câmera e não pelo topo do aparelho",
                "O sensor entrega a direção do topo do celular. Fotografando em pé, o topo " +
                    "aponta para o céu, e esse número gira sozinho — era o que ia para a legenda " +
                    "queimada na foto. Aqui o rumo sai do eixo da câmera, que é a direção que o " +
                    "laudo descreve. Quando a câmera aponta para muito perto do chão ou do céu, " +
                    "o app mostra um traço em vez de inventar um valor."
            )
            Ajuda(
                "Altitude barométrica não é cota",
                "O barômetro usa a atmosfera padrão, que muda com o tempo e com a pressão do dia. " +
                    "Serve para medir VARIAÇÃO entre dois pontos próximos, medidos com poucos " +
                    "minutos de diferença. Para cota absoluta, use a do GNSS e registre a precisão."
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Clinometro.
 *
 * Declividade entra em laudo ambiental o tempo todo: enquadramento de talude, area de
 * preservacao em encosta, projeto de terraplenagem, risco de deslizamento. Fazer isso "no
 * olho" e o padrao em campo, e o olho erra muito.
 */
@Composable
fun TelaClinometro(vm: CapturaViewModel, voltar: () -> Unit) {
    val o by vm.estadoCampo.orientacao.collectAsState()
    val graus = o.inclinacaoSuperficieGraus
    val percent = o.declividadePercent
    val solForte by vm.modoSolForte.collectAsState()
    val escala = escalaSolForte(solForte)

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Clinômetro", voltar)

        Column(
            Modifier.fillMaxWidth().padding(top = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                graus?.let { "%.1f°".format(it) } ?: "—",
                color = corSolForte(solForte, Cores.texto), fontSize = (64 * escala).sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    percent != null -> "%.1f%% de declividade".format(percent)
                    graus != null -> "praticamente vertical (acima de 80°)"
                    else -> "sem leitura"
                },
                color = Cores.bomClaro, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(28.dp))

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .background(Cores.superficie, RoundedCornerShape(6.dp)).padding(16.dp)
            ) {
                Text("COMO MEDIR", color = Cores.textoFraco, fontSize = Tipos.detalhe, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Encoste as COSTAS do celular na superfície que quer medir — o talude, a " +
                        "rampa, o barranco — e leia o número. Deitado no plano dá 0°; encostado " +
                        "numa parede dá 90°.\n\n" +
                        "A leitura não tem sinal, então não há como inverter por engano: ela é " +
                        "sempre o ângulo entre a superfície e o horizonte.",
                    color = Cores.texto, fontSize = 12.5.sp, lineHeight = 19.sp
                )
            }

            Spacer(Modifier.height(18.dp))

            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Campo("Equivalência", "45° = 100%   ·   26,6° = 50%   ·   14° = 25%")
                Campo("Elevação da câmera", o.elevacaoGraus?.let { "%.1f°".format(it) } ?: "—")
            }

            Ajuda(
                "Grau e porcentagem não são a mesma coisa",
                "Declividade em porcentagem é a razão entre subida e distância horizontal, vezes " +
                    "cem. Não é o ângulo. Uma rampa de 45° tem 100% de declividade, não 50%. " +
                    "Confundir os dois muda a conclusão de um laudo, e é um erro comum."
            )
            Ajuda(
                "Uma medida só não descreve uma encosta",
                "A declividade varia ao longo do talude. Para caracterizar, meça em pontos " +
                    "diferentes — pé, meio e topo — e registre cada um com foto. Uma leitura " +
                    "isolada descreve o ponto onde o celular encostou, e mais nada."
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Medicao de area por caminhamento.
 *
 * Marcar vertice a vertice, e nao gravar rastro continuo, e deliberado: o perito anda o
 * perimetro parando nos cantos, que e como se levanta area em campo. Rastro continuo enche o
 * poligono de ruido do GNSS e infla o perimetro sem melhorar a area.
 */
@Composable
fun TelaMedicao(vm: CapturaViewModel, voltar: () -> Unit) {
    val p by vm.estadoCampo.posicao.collectAsState()
    val vertices by vm.vertices.collectAsState()
    val pol by vm.poligono.collectAsState()
    var confirmarLimpeza by rememberSaveable { mutableStateOf(false) }
    val solForte by vm.modoSolForte.collectAsState()
    val escala = escalaSolForte(solForte)

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Medir área", voltar)
        SeloPrecisao(p)

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                pol?.areaFormatada() ?: "—",
                color = corSolForte(solForte, Cores.texto), fontSize = (44 * escala).sp, fontWeight = FontWeight.Bold
            )
            Text(
                pol?.incertezaFormatada() ?: "marque ao menos três vértices",
                color = if (pol?.confiavel() == true) Cores.bomClaro else Cores.atencaoClaro,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                pol?.let { "perímetro ${it.perimetroFormatado()} · ${it.vertices.size} vértices" }
                    ?: "",
                color = Cores.textoFraco, fontSize = 12.sp
            )
        }

        // Mapa ao vivo: o poligono se formando, vertice a vertice, em escala — nao so o
        // numero da area. O ultimo vertice marcado vem destacado, para se ver onde se esta.
        //
        // Ponto de referencia: a posicao ATUAL do GNSS entra no mesmo mapa, num anel de cor
        // diferente, sem participar da linha do poligono — o pedido de ver, ao vivo, o quanto
        // quem esta medindo se aproxima ou se afasta do que ja foi caminhado.
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            val latAtual = p.lat
            val lonAtual = p.lon
            val (pontosMapa, referenciaMapa) = remember(vertices, latAtual, lonAtual) {
                val baseLatLon = vertices.map { it.lat to it.lon }
                val temRef = latAtual != null && lonAtual != null
                val comRef = if (temRef) baseLatLon + (latAtual!! to lonAtual!!) else baseLatLon
                if (comRef.isEmpty()) {
                    emptyList<PontoMapa>() to null
                } else {
                    val locais = PontosLocais.relativos(comRef)
                    val locaisVertices = if (temRef) locais.dropLast(1) else locais
                    val mapaVertices = locaisVertices.mapIndexed { i, local ->
                        PontoMapa(local, Cores.bomClaro, destaque = i == locaisVertices.lastIndex)
                    }
                    val mapaRef = if (temRef) {
                        PontoMapa(locais.last(), Cores.atencaoClaro, rotulo = "você")
                    } else null
                    mapaVertices to mapaRef
                }
            }
            MapaEscala(
                pontosMapa, fecharPoligono = (pol?.vertices?.size ?: 0) >= 3,
                referencia = referenciaMapa,
                vazio = "O polígono aparece aqui conforme os vértices são marcados"
            )
        }

        if (pol != null && pol!!.vertices.size >= 3 && !pol!!.confiavel()) {
            AvisoRestricao(
                "A incerteza passa de 20% da área. Para laudo, espere o GNSS melhorar e refaça, " +
                    "ou trate este número como estimativa.",
                br.com.oanalistaambiental.pericia.geo.Situacao.PROXIMO_AO_LIMITE
            )
        }

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            BotaoLargo("Marcar vértice aqui", principal = true) {
                confirmarLimpeza = false; vm.marcarVertice()
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Box(Modifier.weight(1f)) {
                    BotaoLargo("Desfazer", habilitado = vertices.isNotEmpty()) {
                        confirmarLimpeza = false; vm.desfazerVertice()
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    // Antes: um toque, e os vertices sumiam. Botao do mesmo tamanho de
                    // "Desfazer", ao lado dele, operado com luva e o celular na mao. E
                    // "Desfazer" tira um vertice por vez — nao desfaz o "Limpar". Duas horas
                    // de caminhamento cabiam num toque errado. Agora pede confirmacao, e a
                    // confirmacao diz quantos vertices vao embora.
                    BotaoLargo(
                        if (confirmarLimpeza) "Confirmar?" else "Limpar",
                        habilitado = vertices.isNotEmpty()
                    ) {
                        if (confirmarLimpeza) { vm.limparMedicao(); confirmarLimpeza = false }
                        else confirmarLimpeza = true
                    }
                }
            }
            if (confirmarLimpeza) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Apagar os ${vertices.size} vértices marcados? Toque de novo em " +
                        "“Confirmar?” para apagar, ou em qualquer outro botão para desistir.",
                    color = Cores.alertaClaro, fontSize = 12.sp, lineHeight = 16.sp
                )
            }
            if ((pol?.vertices?.size ?: 0) >= 3) {
                Spacer(Modifier.height(8.dp))
                BotaoLargo("Usar como observação das fotos") { vm.usarMedicaoComoObservacao() }
            }
        }

        if (vertices.isNotEmpty()) {
            Rotulo("EXPORTAR ESTA MEDIÇÃO")
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { BotaoLargo("GPX") { vm.exportarMedicao("gpx") } }
                Box(Modifier.weight(1f)) { BotaoLargo("KML") { vm.exportarMedicao("kml") } }
                Box(Modifier.weight(1f)) { BotaoLargo("CSV") { vm.exportarMedicao("csv") } }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (vertices.isEmpty()) {
            Vazio(
                "Nenhum vértice marcado",
                "Caminhe até o primeiro canto da área, toque em “Marcar vértice aqui” e siga o " +
                    "perímetro parando em cada canto. O app fecha o polígono sozinho."
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                // itemsIndexed, nao items + indexOf: o indexOf percorria a lista inteira para
                // cada item desenhado (O(n^2) na rolagem) e, por comparar por igualdade,
                // numerava dois vertices identicos com o mesmo numero.
                itemsIndexed(vertices) { i, v ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${i + 1}", color = Cores.textoFraco, fontSize = 12.sp,
                            modifier = Modifier.width(26.dp)
                        )
                        Mono("%.6f, %.6f".format(java.util.Locale.US, v.lat, v.lon), Cores.texto, 11)
                        Spacer(Modifier.weight(1f))
                        Mono("±%.0f m".format(v.precisaoM), Cores.textoFraco, 10)
                    }
                    HorizontalDivider(color = Cores.linha)
                }
                item {
                    Ajuda(
                        "De onde vem o ± da área",
                        "Cada vértice pode estar deslocado até a precisão do GNSS. Isso vira uma " +
                            "faixa de incerteza ao longo de todo o contorno, e a área dessa faixa " +
                            "é o ± que aparece: perímetro vezes precisão média. Área pequena com " +
                            "GNSS ruim fica dominada pela incerteza — por isso 1 ha medido com " +
                            "±30 m não serve para laudo, e 100 ha com ±5 m serve."
                    )
                    Ajuda(
                        "Por que marcar canto a canto",
                        "Rastro contínuo parece mais preciso e é menos. Cada ponto carrega o erro " +
                            "do receptor, e centenas de pontos ao longo de uma reta transformam " +
                            "esse erro em zigue-zague: o perímetro incha e a área não melhora. " +
                            "Pare no canto, espere o selo ficar verde, marque."
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * Navegar ate uma coordenada que veio de fora — auto de infracao, planta, memorial.
 *
 * Duas fases na mesma tela. Antes de definir, um campo de texto e o formato reconhecido. Depois
 * de definir, a MESMA bussola redonda da ferramenta de bussola (com a marca do rumo-alvo) e um
 * mapinha em escala com os dois pontos — nao so a fita da bussola da tela de camera, que exigia
 * trocar de ferramenta so para ver a distancia encolhendo.
 */
@Composable
fun TelaIrParaCoordenada(vm: CapturaViewModel, voltar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    val lida = remember(texto) { Medicao.interpretar(texto) }
    val alvo by vm.alvo.collectAsState()
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    var erroImportacao by remember { mutableStateOf<String?>(null) }
    var importando by remember { mutableStateOf(false) }

    val escolherArquivo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        erroImportacao = null
        importando = true
        escopo.launch(Dispatchers.IO) {
            val resultado = runCatching {
                var nome = uri.lastPathSegment ?: "arquivo"
                contexto.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) nome = cursor.getString(idx) ?: nome
                }
                val conteudo = contexto.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("não consegui abrir o arquivo")
                ImportadorCoordenada.extrairPrimeiroPonto(nome, conteudo)
            }
            withContext(Dispatchers.Main) {
                importando = false
                resultado.onSuccess { ponto ->
                    if (ponto != null) vm.definirAlvoCoordenada(ponto.lat, ponto.lon, ponto.rotulo)
                    else erroImportacao = "Não achei uma coordenada legível nesse arquivo — " +
                        "aceita KML, GPX e GeoJSON. Shapefile (.shp) não é suportado: é um " +
                        "formato binário de vários arquivos, sem leitor pronto aqui."
                }.onFailure { erroImportacao = "Falha ao ler o arquivo: ${it.message}" }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Ir para uma coordenada", voltar)

        val a = alvo
        if (a == null) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    label = { Text("Cole ou digite a coordenada") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                BotaoLargo(
                    if (importando) "Lendo arquivo…" else "Importar de um arquivo (KML, GPX, GeoJSON)",
                    habilitado = !importando
                ) { escolherArquivo.launch(arrayOf("*/*")) }
                if (erroImportacao != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(erroImportacao!!, color = Cores.alertaClaro, fontSize = 11.5.sp, lineHeight = 16.sp)
                }
                Spacer(Modifier.height(12.dp))

                when {
                    texto.isBlank() -> Text(
                        "Aceita os três formatos que aparecem de verdade:\n\n" +
                            "  -19.9167, -43.9345\n" +
                            "  19°55'00\"S 43°56'04\"W\n" +
                            "  23S 611520E 7797383N",
                        color = Cores.textoFraco, fontSize = 12.sp, lineHeight = 20.sp
                    )
                    lida == null -> Text(
                        "Não reconheci esse formato. Confira se os dois valores estão presentes e " +
                            "se o separador é vírgula ou espaço.",
                        color = Cores.atencaoClaro, fontSize = 12.sp, lineHeight = 18.sp
                    )
                    else -> {
                        Column(
                            Modifier.fillMaxWidth()
                                .background(Cores.superficie, RoundedCornerShape(6.dp)).padding(14.dp)
                        ) {
                            Text("LIDO COMO ${lida.formato.uppercase()}", color = Cores.textoFraco,
                                fontSize = 10.sp, letterSpacing = 1.sp)
                            Spacer(Modifier.height(8.dp))
                            Mono("%.6f, %.6f".format(lida.lat, lida.lon), Cores.texto, 13)
                            Spacer(Modifier.height(4.dp))
                            Mono(Utm.projetar(lida.lat, lida.lon).formatado(), Cores.textoFraco, 11)
                            Spacer(Modifier.height(4.dp))
                            Mono(Medicao.formatarGms(lida.lat, lida.lon), Cores.textoFraco, 11)
                        }
                        lida.aviso?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, color = Cores.atencaoClaro, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        BotaoLargo("Guiar até este ponto", principal = true) {
                            vm.definirAlvoCoordenada(lida.lat, lida.lon, "Coordenada informada")
                        }
                    }
                }
            }

            Ajuda(
                "O que o guia faz e o que não faz",
                "Ele mostra distância em linha reta e o rumo, na bússola desta tela e na fita da " +
                    "tela de câmera. Não traça rota nem desvia de obstáculo: a leitura é de " +
                    "bússola, como se faz com uma carta na mão. Quando você chega dentro da " +
                    "precisão do GNSS, o app avisa."
            )
        } else {
            GuiaAteCoordenada(vm, a, aoTrocar = { vm.limparAlvo(); texto = "" })
        }
    }
}

/** Fase de guia, depois que uma coordenada foi definida: bússola, distância e mapa em escala. */
@Composable
private fun GuiaAteCoordenada(vm: CapturaViewModel, alvo: CapturaViewModel.Alvo, aoTrocar: () -> Unit) {
    val p by vm.estadoCampo.posicao.collectAsState()
    val o by vm.estadoCampo.orientacao.collectAsState()
    val guia by vm.guia.collectAsState()
    val solForte by vm.modoSolForte.collectAsState()
    val escala = escalaSolForte(solForte)

    SeloPrecisao(p)

    Column(
        Modifier.fillMaxWidth().padding(top = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BussolaCircular(o.azimuteGraus, alvoGraus = guia?.rumoGraus, tamanho = (220 * escala).dp)
        Spacer(Modifier.height(10.dp))
        Text(
            guia?.let { "%.0f m".format(it.distanciaM) } ?: "—",
            color = if (guia?.chegou == true) Cores.bomClaro else corSolForte(solForte, Cores.texto),
            fontSize = (36 * escala).sp, fontWeight = FontWeight.Bold
        )
        Text(
            when {
                guia == null -> "aguardando GNSS"
                guia!!.chegou -> "você chegou — dentro da precisão do GNSS"
                else -> "rumo %.0f° até ${alvo.rotulo}".format(guia!!.rumoGraus)
            },
            color = Cores.textoFraco, fontSize = 12.5.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(18.dp))

        val lat = p.lat
        val lon = p.lon
        val pontosMapa = if (lat != null && lon != null) {
            val locais = PontosLocais.relativos(listOf(lat to lon, alvo.lat to alvo.lon))
            listOf(
                PontoMapa(locais[0], Cores.bomClaro, rotulo = "você", destaque = true),
                PontoMapa(locais[1], Cores.alertaClaro, rotulo = alvo.rotulo)
            )
        } else emptyList()

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            MapaEscala(
                pontosMapa, tracejado = true,
                vazio = "Aguardando posição para desenhar o mapa"
            )
        }

        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            BotaoLargo("Salvar este ponto") { vm.salvarPonto(alvo.rotulo, alvo.lat, alvo.lon, null) }
            Spacer(Modifier.height(8.dp))
            BotaoLargo("Definir outra coordenada", aoClicar = aoTrocar)
        }

        Ajuda(
            "O que o guia faz e o que não faz",
            "Ele mostra distância em linha reta e o rumo, na bússola e no mapinha acima. Não " +
                "traça rota nem desvia de obstáculo: a leitura é de bússola, como se faz com uma " +
                "carta na mão. Quando você chega dentro da precisão do GNSS, o app avisa.",
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Campo(rotulo: String, valor: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(rotulo, color = Cores.textoFraco, fontSize = 12.5.sp)
        Spacer(Modifier.weight(1f))
        Mono(valor, Cores.texto, 12)
    }
    HorizontalDivider(color = Cores.linha)
}

private val formatoDataBr = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun interpretarData(texto: String): java.time.LocalDate? =
    runCatching { java.time.LocalDate.parse(texto.trim(), formatoDataBr) }.getOrNull()

/**
 * Prazo de protocolo da renovacao — art. 12 da DN COPAM 217/2017.
 *
 * So a conta da data, deliberadamente separada de qualquer enquadramento em curso: o vencimento
 * de uma licenca e informacao do PROCESSO, nao da simulacao, e o perito pode querer conferir o
 * prazo de uma licenca cujo enquadramento nem foi feito neste aparelho.
 */
@Composable
fun TelaPrazoRenovacao(voltar: () -> Unit) {
    var texto by rememberSaveable { mutableStateOf("") }
    val validade = remember(texto) { interpretarData(texto) }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Prazo de renovação", voltar)

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                label = { Text("Vencimento da licença (dd/mm/aaaa)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            when {
                texto.isBlank() -> Text(
                    "Informe a data de vencimento da licença para calcular até quando dá para " +
                        "protocolar a renovação.",
                    color = Cores.textoFraco, fontSize = 12.5.sp, lineHeight = 18.sp
                )
                validade == null -> Text(
                    "Não reconheci essa data. Use o formato dd/mm/aaaa, por exemplo 15/03/2027.",
                    color = Cores.atencaoClaro, fontSize = 12.5.sp, lineHeight = 18.sp
                )
                else -> {
                    val r = remember(validade) { PrazoRenovacao.calcular(validade) }
                    CartaoResultado(
                        rotulo = "Protocolar até",
                        valor = r.dataLimiteProtocolo.format(formatoDataBr),
                        linhaExtra = when {
                            r.prazoVencido ->
                                "O prazo de 120 dias já passou há ${-r.diasRestantes} dia(s)."
                            r.proximoDoLimite ->
                                "Faltam ${r.diasRestantes} dia(s) — dentro da janela de atenção."
                            else -> "Faltam ${r.diasRestantes} dia(s)."
                        },
                        corLinhaExtra = when {
                            r.prazoVencido -> Cores.alertaClaro
                            r.proximoDoLimite -> Cores.atencaoClaro
                            else -> Cores.bomClaro
                        }
                    )
                }
            }
        }

        Ajuda(
            "De onde vem o prazo de 120 dias",
            "Art. 12 da DN COPAM 217/2017 exige que o pedido de renovação seja protocolado com " +
                "antecedência mínima de 120 dias do término da validade da licença. Esta " +
                "ferramenta faz só a conta da data — confirme o prazo exato e eventuais " +
                "condicionantes específicas com a Unidade Regional."
        )
    }
}

/** Configuracoes e estado do pacote de camadas. */
@Composable
fun TelaConfiguracoes(vm: CapturaViewModel, voltar: () -> Unit) {
    val camadas by vm.camadas.collectAsState()
    val versao by vm.versaoPacote.collectAsState()

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Configurações", voltar)
        val solForte by vm.modoSolForte.collectAsState()
        val economia by vm.economiaDeBateria.collectAsState()

        LazyColumn(Modifier.weight(1f)) {
            item {
                Rotulo("TELA E BATERIA")
                LinhaAlternavel(
                    titulo = "Modo sol forte",
                    descricao = "Aumenta o tamanho e o contraste dos números grandes, para " +
                        "ler ao sol do meio-dia.",
                    ligado = solForte,
                    aoAlternar = { vm.alternarModoSolForte() }
                )
                LinhaAlternavel(
                    titulo = "Economia de bateria",
                    descricao = "GNSS e bússola atualizam mais devagar. Rende mais numa " +
                        "vistoria longa em área rural; a leitura reage com menos frequência.",
                    ligado = economia,
                    aoAlternar = { vm.alternarEconomiaDeBateria() }
                )

                Rotulo("COORDENADAS")
                Linha("Datum", "SIRGAS 2000", Cores.bomClaro)
                FormatoCoordenadaConfig(vm)

                Rotulo("ALERTA LOCACIONAL")
                Linha("Folga de aviso", "50 m", Cores.texto)
                Text(
                    "Distância além da precisão do GNSS em que o app ainda avisa. Quando a margem " +
                        "de erro cruza o limite da área, ele informa a distância em vez de afirmar " +
                        "que você está dentro.",
                    color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Rotulo("PACOTE DE CAMADAS")
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    BotaoLargo("Recarregar pacote") {
                        vm.abrirPacote()
                        vm.avisar("Recarregando o pacote de camadas…")
                    }
                    Text(
                        "Use depois de copiar um pacote novo para o aparelho — sem isto, o app " +
                            "só lê o arquivo de novo ao reabrir.",
                        color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (camadas.isEmpty()) {
                    Column(
                        Modifier.padding(16.dp).fillMaxWidth()
                            .background(Cores.superficie, RoundedCornerShape(4.dp)).padding(14.dp)
                    ) {
                        Text("Pacote ilegível", color = Cores.atencaoClaro, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "O app tenta abrir o pacote instalado e, na falta dele, o exemplo " +
                                "empacotado — nenhum dos dois abriu. O app funciona normalmente " +
                                "assim mesmo: câmera, GNSS, legenda, hash, sessões, medição e " +
                                "laudo. Só o alerta de restrição fica desligado.\n\n" +
                                "Para instalar o pacote oficial, gere o arquivo com " +
                                "ferramentas/montar-pacote.sh e copie para:",
                            color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Mono("Android/data/br.com.oanalistaambiental.pericia/\n  files/pacotes/mg-base.gpkg", Cores.texto, 10)
                    }
                } else {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        if (versao == "exemplo") {
                            Column(
                                Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                    .background(Cores.atencao, RoundedCornerShape(6.dp)).padding(12.dp)
                            ) {
                                Text(
                                    "EXEMPLO EMPACOTADO — NÃO É DADO DO IDE-SISEMA",
                                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Nenhum pacote oficial foi instalado, então o app carregou este " +
                                        "exemplo fictício só para mostrar como o alerta de restrição " +
                                        "funciona. Gere o pacote real com ferramentas/montar-pacote.sh " +
                                        "antes de usar em campo.",
                                    color = Color(0xE6FFFFFF), fontSize = 11.5.sp, lineHeight = 16.sp
                                )
                            }
                        }
                        Text(
                            "Versão ${versao ?: "—"} · ${camadas.size} camadas",
                            color = Cores.bomClaro, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        camadas.forEach { c ->
                            Column(Modifier.padding(bottom = 12.dp)) {
                                Text(c.nome, color = Cores.texto, fontSize = 12.5.sp)
                                Mono(
                                    "${c.fonte} · extraído ${c.dataExtracao} · simplificação ${c.toleranciaM} m" +
                                        (c.raioM?.let { " · raio ${it.toInt()} m" } ?: ""),
                                    Cores.textoFraco, 10
                                )
                            }
                        }
                    }
                }

                Rotulo("MARCA D'ÁGUA")
                MarcaDaguaConfig(vm)

                Rotulo("BACKUP")
                BackupConfig(vm)

                Rotulo("SOBRE")
                Text(
                    "Ferramenta independente. Não é afiliada ao SISEMA/SEMAD/FEAM nem os substitui. " +
                        "Consome apenas dados públicos, publicados em serviços abertos.\n\n" +
                        "As indicações de restrição são indícios, sujeitos à precisão do receptor GNSS " +
                        "e à data de extração das camadas. Não substituem a análise técnica do perito.",
                    color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 16.sp,
                    modifier = Modifier.padding(16.dp)
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Em qual formato a coordenada aparece primeiro — na legenda queimada na foto e nas telas do
 * app que mostram posição. A UTM continua sempre presente como segunda linha na legenda,
 * porque é a projeção que o processo administrativo espera por padrão.
 */
@Composable
private fun FormatoCoordenadaConfig(vm: CapturaViewModel) {
    val atual by vm.formatoCoordenada.collectAsState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FormatoCoordenada.entries.forEach { formato ->
                Box(Modifier.weight(1f)) {
                    SeletorPosicao(formato.rotulo, atual == formato) {
                        vm.definirFormatoCoordenada(formato)
                    }
                }
            }
        }
    }
}

/**
 * Marca d'água (brasão, logo) queimada no canto da cópia com legenda de toda foto tirada daqui
 * em diante — nunca no original. Um arquivo fixo em armazenamento interno, lido de novo a cada
 * foto: trocar ou remover aqui vale para as PRÓXIMAS fotos, não reprocessa as antigas.
 */
@Composable
private fun MarcaDaguaConfig(vm: CapturaViewModel) {
    val contexto = LocalContext.current
    val temMarca by vm.temMarcaDagua.collectAsState()
    // A versao sobe SO depois que a escrita do arquivo termina (dentro do ViewModel) — por
    // isso a previa usa ela como chave, e nao um contador incrementado aqui na hora do clique:
    // reler o disco antes da escrita acabar mostraria a imagem antiga, ou nenhuma.
    val versao by vm.versaoMarcaDagua.collectAsState()
    var confirmarRemocao by remember { mutableStateOf(false) }
    val preview = remember(temMarca, versao) {
        if (!temMarca) null else runCatching {
            val arquivo = File(contexto.filesDir, "marca_dagua.png")
            BitmapFactory.decodeFile(arquivo.absolutePath)?.asImageBitmap()
        }.getOrNull()
    }

    val escolher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) vm.definirMarcaDagua(uri) }

    val posicaoAtual by vm.posicaoMarcaDagua.collectAsState()

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (temMarca && preview != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    preview, contentDescription = "Marca d'água atual",
                    modifier = Modifier.size(56.dp)
                        .background(Cores.superficie, RoundedCornerShape(6.dp)).padding(4.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Entra em todas as fotos a partir de agora, na posição escolhida abaixo.",
                    color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("POSIÇÃO NA FOTO", color = Cores.textoFraco, fontSize = 10.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1f)) {
                        SeletorPosicao(
                            "Sup. esquerda", posicaoAtual == PosicaoMarcaDagua.SUPERIOR_ESQUERDA
                        ) { vm.definirPosicaoMarcaDagua(PosicaoMarcaDagua.SUPERIOR_ESQUERDA) }
                    }
                    Box(Modifier.weight(1f)) {
                        SeletorPosicao(
                            "Sup. direita", posicaoAtual == PosicaoMarcaDagua.SUPERIOR_DIREITA
                        ) { vm.definirPosicaoMarcaDagua(PosicaoMarcaDagua.SUPERIOR_DIREITA) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1f)) {
                        SeletorPosicao(
                            "Inf. esquerda", posicaoAtual == PosicaoMarcaDagua.INFERIOR_ESQUERDA
                        ) { vm.definirPosicaoMarcaDagua(PosicaoMarcaDagua.INFERIOR_ESQUERDA) }
                    }
                    Box(Modifier.weight(1f)) {
                        SeletorPosicao(
                            "Inf. direita", posicaoAtual == PosicaoMarcaDagua.INFERIOR_DIREITA
                        ) { vm.definirPosicaoMarcaDagua(PosicaoMarcaDagua.INFERIOR_DIREITA) }
                    }
                }
            }
            Text(
                "Nas posições inferiores, a marca entra ACIMA da faixa de coordenada/data — " +
                    "as duas nunca se sobrepõem.",
                color = Cores.textoFraco, fontSize = Tipos.detalhe, lineHeight = 15.sp,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(14.dp))
            val opacidade by vm.opacidadeMarcaDagua.collectAsState()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("OPACIDADE", color = Cores.textoFraco, fontSize = 10.sp, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Mono("${(opacidade * 100).toInt()}%", Cores.texto, 11)
            }
            Slider(
                value = opacidade,
                onValueChange = { vm.definirOpacidadeMarcaDagua(it) },
                valueRange = 0.1f..1f,
                colors = SliderDefaults.colors(thumbColor = Cores.bom, activeTrackColor = Cores.bom)
            )
            Text(
                "10% quase invisível, 100% sólida — vale para a prévia na câmera e para a foto final.",
                color = Cores.textoFraco, fontSize = Tipos.detalhe, lineHeight = 15.sp
            )

            Spacer(Modifier.height(10.dp))
            Row {
                Box(Modifier.weight(1f)) {
                    BotaoLargo("Trocar imagem") { escolher.launch("image/*") }
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    BotaoLargo(if (confirmarRemocao) "Confirmar?" else "Remover") {
                        if (confirmarRemocao) { vm.removerMarcaDagua(); confirmarRemocao = false }
                        else confirmarRemocao = true
                    }
                }
            }
        } else {
            Text(
                "Nenhuma marca definida. Aceita JPG, PNG ou WEBP — PNG com fundo transparente " +
                    "fica melhor sobre a foto.",
                color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp
            )
            Spacer(Modifier.height(10.dp))
            BotaoLargo("Escolher imagem (brasão, logo)") { escolher.launch("image/*") }
        }
    }
}

/**
 * Backup completo — banco de dados + tudo que foi produzido em campo (fotos, áudio, pontos,
 * medições, caminhamentos, ocorrências, registros de captação) num único .zip, salvo onde a
 * pessoa escolher. Não sobe para lugar nenhum sozinho: quem decide para onde vai é quem exporta.
 */
@Composable
private fun BackupConfig(vm: CapturaViewModel) {
    val criarArquivo = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) vm.criarBackup(uri) }

    var uriParaRestaurar by remember { mutableStateOf<Uri?>(null) }
    var restaurando by remember { mutableStateOf(false) }
    var resultadoRestauracao by remember { mutableStateOf<ResultadoRestauracao?>(null) }

    val escolherArquivo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) uriParaRestaurar = uri }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            "Gera um arquivo .zip com o banco de dados e todos os arquivos de campo — se o " +
                "aparelho quebrar ou for perdido, é o que garante não perder o que já foi " +
                "registrado. O pacote de camadas fica de fora (é dado público, não prova " +
                "produzida por você — refazer é só \"Recarregar pacote\" acima).",
            color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))
        BotaoLargo("Criar backup (.zip)") {
            val agora = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
            criarArquivo.launch("pericia-campo-backup-$agora.zip")
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Restaurar substitui TUDO que está no aparelho agora pelo conteúdo do backup " +
                "escolhido — sem como desfazer, além de reinstalar de outro backup depois.",
            color = Cores.atencaoClaro, fontSize = 11.5.sp, lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))
        BotaoLargo("Restaurar backup (.zip)", habilitado = !restaurando) {
            escolherArquivo.launch(arrayOf("application/zip", "*/*"))
        }
        if (restaurando) {
            Spacer(Modifier.height(8.dp))
            Text("Restaurando…", color = Cores.textoFraco, fontSize = 11.5.sp)
        }
    }

    uriParaRestaurar?.let { uri ->
        AlertDialog(
            onDismissRequest = { uriParaRestaurar = null },
            title = { Text("Restaurar este backup?") },
            text = {
                Text(
                    "Isso substitui TODOS os dados atuais do app — sessões, fotos, condicionantes, " +
                        "fichas, tudo — pelo conteúdo deste arquivo. Uma cópia de segurança do " +
                        "banco atual fica guardada, mas o caminho de volta é manual. Depois de " +
                        "restaurar, feche e abra o app de novo para ver os dados restaurados."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val alvo = uri
                    uriParaRestaurar = null
                    restaurando = true
                    vm.restaurarBackup(alvo) { resultado ->
                        restaurando = false
                        resultadoRestauracao = resultado
                    }
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { uriParaRestaurar = null }) { Text("Cancelar") } }
        )
    }

    resultadoRestauracao?.let { resultado ->
        AlertDialog(
            onDismissRequest = { resultadoRestauracao = null },
            title = {
                Text(if (resultado is ResultadoRestauracao.Sucesso) "Backup restaurado" else "Não restaurou")
            },
            text = {
                Text(
                    when (resultado) {
                        is ResultadoRestauracao.Sucesso ->
                            "Banco de dados e ${resultado.pastasRestauradas} pasta(s) de arquivos " +
                                "substituídos. Feche e abra o app agora para ver os dados restaurados."
                        is ResultadoRestauracao.Falha ->
                            "Nada foi alterado. ${resultado.motivo}"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { resultadoRestauracao = null }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun SeletorPosicao(rotulo: String, selecionado: Boolean, aoEscolher: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(if (selecionado) Cores.bom else Cores.superficie, RoundedCornerShape(6.dp))
            .clickable { aoEscolher() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            rotulo, color = if (selecionado) Color.White else Cores.textoFraco,
            fontSize = 12.sp, fontWeight = if (selecionado) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun Linha(rotulo: String, valor: String, cor: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(rotulo, color = Cores.texto, fontSize = 13.5.sp)
        Spacer(Modifier.weight(1f))
        Mono(valor, cor, 12)
    }
    HorizontalDivider(color = Cores.linha)
}

/**
 * Em que Circunscricao Hidrografica (CH) o ponto atual cai — dado real do IGAM.
 *
 * Consulta automaticamente a cada posicao nova, do mesmo jeito que o alerta de restricao ja
 * faz — so que sem virar aviso: estar numa CH nao e restricao, e contexto para uma conversa de
 * outorga.
 */
@Composable
fun TelaBaciaHidrografica(vm: CapturaViewModel, voltar: () -> Unit) {
    val p by vm.estadoCampo.posicao.collectAsState()
    val bacia by vm.bacia.collectAsState()
    val consultando by vm.consultandoBacia.collectAsState()

    LaunchedEffect(p.lat, p.lon) {
        val lat = p.lat
        val lon = p.lon
        if (lat != null && lon != null) vm.consultarBaciaHidrografica(lat, lon)
    }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Bacia hidrográfica", voltar)
        SeloPrecisao(p)

        Column(
            Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                p.lat == null -> Vazio(
                    "Sem coordenada",
                    "Aguarde o GNSS fixar para consultar a Circunscrição Hidrográfica do ponto."
                )
                consultando && bacia == null -> Text(
                    "Consultando…", color = Cores.textoFraco, fontSize = 13.sp
                )
                bacia == null -> Vazio(
                    "Fora de qualquer CH mapeada",
                    "O ponto atual não caiu em nenhuma Circunscrição Hidrográfica de Minas " +
                        "Gerais — comum perto da divisa com outro estado."
                )
                else -> {
                    val b = bacia!!
                    Text(b.sigla, color = Cores.texto, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Text(
                        b.nome.substringAfter(": ").ifBlank { b.nome },
                        color = Cores.bomClaro, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Campo(
                            "Comitê de bacia (CBH)",
                            if (b.temComiteDeBacia) (b.situacaoComite ?: "sim") else "não instalado"
                        )
                        b.areaKm2?.let { Campo("Área da CH", "%.0f km²".format(it)) }
                        b.decreto?.let { Campo("Decreto de criação", it) }
                    }
                }
            }

            Ajuda(
                "O que é uma Circunscrição Hidrográfica",
                "É a unidade de planejamento e gestão de recursos hídricos que o IGAM usa em " +
                    "Minas Gerais — a referência para saber a quem procurar antes de uma " +
                    "conversa de outorga. Não é indício de restrição nenhuma, só contexto: " +
                    "estar numa CH não impede nada, é o recorte administrativo do lugar.\n\n" +
                    "Dado real do IGAM/SEMAD (base GEIRH), simplificado para caber no " +
                    "aplicativo — a fronteira exata pode variar alguns metros do original."
            )
        }
    }
}

/**
 * Altura por trigonometria, pelo método dos DOIS ângulos, com a câmera visível para mirar.
 *
 * Antes a conta assumia a base do objeto no mesmo nível dos pés de quem mede, e somava uma
 * altura de observador fixa (1,5 m) — erra pelo desnível inteiro numa encosta, vala ou talude.
 * Agora se mira a BASE de verdade (zera ali) e depois o TOPO; a altura sai da diferença das
 * duas tangentes (`AlturaTrigonometrica.calcularDuploAngulo`), sem precisar estimar nada.
 */
@Composable
fun TelaAlturaTrigonometrica(vm: CapturaViewModel, voltar: () -> Unit) {
    val o by vm.estadoCampo.orientacao.collectAsState()
    var distanciaTexto by rememberSaveable { mutableStateOf("") }
    val distancia = distanciaTexto.replace(',', '.').toDoubleOrNull()
    val anguloAtual = o.elevacaoGraus

    var anguloBase by rememberSaveable { mutableStateOf<Float?>(null) }
    var anguloTopo by rememberSaveable { mutableStateOf<Float?>(null) }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Altura por trigonometria", voltar)

        // O visor: so para mirar. Nunca vira arquivo, nunca entra em sessao — por isso um
        // painel de altura fixa, como o mapinha da medicao de area, nao a tela cheia da camera
        // de pericia.
        Box(Modifier.fillMaxWidth().height(240.dp)) {
            VisorCamera(Modifier.fillMaxSize())

            // Mira central.
            Box(Modifier.align(Alignment.Center).width(2.dp).height(26.dp).background(Color.White.copy(alpha = 0.85f)))
            Box(Modifier.align(Alignment.Center).width(26.dp).height(2.dp).background(Color.White.copy(alpha = 0.85f)))

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Cores.veuEscuro)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    if (anguloBase == null) "ÂNGULO ATUAL — mire a base" else "ÂNGULO ATUAL — mire o topo",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        anguloAtual?.let { "%.1f°".format(it) } ?: "—",
                        color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold
                    )
                    if (anguloBase != null && anguloAtual != null) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "desde a base: %.1f°".format(anguloAtual - anguloBase!!),
                            color = Cores.bomClaro, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            OutlinedTextField(
                value = distanciaTexto,
                onValueChange = { distanciaTexto = it },
                label = { Text("Distância horizontal até a base (m)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    anguloBase == null -> {
                        Text(
                            "Aponte a mira para a BASE do que vai medir e toque para zerar ali.",
                            color = Cores.textoFraco, fontSize = 12.5.sp, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(14.dp))
                        BotaoLargo(
                            "Marcar base (zerar)", principal = true, habilitado = anguloAtual != null
                        ) { anguloBase = anguloAtual }
                    }

                    anguloTopo == null -> {
                        Text(
                            "Agora suba a mira até o TOPO do que está medindo e toque para marcar.",
                            color = Cores.textoFraco, fontSize = 12.5.sp, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(14.dp))
                        BotaoLargo(
                            "Marcar topo", principal = true,
                            habilitado = anguloAtual != null && distancia != null && distancia > 0
                        ) { anguloTopo = anguloAtual }
                        Spacer(Modifier.height(8.dp))
                        BotaoLargo("Mirar a base de novo") { anguloBase = null }
                        if (distancia == null || distancia <= 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Falta informar a distância horizontal até a base.",
                                color = Cores.atencaoClaro, fontSize = 11.5.sp, textAlign = TextAlign.Center
                            )
                        }
                    }

                    else -> {
                        val d = distancia
                        if (d != null && d > 0) {
                            val r = remember(d, anguloBase, anguloTopo) {
                                AlturaTrigonometrica.calcularDuploAngulo(
                                    d, anguloBase!!.toDouble(), anguloTopo!!.toDouble()
                                )
                            }
                            CartaoResultado(
                                rotulo = "Altura estimada",
                                valor = "%.1f m".format(r.alturaM),
                                linhaExtra = "± %.1f m de incerteza".format(r.incertezaM),
                                corLinhaExtra = Cores.atencaoClaro
                            )
                            Spacer(Modifier.height(6.dp))
                            Mono(
                                "base %.1f° · topo %.1f° · %.1f m".format(
                                    java.util.Locale.US, anguloBase, anguloTopo, d
                                ),
                                Cores.textoFraco, 11
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        BotaoLargo("Medir de novo") { anguloBase = null; anguloTopo = null }
                    }
                }
            }

            Ajuda(
                "Como medir",
                "Fique a uma distância horizontal conhecida da base do que quer medir — passos " +
                    "contados, ou a medição de área do app até lá — e informe essa distância " +
                    "acima.\n\n" +
                    "Mire a câmera para a BASE do objeto e toque em \"Marcar base\": esse é o " +
                    "zero. Suba a mira até o TOPO e toque em \"Marcar topo\". A altura sai da " +
                    "diferença entre os dois ângulos, não da soma de uma altura de observador " +
                    "estimada — por isso funciona igual numa encosta, numa vala ou olhando de " +
                    "cima de um talude, onde a base não está no mesmo nível dos seus pés.\n\n" +
                    "A incerteza cresce com a distância e perto de 90°: um grau de erro no " +
                    "ângulo é pouco a 5 m e muito a 50 m. Para medida de precisão, use um " +
                    "clinômetro dedicado."
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Marcar e guardar a posição ATUAL do GNSS, avulsa — não presa a foto nem a caminhamento de
 * medição. Existe porque hoje só se salva coordenada junto de uma foto ou dentro do polígono da
 * medição de área; às vezes o que se quer é só marcar um ponto e seguir.
 */
@Composable
fun TelaPontosSalvos(vm: CapturaViewModel, voltar: () -> Unit) {
    val p by vm.estadoCampo.posicao.collectAsState()
    val pontos by vm.pontosSalvos.collectAsState()
    var nome by rememberSaveable { mutableStateOf("") }
    var confirmarExclusao by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Pontos salvos", voltar)
        SeloPrecisao(p)

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            OutlinedTextField(
                value = nome,
                onValueChange = { nome = it },
                label = { Text("Nome do ponto (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            BotaoLargo("Salvar este ponto", principal = true, habilitado = p.temPosicao) {
                vm.salvarPonto(nome, p.lat!!, p.lon!!, p.precisaoM)
                nome = ""
            }
            if (!p.temPosicao) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Aguarde o GNSS fixar para salvar.",
                    color = Cores.atencaoClaro, fontSize = 11.5.sp
                )
            }
        }

        if (pontos.isNotEmpty()) {
            Rotulo("EXPORTAR ${pontos.size} PONTO(S)")
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { BotaoLargo("GPX") { vm.exportarPontos("gpx") } }
                Box(Modifier.weight(1f)) { BotaoLargo("KML") { vm.exportarPontos("kml") } }
                Box(Modifier.weight(1f)) { BotaoLargo("CSV") { vm.exportarPontos("csv") } }
            }
        }

        if (pontos.isEmpty()) {
            Vazio(
                "Nenhum ponto salvo",
                "Toque em \"Salvar este ponto\" quando estiver no local que quer registrar."
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(pontos) { pt ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(pt.nome, color = Cores.texto, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            Mono("%.6f, %.6f".format(java.util.Locale.US, pt.lat, pt.lon), Cores.textoFraco, 10)
                        }
                        AcaoTexto(
                            if (confirmarExclusao == pt.id) "confirmar?" else "excluir",
                            cor = Cores.alertaClaro,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            if (confirmarExclusao == pt.id) { vm.excluirPonto(pt.id); confirmarExclusao = null }
                            else confirmarExclusao = pt.id
                        }
                    }
                    HorizontalDivider(color = Cores.linha)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/**
 * Calculadora de taxa em UFEMG — taxa de expediente (análise de intervenção ambiental/DAIA) e
 * taxa florestal. Dado real, extraído da planilha oficial de estimativa de custo (ver
 * `taxas/TaxaUfemg.kt`), com a resolução que fixa o valor da UFEMG citada na tela.
 */
@Composable
fun TelaTaxaUfemg(vm: CapturaViewModel, voltar: () -> Unit) {
    val tabela by vm.tabelaTaxas.collectAsState()
    var abaFlorestal by rememberSaveable { mutableStateOf(false) }
    var itemSelecionadoCodigo by rememberSaveable { mutableStateOf<String?>(null) }
    var quantidadeTexto by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Taxa em UFEMG", voltar)

        val t = tabela
        if (t == null) {
            Vazio("Carregando tabela…", "")
            return@Column
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Box(Modifier.weight(1f)) {
                BotaoLargo("Intervenção (DAIA)", principal = !abaFlorestal) {
                    abaFlorestal = false; itemSelecionadoCodigo = null
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                BotaoLargo("Florestal", principal = abaFlorestal) {
                    abaFlorestal = true; itemSelecionadoCodigo = null
                }
            }
        }

        val itens = if (abaFlorestal) t.taxaFlorestal else t.taxaExpedienteIntervencao
        val selecionado = itens.firstOrNull { it.codigo == itemSelecionadoCodigo }

        if (selecionado == null) {
            Rotulo("ESCOLHA O ITEM")
            LazyColumn(Modifier.weight(1f)) {
                items(itens) { item ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { itemSelecionadoCodigo = item.codigo }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Mono(item.codigo, Cores.bomClaro, 11)
                        Spacer(Modifier.height(2.dp))
                        Text(item.especificacao, color = Cores.texto, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    HorizontalDivider(color = Cores.linha)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().weight(1f).padding(16.dp)) {
                Text(selecionado.especificacao, color = Cores.texto, fontSize = 14.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "clique para escolher outro item",
                    color = Cores.bomClaro, fontSize = 11.5.sp,
                    modifier = Modifier.clickable { itemSelecionadoCodigo = null }
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = quantidadeTexto,
                    onValueChange = { quantidadeTexto = it },
                    label = { Text("Quantidade (${selecionado.unidade})") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                val quantidade = quantidadeTexto.replace(',', '.').toDoubleOrNull()
                if (quantidade != null && quantidade >= 0) {
                    val valor = TaxaUfemg.calcular(selecionado, quantidade, t.valorUfemg)
                    val memoriaCalculo = if (selecionado.fixoUfemg > 0) {
                        "%.0f UFEMG fixas + %.0f × %.2f UFEMG/%s".format(
                            java.util.Locale.US, selecionado.fixoUfemg, selecionado.variavelUfemgPorUnidade,
                            quantidade, selecionado.unidade
                        )
                    } else {
                        "%.2f × %.2f UFEMG/%s".format(
                            java.util.Locale.US, quantidade, selecionado.variavelUfemgPorUnidade, selecionado.unidade
                        )
                    }
                    CartaoResultado(
                        rotulo = "Valor da taxa",
                        valor = "R$ %,.2f".format(java.util.Locale("pt", "BR"), valor)
                    )
                    Spacer(Modifier.height(8.dp))
                    Mono(memoriaCalculo, Cores.textoFraco, 12)
                } else {
                    Text("Informe a quantidade.", color = Cores.textoFraco, fontSize = 12.5.sp)
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "UFEMG ${t.exercicio}: R$ %.4f — %s".format(java.util.Locale.US, t.valorUfemg, t.fonte),
                color = Cores.textoFraco, fontSize = Tipos.detalhe, lineHeight = 15.sp
            )
        }
    }
}

/** Glossário de siglas do SISEMA — referência, sem busca por enquanto: a lista cabe na tela. */
@Composable
fun TelaGlossario(voltar: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Glossário do SISEMA", voltar)
        LazyColumn(Modifier.weight(1f)) {
            items(GlossarioSisema.termos) { t ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(t.sigla, color = Cores.bomClaro, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(t.significado, color = Cores.texto, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(t.descricao, color = Cores.textoFraco, fontSize = 12.sp, lineHeight = 17.sp)
                }
                HorizontalDivider(color = Cores.linha)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * Cadastros e registros do IEF — categorias, quem precisa se cadastrar e base legal.
 *
 * NÃO tem valor de taxa de cadastro inicial: a pesquisa que montou `assets/ief/cadastros.json`
 * não achou uma tabela atual confiável (só uma de 2009, defasada demais para usar). Onde existe
 * um valor confirmado em texto oficial — a taxa de ALTERAÇÃO de registro da flora, em UFEMG —
 * ele aparece calculado contra a UFEMG do exercício vigente; para o resto, a tela diz que não
 * tem o número, em vez de inventar.
 */
@Composable
fun TelaCadastrosIef(vm: CapturaViewModel, voltar: () -> Unit) {
    val cadastros by vm.cadastrosIef.collectAsState()
    val tabelaTaxas by vm.tabelaTaxas.collectAsState()
    var grupoSelecionadoId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Cadastros do IEF", voltar)

        val c = cadastros
        if (c == null) {
            Vazio("Carregando…", "")
            return@Column
        }

        val grupo = c.grupos.firstOrNull { it.id == grupoSelecionadoId }

        if (grupo == null) {
            Text(
                c.aviso, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            Rotulo("ESCOLHA O GRUPO")
            LazyColumn(Modifier.weight(1f)) {
                items(c.grupos) { g ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { grupoSelecionadoId = g.id }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(g.titulo, color = Cores.texto, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${g.categorias.size} categorias — ${g.baseLegal}",
                            color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp
                        )
                    }
                    HorizontalDivider(color = Cores.linha)
                }
            }
        } else {
            Text(
                "clique para escolher outro grupo",
                color = Cores.bomClaro, fontSize = 11.5.sp,
                modifier = Modifier.clickable { grupoSelecionadoId = null }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
            LazyColumn(Modifier.weight(1f)) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text(grupo.titulo, color = Cores.texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Mono(grupo.baseLegal, Cores.textoFraco, 11)
                    }
                }
                item { Rotulo("CATEGORIAS") }
                items(grupo.categorias) { cat ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(cat.nome, color = Cores.texto, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                        if (cat.quemPrecisa != null) {
                            Spacer(Modifier.height(3.dp))
                            Text(cat.quemPrecisa, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp)
                        }
                        if (cat.baseLegalEspecifica != null) {
                            Spacer(Modifier.height(2.dp))
                            Mono(cat.baseLegalEspecifica, Cores.bomClaro, 10)
                        }
                    }
                }
                val quemPrecisaGeral: String? = grupo.quemPrecisaGeral
                if (quemPrecisaGeral != null) {
                    item {
                        Rotulo("QUEM PRECISA SE CADASTRAR")
                        Text(
                            quemPrecisaGeral, color = Cores.texto, fontSize = 12.5.sp, lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                val isencao: String? = grupo.isencao
                if (isencao != null) {
                    item {
                        Rotulo("ISENÇÃO")
                        Text(
                            isencao, color = Cores.texto, fontSize = 12.5.sp, lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                if (grupo.documentos.isNotEmpty()) {
                    item { Rotulo("DOCUMENTOS") }
                    items(grupo.documentos) { doc ->
                        Text(
                            "• $doc", color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                        )
                    }
                }
                item {
                    Rotulo("RENOVAÇÃO")
                    Text(
                        grupo.renovacao, color = Cores.texto, fontSize = 12.5.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                item {
                    Rotulo("TAXA")
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        val taxaUfemg: Double? = grupo.taxaAlteracaoUfemg
                        val ufemg: Double? = tabelaTaxas?.valorUfemg
                        val exercicio: Int = tabelaTaxas?.exercicio ?: 0
                        if (taxaUfemg != null && ufemg != null) {
                            Text(
                                "Alteração de registro: %.0f UFEMG × R$ %.4f = R$ %,.2f (%d)".format(
                                    java.util.Locale("pt", "BR"), taxaUfemg, ufemg, taxaUfemg * ufemg, exercicio
                                ),
                                color = Cores.texto, fontSize = 13.sp, fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(grupo.taxaNota, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp)
                    }
                }
                item {
                    Text(
                        "Cadastro feito em: ${c.sistema}",
                        color = Cores.textoFraco, fontSize = Tipos.detalhe,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LinhaAlternavel(titulo: String, descricao: String, ligado: Boolean, aoAlternar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, color = Cores.texto, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(descricao, color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = ligado, onCheckedChange = { aoAlternar() },
            colors = SwitchDefaults.colors(checkedTrackColor = Cores.bom)
        )
    }
    HorizontalDivider(color = Cores.linha)
}
