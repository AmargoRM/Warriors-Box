package com.warriorsbox.app.ui.routines

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.warriorsbox.app.data.AppSettings
import com.warriorsbox.app.data.db.ExerciseNoteEntity
import com.warriorsbox.app.data.db.PlanItemEntity
import com.warriorsbox.app.data.db.SetLogEntity
import com.warriorsbox.app.notifications.AlarmScheduler
import com.warriorsbox.app.notifications.Notifier
import com.warriorsbox.app.ui.components.AppBackground
import com.warriorsbox.app.ui.components.ChoiceChips
import com.warriorsbox.app.ui.components.ConfirmDialog
import com.warriorsbox.app.ui.components.ExerciseImage
import com.warriorsbox.app.ui.components.NumberBadge
import com.warriorsbox.app.ui.components.TipBox
import com.warriorsbox.app.ui.components.WbCard
import com.warriorsbox.app.ui.components.WbTopBar
import com.warriorsbox.app.ui.components.container
import com.warriorsbox.app.ui.theme.WbBlue
import com.warriorsbox.app.ui.theme.WbGold
import com.warriorsbox.app.ui.theme.WbGreen
import com.warriorsbox.core.engine.Alternatives
import com.warriorsbox.core.engine.Reminders
import com.warriorsbox.core.engine.Units
import com.warriorsbox.core.model.BodyZone
import com.warriorsbox.core.model.Exercise
import com.warriorsbox.core.model.MovementPattern
import com.warriorsbox.core.model.SkipReason
import java.util.Locale

private fun fmtNumber(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

fun prescriptionText(item: PlanItemEntity, exercise: Exercise?, useLb: Boolean): String {
    val cardio = exercise?.pattern == MovementPattern.CARDIO
    val reps = when {
        cardio -> Units.formatDuration(item.repsMax)
        exercise?.timed == true -> "${item.repsMin}–${item.repsMax} s"
        item.repsMin == item.repsMax -> "${item.repsMin}"
        else -> "${item.repsMin}–${item.repsMax}"
    }
    val perSide = if (exercise?.unilateral == true && exercise.timed.not()) " por lado" else ""
    val sets = if (cardio) "" else "${item.sets} × "
    val each = exercise?.equipment?.any { it.name == "DUMBBELL" || it.name == "KETTLEBELL" } == true
    val weight = when {
        item.weightKg <= 0 -> ""
        each -> " · " + Units.formatWeight(item.weightKg, useLb) + " c/u"
        else -> " · " + Units.formatWeight(item.weightKg, useLb)
    }
    val rest = if (item.restSec > 0) " · descanso ${Units.formatDuration(item.restSec)}" else ""
    return "$sets$reps$perSide$weight$rest"
}

private fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    runCatching { vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 150, 350), -1)) }
}

