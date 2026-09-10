package br.com.oanalistaambiental.pericia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import br.com.oanalistaambiental.pericia.unidades.CategoriaUnidade
import br.com.oanalistaambiental.pericia.unidades.ConversorUnidades
import br.com.oanalistaambiental.pericia.unidades.UnidadeConversao
import java.text.NumberFormat
import java.util.Locale

private val nf: NumberFormat = NumberFormat.getInstance(Locale("pt", "BR")).apply {
    maximumFractionDigits = 6
}

/**
 * Conversor de vazão, área, volume, massa e taxa de produção — só aritmética de unidade, exata e
 * verificável, nunca interpretação de norma (por isso não precisa da pesquisa de fonte que a
 * curadoria da DN 217 exigiria para uma tabela de porte por unidade).
 */
@Composable
fun TelaConversorUnidades(voltar: () -> Unit) {
    var categoria by rememberSaveable { mutableStateOf(CategoriaUnidade.VAZAO) }
    val unidades = remember(categoria) { ConversorUnidades.unidadesDe(categoria) }
    var unidadeOrigemId by rememberSaveable(categoria) { mutableStateOf(unidades.first().id) }
    var valorTexto by rememberSaveable(categoria) { mutableStateOf("") }

    val origem = unidades.first { it.id == unidadeOrigemId }
    val valor = remember(valorTexto) { valorTexto.replace(',', '.').toDoubleOrNull() }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Conversor de unidades", voltar)

        LazyColumn(Modifier.weight(1f)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Rotulo("CATEGORIA")
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoriaUnidade.entries.forEach { c ->
                            ChipCategoria(c.titulo, categoria == c) { categoria = c }
                        }
                    }

                    Rotulo("VALOR EM ${origem.rotulo.uppercase()}")
                    OutlinedTextField(
                        value = valorTexto, onValueChange = { valorTexto = it },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (valorTexto.isNotBlank() && valor == null) {
                        Spacer(Modifier.height(4.dp))
                        Text("Não reconheci esse número.", color = Cores.atencaoClaro, fontSize = 11.5.sp)
                    }

                    Rotulo("UNIDADE DE ORIGEM")
                    unidades.forEach { u ->
                        OpcaoUnidade(u.rotulo, origem.id == u.id) { unidadeOrigemId = u.id }
                    }

                    Rotulo("CONVERTIDO PARA")
                    if (valor != null) {
                        unidades.filter { it.id != origem.id }.forEach { destino ->
                            val convertido = ConversorUnidades.converter(valor, origem, destino)
                            LinhaConversao(destino.rotulo, nf.format(convertido))
                        }
                    } else {
                        Text(
                            "Informe um valor para ver a conversão.",
                            color = Cores.textoFraco, fontSize = 12.sp
                        )
                    }

                    if (categoria == CategoriaUnidade.TAXA_MASSA) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Mês contado como 30 dias, ano como 365 dias — convenção deste " +
                                "conversor, não descoberta em nenhum documento. Confirme o " +
                                "período exato de qualquer condicionante antes de usar o valor.",
                            color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 15.sp
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ChipCategoria(rotulo: String, selecionado: Boolean, aoEscolher: () -> Unit) {
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
private fun OpcaoUnidade(rotulo: String, selecionado: Boolean, aoEscolher: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selecionado) Cores.bom.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable { aoEscolher() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            rotulo,
            color = if (selecionado) Cores.bomClaro else Cores.texto,
            fontSize = 13.sp, fontWeight = if (selecionado) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun LinhaConversao(rotuloUnidade: String, valorFormatado: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .background(Cores.superficie, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(rotuloUnidade, color = Cores.textoFraco, fontSize = 12.5.sp)
        Spacer(Modifier.weight(1f))
        Text(valorFormatado, color = Cores.texto, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    }
}
