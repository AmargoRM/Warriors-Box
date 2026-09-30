package com.warriorsbox.app.ui.routines

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.warriorsbox.app.AppContainer
import com.warriorsbox.app.data.AppSettings
import com.warriorsbox.app.data.DayStatus
import com.warriorsbox.app.data.db.PlanDayEntity
import com.warriorsbox.app.data.db.PlanEntity
import com.warriorsbox.app.data.db.PlanItemEntity
import com.warriorsbox.app.data.db.SessionEntity
import com.warriorsbox.app.ui.components.AppBackground
import com.warriorsbox.app.ui.components.ConfirmDialog
import com.warriorsbox.app.ui.components.TipBox
import com.warriorsbox.app.ui.components.WbCard
import com.warriorsbox.app.ui.components.WbTopBar
import com.warriorsbox.app.ui.components.container
import com.warriorsbox.app.ui.components.rememberTrainerGuard
import com.warriorsbox.app.ui.theme.WbBlue
import com.warriorsbox.app.ui.theme.WbGold
import com.warriorsbox.app.ui.theme.WbGreen
import com.warriorsbox.core.engine.PlanGenerator
import com.warriorsbox.core.engine.Reminders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlanUi(
    val loading: Boolean = true,
    val plan: PlanEntity? = null,
    val days: List<PlanDayEntity> = emptyList(),
    val items: List<PlanItemEntity> = emptyList(),
    val sessions: List<SessionEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(val c: AppContainer, val userId: Long) : ViewModel() {
    val ui = c.plans.activePlan(userId).flatMapLatest { plan ->
        if (plan == null) {
            flowOf(PlanUi(loading = false))
        } else {
            combine(c.plans.days(plan.id), c.plans.itemsOfPlan(plan.id), c.plans.sessionsOfPlan(plan.id)) { d, i, s ->
                PlanUi(false, plan, d, i, s)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlanUi())

    var generating by mutableStateOf(false)
    var lastResult by mutableStateOf<Pair<String, List<String>>?>(null)

    fun createCustom() = viewModelScope.launch {
        generating = true
        runCatching { c.plans.createCustom(userId) }
            .onSuccess {
                lastResult = "Rutina vacía creada. Toca el lápiz ✎ de cada día para agregar tus ejercicios, " +
                    "con sus series, repeticiones y peso. Luego usa \"Copiar a otras semanas\" para repetirla." to emptyList()
            }
            .onFailure { lastResult = "No se pudo crear la rutina: ${it.message}" to emptyList() }
        generating = false
    }

    fun generate(newCycle: Boolean) = viewModelScope.launch {
        generating = true
        runCatching { c.plans.generate(userId, newCycle) }
            .onSuccess { (_, plan) -> lastResult = plan.summary to plan.warnings }
            .onFailure { lastResult = "No se pudo generar el plan: ${it.message}" to emptyList() }
        generating = false
    }
}

@Composable
fun PlanScreen(
    userId: Long,
    onBack: () -> Unit,
    onOpenDay: (Long) -> Unit,
    onEditDay: (Long) -> Unit,
    onHistory: () -> Unit,
    onLibrary: () -> Unit,
    onEditProfile: () -> Unit,
) {
    val c = container()
    val context = LocalContext.current
    val vm: PlanViewModel = viewModel(key = "plan-$userId") { PlanViewModel(c, userId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val user by c.users.observe(userId).collectAsStateWithLifecycle(null)
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val (guard, guardDialog) = rememberTrainerGuard()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var selectedWeek by remember { mutableStateOf<Int?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val text = c.plans.exportPlan(userId) ?: return@launch
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
            Toast.makeText(context, "Plan exportado", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("vacío")
                c.plans.importPlan(userId, text)
            }.onSuccess { unknown ->
                Toast.makeText(context, if (unknown == 0) "Plan importado" else "Plan importado ($unknown ejercicios no reconocidos se omitieron)", Toast.LENGTH_LONG).show()
            }.onFailure { Toast.makeText(context, "No se pudo importar: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    val currentWeek = if (ui.days.isEmpty()) 1 else c.plans.currentWeek(ui.days, ui.sessions)
    val week = selectedWeek ?: currentWeek

    AppBackground(user?.backgroundPath ?: settings.backgroundPath, settings.veil) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            topBar = {
                WbTopBar(user?.name ?: "Rutina", onBack) {
                    IconButton(onClick = onHistory) { Icon(Icons.Filled.History, "Historial") }
                    IconButton(onClick = onLibrary) { Icon(Icons.AutoMirrored.Filled.MenuBook, "Biblioteca") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Más opciones") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Nuevo ciclo de 5 semanas") }, onClick = {
                            menu = false
                            guard.run { confirm = "ciclo" }
                        })
                        DropdownMenuItem(text = { Text("Regenerar plan desde el perfil") }, onClick = {
                            menu = false
                            guard.run { confirm = "regenerar" }
                        })
                        DropdownMenuItem(text = { Text("Armar mi propia rutina (desde cero)") }, onClick = {
                            menu = false
                            guard.run { confirm = "propia" }
                        })
                        DropdownMenuItem(text = { Text("Editar perfil") }, onClick = {
                            menu = false
                            guard.run(onEditProfile)
                        })
                        DropdownMenuItem(text = { Text("Exportar plan (archivo)") }, onClick = {
                            menu = false
                            exportLauncher.launch("plan-${user?.name ?: "alumno"}.json")
                        })
                        DropdownMenuItem(text = { Text("Importar plan (archivo)") }, onClick = {
                            menu = false
                            guard.run { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                        })
                    }
                }
            },
        ) { padding ->
            when {
                ui.loading -> Column(Modifier.padding(padding).fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
                ui.plan == null -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WbCard {
                        Text("Todavía no hay plan", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Warriors Box arma un plan realista de 5 semanas × 6 días según el perfil: nivel, días disponibles, " +
                                "objetivo, equipo y lesiones. Funciona sin internet.",
                        )
                        Button(
                            onClick = { guard.run { vm.generate(false) } },
                            enabled = !vm.generating,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("generar_plan"),
                        ) { Text(if (vm.generating) "Generando…" else "Generar mi plan") }
                        OutlinedButton(
                            onClick = { guard.run { vm.createCustom() } },
                            enabled = !vm.generating,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("rutina_propia"),
                        ) { Text("Armar mi propia rutina") }
                        Text(
                            "Con \"Armar mi propia rutina\" eliges tú cada ejercicio, sus series, repeticiones y peso.",
                            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                else -> PlanContent(ui, week, currentWeek, Modifier.padding(padding), onSelectWeek = { selectedWeek = it }, onOpenDay = onOpenDay,
                    onEditDay = { id -> guard.run { onEditDay(id) } })
            }
        }
    }
    guardDialog()

    vm.lastResult?.let { (summary, warnings) ->
        AlertDialog(
            onDismissRequest = { vm.lastResult = null },
            title = { Text(if (summary.startsWith("Rutina vacía")) "Tu rutina" else "Tu plan está listo") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(summary); warnings.forEach { Text("• $it") } } },
            confirmButton = { TextButton(onClick = { vm.lastResult = null }) { Text("¡Vamos!") } },
        )
    }
    when (confirm) {
        "propia" -> ConfirmDialog(
            "Armar mi propia rutina", "Se crea una rutina vacía de 5 semanas × 6 días para que elijas cada ejercicio. " +
                "El plan actual se reemplaza; las sesiones ya registradas se conservan en el historial.",
            "Crear", { confirm = null; vm.createCustom() }, { confirm = null },
        )
        "ciclo" -> ConfirmDialog(
            "Nuevo ciclo", "Se crea un plan nuevo de 5 semanas partiendo de los últimos pesos que usaste. El historial se conserva.",
            "Crear", { confirm = null; vm.generate(true) }, { confirm = null },
        )
        "regenerar" -> ConfirmDialog(
            "Regenerar plan", "Se reemplaza el plan actual por uno nuevo según el perfil. Las sesiones ya registradas se conservan en el historial, " +
                "pero los cambios manuales del plan actual se pierden.",
            "Regenerar", { confirm = null; vm.generate(false) }, { confirm = null },
        )
    }
    LaunchedEffect(ui.plan?.id) { selectedWeek = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanContent(
    ui: PlanUi,
    week: Int,
    currentWeek: Int,
    modifier: Modifier,
    onSelectWeek: (Int) -> Unit,
    onOpenDay: (Long) -> Unit,
    onEditDay: (Long) -> Unit,
) {
    val c = container()
    val useLb = c.settings.settings.collectAsStateWithLifecycle(com.warriorsbox.app.data.AppSettings()).value.useLb
    var catalog by remember { mutableStateOf<Map<String, com.warriorsbox.core.model.Exercise>>(emptyMap()) }
    LaunchedEffect(ui.items.size) { catalog = c.exercises.byId() }
    val mandatory = ui.days.filter { !it.optional }
    val doneIds = ui.sessions.filter { it.completed }.mapNotNull { it.dayId }.toSet()
    val doneCount = mandatory.count { it.id in doneIds }
    Column(modifier) {
        WbCard(Modifier.padding(horizontal = 16.dp)) {
            Text("Ciclo ${ui.plan?.cycle ?: 1} · Semana $currentWeek de ${PlanGenerator.WEEKS}", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { if (mandatory.isEmpty()) 0f else doneCount / mandatory.size.toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            Text("$doneCount de ${mandatory.size} sesiones completadas", style = MaterialTheme.typography.labelMedium)
            if (doneCount == mandatory.size && mandatory.isNotEmpty()) {
                TipBox("¡Terminaste el ciclo! Usa el menú ⋮ → \"Nuevo ciclo de 5 semanas\" para seguir progresando.", color = WbGreen)
            }
        }
        PrimaryScrollableTabRow(selectedTabIndex = week - 1, containerColor = Color.Transparent, contentColor = Color.White, edgePadding = 16.dp) {
            (1..PlanGenerator.WEEKS).forEach { w ->
                Tab(
                    selected = week == w,
                    onClick = { onSelectWeek(w) },
                    text = { Text(if (w == PlanGenerator.DELOAD_WEEK) "Sem. $w (descarga)" else "Semana $w") },
                )
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ui.days.filter { it.week == week }, key = { it.id }) { day ->
                val status = c.plans.statusOf(day, ui.sessions)
                val count = ui.items.count { it.dayId == day.id }
                WbCard(Modifier.clickable { onOpenDay(day.id) }.testTag("dia_${day.week}_${day.dayIndex}"), highlight = status == DayStatus.IN_PROGRESS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Reminders.PLAN_DAY_NAMES[day.dayIndex], style = MaterialTheme.typography.titleLarge)
                            Text(day.focus, fontWeight = FontWeight.SemiBold)
                            Text("$count ejercicios" + if (day.deload) " · semana de descarga" else "", style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(onClick = { onEditDay(day.id) }) { Icon(Icons.Filled.Edit, "Editar día") }
                    }
                    ui.items.filter { it.dayId == day.id }.sortedBy { it.position }.forEachIndexed { i, item ->
                        val ex = catalog[item.exerciseId]
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text("${i + 1}. ${ex?.name ?: item.exerciseId}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                shortPrescription(item, ex, useLb),
                                color = WbGold, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { onOpenDay(day.id) },
                            label = {
                                Text(
                                    when (status) {
                                        DayStatus.DONE -> "✓ Completado"
                                        DayStatus.IN_PROGRESS -> "En curso"
                                        DayStatus.PENDING -> "Pendiente"
                                    },
                                    color = when (status) {
                                        DayStatus.DONE -> WbGreen
                                        DayStatus.IN_PROGRESS -> WbGold
                                        DayStatus.PENDING -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            },
                        )
                        if (day.optional) AssistChip(onClick = { onOpenDay(day.id) }, label = { Text("Opcional", color = WbBlue) })
                    }
                }
            }
            ui.plan?.warnings?.takeIf { it.isNotBlank() }?.let { w ->
                item { TipBox(w.lines().joinToString("\n") { "• $it" }) }
            }
        }
    }
}

/** "3×8–10 · 40 kg" para las listas. */
fun shortPrescription(item: PlanItemEntity, exercise: com.warriorsbox.core.model.Exercise?, useLb: Boolean): String {
    val cardio = exercise?.pattern == com.warriorsbox.core.model.MovementPattern.CARDIO
    if (cardio) return com.warriorsbox.core.engine.Units.formatDuration(item.repsMax)
    val reps = if (item.repsMin == item.repsMax) "${item.repsMin}" else "${item.repsMin}–${item.repsMax}"
    val unit = if (exercise?.timed == true) " s" else ""
    val weight = if (item.weightKg > 0) " · " + com.warriorsbox.core.engine.Units.formatWeight(item.weightKg, useLb) else ""
    return "${item.sets}×$reps$unit$weight"
}
