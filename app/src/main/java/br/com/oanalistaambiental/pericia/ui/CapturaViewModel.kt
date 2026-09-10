package br.com.oanalistaambiental.pericia.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.oanalistaambiental.pericia.captura.Enderecos
import br.com.oanalistaambiental.pericia.captura.EstadoCampo
import br.com.oanalistaambiental.pericia.carimbo.CarimboTempo
import br.com.oanalistaambiental.pericia.carimbo.ClienteTsa
import br.com.oanalistaambiental.pericia.captura.ConferenciaSessao
import br.com.oanalistaambiental.pericia.captura.Integridade
import br.com.oanalistaambiental.pericia.captura.Legenda
import br.com.oanalistaambiental.pericia.captura.PosicaoMarcaDagua
import br.com.oanalistaambiental.pericia.captura.ProvaFoto
import br.com.oanalistaambiental.pericia.captura.SalvarGaleria
import br.com.oanalistaambiental.pericia.dados.AudioGravado
import br.com.oanalistaambiental.pericia.dados.Banco
import br.com.oanalistaambiental.pericia.dados.CadastrosIef
import br.com.oanalistaambiental.pericia.dados.CadastrosIefCarregador
import br.com.oanalistaambiental.pericia.dados.CanaisDenuncia
import br.com.oanalistaambiental.pericia.dados.CanaisDenunciaCarregador
import br.com.oanalistaambiental.pericia.dados.Condicionante
import br.com.oanalistaambiental.pericia.dados.Foto
import br.com.oanalistaambiental.pericia.dados.FotoOcorrencia
import br.com.oanalistaambiental.pericia.dados.MAXIMO_FOTOS_OCORRENCIA
import br.com.oanalistaambiental.pericia.dados.OcorrenciaAmbiental
import br.com.oanalistaambiental.pericia.dados.PontoCaminhamento
import br.com.oanalistaambiental.pericia.dados.RegistroCaptacao
import br.com.oanalistaambiental.pericia.dados.RegistroFicha
import br.com.oanalistaambiental.pericia.dados.RegistroRestricao
import br.com.oanalistaambiental.pericia.fichas.CatalogoFichas
import br.com.oanalistaambiental.pericia.fichas.ModeloFicha
import br.com.oanalistaambiental.pericia.fichas.Resposta
import br.com.oanalistaambiental.pericia.fichas.serializarRespostas
import br.com.oanalistaambiental.pericia.dados.PontoSalvo
import br.com.oanalistaambiental.pericia.dados.Sessao
import br.com.oanalistaambiental.pericia.dados.TiposOcorrencia
import br.com.oanalistaambiental.pericia.taxas.TabelaTaxas
import br.com.oanalistaambiental.pericia.taxas.TaxaUfemg
import br.com.oanalistaambiental.pericia.exportacao.Exportador
import br.com.oanalistaambiental.pericia.geo.CamadaInfo
import br.com.oanalistaambiental.pericia.geo.FormatoCoordenada
import br.com.oanalistaambiental.pericia.geo.Caminhamento
import br.com.oanalistaambiental.pericia.geo.CircunscricaoHidrografica
import br.com.oanalistaambiental.pericia.geo.ConsultaOnline
import br.com.oanalistaambiental.pericia.geo.Medicao
import br.com.oanalistaambiental.pericia.geo.ConsultaRestricao
import br.com.oanalistaambiental.pericia.geo.PontoConsulta
import br.com.oanalistaambiental.pericia.geo.PontoRetorno
import br.com.oanalistaambiental.pericia.geo.Restricao
import br.com.oanalistaambiental.pericia.laudo.LaudoPdf
import br.com.oanalistaambiental.pericia.lembretes.LembreteCondicionante
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Date

class CapturaViewModel(app: Application) : AndroidViewModel(app) {

    val banco = Banco(app)
    val estadoCampo = EstadoCampo(app)

    private val _sessaoAtual = MutableStateFlow<Sessao?>(null)
    val sessaoAtual: StateFlow<Sessao?> = _sessaoAtual

    private val _sessoes = MutableStateFlow<List<Sessao>>(emptyList())
    val sessoes: StateFlow<List<Sessao>> = _sessoes

    private val _ultimasRestricoes = MutableStateFlow<List<Restricao>>(emptyList())
    val ultimasRestricoes: StateFlow<List<Restricao>> = _ultimasRestricoes

    /**
     * Diferente de [ultimasRestricoes] (que só guarda alerta — DENTRO/PRÓXIMO, para não poluir
     * a câmera): aqui entra TODA camada do pacote, inclusive as que o ponto está longe (FORA) —
     * é o "relatório do ponto" pedido, uma resposta completa sobre o que já se sabe do lugar,
     * não só o que precisa de atenção.
     */
    private val _relatorioPonto = MutableStateFlow<List<Restricao>?>(null)
    val relatorioPonto: StateFlow<List<Restricao>?> = _relatorioPonto
    private val _consultandoRelatorio = MutableStateFlow(false)
    val consultandoRelatorio: StateFlow<Boolean> = _consultandoRelatorio

    // ---- modo vistoria: caminhamento ----

    private val _caminhamentoAtivo = MutableStateFlow(false)
    val caminhamentoAtivo: StateFlow<Boolean> = _caminhamentoAtivo

    private val _pontosCaminhamento = MutableStateFlow<List<PontoCaminhamento>>(emptyList())
    val pontosCaminhamento: StateFlow<List<PontoCaminhamento>> = _pontosCaminhamento

    private var jobCaminhamento: Job? = null

    // ---- modo vistoria: audio ----

    private val _gravandoAudio = MutableStateFlow(false)
    val gravandoAudio: StateFlow<Boolean> = _gravandoAudio

    private val _duracaoAudioSegundos = MutableStateFlow(0)
    val duracaoAudioSegundos: StateFlow<Int> = _duracaoAudioSegundos

    private val _audiosDaSessao = MutableStateFlow<List<AudioGravado>>(emptyList())
    val audiosDaSessao: StateFlow<List<AudioGravado>> = _audiosDaSessao

    private var gravador: MediaRecorder? = null
    private var arquivoAudioAtual: File? = null
    private var instanteInicioAudio: Long = 0
    private var jobTiqueAudio: Job? = null

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem

    /** Dado, nao codigo (ver `dados/Modelos.kt`): comeca com o padrao embutido, ate o asset carregar. */
    private val _tiposOcorrencia = MutableStateFlow(TiposOcorrencia.padrao)
    val tiposOcorrencia: StateFlow<List<String>> = _tiposOcorrencia

    private val _tabelaTaxas = MutableStateFlow<TabelaTaxas?>(null)
    val tabelaTaxas: StateFlow<TabelaTaxas?> = _tabelaTaxas

    private val _cadastrosIef = MutableStateFlow<CadastrosIef?>(null)
    val cadastrosIef: StateFlow<CadastrosIef?> = _cadastrosIef

    private val _canaisDenuncia = MutableStateFlow<CanaisDenuncia?>(null)
    val canaisDenuncia: StateFlow<CanaisDenuncia?> = _canaisDenuncia

    private val _ocorrencias = MutableStateFlow<List<OcorrenciaAmbiental>>(emptyList())
    val ocorrencias: StateFlow<List<OcorrenciaAmbiental>> = _ocorrencias

    private val _registrosCaptacao = MutableStateFlow<List<RegistroCaptacao>>(emptyList())
    val registrosCaptacao: StateFlow<List<RegistroCaptacao>> = _registrosCaptacao

    private val _modelosFicha = MutableStateFlow<List<ModeloFicha>>(emptyList())
    val modelosFicha: StateFlow<List<ModeloFicha>> = _modelosFicha

    private val _registrosFicha = MutableStateFlow<List<RegistroFicha>>(emptyList())
    val registrosFicha: StateFlow<List<RegistroFicha>> = _registrosFicha

    private val _condicionantes = MutableStateFlow<List<Condicionante>>(emptyList())
    val condicionantes: StateFlow<List<Condicionante>> = _condicionantes

    /**
     * Marca d'água (brasão do órgão, logo da consultoria) queimada no canto da CÓPIA com
     * legenda — nunca no original, mesma regra de sempre. Guardada como um arquivo fixo em
     * armazenamento interno do app, não como bytes no banco: é uma imagem, não um dado de
     * registro, e um arquivo é trivial de reler a cada foto sem inchar o SQLite.
     */
    private fun arquivoMarcaDagua(): File = File(getApplication<Application>().filesDir, "marca_dagua.png")

    private val _temMarcaDagua = MutableStateFlow(false)
    val temMarcaDagua: StateFlow<Boolean> = _temMarcaDagua

    /**
     * Sobe a cada troca efetiva do arquivo (definir ou remover) — ao contrário de
     * [temMarcaDagua], que fica em `true` sem mudar quando uma marca substitui outra. A tela
     * usa isto como chave de `remember` para reler a prévia do disco só depois que a ESCRITA
     * termina, nunca antes.
     */
    private val _versaoMarcaDagua = MutableStateFlow(0)
    val versaoMarcaDagua: StateFlow<Int> = _versaoMarcaDagua

