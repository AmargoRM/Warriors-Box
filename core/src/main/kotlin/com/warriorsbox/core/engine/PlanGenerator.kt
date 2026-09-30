package com.warriorsbox.core.engine

import com.warriorsbox.core.model.Equipment
import com.warriorsbox.core.model.Exercise
import com.warriorsbox.core.model.GeneratedPlan
import com.warriorsbox.core.model.Goal
import com.warriorsbox.core.model.Level
import com.warriorsbox.core.model.MovementPattern
import com.warriorsbox.core.model.MovementPattern.CARDIO
import com.warriorsbox.core.model.MovementPattern.CARRY
import com.warriorsbox.core.model.MovementPattern.CORE
import com.warriorsbox.core.model.MovementPattern.HINGE
import com.warriorsbox.core.model.MovementPattern.HORIZONTAL_PULL
import com.warriorsbox.core.model.MovementPattern.HORIZONTAL_PUSH
import com.warriorsbox.core.model.MovementPattern.ISOLATION
import com.warriorsbox.core.model.MovementPattern.LUNGE
import com.warriorsbox.core.model.MovementPattern.SQUAT
import com.warriorsbox.core.model.MovementPattern.VERTICAL_PULL
import com.warriorsbox.core.model.MovementPattern.VERTICAL_PUSH
import com.warriorsbox.core.model.Muscle
import com.warriorsbox.core.model.PlannedDay
import com.warriorsbox.core.model.PlannedExercise
import com.warriorsbox.core.model.PlannedWeek
import com.warriorsbox.core.model.TrainingProfile
import kotlin.math.ceil
import kotlin.math.max

/**
 * Motor de recomendaciones basado en reglas (funciona sin internet).
 * Genera un plan de [WEEKS] semanas × 6 días (lunes a sábado).
 */
object PlanGenerator {

    const val WEEKS = 5
    const val DAYS = 6
    const val DELOAD_WEEK = 5

    data class Slot(
        val pattern: MovementPattern,
        val muscles: Set<Muscle> = emptySet(),
        val compound: Boolean = true,
    ) {
        val key: String get() = pattern.name + muscles.sorted().joinToString()
    }

