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

/**
 * Estado do aparelho no momento do clique: posicao, precisao, altitude, azimute, inclinacao.
 *
 * Usa LocationManager (GNSS bruto) em vez do provedor fundido do Google Play Services:
 * menos dependencia, funciona sem servicos Google e entrega precisao declarada pelo sistema,
 * que e o numero que importa para valor probatorio.
 */
class EstadoCampo(private val context: Context) : LocationListener, SensorEventListener {

    data class Leitura(
        val lat: Double? = null,
        val lon: Double? = null,
        val precisaoM: Float? = null,
        val altitudeM: Double? = null,
        val azimuteGraus: Float? = null,
        val inclinacaoGraus: Float? = null,
        val satelites: Int? = null,
        val instante: Long = System.currentTimeMillis()
    ) {
        /** Selo de qualidade do ponto: ensina o perito a esperar mais alguns segundos. */
        val qualidade: Qualidade get() = when {
            precisaoM == null -> Qualidade.SEM_SINAL
            precisaoM <= 5f -> Qualidade.BOA
            precisaoM <= 15f -> Qualidade.ACEITAVEL
            else -> Qualidade.RUIM
        }
    }

    enum class Qualidade { SEM_SINAL, RUIM, ACEITAVEL, BOA }

    private val _leitura = MutableStateFlow(Leitura())
    val leitura: StateFlow<Leitura> = _leitura

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotacao = FloatArray(9)
    private val orientacao = FloatArray(3)
    private var ultimoVetorRotacao: FloatArray? = null

    @SuppressLint("MissingPermission")
    fun iniciar() {
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
        }
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        sm.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun parar() {
        runCatching { lm.removeUpdates(this) }
        sm.unregisterListener(this)
    }

    override fun onLocationChanged(location: Location) {
        _leitura.value = _leitura.value.copy(
            lat = location.latitude,
            lon = location.longitude,
            precisaoM = if (location.hasAccuracy()) location.accuracy else null,
            altitudeM = if (location.hasAltitude()) location.altitude else _leitura.value.altitudeM,
            instante = System.currentTimeMillis()
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                ultimoVetorRotacao = event.values.copyOf()
                SensorManager.getRotationMatrixFromVector(rotacao, event.values)
                SensorManager.getOrientation(rotacao, orientacao)
                val azimute = ((Math.toDegrees(orientacao[0].toDouble()) + 360) % 360).toFloat()
                val inclinacao = Math.toDegrees(orientacao[1].toDouble()).toFloat()
                _leitura.value = _leitura.value.copy(
                    azimuteGraus = azimute,
                    inclinacaoGraus = inclinacao
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
