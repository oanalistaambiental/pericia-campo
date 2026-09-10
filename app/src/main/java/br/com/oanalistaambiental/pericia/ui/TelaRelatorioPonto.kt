package br.com.oanalistaambiental.pericia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.geo.Restricao
import br.com.oanalistaambiental.pericia.geo.Situacao

/**
 * "Relatório do ponto": tudo que o app já sabe sobre a coordenada atual, numa tela só — a bacia/
 * CH, e TODA camada do pacote de restrição, inclusive as que o ponto está longe (fora). É a
 * consulta explícita e completa, diferente do alerta que aparece ao fotografar (que esconde
 * "fora" de propósito, para não poluir a câmera) — aqui a pessoa pediu, então fora também é
 * resposta, não ruído.
 */
@Composable
fun TelaRelatorioPonto(vm: CapturaViewModel, voltar: () -> Unit) {
    val p by vm.estadoCampo.posicao.collectAsState()
    val bacia by vm.bacia.collectAsState()
    val relatorio by vm.relatorioPonto.collectAsState()
    val consultando by vm.consultandoRelatorio.collectAsState()

    LaunchedEffect(p.lat, p.lon) {
        val lat = p.lat
        val lon = p.lon
        if (lat != null && lon != null) {
            vm.consultarBaciaHidrografica(lat, lon)
            vm.consultarRelatorioPonto(lat, lon, p.precisaoM ?: 999f)
        }
    }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Relatório do ponto", voltar)
        SeloPrecisao(p)

        if (p.lat == null) {
            Vazio(
                "Sem coordenada",
                "Aguarde o GNSS fixar para consultar tudo o que se sabe deste ponto."
            )
            return@Column
        }

        LazyColumn(Modifier.weight(1f)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Rotulo("BACIA / CIRCUNSCRIÇÃO HIDROGRÁFICA")
                    val b = bacia
                    if (b == null) {
                        Text(
                            "Fora de qualquer CH mapeada, ou ainda consultando.",
                            color = Cores.textoFraco, fontSize = 12.sp
                        )
                    } else {
                        Text(
                            "${b.sigla} — ${b.nome.substringAfter(": ").ifBlank { b.nome }}",
                            color = Cores.bomClaro, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                        )
                    }

                    Rotulo("CAMADAS DE RESTRIÇÃO E VEDAÇÃO")
                    when {
                        consultando && relatorio == null -> Text(
                            "Consultando todas as camadas…", color = Cores.textoFraco, fontSize = 12.sp
                        )
                        relatorio.isNullOrEmpty() -> Text(
                            "Nenhuma camada respondeu — pacote de camadas não instalado, ou " +
                                "nenhuma camada cobre esta região.",
                            color = Cores.textoFraco, fontSize = 12.sp, lineHeight = 16.sp
                        )
                        else -> relatorio!!.forEach { r -> LinhaRestricaoRelatorio(r) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaRestricaoRelatorio(r: Restricao) {
    val cor = when (r.situacao) {
        Situacao.DENTRO -> Cores.alerta
        Situacao.PROXIMO_AO_LIMITE -> Cores.atencao
        Situacao.FORA -> Cores.neutro
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .background(cor, RoundedCornerShape(6.dp)).padding(10.dp)
    ) {
        Text(r.frase(), color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, lineHeight = 16.sp)
        Mono(
            "${r.fonte} · pacote ${r.proveniencia.pacoteVersao} · extraído ${r.proveniencia.dataExtracao}",
            androidx.compose.ui.graphics.Color(0xB3FFFFFF), 9
        )
    }
}