@Composable
fun SessionScreen(
    userId: Long,
    dayId: Long,
    onBack: () -> Unit,
    onFinished: (Long) -> Unit,
    onExerciseInfo: (String) -> Unit,
) {
    val c = container()
    val context = LocalContext.current
    val vm: SessionViewModel = viewModel(key = "sesion-$dayId") { SessionViewModel(c, userId, dayId) }
    val items by vm.items.collectAsStateWithLifecycle()
    val sets by vm.sets.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val sessionId by vm.sessionId.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val user by c.users.observe(userId).collectAsStateWithLifecycle(null)
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var skipFor by remember { mutableStateOf<Pair<PlanItemEntity, String>?>(null) }
    var noteFor by remember { mutableStateOf<Pair<PlanItemEntity, String>?>(null) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Vibración al terminar el descanso con la app abierta; alarma solo si la app pasa a segundo plano.
    LaunchedEffect(vm) { vm.onRestFinished = { vibrate(context) } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> vm.restEndsAt?.let { end ->
                    val seconds = ((end - System.currentTimeMillis()) / 1000).toInt()
                    if (seconds > 0) AlarmScheduler.scheduleRestEnd(context, seconds)
                }
                Lifecycle.Event.ON_START -> AlarmScheduler.cancelRest(context)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val title = day?.let { "${Reminders.PLAN_DAY_NAMES[it.dayIndex]} · Semana ${it.week}" } ?: "Sesión"
    val started = sessionId != null

    AppBackground(user?.backgroundPath ?: settings.backgroundPath, settings.veil) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            topBar = {
                WbTopBar(title, onBack) {
                    if (started) IconButton(onClick = { confirmDiscard = true }) { Icon(Icons.Filled.Close, "Descartar sesión") }
                }
            },
            bottomBar = {
                Column(Modifier.navigationBarsPadding()) {
                    if (vm.restEndsAt != null) RestBar(vm)
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), contentColor = MaterialTheme.colorScheme.onSurface) {
                        Row(Modifier.fillMaxWidth().padding(12.dp)) {
                            if (!started) {
                                Button(
                                    onClick = {
                                        vm.start()
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canNotify(context)) {
                                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    },
                                    enabled = vm.loaded && items.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("empezar_sesion"),
                                ) { Text("Empezar sesión", style = MaterialTheme.typography.titleMedium) }
                            } else {
                                val done = sets.count { it.done }
                                Column(Modifier.weight(1f)) {
                                    Text("$done de ${sets.size} series", style = MaterialTheme.typography.labelLarge)
                                    LinearProgressIndicator(progress = { if (sets.isEmpty()) 0f else done / sets.size.toFloat() }, modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp))
                                }
                                Button(
                                    onClick = { if (sets.all { it.done }) vm.finish { onFinished(it.sessionId) } else confirmFinish = true },
                                    enabled = !vm.finishing,
                                    colors = ButtonDefaults.buttonColors(containerColor = WbGreen),
                                    modifier = Modifier.testTag("terminar_sesion"),
                                ) { Text(if (vm.finishing) "Guardando…" else "Terminar") }
                            }
                        }
                    }
                }
            },
        ) { padding ->
            if (!vm.loaded) {
                Box(Modifier.padding(padding).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Scaffold
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 4.dp, 16.dp, padding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                day?.let { d ->
                    item {
                        Text(d.focus + if (d.deload) " · descarga" else "", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        if (!started) Text("Revisa los ejercicios y toca \"Empezar sesión\". Todo se guarda automáticamente.", color = Color.White)
                    }
                }
                items(items, key = { it.id }) { item ->
                    val itemSets = sets.filter { it.itemId == item.id }
                    val exerciseId = itemSets.firstOrNull()?.exerciseId ?: item.exerciseId
                    val exercise = vm.catalog[exerciseId]
                    ExerciseCard(
                        number = item.position + 1,
                        item = item,
                        exercise = exercise,
                        substitutedToday = exerciseId != item.exerciseId,
                        previous = vm.previous[exerciseId],
                        sets = itemSets,
                        started = started,
                        useLb = settings.useLb,
                        note = notes.firstOrNull { it.itemId == item.id }?.note,
                        onInfo = { onExerciseInfo(exerciseId) },
                        onSkip = { skipFor = item to exerciseId },
                        onNote = { noteFor = item to exerciseId },
                        onToggle = { vm.toggle(it, item) },
                        onUpdate = { set, w, r, rir, clear -> vm.updateSet(set, w, r, rir, clear) },
                        onAddSet = { vm.addSet(item) },
                        onRemoveSet = { vm.removeSet(it) },
                    )
                }
            }
        }
    }

    skipFor?.let { (item, exerciseId) ->
        SkipDialog(
            vm = vm,
            item = item,
            exerciseId = exerciseId,
            canChangePlan = !settings.trainerMode || c.trainerLock.isUnlocked(),
            onDismiss = { skipFor = null },
        )
    }
    noteFor?.let { (item, exerciseId) ->
        val existing = notes.firstOrNull { it.itemId == item.id }
        NoteDialog(existing, onSave = { vm.saveNote(item, exerciseId, it, existing); noteFor = null }, onDismiss = { noteFor = null }, enabled = started)
    }
    vm.phrase?.let { event ->
        AlertDialog(
            onDismissRequest = { vm.phrase = null },
            title = { Text(event.title, color = if (event.record) WbGold else MaterialTheme.colorScheme.onSurface) },
            text = { Text(event.phrase, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { vm.phrase = null }) { Text(if (event.record) "¡Soy una leyenda!" else "Sigamos") } },
        )
    }
    if (confirmFinish) {
        ConfirmDialog(
            "¿Terminar la sesión?", "Hay series sin marcar. Se guardará solo lo que marcaste como hecho.",
            "Terminar", { confirmFinish = false; vm.finish { onFinished(it.sessionId) } }, { confirmFinish = false },
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            "¿Descartar la sesión?", "Se borrarán las series registradas de esta sesión. El plan no cambia.",
            "Descartar", { confirmDiscard = false; vm.discard {} }, { confirmDiscard = false },
        )
    }
}

