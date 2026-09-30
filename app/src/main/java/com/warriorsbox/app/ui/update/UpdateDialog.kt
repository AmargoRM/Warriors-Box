package com.warriorsbox.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.warriorsbox.app.ui.components.container
import com.warriorsbox.app.updates.UpdateManager
import com.warriorsbox.core.engine.UpdateInfo
import kotlinx.coroutines.launch
import java.io.File

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class NeedsPermission(val info: UpdateInfo, val apk: File) : UpdateState
    data class Message(val title: String, val text: String) : UpdateState
}

/**
 * Revisa actualizaciones al abrir la app (máx. cada 12 h) o cuando se pide desde Ajustes / notificación,
 * y muestra: versión actual → nueva, notas, Actualizar ahora / Más tarde / Omitir esta versión.
 */
@Composable
fun UpdateDialogHost(forceOpen: Boolean, onClosed: () -> Unit) {
    val c = container()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var manual by remember { mutableStateOf(false) }

    suspend fun check(respectSkipped: Boolean) {
        when (val r = c.updates.check(respectSkipped)) {
            is UpdateManager.CheckResult.Available -> state = UpdateState.Available(r.info)
            UpdateManager.CheckResult.UpToDate -> state = if (manual) UpdateState.Message("Estás al día", "Tienes la versión más reciente (${c.updates.installedVersionName}).") else UpdateState.Idle
            is UpdateManager.CheckResult.Error -> state = if (manual) UpdateState.Message("No se pudo revisar", r.message) else UpdateState.Idle
        }
    }

    // Revisión automática al abrir.
    LaunchedEffect(Unit) {
        val s = c.settings.current()
        val twelveHours = 12 * 3600 * 1000L
        if (c.updates.enabled && s.autoUpdateCheck && System.currentTimeMillis() - s.lastUpdateCheck > twelveHours) check(true)
    }
    // Revisión manual (Ajustes o notificación).
    LaunchedEffect(forceOpen) {
        if (forceOpen) {
            manual = true
            state = UpdateState.Checking
            check(false)
        }
    }

    fun close() {
        state = UpdateState.Idle
        manual = false
        onClosed()
    }

    fun download(info: UpdateInfo) = scope.launch {
        state = UpdateState.Downloading(info, 0f)
        runCatching { c.updates.download(info) { p -> state = UpdateState.Downloading(info, p) } }
            .onSuccess { apk ->
                if (c.updates.canInstall()) {
                    c.updates.install(apk)
                    close()
                } else {
                    state = UpdateState.NeedsPermission(info, apk)
                }
            }
            .onFailure { state = UpdateState.Message("No se pudo actualizar", it.message ?: "Error desconocido") }
    }

    when (val s = state) {
        UpdateState.Idle -> Unit
        UpdateState.Checking -> AlertDialog(
            onDismissRequest = { close() },
            title = { Text("Buscando actualizaciones…") },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { close() }) { Text("Cancelar") } },
        )
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = { if (!s.info.obligatoria) close() },
            title = { Text("Nueva versión disponible") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${c.updates.installedVersionName} → ${s.info.versionName}")
                    if (s.info.notas.isNotBlank()) {
                        Text("Qué hay de nuevo:")
                        Text(s.info.notas)
                    }
                    Text("Se hará una copia de seguridad automática antes de instalar. Tus datos se conservan.")
                }
            },
            confirmButton = { TextButton(onClick = { download(s.info) }) { Text("Actualizar ahora") } },
            dismissButton = {
                Column {
                    if (!s.info.obligatoria) {
                        TextButton(onClick = { close() }) { Text("Más tarde") }
                        TextButton(onClick = {
                            scope.launch { c.settings.setSkippedVersion(s.info.versionCode) }
                            close()
                        }) { Text("Omitir esta versión") }
                    }
                }
            },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Descargando ${s.info.versionName}…") },
            text = { LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
        is UpdateState.NeedsPermission -> AlertDialog(
            onDismissRequest = { close() },
            title = { Text("Falta un permiso") },
            text = {
                Text(
                    "Para instalar la actualización, Android necesita que permitas a Warriors Box \"instalar apps desconocidas\". " +
                        "Toca \"Abrir ajustes\", activa el interruptor, vuelve y toca \"Instalar\".",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (c.updates.canInstall()) {
                        scope.launch { c.updates.install(s.apk) }
                        close()
                    } else {
                        context.startActivity(c.updates.unknownSourcesSettingsIntent())
                    }
                }) { Text(if (c.updates.canInstall()) "Instalar" else "Abrir ajustes") }
            },
            dismissButton = { TextButton(onClick = { close() }) { Text("Cancelar") } },
        )
        is UpdateState.Message -> AlertDialog(
            onDismissRequest = { close() },
            title = { Text(s.title) },
            text = { Text(s.text) },
            confirmButton = { TextButton(onClick = { close() }) { Text("OK") } },
        )
    }
}
