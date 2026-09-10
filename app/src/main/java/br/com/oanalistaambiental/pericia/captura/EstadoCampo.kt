package br.com.oanalistaambiental.pericia.captura

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * Estado do aparelho no momento do clique: posicao, precisao, altitude, azimute, inclinacao.
 *
 * Usa LocationManager (GNSS bruto) em vez do provedor fundido do Google Play Services:
 * menos dependencia, funciona sem servicos Google e entrega precisao declarada pelo sistema,
 * que e o numero que importa para valor probatorio.
 *
 * DESEMPENHO — o motivo de existirem tres fluxos e nao um.
 *
 * Antes havia um unico `Leitura` com tudo dentro. A bussola muda dezenas de vezes por segundo;
 * a posicao muda uma vez por segundo. Com tudo no mesmo fluxo, cada tremida do aparelho
 * reemitia o objeto inteiro e o Compose redesenhava a tela de camera toda — incluindo a
 * projecao UTM, o selo de precisao e os avisos de restricao, que nao tinham mudado nada.
 * Isso e a prévia engasgada e o aparelho quente.
 *
 * Separando, cada parte da tela assina so o que lhe diz respeito: a fita da bussola redesenha
 * a 5 Hz, o bloco de coordenada so quando o GNSS entrega posicao nova, e o selo de precisao
 * quase nunca.
 */
class EstadoCampo(private val context: Context) : LocationListener, SensorEventListener {

    // ------------------------------------------------------------------ modelos

    data class Posicao(
        val lat: Double? = null,
        val lon: Double? = null,
        val precisaoM: Float? = null,
        val altitudeM: Double? = null,
        val instante: Long = System.currentTimeMillis(),
        /** Nome do provedor que entregou o ponto: distingue GNSS de rede. */
        val provedor: String? = null,
        /**
         * Leitura que passou do prazo: o GNSS parou de entregar e este ponto ja nao diz onde
         * o perito esta AGORA.
         *
         * BUG grave que isto corrige. A posicao ficava guardada para sempre. Se o sinal caisse
         * — mata fechada, galpao, localizacao desligada no atalho da barra — o app continuava
         * exibindo o selo verde e a coordenada de minutos atras, e a foto tirada 600 m adiante
         * era gravada com aquela coordenada velha, carimbada com a hora de agora e com a
         * precisao antiga. Coordenada de um lugar, hora de outro, e o app afirmando confianca.
         * E o erro que invalida laudo.
         */
        val vencida: Boolean = false
    ) {
        /** Selo de qualidade do ponto: ensina o perito a esperar mais alguns segundos. */
        val qualidade: Qualidade get() = when {
            !temPosicao || precisaoM == null -> Qualidade.SEM_SINAL
            vencida -> Qualidade.VENCIDA
            precisaoM <= 5f -> Qualidade.BOA
            precisaoM <= 15f -> Qualidade.ACEITAVEL
            else -> Qualidade.RUIM
        }

        val temPosicao: Boolean get() = lat != null && lon != null

        /** Idade da leitura em segundos, para a tela dizer de quando e o ponto. */
        fun idadeSegundos(agora: Long = System.currentTimeMillis()): Long =
            ((agora - instante).coerceAtLeast(0L)) / 1000L

        /** Serve para prova? Precisa existir, estar no prazo e nao ser palpite de rede. */
        val serveParaProva: Boolean get() = temPosicao && !vencida && !aproximada

        /** Posicao vinda da rede, nao do GNSS: serve de ponte, nao de prova. */
        val aproximada: Boolean get() = provedor == LocationManager.NETWORK_PROVIDER
    }

