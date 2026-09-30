package com.warriorsbox.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warriorsbox.app.data.AppSettings
import com.warriorsbox.app.ui.components.AppBackground
import com.warriorsbox.app.ui.components.ChoiceChips
import com.warriorsbox.app.ui.components.ConfirmDialog
import com.warriorsbox.app.ui.components.EmptyState
import com.warriorsbox.app.ui.components.ExerciseImage
import com.warriorsbox.app.ui.components.LabeledRow
import com.warriorsbox.app.ui.components.SectionTitle
import com.warriorsbox.app.ui.components.TipBox
import com.warriorsbox.app.ui.components.WbCard
import com.warriorsbox.app.ui.components.WbTopBar
import com.warriorsbox.app.ui.components.container
import com.warriorsbox.app.ui.theme.WbRed
import com.warriorsbox.core.model.Equipment
import com.warriorsbox.core.model.Exercise
import com.warriorsbox.core.model.Level
import com.warriorsbox.core.model.MovementPattern
import com.warriorsbox.core.model.Muscle
import kotlinx.coroutines.launch
import java.text.Normalizer

private fun normalize(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

@Composable
fun LibraryScreen(
    pickMode: Boolean,
    userId: Long,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onPicked: (String) -> Unit,
    onCreateCustom: () -> Unit,
) {
    val c = container()
    val all by c.exercises.all.collectAsStateWithLifecycle(emptyList())
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<Muscle?>(null) }
    var equipment by remember { mutableStateOf<Equipment?>(null) }
    var onlySpanish by remember { mutableStateOf(true) }
    var userEquipment by remember { mutableStateOf<Set<Equipment>?>(null) }
    var onlyMine by remember { mutableStateOf(pickMode && userId > 0) }
    LaunchedEffect(userId) { if (userId > 0) userEquipment = c.users.trainingProfile(userId)?.equipment }

    val filtered = remember(all, query, muscle, equipment, onlySpanish, onlyMine, userEquipment) {
        val q = normalize(query.trim())
        all.asSequence()
            .filter { !onlySpanish || it.curated || it.custom }
            .filter { muscle == null || muscle in it.primaryMuscles }
            .filter { equipment == null || equipment in it.equipment || (equipment == Equipment.NONE && it.isBodyweight) }
            .filter { !onlyMine || userEquipment == null || it.isAvailableWith(userEquipment!!) }
            .filter { q.isEmpty() || normalize(it.name).contains(q) || normalize(it.nameEn.orEmpty()).contains(q) }
            .sortedWith(compareBy<Exercise> { !it.curated }.thenBy { it.rank }.thenBy { it.name })
            .toList()
    }

    AppBackground(settings.backgroundPath, settings.veil) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { WbTopBar(if (pickMode) "Elegir ejercicio" else "Biblioteca", onBack) },
            floatingActionButton = {
                if (!pickMode) ExtendedFloatingActionButton(onClick = onCreateCustom, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Ejercicio propio") })
            },
        ) { padding ->
            Column(Modifier.padding(padding)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    label = { Text("Buscar (español o inglés)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(selected = onlySpanish, onClick = { onlySpanish = !onlySpanish }, label = { Text("Solo en español") }) }
                    if (userEquipment != null) item { FilterChip(selected = onlyMine, onClick = { onlyMine = !onlyMine }, label = { Text("Con mi equipo") }) }
                    items(Muscle.entries.filter { it != Muscle.NECK }) { m ->
                        FilterChip(selected = muscle == m, onClick = { muscle = if (muscle == m) null else m }, label = { Text(m.label) })
                    }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Equipment.entries) { e ->
                        FilterChip(selected = equipment == e, onClick = { equipment = if (equipment == e) null else e }, label = { Text(e.label) })
                    }
                }
                Text("${filtered.size} ejercicios", style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                if (filtered.isEmpty()) EmptyState("Sin resultados. Prueba quitar filtros o desactivar \"Solo en español\".")
                LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { e ->
                        WbCard(Modifier.clickable { if (pickMode) onPicked(e.id) else onOpen(e.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ExerciseImage(e, Modifier.size(64.dp))
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(e.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        e.primaryMuscles.joinToString { it.label } + " · " +
                                            (if (e.isBodyweight) "Sin equipo" else e.equipment.joinToString { it.label }) + " · " + e.level.label,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    if (e.custom) Text("Propio", style = MaterialTheme.typography.labelSmall)
                                }
                                if (pickMode) TextButton(onClick = { onOpen(e.id) }) { Text("Ver") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExerciseDetailScreen(exerciseId: String, onBack: () -> Unit, onProgress: (() -> Unit)?) {
    val c = container()
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var exercise by remember { mutableStateOf<Exercise?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(exerciseId) { exercise = c.exercises.get(exerciseId) }
    AppBackground(settings.backgroundPath, settings.veil) {
        Scaffold(containerColor = Color.Transparent, topBar = { WbTopBar(exercise?.name ?: "Ejercicio", onBack) }) { padding ->
            val e = exercise ?: return@Scaffold
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExerciseImage(e, Modifier.weight(1f).height(170.dp), index = 0)
                    if (e.imageUrls.size > 1) ExerciseImage(e, Modifier.weight(1f).height(170.dp), index = 1)
                }
                WbCard {
                    Text(e.name, style = MaterialTheme.typography.titleLarge)
                    e.nameEn?.takeIf { it != e.name }?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    LabeledRow("Movimiento", e.pattern.label)
                    LabeledRow("Músculos principales", e.primaryMuscles.joinToString { it.label })
                    if (e.secondaryMuscles.isNotEmpty()) LabeledRow("Secundarios", e.secondaryMuscles.joinToString { it.label })
                    LabeledRow("Equipo", if (e.isBodyweight) "Sin equipo" else e.equipment.joinToString { it.label })
                    LabeledRow("Nivel", e.level.label)
                    if (e.contraindications.isNotEmpty()) {
                        LabeledRow("Cuidado si te molesta", e.contraindications.joinToString { it.label.lowercase() })
                    }
                }
                WbCard {
                    SectionTitle("Cómo se hace")
                    if (e.steps.isEmpty()) Text("Sin instrucciones disponibles.")
                    e.steps.forEachIndexed { i, step -> Text("${i + 1}. $step", modifier = Modifier.padding(vertical = 3.dp)) }
                    e.tip?.let { TipBox(it, Modifier.padding(top = 8.dp)) }
                    if (!e.curated && !e.custom) {
                        Text("Instrucciones originales de la fuente (${e.source}).", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                if (onProgress != null) Button(onClick = onProgress, modifier = Modifier.fillMaxWidth()) { Text("Ver mi progreso en este ejercicio") }
                if (e.custom) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Eliminar ejercicio propio", color = WbRed) }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog("¿Eliminar ejercicio?", "Si está en algún plan, allí aparecerá con su código.", "Eliminar", {
            confirmDelete = false
            scope.launch {
                c.exercises.deleteCustom(exerciseId)
                onBack()
            }
        }, { confirmDelete = false })
    }
}

@Composable
fun CustomExerciseScreen(onBack: () -> Unit) {
    val c = container()
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf(MovementPattern.ISOLATION) }
    var muscle by remember { mutableStateOf(Muscle.CHEST) }
    var equipment by remember { mutableStateOf(setOf<Equipment>()) }
    var level by remember { mutableStateOf(Level.BEGINNER) }
    var steps by remember { mutableStateOf("") }
    var timed by remember { mutableStateOf(false) }
    AppBackground(settings.backgroundPath, settings.veil) {
        Scaffold(containerColor = Color.Transparent, topBar = { WbTopBar("Ejercicio propio", onBack) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WbCard {
                    OutlinedTextField(name, { name = it }, label = { Text("Nombre *") }, modifier = Modifier.fillMaxWidth())
                    Text("Tipo de movimiento", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    ChoiceChips(MovementPattern.entries, setOf(pattern), { it.label }, { pattern = it })
                    Text("Músculo principal", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    ChoiceChips(Muscle.entries, setOf(muscle), { it.label }, { muscle = it })
                    Text("Equipo (vacío = sin equipo)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    ChoiceChips(Equipment.entries.filter { it != Equipment.NONE }, equipment, { it.label }, { e -> equipment = if (e in equipment) equipment - e else equipment + e })
                    Text("Nivel", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    ChoiceChips(Level.entries, setOf(level), { it.label }, { level = it })
                    ChoiceChips(listOf(false, true), setOf(timed), { if (it) "Por tiempo" else "Por repeticiones" }, { timed = it })
                    OutlinedTextField(steps, { steps = it }, label = { Text("Instrucciones (una por línea)") }, modifier = Modifier.fillMaxWidth().height(140.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = {
                            val id = "propio-" + normalize(name).replace(Regex("[^a-z0-9]+"), "-").trim('-') + "-" + (System.currentTimeMillis() % 100000)
                            scope.launch {
                                c.exercises.saveCustom(
                                    Exercise(
                                        id = id, name = name.trim(), pattern = pattern, primaryMuscles = listOf(muscle), equipment = equipment,
                                        level = level, compound = pattern !in setOf(MovementPattern.ISOLATION, MovementPattern.CORE, MovementPattern.CARDIO),
                                        steps = steps.lines().map { it.trim() }.filter { it.isNotEmpty() }, timed = timed, custom = true, rank = 5000,
                                    ),
                                )
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Guardar") }
                }
            }
        }
    }
}
