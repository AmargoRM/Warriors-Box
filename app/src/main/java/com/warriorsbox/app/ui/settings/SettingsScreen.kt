package com.warriorsbox.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warriorsbox.app.data.AppSettings
import com.warriorsbox.app.ui.components.AppBackground
import com.warriorsbox.app.ui.components.ConfirmDialog
import com.warriorsbox.app.ui.components.PinDialog
import com.warriorsbox.app.ui.components.SectionTitle
import com.warriorsbox.app.ui.components.TipBox
import com.warriorsbox.app.ui.components.WbCard
import com.warriorsbox.app.ui.components.WbTopBar
import com.warriorsbox.app.ui.components.container
import com.warriorsbox.app.ui.theme.WbRed
import com.warriorsbox.app.work.WorkScheduler
import com.warriorsbox.core.engine.Pin
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onReminders: (Long) -> Unit,
    onTrainerPanel: () -> Unit,
    onLibrary: () -> Unit,
    onAbout: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val c = container()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val users by c.users.users.collectAsStateWithLifecycle(emptyList())
    var pinFlow by remember { mutableStateOf<String?>(null) }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var pickReminderUser by remember { mutableStateOf(false) }
    var syncCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(settings.lastCatalogSync) { syncCounts = c.exercises.countBySource() }

    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = c.photos.save(uri, "fondo") ?: return@launch
            c.photos.delete(settings.backgroundPath)
            c.settings.setBackground(path)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            runCatching { c.backup.exportTo(uri) }
                .onSuccess { Toast.makeText(context, "Copia guardada", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }

    AppBackground(settings.backgroundPath, settings.veil) {
        Scaffold(containerColor = Color.Transparent, topBar = { WbTopBar("Ajustes", onBack) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WbCard {
                    SectionTitle("Fondo de pantalla")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { backgroundPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Elegir foto") }
                        if (settings.backgroundPath != null) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    c.photos.delete(settings.backgroundPath)
                                    c.settings.setBackground(null)
                                }
                            }) { Text("Restaurar fondo original") }
                        }
                    }
                    Text("Oscurecer el fondo: ${(settings.veil * 100).toInt()} %", modifier = Modifier.padding(top = 8.dp))
                    Slider(value = settings.veil, onValueChange = { v -> scope.launch { c.settings.setVeil(v) } }, valueRange = 0f..0.8f)
                    Text("Cada usuario también puede tener su propio fondo (Usuarios → perfil).", style = MaterialTheme.typography.labelMedium)
                }

                WbCard {
                    SectionTitle("Unidades")
                    SwitchRow("Usar libras (lb)", settings.useLb) { scope.launch { c.settings.setUseLb(it) } }
                }

                WbCard {
                    SectionTitle("Recordatorios")
                    Text("Elige a quién recordarle sus días de entrenamiento.")
                    Button(onClick = { pickReminderUser = true }, enabled = users.isNotEmpty(), modifier = Modifier.padding(top = 8.dp)) { Text("Configurar recordatorios") }
                }

                WbCard {
                    SectionTitle("Modo entrenador")
                    Text("Protege con PIN la edición de planes y perfiles. Los alumnos pueden registrar sus sesiones sin PIN.", style = MaterialTheme.typography.bodySmall)
                    SwitchRow("Modo entrenador activo", settings.trainerMode) { on ->
                        pinFlow = if (on) "crear" else "desactivar"
                    }
                    if (settings.trainerMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { if (c.trainerLock.isUnlocked()) onTrainerPanel() else pinFlow = "panel" }) { Text("Panel de alumnos") }
                            OutlinedButton(onClick = { pinFlow = "cambiar" }) { Text("Cambiar PIN") }
                        }
                    }
                }

                WbCard {
                    SectionTitle("Copia de seguridad")
                    Text(
                        "Tus datos viven solo en este celular. Exporta una copia (.zip con datos y fotos) y guárdala en Drive o en tu computadora.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        if (settings.lastBackupAt == 0L) "Nunca se hizo una copia." else "Última copia: " + DateFormat.getDateTimeInstance().format(Date(settings.lastBackupAt)),
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = if (settings.lastBackupAt == 0L) WbRed else MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportLauncher.launch("warriors-box-copia.zip") }) { Text("Exportar") }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) { Text("Importar") }
                    }
                    val autos = c.backup.autoBackups()
                    if (autos.isNotEmpty()) Text("Copias automáticas antes de actualizar: ${autos.size}", style = MaterialTheme.typography.labelMedium)
                }

                WbCard {
                    SectionTitle("Biblioteca de ejercicios")
                    Text(
                        "Se actualiza sola una vez por semana con Wi-Fi desde fuentes públicas (free-exercise-db y wger). Sin inteligencia artificial.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        if (settings.lastCatalogSync == 0L) "Aún no se sincronizó." else "Última sincronización: " + DateFormat.getDateTimeInstance().format(Date(settings.lastCatalogSync)) +
                            syncCounts.entries.joinToString(prefix = " (", postfix = ")") { "${it.key}: ${it.value}" },
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 6.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            WorkScheduler.syncNow(context)
                            Toast.makeText(context, "Sincronizando en segundo plano…", Toast.LENGTH_SHORT).show()
                        }) { Text("Sincronizar ahora") }
                        OutlinedButton(onClick = onLibrary) { Text("Ver biblioteca") }
                    }
                }

                WbCard {
                    SectionTitle("Actualizaciones")
                    Text("Versión instalada: ${c.updates.installedVersionName}")
                    if (!c.updates.enabled) TipBox("Versión de prueba: instala la versión publicada (Releases) para recibir actualizaciones.")
                    SwitchRow("Avisarme de nuevas versiones", settings.autoUpdateCheck) { scope.launch { c.settings.setAutoUpdateCheck(it) } }
                    Button(onClick = onCheckUpdate) { Text("Buscar actualizaciones") }
                }

                TextButton(onClick = onAbout, modifier = Modifier.fillMaxWidth()) { Text("Acerca de y créditos") }
            }
        }
    }

    when (pinFlow) {
        "crear" -> PinDialog("Crear PIN del entrenador", "Elige 4 dígitos. Si lo olvidas, deberás borrar los datos de la app.", onConfirm = { pin ->
            scope.launch { c.settings.setTrainerMode(true, Pin.encode(pin)) }
            c.trainerLock.unlock()
            pinFlow = null
        }, onDismiss = { pinFlow = null })
        "desactivar", "panel", "cambiar" -> PinDialog("PIN del entrenador", null, onConfirm = { pin ->
            if (!Pin.verify(pin, settings.trainerPin)) {
                Toast.makeText(context, "PIN incorrecto", Toast.LENGTH_SHORT).show()
            } else {
                c.trainerLock.unlock()
                when (pinFlow) {
                    "desactivar" -> {
                        scope.launch { c.settings.setTrainerMode(false, null) }
                        c.trainerLock.lock()
                        pinFlow = null
                    }
                    "panel" -> {
                        pinFlow = null
                        onTrainerPanel()
                    }
                    else -> pinFlow = "crear"
                }
            }
        }, onDismiss = { pinFlow = null })
    }

    confirmImport?.let { uri ->
        ConfirmDialog(
            title = "¿Restaurar esta copia?",
            text = "ATENCIÓN: se BORRARÁN todos los datos actuales (usuarios, planes, sesiones) y se reemplazarán por los de la copia. " +
                "Si no estás seguro, primero exporta una copia de lo que tienes ahora.",
            confirm = "Restaurar",
            onConfirm = {
                confirmImport = null
                scope.launch {
                    runCatching { c.backup.importFrom(uri) }
                        .onSuccess { Toast.makeText(context, "Copia restaurada", Toast.LENGTH_LONG).show() }
                        .onFailure { Toast.makeText(context, "No se pudo restaurar: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            },
            onDismiss = { confirmImport = null },
        )
    }

    if (pickReminderUser) {
        AlertDialog(
            onDismissRequest = { pickReminderUser = false },
            title = { Text("¿Para quién?") },
            text = {
                Column {
                    users.forEach { u ->
                        TextButton(onClick = { pickReminderUser = false; onReminders(u.id) }, modifier = Modifier.fillMaxWidth()) { Text(u.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickReminderUser = false }) { Text("Cerrar") } },
        )
    }
}
