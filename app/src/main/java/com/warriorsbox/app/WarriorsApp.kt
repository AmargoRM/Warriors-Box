package com.warriorsbox.app

import android.app.Application
import com.warriorsbox.app.notifications.AlarmScheduler
import com.warriorsbox.app.notifications.Notifier
import com.warriorsbox.app.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WarriorsApp : Application() {

    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        runCatching { WorkScheduler.scheduleAll(this) }
        appScope.launch { runCatching { AlarmScheduler.rescheduleAll(this@WarriorsApp) } }
    }

    /** Solo para pruebas: reemplaza las dependencias (por ejemplo, con una base en memoria). */
    fun replaceContainer(newContainer: AppContainer) {
        container = newContainer
    }
}