    data class Orientacao(
        /**
         * Azimute para onde a CAMERA aponta, nao para onde aponta o topo do aparelho.
         * Null quando a camera olha quase para cima ou para baixo, caso em que nao existe
         * rumo horizontal — ver Orientacoes.azimuteCameraGraus.
         */
        val azimuteGraus: Float? = null,
        /** Elevacao do eixo da camera: 0 na horizontal, positivo para cima. */
        val elevacaoGraus: Float? = null,
        /** Inclinacao das costas do aparelho, 0 a 90. E a leitura do clinometro. */
        val inclinacaoSuperficieGraus: Float? = null,
        /** Confiabilidade da bussola informada pelo sistema. */
        val precisaoBussola: PrecisaoBussola = PrecisaoBussola.DESCONHECIDA
    ) {
        /** Declividade da superficie em porcentagem, que e como talude aparece em laudo. */
        val declividadePercent: Float?
            get() = inclinacaoSuperficieGraus?.let { Orientacoes.declividadePercent(it) }
                // ?.let achataria dois casos diferentes num so: "sem leitura de sensor" e
                // "vertical demais para a tangente significar alguma coisa". A tela precisa
                // distinguir, entao null aqui e sempre "nao ha numero util", e quem exibe olha
                // tambem inclinacaoSuperficieGraus para saber qual dos dois e.
    }

    data class Barometro(
        val pressaoHpa: Float? = null,
        /** Altitude de atmosfera padrao — util para VARIACAO, nao para cota absoluta. */
        val altitudeBarometricaM: Double? = null
    )

    /** Retrato completo do instante da captura. Montado sob demanda, nunca observado. */
    data class Leitura(
        val posicao: Posicao,
        val orientacao: Orientacao,
        val barometro: Barometro
    )

    enum class Qualidade { SEM_SINAL, VENCIDA, RUIM, ACEITAVEL, BOA }

    enum class PrecisaoBussola { DESCONHECIDA, INUTILIZAVEL, BAIXA, MEDIA, ALTA;
        /** Abaixo de MEDIA o azimute nao serve para laudo e o app precisa dizer isso. */
        val precisaCalibrar: Boolean get() = this == INUTILIZAVEL || this == BAIXA
    }

    // ------------------------------------------------------------------ fluxos

    private val _posicao = MutableStateFlow(Posicao())
    val posicao: StateFlow<Posicao> = _posicao

    private val _orientacao = MutableStateFlow(Orientacao())
    val orientacao: StateFlow<Orientacao> = _orientacao

    private val _barometro = MutableStateFlow(Barometro())
    val barometro: StateFlow<Barometro> = _barometro

    /** Ultimo aviso de falha ao ligar o GNSS, para a tela mostrar em vez de silenciar. */
    private val _falha = MutableStateFlow<String?>(null)
    val falha: StateFlow<String?> = _falha

    fun leituraAtual(): Leitura = Leitura(_posicao.value, _orientacao.value, _barometro.value)

    // ------------------------------------------------------------------ ciclo de vida

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotacao = FloatArray(9)
    private val vetorCurto = FloatArray(4)
    private var ultimaOrientacaoMs = 0L
    private var ligado = false

    companion object {
        /**
         * Prazo de validade de um ponto de GNSS parado. O receptor entrega uma leitura por
         * segundo; passados quinze, ou o sinal caiu ou a localizacao foi desligada, e o ponto
         * guardado deixa de valer como "onde estou".
         */
        const val VALIDADE_FIX_MS = 15_000L
    }

    /**
     * Vigia do prazo: um StateFlow so reemite quando alguem escreve nele, e o tempo passando
     * nao escreve nada. Sem este relogio a tela ficaria eternamente verde com um ponto morto.
     */
    private val relogio = android.os.Handler(android.os.Looper.getMainLooper())
    private val vigiaDeValidade = object : Runnable {
        override fun run() {
            val p = _posicao.value
            if (p.temPosicao) {
                val venceu = System.currentTimeMillis() - p.instante > VALIDADE_FIX_MS
                if (venceu != p.vencida) _posicao.value = p.copy(vencida = venceu)
            }
            if (ligado) relogio.postDelayed(this, 2_000L)
        }
    }