    /**
     * Decodifica de novo e regrava como PNG: aceita qualquer formato comum na entrada (JPG,
     * PNG, WEBP — o que o BitmapFactory já lê), sai sempre em PNG para preservar transparência
     * quando o arquivo original já tiver (um brasão recortado, por exemplo).
     */
    fun definirMarcaDagua(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val ctx = getApplication<Application>()
                val bitmap = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: throw IllegalStateException("não consegui ler essa imagem")
                // Teto de tamanho generoso: e so um logo, nao precisa do arquivo original inteiro
                // de uma foto de 12 MP — mas tambem nao ha por que reduzir demais na entrada,
                // Legenda.desenharMarcaDagua ja redimensiona para cada foto na hora de desenhar.
                val maior = maxOf(bitmap.width, bitmap.height)
                val fator = if (maior > 1200) 1200f / maior else 1f
                val final = if (fator < 1f) {
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * fator).toInt(), (bitmap.height * fator).toInt(), true)
                        .also { if (it !== bitmap) bitmap.recycle() }
                } else bitmap
                FileOutputStream(arquivoMarcaDagua()).use { final.compress(Bitmap.CompressFormat.PNG, 100, it) }
                final.recycle()
            }.onSuccess {
                _temMarcaDagua.value = true
                _versaoMarcaDagua.value++
                _mensagem.value = "Marca d'água definida — entra nas próximas fotos."
            }.onFailure { _mensagem.value = "Falha ao definir marca d'água: ${it.message}" }
        }
    }

    fun removerMarcaDagua() {
        runCatching { arquivoMarcaDagua().delete() }
        _temMarcaDagua.value = false
        _versaoMarcaDagua.value++
    }

    private val _fotosDaSessao = MutableStateFlow<List<Foto>>(emptyList())
    val fotosDaSessao: StateFlow<List<Foto>> = _fotosDaSessao

    /**
     * BUG corrigido: as telas consultavam o banco dentro da composicao, na thread da UI —
     * ate 2 consultas por foto a cada recomposicao. Agora o mapa e montado uma vez em
     * background, e a tela so le memoria.
     */
    private val _restricoesPorFoto = MutableStateFlow<Map<Long, List<RegistroRestricao>>>(emptyMap())
    val restricoesPorFoto: StateFlow<Map<Long, List<RegistroRestricao>>> = _restricoesPorFoto

    private val _camadas = MutableStateFlow<List<CamadaInfo>>(emptyList())
    val camadas: StateFlow<List<CamadaInfo>> = _camadas

    private val _versaoPacote = MutableStateFlow<String?>(null)
    val versaoPacote: StateFlow<String?> = _versaoPacote

    // ------------------------------------------------------------------ preferencias

    /**
     * `SharedPreferences` direto, sem Banco: sao dois booleanos de interface, nao dado de
     * pericia. Guardar em tabela seria peso morto para o que e.
     */
    private val prefs = getApplication<Application>()
        .getSharedPreferences("pericia_prefs", android.content.Context.MODE_PRIVATE)

    /** Fonte maior e contraste no maximo nos numeros grandes — leitura ao sol do meio-dia. */
    private val _modoSolForte = MutableStateFlow(prefs.getBoolean("modo_sol_forte", false))
    val modoSolForte: StateFlow<Boolean> = _modoSolForte

    fun alternarModoSolForte() {
        val novo = !_modoSolForte.value
        _modoSolForte.value = novo
        prefs.edit().putBoolean("modo_sol_forte", novo).apply()
    }

    /**
     * GNSS e bussola atualizam mais devagar — mais bateria numa vistoria longa em area rural.
     * Precisa reiniciar `estadoCampo` para o novo intervalo valer (ver [EstadoCampo.iniciar]).
     */
    private val _economiaDeBateria = MutableStateFlow(prefs.getBoolean("economia_bateria", false))
    val economiaDeBateria: StateFlow<Boolean> = _economiaDeBateria

    fun alternarEconomiaDeBateria() {
        val novo = !_economiaDeBateria.value
        _economiaDeBateria.value = novo
        prefs.edit().putBoolean("economia_bateria", novo).apply()
        estadoCampo.economiaDeBateria = novo
        estadoCampo.parar()
        estadoCampo.iniciar()
    }

    /** Canto onde a marca d'água entra — mesma escolha vale para a prévia ao vivo da câmera. */
    private val _posicaoMarcaDagua = MutableStateFlow(
        runCatching { PosicaoMarcaDagua.valueOf(prefs.getString("posicao_marca_dagua", null) ?: "") }
            .getOrDefault(PosicaoMarcaDagua.SUPERIOR_DIREITA)
    )
    val posicaoMarcaDagua: StateFlow<PosicaoMarcaDagua> = _posicaoMarcaDagua

    fun definirPosicaoMarcaDagua(posicao: PosicaoMarcaDagua) {
        _posicaoMarcaDagua.value = posicao
        prefs.edit().putString("posicao_marca_dagua", posicao.name).apply()
    }

    /** 0f = totalmente transparente, 1f = sólida. Vale tanto para a prévia ao vivo quanto para a foto final. */
    private val _opacidadeMarcaDagua = MutableStateFlow(prefs.getFloat("opacidade_marca_dagua", 0.78f))
    val opacidadeMarcaDagua: StateFlow<Float> = _opacidadeMarcaDagua

    fun definirOpacidadeMarcaDagua(opacidade: Float) {
        val v = opacidade.coerceIn(0.05f, 1f)
        _opacidadeMarcaDagua.value = v
        prefs.edit().putFloat("opacidade_marca_dagua", v).apply()
    }

    /** Qual formato de coordenada entra primeiro na legenda da foto e nas telas do app. */
    private val _formatoCoordenada = MutableStateFlow(
        runCatching { FormatoCoordenada.valueOf(prefs.getString("formato_coordenada", null) ?: "") }
            .getOrDefault(FormatoCoordenada.UTM)
    )
    val formatoCoordenada: StateFlow<FormatoCoordenada> = _formatoCoordenada

    fun definirFormatoCoordenada(formato: FormatoCoordenada) {
        _formatoCoordenada.value = formato
        prefs.edit().putString("formato_coordenada", formato.name).apply()
    }

    /**
     * Para onde o app esta guiando. Nasceu como "voltar aquela foto" e virou generico, porque
     * a outra metade do trabalho e chegar a uma coordenada que veio de fora: auto de infracao,
     * planta, memorial descritivo. So o rotulo e o enquadramento mudam.
     */
    data class Alvo(
        val lat: Double,
        val lon: Double,
        /** Enquadramento a reproduzir. Nulo quando o alvo e so um ponto a alcancar. */
        val azimuteGraus: Float?,
        val rotulo: String,
        val fotoId: Long? = null
    )

    private val _alvo = MutableStateFlow<Alvo?>(null)
    val alvo: StateFlow<Alvo?> = _alvo

    /** Instrucao de caminhada ate o alvo. Nome distinto do fluxo de sensor, de proposito. */
    private val _guia = MutableStateFlow<PontoRetorno.Orientacao?>(null)
    val guia: StateFlow<PontoRetorno.Orientacao?> = _guia

    // ---- medicao de area por caminhamento ----

    private val _vertices = MutableStateFlow<List<Medicao.Vertice>>(emptyList())
    val vertices: StateFlow<List<Medicao.Vertice>> = _vertices

    private val _poligono = MutableStateFlow<Medicao.Poligono?>(null)
    val poligono: StateFlow<Medicao.Poligono?> = _poligono

    /**
     * BUG corrigido: `tipoOcorrencia` e `observacao` eram campos comuns (`var`). O Compose nao
     * observa campo comum: o perito escolhia o tipo no formulario, o valor era guardado, mas a
     * tela continuava mostrando "definir" e o campo de observacao voltava vazio. Parecia que o
     * app nao aceitava informacao nenhuma. Agora sao StateFlow e a tela reage.
     */
    private val _tipoOcorrencia = MutableStateFlow<String?>(null)
    val tipoOcorrencia: StateFlow<String?> = _tipoOcorrencia

    private val _observacao = MutableStateFlow("")
    val observacao: StateFlow<String> = _observacao

    fun definirTipoOcorrencia(tipo: String?) { _tipoOcorrencia.value = tipo }

    fun definirObservacao(texto: String) { _observacao.value = texto }

    /** Aviso curto na barra inferior. Usado tambem pela tela de camera. */
    fun avisar(texto: String) { _mensagem.value = texto }

    /** BUG corrigido: uma instancia reaproveitada, em vez de reabrir o GeoPackage a cada foto. */
    private var consulta: ConsultaRestricao? = null

    /**
     * BUG corrigido: o app procurava o pacote no armazenamento INTERNO, invisivel ao usuario,
     * enquanto a tela de configuracoes e o script mandavam copiar para o EXTERNO. O alerta
     * locacional — o coracao do produto — ficaria desligado para sempre, e em silencio.
     */
    private val arquivoPacote: File
        get() = File(getApplication<Application>().getExternalFilesDir(null), "pacotes/mg-base.gpkg")

    /**
     * Copia um `.gpkg` de `assets/pacotes/<nome>` para o armazenamento interno, uma unica vez —
     * SQLite precisa de um caminho de arquivo de verdade, nao da de abrir direto de dentro do
     * APK. Usada pelos tres pacotes embarcados: o real (`base-real.gpkg`), o de circunscricoes
     * hidrograficas e o de exemplo ficticio.
     */
    private fun copiarAssetPacote(nome: String): File {
        val destino = File(getApplication<Application>().filesDir, "pacotes/$nome")
        if (!destino.exists()) {
            destino.parentFile?.mkdirs()
            getApplication<Application>().assets.open("pacotes/$nome").use { entrada ->
                destino.outputStream().use { saida -> entrada.copyTo(saida) }
            }
        }
        return destino
    }

    // ------------------------------------------------------------------ circunscricao hidrografica

    /**
     * Copia unica do pacote de Circunscricoes Hidrograficas (dado real do IGAM), do mesmo jeito
     * que [copiarExemploSeNecessario] copia o pacote de exemplo — sempre embarcado, nunca
     * opcional, porque cobre o estado inteiro e nao depende de instalar nada em campo.
     */
    // ------------------------------------------------------------------ pontos avulsos

    private val _pontosSalvos = MutableStateFlow<List<PontoSalvo>>(emptyList())
    val pontosSalvos: StateFlow<List<PontoSalvo>> = _pontosSalvos

    private fun recarregarPontos() {
        viewModelScope.launch(Dispatchers.IO) { _pontosSalvos.value = banco.pontosSalvos() }
    }

    /** Marca e guarda a posicao ATUAL do GNSS (ou uma coordenada ja lida, ex.: a digitada em
     * "ir para uma coordenada") — nao presa a foto nem a um caminhamento de medicao. */
    fun salvarPonto(nome: String, lat: Double, lon: Double, precisaoM: Float?) {
        viewModelScope.launch(Dispatchers.IO) {
            val rotulo = nome.ifBlank {
                "Ponto " + java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("pt", "BR"))
                    .format(java.util.Date())
            }
            banco.salvarPonto(PontoSalvo(nome = rotulo, lat = lat, lon = lon, precisaoM = precisaoM, instante = System.currentTimeMillis()))
            recarregarPontos()
            _mensagem.value = "Ponto salvo: $rotulo"
        }
    }

    fun excluirPonto(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { banco.excluirPonto(id); recarregarPontos() }
    }

    fun exportarPontos(formato: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val pontos = _pontosSalvos.value
            if (pontos.isEmpty()) { _mensagem.value = "Nenhum ponto salvo para exportar."; return@launch }
            val pasta = File(ctx.filesDir, "pontos").apply { mkdirs() }
            val base = "pontos-salvos-${System.currentTimeMillis()}"
            runCatching {
                val arquivo: File = when (formato) {
                    "gpx" -> Exportador.gpxPontos(pontos, File(pasta, "$base.gpx"))
                    "kml" -> Exportador.kmlPontos(pontos, File(pasta, "$base.kml"))
                    "csv" -> Exportador.csvPontos(pontos, File(pasta, "$base.csv"))
                    else -> throw IllegalArgumentException("Formato desconhecido: $formato")
                }
                withContext(Dispatchers.Main) {
                    Exportador.compartilhar(ctx, listOf(arquivo), "Pontos salvos")
                }
            }.onFailure { _mensagem.value = "Falha ao exportar: ${it.message}" }
        }
    }

    // ------------------------------------------------------------------ ocorrencia ambiental

    /**
     * Cada FOTO e copiada para a pasta propria da ocorrencia e recebe hash — mesma ideia de
     * proveniencia da camera de pericia, sem o aparato inteiro de sessao/legenda: aqui o
     * registro e mais leve, pensado para documentar rapido e decidir depois para onde levar.
     */
    fun salvarOcorrencia(
        lat: Double, lon: Double, precisaoM: Float?, descricao: String,
        transcricaoAudio: String?, fotosOriginais: List<File>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val fotos = copiarFotosOcorrencia(fotosOriginais, MAXIMO_FOTOS_OCORRENCIA)
                banco.inserirOcorrencia(
                    OcorrenciaAmbiental(
                        lat = lat, lon = lon, precisaoM = precisaoM, instante = System.currentTimeMillis(),
                        descricao = descricao.ifBlank { null },
                        transcricaoAudio = transcricaoAudio?.ifBlank { null },
                        fotos = fotos
                    )
                )
            }.onSuccess {
                _ocorrencias.value = banco.ocorrencias()
                _mensagem.value = "Ocorrência registrada."
            }.onFailure { _mensagem.value = "Falha ao salvar ocorrência: ${it.message}" }
        }
    }

    /**
     * Atualiza descrição/transcrição e acrescenta fotos novas de uma ocorrência já salva.
     * NUNCA mexe em lat/lon/instante — o que foi observado e quando não é editável depois,
     * só o relato em volta disso.
     */
    fun atualizarOcorrencia(
        id: Long, descricao: String, transcricaoAudio: String?,
        quantasFotosJaTem: Int, fotosNovas: List<File>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val fotos = copiarFotosOcorrencia(fotosNovas, MAXIMO_FOTOS_OCORRENCIA - quantasFotosJaTem)
                banco.atualizarOcorrencia(
                    id, descricao.ifBlank { null }, transcricaoAudio?.ifBlank { null }, fotos
                )
            }.onSuccess {
                _ocorrencias.value = banco.ocorrencias()
                _mensagem.value = "Ocorrência atualizada."
            }.onFailure { _mensagem.value = "Falha ao atualizar ocorrência: ${it.message}" }
        }
    }

    fun excluirFotoDaOcorrencia(foto: FotoOcorrencia) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { File(foto.arquivo).delete() }
            banco.excluirFotoOcorrencia(foto.id)
            _ocorrencias.value = banco.ocorrencias()
        }
    }

    fun excluirOcorrencia(o: OcorrenciaAmbiental) {
        viewModelScope.launch(Dispatchers.IO) {
            o.fotos.forEach { runCatching { File(it.arquivo).delete() } }
            banco.excluirOcorrencia(o.id)
            _ocorrencias.value = banco.ocorrencias()
        }
    }

    /**
     * Grava um registro de campo de captação de água — foto (quando houver) com hash, mesma
     * ideia de proveniência das outras fichas de campo. A classificação e a base legal vêm já
     * calculadas de [br.com.oanalistaambiental.pericia.geo.UsoInsignificante] no momento do
     * registro — gravadas como texto, não recalculadas depois: o limiar pode mudar de norma no
     * futuro, e o registro precisa continuar dizendo o que valia quando foi feito.
     */
    fun salvarRegistroCaptacao(
        lat: Double, lon: Double, precisaoM: Float?, tipoCaptacao: String,
        vazaoOuVolume: Double?, unidade: String, comBomba: Boolean?,
        classificacao: String, baseLegal: String, fotoOriginal: File?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                var fotoArquivo: String? = null
                var fotoSha256: String? = null
                if (fotoOriginal != null && fotoOriginal.exists()) {
                    val pasta = File(getApplication<Application>().filesDir, "captacoes").apply { mkdirs() }
                    val destino = File(pasta, "captacao-${System.currentTimeMillis()}.jpg")
                    fotoOriginal.copyTo(destino, overwrite = true)
                    fotoArquivo = destino.absolutePath
                    fotoSha256 = Integridade.sha256(destino)
                }
                banco.inserirRegistroCaptacao(
                    RegistroCaptacao(
                        lat = lat, lon = lon, precisaoM = precisaoM, instante = System.currentTimeMillis(),
                        tipoCaptacao = tipoCaptacao, vazaoOuVolume = vazaoOuVolume, unidade = unidade,
                        comBomba = comBomba, fotoArquivo = fotoArquivo, fotoSha256 = fotoSha256,
                        classificacao = classificacao, baseLegal = baseLegal
                    )
                )
            }.onSuccess {
                _registrosCaptacao.value = banco.registrosCaptacao()
                _mensagem.value = "Registro de captação salvo."
            }.onFailure { _mensagem.value = "Falha ao salvar registro: ${it.message}" }
        }
    }

    fun excluirRegistroCaptacao(r: RegistroCaptacao) {
        viewModelScope.launch(Dispatchers.IO) {
            r.fotoArquivo?.let { runCatching { File(it).delete() } }
            banco.excluirRegistroCaptacao(r.id)
            _registrosCaptacao.value = banco.registrosCaptacao()
        }
    }

    /** Grava uma ficha de vistoria preenchida, com a coordenada de quem a preencheu. */
    fun salvarRegistroFicha(
        modelo: ModeloFicha, respostas: List<Resposta>,
        lat: Double, lon: Double, precisaoM: Float?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                banco.inserirRegistroFicha(
                    RegistroFicha(
                        modeloId = modelo.id, modeloNome = modelo.nome,
                        lat = lat, lon = lon, precisaoM = precisaoM,
                        instante = System.currentTimeMillis(),
                        respostasJson = serializarRespostas(respostas)
                    )
                )
            }.onSuccess {
                _registrosFicha.value = banco.registrosFicha()
                _mensagem.value = "Ficha de vistoria salva."
            }.onFailure { _mensagem.value = "Falha ao salvar ficha: ${it.message}" }
        }
    }

    fun excluirRegistroFicha(r: RegistroFicha) {
        viewModelScope.launch(Dispatchers.IO) {
            banco.excluirRegistroFicha(r.id)
            _registrosFicha.value = banco.registrosFicha()
        }
    }

    /** Grava uma condicionante com prazo, opcionalmente com a foto do parecer que a originou. */
    fun salvarCondicionante(
        descricao: String, formaCumprimento: String?, prazoData: Long, fotoOriginal: File?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                var fotoArquivo: String? = null
                var fotoSha256: String? = null
                if (fotoOriginal != null && fotoOriginal.exists()) {
                    val pasta = File(getApplication<Application>().filesDir, "condicionantes").apply { mkdirs() }
                    val destino = File(pasta, "parecer-${System.currentTimeMillis()}.jpg")
                    fotoOriginal.copyTo(destino, overwrite = true)
                    fotoArquivo = destino.absolutePath
                    fotoSha256 = Integridade.sha256(destino)
                }
                val condicionante = Condicionante(
                    descricao = descricao, formaCumprimento = formaCumprimento,
                    prazoData = prazoData, criadaEm = System.currentTimeMillis(),
                    fotoArquivo = fotoArquivo, fotoSha256 = fotoSha256
                )
                val id = banco.inserirCondicionante(condicionante)
                LembreteCondicionante.agendar(getApplication<Application>(), condicionante.copy(id = id))
            }.onSuccess {
                _condicionantes.value = banco.condicionantes()
                _mensagem.value = "Condicionante salva."
            }.onFailure { _mensagem.value = "Falha ao salvar condicionante: ${it.message}" }
        }
    }

    fun marcarCondicionanteCumprida(c: Condicionante, cumprida: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            banco.marcarCondicionanteCumprida(c.id, cumprida)
            if (cumprida) LembreteCondicionante.cancelar(getApplication<Application>(), c.id)
            else LembreteCondicionante.agendar(getApplication<Application>(), c.copy(cumprida = false))
            _condicionantes.value = banco.condicionantes()
        }
    }

    fun excluirCondicionante(c: Condicionante) {
        viewModelScope.launch(Dispatchers.IO) {
            c.fotoArquivo?.let { runCatching { File(it).delete() } }
            banco.excluirCondicionante(c.id)
            LembreteCondicionante.cancelar(getApplication<Application>(), c.id)
            _condicionantes.value = banco.condicionantes()
        }
    }

    /** Copia até [limite] arquivos para a pasta própria da ocorrência e devolve com hash — usado ao criar e ao editar. */
    private fun copiarFotosOcorrencia(originais: List<File>, limite: Int): List<FotoOcorrencia> {
        if (limite <= 0) return emptyList()
        val pasta = File(getApplication<Application>().filesDir, "ocorrencias").apply { mkdirs() }
        return originais.take(limite).filter { it.exists() }.mapIndexed { i, original ->
            val destino = File(pasta, "ocorrencia-${System.currentTimeMillis()}-$i.jpg")
            original.copyTo(destino, overwrite = true)
            FotoOcorrencia(ocorrenciaId = 0, arquivo = destino.absolutePath, sha256 = Integridade.sha256(destino))
        }
    }

    /**
     * Compartilha o resumo em texto (coordenada, descrição, transcrição, hash de cada foto) +
     * uma CÓPIA de cada foto com a legenda queimada (coordenada, data, hash) — não a original
     * crua. É a mesma regra da câmera de perícia: quem recebe o arquivo por fora do app precisa
     * conseguir ler a informação sem abrir mais nada.
     *
     * BUG corrigido: o resumo ia como um .txt ANEXO junto das fotos — o WhatsApp (e outros apps
     * de mensagem) costuma ignorar silenciosamente um anexo que não seja imagem quando envia
     * várias fotos de uma vez, e a pessoa recebia só as fotos, sem coordenada nem descrição
     * nenhuma. Agora o texto vai em EXTRA_TEXT (parâmetro `corpo` de [Exportador.compartilhar]),
     * que chega independente de anexo.
     */
    fun compartilharOcorrencia(o: OcorrenciaAmbiental) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val resumo = Exportador.resumoOcorrencia(o)
                val marca = arquivoMarcaDagua().takeIf { it.exists() }
                val fotosComLegenda = o.fotos.mapNotNull { f ->
                    val original = File(f.arquivo)
                    if (!original.exists()) return@mapNotNull null
                    runCatching {
                        val destino = File(original.parentFile, original.nameWithoutExtension + "_legenda.jpg")
                        val fotoSintetica = Foto(
                            sessaoId = 0, arquivoOriginal = f.arquivo, arquivoComLegenda = null,
                            sha256 = f.sha256, lat = o.lat, lon = o.lon, precisaoM = o.precisaoM ?: 999f,
                            altitudeM = null, azimuteGraus = null, inclinacaoGraus = null,
                            instante = o.instante, tipoOcorrencia = null, observacao = o.descricao
                        )
                        Legenda.gerar(
                            original, destino, fotoSintetica, "OCORRÊNCIA AMBIENTAL", marca,
                            _posicaoMarcaDagua.value, _opacidadeMarcaDagua.value, _formatoCoordenada.value
                        )
                    }.getOrElse { original }
                }
                withContext(Dispatchers.Main) {
                    Exportador.compartilhar(
                        getApplication(), fotosComLegenda, "Ocorrência ambiental registrada", resumo
                    )
                }
            }.onFailure { _mensagem.value = "Falha ao compartilhar: ${it.message}" }
        }
    }

    /** Exporta o polígono medido por caminhamento — GPX (rota), KML (área) ou CSV. */
    fun exportarMedicao(formato: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val pol = _poligono.value
            if (pol == null || pol.vertices.isEmpty()) {
                _mensagem.value = "Nenhum vértice marcado para exportar."
                return@launch
            }
            val pasta = File(ctx.filesDir, "medicoes").apply { mkdirs() }
            val base = "medicao-${System.currentTimeMillis()}"
            runCatching {
                val arquivo: File = when (formato) {
                    "gpx" -> Exportador.gpxMedicao(pol, File(pasta, "$base.gpx"))
                    "kml" -> Exportador.kmlMedicao(pol, File(pasta, "$base.kml"))
                    "csv" -> Exportador.csvMedicao(pol, File(pasta, "$base.csv"))
                    else -> throw IllegalArgumentException("Formato desconhecido: $formato")
                }
                withContext(Dispatchers.Main) {
                    Exportador.compartilhar(ctx, listOf(arquivo), "Medição de área")
                }
            }.onFailure { _mensagem.value = "Falha ao exportar: ${it.message}" }
        }
    }

    private val _bacia = MutableStateFlow<CircunscricaoHidrografica.Info?>(null)
    val bacia: StateFlow<CircunscricaoHidrografica.Info?> = _bacia

    private val _consultandoBacia = MutableStateFlow(false)
    val consultandoBacia: StateFlow<Boolean> = _consultandoBacia

    fun consultarBaciaHidrografica(lat: Double, lon: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            _consultandoBacia.value = true
            _bacia.value = runCatching {
                CircunscricaoHidrografica.localizar(
                    copiarAssetPacote("circunscricoes-hidrograficas.gpkg"), lat, lon
                )
            }.getOrNull()
            _consultandoBacia.value = false
        }
    }

    init {
        recarregar()
        // O GNSS NAO e iniciado aqui: sem permissao a chamada e recusada em silencio e nada
        // religa depois. Quem inicia e a MainActivity, assim que a permissao existe.
        estadoCampo.economiaDeBateria = _economiaDeBateria.value
        abrirPacote()
        observarPosicaoParaRetorno()
        recarregarPontos()
        viewModelScope.launch(Dispatchers.IO) {
            _tiposOcorrencia.value = TiposOcorrencia.carregar {
                getApplication<Application>().assets.open("tipos_ocorrencia.json")
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                TaxaUfemg.carregar { getApplication<Application>().assets.open("taxas/ufemg_2026.json") }
            }.onSuccess { _tabelaTaxas.value = it }
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                CadastrosIefCarregador.carregar { getApplication<Application>().assets.open("ief/cadastros.json") }
            }.onSuccess { _cadastrosIef.value = it }
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                CanaisDenunciaCarregador.carregar { getApplication<Application>().assets.open("ocorrencia/canais.json") }
            }.onSuccess { _canaisDenuncia.value = it }
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                CatalogoFichas.carregar { getApplication<Application>().assets.open("fichas/fichas.json") }
            }.onSuccess { _modelosFicha.value = it }
        }
        viewModelScope.launch(Dispatchers.IO) { _ocorrencias.value = banco.ocorrencias() }
        viewModelScope.launch(Dispatchers.IO) { _registrosCaptacao.value = banco.registrosCaptacao() }
        viewModelScope.launch(Dispatchers.IO) { _registrosFicha.value = banco.registrosFicha() }
        viewModelScope.launch(Dispatchers.IO) { _condicionantes.value = banco.condicionantes() }
        _temMarcaDagua.value = arquivoMarcaDagua().exists()
    }

    override fun onCleared() {
        estadoCampo.parar()
        runCatching { consulta?.close() }
        jobCaminhamento?.cancel()
        jobTiqueAudio?.cancel()
        runCatching { gravador?.stop() }
        runCatching { gravador?.release() }
        super.onCleared()
    }

    // ------------------------------------------------------------------ pacote

    /**
     * Recarrega o pacote de camadas.
     *
     * Publica de proposito: alem da chamada em `init`, a tela de configuracoes pode chamar de
     * novo depois que o perito copia `mg-base.gpkg` para o aparelho. Sem isso, quem instalasse
     * o pacote real com o app ja aberto so veria o efeito depois de forcar o app a fechar — o
     * pacote so era lido uma vez, na criacao do ViewModel.
     */
    fun abrirPacote() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { consulta?.close() }
            runCatching {
                // Ordem: pacote oficial instalado (montar-pacote.sh) > base real embarcada
                // (leve, sempre no APK) > exemplo ficticio (so se ate a base real falhar).
                val arquivo = when {
                    arquivoPacote.exists() -> arquivoPacote
                    else -> runCatching { copiarAssetPacote("base-real.gpkg") }
                        .getOrElse { copiarAssetPacote("exemplo.gpkg") }
                }
                val c = ConsultaRestricao.abrir(arquivo)
                consulta = c
                _camadas.value = c.camadasInstaladas()
                _versaoPacote.value = c.versaoDoPacote()
            }.onFailure {
                _mensagem.value = "Pacote de camadas ilegível: ${it.message}"
            }
        }
    }

    fun pastaDePacotes(): File =
        File(getApplication<Application>().getExternalFilesDir(null), "pacotes").apply { mkdirs() }

    /**
     * Backup completo (banco + fotos + áudio + registros) num arquivo .zip escolhido pela
     * própria pessoa — Storage Access Framework, então pode ir para qualquer lugar que o
     * aparelho enxergue (armazenamento local, um app de nuvem, um pendrive OTG). Não tenta
     * mandar para lugar nenhum sozinho, mesma regra de sempre.
     */
    fun criarBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val ctx = getApplication<Application>()
                val saida = ctx.contentResolver.openOutputStream(uri)
                    ?: error("Não foi possível abrir o arquivo de destino.")
                saida.use { Exportador.criarBackup(ctx, it) }
            }.onSuccess {
                _mensagem.value = "Backup criado."
            }.onFailure {
                _mensagem.value = "Falha ao criar backup: ${it.message}"
            }
        }
    }

    // ------------------------------------------------------------------ sessoes

    fun recarregar() {
        viewModelScope.launch(Dispatchers.IO) {
            val lista = banco.sessoes()
            _sessoes.value = lista
            if (_sessaoAtual.value == null) _sessaoAtual.value = lista.firstOrNull { it.fechadaEm == null }
            _sessaoAtual.value?.let {
                resgatarFotosOrfas(it.id)
                carregarFotos(it.id)
            }
        }
    }

    /**
     * Recupera foto que existe no disco mas nao tem linha no banco.
     *
     * COMO A FOTO SUMIA. O CameraX grava o JPEG, o clarao branco confirma para o perito que
     * deu certo, e so entao uma corrotina calcula o SHA-256 — segundos, num sensor de 50 MP —
     * e insere no banco. Se o sistema matar o app nesse intervalo (memoria baixa, ou o proprio
     * perito fechando pelo botao de recentes), o arquivo fica em disco e o registro nunca
     * nasce. A foto nao aparece na sessao, nao entra no laudo, nao entra no Merkle, nao entra
     * no CSV — e nada no app jamais a procurava de novo. Ela estava la, e para o app nao
     * existia.
     *
     * O resgate roda ao abrir o app. A foto volta sem coordenada: a leitura de GNSS daquele
     * instante se perdeu junto com o processo, e inventar posicao seria pior que nao ter.
     */
    private fun resgatarFotosOrfas(sessaoId: Long) {
        runCatching {
            val pasta = pastaDaSessao(sessaoId)
            val registradas = banco.fotosDaSessao(sessaoId).map { it.arquivoOriginal }.toSet()
            val orfas = pasta.listFiles { f ->
                f.isFile && f.name.endsWith(".jpg") && !f.name.endsWith("_legenda.jpg") &&
                    f.absolutePath !in registradas
            }?.sortedBy { it.lastModified() } ?: return

            for (arq in orfas) {
                banco.inserirFoto(
                    Foto(
                        sessaoId = sessaoId,
                        arquivoOriginal = arq.absolutePath,
                        arquivoComLegenda = null,
                        sha256 = Integridade.sha256(arq),
                        lat = 0.0, lon = 0.0, precisaoM = 999f, altitudeM = null,
                        azimuteGraus = null, inclinacaoGraus = null,
                        instante = arq.lastModified(),
                        idadeFixSegundos = null,
                        tipoOcorrencia = null,
                        observacao = "Registro recuperado: o aplicativo foi encerrado antes de " +
                            "gravar os dados desta foto. A coordenada daquele instante se perdeu."
                    )
                )
            }
            if (orfas.isNotEmpty()) {
                _mensagem.value = "${orfas.size} foto(s) recuperada(s) do disco, sem coordenada — " +
                    "o app tinha sido encerrado antes de registrá-las."
            }
        }
    }

    fun novaSessao(titulo: String, processo: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = banco.criarSessao(titulo, processo)
            _sessoes.value = banco.sessoes()
            _sessaoAtual.value = _sessoes.value.firstOrNull { it.id == id }
            pastaDaSessao(id)
            _fotosDaSessao.value = emptyList()
            _restricoesPorFoto.value = emptyMap()
        }
    }

    fun selecionarSessao(s: Sessao) {
        _sessaoAtual.value = s
        viewModelScope.launch(Dispatchers.IO) { pastaDaSessao(s.id) }
        carregarFotos(s.id)
    }

    /** BUG corrigido: a lista de fotos era lida na thread principal dentro do composable. */
    fun carregarFotos(sessaoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val fotos = banco.fotosDaSessao(sessaoId)
            _fotosDaSessao.value = fotos
            _restricoesPorFoto.value = fotos.associate { it.id to banco.restricoesDaFoto(it.id) }
        }
    }

    fun pastaDaSessao(sessaoId: Long): File =
        File(getApplication<Application>().filesDir, "sessoes/$sessaoId").apply { mkdirs() }

    // ------------------------------------------------------------------ modo vistoria: caminhamento

    /** Recarrega os pontos do caminhamento já gravados desta sessão — chamar ao abrir a tela. */
    fun carregarCaminhamento(sessaoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _pontosCaminhamento.value = banco.pontosCaminhamento(sessaoId)
        }
    }

    /**
     * Marca UM ponto por leitura de GNSS, não um rastro contínuo — mesma lógica da medição de
     * área: leitura contínua a 1 Hz por uma vistoria de horas encheria o banco de ruído sem
     * melhorar o traçado. O intervalo aqui é por TEMPO (a cada [INTERVALO_CAMINHAMENTO_MS]),
     * não por toque, porque ninguém vai parar e tocar a cada passo durante horas de campo — é
     * exatamente a diferença de proposta entre as duas ferramentas.
     */
    fun iniciarCaminhamento(sessaoId: Long) {
        if (_caminhamentoAtivo.value) {
            _mensagem.value = "Já existe um caminhamento em andamento — talvez de outra sessão. Pare antes de iniciar outro."
            return
        }
        _caminhamentoAtivo.value = true
        jobCaminhamento = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val p = estadoCampo.posicao.value
                if (p.temPosicao) {
                    val ponto = PontoCaminhamento(
                        sessaoId = sessaoId, lat = p.lat!!, lon = p.lon!!,
                        precisaoM = p.precisaoM ?: 999f, instante = System.currentTimeMillis()
                    )
                    banco.inserirPontoCaminhamento(ponto)
                    _pontosCaminhamento.value = _pontosCaminhamento.value + ponto
                }
                kotlinx.coroutines.delay(INTERVALO_CAMINHAMENTO_MS)
            }
        }
    }

    fun pararCaminhamento() {
        jobCaminhamento?.cancel()
        jobCaminhamento = null
        _caminhamentoAtivo.value = false
    }

    fun exportarCaminhamento(formato: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val pontos = _pontosCaminhamento.value
            if (pontos.isEmpty()) { _mensagem.value = "Nenhum ponto de caminhamento para exportar."; return@launch }
            val pasta = File(ctx.filesDir, "caminhamentos").apply { mkdirs() }
            val base = "caminhamento-${System.currentTimeMillis()}"
            runCatching {
                val arquivo: File = when (formato) {
                    "gpx" -> Exportador.gpxCaminhamento(pontos, File(pasta, "$base.gpx"))
                    "kml" -> Exportador.kmlCaminhamento(pontos, File(pasta, "$base.kml"))
                    "csv" -> Exportador.csvCaminhamento(pontos, File(pasta, "$base.csv"))
                    else -> throw IllegalArgumentException("Formato desconhecido: $formato")
                }
                withContext(Dispatchers.Main) {
                    Exportador.compartilhar(ctx, listOf(arquivo), "Caminhamento da vistoria")
                }
            }.onFailure { _mensagem.value = "Falha ao exportar: ${it.message}" }
        }
    }

    // ------------------------------------------------------------------ modo vistoria: audio

    /** Recarrega os áudios já gravados desta sessão — chamar ao abrir a tela. */
    fun carregarAudios(sessaoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _audiosDaSessao.value = banco.audiosDaSessao(sessaoId)
        }
    }

    /**
     * Grava em M4A (AAC), no mesmo padrão de qualidade de voz de um gravador comum — não é
     * áudio de estúdio, é prova de campo. Para sozinha em 1 hora
     * ([DURACAO_MAXIMA_AUDIO_MS]) e SALVA o que já gravou até lá — nunca descarta silenciosamente
     * por estourar o teto.
     *
     * NÃO faz: transcrição nem resumo automático. O áudio fica íntegro, com hash calculado ao
     * parar — é matéria-prima para o parecer, não o parecer pronto.
     */
    fun iniciarGravacaoAudio(sessaoId: Long) {
        if (_gravandoAudio.value) {
            _mensagem.value = "Já existe uma gravação em andamento — talvez de outra sessão. Pare antes de iniciar outra."
            return
        }
        val pasta = File(pastaDaSessao(sessaoId), "audio").apply { mkdirs() }
        val arquivo = File(pasta, "audio-${System.currentTimeMillis()}.m4a")
        val rec = criarMediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setMaxDuration(DURACAO_MAXIMA_AUDIO_MS)
            setOutputFile(arquivo.absolutePath)
            setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    _mensagem.value = "Gravação atingiu 1 hora e foi salva automaticamente."
                    pararGravacaoAudio(sessaoId)
                }
            }
            try {
                prepare()
                start()
            } catch (e: Exception) {
                _mensagem.value = "Falha ao iniciar a gravação: ${e.message}"
                runCatching { release() }
                return
            }
        }
        gravador = rec
        arquivoAudioAtual = arquivo
        instanteInicioAudio = System.currentTimeMillis()
        _gravandoAudio.value = true
        _duracaoAudioSegundos.value = 0
        jobTiqueAudio = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                kotlinx.coroutines.delay(1_000)
                _duracaoAudioSegundos.value = ((System.currentTimeMillis() - instanteInicioAudio) / 1000).toInt()
            }
        }
    }

    fun pararGravacaoAudio(sessaoId: Long) {
        val rec = gravador ?: return
        val arquivo = arquivoAudioAtual
        jobTiqueAudio?.cancel()
        jobTiqueAudio = null
        val duracaoS = ((System.currentTimeMillis() - instanteInicioAudio) / 1000).toInt()
        runCatching { rec.stop() }
        runCatching { rec.release() }
        gravador = null
        _gravandoAudio.value = false

        if (arquivo != null && arquivo.exists() && duracaoS > 0) {
            viewModelScope.launch(Dispatchers.IO) {
                val hash = Integridade.sha256(arquivo)
                val audio = AudioGravado(
                    sessaoId = sessaoId, arquivo = arquivo.absolutePath, duracaoSegundos = duracaoS,
                    instanteInicio = instanteInicioAudio, sha256 = hash
                )
                banco.inserirAudio(audio)
                _audiosDaSessao.value = banco.audiosDaSessao(sessaoId)
            }
        }
        arquivoAudioAtual = null
    }

    fun excluirAudio(sessaoId: Long, audio: AudioGravado) {
        runCatching { File(audio.arquivo).delete() }
        banco.excluirAudio(audio.id)
        _audiosDaSessao.value = _audiosDaSessao.value.filterNot { it.id == audio.id }
    }

    fun compartilharAudio(audio: AudioGravado) {
        val arquivo = File(audio.arquivo)
        if (!arquivo.exists()) { _mensagem.value = "Arquivo de áudio não encontrado no aparelho."; return }
        runCatching {
            Exportador.compartilhar(getApplication(), listOf(arquivo), "Áudio da vistoria")
        }.onFailure { _mensagem.value = "Falha ao compartilhar: ${it.message}" }
    }

    @Suppress("DEPRECATION")
    private fun criarMediaRecorder(): MediaRecorder {
        val ctx = getApplication<Application>()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            MediaRecorder(ctx)
        } else {
            MediaRecorder()
        }
    }

    // ------------------------------------------------------------------ captura

    fun registrarCaptura(original: File) {
        val sessao = _sessaoAtual.value ?: run {
            _mensagem.value = "Crie ou selecione uma sessão antes de fotografar."
            return
        }
        val leitura = estadoCampo.leituraAtual()
        val pos = leitura.posicao

        // REGRA DURA: coordenada vencida NAO entra na prova.
        //
        // O erro que isto impede: o perito pega o fix de +-4 m na entrada da area, o sinal cai
        // debaixo da mata, ele caminha 600 m ate o ponto do dano e fotografa. Antes, a foto
        // saia com a coordenada da entrada, a hora de agora e "+-4 m" — tres afirmacoes
        // coerentes entre si e todas erradas. Preferimos foto sem coordenada, que e honesta e
        // recuperavel, a foto com coordenada falsa, que nao e nem uma coisa nem outra.
        val posValida = pos.temPosicao && !pos.vencida
        val idadeFix = if (posValida) pos.idadeSegundos() else null
        _mensagem.value = when {
            !pos.temPosicao ->
                "Sem posição GNSS: a foto foi guardada, mas sem coordenada."
            pos.vencida ->
                "Sinal de GNSS parado há ${pos.idadeSegundos()} s — foto guardada SEM coordenada. " +
                    "Espere o selo voltar ao verde e refaça o registro deste ponto."
            else -> null
        }
        _ultimasRestricoes.value = emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            // CAMINHO CRITICO — nesta ordem, sempre: grava, hasheia, registra.
            val hash = Integridade.sha256(original)
            val foto = Foto(
                sessaoId = sessao.id,
                arquivoOriginal = original.absolutePath,
                arquivoComLegenda = null,
                sha256 = hash,
                lat = if (posValida) pos.lat ?: 0.0 else 0.0,
                lon = if (posValida) pos.lon ?: 0.0 else 0.0,
                precisaoM = if (posValida) pos.precisaoM ?: 999f else 999f,
                altitudeM = if (posValida) pos.altitudeM else null,
                azimuteGraus = leitura.orientacao.azimuteGraus,
                inclinacaoGraus = leitura.orientacao.elevacaoGraus,
                instante = System.currentTimeMillis(),
                idadeFixSegundos = idadeFix,
                tipoOcorrencia = _tipoOcorrencia.value,
                observacao = _observacao.value.ifBlank { null }
            )
            val fotoId = banco.inserirFoto(foto)
            val comId = foto.copy(id = fotoId)

            // Fora do caminho critico: copia com legenda.
            runCatching {
                val destino = File(original.parentFile, original.nameWithoutExtension + "_legenda.jpg")
                val marca = arquivoMarcaDagua().takeIf { it.exists() }
                Legenda.gerar(
                    original, destino, comId, sessao.titulo, marca,
                    _posicaoMarcaDagua.value, _opacidadeMarcaDagua.value, _formatoCoordenada.value
                )
                // BUG corrigido: o caminho da copia nunca era gravado, e o laudo usava o original.
                banco.atualizarLegenda(fotoId, destino.absolutePath)
                // A copia (com legenda e marca d'agua) vai tambem para a galeria publica do
                // aparelho — o ORIGINAL, que tem o hash, nunca sai da pasta interna do app.
                // Pedido de Francisco: achar a foto so pela galeria, sem precisar exportar.
                SalvarGaleria.salvar(getApplication(), destino, "Perícia Campo")
            }.onFailure { _mensagem.value = "Legenda não gerada: ${it.message}" }

            if (posValida) consultarRestricoes(fotoId, comId)
            carregarFotos(sessao.id)
            _sessoes.value = banco.sessoes()
        }
    }

    private suspend fun consultarRestricoes(fotoId: Long, foto: Foto): Unit = withContext(Dispatchers.IO) {
        val c = consulta ?: run {
            _mensagem.value = "Pacote de camadas não instalado — alerta locacional indisponível."
            return@withContext
        }
        runCatching {
            c.consultar(PontoConsulta(foto.lat, foto.lon, foto.precisaoM, foto.instante))
        }.onSuccess { lista ->
            _ultimasRestricoes.value = lista
            lista.forEach { r ->
                banco.inserirRestricao(
                    RegistroRestricao(
                        fotoId = fotoId, camada = r.camadaNome, fonte = r.fonte,
                        situacao = r.situacao.name, distanciaM = r.distanciaBordaM,
                        atributos = r.atributos.entries.joinToString("; ") { "${it.key}=${it.value}" },
                        pacoteVersao = r.proveniencia.pacoteVersao,
                        uuidMetadado = r.proveniencia.uuidMetadado,
                        dataExtracao = r.proveniencia.dataExtracao,
                        toleranciaM = r.proveniencia.toleranciaSimplificacaoM
                    )
                )
            }
            // Camada que existe no pacote mas nao pode ser lida NAO pode passar por "nada
            // aqui". O perito precisa saber que aquela camada especifica ficou sem resposta,
            // senao ele le a ausencia de alerta como ausencia de restricao.
            val falhas = c.falhasDaUltimaConsulta()
            if (falhas.isNotEmpty()) {
                _mensagem.value = "${falhas.size} camada(s) sem leitura nesta consulta " +
                    "(não é 'sem restrição') — veja Configurações → Camadas."
            }
        }.onFailure { _mensagem.value = "Consulta de restrição falhou: ${it.message}" }

        consultarRestricoesOnline(fotoId, foto)
    }

    /**
     * Complemento AO VIVO da consulta offline — ver [ConsultaOnline]. Roda depois e a parte,
     * nunca atrasa nem substitui o resultado offline: sem sinal (o caso normal em campo), esta
     * funcao simplesmente nao acrescenta nada, em silencio.
     */
    private suspend fun consultarRestricoesOnline(fotoId: Long, foto: Foto): Unit = withContext(Dispatchers.IO) {
        val online = runCatching {
            ConsultaOnline.consultar(foto.lat, foto.lon, foto.precisaoM)
        }.getOrNull() ?: return@withContext
        if (online.isEmpty()) return@withContext

        _ultimasRestricoes.value = _ultimasRestricoes.value + online
        online.forEach { r ->
            banco.inserirRestricao(
                RegistroRestricao(
                    fotoId = fotoId, camada = r.camadaNome, fonte = r.fonte,
                    situacao = r.situacao.name, distanciaM = r.distanciaBordaM,
                    atributos = r.atributos.entries.joinToString("; ") { "${it.key}=${it.value}" },
                    pacoteVersao = r.proveniencia.pacoteVersao,
                    uuidMetadado = r.proveniencia.uuidMetadado,
                    dataExtracao = r.proveniencia.dataExtracao,
                    toleranciaM = r.proveniencia.toleranciaSimplificacaoM
                )
            )
        }
    }

    /**
     * "Relatório do ponto" — TODA camada do pacote offline contra a coordenada atual, incluindo
     * as que o ponto está longe (FORA), mais o que o servidor online souber a mais. Diferente da
     * consulta que roda ao fotografar: aqui a pessoa pediu explicitamente uma resposta completa,
     * então "fora" também é informação, não ruído a esconder.
     */
    fun consultarRelatorioPonto(lat: Double, lon: Double, precisaoM: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            _consultandoRelatorio.value = true
            val ponto = PontoConsulta(lat, lon, precisaoM, System.currentTimeMillis())
            val offline = runCatching {
                consulta?.consultar(ponto, incluirFora = true) ?: emptyList()
            }.getOrDefault(emptyList())
            val online = runCatching { ConsultaOnline.consultar(lat, lon, precisaoM) }.getOrDefault(emptyList())
            _relatorioPonto.value = (offline + online).sortedBy { it.distanciaBordaM }
            _consultandoRelatorio.value = false
        }
    }

    // ------------------------------------------------------------ ponto de retorno

    fun definirAlvoRetorno(foto: Foto?) {
        _alvo.value = foto?.let {
            Alvo(it.lat, it.lon, it.azimuteGraus, "Repetir registro #${it.id}", it.id)
        }
    }

    fun definirAlvoCoordenada(lat: Double, lon: Double, rotulo: String) {
        _alvo.value = Alvo(lat, lon, null, rotulo)
    }

    fun limparAlvo() { _alvo.value = null }

    /**
     * DESEMPENHO: combina os dois fluxos em vez de assinar um objeto gigante. A conta so roda
     * quando posicao ou rumo mudam de verdade, e nao a cada tremida do aparelho.
     */
    private fun observarPosicaoParaRetorno() {
        viewModelScope.launch {
            combine(estadoCampo.posicao, estadoCampo.orientacao) { p, o -> p to o }
                .collect { (p, o) ->
                    val alvo = _alvo.value
                    val lat = p.lat
                    val lon = p.lon
                    _guia.value = if (alvo != null && lat != null && lon != null) {
                        PontoRetorno.orientar(
                            alvo.lat, alvo.lon, alvo.azimuteGraus,
                            lat, lon, p.precisaoM ?: 99f, o.azimuteGraus
                        )
                    } else null
                }
        }
    }

    // ------------------------------------------------------------------ medicao de area

    /**
     * Marca um vertice do caminhamento na posicao atual.
     *
     * Marcar manualmente, e nao gravar rastro continuo, e deliberado: o perito anda o
     * perimetro parando nos cantos, que e como se levanta uma area em campo. Rastro continuo
     * enche o poligono de ruido de GNSS e infla o perimetro.
     */
    fun marcarVertice() {
        val p = estadoCampo.posicao.value
        val lat = p.lat
        val lon = p.lon
        if (lat == null || lon == null) {
            _mensagem.value = "Sem posição: aguarde o GNSS antes de marcar o vértice."
            return
        }
        if (p.aproximada) {
            _mensagem.value = "Posição ainda vem da rede. Aguarde o GNSS para medir área."
            return
        }
        // Sem esta guarda, um perimetro inteiro podia ser marcado sobre o mesmo ponto
        // congelado: o perito anda, toca "marcar" em cada canto, e todos os vertices caem no
        // mesmo lugar. Sai area zero, ou uma figura sem sentido, sem aviso nenhum.
        if (p.vencida) {
            _mensagem.value =
                "Sinal parado há ${p.idadeSegundos()} s — vértice não marcado. " +
                    "Sem GNSS ao vivo, todos os cantos cairiam no mesmo ponto."
            return
        }
        val novo = _vertices.value + Medicao.Vertice(lat, lon, p.precisaoM ?: 99f, System.currentTimeMillis())
        _vertices.value = novo
        _poligono.value = Medicao.medir(novo)
    }

    fun desfazerVertice() {
        val atual = _vertices.value
        if (atual.isEmpty()) return
        val novo = atual.dropLast(1)
        _vertices.value = novo
        _poligono.value = if (novo.isEmpty()) null else Medicao.medir(novo)
    }

    fun limparMedicao() {
        _vertices.value = emptyList()
        _poligono.value = null
    }

    /**
     * Leva o resultado da medicao para a observacao das proximas fotos — assim a area entra na
     * legenda queimada, no laudo e no CSV, em vez de morrer numa tela que ninguem exporta.
     */
    fun usarMedicaoComoObservacao() {
        val pol = _poligono.value
        if (pol == null || pol.vertices.size < 3) {
            _mensagem.value = "Marque pelo menos três vértices antes de usar a medição."
            return
        }
        val texto = "Área medida por caminhamento: ${pol.areaFormatada()} ${pol.incertezaFormatada()}" +
            " (perímetro ${pol.perimetroFormatado()}, ${pol.vertices.size} vértices)"
        definirObservacao(texto)
        _mensagem.value = "Medição copiada para a observação das próximas fotos."
    }

    // ------------------------------------------------------------------ sessao/selo

    fun fecharSessao(sessao: Sessao) {
        viewModelScope.launch(Dispatchers.IO) {
            val hashes = banco.fotosDaSessao(sessao.id).map { it.sha256 }
            if (hashes.isEmpty()) { _mensagem.value = "Sessão sem fotos."; return@launch }
            banco.fecharSessao(sessao.id, Integridade.raizMerkle(hashes))
            _sessoes.value = banco.sessoes()
            _sessaoAtual.value = banco.sessao(sessao.id)
            _mensagem.value = "Sessão fechada e integridade selada."
        }
    }

    fun conferirIntegridade(sessaoId: Long, aoTerminar: (List<Integridade.Conferencia>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val fotos = banco.fotosDaSessao(sessaoId)
            val r = Integridade.conferir(fotos.map { it.arquivoOriginal to it.sha256 })
            withContext(Dispatchers.Main) { aoTerminar(r) }
        }
    }

    /**
     * Conferencia COMPLETA: arquivo, hash e arvore.
     *
     * A conferencia acima responde so "o arquivo mudou?". Esta responde tambem "este registro
     * pertence ao conjunto que foi selado?" — a pergunta que pega foto acrescentada ao banco
     * depois do fechamento da sessao, caso em que todos os arquivos batem com seus hashes e
     * mesmo assim o conjunto nao e o que foi carimbado.
     *
     * A ordem das folhas vem de `fotosDaSessao`, que ordena por `instante, id`. Essa ordem NAO
     * pode mudar entre o fechamento e a conferencia: a arvore depende dela.
     */
    fun conferirSessaoCompleta(sessaoId: Long, aoTerminar: (ConferenciaSessao.Resultado) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val sessao = banco.sessao(sessaoId)
            val fotos = banco.fotosDaSessao(sessaoId)
            val folhas = fotos.mapIndexed { i, f ->
                ConferenciaSessao.Folha(
                    arquivo = f.arquivoOriginal,
                    hashGravado = f.sha256,
                    rotulo = "Registro ${i + 1}"
                )
            }
            val r = ConferenciaSessao.conferir(sessao?.raizMerkle, folhas)
            withContext(Dispatchers.Main) { aoTerminar(r) }
        }
    }

    // ------------------------------------------------------------------ exportacao

    fun exportar(sessao: Sessao, formato: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val fotos = banco.fotosDaSessao(sessao.id)
            if (fotos.isEmpty()) { _mensagem.value = "Sessão sem fotos para exportar."; return@launch }
            val pasta = pastaDaSessao(sessao.id)
            val base = "sessao-${sessao.id}"
            runCatching {
                val arquivo: File = when (formato) {
                    "pdf" -> LaudoPdf.gerar(banco, sessao, fotos, File(pasta, "$base-laudo.pdf"))
                    "csv" -> Exportador.csv(banco, fotos, File(pasta, "$base-metadados.csv"))
                    "kmz" -> Exportador.kmz(banco, sessao, fotos, File(pasta, "$base.kmz"))
                    "gpx" -> Exportador.gpx(fotos, File(pasta, "$base.gpx"))
                    else -> throw IllegalArgumentException("Formato desconhecido: $formato")
                }
                withContext(Dispatchers.Main) {
                    Exportador.compartilhar(ctx, listOf(arquivo), sessao.titulo)
                }
                _mensagem.value = "Gerado: ${arquivo.name}"
            }.onFailure { _mensagem.value = "Falha ao exportar: ${it.message}" }
        }
    }

    // ------------------------------------------------------------- carimbo do tempo

    /**
     * Endereco da Autoridade de Carimbo do Tempo, e a declaracao de credenciamento.
     *
     * Fica em StateFlow porque a tela precisa mostrar QUAL autoridade sera usada e se ela foi
     * marcada como credenciada — um carimbo cuja origem o usuario nao ve na hora de pedir e
     * um carimbo que ele nao consegue defender depois.
     */
    private val _tsaUrl = MutableStateFlow(ClienteTsa.URL_PADRAO)
    val tsaUrl: StateFlow<String> = _tsaUrl

    private val _tsaCredenciada = MutableStateFlow(ClienteTsa.PADRAO_E_CREDENCIADA)
    val tsaCredenciada: StateFlow<Boolean> = _tsaCredenciada

    fun configurarTsa(url: String, credenciada: Boolean) {
        _tsaUrl.value = url.trim()
        _tsaCredenciada.value = credenciada
    }

    private val _carimbando = MutableStateFlow(false)
    val carimbando: StateFlow<Boolean> = _carimbando

    /**
     * Pede o carimbo do tempo sobre a raiz da sessao.
     *
     * So faz sentido depois de fechada: a raiz e o que se carimba. E o carimbo recebido so e
     * gravado se se referir a ESTA raiz e ao numero aleatorio deste pedido — ver
     * [CarimboTempo.conferirResposta]. Um selo que carimba outro hash seria pior que nenhum.
     */
    fun carimbarSessao(sessao: Sessao) {
        val raiz = sessao.raizMerkle
        if (raiz.isNullOrBlank()) {
            _mensagem.value = "Feche a sessão antes: o carimbo é aplicado sobre a raiz."
            return
        }
        if (sessao.carimboTempo != null) {
            _mensagem.value = "Esta vistoria já tem carimbo do tempo."
            return
        }
        if (_carimbando.value) return
        _carimbando.value = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val url = _tsaUrl.value
                val pedido = CarimboTempo.pedido(raiz)
                val resposta = ClienteTsa.enviar(url, pedido.bytes)
                val carimbo = CarimboTempo.conferirResposta(
                    resposta, pedido,
                    autoridade = if (url == ClienteTsa.URL_PADRAO) ClienteTsa.NOME_PADRAO else url,
                    credenciadaIcpBrasil = _tsaCredenciada.value
                )
                banco.atualizarCarimbo(
                    sessao.id, carimbo.tokenBase64, carimbo.instante,
                    carimbo.autoridade, carimbo.credenciadaIcpBrasil
                )
                carimbo
            }.onSuccess { c ->
                recarregar()
                _mensagem.value = "Carimbo aplicado: ${fmtCarimbo.format(Date(c.instante))} (UTC " +
                    "declarado pela Autoridade)." +
                    if (!c.credenciadaIcpBrasil) " Autoridade NÃO credenciada na ICP-Brasil." else ""
            }.onFailure {
                // A mensagem da recusa e a informacao util: diz se foi rede, se a Autoridade
                // negou, ou se o carimbo se referia a outro hash.
                _mensagem.value = it.message ?: "Não foi possível obter o carimbo do tempo."
            }
            _carimbando.value = false
        }
    }

    private val fmtCarimbo = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale("pt", "BR"))
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }

    /**
     * Exporta a prova de UMA fotografia, para juntar sozinha a um processo.
     *
     * O caminho de Merkle NAO fica guardado no banco: e derivado, e recalcula-lo aqui a partir
     * dos hashes da sessao evita um campo que poderia envelhecer em relacao a arvore. A ordem
     * das folhas vem de `fotosDaSessao` (por `instante, id`), a mesma usada no fechamento.
     */
    fun exportarProvaDaFoto(sessao: Sessao, foto: Foto) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            runCatching {
                val fotos = banco.fotosDaSessao(sessao.id)
                val indice = fotos.indexOfFirst { it.id == foto.id }
                if (indice < 0) throw IllegalStateException("Registro não encontrado na sessão.")
                val hashes = fotos.map { it.sha256 }
                val arquivo = File(foto.arquivoOriginal)
                val texto = ProvaFoto.gerar(
                    tituloSessao = sessao.titulo,
                    processo = sessao.processo,
                    fechadaEm = sessao.fechadaEm,
                    raizSelada = sessao.raizMerkle,
                    comCarimbo = sessao.carimboTempo != null,
                    carimboInstante = sessao.carimboInstante,
                    carimboAutoridade = sessao.carimboAutoridade,
                    carimboCredenciado = sessao.carimboCredenciado,
                    indice = indice + 1,
                    total = fotos.size,
                    nomeArquivo = arquivo.name,
                    hashGravado = foto.sha256,
                    instanteCaptura = foto.instante,
                    caminho = Integridade.caminhoMerkle(hashes, indice),
                    hashAtual = if (arquivo.exists()) Integridade.sha256(arquivo) else null
                )
                val destino = File(pastaDaSessao(sessao.id), ProvaFoto.nomeSugerido(arquivo.name))
                destino.writeText(texto)
                // A prova acompanha a fotografia: entregar so o .txt obrigaria a outra parte a
                // procurar a imagem, e entregar so a imagem nao prova nada.
                val anexos = if (arquivo.exists()) listOf(arquivo, destino) else listOf(destino)
                withContext(Dispatchers.Main) {
                    Exportador.compartilhar(ctx, anexos, "Prova de integridade — ${arquivo.name}")
                }
                _mensagem.value = "Gerado: ${destino.name}"
            }.onFailure {
                _mensagem.value = when (it) {
                    is ProvaFoto.SessaoNaoSelada -> it.message ?: "Vistoria não selada."
                    else -> "Falha ao gerar a prova: ${it.message}"
                }
            }
        }
    }

    /**
     * Ao contrario de `exportar`, esta funcao nao tinha runCatching nenhum — e
     * `getUriForFile` lanca para arquivo fora dos caminhos declarados, `startActivity` lanca
     * quando nao ha app receptor, e `compartilhar` agora lanca quando os arquivos sumiram.
     * Excecao nao tratada dentro de viewModelScope derruba o processo inteiro.
     */
    fun compartilharOriginais(sessao: Sessao) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val fotos = banco.fotosDaSessao(sessao.id).map { File(it.arquivoOriginal) }
            withContext(Dispatchers.Main) {
                runCatching {
                    Exportador.compartilhar(ctx, fotos, "${sessao.titulo} — arquivos originais")
                }.onFailure { _mensagem.value = "Não foi possível compartilhar: ${it.message}" }
            }
        }
    }

    // ------------------------------------------------------------------ enderecos

    fun resolverEnderecos() {
        viewModelScope.launch(Dispatchers.IO) {
            // Cada causa tem a sua frase. Antes as tres davam a mesma, e a mesma frase para
            // "seu aparelho nao tem esse servico" e "voce esta sem internet" nao ajuda ninguem.
            val r = runCatching { Enderecos.resolver(getApplication(), banco) }.getOrNull()
            _mensagem.value = when (r) {
                null -> "Falha ao consultar endereços."
                is Enderecos.Resultado.SemServico ->
                    "Este aparelho não tem serviço de geocodificação. O endereço fica em branco — " +
                        "a coordenada, que é o que vale para o laudo, continua registrada."
                is Enderecos.Resultado.NadaPendente ->
                    "Nenhuma foto com endereço pendente."
                is Enderecos.Resultado.Concluido -> when {
                    r.resolvidos == r.tentados -> "${r.resolvidos} endereço(s) completado(s)."
                    r.resolvidos > 0 ->
                        "${r.resolvidos} de ${r.tentados} endereços completados. " +
                            "O resto continua na fila — tente de novo com sinal melhor."
                    else ->
                        "Nenhum dos ${r.tentados} endereços foi resolvido. Provavelmente falta " +
                            "conexão: eles continuam na fila para a próxima tentativa."
                }
            }
            _sessaoAtual.value?.let { carregarFotos(it.id) }
        }
    }

    fun limparMensagem() { _mensagem.value = null }

    private companion object {
        const val INTERVALO_CAMINHAMENTO_MS = 15_000L
        const val DURACAO_MAXIMA_AUDIO_MS = 60 * 60 * 1000
    }
}
