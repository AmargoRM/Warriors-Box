package com.warriorsbox.app.ui.routines

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warriorsbox.app.data.AppSettings
import com.warriorsbox.app.data.db.PlanItemEntity
import com.warriorsbox.app.ui.components.AppBackground
import com.warriorsbox.app.ui.components.ChoiceChips
import com.warriorsbox.app.ui.components.ConfirmDialog
import com.warriorsbox.app.ui.components.NumberBadge
import com.warriorsbox.app.ui.components.NumberField
import com.warriorsbox.app.ui.components.WbCard
import com.warriorsbox.app.ui.components.WbTopBar
import com.warriorsbox.app.ui.components.container
import com.warriorsbox.core.engine.PlanGenerator
import com.warriorsbox.core.engine.Reminders
import com.warriorsbox.core.engine.Units
import com.warriorsbox.core.model.Exercise
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun PlanEditorScreen(
    userId: Long,
    dayId: Long,
    pickedFlow: StateFlow<String?>,
    onPickedHandled: () -> Unit,
    onBack: () -> Unit,
    onPickExercise: (String) -> Unit,
) {
    val c = container()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by c.plans.items(dayId).collectAsStateWithLifecycle(emptyList())
    val day by c.plans.day(dayId).collectAsStateWithLifecycle(null)
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val picked by pickedFlow.collectAsStateWithLifecycle()
    var catalog by remember { mutableStateOf<Map<String, Exercise>>(emptyMap()) }
    var editing by remember { mutableStateOf<PlanItemEntity?>(null) }
    var deleting by remember { mutableStateOf<PlanItemEntity?>(null) }
    var copyDialog by remember { mutableStateOf(false) }
    var duplicateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { catalog = c.exercises.byId() }
    LaunchedEffect(picked) {
        val value = picked ?: return@LaunchedEffect
        val (mode, exerciseId) = value.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val exercise = c.exercises.get(exerciseId)
        if (exercise != null) {
            catalog = c.exercises.byId()
            when {
                mode == "add" -> c.plans.addItem(dayId, exercise, userId)
                mode.startsWith("replace-") -> {
                    val itemId = mode.removePrefix("replace-").toLongOrNull()
                    val item = itemId?.let { id -> c.plans.itemsNow(dayId).firstOrNull { it.id == id } }
                    if (item != null) c.plans.replaceItemExercise(item, exercise, userId)
                }
            }
        }
        onPickedHandled()
    }

    val title = day?.let { "Editar ${Reminders.PLAN_DAY_NAMES[it.dayIndex]} · Sem. ${it.week}" } ?: "Editar día"
    AppBackground(settings.backgroundPath, settings.veil) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { WbTopBar(title, onBack) },
            floatingActionButton = {
                ExtendedFloatingActionButton(onClick = { onPickExercise("add") }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Agregar ejercicio") })
            },
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp, padding.calculateTopPadding(), 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { copyDialog = true }, modifier = Modifier.weight(1f)) { Text("Copiar a otras semanas") }
                        OutlinedButton(onClick = { duplicateDialog = true }, modifier = Modifier.weight(1f)) { Text("Duplicar semana") }
                    }
                }
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    val exercise = catalog[item.exerciseId]
                    WbCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NumberBadge(index + 1, size = 30.dp)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(exercise?.name ?: item.exerciseId, fontWeight = FontWeight.Bold)
                                Text(prescriptionText(item, exercise, settings.useLb), style = MaterialTheme.typography.bodySmall)
                                item.trainerNote?.takeIf { it.isNotBlank() }?.let { Text("Nota: $it", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { scope.launch { c.plans.moveItem(item, -1) } }, enabled = index > 0) { Icon(Icons.Filled.ArrowUpward, "Subir") }
                            IconButton(onClick = { scope.launch { c.plans.moveItem(item, 1) } }, enabled = index < items.lastIndex) { Icon(Icons.Filled.ArrowDownward, "Bajar") }
                            IconButton(onClick = { editing = item }) { Icon(Icons.Filled.Edit, "Editar") }
                            IconButton(onClick = { onPickExercise("replace-${item.id}") }) { Icon(Icons.Filled.SwapHoriz, "Reemplazar") }
                            IconButton(onClick = { deleting = item }) { Icon(Icons.Filled.Delete, "Quitar") }
                        }
                    }
                }
            }
        }
    }

    editing?.let { item ->
        EditItemDialog(item, settings.useLb, onDismiss = { editing = null }) { updated ->
            scope.launch { c.plans.updateItem(updated) }
            editing = null
        }
    }
    deleting?.let { item ->
        ConfirmDialog("¿Quitar ejercicio?", "Se quita solo de este día.", "Quitar", {
            scope.launch { c.plans.removeItem(item) }
            deleting = null
        }, { deleting = null })
    }
    if (copyDialog) {
        val d = day
        var weeks by remember { mutableStateOf(setOf<Int>()) }
        AlertDialog(
            onDismissRequest = { copyDialog = false },
            title = { Text("Copiar este día a…") },
            text = {
                Column {
                    (1..PlanGenerator.WEEKS).filter { it != d?.week }.forEach { w ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = w in weeks, onCheckedChange = { weeks = if (it) weeks + w else weeks - w })
                            Text("Semana $w" + if (w == PlanGenerator.DELOAD_WEEK) " (se ajusta a descarga)" else "")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = weeks.isNotEmpty(), onClick = {
                    scope.launch {
                        c.plans.copyDayToWeeks(dayId, weeks.toList())
                        Toast.makeText(context, "Día copiado", Toast.LENGTH_SHORT).show()
                    }
                    copyDialog = false
                }) { Text("Copiar") }
            },
            dismissButton = { TextButton(onClick = { copyDialog = false }) { Text("Cancelar") } },
        )
    }
    if (duplicateDialog) {
        val d = day
        var from by remember { mutableStateOf(d?.week ?: 1) }
        var to by remember { mutableStateOf(((d?.week ?: 1) % PlanGenerator.WEEKS) + 1) }
        AlertDialog(
            onDismissRequest = { duplicateDialog = false },
            title = { Text("Duplicar semana completa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Copiar la semana…")
                    ChoiceChips((1..PlanGenerator.WEEKS).toList(), setOf(from), { "$it" }, { from = it })
                    Text("…sobre la semana (reemplaza sus ejercicios)")
                    ChoiceChips((1..PlanGenerator.WEEKS).toList(), setOf(to), { "$it" }, { to = it })
                }
            },
            confirmButton = {
                TextButton(enabled = from != to, onClick = {
                    val planId = d?.planId ?: return@TextButton
                    scope.launch {
                        c.plans.duplicateWeek(planId, from, to)
                        Toast.makeText(context, "Semana $from copiada sobre la $to", Toast.LENGTH_SHORT).show()
                    }
                    duplicateDialog = false
                }) { Text("Duplicar") }
            },
            dismissButton = { TextButton(onClick = { duplicateDialog = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun EditItemDialog(item: PlanItemEntity, useLb: Boolean, onDismiss: () -> Unit, onSave: (PlanItemEntity) -> Unit) {
    var sets by remember { mutableStateOf(item.sets.toString()) }
    var min by remember { mutableStateOf(item.repsMin.toString()) }
    var max by remember { mutableStateOf(item.repsMax.toString()) }
    val shown = if (useLb) Units.kgToLb(item.weightKg) else item.weightKg
    var weight by remember { mutableStateOf((Math.round(shown * 10) / 10.0).toString()) }
    var rest by remember { mutableStateOf(item.restSec.toString()) }
    var note by remember { mutableStateOf(item.trainerNote.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar ejercicio") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(sets, { sets = it }, "Series", decimal = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(min, { min = it }, "Reps mín.", Modifier.weight(1f), decimal = false)
                    NumberField(max, { max = it }, "Reps máx.", Modifier.weight(1f), decimal = false)
                }
                NumberField(weight, { weight = it }, "Peso", suffix = if (useLb) "lb" else "kg")
                NumberField(rest, { rest = it }, "Descanso", suffix = "s", decimal = false)
                OutlinedTextField(note, { note = it }, label = { Text("Nota del entrenador") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val repsMin = min.toIntOrNull() ?: item.repsMin
                val repsMax = (max.toIntOrNull() ?: item.repsMax).coerceAtLeast(repsMin)
                val w = weight.toDoubleOrNull()?.let { if (useLb) Units.lbToKg(it) else it } ?: item.weightKg
                onSave(
                    item.copy(
                        sets = (sets.toIntOrNull() ?: item.sets).coerceIn(1, 12),
                        repsMin = repsMin,
                        repsMax = repsMax,
                        targetReps = item.targetReps.coerceIn(repsMin, repsMax),
                        weightKg = w,
                        restSec = rest.toIntOrNull() ?: item.restSec,
                        trainerNote = note.trim().ifBlank { null },
                    ),
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