    enum class DayType(val focus: String, val slots: List<Slot>, val optional: Boolean = false) {
        FULL_A(
            "Cuerpo completo A",
            listOf(
                Slot(SQUAT), Slot(HORIZONTAL_PUSH), Slot(HORIZONTAL_PULL), Slot(HINGE), Slot(VERTICAL_PUSH),
                Slot(CORE, compound = false), Slot(ISOLATION, setOf(Muscle.BICEPS), false),
                Slot(ISOLATION, setOf(Muscle.CALVES), false),
            ),
        ),
        FULL_B(
            "Cuerpo completo B",
            listOf(
                Slot(HINGE), Slot(VERTICAL_PULL), Slot(HORIZONTAL_PUSH), Slot(LUNGE), Slot(HORIZONTAL_PULL),
                Slot(CORE, compound = false), Slot(ISOLATION, setOf(Muscle.TRICEPS), false),
                Slot(ISOLATION, setOf(Muscle.SHOULDERS), false),
            ),
        ),
        FULL_C(
            "Cuerpo completo C",
            listOf(
                Slot(SQUAT), Slot(HORIZONTAL_PULL), Slot(VERTICAL_PUSH), Slot(HINGE), Slot(HORIZONTAL_PUSH),
                Slot(CORE, compound = false), Slot(ISOLATION, setOf(Muscle.HAMSTRINGS), false),
                Slot(ISOLATION, setOf(Muscle.BICEPS), false),
            ),
        ),
        UPPER(
            "Torso",
            listOf(
                Slot(HORIZONTAL_PUSH), Slot(HORIZONTAL_PULL), Slot(VERTICAL_PUSH), Slot(VERTICAL_PULL),
                Slot(ISOLATION, setOf(Muscle.SHOULDERS), false), Slot(ISOLATION, setOf(Muscle.BICEPS), false),
                Slot(ISOLATION, setOf(Muscle.TRICEPS), false),
            ),
        ),
        LOWER(
            "Pierna",
            listOf(
                Slot(SQUAT), Slot(HINGE), Slot(LUNGE), Slot(ISOLATION, setOf(Muscle.QUADS), false),
                Slot(ISOLATION, setOf(Muscle.HAMSTRINGS), false), Slot(ISOLATION, setOf(Muscle.CALVES), false),
                Slot(CORE, compound = false),
            ),
        ),
        PUSH(
            "Empuje (pecho, hombro, tríceps)",
            listOf(
                Slot(HORIZONTAL_PUSH), Slot(VERTICAL_PUSH), Slot(HORIZONTAL_PUSH, setOf(Muscle.CHEST)),
                Slot(ISOLATION, setOf(Muscle.SHOULDERS), false), Slot(ISOLATION, setOf(Muscle.TRICEPS), false),
                Slot(ISOLATION, setOf(Muscle.CHEST), false), Slot(CORE, compound = false),
            ),
        ),
        PULL(
            "Tracción (espalda, bíceps)",
            listOf(
                Slot(VERTICAL_PULL), Slot(HORIZONTAL_PULL), Slot(HORIZONTAL_PULL, setOf(Muscle.MIDDLE_BACK)),
                Slot(HORIZONTAL_PULL, setOf(Muscle.SHOULDERS), false), Slot(ISOLATION, setOf(Muscle.BICEPS), false),
                Slot(ISOLATION, setOf(Muscle.BICEPS, Muscle.FOREARMS), false), Slot(CORE, compound = false),
            ),
        ),
        LEGS(
            "Pierna",
            listOf(
                Slot(SQUAT), Slot(HINGE), Slot(LUNGE), Slot(ISOLATION, setOf(Muscle.QUADS), false),
                Slot(ISOLATION, setOf(Muscle.HAMSTRINGS), false), Slot(ISOLATION, setOf(Muscle.CALVES), false),
                Slot(CORE, compound = false),
            ),
        ),
        CARDIO_CORE(
            "Cardio + core (opcional)",
            listOf(Slot(CARDIO, compound = false), Slot(CORE, compound = false), Slot(CORE, compound = false), Slot(CARRY)),
            optional = true,
        ),
    }

    fun splitFor(daysPerWeek: Int): List<DayType> = when (daysPerWeek.coerceIn(3, 6)) {
        3 -> listOf(DayType.FULL_A, DayType.CARDIO_CORE, DayType.FULL_B, DayType.CARDIO_CORE, DayType.FULL_C, DayType.CARDIO_CORE)
        4 -> listOf(DayType.UPPER, DayType.LOWER, DayType.CARDIO_CORE, DayType.UPPER, DayType.LOWER, DayType.CARDIO_CORE)
        5 -> listOf(DayType.UPPER, DayType.LOWER, DayType.CARDIO_CORE, DayType.PUSH, DayType.PULL, DayType.LEGS)
        else -> listOf(DayType.PUSH, DayType.PULL, DayType.LEGS, DayType.PUSH, DayType.PULL, DayType.LEGS)
    }

    fun slotCount(minutes: Int): Int = when {
        minutes <= 30 -> 4
        minutes <= 45 -> 5
        minutes <= 60 -> 6
        else -> 7
    }

    data class Prescription(val sets: Int, val repsMin: Int, val repsMax: Int, val restSec: Int)

