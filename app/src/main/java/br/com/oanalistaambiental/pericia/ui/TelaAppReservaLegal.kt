package br.com.oanalistaambiental.pericia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.geo.AppReservaLegal
import br.com.oanalistaambiental.pericia.geo.AppReservaLegal.RegiaoReservaLegal

/**
 * Faixa de APP (art. 4º) e percentual de Reserva Legal (art. 12), Lei 12.651/2012 — regra GERAL
 * federal, direto do texto oficial. Não decide área consolidada, não confere CAR/PRA, e não
 * substitui regra própria que o órgão estadual tenha fixado para o caso concreto.
 */
@Composable
fun TelaAppReservaLegal(voltar: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("APP e Reserva Legal", voltar)
        LazyColumn(Modifier.weight(1f)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Lei 12.651/2012 (Código Florestal), arts. 4º e 12. É regra geral federal — " +
                            "não decide área consolidada, não confere CAR/PRA, e não substitui regra " +
                            "própria de órgão estadual para o caso concreto.",
                        color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp
                    )

                    BlocoCursoDagua()
                    BlocoLagoLagoa()
                    BlocoReservaLegal()

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun BlocoCursoDagua() {
    var larguraTexto by rememberSaveable { mutableStateOf("") }
    val largura = remember(larguraTexto) { larguraTexto.replace(',', '.').toDoubleOrNull() }

    Rotulo("FAIXA MARGINAL — CURSO D'ÁGUA (ART. 4º, I)")
    OutlinedTextField(
        value = larguraTexto, onValueChange = { larguraTexto = it },
        label = { Text("Largura do curso d'água (m)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
    if (largura != null) {
        Spacer(Modifier.height(8.dp))
        CartaoResultado("Faixa mínima de APP: ${fmt(AppReservaLegal.faixaCursoDaguaM(largura))} m")
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "Vale para curso d'água perene ou intermitente (exclui efêmero), medido da borda da " +
            "calha do leito regular. Nascente e olho d'água perenes têm raio fixo de " +
            "${fmt(AppReservaLegal.RAIO_NASCENTE_OLHO_DAGUA_M)} m, qualquer topografia.",
        color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 15.sp
    )
}

@Composable
private fun BlocoLagoLagoa() {
    var zonaUrbana by rememberSaveable { mutableStateOf(false) }
    var areaTexto by rememberSaveable { mutableStateOf("") }
    val area = remember(areaTexto) { areaTexto.replace(',', '.').toDoubleOrNull() }

    Rotulo("ENTORNO DE LAGO OU LAGOA NATURAL (ART. 4º, II)")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OpcaoZona("Rural", !zonaUrbana) { zonaUrbana = false }
        OpcaoZona("Urbana", zonaUrbana) { zonaUrbana = true }
    }
    if (!zonaUrbana) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = areaTexto, onValueChange = { areaTexto = it },
            label = { Text("Área do corpo d'água (ha) — opcional") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(Modifier.height(8.dp))
    CartaoResultado(
        "Faixa mínima de APP: " +
            "${fmt(AppReservaLegal.faixaLagoLagoaNaturalM(zonaUrbana, area))} m"
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "Vale para lago/lagoa NATURAL — reservatório artificial segue a faixa definida na " +
            "própria licença do empreendimento (art. 4º, III), não esta conta. Corpo d'água com " +
            "menos de 1 ha fica dispensado da faixa (§4º), mas continua vedada nova supressão de " +
            "vegetação nativa ali sem autorização do órgão ambiental.",
        color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 15.sp
    )
}

@Composable
private fun BlocoReservaLegal() {
    var regiao by rememberSaveable { mutableStateOf(RegiaoReservaLegal.DEMAIS_REGIOES) }

    Rotulo("RESERVA LEGAL — % DA ÁREA DO IMÓVEL (ART. 12)")
    RegiaoReservaLegal.entries.forEach { r ->
        OpcaoRegiao(r.titulo, regiao == r) { regiao = r }
    }
    Spacer(Modifier.height(8.dp))
    CartaoResultado("Reserva Legal mínima: ${fmt(AppReservaLegal.percentualReservaLegal(regiao))}%")
    Spacer(Modifier.height(6.dp))
    Text(
        "Minas Gerais não integra a Amazônia Legal — cai sempre em \"demais regiões\", 20%. As " +
            "reduções condicionadas a Zoneamento Ecológico-Econômico e cobertura por UC/terra " +
            "indígena (art. 12, §§4º e 5º) só valem para imóvel na Amazônia Legal em área de " +
            "florestas, e não estão calculadas aqui.",
        color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 15.sp
    )
}

private fun fmt(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

@Composable
private fun CartaoResultado(texto: String) {
    Column(
        Modifier.fillMaxWidth().background(Cores.superficie, RoundedCornerShape(8.dp)).padding(14.dp)
    ) {
        Text(texto, color = Cores.bomClaro, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OpcaoZona(rotulo: String, selecionado: Boolean, aoEscolher: () -> Unit) {
    Box(
        Modifier
            .background(if (selecionado) Cores.bom else Cores.superficie, RoundedCornerShape(6.dp))
            .clickable { aoEscolher() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            rotulo, color = if (selecionado) Color.White else Cores.textoFraco,
            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OpcaoRegiao(rotulo: String, selecionado: Boolean, aoEscolher: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selecionado) Cores.bom.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable { aoEscolher() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            rotulo,
            color = if (selecionado) Cores.bomClaro else Cores.texto,
            fontSize = 12.5.sp, fontWeight = if (selecionado) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
