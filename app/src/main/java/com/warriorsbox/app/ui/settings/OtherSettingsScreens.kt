package com.warriorsbox.app.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warriorsbox.app.data.AppSettings
import com.warriorsbox.app.data.StudentStatus
import com.warriorsbox.app.data.db.ReminderEntity
import com.warriorsbox.app.notifications.AlarmScheduler
import com.warriorsbox.app.notifications.Notifier
import com.warriorsbox.app.ui.components.AppBackground
import com.warriorsbox.app.ui.components.ChoiceChips
import com.warriorsbox.app.ui.components.LabeledRow
import com.warriorsbox.app.ui.components.SectionTitle
import com.warriorsbox.app.ui.components.TipBox
import com.warriorsbox.app.ui.components.WbCard
import com.warriorsbox.app.ui.components.WbTopBar
import com.warriorsbox.app.ui.components.container
import com.warriorsbox.app.ui.theme.WbGold
import com.warriorsbox.app.ui.theme.WbRed
import com.warriorsbox.app.ui.users.UserAvatar
import com.warriorsbox.core.engine.CatalogSource
import com.warriorsbox.core.engine.Reminders
import kotlinx.coroutines.launch
import java.time.DayOfWeek

@Composable
fun RemindersScreen(userId: Long, onBack: () -> Unit) {
    val c = container()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val user by c.users.observe(userId).collectAsStateWithLifecycle(null)
    val saved by c.users.reminder(userId).collectAsStateWithLifecycle(null)
    var days by remember { mutableStateOf(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)) }
    var hour by remember { mutableStateOf(18) }
    var minute by remember { mutableStateOf(0) }
    var enabled by remember { mutableStateOf(true) }
    var missed by remember { mutableStateOf(true) }
    var initialized by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(saved) {
        val r = saved
        if (r != null && !initialized) {
            days = Reminders.daysOf(r.daysMask).toSet()
            hour = r.hour
            minute = r.minute
            enabled = r.enabled
            missed = r.missedAlert
        }
        initialized = true
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    AppBackground(user?.backgroundPath ?: settings.backgroundPath, settings.veil) {
        Scaffold(containerColor = Color.Transparent, contentColor = Color.White, topBar = { WbTopBar("Recordatorios · ${user?.name ?: ""}", onBack) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WbCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recordatorios activos", Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    Text("Días", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    ChoiceChips(DayOfWeek.entries, days, { Reminders.dayLabel(it) }, { d -> days = if (d in days) days - d else days + d })
                    OutlinedButton(
                        onClick = { TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show() },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Hora: %02d:%02d".format(hour, minute)) }
                    Row(Modifier.fillMaxWidth().clickable { missed = !missed }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Avisar si pasan 2 días programados sin entrenar", Modifier.weight(1f))
                        Switch(checked = missed, onCheckedChange = { missed = it })
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Modo sin filtro 🤬")
                            Text(
                                "Recordatorios groseros en jerga tica (ej. \"Vamos gordo carepicha…\"). Solo para ${user?.name ?: "este usuario"}.",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Switch(
                            checked = userId in settings.rudeUsers,
                            onCheckedChange = { on -> scope.launch { c.settings.setRude(userId, on) } },
                            modifier = Modifier.testTag("modo_sin_filtro"),
                        )
                    }
                    TipBox("La notificación dice qué toca ese día (ej. \"Hoy toca: Pierna — Semana 2 Miércoles\") y al tocarla abre la sesión.")
                }
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canNotify(context)) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        val mask = Reminders.maskOf(days)
                        scope.launch {
                            c.users.saveReminder(
                                ReminderEntity(id = saved?.id ?: 0, userId = userId, daysMask = mask, hour = hour, minute = minute, enabled = enabled, missedAlert = missed),
                            )
                            if (enabled && mask != 0) AlarmScheduler.schedule(context, userId, mask, hour, minute) else AlarmScheduler.cancelReminder(context, userId)
                            val next = Reminders.nextTrigger(java.time.LocalDateTime.now(), mask, hour, minute)
                            savedMessage = if (enabled && next != null) "Guardado. Próximo aviso: ${Reminders.dayLabel(next.dayOfWeek)} ${"%02d:%02d".format(hour, minute)}" else "Recordatorios desactivados."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Guardar") }
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canNotify(context)) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            val text = if (userId in settings.rudeUsers) com.warriorsbox.core.engine.Roasts.reminder("Pierna")
                            else "Hoy toca entrenar. ¡Vamos, ${user?.name ?: ""}!"
                            Notifier.show(context, (Notifier.ID_REMINDER_BASE + userId).toInt(), Notifier.CHANNEL_REMINDERS, "Warriors Box · ${user?.name ?: ""}", text)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Probar recordatorio ahora") }
                savedMessage?.let { Text(it, color = WbGold) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canNotify(context)) {
                    TipBox("Las notificaciones están desactivadas para Warriors Box. Al guardar se pedirá el permiso.", color = WbRed)
                }
            }
        }
    }
}