    fun prescription(goal: Goal, level: Level, compound: Boolean, exercise: Exercise): Prescription {
        if (exercise.pattern == CARDIO) {
            val minutes = when (goal) {
                Goal.FAT_LOSS, Goal.ENDURANCE -> 20
                Goal.REHAB -> 10
                else -> 15
            }
            return Prescription(1, minutes * 60, minutes * 60, 0)
        }
        if (exercise.timed) {
            val (lo, hi) = when (level) {
                Level.BEGINNER -> 20 to 30
                Level.INTERMEDIATE -> 30 to 45
                Level.ADVANCED -> 45 to 60
            }
            return Prescription(3, lo, hi, 45)
        }
        var p = when (goal) {
            Goal.STRENGTH -> if (compound) Prescription(4, 4, 6, 150) else Prescription(3, 8, 10, 90)
            Goal.MUSCLE -> if (compound) Prescription(4, 6, 10, 120) else Prescription(3, 10, 12, 75)
            Goal.FAT_LOSS -> if (compound) Prescription(3, 10, 12, 60) else Prescription(3, 12, 15, 45)
            Goal.ENDURANCE -> Prescription(3, 12, 15, 45)
            Goal.HEALTH -> if (compound) Prescription(3, 8, 12, 90) else Prescription(3, 10, 12, 60)
            Goal.REHAB -> Prescription(2, 12, 15, 60)
        }
        if (level == Level.BEGINNER) p = p.copy(sets = minOf(p.sets, 3))
        if (level == Level.ADVANCED && compound && goal != Goal.REHAB) p = p.copy(sets = p.sets + 1)
        return p
    }

    /** Candidatos para un hueco del plan, ordenados del más recomendado al menos. */
    fun candidates(slot: Slot, profile: TrainingProfile, catalog: List<Exercise>, strictLevel: Boolean = true): List<Exercise> {
        val maxLevel = if (profile.goal == Goal.REHAB) Level.BEGINNER.rank else profile.level.rank
        return catalog.asSequence()
            .filter { it.pattern == slot.pattern }
            .filter { slot.muscles.isEmpty() || it.primaryMuscles.any { m -> m in slot.muscles } }
            .filter { it.isAvailableWith(profile.equipment) }
            .filter { it.isSafeFor(profile.activeInjuries) }
            .filter { !strictLevel || it.level.rank <= maxLevel }
            .sortedWith(compareByDescending<Exercise> { score(it, slot, profile) }.thenBy { it.rank }.thenBy { it.id })
            .toList()
    }

    private fun score(e: Exercise, slot: Slot, profile: TrainingProfile): Int {
        var s = 0
        if (e.curated) s += 100
        if (slot.compound == e.compound) s += 20
        s -= kotlin.math.abs(e.level.rank - profile.level.rank) * 5
        val beginnerFriendly = setOf(Equipment.MACHINE, Equipment.DUMBBELL, Equipment.CABLE)
        when {
            profile.goal == Goal.REHAB &&
                (e.isBodyweight || Equipment.BANDS in e.equipment || Equipment.MACHINE in e.equipment) -> s += 10
            profile.level == Level.BEGINNER && e.equipment.any { it in beginnerFriendly } -> s += 8
            profile.level != Level.BEGINNER && profile.goal == Goal.STRENGTH && Equipment.BARBELL in e.equipment -> s += 10
            profile.level != Level.BEGINNER && Equipment.BARBELL in e.equipment && e.compound -> s += 5
        }
        // Con equipo disponible, preferir ejercicios con carga sobre peso corporal (salvo core y cardio).
        val light = e.equipment.all { it == Equipment.NONE || it == Equipment.BANDS }
        if (light && slot.pattern !in setOf(CORE, CARDIO) &&
            profile.equipment.any { it !in setOf(Equipment.NONE, Equipment.BENCH, Equipment.BANDS) }
        ) {
            s -= 6
        }
        if (slot.muscles.isNotEmpty() && e.primaryMuscles.firstOrNull() in slot.muscles) s += 5
        if (e.unilateral) s -= 2
        return s
    }

