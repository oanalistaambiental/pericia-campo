package br.com.oanalistaambiental.pericia.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.oanalistaambiental.pericia.captura.Enderecos
import br.com.oanalistaambiental.pericia.captura.EstadoCampo
import br.com.oanalistaambiental.pericia.captura.Integridade
import br.com.oanalistaambiental.pericia.captura.Legenda
import br.com.oanalistaambiental.pericia.dados.Banco
import br.com.oanalistaambiental.pericia.dados.Foto
import br.com.oanalistaambiental.pericia.dados.RegistroRestricao
import br.com.oanalistaambiental.pericia.dados.Sessao
import br.com.oanalistaambiental.pericia.exportacao.Exportador
import br.com.oanalistaambiental.pericia.geo.CamadaInfo
import br.com.oanalistaambiental.pericia.geo.ConsultaRestricao
import br.com.oanalistaambiental.pericia.geo.PontoConsulta
import br.com.oanalistaambiental.pericia.geo.PontoRetorno
import br.com.oanalistaambiental.pericia.geo.Restricao
import br.com.oanalistaambiental.pericia.laudo.LaudoPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CapturaViewModel(app: Application) : AndroidViewModel(app) {

    val banco = Banco(app)
    val estadoCampo = EstadoCampo(app)

    private val _sessaoAtual = MutableStateFlow<Sessao?>(null)
    val sessaoAtual: StateFlow<Sessao?> = _sessaoAtual

    private val _sessoes = MutableStateFlow<List<Sessao>>(emptyList())
    val sessoes: StateFlow<List<Sessao>> = _sessoes

    private val _ultimasRestricoes = MutableStateFlow<List<Restricao>>(emptyList())
    val ultimasRestricoes: StateFlow<List<Restricao>> = _ultimasRestricoes

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem

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

    /** Foto escolhida como alvo do ponto de retorno. */
    private val _alvoRetorno = MutableStateFlow<Foto?>(null)
    val alvoRetorno: StateFlow<Foto?> = _alvoRetorno

    private val _orientacao = MutableStateFlow<PontoRetorno.Orientacao?>(null)
    val orientacao: StateFlow<PontoRetorno.Orientacao?> = _orientacao

    var tipoOcorrencia: String? = null
    var observacao: String = ""

    /** BUG corrigido: uma instancia reaproveitada, em vez de reabrir o GeoPackage a cada foto. */
    private var consulta: ConsultaRestricao? = null

    /**
     * BUG corrigido: o app procurava o pacote no armazenamento INTERNO, invisivel ao usuario,
     * enquanto a tela de configuracoes e o script mandavam copiar para o EXTERNO. O alerta
     * locacional — o coracao do produto — ficaria desligado para sempre, e em silencio.
     */
    private val arquivoPacote: File
        get() = File(getApplication<Application>().getExternalFilesDir(null), "pacotes/mg-base.gpkg")

    init {
        recarregar()
        // O GNSS NAO e iniciado aqui: sem permissao a chamada e recusada em silencio e nada
        // religa depois. Quem inicia e a MainActivity, assim que a permissao existe.
        abrirPacote()
        observarPosicaoParaRetorno()
    }

    override fun onCleared() {
        estadoCampo.parar()
        runCatching { consulta?.close() }
        super.onCleared()
    }

    // ------------------------------------------------------------------ pacote

    private fun abrirPacote() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (arquivoPacote.exists()) {
                    val c = ConsultaRestricao.abrir(arquivoPacote)
                    consulta = c
                    _camadas.value = c.camadasInstaladas()
                    _versaoPacote.value = c.versaoDoPacote()
                }
            }.onFailure {
                _mensagem.value = "Pacote de camadas ilegível: ${it.message}"
            }
        }
    }

    fun pastaDePacotes(): File =
        File(getApplication<Application>().getExternalFilesDir(null), "pacotes").apply { mkdirs() }

    // ------------------------------------------------------------------ sessoes

    fun recarregar() {
        viewModelScope.launch(Dispatchers.IO) {
            val lista = banco.sessoes()
            _sessoes.value = lista
            if (_sessaoAtual.value == null) _sessaoAtual.value = lista.firstOrNull { it.fechadaEm == null }
            _sessaoAtual.value?.let { carregarFotos(it.id) }
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

    // ------------------------------------------------------------------ captura

    fun registrarCaptura(original: File) {
        val sessao = _sessaoAtual.value ?: run {
            _mensagem.value = "Crie ou selecione uma sessão antes de fotografar."
            return
        }
        val leitura = estadoCampo.leitura.value
        if (leitura.lat == null || leitura.lon == null) {
            _mensagem.value = "Sem posição GNSS: a foto foi guardada, mas sem coordenada."
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
                lat = leitura.lat ?: 0.0,
                lon = leitura.lon ?: 0.0,
                precisaoM = leitura.precisaoM ?: 999f,
                altitudeM = leitura.altitudeM,
                azimuteGraus = leitura.azimuteGraus,
                inclinacaoGraus = leitura.inclinacaoGraus,
                instante = System.currentTimeMillis(),
                tipoOcorrencia = tipoOcorrencia,
                observacao = observacao.ifBlank { null }
            )
            val fotoId = banco.inserirFoto(foto)
            val comId = foto.copy(id = fotoId)

            // Fora do caminho critico: copia com legenda.
            runCatching {
                val destino = File(original.parentFile, original.nameWithoutExtension + "_legenda.jpg")
                Legenda.gerar(original, destino, comId, sessao.titulo)
                // BUG corrigido: o caminho da copia nunca era gravado, e o laudo usava o original.
                banco.atualizarLegenda(fotoId, destino.absolutePath)
            }.onFailure { _mensagem.value = "Legenda não gerada: ${it.message}" }

            if (leitura.lat != null && leitura.lon != null) consultarRestricoes(fotoId, comId)
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
        }.onFailure { _mensagem.value = "Consulta de restrição falhou: ${it.message}" }
    }

    // ------------------------------------------------------------ ponto de retorno

    fun definirAlvoRetorno(foto: Foto?) { _alvoRetorno.value = foto }

    private fun observarPosicaoParaRetorno() {
        viewModelScope.launch {
            estadoCampo.leitura.collect { l ->
                val alvo = _alvoRetorno.value
                _orientacao.value = if (alvo != null && l.lat != null && l.lon != null) {
                    PontoRetorno.orientar(alvo, l.lat, l.lon, l.precisaoM ?: 99f, l.azimuteGraus)
                } else null
            }
        }
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
                    else -> throw IllegalArgumentException("Formato desconhecido: $formato")
                }
                withContext(Dispatchers.Main) {
                    Exportador.compartilhar(ctx, listOf(arquivo), sessao.titulo)
                }
                _mensagem.value = "Gerado: ${arquivo.name}"
            }.onFailure { _mensagem.value = "Falha ao exportar: ${it.message}" }
        }
    }

    fun compartilharOriginais(sessao: Sessao) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val fotos = banco.fotosDaSessao(sessao.id).map { File(it.arquivoOriginal) }
            withContext(Dispatchers.Main) {
                Exportador.compartilhar(ctx, fotos, "${sessao.titulo} — arquivos originais")
            }
        }
    }

    // ------------------------------------------------------------------ enderecos

    fun resolverEnderecos() {
        viewModelScope.launch(Dispatchers.IO) {
            val n = runCatching { Enderecos.resolverPendentes(getApplication(), banco) }.getOrDefault(0)
            _mensagem.value = if (n > 0) "$n endereço(s) completado(s)." else "Nenhum endereço pendente foi resolvido."
            _sessaoAtual.value?.let { carregarFotos(it.id) }
        }
    }

    fun limparMensagem() { _mensagem.value = null }
}
