package com.warriorsbox.app.data

import androidx.room.withTransaction
import com.warriorsbox.app.data.db.AppDatabase
import com.warriorsbox.app.data.db.PlanDayEntity
import com.warriorsbox.app.data.db.PlanEntity
import com.warriorsbox.app.data.db.PlanItemEntity
import com.warriorsbox.app.data.db.SessionEntity
import com.warriorsbox.app.data.db.SetLogEntity
import com.warriorsbox.core.engine.PlanGenerator
import com.warriorsbox.core.engine.Progression
import com.warriorsbox.core.engine.WeightMath
import com.warriorsbox.core.model.Exercise
import com.warriorsbox.core.model.GeneratedPlan
import com.warriorsbox.core.model.LoggedSet
import com.warriorsbox.core.model.PlannedExercise
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class DayStatus { PENDING, IN_PROGRESS, DONE }

/** Formato del archivo para pasar un plan a otro celular (.json). */
@Serializable
data class PlanTransferFile(
    val format: String = "warriors-box-plan",
    val version: Int = 1,
    val studentName: String = "",
    val summary: String = "",
    val days: List<TransferDay>,
) {
    @Serializable
    data class TransferDay(
        val week: Int,
        val dayIndex: Int,
        val focus: String,
        val optional: Boolean,
        val deload: Boolean,
        val items: List<TransferItem>,
    )

    @Serializable
    data class TransferItem(
        val exerciseId: String,
        val sets: Int,
        val repsMin: Int,
        val repsMax: Int,
        val targetReps: Int,
        val weightKg: Double,
        val restSec: Int,
        val trainerNote: String? = null,
    )
}

const val CUSTOM_SUMMARY = "Rutina armada por ti: elige los ejercicios de cada día."
const val REST_FOCUS = "Descanso"
const val CUSTOM_FOCUS = "Mi rutina"

