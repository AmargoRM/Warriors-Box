package com.warriorsbox.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val nameEn: String? = null,
    val pattern: MovementPattern,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle> = emptyList(),
    /** Todo el equipo necesario (se requieren todos). Vacío o [Equipment.NONE] = peso corporal. */
    val equipment: Set<Equipment> = emptySet(),
    val level: Level = Level.BEGINNER,
    val contraindications: Set<BodyZone> = emptySet(),
    val compound: Boolean = false,
    val steps: List<String> = emptyList(),
    val tip: String? = null,
    val commonErrors: List<String> = emptyList(),
    val image: String? = null,
    val imageUrls: List<String> = emptyList(),
    /** true si el ejercicio tiene nombre e instrucciones en español revisados. */
    val curated: Boolean = false,
    /** true si trabaja cada lado por separado (repeticiones "por lado"). */
    val unilateral: Boolean = false,
    /** Segundos por serie en lugar de repeticiones (planchas, cardio). */
    val timed: Boolean = false,
    val source: String = "warriors-box",
    val custom: Boolean = false,
    /** Orden de preferencia (menor = más usado y recomendado). */
    val rank: Int = 10_000,
    /** false si la carga es el propio cuerpo aunque use barra/paralelas (dominadas, fondos...). */
    val loadable: Boolean = true,
) {
    val isBodyweight: Boolean get() = equipment.isEmpty() || equipment == setOf(Equipment.NONE)
    val isUpperBody: Boolean
        get() = pattern in setOf(
            MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH,
            MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PULL,
        ) || primaryMuscles.any { it in UPPER_MUSCLES }

    fun isAvailableWith(available: Set<Equipment>): Boolean =
        equipment.all { it == Equipment.NONE || it in available }

    fun isSafeFor(injuries: Set<BodyZone>): Boolean = contraindications.none { it in injuries }

    companion object {
        val UPPER_MUSCLES = setOf(
            Muscle.CHEST, Muscle.LATS, Muscle.MIDDLE_BACK, Muscle.TRAPS, Muscle.SHOULDERS,
            Muscle.BICEPS, Muscle.TRICEPS, Muscle.FOREARMS,
        )
    }
}

/** Datos del perfil que usa el motor de recomendaciones. */
@Serializable
data class TrainingProfile(
    val level: Level,
    val goal: Goal,
    val daysPerWeek: Int,
    val minutesPerSession: Int,
    val equipment: Set<Equipment>,
    val activeInjuries: Set<BodyZone> = emptySet(),
    val bodyWeightKg: Double,
    val sex: Sex = Sex.UNSPECIFIED,
    val age: Int = 30,
    /** Mancuerna más pesada disponible (en casa). Null = sin límite. */
    val maxDumbbellKg: Double? = null,
    val medicalRestriction: Boolean = false,
)

@Serializable
data class PlannedExercise(
    val exerciseId: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val targetReps: Int,
    val weightKg: Double,
    val restSec: Int,
    val reason: String = "",
    val note: String? = null,
)

@Serializable
data class PlannedDay(
    /** 0 = lunes … 5 = sábado. */
    val dayIndex: Int,
    val focus: String,
    val optional: Boolean = false,
    val exercises: List<PlannedExercise>,
)

@Serializable
data class PlannedWeek(
    /** 1..5 */
    val week: Int,
    val deload: Boolean,
    val days: List<PlannedDay>,
)

@Serializable
data class GeneratedPlan(
    val weeks: List<PlannedWeek>,
    val summary: String,
    val warnings: List<String> = emptyList(),
)

/** Serie registrada, usada para calcular la progresión. */
@Serializable
data class LoggedSet(
    val weightKg: Double,
    val reps: Int,
    val done: Boolean,
    /** Repeticiones en reserva (0–4). Null si no se registró. */
    val rir: Int? = null,
)
