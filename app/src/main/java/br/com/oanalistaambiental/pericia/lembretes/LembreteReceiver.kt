package br.com.oanalistaambiental.pericia.lembretes

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import br.com.oanalistaambiental.pericia.MainActivity

/** Recebe o alarme agendado por [LembreteCondicionante.agendar] e mostra a notificação. */
class LembreteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", 0L)
        val descricao = intent.getStringExtra("descricao") ?: "Condicionante com prazo próximo"

        LembreteCondicionante.garantirCanal(context)

        val abrir = PendingIntent.getActivity(
            context, id.toInt(), Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificacao = NotificationCompat.Builder(context, "prazos")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Prazo se aproxima")
            .setContentText(descricao)
            .setStyle(NotificationCompat.BigTextStyle().bigText(descricao))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()

        val temPermissao = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (temPermissao) {
            NotificationManagerCompat.from(context).notify(id.toInt(), notificacao)
        }
    }
}
