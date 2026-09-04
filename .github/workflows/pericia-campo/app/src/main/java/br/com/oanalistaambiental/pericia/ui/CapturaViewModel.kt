package br.com.oanalistaambiental.pericia.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.oanalistaambiental.pericia.captura.EstadoCampo
import br.com.oanalistaambiental.pericia.captura.Integridade
import br.com.oanalistaambiental.pericia.captura.Legenda
import br.com.oanalistaambiental.pericia.dados.Banco
import br.com.oanalistaambiental.pericia.dados.Foto
import br.com.oanalistaambiental.pericia.dados.RegistroRestricao
import br.com.oanalistaambiental.pericia.dados.Sessao
import br.com.oanalistaambiental.pericia.geo.ConsultaRestricao
import br.com.oanalistaambiental.pericia.geo.PontoConsulta
import br.com.oanalistaambiental.pericia.geo.Restricao
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

    var tipoOcorrencia: String? = null
    var observacao: String = ""

    init { recarregar(); estadoCampo.iniciar() }

    override fun onCleared() { estadoCampo.parar(); super.onCleared() }

    fun recarregar() {
        viewModelScope.launch(Dispatchers.IO) {
            val lista = banco.sessoes()
            _sessoes.value = lista
            if (_sessaoAtual.value == null) _sessaoAtual.value = lista.firstOrNull { it.fechadaEm == null }
        }
    }

    fun novaSessao(titulo: String, processo: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = banco.criarSessao(titulo, processo)
            _sessoes.value = banco.sessoes()
            _sessaoAtual.value = _sessoes.value.firstOrNull { it.id == id }
        }
    }

    fun selecionarSessao(s: Sessao) { _sessaoAtual.value = s }

    fun pastaDaSessao(sessaoId: Long): File =
        File(getApplication<Application>().filesDir, "sessoes/$sessaoId").apply { mkdirs() }

    /**
     * Chamado assim que o CameraX termina de gravar o arquivo original.
     *
     * Ordem que nao muda: grava original -> hash -> registro. So DEPOIS, e em background,
     * gera a copia com legenda e consulta as restricoes. Se qualquer um desses dois falhar,
     * a prova ja esta integra e registrada.
     */
    fun registrarCaptura(original: File) {
        val sessao = _sessaoAtual.value ?: run {
            _mensagem.value = "Crie ou selecione uma sessão antes de fotografar."
            return
        }
        val leitura = estadoCampo.leitura.value
        if (leitura.lat == null || leitura.lon == null) {
            _mensagem.value = "Sem posição GNSS. A foto foi guardada, mas sem coordenada."
        }

        viewModelScope.launch(Dispatchers.IO) {
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

            // 1) copia com legenda — nunca sobre o original ja assinado
            runCatching {
                val destino = File(original.parentFile, original.nameWithoutExtension + "_legenda.jpg")
                Legenda.gerar(original, destino, comId, sessao.titulo)
            }

            // 2) consulta de restricao, fora do caminho critico
            if (leitura.lat != null && leitura.lon != null) {
                consultarRestricoes(fotoId, comId)
            }
            recarregar()
        }
    }

    private suspend fun consultarRestricoes(fotoId: Long, foto: Foto) = withContext(Dispatchers.IO) {
        val gpkg = File(getApplication<Application>().filesDir, "pacotes/mg-base.gpkg")
        if (!gpkg.exists()) {
            _mensagem.value = "Pacote de camadas não instalado — alerta locacional indisponível."
            return@withContext
        }
        runCatching {
            ConsultaRestricao.abrir(gpkg).consultar(
                PontoConsulta(foto.lat, foto.lon, foto.precisaoM, foto.instante)
            )
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
        }
    }

    fun fecharSessao(sessao: Sessao) {
        viewModelScope.launch(Dispatchers.IO) {
            val hashes = banco.fotosDaSessao(sessao.id).map { it.sha256 }
            banco.fecharSessao(sessao.id, Integridade.raizMerkle(hashes))
            recarregar()
            _mensagem.value = "Sessão fechada. Raiz de Merkle calculada."
        }
    }

    fun limparMensagem() { _mensagem.value = null }
}
