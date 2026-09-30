package com.warriorsbox.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.warriorsbox.app.notifications.Notifier
import com.warriorsbox.app.ui.WarriorsNavHost
import com.warriorsbox.app.ui.theme.WarriorsTheme

/** Qué abrir al tocar una notificación. */
data class LaunchRequest(val userId: Long? = null, val dayId: Long? = null, val openUpdate: Boolean = false)

class MainActivity : ComponentActivity() {

    private val launchRequest = mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        launchRequest.value = parse(intent)
        setContent {
            WarriorsTheme {
                WarriorsNavHost(
                    launchRequest = launchRequest.value,
                    onLaunchHandled = { launchRequest.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launchRequest.value = parse(intent)
    }

    private fun parse(intent: Intent?): LaunchRequest? {
        if (intent == null) return null
        val user = intent.getLongExtra(Notifier.EXTRA_OPEN_USER, -1).takeIf { it >= 0 }
        val day = intent.getLongExtra(Notifier.EXTRA_OPEN_DAY, -1).takeIf { it >= 0 }
        val update = intent.getBooleanExtra(Notifier.EXTRA_OPEN_UPDATE, false)
        return if (user != null || update) LaunchRequest(user, day, update) else null
    }
}