class PlanRepository(
    private val db: AppDatabase,
    private val exercises: ExerciseRepository,
    private val users: UserRepository,
) {
    private val dao = db.plans()
    private val sessionDao = db.sessions()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun activePlan(userId: Long): Flow<PlanEntity?> = dao.observeActivePlan(userId)
    suspend fun activePlanNow(userId: Long): PlanEntity? = dao.activePlan(userId)
    fun days(planId: Long): Flow<List<PlanDayEntity>> = dao.observeDays(planId)
    suspend fun daysNow(planId: Long): List<PlanDayEntity> = dao.days(planId)
    fun day(dayId: Long): Flow<PlanDayEntity?> = dao.observeDay(dayId)
    suspend fun dayNow(dayId: Long): PlanDayEntity? = dao.day(dayId)
    fun items(dayId: Long): Flow<List<PlanItemEntity>> = dao.observeItems(dayId)
    suspend fun itemsNow(dayId: Long): List<PlanItemEntity> = dao.items(dayId)
    fun itemsOfPlan(planId: Long): Flow<List<PlanItemEntity>> = dao.observeItemsOfPlan(planId)
    fun sessionsOfPlan(planId: Long): Flow<List<SessionEntity>> = sessionDao.observeSessionsOfPlan(planId)
    suspend fun plan(planId: Long): PlanEntity? = dao.plan(planId)

    /** Genera un plan nuevo (o un nuevo ciclo) y lo deja como activo. */
    suspend fun generate(userId: Long, newCycle: Boolean = false): Pair<Long, GeneratedPlan> {
        val profile = users.trainingProfile(userId) ?: error("Usuario no encontrado")
        val catalog = exercises.allNow()
        val cycle = if (newCycle) (dao.maxCycle(userId) ?: 0) + 1 else (dao.maxCycle(userId) ?: 0).coerceAtLeast(1)
        val previousWeights = if (newCycle) lastWeights(userId) else emptyMap()
        val generated = PlanGenerator.generate(profile, catalog, variation = cycle - 1, previousWeights = previousWeights)
        val planId = db.withTransaction {
            dao.deactivatePlans(userId)
            val planId = dao.insertPlan(
                PlanEntity(
                    userId = userId,
                    cycle = if (newCycle) cycle else maxOf(cycle, 1),
                    summary = generated.summary,
                    warnings = generated.warnings.joinToString("\n"),
                ),
            )
            generated.weeks.forEach { week ->
                week.days.forEach { d ->
                    val dayId = dao.insertDay(
                        PlanDayEntity(planId = planId, week = week.week, dayIndex = d.dayIndex, focus = d.focus, optional = d.optional, deload = week.deload),
                    )
                    dao.insertItems(d.exercises.mapIndexed { i, e -> e.toEntity(dayId, i) })
                }
            }
            planId
        }
        return planId to generated
    }

    /**
     * Rutina armada por el usuario: plan vacío de 5 semanas × 6 días. Los días sin ejercicios
     * cuentan como descanso; al agregar ejercicios pasan a ser días de entrenamiento.
     */
    suspend fun createCustom(userId: Long): Long = db.withTransaction {
        dao.deactivatePlans(userId)
        val planId = dao.insertPlan(
            PlanEntity(
                userId = userId,
                cycle = (dao.maxCycle(userId) ?: 0) + 1,
                summary = CUSTOM_SUMMARY,
            ),
        )
        for (w in 1..PlanGenerator.WEEKS) for (d in 0 until PlanGenerator.DAYS) {
            dao.insertDay(PlanDayEntity(planId = planId, week = w, dayIndex = d, focus = REST_FOCUS, optional = true))
        }
        planId
    }

    suspend fun renameDay(dayId: Long, focus: String) {
        val day = dao.day(dayId) ?: return
        dao.updateDay(day.copy(focus = focus.trim().ifBlank { day.focus }))
    }

    /** Un día con ejercicios es de entrenamiento; uno vacío, de descanso. */
    private suspend fun syncDayKind(dayId: Long) {
        val day = dao.day(dayId) ?: return
        val hasItems = dao.items(dayId).isNotEmpty()
        when {
            hasItems && day.optional && day.focus == REST_FOCUS -> dao.updateDay(day.copy(optional = false, focus = CUSTOM_FOCUS))
            !hasItems && !day.optional -> dao.updateDay(day.copy(optional = true, focus = REST_FOCUS))
        }
    }

    /** Último peso usado por ejercicio (para empezar el nuevo ciclo donde quedaste). */
    suspend fun lastWeights(userId: Long): Map<String, Double> =
        sessionDao.allHistory(userId).groupBy { it.exerciseId }.mapValues { (_, rows) -> rows.last().weightKg }

    fun statusOf(day: PlanDayEntity, sessions: List<SessionEntity>): DayStatus {
        val mine = sessions.filter { it.dayId == day.id }
        return when {
            mine.any { it.completed } -> DayStatus.DONE
            mine.isNotEmpty() -> DayStatus.IN_PROGRESS
            else -> DayStatus.PENDING
        }
    }

    /** Primera semana con días obligatorios sin completar. */
    fun currentWeek(days: List<PlanDayEntity>, sessions: List<SessionEntity>): Int {
        val done = sessions.filter { it.completed }.mapNotNull { it.dayId }.toSet()
        if (days.none { !it.optional }) return 1 // rutina propia todavía sin ejercicios
        return days.filter { !it.optional && it.id !in done }.minOfOrNull { it.week } ?: PlanGenerator.WEEKS
    }

    // ---------------------------------------------------------------- progresión

    /** Aplica la progresión a la semana siguiente según lo registrado en la sesión. */
    suspend fun applyProgression(session: SessionEntity, sets: List<SetLogEntity>): List<String> {
        val day = session.dayId?.let { dao.day(it) } ?: return emptyList()
        if (day.week >= PlanGenerator.WEEKS) return emptyList()
        val nextDay = dao.dayAt(day.planId, day.week + 1, day.dayIndex) ?: return emptyList()
        val items = dao.items(day.id)
        val nextItems = dao.items(nextDay.id)
        val catalog = exercises.byId()
        val messages = mutableListOf<String>()
        val updates = mutableListOf<PlanItemEntity>()
        for (item in items) {
            val logs = sets.filter { it.itemId == item.id }
            // Si hoy se usó otro ejercicio "solo por hoy", no se progresa el original.
            if (logs.isEmpty() || logs.any { it.exerciseId != item.exerciseId }) continue
            val target = nextItems.firstOrNull { it.position == item.position && it.exerciseId == item.exerciseId }
                ?: nextItems.firstOrNull { it.exerciseId == item.exerciseId }
                ?: continue
            val exercise = catalog[item.exerciseId] ?: continue
            val logged = logs.map { LoggedSet(it.weightKg, it.reps, it.done, it.rir) }
            val next = if (nextDay.deload) {
                Progression.deloadFrom(target.toPlanned(), logged)
            } else {
                val result = Progression.next(item.toPlanned(), logged, exercise)
                if (result.outcome != Progression.Outcome.NO_DATA) messages += "${exercise.name}: ${result.message}"
                // Se conservan series y descanso de la semana siguiente por si el entrenador los editó.
                result.next.copy(sets = target.sets, restSec = target.restSec)
            }
            updates += target.copy(weightKg = next.weightKg, targetReps = next.targetReps, repsMax = maxOf(next.repsMax, target.repsMin), sets = next.sets)
        }
        if (updates.isNotEmpty()) dao.updateItems(updates)
        return messages
    }

    // ---------------------------------------------------------------- "No puedo hacerlo"

    /**
     * Reemplaza el ejercicio de un ítem. Si [wholePlan] es true, lo cambia en todas las semanas
     * (desde la actual) en el mismo día y posición; si no, solo en la sesión de hoy.
     */
    suspend fun substitute(item: PlanItemEntity, newExercise: Exercise, wholePlan: Boolean, sessionId: Long?, userId: Long) {
        db.withTransaction {
            if (sessionId != null) sessionDao.replaceExercise(sessionId, item.id, newExercise.id)
            if (!wholePlan) return@withTransaction
            val profile = users.trainingProfile(userId) ?: return@withTransaction
            val day = dao.day(item.dayId) ?: return@withTransaction
            val weight = WeightMath.initialWeight(newExercise, profile)
            val sameSlot = dao.days(day.planId)
                .filter { it.dayIndex == day.dayIndex && it.week >= day.week }
                .flatMap { d -> dao.items(d.id).filter { it.exerciseId == item.exerciseId }.map { it to d } }
            dao.updateItems(
                sameSlot.map { (it, d) ->
                    it.copy(
                        exerciseId = newExercise.id,
                        originalExerciseId = it.originalExerciseId ?: item.exerciseId,
                        weightKg = if (d.deload) WeightMath.roundTo(weight * 0.9, if (weight >= 20) 2.5 else 1.0) else weight,
                        reason = "Reemplazo elegido por ti (antes: ${it.originalExerciseId ?: item.exerciseId}).",
                    )
                },
            )
        }
    }

    // ---------------------------------------------------------------- editor

    suspend fun addItem(dayId: Long, exercise: Exercise, userId: Long) {
        val profile = users.trainingProfile(userId)
        val day = dao.day(dayId)
        val items = dao.items(dayId)
        val rx = if (profile != null) {
            PlanGenerator.prescription(profile.goal, profile.level, exercise.compound, exercise)
        } else {
            PlanGenerator.Prescription(3, 8, 12, 90)
        }
        val weight = profile?.let { WeightMath.initialWeight(exercise, it) } ?: 0.0
        val sets = if (day?.deload == true) maxOf(1, (rx.sets * 0.6).toInt()) else rx.sets
        dao.insertItem(
            PlanItemEntity(
                dayId = dayId, position = (items.maxOfOrNull { it.position } ?: -1) + 1, exerciseId = exercise.id,
                sets = sets, repsMin = rx.repsMin, repsMax = rx.repsMax, targetReps = rx.repsMin, weightKg = weight,
                restSec = rx.restSec, reason = "Agregado manualmente.",
            ),
        )
        syncDayKind(dayId)
    }

    suspend fun updateItem(item: PlanItemEntity) = dao.updateItem(item)

    suspend fun removeItem(item: PlanItemEntity) = db.withTransaction {
        dao.deleteItem(item)
        dao.updateItems(dao.items(item.dayId).mapIndexed { i, it -> it.copy(position = i) })
        syncDayKind(item.dayId)
    }

    suspend fun moveItem(item: PlanItemEntity, delta: Int) = db.withTransaction {
        val list = dao.items(item.dayId).toMutableList()
        val from = list.indexOfFirst { it.id == item.id }
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return@withTransaction
        val moved = list.removeAt(from)
        list.add(to, moved)
        dao.updateItems(list.mapIndexed { i, it -> it.copy(position = i) })
    }

    suspend fun replaceItemExercise(item: PlanItemEntity, exercise: Exercise, userId: Long) {
        val profile = users.trainingProfile(userId)
        val weight = profile?.let { WeightMath.initialWeight(exercise, it) } ?: item.weightKg
        dao.updateItem(item.copy(exerciseId = exercise.id, weightKg = weight, originalExerciseId = item.originalExerciseId ?: item.exerciseId))
    }

    /** Copia los ejercicios de un día al mismo día de otras semanas (la semana de descarga se ajusta sola). */
    suspend fun copyDayToWeeks(dayId: Long, weeks: List<Int>) = db.withTransaction {
        val source = dao.day(dayId) ?: return@withTransaction
        val items = dao.items(dayId)
        for (w in weeks) {
            if (w == source.week) continue
            val target = dao.dayAt(source.planId, w, source.dayIndex) ?: continue
            dao.updateDay(target.copy(focus = source.focus, optional = source.optional))
            dao.deleteItemsOfDay(target.id)
            dao.insertItems(
                items.map {
                    val base = it.copy(id = 0, dayId = target.id)
                    if (target.deload && !source.deload) base.toPlanned().let { p -> PlanGenerator.deloadOf(p) }.toEntity(target.id, it.position).copy(trainerNote = it.trainerNote)
                    else base
                },
            )
        }
    }

    /** Duplica una semana completa sobre otra. */
    suspend fun duplicateWeek(planId: Long, fromWeek: Int, toWeek: Int) {
        val days = dao.days(planId).filter { it.week == fromWeek }
        for (d in days) copyDayToWeeks(d.id, listOf(toWeek))
    }

    // ---------------------------------------------------------------- pasar planes entre celulares

    suspend fun exportPlan(userId: Long): String? {
        val plan = dao.activePlan(userId) ?: return null
        val user = users.get(userId)
        val days = dao.days(plan.id).map { d ->
            PlanTransferFile.TransferDay(
                week = d.week, dayIndex = d.dayIndex, focus = d.focus, optional = d.optional, deload = d.deload,
                items = dao.items(d.id).map {
                    PlanTransferFile.TransferItem(it.exerciseId, it.sets, it.repsMin, it.repsMax, it.targetReps, it.weightKg, it.restSec, it.trainerNote)
                },
            )
        }
        return json.encodeToString(PlanTransferFile.serializer(), PlanTransferFile(studentName = user?.name.orEmpty(), summary = plan.summary, days = days))
    }

    /** Importa un plan y lo deja activo para [userId]. Devuelve cuántos ejercicios no se reconocieron. */
    suspend fun importPlan(userId: Long, text: String): Int {
        val file = json.decodeFromString(PlanTransferFile.serializer(), text)
        require(file.format == "warriors-box-plan") { "El archivo no es un plan de Warriors Box." }
        val known = exercises.byId()
        var unknown = 0
        db.withTransaction {
            dao.deactivatePlans(userId)
            val planId = dao.insertPlan(PlanEntity(userId = userId, cycle = (dao.maxCycle(userId) ?: 0) + 1, summary = "Plan importado. " + file.summary))
            file.days.forEach { d ->
                val dayId = dao.insertDay(PlanDayEntity(planId = planId, week = d.week, dayIndex = d.dayIndex, focus = d.focus, optional = d.optional, deload = d.deload))
                dao.insertItems(
                    d.items.filter { (it.exerciseId in known).also { ok -> if (!ok) unknown++ } }.mapIndexed { i, it ->
                        PlanItemEntity(
                            dayId = dayId, position = i, exerciseId = it.exerciseId, sets = it.sets, repsMin = it.repsMin,
                            repsMax = it.repsMax, targetReps = it.targetReps, weightKg = it.weightKg, restSec = it.restSec,
                            reason = "Plan importado.", trainerNote = it.trainerNote,
                        )
                    },
                )
            }
        }
        return unknown
    }
}

fun PlannedExercise.toEntity(dayId: Long, position: Int) = PlanItemEntity(
    dayId = dayId, position = position, exerciseId = exerciseId, sets = sets, repsMin = repsMin, repsMax = repsMax,
    targetReps = targetReps, weightKg = weightKg, restSec = restSec, reason = reason, trainerNote = note,
)

fun PlanItemEntity.toPlanned() = PlannedExercise(
    exerciseId = exerciseId, sets = sets, repsMin = repsMin, repsMax = repsMax, targetReps = targetReps,
    weightKg = weightKg, restSec = restSec, reason = reason, note = trainerNote,
)
