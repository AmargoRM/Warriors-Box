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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
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

/** Partes del cuerpo para filtrar la biblioteca ("ver todos los de pecho"). */
enum class BodyPart(val label: String, val muscles: Set<Muscle>) {
    CHEST("Pecho", setOf(Muscle.CHEST)),
    BACK("Espalda", setOf(Muscle.LATS, Muscle.MIDDLE_BACK, Muscle.LOWER_BACK, Muscle.TRAPS)),
    SHOULDERS("Hombros", setOf(Muscle.SHOULDERS)),
    BICEPS("Bíceps", setOf(Muscle.BICEPS)),
    TRICEPS("Tríceps", setOf(Muscle.TRICEPS)),
    FOREARMS("Antebrazos", setOf(Muscle.FOREARMS)),
    LEGS("Piernas", setOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.ADDUCTORS, Muscle.ABDUCTORS)),
    GLUTES("Glúteos", setOf(Muscle.GLUTES)),
    CALVES("Pantorrillas", setOf(Muscle.CALVES)),
    ABS("Abdomen", setOf(Muscle.ABS)),
    CARDIO("Cardio", setOf(Muscle.CARDIO)),
    ;

    fun matches(e: Exercise): Boolean =
        e.primaryMuscles.any { it in muscles } || (this == CARDIO && e.pattern == MovementPattern.CARDIO)
}

internal fun normalize(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

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
    var bodyPart by remember { mutableStateOf<BodyPart?>(null) }
    var equipment by remember { mutableStateOf<Equipment?>(null) }
    var onlySpanish by remember { mutableStateOf(true) }
    var userEquipment by remember { mutableStateOf<Set<Equipment>?>(null) }
    var onlyMine by remember { mutableStateOf(false) }
    LaunchedEffect(userId) { if (userId > 0) userEquipment = c.users.trainingProfile(userId)?.equipment }

    val filtered = remember(all, query, bodyPart, equipment, onlySpanish, onlyMine, userEquipment) {
        val q = normalize(query.trim())
        all.asSequence()
            .filter { !onlySpanish || it.curated || it.custom }
            .filter { e -> bodyPart?.matches(e) ?: true }
            .filter { equipment == null || equipment in it.equipment || (equipment == Equipment.NONE && it.isBodyweight) }
            .filter { !onlyMine || userEquipment == null || it.isAvailableWith(userEquipment!!) }
            .filter { q.isEmpty() || normalize(it.name).contains(q) || normalize(it.nameEn.orEmpty()).contains(q) }
            .sortedWith(compareBy<Exercise> { !it.curated }.thenBy { it.rank }.thenBy { it.name })
            .toList()
    }

    AppBackground(settings.backgroundPath, settings.veil) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            topBar = { WbTopBar(if (pickMode) "Elegir ejercicio" else "Biblioteca", onBack) },
            floatingActionButton = {
                if (!pickMode) ExtendedFloatingActionButton(onClick = onCreateCustom, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Ejercicio propio") })
            },
        ) { padding ->
            Column(Modifier.padding(padding)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        // Buscar por nombre quita los filtros de parte del cuerpo y elemento.
                        if (it.isNotBlank()) {
                            bodyPart = null
                            equipment = null
                        }
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Borrar búsqueda") }
                    },
                    label = { Text("Buscar por nombre (español o inglés)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("buscar_ejercicio"),
                )
                Text("Parte del cuerpo", style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(selected = bodyPart == null, onClick = { bodyPart = null }, label = { Text("Todas") }) }
                    items(BodyPart.entries) { b ->
                        FilterChip(
                            selected = bodyPart == b,
                            onClick = {
                                bodyPart = if (bodyPart == b) null else b
                                query = ""
                            },
                            label = { Text(b.label) },
                            modifier = Modifier.testTag("parte_${b.name}"),
                        )
                    }
                }
                Text("Elemento", style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(selected = equipment == null, onClick = { equipment = null }, label = { Text("Todos") }) }
                    items(Equipment.entries) { e ->
                        FilterChip(
                            selected = equipment == e,
                            onClick = {
                                equipment = if (equipment == e) null else e
                                query = ""
                            },
                            label = { Text(e.label) },
                        )
                    }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(selected = onlySpanish, onClick = { onlySpanish = !onlySpanish }, label = { Text("Solo en español") }) }
                    if (userEquipment != null) item { FilterChip(selected = onlyMine, onClick = { onlyMine = !onlyMine }, label = { Text("Con mi equipo") }) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val title = listOfNotNull(
                        bodyPart?.label,
                        equipment?.label,
                        query.takeIf { it.isNotBlank() }?.let { "\"$it\"" },
                    ).joinToString(" · ").ifBlank { "Todos" }
                    Text(
                        "$title · ${filtered.size} ejercicios",
                        style = MaterialTheme.typography.titleSmall, color = Color.White, modifier = Modifier.weight(1f),
                    )
                    if (bodyPart != null || equipment != null || query.isNotBlank()) {
                        TextButton(onClick = {
                            bodyPart = null
                            equipment = null
                            query = ""
                        }) { Text("Ver todos") }
                    }
                }
                if (filtered.isEmpty()) {
                    EmptyState(
                        if (onlyMine) "Sin resultados con el equipo de tu perfil. Desactiva \"Con mi equipo\" o agrega ese equipo en tu perfil (paso 3)."
                        else "Sin resultados. Prueba quitar filtros o desactivar \"Solo en español\".",
                    )
                }
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
        Scaffold(containerColor = Color.Transparent, contentColor = Color.White, topBar = { WbTopBar(exercise?.name ?: "Ejercicio", onBack) }) { padding ->
            val e = exercise ?: return@Scaffold
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (e.image == null && e.imageUrls.size > 2) {
                    // Ejercicios propios con varias fotos: se deslizan de costado.
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(e.imageUrls.size) { i -> ExerciseImage(e, Modifier.size(width = 240.dp, height = 200.dp), index = i) }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExerciseImage(e, Modifier.weight(1f).height(170.dp), index = 0)
                        if (e.imageUrls.size > 1) ExerciseImage(e, Modifier.weight(1f).height(170.dp), index = 1)
                    }
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
