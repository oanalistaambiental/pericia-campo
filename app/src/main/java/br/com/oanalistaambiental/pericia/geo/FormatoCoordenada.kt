package br.com.oanalistaambiental.pericia.geo

import java.util.Locale

/** Como a coordenada aparece primeiro — na legenda queimada na foto e nas telas que mostram mais de um formato. */
enum class FormatoCoordenada {
    UTM, GRAUS_DECIMAIS, GMS;

    val rotulo: String get() = when (this) {
        UTM -> "UTM SIRGAS 2000"
        GRAUS_DECIMAIS -> "Graus decimais"
        GMS -> "Grau, minuto, segundo"
    }
}

/** Uma linha formatada no padrão escolhido — usa sempre os mesmos formatadores do resto do app, nunca duplica a conta. */
fun formatarPreferido(lat: Double, lon: Double, formato: FormatoCoordenada): String = when (formato) {
    FormatoCoordenada.UTM -> Utm.projetar(lat, lon).formatado()
    FormatoCoordenada.GRAUS_DECIMAIS -> "%.6f, %.6f".format(Locale.US, lat, lon)
    FormatoCoordenada.GMS -> Medicao.formatarGms(lat, lon)
}