@Composable
fun TrainerPanelScreen(onBack: () -> Unit, onStudent: (Long) -> Unit, onPlan: (Long) -> Unit) {
    val c = container()
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val users by c.users.users.collectAsStateWithLifecycle(emptyList())
    var students by remember { mutableStateOf<List<StudentStatus>>(emptyList()) }
    LaunchedEffect(users) { students = c.stats.students() }
    AppBackground(settings.backgroundPath, settings.veil) {
        Scaffold(containerColor = Color.Transparent, contentColor = Color.White, topBar = { WbTopBar("Panel de alumnos", onBack) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (students.isEmpty()) Text("Sin alumnos todavía.", color = Color.White)
                students.sortedByDescending { it.alerts.size }.forEach { s ->
                    WbCard(highlight = s.alerts.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(s.user, 48)
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(s.user.name, fontWeight = FontWeight.Bold)
                                Text(s.user.goalEnum.label + " · " + s.user.levelEnum.label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        LabeledRow("Última sesión", s.lastSession?.toString() ?: "—")
                        LabeledRow("Sesiones esta semana", "${s.sessionsThisWeek}")
                        LabeledRow("Cumplimiento del plan", "${s.compliance} %")
                        LabeledRow("Récords (14 días)", "${s.recentRecords}")
                        s.alerts.forEach { Text("⚠ $it", color = WbRed) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            Button(onClick = { onPlan(s.user.id) }) { Text("Plan") }
                            OutlinedButton(onClick = { onStudent(s.user.id) }) { Text("Perfil") }
                        }
                    }
                }
                TipBox("Para pasar un plan a otro celular: abre el plan del alumno → menú ⋮ → Exportar plan, y envía el archivo por WhatsApp o correo.")
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, sources: List<CatalogSource>) {
    val c = container()
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    AppBackground(settings.backgroundPath, settings.veil) {
        Scaffold(containerColor = Color.Transparent, contentColor = Color.White, topBar = { WbTopBar("Acerca de", onBack) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WbCard {
                    Text("Warriors Box", style = MaterialTheme.typography.titleLarge)
                    Text("Versión ${c.updates.installedVersionName} · uso personal, sin Play Store.")
                    TipBox("Esta app no reemplaza la opinión de un médico o entrenador certificado.")
                }
                WbCard {
                    SectionTitle("Fuentes y licencias")
                    val all = sources.map { it.name to it.license } + listOf(
                        "free-exercise-db (github.com/yuhonas/free-exercise-db)" to "Unlicense — dominio público",
                        "wger (wger.de) — ejercicios sincronizados" to "CC-BY-SA 4.0",
                        "Compendium of Physical Activities (valores MET)" to "Uso académico público",
                        "Fuente Black Ops One" to "SIL Open Font License 1.1",
                        "Iconos Material" to "Apache 2.0",
                    )
                    all.distinctBy { it.first }.forEach { (name, license) -> LabeledRow(name, license) }
                }
            }
        }
    }
}
