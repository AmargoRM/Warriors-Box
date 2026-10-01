package com.warriorsbox.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.warriorsbox.app.WarriorsApp
import com.warriorsbox.app.notifications.Notifier
import com.warriorsbox.app.updates.UpdateManager
import com.warriorsbox.core.engine.ExternalSources
import com.warriorsbox.core.model.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Revisa una vez al día si hay una versión nueva publicada en GitHub. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as WarriorsApp).container
        if (!c.settings.current().autoUpdateCheck || !c.updates.enabled) return Result.success()
        when (val r = c.updates.check()) {
            is UpdateManager.CheckResult.Available -> Notifier.show(
                applicationContext, Notifier.ID_UPDATE, Notifier.CHANNEL_UPDATES,
                "Nueva versión ${r.info.versionName} disponible",
                r.info.notas.ifBlank { "Toca para ver las novedades y actualizar." },
                Notifier.openAppIntent(applicationContext, Notifier.ID_UPDATE) { putExtra(Notifier.EXTRA_OPEN_UPDATE, true) },
            )
            is UpdateManager.CheckResult.Error -> return Result.retry()
            UpdateManager.CheckResult.UpToDate -> Unit
        }
        return Result.success()
    }
}

/**
 * Sincroniza la biblioteca con fuentes públicas (sin IA):
 * free-exercise-db (dominio público) y wger (CC-BY-SA).
 */
class CatalogSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val c = (applicationContext as WarriorsApp).container
        val found = mutableListOf<Exercise>()
        var anySuccess = false
        runCatching {
            found += ExternalSources.parseFreeExerciseDb(fetch(c, ExternalSources.FEDB_JSON_URL))
            anySuccess = true
        }
        val existingIds = c.exercises.allNow().map { it.id }.toSet()
        val newFromFedb = found.filter { it.id !in existingIds }
        c.exercises.saveSynced(newFromFedb, "free-exercise-db")
        val wger = mutableListOf<Exercise>()
        runCatching {
            var offset = 0
            var pages = 0
            do {
                val page = ExternalSources.parseWgerPage(fetch(c, ExternalSources.WGER_URL + offset))
                wger += page.exercises
                offset += 100
                pages++
            } while (page.hasNext && pages < 20)
            anySuccess = true
        }
        c.exercises.saveSynced(wger, "wger")
        if (anySuccess) {
            c.settings.setLastCatalogSync(System.currentTimeMillis())
            Result.success(androidx.work.workDataOf("nuevos" to newFromFedb.size + wger.size))
        } else {
            Result.retry()
        }
    }

    private fun fetch(c: com.warriorsbox.app.AppContainer, url: String): String =
        c.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            r.body?.string() ?: error("vacío")
        }
}

/** Recordatorio mensual de hacer copia de seguridad. */
class BackupReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as WarriorsApp).container
        val last = c.settings.current().lastBackupAt
        val days = (System.currentTimeMillis() - last) / (24 * 3600 * 1000L)
        if (c.users.count() > 0 && days >= 30) {
            Notifier.show(
                applicationContext, Notifier.ID_BACKUP, Notifier.CHANNEL_BACKUP, "Haz una copia de seguridad",
                if (last == 0L) "Todavía no guardaste ninguna copia. Si pierdes el celular, se pierden tus datos."
                else "Tu última copia tiene $days días. Ajustes → Copia de seguridad → Exportar.",
            )
        }
        return Result.success()
    }
}

/**
 * Modo coach: envía lo pendiente y recoge planes, confirmaciones y avisos del buzón.
 * Corre cada hora con internet, y también poco después de cada cambio.
 */
class CoachSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as WarriorsApp).container
        val result = c.coach.sync()
        return if (result.offline && c.coach.hasPendingDeliveries()) Result.retry() else Result.success()
    }
}

object WorkScheduler {
    const val SYNC_NOW = "sincronizar-ahora"
    private const val COACH_SOON = "coach-pronto"

    fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniquePeriodicWork(
            "revisar-actualizaciones", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS).setConstraints(network)
                .setInitialDelay(1, TimeUnit.HOURS).build(),
        )
        wm.enqueueUniquePeriodicWork(
            "sincronizar-catalogo", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CatalogSyncWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build())
                .setInitialDelay(1, TimeUnit.DAYS).build(),
        )
        wm.enqueueUniquePeriodicWork(
            "coach-buzon", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CoachSyncWorker>(1, TimeUnit.HOURS).setConstraints(network).build(),
        )
        wm.enqueueUniquePeriodicWork(
            "recordar-copia", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupReminderWorker>(7, TimeUnit.DAYS).setInitialDelay(7, TimeUnit.DAYS).build(),
        )
    }

    fun coachSyncSoon(context: Context, delaySeconds: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            COACH_SOON, androidx.work.ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CoachSyncWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
        )
    }

    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_NOW, androidx.work.ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CatalogSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
        )
    }
}
