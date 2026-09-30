package com.warriorsbox.app.ui.routines

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warriorsbox.app.AppContainer
import com.warriorsbox.app.data.SessionRepository
import com.warriorsbox.app.data.SessionSummary
import com.warriorsbox.app.data.db.ExerciseNoteEntity
import com.warriorsbox.app.data.db.PlanDayEntity
import com.warriorsbox.app.data.db.PlanItemEntity
import com.warriorsbox.app.data.db.SetLogEntity
import com.warriorsbox.core.engine.Alternatives
import com.warriorsbox.core.engine.Motivation
import com.warriorsbox.core.model.BodyZone
import com.warriorsbox.core.model.Exercise
import com.warriorsbox.core.model.SkipReason
import com.warriorsbox.core.model.TrainingProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

data class PhraseEvent(val title: String, val phrase: String, val record: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel(private val c: AppContainer, val userId: Long, val dayId: Long) : ViewModel() {

    private val sessionIdFlow = MutableStateFlow<Long?>(null)
    val sessionId: StateFlow<Long?> = sessionIdFlow

    val items: StateFlow<List<PlanItemEntity>> =
        c.plans.items(dayId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val day: StateFlow<PlanDayEntity?> =
        c.plans.day(dayId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val sets: StateFlow<List<SetLogEntity>> = sessionIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else c.sessions.sets(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val notes: StateFlow<List<ExerciseNoteEntity>> = sessionIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else c.sessions.notes(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var catalog by mutableStateOf<Map<String, Exercise>>(emptyMap())
        private set
    var profile by mutableStateOf<TrainingProfile?>(null)
        private set
    /** exerciseId → "Semana anterior: 3 × 10/10/9 @ 40 kg". */
    var previous by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var loaded by mutableStateOf(false)
        private set

    var restTotal by mutableStateOf(0)
        private set
    var restRemaining by mutableStateOf(0)
        private set
    var restEndsAt by mutableStateOf<Long?>(null)
        private set
    private var restJob: Job? = null
    var onRestFinished: (() -> Unit)? = null

    var phrase by mutableStateOf<PhraseEvent?>(null)
    var finishing by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            catalog = c.exercises.byId()
            profile = c.users.trainingProfile(userId)
            sessionIdFlow.value = c.sessions.openSessionFor(dayId)?.id
            refreshPrevious()
            loaded = true
        }
    }

    private suspend fun refreshPrevious() {
        val current = sessionIdFlow.value
        val rows = c.db.sessions().allHistory(userId).filter { it.sessionId != current }
        previous = rows.groupBy { it.exerciseId }.mapValues { (id, list) ->
            val lastSession = list.last().sessionId
            val last = list.filter { it.sessionId == lastSession }
            val date = java.time.Instant.ofEpochMilli(last.first().startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            "Última vez ($date): " + SessionRepository.describe(
                last.map { SetLogEntity(sessionId = 0, itemId = null, exerciseId = id, setIndex = 0, weightKg = it.weightKg, reps = it.reps) },
                catalog[id],
            )
        }
    }

    fun start() = viewModelScope.launch {
        sessionIdFlow.value = c.sessions.startSession(userId, dayId)
    }

    fun updateSet(set: SetLogEntity, weightKg: Double? = null, reps: Int? = null, rir: Int? = null, clearRir: Boolean = false) =
        viewModelScope.launch {
            c.sessions.updateSet(
                set.copy(
                    weightKg = weightKg ?: set.weightKg,
                    reps = reps ?: set.reps,
                    rir = if (clearRir) null else rir ?: set.rir,
                ),
            )
        }

    fun toggle(set: SetLogEntity, item: PlanItemEntity?) = viewModelScope.launch {
        val nowDone = !set.done
        val record = c.sessions.toggleDone(set, nowDone, userId)
        if (!nowDone) return@launch
        startRest(item?.restSec ?: 90)
        // ¿Se completó el ejercicio? → frase irónica.
        val all = sets.value.map { if (it.id == set.id) it.copy(done = true, isRecord = record) else it }
        val mine = all.filter { it.itemId == set.itemId }
        val exerciseDone = mine.isNotEmpty() && mine.all { it.done }
        if (record || exerciseDone) {
            val exercise = catalog[set.exerciseId]
            val isLast = all.all { it.done }
            val struggled = item != null && mine.any { it.reps < item.targetReps && catalog[it.exerciseId]?.timed != true }
            val context = if (exercise == null) Motivation.Context.GENERIC else Motivation.contextFor(exercise, record, struggled, isLast)
            val avoid = c.settings.current().lastPhrase
            val text = Motivation.pick(context, Random.Default, avoid)
            c.settings.setLastPhrase(text)
            phrase = PhraseEvent(
                title = when {
                    record -> "¡Récord personal!"
                    isLast -> "¡Sesión completa!"
                    else -> "${exercise?.name ?: "Ejercicio"} completado"
                },
                phrase = text,
                record = record,
            )
        }
    }

    fun addSet(item: PlanItemEntity) = viewModelScope.launch {
        val id = sessionIdFlow.value ?: return@launch
        c.sessions.addSet(id, item.id, item.exerciseId)
    }

    fun removeSet(set: SetLogEntity) = viewModelScope.launch { c.sessions.removeSet(set) }

    fun saveNote(item: PlanItemEntity, exerciseId: String, text: String, existing: ExerciseNoteEntity?) = viewModelScope.launch {
        val id = sessionIdFlow.value ?: return@launch
        c.sessions.saveNote(id, item.id, exerciseId, text, existing?.id)
    }

    // ------------------------------------------------------------ descanso

    fun startRest(seconds: Int) {
        if (seconds <= 0) return
        restJob?.cancel()
        restTotal = seconds
        restEndsAt = System.currentTimeMillis() + seconds * 1000L
        restJob = viewModelScope.launch {
            // Termina por reloj real (si el celular se bloquea) o por número de ciclos (sin depender solo del reloj).
            var ticks = 0
            while (true) {
                val left = (((restEndsAt ?: 0L) - System.currentTimeMillis()) / 1000L).toInt()
                restRemaining = left.coerceAtLeast(0)
                if (left <= 0 || ticks > restTotal * 4 + 8) break
                delay(250)
                ticks++
            }
            restEndsAt = null
            onRestFinished?.invoke()
        }
    }

    fun addRest(seconds: Int) {
        val end = restEndsAt ?: return
        restEndsAt = end + seconds * 1000L
        restTotal += seconds
    }

    fun skipRest() {
        restJob?.cancel()
        restEndsAt = null
        restRemaining = 0
    }

    // ------------------------------------------------------------ "No puedo hacerlo"

    fun alternatives(item: PlanItemEntity, currentExerciseId: String, reason: SkipReason, zone: BodyZone?): List<Alternatives.Suggestion> {
        val p = profile ?: return emptyList()
        val original = catalog[currentExerciseId] ?: return emptyList()
        return Alternatives.find(original, reason, p, catalog.values.toList(), zone)
    }

    fun substitute(item: PlanItemEntity, newExercise: Exercise, wholePlan: Boolean, reason: SkipReason, zone: BodyZone?, originalName: String) =
        viewModelScope.launch {
            if (reason == SkipReason.PAIN) {
                val z = zone ?: catalog[item.exerciseId]?.contraindications?.firstOrNull() ?: BodyZone.OTHER
                c.users.addPain(userId, z, "Molestia registrada durante: $originalName")
                profile = c.users.trainingProfile(userId)
            }
            c.plans.substitute(item, newExercise, wholePlan, sessionIdFlow.value, userId)
        }

    fun finish(onDone: (SessionSummary) -> Unit) = viewModelScope.launch {
        val id = sessionIdFlow.value ?: return@launch
        finishing = true
        skipRest()
        val summary = c.sessions.finish(id)
        finishing = false
        onDone(summary)
    }

    fun discard(onDone: () -> Unit) = viewModelScope.launch {
        sessionIdFlow.value?.let { c.sessions.discard(it) }
        sessionIdFlow.value = null
        skipRest()
        onDone()
    }
}
