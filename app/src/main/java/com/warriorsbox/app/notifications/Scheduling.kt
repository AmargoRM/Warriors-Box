package com.warriorsbox.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.warriorsbox.app.WarriorsApp
import com.warriorsbox.core.engine.Reminders
import com.warriorsbox.core.engine.Roasts
import com.warriorsbox.core.engine.Stats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Programa las alarmas de recordatorio y del temporizador de descanso. */
object AlarmScheduler {

    private const val ACTION_REMINDER = "com.warriorsbox.app.RECORDATORIO"
    private const val ACTION_REST = "com.warriorsbox.app.DESCANSO"
    const val EXTRA_USER = "usuario"

    private fun reminderIntent(context: Context, userId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (Notifier.ID_REMINDER_BASE + userId).toInt(),
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMINDER).putExtra(EXTRA_USER, userId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun restIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            Notifier.ID_REST,
            Intent(context, RestTimerReceiver::class.java).setAction(ACTION_REST),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Reprograma todos los recordatorios (al abrir la app, al reiniciar el celular o al editar). */
    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext as WarriorsApp
        val reminders = app.container.users.allReminders()
        reminders.forEach { r ->
            if (r.enabled) schedule(context, r.userId, r.daysMask, r.hour, r.minute) else cancelReminder(context, r.userId)
        }
    }

    fun schedule(context: Context, userId: Long, mask: Int, hour: Int, minute: Int) {
        val next = Reminders.nextTrigger(LocalDateTime.now(), mask, hour, minute) ?: return cancelReminder(context, userId)
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val alarm = (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        // Ventana de 10 minutos: no requiere el permiso de alarmas exactas y cuida la batería.
        alarm.setWindow(AlarmManager.RTC_WAKEUP, millis, 10 * 60 * 1000L, reminderIntent(context, userId))
    }

    fun cancelReminder(context: Context, userId: Long) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(reminderIntent(context, userId))
    }

    fun scheduleRestEnd(context: Context, secondsFromNow: Int) {
        val alarm = (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        val at = System.currentTimeMillis() + secondsFromNow * 1000L
        val pi = restIntent(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()) {
            try {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                return
            } catch (_: SecurityException) {
                // Sin permiso de alarma exacta: se usa una inexacta.
            }
        }
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancelRest(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(restIntent(context))
        Notifier.cancel(context, Notifier.ID_REST)
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val userId = intent.getLongExtra(AlarmScheduler.EXTRA_USER, -1)
        if (userId < 0) return
        runAsync {
            val app = context.applicationContext as WarriorsApp
            val c = app.container
            val user = c.users.get(userId) ?: return@runAsync
            val reminder = c.users.reminderNow(userId) ?: return@runAsync
            val plan = c.plans.activePlanNow(userId)
            val today = LocalDate.now()
            val dayIndex = Reminders.planDayIndex(today)
            var text = "Hoy toca entrenar. ¡Vamos, ${user.name}!"
            var dayId: Long? = null
            var focus: String? = null
            val rude = userId in c.settings.current().rudeUsers
            if (plan != null && dayIndex != null) {
                val days = c.plans.daysNow(plan.id)
                val sessions = c.sessions.sessionsNow(userId).filter { s -> days.any { it.id == s.dayId } }
                val week = c.plans.currentWeek(days, sessions)
                days.firstOrNull { it.week == week && it.dayIndex == dayIndex }?.let {
                    text = "Hoy toca: ${it.focus} — Semana ${it.week} ${Reminders.PLAN_DAY_NAMES[it.dayIndex]}"
                    dayId = it.id
                    focus = it.focus
                }
            }
            if (rude) text = Roasts.reminder(focus)
            val openDay = dayId
            Notifier.show(
                context, (Notifier.ID_REMINDER_BASE + userId).toInt(), Notifier.CHANNEL_REMINDERS,
                "Warriors Box · ${user.name}", text,
                Notifier.openAppIntent(context, (Notifier.ID_REMINDER_BASE + userId).toInt()) {
                    putExtra(Notifier.EXTRA_OPEN_USER, userId)
                    if (openDay != null) putExtra(Notifier.EXTRA_OPEN_DAY, openDay)
                },
            )
            // Aviso si pasaron 2 días programados sin entrenar.
            if (reminder.missedAlert) {
                val last = c.sessions.sessionsNow(userId).filter { it.completed }.maxOfOrNull { it.startedAt }
                    ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                val scheduledSince = (1..14).map { today.minusDays(it.toLong()) }
                    .filter { Reminders.isActiveOn(reminder.daysMask, it.dayOfWeek) }
                    .filter { last == null || it.isAfter(last) }
                if (last != null && scheduledSince.size >= 2) {
                    Notifier.show(
                        context, (Notifier.ID_MISSED_BASE + userId).toInt(), Notifier.CHANNEL_REMINDERS,
                        "${user.name}, ¿todo bien?",
                        if (rude) Roasts.missed(Stats.daysSince(last, today) ?: 2)
                        else "Llevas ${Stats.daysSince(last, today)} días sin entrenar. Las pesas empiezan a sospechar que te fuiste con otras.",
                    )
                }
            }
            AlarmScheduler.schedule(context, userId, reminder.daysMask, reminder.hour, reminder.minute)
        }
    }
}

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifier.show(context, Notifier.ID_REST, Notifier.CHANNEL_REST, "¡Descanso terminado!", "A la siguiente serie. El sofá puede esperar.")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync { AlarmScheduler.rescheduleAll(context) }
    }
}