/** Series, repeticiones, peso y descanso en grande, fáciles de leer en el gimnasio. */
@Composable
fun TargetStats(item: PlanItemEntity, exercise: Exercise?, useLb: Boolean, modifier: Modifier = Modifier) {
    val cardio = exercise?.pattern == MovementPattern.CARDIO
    val timed = exercise?.timed == true
    val reps = when {
        cardio -> Units.formatDuration(item.repsMax)
        timed -> "${item.repsMin}–${item.repsMax} s"
        item.repsMin == item.repsMax -> "${item.repsMin}"
        else -> "${item.repsMin}–${item.repsMax}"
    }
    val perSide = exercise?.unilateral == true && !timed
    val each = exercise?.equipment?.any { it.name == "DUMBBELL" || it.name == "KETTLEBELL" } == true
    val stats = buildList {
        if (!cardio) add("${item.sets}" to "series")
        add(reps to if (cardio) "duración" else if (timed) "tiempo" else if (perSide) "reps por lado" else "repeticiones")
        add((if (item.weightKg > 0) Units.formatWeight(item.weightKg, useLb) else "—") to if (item.weightKg > 0 && each) "peso c/u" else if (item.weightKg > 0) "peso" else "sin peso")
        if (item.restSec > 0) add(Units.formatDuration(item.restSec) to "descanso")
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        stats.forEach { (value, label) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(value, color = WbGold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RestBar(vm: SessionViewModel) {
    Surface(color = WbBlue, contentColor = Color.White) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Descanso: ${Units.formatDuration(vm.restRemaining)}", color = Color.White, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = { if (vm.restTotal == 0) 0f else vm.restRemaining / vm.restTotal.toFloat() },
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 12.dp),
                )
            }
            TextButton(onClick = { vm.addRest(15) }) { Text("+15 s", color = Color.White) }
            TextButton(onClick = { vm.skipRest() }) { Text("Saltar", color = Color.White) }
        }
    }
}

