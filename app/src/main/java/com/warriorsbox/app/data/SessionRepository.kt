package com.warriorsbox.app.data

import androidx.room.withTransaction
import com.warriorsbox.app.data.db.AppDatabase
import com.warriorsbox.app.data.db.ExerciseNoteEntity
import com.warriorsbox.app.data.db.SessionEntity
import com.warriorsbox.app.data.db.SetLogEntity
import com.warriorsbox.core.engine.Calories
import com.warriorsbox.core.engine.Reminders
import com.warriorsbox.core.engine.Stats
import com.warriorsbox.core.model.Exercise
import com.warriorsbox.core.model.MovementPattern
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SummaryLine(val name: String, val detail: String, val record: Boolean)

data class SessionSummary(
    val sessionId: Long,
    val userName: String,
    val userPhoto: String?,
    val title: String,
    val date: LocalDate,
    val durationMin: Int,
    val kcal: Int,
    val volumeKg: Double,
    val lines: List<SummaryLine>,
    val records: List<String>,
    val streak: Int,
    val progressionNotes: List<String> = emptyList(),
)

class SessionRepository(
    private val db: AppDatabase,
    private val exercises: ExerciseRepository,
    private val plans: PlanRepository,
    private val users: UserRepository,
) {
    private val dao = db.sessions()
    private val planDao = db.plans()

    fun session(id: Long): Flow<SessionEntity?> = dao.observe(id)
    fun sets(sessionId: Long): Flow<List<SetLogEntity>> = dao.observeSets(sessionId)
    fun notes(sessionId: Long): Flow<List<ExerciseNoteEntity>> = dao.observeNotes(sessionId)
    fun sessionsOf(userId: Long): Flow<List<SessionEntity>> = dao.observeSessions(userId)
    suspend fun sessionsNow(userId: Long): List<SessionEntity> = dao.sessions(userId)
    suspend fun get(id: Long): SessionEntity? = dao.get(id)

    /** Sesión en curso del día, si existe. */
    suspend fun openSessionFor(dayId: Long): SessionEntity? = dao.sessionsOfDay(dayId).firstOrNull { !it.completed }

    /** Empieza (o retoma) la sesión de un día del plan, creando las series con los valores objetivo. */
    suspend fun startSession(userId: Long, dayId: Long): Long = db.withTransaction {
        openSessionFor(dayId)?.let { return@withTransaction it.id }
        val day = planDao.day(dayId)
        val title = day?.let { "Semana ${it.week} · ${Reminders.PLAN_DAY_NAMES.getOrElse(it.dayIndex) { "" }} · ${it.focus}" }.orEmpty()
        val id = dao.insert(SessionEntity(userId = userId, dayId = dayId, title = title))
        val items = planDao.items(dayId)
        dao.insertSets(
            items.flatMap { item ->
                (0 until item.sets).map { i ->
                    SetLogEntity(sessionId = id, itemId = item.id, exerciseId = item.exerciseId, setIndex = i, weightKg = item.weightKg, reps = item.targetReps)
                }
            },
        )
        id
    }

    suspend fun updateSet(set: SetLogEntity) = dao.updateSet(set)

    suspend fun addSet(sessionId: Long, itemId: Long?, exerciseId: String) {
        val existing = dao.sets(sessionId).filter { it.itemId == itemId }
        val last = existing.maxByOrNull { it.setIndex }
        dao.insertSet(
            SetLogEntity(
                sessionId = sessionId, itemId = itemId, exerciseId = last?.exerciseId ?: exerciseId,
                setIndex = (last?.setIndex ?: -1) + 1, weightKg = last?.weightKg ?: 0.0, reps = last?.reps ?: 10,
            ),
        )
    }

    suspend fun removeSet(set: SetLogEntity) = dao.deleteSet(set)

    /**
     * Marca o desmarca una serie. Devuelve true si es un récord personal.
     */
    suspend fun toggleDone(set: SetLogEntity, done: Boolean, userId: Long): Boolean {
        if (!done) {
            dao.updateSet(set.copy(done = false, doneAt = null, isRecord = false))
            return false
        }
        val history = dao.history(userId, set.exerciseId)
            .filter { it.sessionId != set.sessionId }
            .map { Stats.SetPerf(it.weightKg, it.reps) }
        val sameSession = dao.sets(set.sessionId).filter { it.exerciseId == set.exerciseId && it.done && it.id != set.id }
            .map { Stats.SetPerf(it.weightKg, it.reps) }
        val record = Stats.isRecord(Stats.SetPerf(set.weightKg, set.reps), history) &&
            Stats.isRecord(Stats.SetPerf(set.weightKg, set.reps), history + sameSession)
        dao.updateSet(set.copy(done = true, doneAt = System.currentTimeMillis(), isRecord = record))
        return record
    }

    suspend fun saveNote(sessionId: Long, itemId: Long?, exerciseId: String, note: String, existingId: Long?) {
        dao.upsertNote(ExerciseNoteEntity(id = existingId ?: 0, sessionId = sessionId, itemId = itemId, exerciseId = exerciseId, note = note))
    }

    suspend fun saveSessionNote(sessionId: Long, note: String) {
        val s = dao.get(sessionId) ?: return
        dao.update(s.copy(notes = note))
    }

    suspend fun discard(sessionId: Long) = dao.delete(sessionId)

    /** Termina la sesión: calcula calorías, aplica la progresión y devuelve el resumen. */
    suspend fun finish(sessionId: Long): SessionSummary {
        val session = dao.get(sessionId) ?: error("Sesión no encontrada")
        val sets = dao.sets(sessionId)
        val user = users.get(session.userId)
        val catalog = exercises.byId()
        val now = System.currentTimeMillis()
        val done = sets.filter { it.done }
        val measuredMin = ((now - session.startedAt) / 60000.0)
        val estimatedMin = done.size * 2.0 + 5
        val minutes = if (measuredMin in 5.0..240.0) measuredMin else estimatedMin
        val cardio = done.filter { catalog[it.exerciseId]?.pattern == MovementPattern.CARDIO }
            .map { it.exerciseId to it.reps / 60.0 }
        val avgRir = done.mapNotNull { it.rir }.takeIf { it.isNotEmpty() }?.average()
        val kcal = Calories.session(user?.weightKg ?: 70.0, maxOf(minutes, cardio.sumOf { it.second }), avgRir, cardio)
        val updated = session.copy(endedAt = now, completed = true, kcal = kcal)
        dao.update(updated)
        val notes = plans.applyProgression(updated, sets)
        return summary(sessionId, notes)
    }

    suspend fun summary(sessionId: Long, progressionNotes: List<String> = emptyList()): SessionSummary {
        val session = dao.get(sessionId) ?: error("Sesión no encontrada")
        val user = users.get(session.userId)
        val catalog = exercises.byId()
        val done = dao.sets(sessionId).filter { it.done }
        val zone = ZoneId.systemDefault()
        val lines = done.groupBy { it.itemId to it.exerciseId }.map { (key, list) ->
            val ex = catalog[key.second]
            SummaryLine(
                name = ex?.name ?: key.second,
                detail = describe(list, ex),
                record = list.any { it.isRecord },
            )
        }
        val trainedDays = dao.sessions(session.userId).filter { it.completed }
            .map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }.toSet()
        val end = session.endedAt ?: System.currentTimeMillis()
        return SessionSummary(
            sessionId = sessionId,
            userName = user?.name.orEmpty(),
            userPhoto = user?.photoPath,
            title = session.title,
            date = Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate(),
            durationMin = ((end - session.startedAt) / 60000).toInt().coerceIn(1, 600),
            kcal = session.kcal ?: 0,
            volumeKg = Stats.volume(done.map { Stats.SetPerf(it.weightKg, it.reps) }),
            lines = lines,
            records = lines.filter { it.record }.map { it.name },
            streak = Stats.streak(trainedDays, LocalDate.now()),
            progressionNotes = progressionNotes,
        )
    }

    companion object {
        fun describe(sets: List<SetLogEntity>, exercise: Exercise?): String {
            if (sets.isEmpty()) return ""
            val timed = exercise?.timed == true
            val sameWeight = sets.map { it.weightKg }.distinct().size == 1
            val reps = sets.joinToString("/") { if (timed) com.warriorsbox.core.engine.Units.formatDuration(it.reps) else it.reps.toString() }
            val weight = sets.maxOf { it.weightKg }
            val w = if (weight <= 0) "" else " @ " + formatKg(weight) + (if (sameWeight) "" else " (máx.)")
            return "${sets.size} × $reps$w"
        }

        fun formatKg(v: Double): String = if (v % 1.0 == 0.0) "${v.toInt()} kg" else "${"%.1f".format(java.util.Locale.US, v)} kg"
    }
}