    /**
     * BUG corrigido, tres de uma vez:
     *
     * 1. So o GPS_PROVIDER era pedido. Debaixo de mata fechada, dentro de galpao ou nos
     *    primeiros segundos apos abrir o app, o selo ficava em "SEM SINAL" e toda foto era
     *    gravada sem coordenada. Agora o provedor de rede tambem entra, como ponte ate o
     *    GNSS fixar, e a ultima posicao conhecida serve de semente imediata — marcada como
     *    aproximada, para nunca ser confundida com leitura de GNSS.
     * 2. `iniciar()` e chamado de novo a cada vez que a permissao muda; sem guarda, os
     *    ouvintes eram registrados em duplicata e o consumo de bateria dobrava.
     * 3. A falha de permissao era engolida por um `runCatching {}` vazio: o app parecia
     *    apenas "sem sinal", sem dizer que o problema era outro.
     */
    /**
     * Ligado, GNSS e bussola atualizam com menor frequencia — mais bateria dura numa vistoria
     * longa em area rural, ao custo de a leitura reagir mais devagar. Lido dentro de [iniciar];
     * para o valor novo valer, quem alterna chama [parar] e [iniciar] de novo (ver
     * `CapturaViewModel.alternarEconomiaDeBateria`).
     */
    var economiaDeBateria: Boolean = false

    @SuppressLint("MissingPermission")
    fun iniciar() {
        if (ligado) return
        ligado = true
        _falha.value = null

        val intervaloGnssMs = if (economiaDeBateria) 5_000L else 1_000L
        var algumProvedor = false
        var permissaoNegada = false
        for (provedor in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            runCatching {
                if (lm.isProviderEnabled(provedor)) {
                    lm.requestLocationUpdates(provedor, intervaloGnssMs, 0f, this)
                    algumProvedor = true
                    lm.getLastKnownLocation(provedor)?.let { semear(it) }
                }
            }.onFailure { if (it is SecurityException) permissaoNegada = true }
        }
        // As duas causas produziam a MESMA frase, e a frase falava do GPS. O perito ia ao
        // painel de atalhos, via o GPS ligado, e nunca descobria que faltava a permissao.
        _falha.value = when {
            permissaoNegada ->
                "Sem permissão de localização — libere em Ajustes › Apps › Perícia › Permissões."
            !algumProvedor ->
                "Localização desligada no aparelho — ligue o GPS antes de fotografar."
            else -> null
        }

        relogio.removeCallbacks(vigiaDeValidade)
        relogio.post(vigiaDeValidade)

        val atrasoRotacao = if (economiaDeBateria) SensorManager.SENSOR_DELAY_NORMAL else SensorManager.SENSOR_DELAY_UI
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sm.registerListener(this, it, atrasoRotacao)
        }
        sm.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    /** Semente: ponto de partida imediato, que nunca sobrescreve uma leitura ao vivo. */
    private fun semear(l: Location) {
        if (_posicao.value.lat == null) onLocationChanged(l)
    }

    fun parar() {
        ligado = false
        relogio.removeCallbacks(vigiaDeValidade)
        runCatching { lm.removeUpdates(this) }
        sm.unregisterListener(this)
    }

    // ------------------------------------------------------------------ sensores

