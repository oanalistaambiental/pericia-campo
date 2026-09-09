package br.com.oanalistaambiental.pericia.enquadramento.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.enquadramento.norma.*
import java.text.NumberFormat
import java.util.Locale

private val nf: NumberFormat = NumberFormat.getInstance(Locale("pt", "BR"))

/* ------------------------------------------------------------------ INÍCIO */

@Composable
fun TelaInicio(vm: SimulacaoViewModel, irParaSimulacao: () -> Unit, irParaNorma: () -> Unit) {
    val regras by vm.regras.collectAsState()
    val erro by vm.erroBase.collectAsState()

    Column(Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Cabecalho(
            "Enquadra MG",
            "Simulação de enquadramento e modalidade de licenciamento ambiental, pela DN COPAM 217/2017"
        )
        LazyColumn(Modifier.weight(1f)) {
            item {
                erro?.let { Spacer(Modifier.height(12.dp)); Aviso(it, TipoAviso.ALERTA) }

                Spacer(Modifier.height(12.dp))
                Aviso(
                    "Ferramenta independente, não afiliada ao SISEMA/SEMAD/FEAM e sem vínculo com " +
                        "o sistema oficial de licenciamento. O resultado é uma SIMULAÇÃO a partir de " +
                        "norma pública: não substitui o enquadramento feito pelo órgão ambiental.",
                    TipoAviso.INFO
                )

                regras?.let { r ->
                    Spacer(Modifier.height(8.dp))
                    Aviso(r.procedencia["aviso"] ?: "", TipoAviso.ATENCAO)
                }

                Spacer(Modifier.height(20.dp))
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Botao("Nova simulação", principal = true, habilitado = regras != null) {
                        vm.novaSimulacao(); irParaSimulacao()
                    }
                    Botao("Consultar a norma", habilitado = regras != null) { irParaNorma() }
                }

                regras?.let { r ->
                    Rotulo("BASE CARREGADA")
                    Cartao {
                        LinhaDado("Norma", r.procedencia["norma"] ?: "—")
                        LinhaDado("Extraída em", r.procedencia["extraido_em"] ?: "—")
                        LinhaDado("Atividades", "${r.atividades.size} no catálogo")
                        LinhaDado("Cobertura", r.procedencia["cobertura"] ?: "—")
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/* --------------------------------------------------------------- ATIVIDADE */

@Composable
fun TelaAtividade(vm: SimulacaoViewModel, avancar: () -> Unit, voltar: () -> Unit) {
    val regras by vm.regras.collectAsState()
    var busca by rememberSaveable { mutableStateOf("") }
    val erroBase by vm.erroBase.collectAsState()
    val r = regras ?: return Carregando("1. Atividade", voltar, erroBase)

    val lista = remember(busca, r) {
        if (busca.isBlank()) r.atividades
        else r.atividades.filter {
            it.codigo.contains(busca, true) || it.descricao.contains(busca, true)
        }
    }

    Column(Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Cabecalho("1. Atividade", "Escolha no catálogo, ou informe porte e potencial à mão", voltar)

        OutlinedTextField(
            value = busca, onValueChange = { busca = it },
            label = { Text("Buscar por código ou descrição") },
            modifier = Modifier.fillMaxWidth().padding(16.dp), singleLine = true
        )

        Box(Modifier.padding(horizontal = 16.dp)) {
            Botao("Não achei a atividade — informar à mão") {
                vm.escolherAtividade(atividadeManual()); avancar()
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "O modo manual usa apenas as tabelas conferidas da DN 217. É o caminho mais confiável " +
                "quando a atividade ainda não está no catálogo.",
            color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Rotulo("CATÁLOGO — ${lista.size} ATIVIDADE(S)")
        LazyColumn(Modifier.weight(1f)) {
            items(lista) { a ->
                Column(
                    Modifier.fillMaxWidth().clickable { vm.escolherAtividade(a); avancar() }
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Mono(a.codigo, Cores.acento, 12)
                        Spacer(Modifier.weight(1f))
                        Selo(a.conferencia)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(a.descricao, color = Cores.texto, fontSize = 13.5.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(3.dp))
                    Mono("potencial geral ${a.pp.geral.name} · porte por ${a.parametro.lowercase()}")
                }
                HorizontalDivider(color = Cores.linha)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun atividadeManual() = Atividade(
    codigo = "—", descricao = "Enquadramento informado manualmente",
    pp = PotencialPoluidor(Grau.P, Grau.P, Grau.P, Grau.P),
    tipo = "manual", parametro = "informado pelo usuário", conferencia = Conferencia.CRUZADO
)

@Composable
private fun Selo(c: Conferencia) {
    val (fundo, cor, texto) = when (c) {
        Conferencia.CRUZADO -> Triple(Cores.acentoClaro, Cores.acento, "conferido")
        Conferencia.UNICO -> Triple(Cores.atencaoClaro, Cores.atencao, "1 fonte")
        Conferencia.DIVERGENTE -> Triple(Cores.alertaClaro, Cores.alerta, "divergente")
        // Faixa nova, e a melhor que existe: o dado veio do texto oficial da DN 217 no SIAM,
        // campo a campo. Nao usa a cor de atencao — nao ha nada a conferir aqui.
        Conferencia.OFICIAL -> Triple(Cores.acentoClaro, Cores.acento, "texto oficial")
    }
    Text(texto, color = cor, fontSize = 10.sp,
        modifier = Modifier.background(fundo, RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 3.dp))
}

/* ------------------------------------------------------------------- PORTE */

@Composable
fun TelaPorte(
    vm: SimulacaoViewModel,
    avancar: () -> Unit,
    avancarDispensa: () -> Unit,
    voltar: () -> Unit
) {
    val a by vm.atividade.collectAsState()
    val porte by vm.porte.collectAsState()
    val potencial by vm.potencialManual.collectAsState()
    // rememberSaveable: girar o aparelho apagava o valor ja digitado.
    var valor by rememberSaveable { mutableStateOf("") }
    var alternativa by rememberSaveable { mutableStateOf(false) }
    val dispensa by vm.dispensa.collectAsState()
    val lacuna by vm.lacuna.collectAsState()
    val atividade = a ?: return Carregando("2. Porte e potencial", voltar)
    val manual = atividade.tipo == "manual"

    Column(Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Cabecalho("2. Porte e potencial",
            if (manual) "Informe os dois graus, conforme a listagem da DN 217"
            else "${atividade.codigo} — ${atividade.descricao}", voltar)

        LazyColumn(Modifier.weight(1f)) {
            item {
                if (manual) {
                    Rotulo("PORTE")
                    EscolhaGrau(porte) { vm.definirPorteManual(it) }
                    Rotulo("POTENCIAL POLUIDOR/DEGRADADOR GERAL")
                    EscolhaGrau(potencial) { vm.definirPotencialManual(it) }
                    Aviso(
                        "O potencial geral vem pronto na listagem de atividades da DN 217, coluna " +
                            "\"geral\". Não precisa combinar ar, água e solo à mão.", TipoAviso.INFO
                    )
                } else when (atividade.tipo) {
                    "categorico" -> {
                        Rotulo("CATEGORIA — ${atividade.parametro.uppercase()}")
                        atividade.categorias.forEach { cat ->
                            Cartao {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { vm.calcularPorteCategoria(cat.rotulo) }) {
                                    Text(cat.rotulo, color = Cores.texto, fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.weight(1f))
                                    Text("porte ${cat.porte.extenso}", color = Cores.textoFraco, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    else -> {
                        Rotulo(atividade.parametro.uppercase())
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            if (atividade.unidadeAlternativa != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Unidade:", color = Cores.textoFraco, fontSize = 12.5.sp)
                                    Spacer(Modifier.width(10.dp))
                                    listOf(false, true).forEach { alt ->
                                        val rot = if (alt) atividade.unidadeAlternativa else atividade.unidade
                                        FilterChip(
                                            selected = alternativa == alt,
                                            onClick = {
                                                alternativa = alt
                                                // BUG corrigido: trocar a unidade sem recalcular
                                                // deixava valer o porte da unidade anterior, e ele
                                                // seguia para o resultado e para o PDF sem aviso.
                                                // Passar null quando o campo nao le LIMPA o porte,
                                                // em vez de deixar o anterior de pe.
                                                vm.calcularPorte(Numeros.ler(valor), alt)
                                            },
                                            label = { Text(rot ?: "—", fontSize = 12.sp) },
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            OutlinedTextField(
                                value = valor,
                                onValueChange = { v ->
                                    // Numeros.filtrar aceita digitos e UM separador decimal, e
                                    // recusa separador de milhar. Nao ha heuristica aqui de
                                    // proposito: "1.500" e ambiguo entre mil e quinhentos e um
                                    // e meio, e chutar errado num numero que decide modalidade
                                    // de licenciamento e inaceitavel.
                                    valor = Numeros.filtrar(v)
                                    vm.calcularPorte(Numeros.ler(valor), alternativa)
                                },
                                label = {
                                    Text("Valor em ${if (alternativa) atividade.unidadeAlternativa else atividade.unidade}")
                                },
                                supportingText = {
                                    Text(
                                        "Vírgula para decimal, sem ponto de milhar. " +
                                            "Ex.: 1500000 ou 3,5",
                                        fontSize = 11.sp
                                    )
                                },
                                // KeyboardType.Decimal, nao Number: o teclado Number nao
                                // oferece virgula na maioria dos aparelhos, e era justamente o
                                // separador decimal que o usuario precisava digitar.
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                            // Devolve o numero por extenso: quem digita confere a ordem de
                            // grandeza ANTES de seguir, que e onde o erro de casa decimal
                            // costuma passar despercebido.
                            val lido = Numeros.ler(valor)
                            // Porte inferior nao e erro: e dispensa do art. 10. O cartao fica
                            // ancorado no campo e leva direto ao resultado da dispensa.
                            dispensa?.let { d ->
                                Spacer(Modifier.height(10.dp))
                                Cartao {
                                    Text(
                                        d.titulo,
                                        color = Cores.acento, fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold, lineHeight = 19.sp
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        d.fundamento,
                                        color = Cores.texto, fontSize = 12.sp, lineHeight = 17.sp
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Botao("Ver a dispensa e o que continua obrigatório", principal = true) {
                                        avancarDispensa()
                                    }
                                }
                            }
                            // Lacuna da norma: o valor exato nao cai em faixa nenhuma. Nao ha
                            // botao aqui de proposito — nao existe resultado a mostrar, e
                            // oferecer um seria fingir que a norma decidiu.
                            lacuna?.let { l ->
                                Spacer(Modifier.height(10.dp))
                                Cartao {
                                    Text(
                                        "A DN 217 não define faixa para este valor",
                                        color = Cores.alerta, fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold, lineHeight = 19.sp
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Em ${l.atividade.codigo} as três faixas foram escritas com " +
                                            "desigualdade estrita, então ${l.parametroFormatado()} " +
                                            "não pertence a Pequeno, nem a Médio, nem a Grande. " +
                                            "É lacuna de redação da norma, não erro seu.",
                                        color = Cores.texto, fontSize = 12.sp, lineHeight = 17.sp
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text("AS DUAS LEITURAS POSSÍVEIS",
                                        color = Cores.textoFraco, fontSize = 10.5.sp, letterSpacing = 1.sp)
                                    Spacer(Modifier.height(4.dp))
                                    l.leituras().forEach { leitura ->
                                        Text(
                                            "• Porte ${leitura.porte.extenso.uppercase()} — ${leitura.fundamento}",
                                            color = Cores.texto, fontSize = 12.sp, lineHeight = 17.sp
                                        )
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Aviso(Enquadramento.LacunaDeFaixa.ORIENTACAO, TipoAviso.ATENCAO)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                when {
                                    valor.isBlank() -> "Informe o valor do parâmetro."
                                    lido == null -> "Valor não reconhecido. Use só dígitos e uma vírgula decimal."
                                    else -> "Lido como ${Numeros.porExtenso(lido)} " +
                                        "${if (alternativa) atividade.unidadeAlternativa else atividade.unidade}"
                                },
                                color = if (lido == null && valor.isNotBlank()) Cores.alerta else Cores.textoFraco,
                                fontSize = 12.sp
                            )
                        }
                        Rotulo("FAIXAS DESTA ATIVIDADE")
                        Cartao {
                            val lp = if (alternativa) atividade.limitePAlt else atividade.limiteP
                            val lm = if (alternativa) atividade.limiteMAlt else atividade.limiteM
                            val un = if (alternativa) atividade.unidadeAlternativa else atividade.unidade
                            // As faixas eram exibidas como "Pequeno <= X" / "Medio ate Y" /
                            // "Grande acima de Y". Lido ao pe da letra, "Medio: ate Y" inclui
                            // tudo abaixo de Y e SOBREPOE a faixa Pequeno — e quem usa isto
                            // precisa citar a faixa num parecer. Agora cada faixa mostra piso e
                            // teto, e o `limiteMExclusivo` deixa de ser ignorado.
                            //
                            // nf.format tambem lancava IllegalArgumentException com limite
                            // nulo: uma atividade com unidade alternativa sem limitePAlt fazia
                            // o app fechar ao tocar no chip da unidade.
                            fun num(v: Double?): String = v?.let { nf.format(it) } ?: "—"
                            val abaixoP = if (atividade.limitePExclusivo) "<" else "≤"
                            val acimaP = if (atividade.limitePExclusivo) "a partir de" else "acima de"
                            val abaixoM = if (atividade.limiteMExclusivo) "<" else "≤"
                            val acimaM = if (atividade.limiteMExclusivo) "a partir de" else "acima de"
                            LinhaDado("Pequeno", "$abaixoP ${num(lp)} $un", porte == Grau.P)
                            LinhaDado(
                                "Médio",
                                "$acimaP ${num(lp)} e $abaixoM ${num(lm)} $un",
                                porte == Grau.M
                            )
                            LinhaDado("Grande", "$acimaM ${num(lm)} $un", porte == Grau.G)
                        }
                        // A nota da atividade explica lacuna, sobreposicao ou piso da propria
                        // norma. Vem do texto oficial e precisa aparecer antes de escolher.
                        atividade.nota?.let { Aviso(it, TipoAviso.ATENCAO) }
                    }
                }

                porte?.let {
                    Spacer(Modifier.height(10.dp))
                    Cartao {
                        Text("Porte ${it.extenso.uppercase()}", color = Cores.acento,
                            fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        if (!manual) {
                            Spacer(Modifier.height(4.dp))
                            Text("Potencial poluidor geral ${atividade.pp.geral.extenso} " +
                                "(ar ${atividade.pp.ar}, água ${atividade.pp.agua}, solo ${atividade.pp.solo})",
                                color = Cores.textoFraco, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Botao("Continuar", principal = true,
                        habilitado = porte != null && (!manual || potencial != null)) { avancar() }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun EscolhaGrau(atual: Grau?, aoEscolher: (Grau) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Grau.entries.forEach { g ->
            val sel = atual == g
            Box(
                Modifier.weight(1f).heightIn(min = 52.dp)
                    .background(if (sel) Cores.acento else Cores.superficie, RoundedCornerShape(6.dp))
                    .clickable { aoEscolher(g) }.padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(g.extenso.replaceFirstChar { it.uppercase() },
                    color = if (sel) Color.White else Cores.texto,
                    fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
