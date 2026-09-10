package br.com.oanalistaambiental.pericia.lembretes

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import br.com.oanalistaambiental.pericia.dados.Condicionante

private const val CANAL_ID = "prazos"
private const val DIAS_ANTECEDENCIA = 15L
private const val UM_DIA_MS = 24L * 60 * 60 * 1000

/**
 * Aviso local de prazo de condicionante — sem servidor, sem conta, só `AlarmManager` +
 * `NotificationManager` no próprio aparelho. Um alarme por condicionante, disparado
 * [DIAS_ANTECEDENCIA] dias antes do prazo cadastrado.
 */
object LembreteCondicionante {

    fun garantirCanal(context: Context) {
        val gerenciador = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (gerenciador.getNotificationChannel(CANAL_ID) == null) {
            gerenciador.createNotificationChannel(
                NotificationChannel(CANAL_ID, "Prazos e condicionantes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Aviso antes do vencimento de uma condicionante cadastrada."
                }
            )
        }
    }

    /**
     * Prazo já vencido, cumprido, ou já dentro da janela de antecedência não agenda nada —
     * avisar de algo que já devia ter vencido só confundiria; a lista da ferramenta já mostra
     * "vencida" em vermelho, e isso não deve virar notificação atrasada no dia em que a pessoa
     * cadastrou o prazo.
     */
    fun agendar(context: Context, condicionante: Condicionante) {
        if (condicionante.id <= 0L || condicionante.cumprida) return
        val disparo = condicionante.prazoData - DIAS_ANTECEDENCIA * UM_DIA_MS
        if (disparo <= System.currentTimeMillis()) return
        garantirCanal(context)
        val gerenciador = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendente = pendingIntentDe(context, condicionante.id, condicionante.descricao)
        runCatching {
            gerenciador.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, disparo, pendente)
        }
    }

    fun cancelar(context: Context, condicionanteId: Long) {
        val gerenciador = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        gerenciador.cancel(pendingIntentDe(context, condicionanteId, null))
    }

    private fun pendingIntentDe(context: Context, id: Long, descricao: String?): PendingIntent {
        val intent = Intent(context, LembreteReceiver::class.java).apply {
            putExtra("id", id)
            if (descricao != null) putExtra("descricao", descricao)
        }
        return PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
