package com.warriorsbox.app

import android.content.Context
import com.warriorsbox.app.data.BackupManager
import com.warriorsbox.app.data.ExerciseRepository
import com.warriorsbox.app.data.PhotoStore
import com.warriorsbox.app.data.PlanRepository
import com.warriorsbox.app.data.SessionRepository
import com.warriorsbox.app.data.SettingsStore
import com.warriorsbox.app.data.StatsRepository
import com.warriorsbox.app.data.UserRepository
import com.warriorsbox.app.data.db.AppDatabase
import com.warriorsbox.app.share.ShareCardRenderer
import com.warriorsbox.app.updates.UpdateManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Dependencias de la app (inyección manual, simple y sin magia). */
class AppContainer(context: Context, val db: AppDatabase = AppDatabase.build(context)) {
    val settings = SettingsStore(context)
    val photos = PhotoStore(context)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "WarriorsBox/${BuildConfig.VERSION_NAME} (Android)")
                    .build(),
            )
        }
        .build()
    val exercises = ExerciseRepository(context, db.extras())
    val users = UserRepository(db, photos)
    val plans = PlanRepository(db, exercises, users)
    val sessions = SessionRepository(db, exercises, plans, users)
    val stats = StatsRepository(db)
    val backup = BackupManager(context, db, settings, photos)
    val updates = UpdateManager(context, settings, backup, http)
    val share = ShareCardRenderer(context)
    val trainerLock = TrainerLock()
}

/** Desbloqueo temporal del modo entrenador (10 minutos tras ingresar el PIN). */
class TrainerLock {
    private var unlockedUntil = 0L
    fun isUnlocked(): Boolean = System.currentTimeMillis() < unlockedUntil
    fun unlock() {
        unlockedUntil = System.currentTimeMillis() + 10 * 60 * 1000L
    }
    fun lock() {
        unlockedUntil = 0
    }
}
