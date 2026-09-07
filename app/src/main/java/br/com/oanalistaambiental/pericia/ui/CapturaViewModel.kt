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
import br.com.oanalistaambiental.pericia.geo.Medicao
import br.com.oanalistaambiental.pericia.geo.ConsultaRestricao
import br.com.oanalistaambiental.pericia.geo.PontoConsulta
import br.com.oanalistaambiental.pericia.geo.PontoRetorno
import br.com.oanalistaambiental.pericia.geo.Restricao
import br.com.oanalistaambiental.pericia.laudo.LaudoPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
        val leitura = estadoCampo.leituraAtual()
        val pos = leitura.posicao
        if (!pos.temPosicao) {
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
                lat = pos.lat ?: 0.0,
                lon = pos.lon ?: 0.0,
                precisaoM = pos.precisaoM ?: 999f,
                altitudeM = pos.altitudeM,
                azimuteGraus = leitura.orientacao.azimuteGraus,
                inclinacaoGraus = leitura.orientacao.elevacaoGraus,
                instante = System.currentTimeMillis(),
                tipoOcorrencia = _tipoOcorrencia.value,
                observacao = _observacao.value.ifBlank { null }
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

            if (pos.temPosicao) consultarRestricoes(fotoId, comId)
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
