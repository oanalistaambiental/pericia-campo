package br.com.oanalistaambiental.pericia.enquadramento.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.geo.Coordenadas
import br.com.oanalistaambiental.pericia.geo.Utm
import br.com.oanalistaambiental.pericia.enquadramento.norma.CasoEspecial
import br.com.oanalistaambiental.pericia.enquadramento.norma.Decisoes
import br.com.oanalistaambiental.pericia.enquadramento.norma.Dispensa
import br.com.oanalistaambiental.pericia.enquadramento.norma.Grau
import br.com.oanalistaambiental.pericia.taxas.FasesLicenciamento
import br.com.oanalistaambiental.pericia.taxas.TabelaTaxas

/* ------------------------------------------------------- CRITÉRIO LOCACIONAL */

@Composable
fun TelaLocacional(vm: SimulacaoViewModel, avancar: () -> Unit, voltar: () -> Unit) {
    val regras by vm.regras.collectAsState()
    val marcados by vm.marcados.collectAsState()
    val fatores by vm.fatoresMarcados.collectAsState()
    val deteccao by vm.deteccao.collectAsState()
    val comEia by vm.comEia.collectAsState()
    var coordenada by rememberSaveable { mutableStateOf("") }
    val pacoteInstalado by vm.pacoteInstalado.collectAsState()
    LaunchedEffect(Unit) { vm.conferirPacote() }
    // Seletor de arquivo do sistema. E o unico caminho pelo qual o pacote de camadas pode
    // chegar ao aparelho sem adb — antes nao havia caminho nenhum.
    val escolherPacote = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.instalarPacote(uri) }
    val lida = remember(coordenada) { Coordenadas.interpretar(coordenada) }
    val erroBase by vm.erroBase.collectAsState()
    val r = regras ?: return Carregando("3. Critério locacional", voltar, erroBase)

    val incidentes = r.criterios.filter { it.id in marcados }
    val peso = incidentes.maxOfOrNull { it.peso } ?: 0

    Column(Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Cabecalho("3. Critério locacional", "Tabela 4 da DN 217 — prevalece o de maior peso", voltar)

        LazyColumn(Modifier.weight(1f)) {
            item {
                Rotulo("SUGESTÃO PELA COORDENADA")
                Cartao {
                    Text(
                        "O art. 6º, §5º da DN 217 manda consultar o IDE-Sisema para verificar a " +
                            "incidência. O aplicativo faz essa consulta no mesmo pacote de camadas do " +
                            "app de campo, sem internet — e a resposta é sugestão, não decisão.",
                        color = Cores.textoFraco, fontSize = 12.sp, lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    // BUG corrigido, e impedia usar o recurso: eram dois campos com
                    // KeyboardType.Number, que na maioria dos teclados do Android NAO tem o
                    // sinal de menos — ou seja, era impossivel digitar uma latitude no Brasil.
                    // E, se o texto nao virasse numero, o botao nao fazia nada, em silencio.
                    // Agora e um campo so, que aceita colar nos tres formatos que aparecem de
                    // verdade, mostra como interpretou e diz quando nao entendeu.
                    OutlinedTextField(
                        coordenada, { coordenada = it },
                        label = { Text("Coordenada do empreendimento") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    when {
                        coordenada.isBlank() -> Mono(
                            "aceita  -19.9167, -43.9345   ·   19°55'00\"S 43°56'04\"W   ·   23S 611520E 7797383N",
                            Cores.textoFraco, 10
                        )
                        lida == null -> Text(
                            "Não reconheci esse formato. Confira se os dois valores estão " +
                                "presentes e se o separador é vírgula ou espaço.",
                            color = Cores.atencao, fontSize = 11.5.sp, lineHeight = 16.sp
                        )
                        else -> {
                            Mono("lido como ${lida.formato}: %.6f, %.6f".format(lida.lat, lida.lon), Cores.texto, 11)
                            Spacer(Modifier.height(2.dp))
                            Mono(Utm.projetar(lida.lat, lida.lon).formatado(), Cores.textoFraco, 10)
                            lida.aviso?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it, color = Cores.atencao, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Botao("Verificar camadas neste ponto") {
                        val l = lida
                        if (l == null) vm.avisar(
                            "Informe uma coordenada válida antes de verificar as camadas."
                        ) else vm.detectarPorCoordenada(l.lat, l.lon)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!pacoteInstalado) {
                        Text(
                            "Usando a base embarcada no aplicativo (a mesma da câmera de campo) — " +
                                "cobre o estado inteiro, mas pode estar um pouco defasada. Para a " +
                                "versão mais atual, instale o arquivo .gpkg oficial abaixo.",
                            color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Botao(if (pacoteInstalado) "Trocar pacote de camadas" else "Instalar pacote de camadas") {
                        // */* e nao application/geopackage: o Android nao reconhece a
                        // extensao .gpkg e o seletor apareceria vazio, sem explicacao.
                        escolherPacote.launch(arrayOf("*/*"))
                    }
                    deteccao?.let { d ->
                        Spacer(Modifier.height(8.dp))
                        Mono("pacote ${d.versaoPacote}")
                        if (d.camadasNaoInstaladas.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Mono("camadas ausentes no pacote: ${d.camadasNaoInstaladas.joinToString(", ")}",
                                Cores.atencao, 10)
                        }
                        if (d.camadasComFalha.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "SEM LEITURA: ${d.camadasComFalha.joinToString(", ")}. Isto NÃO " +
                                    "quer dizer que não incide — quer dizer que a camada não " +
                                    "pôde ser consultada. Reinstale o pacote.",
                                color = Cores.alerta, fontSize = 11.sp, lineHeight = 15.sp
                            )
                        }
                        if (d.aConferir.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "A CONFERIR à mão: ${d.aConferir.joinToString(", ") { it.id }}. " +
                                    "A geometria bate, mas o pacote não traz a categoria da UC — " +
                                    "e o critério depende dela (APA, por exemplo, é excluída do " +
                                    "critério de uso sustentável).",
                                color = Cores.atencao, fontSize = 11.sp, lineHeight = 15.sp
                            )
                        }
                    }
                }

                Rotulo("CRITÉRIOS — TABELA 4")
                r.criterios.forEach { c ->
                    ItemMarcavel(
                        marcado = c.id in marcados,
                        titulo = c.texto,
                        etiqueta = "peso ${c.peso}",
                        destaque = c.peso == 2,
                        nota = c.nota ?: if (!c.automatico) "Depende do projeto — o app não consegue deduzir do ponto." else null,
                        aoAlternar = { vm.alternarCriterio(c.id) }
                    )
                }

                Spacer(Modifier.height(12.dp))
                Cartao {
                    Text("Fator locacional: peso $peso", color = Cores.acento,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (incidentes.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Você marcou ${incidentes.size} critérios. Os pesos NÃO se somam: pelo " +
                                "art. 6º, §3º prevalece o de maior peso.",
                            color = Cores.textoFraco, fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }
                }

                Rotulo("FATORES DE RESTRIÇÃO OU VEDAÇÃO — TABELA 5")
                Aviso(
                    "Não conferem peso e não mudam o enquadramento (art. 6º, §4º). Entram na " +
                        "abordagem dos estudos — e alguns vedam a atividade no local.", TipoAviso.INFO
                )
                r.fatores.forEach { f ->
                    ItemMarcavel(
                        marcado = f.id in fatores,
                        titulo = f.nome,
                        etiqueta = null,
                        destaque = false,
                        nota = f.texto,
                        aoAlternar = { vm.alternarFator(f.id) }
                    )
                }

                Rotulo("ESTUDO")
                Cartao {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { vm.definirEia(!comEia) }) {
                        Checkbox(checked = comEia, onCheckedChange = { vm.definirEia(it) })
                        Spacer(Modifier.width(6.dp))
                        Text("Haverá EIA-Rima ou audiência pública", color = Cores.texto, fontSize = 13.sp)
                    }
                    Text("Muda o prazo de análise de 6 para 12 meses (Decreto 47.383/2018, art. 22).",
                        color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp)
                }

                Spacer(Modifier.height(18.dp))
                Box(Modifier.padding(horizontal = 16.dp)) {
                    // `vm.calcular(); avancar()` era incondicional. Falhando o calculo, a tela
                    // de resultado exibia A SIMULACAO ANTERIOR com cara de resultado novo,
                    // enquanto o erro passava cinco segundos na barra de baixo. Documento
                    // assinavel com o numero errado. Agora so avanca se houve resultado novo.
                    Botao("Calcular enquadramento", principal = true) {
                        if (vm.calcularComRetorno()) avancar()
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun ItemMarcavel(
    marcado: Boolean, titulo: String, etiqueta: String?, destaque: Boolean,
    nota: String?, aoAlternar: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .background(if (marcado) Cores.acentoClaro else Cores.superficie, RoundedCornerShape(6.dp))
            .clickable { aoAlternar() }.padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(checked = marcado, onCheckedChange = { aoAlternar() })
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, color = Cores.texto, fontSize = 12.5.sp, lineHeight = 17.sp)
            nota?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        etiqueta?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, color = if (destaque) Cores.alerta else Cores.textoFraco,
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/* --------------------------------------------------------------- RESULTADO */

@Composable
fun TelaResultado(
    vm: SimulacaoViewModel,
    exportar: () -> Unit,
    novaSimulacao: () -> Unit,
    voltar: () -> Unit
) {
    val contexto = LocalContext.current
    fun abrirLink(url: String) {
        runCatching {
            contexto.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { vm.avisar("Não foi possível abrir o navegador: ${it.message}") }
    }
    val res by vm.resultado.collectAsState()
    val dica by vm.dica.collectAsState()
    val tabelaTaxas by vm.tabelaTaxas.collectAsState()
    val atividadeAtual by vm.atividade.collectAsState()
    val r = res ?: run {
        Column(Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Cabecalho("Resultado", voltar = voltar)
            Aviso("Nada calculado ainda.", TipoAviso.ATENCAO)
        }
        return
    }

    Column(Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Cabecalho("Resultado da simulação", voltar = voltar)

        LazyColumn(Modifier.weight(1f)) {
            item {
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .background(Cores.acento, RoundedCornerShape(8.dp)).padding(20.dp)
                ) {
                    Text("MODALIDADE", color = Color(0xCCFFFFFF), fontSize = 11.sp, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(r.modalidade.sigla, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(r.modalidade.nome, color = Color(0xE6FFFFFF), fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(14.dp))
                    Row {
                        Indicador("CLASSE", r.classe.toString())
                        Spacer(Modifier.width(28.dp))
                        Indicador("PORTE", r.porte.name)
                        Spacer(Modifier.width(28.dp))
                        Indicador("POTENCIAL", r.potencialGeral.name)
                        Spacer(Modifier.width(28.dp))
                        Indicador("LOCACIONAL", r.fatorLocacional.toString())
                    }
                }

                r.avisos.forEach { Spacer(Modifier.height(8.dp)); Aviso(it, TipoAviso.ATENCAO) }

                // Dicas de pareceres — dado, nao codigo (ver enquadramento/norma/Dicas.kt). A
                // maioria das atividades ainda nao tem arquivo, e a ausencia precisa dizer isso
                // explicitamente: silencio aqui seria indistinguivel de "sem padrao a apontar".
                Rotulo("DICAS DE PARECERES")
                Cartao {
                    val d = dica
                    if (d != null) {
                        Text(d.texto, color = Cores.texto, fontSize = 13.sp, lineHeight = 19.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Extraído de ${d.pareceresConsultados} parecer(es) deferidos, em ${d.dataExtracao}. " +
                                "Generaliza o padrão recorrente — não é a decisão de nenhum processo específico.",
                            color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 16.sp
                        )
                    } else {
                        Text(
                            "Ainda não lemos pareceres desta atividade — nenhuma dica disponível. " +
                                "Isso não é indício de que a atividade não tem particularidades, " +
                                "só de que a leitura ainda não chegou nela.",
                            color = Cores.textoFraco, fontSize = 12.5.sp, lineHeight = 18.sp
                        )
                    }
                }

                // Aviso GERAL, nao um cruzamento codigo-a-codigo: o CTF/APP do Ibama usa uma
                // classificacao federal diferente da DN 217, e cruzar as duas exigiria ler o
                // Anexo I da norma do Ibama linha a linha, do mesmo jeito que o catalogo da DN
                // 217 foi extraido. Sem essa leitura, uma tabela codigo-a-codigo seria dado
                // fabricado — o aviso fica generico e verdadeiro, nunca especifico e inventado.
                Spacer(Modifier.height(8.dp))
                Aviso(
                    "Atividade potencialmente poluidora também pode exigir Cadastro Técnico " +
                        "Federal (CTF/APP) do Ibama, independente do licenciamento estadual. " +
                        "Este app não sabe dizer se esta atividade específica exige — confira em " +
                        "servicos.ibama.gov.br.",
                    TipoAviso.INFO
                )

                // Taxa de entrada no processo (DAE) — dado real da FEAM, tabelado por
                // classe/modalidade/listagem. A "fase" (LP, LI, LO...) nao sai do enquadramento
                // sozinho quando a modalidade tem mais de uma linha na tabela (LAT, LAC2): e
                // decisao do processo, nao da simulacao, por isso a tela pergunta.
                tabelaTaxas?.let { t -> BlocoTaxaEntrada(t, r.classe, r.modalidade.sigla, atividadeAtual?.codigo) }

                // Art. 18 — o caso condicional ganha bloco proprio, e nao so uma linha de aviso.
                // A condicao precisa caber inteira na tela: quem vai formalizar o processo tem
                // de conseguir ler e dizer "isto e o meu caso" ou "nao e", sem abrir a norma.
                r.casoArt18?.let { caso ->
                    Rotulo("CASO ESPECIAL DA NORMA — ${caso.referencia.uppercase()}")
                    Cartao {
                        Text(
                            caso.resumo,
                            color = Cores.texto, fontSize = 13.sp, lineHeight = 19.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("SÓ VALE SE FOR VERDADE QUE:",
                            color = Cores.textoFraco, fontSize = 10.5.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(caso.condicao, color = Cores.texto, fontSize = 12.5.sp, lineHeight = 18.sp)
                        caso.condicaoExtra?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = Cores.texto, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        caso.segundaHipotese?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = Cores.texto, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Aviso(
                            "O aplicativo NÃO aplicou este caso. A modalidade acima é a da " +
                                "Tabela 3. Confirmar a condição é do empreendedor.",
                            TipoAviso.ALERTA
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("TEXTO DA NORMA",
                            color = Cores.textoFraco, fontSize = 10.5.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        Mono(caso.texto, tamanho = 11)
                    }
                }

                Rotulo("O QUE ISSO SIGNIFICA")
                Cartao {
                    Text(r.modalidade.descricao, color = Cores.texto, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(10.dp))
                    LinhaDado("Licenças", r.modalidade.licencas.joinToString(", "))
                    LinhaDado("Etapas", "${r.modalidade.etapas}")
                    LinhaDado("Prazo de análise", "${r.prazoAnaliseDias} dias", destaque = true)
                    LinhaDado("Validade", r.modalidade.validadeTexto)
                }

                // Bloco novo: decisões públicas da MESMA atividade, filtradas por código.
                // Nunca um processo específico — ver a documentação de Decisoes.kt.
                r.valorInformado?.let { vi ->
                    val cod = r.codigoAtividade
                    if (cod != null && cod != "—") {
                        Rotulo("DECISÕES JÁ DEFERIDAS DESTA ATIVIDADE")
                        Cartao {
                            Text(
                                "Consulta pública do SISEMA, filtrada pelo código $cod.",
                                color = Cores.texto, fontSize = 12.5.sp, lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                Decisoes.COMO_USAR,
                                color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Aviso(Decisoes.CUIDADO_NORMA_ANTIGA, TipoAviso.ATENCAO)
                            Spacer(Modifier.height(10.dp))
                            Botao("Abrir decisões do código $cod") { abrirLink(Decisoes.porAtividade(cod)) }
                            Spacer(Modifier.height(6.dp))
                            Botao("Portal Ecossistemas (visitante)") { abrirLink(Decisoes.ECOSSISTEMAS_VISITANTE) }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "O app não mostra processo, empreendimento nem CNPJ: mostra a busca por " +
                                    "atividade. Enquadramento de um empreendimento não vincula o de outro.",
                                color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 15.sp
                            )
                        }
                    }
                }

                Rotulo("ESTUDOS EXIGIDOS")
                Cartao {
                    r.modalidade.estudos.forEach {
                        Text("• $it", color = Cores.texto, fontSize = 12.5.sp, lineHeight = 18.sp,
                            modifier = Modifier.padding(vertical = 3.dp))
                    }
                    r.modalidade.audienciaTexto?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp)
                    }
                }

                if (r.fatoresRestricao.isNotEmpty()) {
                    Rotulo("FATORES DE RESTRIÇÃO OU VEDAÇÃO INCIDENTES")
                    r.fatoresRestricao.forEach { f ->
                        Cartao {
                            Text(f.nome, color = Cores.alerta, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(f.texto, color = Cores.texto, fontSize = 11.5.sp, lineHeight = 16.sp)
                        }
                    }
                }

                Rotulo("MEMÓRIA DE CÁLCULO")
                r.passos.forEachIndexed { i, p ->
                    Cartao {
                        Row(verticalAlignment = Alignment.Top) {
                            Text("${i + 1}", color = Cores.acento, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                            Column {
                                Text(p.rotulo, color = Cores.textoFraco, fontSize = 11.sp, letterSpacing = 0.5.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(p.valor, color = Cores.texto, fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(p.fundamento, color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                        }
                    }
                }

                vm.regras.value?.let { regras ->
                    Rotulo("RENOVAÇÃO")
                    Cartao {
                        Text(regras.gerais.renovacaoTexto, color = Cores.texto,
                            fontSize = 12.5.sp, lineHeight = 18.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Botao("Exportar simulação em PDF", principal = true) { exportar() }
                    Botao("Nova simulação") { novaSimulacao() }
                }

                Spacer(Modifier.height(16.dp))
                Aviso(
                    "Simulação com base em norma pública. Não substitui o enquadramento do órgão " +
                        "ambiental nem vincula a Administração. Ferramenta independente, sem vínculo " +
                        "com o SISEMA.", TipoAviso.INFO
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/**
 * Taxa de entrada no processo (DAE) — dado real da FEAM, tabelado ano a ano por classe,
 * modalidade e listagem da atividade (ver o pacote taxas, arquivo TaxaUfemg). Quando a modalidade tem mais de
 * uma linha na tabela (LAT, LAC2), a tela pede pra escolher a fase — informação do processo,
 * que o enquadramento sozinho não decide.
 */
@Composable
private fun BlocoTaxaEntrada(
    tabela: TabelaTaxas,
    classe: Int,
    modalidadeSigla: String,
    codigoAtividade: String?
) {
    val opcoes = FasesLicenciamento.opcoes(modalidadeSigla)
    if (opcoes.isEmpty()) return
    var faseEscolhida by remember(modalidadeSigla) { mutableStateOf(opcoes.first().chave) }
    val listagemG = codigoAtividade?.startsWith("G") == true

    Rotulo("TAXA DE ENTRADA NO PROCESSO (DAE)")
    Cartao {
        if (opcoes.size > 1) {
            Text(
                "Fase que está sendo requerida:",
                color = Cores.textoFraco, fontSize = 11.5.sp, letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                opcoes.forEach { op ->
                    val ativo = op.chave == faseEscolhida
                    Text(
                        op.rotulo,
                        color = if (ativo) Color.White else Cores.texto,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(if (ativo) Cores.acento else Cores.superficie, RoundedCornerShape(6.dp))
                            .clickable { faseEscolhida = op.chave }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        val valor = tabela.licenciamento.valor(faseEscolhida, classe, listagemG)
        if (valor == null) {
            Text(
                "A tabela oficial não traz valor para classe $classe nesta fase — confira " +
                    "diretamente com a Unidade Regional.",
                color = Cores.textoFraco, fontSize = 12.5.sp, lineHeight = 18.sp
            )
        } else {
            Text(
                "R$ %,.2f".format(java.util.Locale("pt", "BR"), valor),
                color = Cores.texto, fontSize = 30.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Listagem ${if (listagemG) "G — agrossilvipastoril" else "A a F — industrial, minerária e infraestrutura"}" +
                    (if (codigoAtividade == null) " (sem atividade do catálogo escolhida — confira se é essa a listagem)" else ""),
                color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 16.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Fonte: ${tabela.licenciamento.fonte}. Valor de tabela, sujeito a atualização anual " +
                "pela FEAM — confirme antes de emitir o DAE.",
            color = Cores.textoFraco, fontSize = 10.5.sp, lineHeight = 15.sp
        )
    }
}

@Composable
private fun Indicador(rotulo: String, valor: String) {
    Column {
        Text(rotulo, color = Color(0xB3FFFFFF), fontSize = 9.5.sp, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(2.dp))
        Text(valor, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}

/* ------------------------------------------------------------------ NORMA */

@Composable
fun TelaNorma(vm: SimulacaoViewModel, voltar: () -> Unit) {
    val regras by vm.regras.collectAsState()
    val erroBase by vm.erroBase.collectAsState()
    val r = regras ?: return Carregando("A norma", voltar, erroBase)

    Column(Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Cabecalho("A norma", r.procedencia["norma"], voltar)
        LazyColumn(Modifier.weight(1f)) {
            item {
                Rotulo("TABELA 2 — CLASSE POR PORTE E POTENCIAL")
                Cartao {
                    Row {
                        Text("", modifier = Modifier.width(70.dp))
                        listOf("PP P", "PP M", "PP G").forEach {
                            Text(it, color = Cores.textoFraco, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        }
                    }
                    Grau.entries.forEach { porte ->
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Text("Porte ${porte.name}", color = Cores.textoFraco, fontSize = 11.sp,
                                modifier = Modifier.width(70.dp))
                            Grau.entries.forEach { pp ->
                                Text("${r.tabela2["${porte.name}${pp.name}"]}", color = Cores.texto,
                                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Rotulo("TABELA 3 — MODALIDADE POR CLASSE E CRITÉRIO LOCACIONAL")
                Cartao {
                    Row {
                        Text("", modifier = Modifier.width(58.dp))
                        listOf("peso 0", "peso 1", "peso 2").forEach {
                            Text(it, color = Cores.textoFraco, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        }
                    }
                    (1..6).forEach { classe ->
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Text("Classe $classe", color = Cores.textoFraco, fontSize = 11.sp,
                                modifier = Modifier.width(58.dp))
                            (0..2).forEach { fator ->
                                Text(r.tabela3["$classe|$fator"] ?: "—", color = Cores.texto,
                                    fontSize = 11.5.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Rotulo("TABELA 4 — CRITÉRIOS LOCACIONAIS")
                r.criterios.forEach { c ->
                    Cartao {
                        Row {
                            Text("peso ${c.peso}", color = if (c.peso == 2) Cores.alerta else Cores.textoFraco,
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(56.dp))
                            Text(c.texto, color = Cores.texto, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                }

                Rotulo("TABELA 5 — FATORES DE RESTRIÇÃO OU VEDAÇÃO")
                r.fatores.forEach { f ->
                    Cartao {
                        Text(f.nome, color = Cores.texto, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(f.texto, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp)
                    }
                }

                Rotulo("PRAZOS")
                Cartao {
                    LinhaDado("Análise", r.gerais.prazoAnaliseTexto)
                    LinhaDado("Com EIA-Rima", r.gerais.prazoAnaliseEiaTexto)
                    LinhaDado("Renovação", r.gerais.renovacaoTexto)
                    Spacer(Modifier.height(6.dp))
                    Text(r.gerais.observacaoPrazo, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp)
                }

                Rotulo("PROCEDÊNCIA DESTA BASE")
                Cartao {
                    Text(r.procedencia["aviso"] ?: "", color = Cores.atencao, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    LinhaDado("Extraída em", r.procedencia["extraido_em"] ?: "—")
                    LinhaDado("Cobertura", r.procedencia["cobertura"] ?: "—")
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/* ------------------------------------------------------- DISPENSA (ART. 10) */

/**
 * Resultado de dispensa de licenciamento — art. 10 da DN 217.
 *
 * A tela foi desenhada em torno de um risco de comunicação, não de um cálculo: quem lê este
 * resultado costuma ser um analista de crédito, e "dispensado" lido sozinho vira "não preciso
 * de nada". Por isso a palavra dispensa nunca aparece aqui sem os três deveres do parágrafo
 * único logo abaixo, no mesmo peso visual.
 */
@Composable
fun TelaDispensa(
    vm: SimulacaoViewModel,
    exportar: () -> Unit,
    novaSimulacao: () -> Unit,
    voltar: () -> Unit
) {
    val d = vm.dispensa.collectAsState().value ?: run {
        Column(Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Cabecalho("Dispensa", voltar = voltar)
            Aviso("Nada calculado ainda.", TipoAviso.ATENCAO)
        }
        return
    }

    Column(Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Cabecalho("Dispensa de licenciamento", voltar = voltar)

        LazyColumn(Modifier.weight(1f)) {
            item {
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .background(Cores.acento, RoundedCornerShape(8.dp)).padding(20.dp)
                ) {
                    Text("RESULTADO", color = Color(0xCCFFFFFF), fontSize = 11.sp, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (d.motivo == Dispensa.Motivo.PORTE_INFERIOR) "PORTE INFERIOR"
                        else "NÃO LISTADA",
                        color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Dispensado do licenciamento ambiental no âmbito estadual",
                        color = Color(0xE6FFFFFF), fontSize = 13.sp, lineHeight = 18.sp
                    )
                }

                Rotulo("FUNDAMENTO")
                Cartao {
                    Text(d.fundamento, color = Cores.texto, fontSize = 12.5.sp, lineHeight = 18.sp)
                    d.atividade?.let {
                        Spacer(Modifier.height(8.dp))
                        LinhaDado("Atividade", "${it.codigo} — ${it.descricao}")
                    }
                    d.valorInformado?.let {
                        LinhaDado("Informado", it.descricao())
                    }
                }

                // O bloco que impede a leitura apressada. Vem antes de qualquer botão.
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .background(Cores.alerta, RoundedCornerShape(8.dp)).padding(16.dp)
                ) {
                    Text(
                        "A DISPENSA NÃO EXIME DE NADA DISTO",
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "DN 217/2017, art. 10, parágrafo único. A dispensa é do processo de " +
                            "licenciamento estadual — e de mais nada.",
                        color = Color(0xE6FFFFFF), fontSize = 12.sp, lineHeight = 17.sp
                    )
                }

                d.deveres.forEach { dev ->
                    Rotulo("INCISO ${dev.inciso} — ${dev.titulo.uppercase()}")
                    Cartao {
                        Text(dev.texto, color = Cores.texto, fontSize = 12.5.sp, lineHeight = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Exemplos do que costuma incidir:",
                            color = Cores.textoFraco, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        dev.exemplos.forEach {
                            Text(
                                "• $it", color = Cores.texto, fontSize = 12.sp, lineHeight = 17.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "A lista de exemplos não é exaustiva e não está na norma: ela traduz " +
                                "o que costuma incidir em Minas Gerais. Confira o caso concreto.",
                            color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 15.sp
                        )
                    }
                }

                Rotulo("ATENÇÃO")
                d.avisos.forEach { Aviso(it, TipoAviso.ATENCAO); Spacer(Modifier.height(6.dp)) }

                Spacer(Modifier.height(18.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Botao("Exportar em PDF", principal = true) { exportar() }
                    Spacer(Modifier.height(8.dp))
                    Botao("Nova simulação") { novaSimulacao() }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}
