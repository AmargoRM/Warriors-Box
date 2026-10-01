package com.warriorsbox.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.warriorsbox.app.MainActivity
import com.warriorsbox.app.R

object Notifier {
    const val CHANNEL_REMINDERS = "recordatorios"
    const val CHANNEL_REST = "descanso"
    const val CHANNEL_UPDATES = "actualizaciones"
    const val CHANNEL_BACKUP = "respaldo"
    const val CHANNEL_COACH = "coach"

    const val ID_REST = 1001
    const val ID_UPDATE = 1002
    const val ID_BACKUP = 1003
    const val ID_COACH_PLAN = 1004
    const val ID_COACH_ALERT = 1005
    const val ID_REMINDER_BASE = 2000
    const val ID_MISSED_BASE = 3000

    const val EXTRA_OPEN_USER = "abrir_usuario"
    const val EXTRA_OPEN_DAY = "abrir_dia"
    const val EXTRA_OPEN_UPDATE = "abrir_actualizacion"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_REMINDERS, "Recordatorios de entrenamiento", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_REST, "Fin del descanso", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                },
                NotificationChannel(CHANNEL_UPDATES, "Nuevas versiones", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_BACKUP, "Copias de seguridad", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_COACH, "Coach a distancia", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openAppIntent(context: Context, requestCode: Int, extras: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extras()
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun show(context: Context, id: Int, channel: String, title: String, text: String, intent: PendingIntent? = null) {
        if (!canNotify(context)) return
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(intent ?: openAppIntent(context, id))
            .setColor(0xFFE0A95B.toInt())
        if (channel == CHANNEL_REST) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH).setVibrate(longArrayOf(0, 400, 200, 400))
        }
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permiso revocado mientras tanto: no hay nada que hacer.
        }
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)
}