@Composable
private fun ExerciseCard(
    number: Int,
    item: PlanItemEntity,
    exercise: Exercise?,
    substitutedToday: Boolean,
    previous: String?,
    sets: List<SetLogEntity>,
    started: Boolean,
    useLb: Boolean,
    note: String?,
    onInfo: () -> Unit,
    onSkip: () -> Unit,
    onNote: () -> Unit,
    onToggle: (SetLogEntity) -> Unit,
    onUpdate: (SetLogEntity, Double?, Int?, Int?, Boolean) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (SetLogEntity) -> Unit,
) {
    val allDone = sets.isNotEmpty() && sets.all { it.done }
    WbCard(highlight = allDone) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberBadge(number)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(exercise?.name ?: item.exerciseId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(prescriptionText(item, exercise, useLb), style = MaterialTheme.typography.bodyMedium)
            }
            if (allDone) Icon(Icons.Filled.CheckCircle, "Completado", tint = WbGreen)
        }
        if (substitutedToday) Text("Reemplazo solo por hoy", color = WbGold, style = MaterialTheme.typography.labelMedium)
        TargetStats(item, exercise, useLb, Modifier.padding(top = 10.dp))
        ExerciseImage(
            exercise,
            Modifier.padding(top = 10.dp).fillMaxWidth().aspectRatio(16f / 10f).clickable(onClick = onInfo),
        )
        val tip = exercise?.tip ?: exercise?.steps?.firstOrNull()
        if (tip != null) TipBox(tip, Modifier.padding(top = 8.dp))
        if (previous != null) Text(previous, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        item.trainerNote?.takeIf { it.isNotBlank() }?.let { TipBox("Entrenador: $it", Modifier.padding(top = 6.dp), color = WbGold) }
        note?.takeIf { it.isNotBlank() }?.let { Text("Tu nota: $it", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onInfo) { Icon(Icons.Filled.Info, null); Text(" Técnica") }
            TextButton(onClick = onSkip, modifier = Modifier.testTag("no_puedo_$number")) { Icon(Icons.Filled.SwapHoriz, null); Text(" No puedo") }
            TextButton(onClick = onNote) { Icon(Icons.AutoMirrored.Filled.Notes, null); Text(" Nota") }
        }
        if (started) {
            val cardio = exercise?.pattern == MovementPattern.CARDIO
            val timed = exercise?.timed == true
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Serie", Modifier.width(48.dp), style = MaterialTheme.typography.labelMedium)
                Text(if (useLb) "Peso (lb)" else "Peso (kg)", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text(if (cardio) "Minutos" else if (timed) "Segundos" else "Reps", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("RIR", Modifier.width(48.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            sets.sortedBy { it.setIndex }.forEachIndexed { i, set ->
                SetRow(i + 1, set, useLb, cardio, number, onToggle, onUpdate, onRemoveSet)
            }
            TextButton(onClick = onAddSet) { Icon(Icons.Filled.Add, null); Text(" Agregar serie") }
        }
    }
}

@Composable
private fun SetRow(
    index: Int,
    set: SetLogEntity,
    useLb: Boolean,
    cardio: Boolean,
    exerciseNumber: Int,
    onToggle: (SetLogEntity) -> Unit,
    onUpdate: (SetLogEntity, Double?, Int?, Int?, Boolean) -> Unit,
    onRemove: (SetLogEntity) -> Unit,
) {
    val shownWeight = if (useLb) Units.kgToLb(set.weightKg) else set.weightKg
    var weightText by remember(set.id) { mutableStateOf(fmtNumber(Math.round(shownWeight * 10) / 10.0)) }
    var repsText by remember(set.id) { mutableStateOf(if (cardio) fmtNumber(set.reps / 60.0) else set.reps.toString()) }
    var confirmRemove by remember { mutableStateOf(false) }
    val doneColor = if (set.done) WbGreen.copy(alpha = 0.18f) else Color.Transparent
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(doneColor).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$index", Modifier.width(48.dp).clickable { confirmRemove = true }, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        OutlinedTextField(
            value = weightText,
            onValueChange = { v ->
                weightText = v.replace(',', '.').filter { it.isDigit() || it == '.' }
                weightText.toDoubleOrNull()?.let { onUpdate(set, if (useLb) Units.lbToKg(it) else it, null, null, false) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f).padding(end = 6.dp).heightIn(min = 48.dp),
        )
        OutlinedTextField(
            value = repsText,
            onValueChange = { v ->
                repsText = v.replace(',', '.').filter { it.isDigit() || (cardio && it == '.') }
                val reps = if (cardio) repsText.toDoubleOrNull()?.let { (it * 60).toInt() } else repsText.toIntOrNull()
                reps?.let { onUpdate(set, null, it, null, false) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (cardio) KeyboardType.Decimal else KeyboardType.Number),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("reps_${exerciseNumber}_$index"),
        )
        // RIR: toca para cambiar (– → 0 → 1 → 2 → 3 → 4 → –)
        Text(
            set.rir?.toString() ?: "–",
            Modifier.width(48.dp).clickable {
                val next = when (val r = set.rir) { null -> 0; 4 -> null; else -> r + 1 }
                if (next == null) onUpdate(set, null, null, null, true) else onUpdate(set, null, null, next, false)
            }.padding(8.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = { onToggle(set) }, modifier = Modifier.size(48.dp).testTag("serie_${exerciseNumber}_$index")) {
            Icon(
                if (set.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (set.done) "Serie hecha" else "Marcar serie",
                tint = if (set.done) WbGreen else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    if (confirmRemove) {
        ConfirmDialog("¿Quitar la serie $index?", "Se borra solo de la sesión de hoy.", "Quitar", { confirmRemove = false; onRemove(set) }, { confirmRemove = false })
    }
}

@Composable
private fun SkipDialog(vm: SessionViewModel, item: PlanItemEntity, exerciseId: String, canChangePlan: Boolean, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf<SkipReason?>(null) }
    var zone by remember { mutableStateOf<BodyZone?>(null) }
    var chosen by remember { mutableStateOf<Alternatives.Suggestion?>(null) }
    var wholePlan by remember { mutableStateOf(false) }
    val original = vm.catalog[exerciseId]
    val suggestions = remember(reason, zone) { reason?.let { vm.alternatives(item, exerciseId, it, zone) }.orEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("No puedo hacer: ${original?.name ?: ""}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("¿Por qué?", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(SkipReason.entries, setOfNotNull(reason), { it.label }, { reason = it; chosen = null })
                if (reason == SkipReason.PAIN) {
                    Text("¿Dónde molesta?", style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(BodyZone.entries.filter { it != BodyZone.OTHER }, setOfNotNull(zone), { it.label }, { zone = it; chosen = null })
                    TipBox("Se registrará la molestia en tu perfil para que el plan la tenga en cuenta. Si el dolor es agudo, detente.")
                }
                if (reason != null) {
                    Text("Alternativas para el mismo músculo", style = MaterialTheme.typography.labelLarge)
                    if (suggestions.isEmpty()) Text("No hay alternativas con tu equipo. Puedes saltarte este ejercicio hoy.")
                    suggestions.forEach { s ->
                        val selected = chosen?.exercise?.id == s.exercise.id
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(if (selected) WbGold.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { chosen = s }.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExerciseImage(s.exercise, Modifier.size(56.dp))
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(s.exercise.name, fontWeight = FontWeight.Bold)
                                Text(s.why, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (chosen != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { wholePlan = false }) {
                            RadioButton(selected = !wholePlan, onClick = { wholePlan = false }); Text("Solo por hoy")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = canChangePlan) { wholePlan = true }) {
                            RadioButton(selected = wholePlan, onClick = { wholePlan = true }, enabled = canChangePlan)
                            Text(if (canChangePlan) "Para todo el plan" else "Para todo el plan (requiere PIN del entrenador)")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = chosen != null,
                onClick = {
                    val c = chosen ?: return@TextButton
                    vm.substitute(item, c.exercise, wholePlan, reason ?: SkipReason.BUSY, zone, original?.name ?: exerciseId)
                    onDismiss()
                },
            ) { Text("Cambiar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun NoteDialog(existing: ExerciseNoteEntity?, onSave: (String) -> Unit, onDismiss: () -> Unit, enabled: Boolean) {
    var text by remember { mutableStateOf(existing?.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nota del ejercicio") },
        text = {
            if (enabled) {
                OutlinedTextField(text, { text = it }, label = { Text("Ej.: subir el asiento, molestia leve…") }, modifier = Modifier.fillMaxWidth())
            } else {
                Text("Empieza la sesión para poder guardar notas.")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = enabled) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
