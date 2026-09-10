package br.com.oanalistaambiental.pericia.enquadramento.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.oanalistaambiental.pericia.enquadramento.geo.DeteccaoLocacional
import br.com.oanalistaambiental.pericia.enquadramento.laudo.SimulacaoPdf
import br.com.oanalistaambiental.pericia.enquadramento.norma.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SimulacaoViewModel(app: Application) : AndroidViewModel(app) {

    private val _regras = MutableStateFlow<Regras?>(null)
    val regras: StateFlow<Regras?> = _regras

    private val _erroBase = MutableStateFlow<String?>(null)
    val erroBase: StateFlow<String?> = _erroBase

    // ---- estado da simulação em curso ----
    private val _atividade = MutableStateFlow<Atividade?>(null)
    val atividade: StateFlow<Atividade?> = _atividade

    /** Dica da atividade atual, lida de `assets/norma/dicas/<codigo>.json`. Ver [Dicas]. */
    private val _dica = MutableStateFlow<DicaAtividade?>(null)
    val dica: StateFlow<DicaAtividade?> = _dica

    private fun carregarDica(codigo: String) {
        _dica.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val d = Dicas.carregar(codigo) { nome ->
                getApplication<Application>().assets.open("norma/dicas/$nome")
            }
            _dica.value = d
        }
    }

    private val _porte = MutableStateFlow<Grau?>(null)
    val porte: StateFlow<Grau?> = _porte

    /**
     * O valor que o usuario informou, guardado para chegar ao resultado e ao PDF.
     *
     * Faltava por completo: o `Resultado` guardava so o Grau, e a memoria de calculo dizia
     * "Porte: MEDIO — parametro: Producao bruta". Quem lesse o PDF nao tinha como refazer a
     * conta, porque nao sabia se foram 500.000 t/ano ou 5.000 — e um parecer que nao permite
     * refazer a conta nao serve para instruir processo.
     */
    private val _valorInformado = MutableStateFlow<ValorInformado?>(null)
    val valorInformado: StateFlow<ValorInformado?> = _valorInformado

    /**
     * Resultado de DISPENSA pelo art. 10 — porte inferior ao menor previsto, ou atividade não
     * listada. Não é erro: é um dos resultados possíveis da simulação, e o que bancos pedem
     * como "declaração de dispensa de licenciamento".
     */
    private val _dispensa = MutableStateFlow<Dispensa.Resultado?>(null)
    val dispensa: StateFlow<Dispensa.Resultado?> = _dispensa

    /**
     * Valor que caiu numa LACUNA da norma — nenhuma faixa de porte o cobre.
     *
     * Estado proprio, e nao mensagem de erro, pela mesma razao da dispensa do art. 10: quem
     * informou 5 ha em C-04-21-9 nao errou nada. Ver "erro" ali faria a pessoa achar que
     * digitou errado e mexer no numero ate a tela parar de reclamar — que e exatamente o
     * caminho para um enquadramento inventado.
     */
    private val _lacuna = MutableStateFlow<Enquadramento.LacunaDeFaixa?>(null)
    val lacuna: StateFlow<Enquadramento.LacunaDeFaixa?> = _lacuna

    fun limparDispensa() { _dispensa.value = null }
    fun limparLacuna() { _lacuna.value = null }

    /** Dispensa por a atividade não constar da Listagem do Anexo Único (art. 10, caput). */
    fun declararNaoListada(descricao: String) {
        if (descricao.isBlank()) {
            _mensagem.value = "Descreva a atividade antes de concluir pela dispensa."
            return
        }
        _dispensa.value = Dispensa.porNaoEstarListada(descricao.trim())
        _porte.value = null
        _resultado.value = null
    }

    /** Modo manual: o usuário informa porte e potencial sem escolher atividade do catálogo. */
    private val _potencialManual = MutableStateFlow<Grau?>(null)
    val potencialManual: StateFlow<Grau?> = _potencialManual

    private val _marcados = MutableStateFlow<Set<String>>(emptySet())
    val marcados: StateFlow<Set<String>> = _marcados

    private val _fatoresMarcados = MutableStateFlow<Set<String>>(emptySet())
    val fatoresMarcados: StateFlow<Set<String>> = _fatoresMarcados

    private val _comEia = MutableStateFlow(false)
    val comEia: StateFlow<Boolean> = _comEia

    private val _resultado = MutableStateFlow<Enquadramento.Resultado?>(null)
    val resultado: StateFlow<Enquadramento.Resultado?> = _resultado

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem

    private val _deteccao = MutableStateFlow<DeteccaoLocacional.Incidencia?>(null)
    val deteccao: StateFlow<DeteccaoLocacional.Incidencia?> = _deteccao

    /**
     * Quais criterios vieram da consulta as camadas, e nao da mao do usuario.
     *
     * Existe por duas razoes. A primeira e um bug: a deteccao SOMAVA ao conjunto ja marcado e
     * nunca limpava o que ela mesma tinha marcado antes. Quem colasse a coordenada com o sinal
     * trocado, marcasse "UC de protecao integral" (peso 2) por engano do app, corrigisse a
     * coordenada e consultasse de novo, recebia "Nenhuma camada incide neste ponto" — com o
     * peso 2 ainda marcado, e o enquadramento saindo LAC2 em vez de LAS/RAS. A segunda e que o
     * art. 6o, par. 5o manda documentar a origem de cada criterio, e o PDF precisa distinguir
     * "o app sugeriu" de "o responsavel afirmou".
     */
    private val _criteriosAutomaticos = MutableStateFlow<Set<String>>(emptySet())
    val criteriosAutomaticos: StateFlow<Set<String>> = _criteriosAutomaticos

    val arquivoPacote: File
        get() = File(getApplication<Application>().getExternalFilesDir(null), "pacotes/mg-base.gpkg")

    private val _pacoteInstalado = MutableStateFlow(false)
    val pacoteInstalado: StateFlow<Boolean> = _pacoteInstalado

    fun conferirPacote() { _pacoteInstalado.value = arquivoPacote.exists() }

    /**
     * Instala o pacote de camadas a partir de um arquivo escolhido pelo usuario.
     *
     * ISTO NAO EXISTIA. `arquivoPacote` apontava para uma pasta que nenhuma tela sabia
     * preencher: nao havia importacao, nem download, nem asset embarcado. Na pratica, todo
     * usuario que tocasse em "Verificar camadas neste ponto" recebia "Pacote de camadas não
     * instalado" para sempre, enquanto o cartao logo acima vendia a consulta ao IDE-Sisema
     * como funcionalidade central e citava o art. 6o, par. 5o. Beco sem saida para quem nao
     * tem adb.
     */
    fun instalarPacote(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val ctx = getApplication<Application>()
                val destino = arquivoPacote
                destino.parentFile?.mkdirs()
                // Grava num temporario e so entao substitui: uma copia interrompida na metade
                // produzia um .gpkg truncado que ABRE normalmente e devolve consulta vazia —
                // ou seja, "nenhuma restricao aqui" quando a verdade e "nao consegui ler".
                val temp = File(destino.parentFile, destino.name + ".parcial")
                ctx.contentResolver.openInputStream(uri).use { entrada ->
                    checkNotNull(entrada) { "Não foi possível abrir o arquivo escolhido." }
                    temp.outputStream().use { entrada.copyTo(it) }
                }
                check(temp.length() > 0) { "O arquivo escolhido está vazio." }
                // Conferencia antes de valer: se nao der para listar as camadas, nao instala.
                val camadas = DeteccaoLocacional.abrir(temp).use { it.camadasDoPacote() }
                check(camadas.isNotEmpty()) {
                    "O arquivo abriu, mas não tem a tabela de camadas de um pacote de perícia."
                }
                if (destino.exists()) destino.delete()
                check(temp.renameTo(destino)) { "Não foi possível gravar o pacote." }
                camadas.size
            }.onSuccess { n ->
                _pacoteInstalado.value = true
                _mensagem.value = "Pacote instalado: $n camada(s). Já dá para verificar por coordenada."
            }.onFailure {
                _pacoteInstalado.value = arquivoPacote.exists()
                _mensagem.value = "Não foi possível instalar o pacote: ${it.message}"
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                BaseNormativa.carregar { nome ->
                    getApplication<Application>().assets.open("norma/$nome")
                }
            }.onSuccess { _regras.value = it }
             .onFailure { _erroBase.value = "Não foi possível carregar a base normativa: ${it.message}" }
        }
    }

    // ------------------------------------------------------------------ fluxo

    fun novaSimulacao() {
        _atividade.value = null
        _porte.value = null
        _valorInformado.value = null
        _criteriosAutomaticos.value = emptySet()
        _potencialManual.value = null
        _marcados.value = emptySet()
        _fatoresMarcados.value = emptySet()
        _comEia.value = false
        _resultado.value = null
        _deteccao.value = null
        _dispensa.value = null
        _lacuna.value = null
        ultimaCoordenada = null
        _fatoresAutomaticos = emptySet()
    }

    /**
     * Troca a atividade da simulacao em curso.
     *
     * BUG CORRIGIDO — o mesmo que a calculadora do site tinha e ja corrigiu. Antes esta funcao
     * so limpava porte, valor e potencial. Tudo o que dependia da atividade e nao do lugar
     * seguia marcado da atividade ANTERIOR:
     *
     *  - `_comEia` (o empreendedor apresentou EIA/RIMA). Marcado numa atividade classe 5 e
     *    esquecido ao trocar para uma classe 3, ele mudava a modalidade da nova atividade sem
     *    que nada na tela dissesse por que. E uma declaracao sobre UM empreendimento, nunca
     *    sobre o lugar.
     *  - `_resultado`. Ficava o da atividade anterior ate o proximo calculo, e o botao de
     *    exportar PDF continuava ativo apontando para ele.
     *
     * O que NAO se apaga, de proposito: os criterios da Tabela 4, os fatores da Tabela 5, a
     * deteccao das camadas e a coordenada. Esses descrevem o LUGAR, e o caso normal e simular
     * varias atividades no mesmo ponto — apagar obrigaria a refazer a consulta a cada troca.
     *
     * Mas silencio tambem nao serve: quem trocou de atividade precisa SABER que as marcacoes
     * do lugar continuam valendo, senao confere o resultado achando que partiu do zero. Por
     * isso, havendo marcacao herdada, a troca avisa. Ausencia de aviso nao pode significar
     * "nada foi herdado".
     */
    fun escolherAtividade(a: Atividade) {
        val trocou = _atividade.value?.codigo != a.codigo
        _atividade.value = a
        if (trocou) carregarDica(a.codigo)
        _porte.value = null
        _valorInformado.value = null
        _dispensa.value = null
        _lacuna.value = null
        _potencialManual.value = null

        // Dependem da atividade, nao do lugar: nao podem atravessar a troca.
        _comEia.value = false
        _resultado.value = null

        if (trocou) {
            val herdados = _marcados.value.size + _fatoresMarcados.value.size
            if (herdados > 0) {
                _mensagem.value = "Mantidos $herdados item(ns) locacional(is) marcado(s) do ponto " +
                    "anterior — eles descrevem o local, nao a atividade. Confira na tela de " +
                    "criterios locacionais antes de calcular."
            }
        }
    }

    fun definirPorteManual(g: Grau) { _porte.value = g }
    fun definirPotencialManual(g: Grau) { _potencialManual.value = g }

    /**
     * [valor] nulo LIMPA o porte, e isso e a correcao de um bug real.
     *
     * Antes a tela so chamava esta funcao quando o texto parseava. Se o usuario digitasse
     * 2.000.000, visse "Porte GRANDE", selecionasse tudo e apagasse, o campo ficava vazio mas
     * o cartao continuava dizendo GRANDE, o botao "Continuar" seguia habilitado, e o resultado
     * e o PDF saiam com um porte que nao correspondia a valor nenhum informado. O mesmo
     * acontecia ao trocar a unidade com o campo vazio.
     */
    fun calcularPorte(valor: Double?, usarAlternativa: Boolean) {
        val a = _atividade.value ?: return
        if (valor == null) {
            _porte.value = null
            _valorInformado.value = null
            _lacuna.value = null
            return
        }
        runCatching { Enquadramento.porteDe(a, valor, usarAlternativa) }
            .onSuccess {
                _porte.value = it
                _dispensa.value = null
                _lacuna.value = null
                _valorInformado.value = ValorInformado(
                    valor,
                    (if (usarAlternativa) a.unidadeAlternativa else a.unidade) ?: "",
                    a.parametro
                )
            }
            .onFailure { e ->
                _porte.value = null
                _valorInformado.value = null
                _lacuna.value = null
                if (e is Enquadramento.PorteInferior) {
                    // Nao e erro: e o resultado de dispensa do art. 10. Monta o resultado
                    // completo, com os tres deveres do paragrafo unico junto.
                    _dispensa.value = Dispensa.porPorteInferior(
                        e.atividade, e.valor,
                        (if (usarAlternativa) a.unidadeAlternativa else a.unidade) ?: ""
                    )
                } else if (e is Enquadramento.LacunaDeFaixa) {
                    // Tambem nao e erro: a NORMA nao define faixa para este valor exato.
                    // Em vez de escolher um lado, a tela mostra as duas leituras.
                    _dispensa.value = null
                    _lacuna.value = e
                } else {
                    _dispensa.value = null
                    _mensagem.value = e.message
                }
            }
    }

    fun calcularPorteCategoria(rotulo: String) {
        val a = _atividade.value ?: return
        runCatching { Enquadramento.porteDe(a, rotulo) }
            .onSuccess {
                _porte.value = it
                _valorInformado.value = ValorInformado(null, "", a.parametro, rotulo)
            }
            .onFailure { _mensagem.value = it.message }
    }

    /** Fatores da Tabela 5 sugeridos pela ultima consulta. Nao pesam, mas avisam. */
    private var _fatoresAutomaticos: Set<String> = emptySet()

    /**
     * Tocar num criterio o torna do usuario, mesmo que tenha vindo da sugestao: quem
     * desmarcou um criterio automatico nao quer ve-lo voltar sozinho na proxima consulta.
     */
    fun alternarCriterio(id: String) {
        _marcados.value = _marcados.value.let { if (id in it) it - id else it + id }
        _criteriosAutomaticos.value = _criteriosAutomaticos.value - id
    }

    fun alternarFator(id: String) {
        _fatoresMarcados.value = _fatoresMarcados.value.let { if (id in it) it - id else it + id }
    }

    fun definirEia(v: Boolean) { _comEia.value = v }

    /** Sugestão automática pelo pacote de camadas — o mesmo do aplicativo de campo. */
    /** Coordenada da ultima consulta, para constar do PDF conforme o art. 6o, par. 5o. */
    private var ultimaCoordenada: Pair<Double, Double>? = null

    fun detectarPorCoordenada(lat: Double, lon: Double) {
        val r = _regras.value ?: return
        ultimaCoordenada = lat to lon
        viewModelScope.launch(Dispatchers.IO) {
            if (!arquivoPacote.exists()) {
                _mensagem.value = "Pacote de camadas não instalado. Marque os critérios à mão."
                return@launch
            }
            runCatching {
                DeteccaoLocacional.abrir(arquivoPacote).use { it.verificar(r, lat, lon) }
            }.onSuccess { inc ->
                _deteccao.value = inc
                // Uma consulta nova SUBSTITUI a sugestao anterior em vez de somar a ela. O que
                // o usuario marcou a mao continua marcado; so o que veio do app e refeito.
                val novosCriterios = inc.criterios.map { it.id }.toSet()
                val novosFatores = inc.fatores.map { it.id }.toSet()
                _marcados.value = (_marcados.value - _criteriosAutomaticos.value) + novosCriterios
                _fatoresMarcados.value =
                    (_fatoresMarcados.value - _fatoresAutomaticos) + novosFatores
                _criteriosAutomaticos.value = novosCriterios
                _fatoresAutomaticos = novosFatores
                // Tres situacoes que antes viravam duas frases. "Nada incide" e "nao consegui
                // ler" nao podem soar igual: a segunda pede providencia.
                _mensagem.value = buildString {
                    if (inc.criterios.isEmpty() && inc.fatores.isEmpty()) {
                        append("Nenhuma camada do pacote incide neste ponto. ")
                        append("Confira também os critérios que dependem do projeto.")
                    } else {
                        append("Sugestão aplicada: ${inc.criterios.size} critério(s) e ")
                        append("${inc.fatores.size} fator(es). Confira antes de seguir.")
                    }
                    if (inc.aConferir.isNotEmpty()) {
                        append(" A CONFERIR à mão: ")
                        append(inc.aConferir.joinToString(", ") { it.id })
                        append(" — a geometria bate, mas o pacote não traz a categoria da UC.")
                    }
                    if (inc.camadasComFalha.isNotEmpty()) {
                        append(" SEM LEITURA: ")
                        append(inc.camadasComFalha.joinToString(", "))
                        append(" — isso não é 'não incide'. Refaça o pacote de camadas.")
                    }
                }
            }.onFailure { _mensagem.value = "Consulta de camadas falhou: ${it.message}" }
        }
    }

    fun calcular() {
        val r = _regras.value ?: run {
            // Retorno mudo: o usuario tocava em "Calcular" e nada acontecia, sem explicacao.
            _mensagem.value = "Base normativa ainda não carregada. Aguarde um instante."
            return
        }
        val porte = _porte.value ?: run { _mensagem.value = "Informe o porte."; return }
        val incidentes = r.criterios.filter { it.id in _marcados.value }
        val fatores = r.fatores.filter { it.id in _fatoresMarcados.value }

        // BUG corrigido: a atividade sintética do modo manual NÃO é nula (a tela a define para
        // habilitar os seletores), então o elvis nunca disparava e o potencial ficava sempre
        // pequeno — o que resultaria em classe 1 em qualquer simulação manual.
        val escolhida = _atividade.value
        val a = if (escolhida == null || escolhida.tipo == "manual") {
            val pp = _potencialManual.value ?: run {
                _mensagem.value = "Informe o potencial poluidor/degradador geral."; return
            }
            Atividade(
                codigo = "—", descricao = "Enquadramento informado manualmente",
                pp = PotencialPoluidor(pp, pp, pp, pp),
                tipo = "manual", parametro = "informado pelo usuário",
                conferencia = Conferencia.CRUZADO
            )
        } else escolhida

        runCatching {
            Enquadramento.simular(
                r, a, porte, incidentes, fatores, _comEia.value,
                valorInformado = _valorInformado.value,
                coordenadaConsultada = ultimaCoordenada,
                versaoPacote = _deteccao.value?.versaoPacote,
                criteriosAutomaticos = _criteriosAutomaticos.value
            )
        }
            .onSuccess { _resultado.value = it }
            .onFailure { _mensagem.value = it.message }
    }

    /**
     * `calcular()` que devolve se deu certo, para a tela nao navegar em cima de falha.
     *
     * BUG corrigido: o botao fazia `vm.calcular(); avancar()` — incondicional. `calcular()` tem
     * quatro saidas sem sucesso, uma delas completamente muda (regras nulas), e `_resultado` so
     * e sobrescrito no sucesso. Falhando, a tela de resultado exibia A SIMULACAO ANTERIOR com
     * cara de resultado novo, enquanto a mensagem de erro passava por cinco segundos na barra
     * de baixo. Documento assinavel com o numero errado.
     */
    fun calcularComRetorno(): Boolean {
        val antes = _resultado.value
        calcular()
        val depois = _resultado.value
        return depois != null && depois !== antes
    }

    /**
     * Gera e compartilha o PDF.
     *
     * Isto ficava no callback de clique da tela, ou seja, desenhava o documento inteiro e
     * gravava o arquivo na thread da UI — e o runCatching descartava a falha, então um erro
     * fazia o botão simplesmente não responder. Agora o trabalho vai para IO e a falha aparece.
     */
    fun exportarPdf(contexto: Context) {
        val r = _regras.value ?: return
        val res = _resultado.value ?: run { _mensagem.value = "Nada calculado ainda."; return }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val pasta = File(contexto.filesDir, "simulacoes").apply { mkdirs() }
                SimulacaoPdf.gerar(r, res, File(pasta, "simulacao-${System.currentTimeMillis()}.pdf"))
            }.onSuccess { arquivo ->
                // startActivity precisa da thread principal.
                withContext(Dispatchers.Main) { SimulacaoPdf.compartilhar(contexto, arquivo) }
            }.onFailure { _mensagem.value = "Não foi possível gerar o PDF: ${it.message}" }
        }
    }

    /**
     * PDF do resultado de dispensa. Separado do `exportarPdf` porque o documento é outro: ali
     * é memória de cálculo de enquadramento, aqui é a "declaração de dispensa" que o banco pede
     * — com os três deveres do art. 10 ocupando a maior parte da folha, de propósito.
     */
    fun exportarDispensaPdf(contexto: Context) {
        val r = _regras.value ?: return
        val d = _dispensa.value ?: run { _mensagem.value = "Nenhuma dispensa calculada."; return }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val pasta = File(contexto.filesDir, "simulacoes").apply { mkdirs() }
                SimulacaoPdf.gerarDispensa(r, d, File(pasta, "dispensa-${System.currentTimeMillis()}.pdf"))
            }.onSuccess { arquivo ->
                withContext(Dispatchers.Main) { SimulacaoPdf.compartilhar(contexto, arquivo) }
            }.onFailure { _mensagem.value = "Não foi possível gerar o PDF: ${it.message}" }
        }
    }

    /** Aviso curto na barra inferior. */
    fun avisar(texto: String) { _mensagem.value = texto }

    fun limparMensagem() { _mensagem.value = null }
}