    fun generate(
        profile: TrainingProfile,
        catalog: List<Exercise>,
        variation: Int = 0,
        previousWeights: Map<String, Double> = emptyMap(),
    ): GeneratedPlan {
        val usable = catalog.filter { it.pattern != MovementPattern.MOBILITY }
        val split = splitFor(profile.daysPerWeek)
        val count = slotCount(profile.minutesPerSession)
        val slotUse = mutableMapOf<String, Int>()
        val excludedByInjury = catalog.count { !it.isSafeFor(profile.activeInjuries) }
        val warnings = mutableListOf<String>()

        val baseDays = split.mapIndexed { index, type ->
            val usedToday = mutableSetOf<String>()
            val slots = buildList {
                addAll(type.slots.take(if (type.optional) type.slots.size else count))
                if (!type.optional && profile.goal == Goal.FAT_LOSS && profile.minutesPerSession >= 45) {
                    add(Slot(CARDIO, compound = false))
                }
            }
            val exercises = slots.mapNotNull { slot ->
                var options = candidates(slot, profile, usable).filter { it.id !in usedToday }
                if (options.isEmpty()) options = candidates(slot, profile, usable, strictLevel = false).filter { it.id !in usedToday }
                if (options.isEmpty()) return@mapNotNull null
                val use = slotUse.getOrDefault(slot.key, 0)
                slotUse[slot.key] = use + 1
                // Rotar entre las 2 mejores opciones (de la misma calidad) para dar variedad.
                val top = options.takeWhile { it.curated == options[0].curated }.take(2)
                val pick = top[(use + variation) % top.size]
                usedToday += pick.id
                val rx = prescription(profile.goal, profile.level, slot.compound && pick.compound, pick)
                val weight = previousWeights[pick.id] ?: WeightMath.initialWeight(pick, profile)
                PlannedExercise(
                    exerciseId = pick.id,
                    sets = rx.sets,
                    repsMin = rx.repsMin,
                    repsMax = rx.repsMax,
                    targetReps = rx.repsMin,
                    weightKg = weight,
                    restSec = rx.restSec,
                    reason = reasonFor(pick, slot, profile),
                )
            }
            PlannedDay(dayIndex = index, focus = type.focus, optional = type.optional, exercises = exercises)
        }

        val weeks = (1..WEEKS).map { w ->
            val deload = w == DELOAD_WEEK
            PlannedWeek(
                week = w,
                deload = deload,
                days = if (deload) baseDays.map { d -> d.copy(exercises = d.exercises.map { deloadOf(it) }) } else baseDays,
            )
        }

        if (profile.medicalRestriction) {
            warnings += "Indicaste una restricción médica o un problema cardíaco: consulta a un profesional antes de empezar."
        }
        if (profile.activeInjuries.isNotEmpty()) {
            warnings += "Se excluyeron $excludedByInjury ejercicios del catálogo por tus molestias en: " +
                profile.activeInjuries.joinToString { it.label.lowercase() } + "."
        }
        warnings += "Los pesos son una sugerencia inicial: ajústalos para dejar 2–3 repeticiones en reserva."

        val summary = "Plan de ${profile.daysPerWeek} días de entrenamiento + días opcionales de cardio, " +
            "objetivo ${profile.goal.label.lowercase()}, nivel ${profile.level.label.lowercase()}, " +
            "${profile.minutesPerSession} min por sesión. Semana $DELOAD_WEEK = descarga."
        return GeneratedPlan(weeks = weeks, summary = summary, warnings = warnings)
    }

    fun deloadOf(item: PlannedExercise): PlannedExercise {
        val sets = max(1, ceil(item.sets * 0.6).toInt())
        return item.copy(
            sets = sets,
            weightKg = WeightMath.roundTo(item.weightKg * 0.9, if (item.weightKg >= 20) 2.5 else 1.0),
            targetReps = item.repsMin,
        )
    }

    private fun reasonFor(e: Exercise, slot: Slot, profile: TrainingProfile): String {
        val parts = mutableListOf<String>()
        parts += "Trabaja ${slot.pattern.label.lowercase()}"
        if (profile.activeInjuries.isNotEmpty()) parts += "es seguro para tus molestias registradas"
        if (e.isBodyweight) parts += "no necesita equipo" else parts += "usa ${e.equipment.joinToString { it.label.lowercase() }}"
        parts += "nivel ${e.level.label.lowercase()}"
        return "Elegido porque: " + parts.joinToString(", ") + " (objetivo: ${profile.goal.label.lowercase()})."
    }
}
