package br.com.oanalistaambiental.pericia.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import br.com.oanalistaambiental.pericia.captura.Transcricao
import br.com.oanalistaambiental.pericia.dados.FotoOcorrencia
import br.com.oanalistaambiental.pericia.dados.GrupoCanal
import br.com.oanalistaambiental.pericia.dados.ItemCanal
import br.com.oanalistaambiental.pericia.dados.MAXIMO_FOTOS_OCORRENCIA
import br.com.oanalistaambiental.pericia.dados.OcorrenciaAmbiental
import br.com.oanalistaambiental.pericia.geo.Utm
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fmtOcorrencia = SimpleDateFormat("dd/MM/yy HH:mm", Locale("pt", "BR"))

/**
 * Ocorrência ambiental: registrar o que se observou em campo (coordenada + foto + descrição,
 * com transcrição de voz opcional) e, à parte, os canais oficiais de denúncia — o app NUNCA
 * envia nada sozinho, só documenta e mostra para onde essa documentação pode ir.
 *
 * Pensado para quem já usa o kit (analista, consultor, perito), não para o cidadão em geral:
 * é registro técnico de campo, não um portal público de denúncia.
 */
@Composable
fun TelaOcorrenciaAmbiental(vm: CapturaViewModel, voltar: () -> Unit) {
    var aba by rememberSaveable { mutableStateOf(0) }
    var editando by remember { mutableStateOf<OcorrenciaAmbiental?>(null) }

    Column(
        Modifier.fillMaxSize().background(Cores.fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Cabecalho("Ocorrência ambiental", voltar)

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                BotaoLargo(if (editando != null) "Editando" else "Registrar", principal = aba == 0) {
                    aba = 0
                }
            }
            Box(Modifier.weight(1f)) { BotaoLargo("Minhas", principal = aba == 1) { aba = 1; editando = null } }
            Box(Modifier.weight(1f)) { BotaoLargo("Canais oficiais", principal = aba == 2) { aba = 2; editando = null } }
        }

        when (aba) {
            0 -> NovaOcorrencia(vm, editando, aoTerminarEdicao = { editando = null }) { aba = 1 }
            1 -> MinhasOcorrencias(vm, aoEditar = { o -> editando = o; aba = 0 })
            else -> CanaisOficiais(vm)
        }
    }
}

// ---------------------------------------------------------------- registrar

