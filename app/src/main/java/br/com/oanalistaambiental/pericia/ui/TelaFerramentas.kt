package br.com.oanalistaambiental.pericia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.pericia.geo.Utm

/** Menu de ferramentas — o que existe fora do ato de fotografar. */
@Composable
fun TelaFerramentas(
    vm: CapturaViewModel,
    irParaBussola: () -> Unit,
    irParaConfiguracoes: () -> Unit,
    irParaSessoes: () -> Unit,
    voltar: () -> Unit
) {
    val alvo by vm.alvoRetorno.collectAsState()

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Ferramentas", voltar)
        LazyColumn(Modifier.weight(1f)) {
            item {
                Item("Sessões de vistoria", "Agrupar fotos, exportar laudo, conferir integridade", irParaSessoes)
                Item("Bússola e altímetro", "Leitura de campo sem precisar fotografar", irParaBussola)
                Item(
                    "Completar endereços pendentes",
                    "Usa a conexão atual para resolver os endereços das fotos feitas offline"
                ) { vm.resolverEnderecos() }
                if (alvo != null) {
                    Item(
                        "Cancelar ponto de retorno",
                        "Guia ativo para um registro anterior"
                    ) { vm.definirAlvoRetorno(null) }
                }
                Item("Configurações e pacotes", "Datum, folga de aviso, camadas instaladas", irParaConfiguracoes)
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
 * Util quando o perito precisa da leitura sem produzir registro fotografico — conferir o rumo
 * de uma divisa, anotar a cota de um ponto, orientar uma descricao no laudo.
 */
@Composable
fun TelaBussola(vm: CapturaViewModel, voltar: () -> Unit) {
    val l by vm.estadoCampo.leitura.collectAsState()

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Bússola e altímetro", voltar)
        SeloPrecisao(l)

        Column(
            Modifier.fillMaxWidth().padding(top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(210.dp).background(Cores.superficie, CircleShape)
                )
                Text(
                    "▲",
                    color = Cores.alertaClaro, fontSize = 40.sp,
                    modifier = Modifier.offset(y = (-70).dp).rotate(-(l.azimuteGraus ?: 0f))
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        l.azimuteGraus?.let { "%.0f°".format(it) } ?: "—",
                        color = Cores.texto, fontSize = 44.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        l.azimuteGraus?.let { rosa(it) } ?: "sem bússola",
                        color = Cores.textoFraco, fontSize = 15.sp
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                val lat = l.lat
                val lon = l.lon
                if (lat != null && lon != null) {
                    Campo("UTM SIRGAS 2000", Utm.projetar(lat, lon).formatado())
                    Campo("Geográfica", "%.6f, %.6f".format(lat, lon))
                }
                Campo("Altitude GNSS", l.altitudeM?.let { "%.0f m".format(it) } ?: "—")
                Campo(
                    "Altitude barométrica",
                    l.altitudeBarometricaM?.let { "%.0f m".format(it) } ?: "sem barômetro"
                )
                l.pressaoHpa?.let { Campo("Pressão", "%.1f hPa".format(it)) }
                Campo("Inclinação", l.inclinacaoGraus?.let { "%.0f°".format(it) } ?: "—")
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "A altitude barométrica usa a atmosfera padrão: serve para medir VARIAÇÃO entre " +
                    "dois pontos próximos, não como cota absoluta. Para cota, use a do GNSS e " +
                    "registre a precisão.",
                color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
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
                                "sessões e laudo. Só o alerta de restrição fica desligado.\n\n" +
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
