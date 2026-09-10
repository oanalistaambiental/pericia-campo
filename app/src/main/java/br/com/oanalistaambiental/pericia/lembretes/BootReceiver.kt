package br.com.oanalistaambiental.pericia.lembretes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.oanalistaambiental.pericia.dados.Banco

/**
 * Reagenda todos os avisos pendentes após reiniciar o aparelho — o `AlarmManager` esquece tudo
 * no reboot, e sem isto um prazo cadastrado antes de desligar o celular nunca mais avisaria.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val banco = Banco(context)
        runCatching {
            banco.condicionantes()
                .filter { !it.cumprida }
                .forEach { LembreteCondicionante.agendar(context, it) }
        }
        banco.close()
    }
}
