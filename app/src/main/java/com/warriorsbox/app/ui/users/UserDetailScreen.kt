package com.warriorsbox.app.ui.users

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warriorsbox.app.data.AppSettings
import com.warriorsbox.app.data.db.MeasurementEntity
import com.warriorsbox.app.ui.components.AppBackground
import com.warriorsbox.app.ui.components.ConfirmDialog
import com.warriorsbox.app.ui.components.LabeledRow
import com.warriorsbox.app.ui.components.LineChart
import com.warriorsbox.app.ui.components.NumberField
import com.warriorsbox.app.ui.components.SectionTitle
import com.warriorsbox.app.ui.components.TipBox
import com.warriorsbox.app.ui.components.WbCard
import com.warriorsbox.app.ui.components.WbTopBar
import com.warriorsbox.app.ui.components.container
import com.warriorsbox.app.ui.theme.WbRed
import com.warriorsbox.core.engine.BodyMetrics
import com.warriorsbox.core.engine.Units
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun UserDetailScreen(
    userId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPlan: () -> Unit,
    onHistory: () -> Unit,
    onReminders: () -> Unit,
    onDeleted: () -> Unit,
) {
    val c = container()
    val user by c.users.observe(userId).collectAsStateWithLifecycle(null)
    val injuries by c.users.injuries(userId).collectAsStateWithLifecycle(emptyList())
    val measurements by c.users.measurements(userId).collectAsStateWithLifecycle(emptyList())
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    var addMeasure by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val u = user
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && u != null) scope.launch {
            val path = c.photos.save(uri, "fondo-usuario") ?: return@launch
            c.photos.delete(u.backgroundPath)
            c.users.updateUser(u.copy(backgroundPath = path))
        }
    }

    AppBackground(u?.backgroundPath ?: settings.backgroundPath, settings.veil) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                WbTopBar(u?.name ?: "Usuario", onBack) {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Editar") }
                }
            },
        ) { padding ->
            if (u == null) return@Scaffold
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WbCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(u, 72)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(u.name, style = MaterialTheme.typography.titleLarge)
                            Text(userSubtitle(u))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPlan, modifier = Modifier.weight(1f)) { Text("Ver plan") }
                    OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f)) { Text("Historial") }
                }
                OutlinedButton(onClick = onReminders, modifier = Modifier.fillMaxWidth()) { Text("Recordatorios de entrenamiento") }

                WbCard {
                    SectionTitle("Perfil")
                    u.weightKg?.let { LabeledRow("Peso", Units.formatWeight(it, settings.useLb)) }
                    u.heightCm?.let { LabeledRow("Altura", "${it.toInt()} cm (${Units.cmToFeetText(it)})") }
                    val bmi = if (u.weightKg != null && u.heightCm != null) BodyMetrics.bmi(u.weightKg, u.heightCm) else null
                    bmi?.let { LabeledRow("IMC", "${BodyMetrics.format(it)} · ${BodyMetrics.bmiCategory(it)}") }
                    u.bodyFatPct?.let { LabeledRow("% grasa", "${BodyMetrics.format(it)} %") }
                    LabeledRow("Días / sesión", "${u.daysPerWeek} días · ${u.minutesPerSession} min")
                    LabeledRow("Lugar", u.locationEnum.label)
                    u.sleepHours?.let { LabeledRow("Sueño", "$it h") }
                    u.emergencyContact?.let { LabeledRow("Emergencia", it) }
                    if (u.medicalRestriction) {
                        TipBox("Restricción médica o problema cardíaco registrado: entrenar con autorización profesional.", color = WbRed)
                    }
                }

                WbCard {
                    SectionTitle("Lesiones y molestias")
                    if (injuries.isEmpty()) Text("Ninguna registrada.")
                    injuries.forEach {
                        Text("• ${it.zoneEnum.label}${if (it.active) " — sigue doliendo" else " — recuperada"}${if (it.description.isNotBlank()) ": ${it.description}" else ""}")
                    }
                }

                WbCard {
                    SectionTitle("Evolución del peso")
                    val weights = measurements.mapNotNull { m -> m.weightKg?.let { (if (settings.useLb) Units.kgToLb(it) else it).toFloat() } }
                    LineChart(weights)
                    measurements.takeLast(5).reversed().forEach { m ->
                        LabeledRow(
                            LocalDate.ofEpochDay(m.epochDay).toString(),
                            listOfNotNull(
                                m.weightKg?.let { Units.formatWeight(it, settings.useLb) },
                                m.waistCm?.let { "cintura ${it.toInt()} cm" },
                                m.bodyFatPct?.let { "${BodyMetrics.format(it)} % grasa" },
                            ).joinToString(" · "),
                        )
                    }
                    OutlinedButton(onClick = { addMeasure = true }, modifier = Modifier.fillMaxWidth()) { Text("Registrar medida de hoy") }
                }

                WbCard {
                    SectionTitle("Fondo personalizado")
                    Text("Elige una foto de fondo solo para ${u.name}.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { backgroundPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Text("Elegir foto")
                        }
                        if (u.backgroundPath != null) {
                            TextButton(onClick = {
                                scope.launch {
                                    c.photos.delete(u.backgroundPath)
                                    c.users.updateUser(u.copy(backgroundPath = null))
                                }
                            }) { Text("Quitar") }
                        }
                    }
                }

                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Eliminar usuario", color = WbRed)
                }
            }
        }
    }

    if (addMeasure && u != null) {
        var weight by remember { mutableStateOf("") }
        var waist by remember { mutableStateOf("") }
        var fat by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addMeasure = false },
            title = { Text("Medida de hoy") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(weight, { weight = it }, "Peso", suffix = if (settings.useLb) "lb" else "kg")
                    NumberField(waist, { waist = it }, "Cintura", suffix = "cm")
                    NumberField(fat, { fat = it }, "% grasa", suffix = "%")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val kg = weight.toDoubleOrNull()?.let { if (settings.useLb) Units.lbToKg(it) else it }
                    scope.launch {
                        c.users.addMeasurement(
                            MeasurementEntity(userId = userId, epochDay = LocalDate.now().toEpochDay(), weightKg = kg, waistCm = waist.toDoubleOrNull(), bodyFatPct = fat.toDoubleOrNull()),
                        )
                    }
                    addMeasure = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { addMeasure = false }) { Text("Cancelar") } },
        )
    }
    if (confirmDelete && u != null) {
        ConfirmDialog(
            title = "¿Eliminar a ${u.name}?",
            text = "Se borrará su perfil y TODO su historial. Esto no se puede deshacer, salvo que tengas una copia de seguridad.",
            confirm = "Eliminar",
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    c.users.delete(u)
                    onDeleted()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