    override fun onLocationChanged(location: Location) {
        val anterior = _posicao.value
        // O provedor de rede nao pode atropelar um ponto de GNSS recente: a rede erra centenas
        // de metros e sobrescrever seria trocar prova por palpite.
        val vindoDaRede = location.provider == LocationManager.NETWORK_PROVIDER
        val gnssRecente = anterior.provedor == LocationManager.GPS_PROVIDER &&
            System.currentTimeMillis() - anterior.instante < 30_000
        if (vindoDaRede && gnssRecente) return

        _posicao.value = Posicao(
            lat = location.latitude,
            lon = location.longitude,
            precisaoM = if (location.hasAccuracy()) location.accuracy else null,
            altitudeM = if (location.hasAltitude()) location.altitude else anterior.altitudeM,
            instante = System.currentTimeMillis(),
            provedor = location.provider,
            vencida = false
        )
        if (!vindoDaRede) _falha.value = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                // Algumas ROMs antigas entregam o vetor com 5 componentes e
                // getRotationMatrixFromVector lanca IllegalArgumentException — o app fechava
                // sozinho ao mexer no aparelho. Copiar os quatro primeiros resolve.
                val v = if (event.values.size > 4) {
                    vetorCurto.also { System.arraycopy(event.values, 0, it, 0, 4) }
                } else {
                    event.values
                }
                runCatching { SensorManager.getRotationMatrixFromVector(rotacao, v) }
                    .onFailure { return }

                // BUG corrigido, e dos que contaminam a prova: o azimute vinha de
                // getOrientation, que devolve a direcao do TOPO do aparelho. Fotografando em
                // pe, o topo aponta para o ceu e o numero gira sozinho — e ia assim para a
                // legenda queimada na foto. Agora sai do eixo da camera. Ver Orientacoes.
                val azimute = Orientacoes.azimuteCameraGraus(rotacao)
                val elevacao = Orientacoes.elevacaoCameraGraus(rotacao)
                val superficie = Orientacoes.inclinacaoSuperficieGraus(rotacao)

                // A bussola emitia a ~60 leituras por segundo, e cada emissao redesenhava a
                // tela de camera inteira. Duas guardas: so emite se mudou de verdade, e no
                // maximo cinco vezes por segundo. A bussola de celular nao chega perto de
                // 0,5 grau de resolucao, entao nada de util se perde.
                val agora = System.currentTimeMillis()
                val anterior = _orientacao.value
                val mudouRumo = when {
                    azimute == null || anterior.azimuteGraus == null ->
                        (azimute == null) != (anterior.azimuteGraus == null)
                    else -> Orientacoes.diferencaAngular(anterior.azimuteGraus, azimute) > 0.5f
                }
                val mudouInclinacao =
                    abs((anterior.inclinacaoSuperficieGraus ?: -99f) - superficie) > 0.3f
                if ((mudouRumo || mudouInclinacao) && agora - ultimaOrientacaoMs >= 200L) {
                    ultimaOrientacaoMs = agora
                    _orientacao.value = anterior.copy(
                        azimuteGraus = azimute,
                        elevacaoGraus = elevacao,
                        inclinacaoSuperficieGraus = superficie
                    )
                }
            }
            Sensor.TYPE_PRESSURE -> {
                val hpa = event.values[0]
                val anteriorHpa = _barometro.value.pressaoHpa
                if (anteriorHpa != null && abs(anteriorHpa - hpa) < 0.05f) return
                _barometro.value = Barometro(
                    pressaoHpa = hpa,
                    altitudeBarometricaM = SensorManager.getAltitude(
                        SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hpa
                    ).toDouble()
                )
            }
        }
    }

    /**
     * A bussola do celular perde calibracao perto de metal, dentro de carro e depois de queda.
     * O sistema sabe disso e informa; o app precisa repassar, porque um azimute errado vai
     * para a legenda queimada na foto e para o laudo sem nenhum sinal de que esta errado.
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return
        val nova = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> PrecisaoBussola.ALTA
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> PrecisaoBussola.MEDIA
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> PrecisaoBussola.BAIXA
            SensorManager.SENSOR_STATUS_UNRELIABLE -> PrecisaoBussola.INUTILIZAVEL
            else -> PrecisaoBussola.DESCONHECIDA
        }
        if (nova != _orientacao.value.precisaoBussola) {
            _orientacao.value = _orientacao.value.copy(precisaoBussola = nova)
        }
    }

    // Em Android 8 a 10 (API 26-29) estes metodos ainda sao abstratos em LocationListener.
    // Sem eles o app fecha com AbstractMethodError assim que o GPS troca de estado — e o
    // minSdk do projeto e 26, entao esses aparelhos estao no alvo.
    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) _falha.value = null
    }

    /**
     * Antes era um corpo vazio. Quem desligasse a localizacao no atalho da barra de
     * notificacoes — coisa comum, para poupar bateria — continuava vendo o ultimo ponto como
     * se fosse atual. Agora a leitura vence na hora e o app diz o que aconteceu.
     */
    override fun onProviderDisabled(provider: String) {
        if (provider != LocationManager.GPS_PROVIDER) return
        _posicao.value = _posicao.value.copy(vencida = true)
        _falha.value = "GPS desligado agora — a última coordenada não vale mais."
    }

    @Deprecated("Mantido apenas para compatibilidade com Android 8-10")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
}