@Composable
private fun ColumnScope.NovaOcorrencia(
    vm: CapturaViewModel,
    editando: OcorrenciaAmbiental?,
    aoTerminarEdicao: () -> Unit,
    aoSalvar: () -> Unit
) {
    val contexto = LocalContext.current
    val p by vm.estadoCampo.posicao.collectAsState()

    // Chave = id da ocorrencia (ou "nova"): trocar QUAL ocorrencia esta sendo editada reseta
    // os campos para o conteudo dela, em vez de continuar com o que sobrou da edicao anterior.
    val chave = editando?.id ?: -1L
    var descricao by rememberSaveable(chave) { mutableStateOf(editando?.descricao ?: "") }
    var transcricao by rememberSaveable(chave) { mutableStateOf(editando?.transcricaoAudio ?: "") }
    val fotosExistentes = remember(chave) {
        mutableStateListOf<FotoOcorrencia>().apply { editando?.fotos?.let(::addAll) }
    }
    val fotosNovas = remember(chave) { mutableStateListOf<File>() }
    val totalFotos = fotosExistentes.size + fotosNovas.size
    var capturandoFoto by remember { mutableStateOf(false) }
    var ditando by remember { mutableStateOf(false) }
    var erroDitado by remember { mutableStateOf<String?>(null) }

    val transcritor = remember(contexto) { Transcricao(contexto) }
    DisposableEffect(Unit) { onDispose { transcritor.parar() } }

    var temPermissaoMic by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val pedirMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        temPermissaoMic = concedida
        if (concedida) {
            ditando = true
            transcritor.iniciar(
                aoAtualizar = { transcricao = it },
                aoErro = { erroDitado = it; ditando = false }
            )
        }
    }

    var temPermissaoCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val pedirCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        temPermissaoCamera = concedida
        if (concedida) capturandoFoto = true
    }

    if (capturandoFoto) {
        CapturaFotoMinima(
            prefixoArquivo = "ocorrencia_temp",
            aoCapturar = { arquivo -> fotosNovas.add(arquivo); capturandoFoto = false },
            aoCancelar = { capturandoFoto = false }
        )
        return
    }

    LazyColumn(Modifier.weight(1f)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Rotulo("COORDENADA")
                if (editando != null) {
                    Mono("%.6f, %.6f".format(Locale.US, editando.lat, editando.lon), Cores.texto, 12)
                    Mono(
                        (editando.precisaoM?.let { "±%.0f m ".format(it) } ?: "") +
                            "no momento do registro — não muda ao editar",
                        Cores.textoFraco, 10
                    )
                } else if (p.temPosicao) {
                    Mono("%.6f, %.6f".format(Locale.US, p.lat, p.lon), Cores.texto, 12)
                    Mono("±%.0f m".format(p.precisaoM ?: 0f), Cores.textoFraco, 10)
                } else {
                    Text("Aguardando GNSS…", color = Cores.atencaoClaro, fontSize = 12.sp)
                }

                Rotulo("FOTOS (até ${MAXIMO_FOTOS_OCORRENCIA})")
                if (totalFotos > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fotosExistentes.forEachIndexed { i, foto ->
                            MiniaturaFoto(
                                arquivo = File(foto.arquivo), descricao = "Foto ${i + 1} da ocorrência",
                                aoRemover = { vm.excluirFotoDaOcorrencia(foto); fotosExistentes.removeAt(i) }
                            )
                        }
                        fotosNovas.forEachIndexed { i, arquivo ->
                            MiniaturaFoto(
                                arquivo = arquivo,
                                descricao = "Foto ${fotosExistentes.size + i + 1} da ocorrência",
                                aoRemover = { fotosNovas.removeAt(i) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (totalFotos < MAXIMO_FOTOS_OCORRENCIA) {
                    BotaoLargo(if (totalFotos == 0) "Tirar foto" else "Tirar outra foto", principal = totalFotos == 0) {
                        if (temPermissaoCamera) capturandoFoto = true else pedirCamera.launch(Manifest.permission.CAMERA)
                    }
                } else {
                    Text(
                        "Limite de ${MAXIMO_FOTOS_OCORRENCIA} fotos atingido.",
                        color = Cores.textoFraco, fontSize = 11.sp
                    )
                }

                Rotulo("DESCRIÇÃO")
                OutlinedTextField(
                    value = descricao, onValueChange = { descricao = it },
                    label = { Text("O que foi observado") },
                    minLines = 3, modifier = Modifier.fillMaxWidth()
                )

                Rotulo("DITAR POR VOZ (OPCIONAL)")
                Text(
                    "Transcrito pelo reconhecimento de voz do Android — pode usar o serviço " +
                        "online do aparelho para melhorar a precisão, o que manda o áudio da " +
                        "fala para fora do aparelho. Revise antes de salvar: transcrição erra.",
                    color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                if (!transcritor.disponivel()) {
                    Text(
                        "Reconhecimento de voz indisponível neste aparelho.",
                        color = Cores.atencaoClaro, fontSize = 12.sp
                    )
                } else {
                    BotaoLargo(if (ditando) "Parar ditado" else "Começar a ditar", principal = ditando) {
                        if (ditando) {
                            transcritor.parar(); ditando = false
                        } else if (temPermissaoMic) {
                            erroDitado = null; ditando = true
                            transcritor.iniciar(
                                aoAtualizar = { transcricao = it },
                                aoErro = { erroDitado = it; ditando = false }
                            )
                        } else {
                            pedirMic.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
                erroDitado?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Cores.alertaClaro, fontSize = 11.5.sp)
                }
                if (transcricao.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = transcricao, onValueChange = { transcricao = it },
                        label = { Text("Transcrição (edite se precisar)") },
                        minLines = 3, modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(20.dp))
                if (editando != null) {
                    BotaoLargo(
                        "Salvar alterações", principal = true,
                        habilitado = descricao.isNotBlank() || transcricao.isNotBlank() || totalFotos > 0
                    ) {
                        vm.atualizarOcorrencia(
                            editando.id, descricao, transcricao, fotosExistentes.size, fotosNovas.toList()
                        )
                        aoTerminarEdicao()
                        aoSalvar()
                    }
                    Spacer(Modifier.height(8.dp))
                    BotaoLargo("Cancelar edição") { aoTerminarEdicao() }
                } else {
                    BotaoLargo(
                        "Salvar ocorrência", principal = true,
                        habilitado = p.temPosicao &&
                            (descricao.isNotBlank() || transcricao.isNotBlank() || totalFotos > 0)
                    ) {
                        vm.salvarOcorrencia(p.lat!!, p.lon!!, p.precisaoM, descricao, transcricao, fotosNovas.toList())
                        descricao = ""; transcricao = ""; fotosNovas.clear()
                        aoSalvar()
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Uma miniatura com um × para remover — mesmo visual pra foto já salva ou recém-tirada. */
@Composable
private fun MiniaturaFoto(arquivo: File, descricao: String, aoRemover: () -> Unit) {
    Box {
        val bitmap = remember(arquivo) {
            runCatching {
                android.graphics.BitmapFactory.decodeFile(arquivo.absolutePath)?.asImageBitmap()
            }.getOrNull()
        }
        if (bitmap != null) {
            Image(
                bitmap, contentDescription = descricao,
                modifier = Modifier.size(72.dp).background(Cores.superficie, RoundedCornerShape(8.dp))
            )
        }
        Text(
            "×", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(3.dp)
                .background(Cores.alertaClaro, RoundedCornerShape(50))
                .clickable { aoRemover() }
                .padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}


// ---------------------------------------------------------------- minhas ocorrencias

@Composable
private fun ColumnScope.MinhasOcorrencias(vm: CapturaViewModel, aoEditar: (OcorrenciaAmbiental) -> Unit) {
    val ocorrencias by vm.ocorrencias.collectAsState()
    var confirmarExclusao by remember { mutableStateOf<Long?>(null) }

    if (ocorrencias.isEmpty()) {
        Vazio(
            "Nenhuma ocorrência registrada",
            "Use a aba \"Registrar\" para documentar o que observou em campo."
        )
        return
    }

    LazyColumn(Modifier.weight(1f)) {
        items(ocorrencias) { o ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(fmtOcorrencia.format(Date(o.instante)), color = Cores.texto, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                val utm = remember(o.lat, o.lon) { Utm.projetar(o.lat, o.lon) }
                Mono(utm.formatado(), Cores.textoFraco, 10)
                o.descricao?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = Cores.texto, fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 3)
                }
                if (o.fotos.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (o.fotos.size == 1) "1 foto" else "${o.fotos.size} fotos",
                        color = Cores.bomClaro, fontSize = Tipos.detalhe
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    AcaoTexto("editar", modifier = Modifier.padding(end = 20.dp, top = 4.dp, bottom = 4.dp)) {
                        aoEditar(o)
                    }
                    AcaoTexto(
                        "compartilhar",
                        modifier = Modifier.padding(end = 20.dp, top = 4.dp, bottom = 4.dp)
                    ) { vm.compartilharOcorrencia(o) }
                    AcaoTexto(
                        if (confirmarExclusao == o.id) "confirmar exclusão?" else "excluir",
                        cor = Cores.alertaClaro,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        if (confirmarExclusao == o.id) { vm.excluirOcorrencia(o); confirmarExclusao = null }
                        else confirmarExclusao = o.id
                    }
                }
            }
            HorizontalDivider(color = Cores.linha)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ---------------------------------------------------------------- canais oficiais

@Composable
private fun ColumnScope.CanaisOficiais(vm: CapturaViewModel) {
    val canais by vm.canaisDenuncia.collectAsState()
    val c = canais
    if (c == null) {
        Vazio("Carregando…", "")
        return
    }

    LazyColumn(Modifier.weight(1f)) {
        item {
            Text(
                c.aviso, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        c.grupos.forEach { grupo ->
            item { GrupoCanalBloco(grupo) }
        }
        item {
            Rotulo(c.uras.titulo.uppercase())
            Text(
                c.uras.descricao, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        items(c.uras.regionais) { r ->
            val contexto = LocalContext.current
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(r.nome, color = Cores.texto, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(r.endereco, color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 15.sp)
                r.telefone?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        it, color = Cores.bomClaro, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { discar(contexto, it) }
                    )
                }
            }
            HorizontalDivider(color = Cores.linha)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun GrupoCanalBloco(grupo: GrupoCanal) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Rotulo(grupo.titulo.uppercase())
        Text(
            grupo.descricao, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        grupo.itens.forEach { item -> ItemCanalLinha(item) }
    }
}

/** Abre o discador com o número já preenchido — nunca liga sozinho, só prepara a tela. */
private fun discar(contexto: Context, telefone: String) {
    val numero = telefone.substringBefore("/").trim().filter { it.isDigit() || it == '+' }
    if (numero.isBlank()) return
    runCatching { contexto.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numero"))) }
}

private fun abrirLink(contexto: Context, url: String) {
    runCatching { contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
private fun ItemCanalLinha(item: ItemCanal) {
    val contexto = LocalContext.current
    CartaoItem {
        Text(item.nome, color = Cores.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        item.detalhe?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 15.sp)
        }
        item.telefone?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it, color = Cores.bomClaro, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { discar(contexto, it) }
            )
        }
        item.horario?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, color = Cores.textoFraco, fontSize = Tipos.detalhe)
        }
        item.endereco?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, color = Cores.textoFraco, fontSize = Tipos.detalhe, lineHeight = 14.sp)
        }
        item.link?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it, color = Cores.bomClaro, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { abrirLink(contexto, it) }
            )
        }
    }
}
