package com.warriorsbox.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warriorsbox.app.data.AppSettings
import com.warriorsbox.app.data.WeeklySummary
import com.warriorsbox.app.ui.components.AppBackground
import com.warriorsbox.app.ui.components.LabeledRow
import com.warriorsbox.app.ui.components.LineChart
import com.warriorsbox.app.ui.components.SectionTitle
import com.warriorsbox.app.ui.components.StatTile
import com.warriorsbox.app.ui.components.WbCard
import com.warriorsbox.app.ui.components.WbTopBar
import com.warriorsbox.app.ui.components.container
import com.warriorsbox.app.ui.theme.WbGold
import com.warriorsbox.app.ui.theme.WbGreen
import com.warriorsbox.core.engine.Units
import com.warriorsbox.core.model.Exercise
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val es: Locale = Locale.forLanguageTag("es")

@Composable
fun HistoryScreen(userId: Long, onBack: () -> Unit, onExercise: (String) -> Unit, onSession: (Long) -> Unit) {
    val c = container()
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val sessions by c.sessions.sessionsOf(userId).collectAsStateWithLifecycle(emptyList())
    val history by c.db.sessions().observeAllHistory(userId).collectAsStateWithLifecycle(emptyList())
    val user by c.users.observe(userId).collectAsStateWithLifecycle(null)
    var weekly by remember { mutableStateOf(WeeklySummary(0, 0.0, 0, 0)) }
    var catalog by remember { mutableStateOf<Map<String, Exercise>>(emptyMap()) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    LaunchedEffect(sessions.size) {
        weekly = c.stats.weekly(userId)
        catalog = c.exercises.byId()
    }
    val zone = ZoneId.systemDefault()
    val completed = sessions.filter { it.completed }
    val trainedDays = completed.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }.toSet()

    AppBackground(user?.backgroundPath ?: settings.backgroundPath, settings.veil) {
        Scaffold(containerColor = Color.Transparent, contentColor = Color.White, topBar = { WbTopBar("Historial · ${user?.name ?: ""}", onBack) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WbCard {
                    SectionTitle("Esta semana")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("${weekly.sessions}", "sesiones", Modifier.weight(1f))
                        StatTile(Units.formatWeight(weekly.volumeKg, settings.useLb).substringBefore(' '), if (settings.useLb) "lb volumen" else "kg volumen", Modifier.weight(1f))
                        StatTile("${weekly.streak}", "días de racha", Modifier.weight(1f))
                        StatTile("≈${weekly.kcal}", "kcal", Modifier.weight(1f))
                    }
                }
                WbCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mes anterior") }
                        Text(
                            month.month.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() } + " ${month.year}",
                            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente") }
                    }
                    MonthCalendar(month, trainedDays)
                }
                WbCard {
                    SectionTitle("Sesiones")
                    if (completed.isEmpty()) Text("Todavía no hay sesiones completadas.")
                    completed.take(30).forEach { s ->
                        val date = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate()
                        Column(Modifier.fillMaxWidth().clickable { onSession(s.id) }.padding(vertical = 6.dp)) {
                            Text("$date · ${s.title}", fontWeight = FontWeight.SemiBold)
                            Text("≈${s.kcal ?: 0} kcal", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                WbCard {
                    SectionTitle("Progreso por ejercicio")
                    val byExercise = history.groupBy { it.exerciseId }
                    if (byExercise.isEmpty()) Text("Registra series para ver tu progreso.")
                    byExercise.entries.sortedByDescending { it.value.size }.forEach { (id, rows) ->
                        val best = rows.maxOf { it.weightKg }
                        val records = rows.count { it.isRecord }
                        Row(Modifier.fillMaxWidth().clickable { onExercise(id) }.padding(vertical = 6.dp)) {
                            Text(catalog[id]?.name ?: id, modifier = Modifier.weight(1f))
                            Text(
                                (if (best > 0) "máx. ${Units.formatWeight(best, settings.useLb)}" else "${rows.maxOf { it.reps }} reps") +
                                    if (records > 0) " · 🏆$records" else "",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCalendar(month: YearMonth, trained: Set<LocalDate>) {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val today = LocalDate.now()
    Column {
        Row { listOf("L", "M", "M", "J", "V", "S", "D").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) } }
        val cells = offset + days
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row {
                for (col in 0 until 7) {
                    val dayNumber = r * 7 + col - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (dayNumber in 1..days) {
                            val date = month.atDay(dayNumber)
                            val done = date in trained
                            Box(
                                Modifier.fillMaxSize().clip(CircleShape)
                                    .background(if (done) WbGreen else if (date == today) WbGold.copy(alpha = 0.3f) else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("$dayNumber", color = if (done) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = if (done) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressScreen(userId: Long, exerciseId: String, onBack: () -> Unit) {
    val c = container()
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val history by c.db.sessions().observeAllHistory(userId).collectAsStateWithLifecycle(emptyList())
    var exercise by remember { mutableStateOf<Exercise?>(null) }
    var metric by remember { mutableStateOf(0) }
    LaunchedEffect(exerciseId) { exercise = c.exercises.get(exerciseId) }
    val points = c.stats.progress(history, exerciseId)
    val lb = settings.useLb
    fun w(v: Double) = if (lb) Units.kgToLb(v) else v
    AppBackground(settings.backgroundPath, settings.veil) {
        Scaffold(containerColor = Color.Transparent, contentColor = Color.White, topBar = { WbTopBar(exercise?.name ?: "Progreso", onBack) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WbCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Peso máx.", "1RM estimado", "Volumen", "Reps").forEachIndexed { i, label ->
                            FilterChip(selected = metric == i, onClick = { metric = i }, label = { Text(label) })
                        }
                    }
                    LineChart(
                        points.map {
                            when (metric) {
                                0 -> w(it.maxWeight)
                                1 -> w(it.e1rm)
                                2 -> w(it.volume)
                                else -> it.bestReps.toDouble()
                            }.toFloat()
                        },
                    )
                    if (points.isNotEmpty()) Text("${points.first().date} → ${points.last().date}", style = MaterialTheme.typography.labelMedium)
                }
                WbCard {
                    SectionTitle("Récords")
                    val best = points.maxByOrNull { it.e1rm }
                    if (best == null) Text("Sin datos todavía.")
                    best?.let {
                        LabeledRow("Mejor 1RM estimado", Units.formatWeight(it.e1rm, lb) + " (${it.date})")
                        LabeledRow("Peso máximo", Units.formatWeight(points.maxOf { p -> p.maxWeight }, lb))
                        LabeledRow("Más repeticiones", "${points.maxOf { p -> p.bestReps }}")
                        LabeledRow("Sesiones registradas", "${points.size}")
                    }
                    history.filter { it.exerciseId == exerciseId && it.isRecord }.takeLast(10).reversed().forEach {
                        val date = Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                        Text("🏆 $date: ${if (it.weightKg > 0) Units.formatWeight(it.weightKg, lb) + " × " else ""}${it.reps}", color = WbGold)
                    }
                }
            }
        }
    }
}
