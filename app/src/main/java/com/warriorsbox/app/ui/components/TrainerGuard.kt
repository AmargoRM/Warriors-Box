package com.warriorsbox.app.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warriorsbox.app.data.AppSettings
import com.warriorsbox.core.engine.Pin

/**
 * Protección del modo entrenador. Uso:
 *   val guard = rememberTrainerGuard()
 *   guard.run { accionProtegida() }
 *   guard.Dialog()
 */
class TrainerGuard internal constructor(
    val active: Boolean,
    private val isUnlocked: () -> Boolean,
    private val request: (() -> Unit) -> Unit,
) {
    fun run(action: () -> Unit) {
        if (!active || isUnlocked()) action() else request(action)
    }
}

@Composable
fun rememberTrainerGuard(): Pair<TrainerGuard, @Composable () -> Unit> {
    val c = container()
    val context = LocalContext.current
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val guard = TrainerGuard(
        active = settings.trainerMode,
        isUnlocked = { c.trainerLock.isUnlocked() },
        request = { pending = it },
    )
    val dialog: @Composable () -> Unit = {
        val action = pending
        if (action != null) {
            PinDialog(
                title = "Modo entrenador",
                message = "Esta acción está protegida. Ingresa el PIN del entrenador.",
                onConfirm = { pin ->
                    if (Pin.verify(pin, settings.trainerPin)) {
                        c.trainerLock.unlock()
                        pending = null
                        action()
                    } else {
                        Toast.makeText(context, "PIN incorrecto", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { pending = null },
            )
        }
    }
    return guard to dialog
}
