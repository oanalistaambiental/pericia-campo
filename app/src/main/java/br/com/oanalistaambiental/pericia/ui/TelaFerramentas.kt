package br.com.oanalistaambiental.pericia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.captura.Orientacoes
import br.com.oanalistaambiental.pericia.geo.Medicao
import br.com.oanalistaambiental.pericia.geo.Utm

/** Menu de ferramentas — o que existe fora do ato de fotografar. */
@Composable
fun TelaFerramentas(
    vm: CapturaViewModel,
    irParaBussola: () -> Unit,
    irParaClinometro: () -> Unit,
    irParaMedicao: () -> Unit,
    irParaCoordenada: () -> Unit,
    irParaConfiguracoes: () -> Unit,
    irParaSessoes: () -> Unit,
    voltar: () -> Unit
) {
    val alvo by vm.alvo.collectAsState()
    val vertices by vm.vertices.collectAsState()

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Ferramentas", voltar)
        LazyColumn(Modifier.weight(1f)) {
            item {
                Rotulo("REGISTRO")
                Item("Sessões de vistoria", "Agrupar fotos, exportar laudo, conferir integridade", irParaSessoes)

                Rotulo("MEDIR")
                Item(
                    "Medir área por caminhamento",
                    if (vertices.isEmpty()) "Ande o perímetro marcando os cantos; o app calcula a área"
                    else "Em andamento — ${vertices.size} vértice(s) marcado(s)",
                    irParaMedicao
                )
                Item("Clinômetro", "Declividade de talude ou rampa, em graus e em porcentagem", irParaClinometro)
                Item("Bússola e altímetro", "Leitura de campo sem precisar fotografar", irParaBussola)

                Rotulo("NAVEGAR")
                Item(
                    "Ir para uma coordenada",
                    "Cole a coordenada de um auto, planta ou memorial e caminhe até ela",
                    irParaCoordenada
                )
                if (alvo != null) {
                    Item("Cancelar guia ativo", alvo!!.rotulo) { vm.limparAlvo() }
                }

                Rotulo("MANUTENÇÃO")
                Item(
                    "Completar endereços pendentes",
                    "Usa a conexão atual para resolver os endereços das fotos feitas offline"
                ) { vm.resolverEnderecos() }
                Item("Configurações e pacotes", "Datum, folga de aviso, camadas instaladas", irParaConfiguracoes)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Item(titulo: String, descricao: String, aoClicar: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable { aoClicar() }
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(titulo, color = Cores.texto, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(descricao, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp)
    }
    HorizontalDivider(color = Cores.linha)
}

/**
 * Bussola e altimetro autonomos.
 *
 * BUG corrigido: o ponteiro anterior era um caractere "▲" com `rotate()` aplicado sobre ele
 * mesmo. Rotacionar um simbolo em torno do proprio centro nao o faz orbitar o mostrador — ele
 * apenas girava no lugar, sem apontar para lugar nenhum. Trocado pela fita de rumo, que e o
 * que se usa em navegacao e ja esta na tela da camera.
 */
@Composable
fun TelaBussola(vm: CapturaViewModel, voltar: () -> Unit) {
    val p by vm.estadoCampo.posicao.collectAsState()
    val o by vm.estadoCampo.orientacao.collectAsState()
    val b by vm.estadoCampo.barometro.collectAsState()

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Bússola e altímetro", voltar)
        SeloPrecisao(p)
        FitaBussola(o)

        Column(
            Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                o.azimuteGraus?.let { "%.0f°".format(it) } ?: "—",
                color = Cores.texto, fontSize = 56.sp, fontWeight = FontWeight.Bold
            )
            Text(
                o.azimuteGraus?.let { "${Orientacoes.rosa(it)} · direção da câmera" }
                    ?: "aponte a câmera para o horizonte",
                color = Cores.textoFraco, fontSize = 13.sp
            )

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
                color = Cores.texto, fontSize = 64.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                percent?.let { "%.1f%% de declividade".format(it) } ?: "sem leitura",
                color = Cores.bomClaro, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(28.dp))

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .background(Cores.superficie, RoundedCornerShape(6.dp)).padding(16.dp)
            ) {
                Text("COMO MEDIR", color = Cores.textoFraco, fontSize = 10.5.sp, letterSpacing = 1.sp)
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
                color = Cores.texto, fontSize = 44.sp, fontWeight = FontWeight.Bold
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

        if (pol != null && pol!!.vertices.size >= 3 && !pol!!.confiavel()) {
            AvisoRestricao(
                "A incerteza passa de 20% da área. Para laudo, espere o GNSS melhorar e refaça, " +
                    "ou trate este número como estimativa.",
                br.com.oanalistaambiental.pericia.geo.Situacao.PROXIMO_AO_LIMITE
            )
        }

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            BotaoLargo("Marcar vértice aqui", principal = true) { vm.marcarVertice() }
            Spacer(Modifier.height(8.dp))
            Row {
                Box(Modifier.weight(1f)) {
                    BotaoLargo("Desfazer", habilitado = vertices.isNotEmpty()) { vm.desfazerVertice() }
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    BotaoLargo("Limpar", habilitado = vertices.isNotEmpty()) { vm.limparMedicao() }
                }
            }
            if ((pol?.vertices?.size ?: 0) >= 3) {
                Spacer(Modifier.height(8.dp))
                BotaoLargo("Usar como observação das fotos") { vm.usarMedicaoComoObservacao() }
            }
        }

        if (vertices.isEmpty()) {
            Vazio(
                "Nenhum vértice marcado",
                "Caminhe até o primeiro canto da área, toque em “Marcar vértice aqui” e siga o " +
                    "perímetro parando em cada canto. O app fecha o polígono sozinho."
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(vertices) { v ->
                    val i = vertices.indexOf(v)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${i + 1}", color = Cores.textoFraco, fontSize = 12.sp,
                            modifier = Modifier.width(26.dp)
                        )
                        Mono("%.6f, %.6f".format(v.lat, v.lon), Cores.texto, 11)
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

/** Navegar ate uma coordenada que veio de fora — auto de infracao, planta, memorial. */
@Composable
fun TelaIrParaCoordenada(vm: CapturaViewModel, aoDefinir: () -> Unit, voltar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    val lida = remember(texto) { Medicao.interpretar(texto) }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Ir para uma coordenada", voltar)

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                label = { Text("Cole ou digite a coordenada") },
                modifier = Modifier.fillMaxWidth()
            )
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
                        aoDefinir()
                    }
                }
            }
        }

        Ajuda(
            "O que o guia faz e o que não faz",
            "Ele mostra distância em linha reta e o rumo, e marca o alvo na fita da bússola da " +
                "tela de câmera. Não traça rota nem desvia de obstáculo: a leitura é de bússola, " +
                "como se faz com uma carta na mão. Quando você chega dentro da precisão do GNSS, " +
                "o app avisa."
        )
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

        LazyColumn(Modifier.weight(1f)) {
            item {
                Rotulo("COORDENADAS")
                Linha("Datum de exibição", "SIRGAS 2000", Cores.bomClaro)
                Linha("Projeção da legenda", "UTM, fuso automático", Cores.texto)

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
                if (camadas.isEmpty()) {
                    Column(
                        Modifier.padding(16.dp).fillMaxWidth()
                            .background(Cores.superficie, RoundedCornerShape(4.dp)).padding(14.dp)
                    ) {
                        Text("Nenhum pacote instalado", color = Cores.atencaoClaro, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "O app funciona normalmente sem ele — câmera, GNSS, legenda, hash, " +
                                "sessões, medição e laudo. Só o alerta de restrição fica desligado.\n\n" +
                                "Para ligar, gere o arquivo com ferramentas/montar-pacote.sh e copie para:",
                            color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Mono("Android/data/br.com.oanalistaambiental.pericia/\n  files/pacotes/mg-base.gpkg", Cores.texto, 10)
                    }
                } else {
                    Column(Modifier.padding(horizontal = 16.dp)) {
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
