package com.warriorsbox.core.engine

import com.warriorsbox.core.model.Equipment
import com.warriorsbox.core.model.Exercise
import com.warriorsbox.core.model.Goal
import com.warriorsbox.core.model.Level
import com.warriorsbox.core.model.MovementPattern
import com.warriorsbox.core.model.Muscle
import com.warriorsbox.core.model.Sex
import com.warriorsbox.core.model.TrainingProfile
import kotlin.math.max
import kotlin.math.roundToInt

/** Cálculos de cargas: incrementos, redondeos y peso inicial sugerido. */
object WeightMath {

    /** Salto mínimo de peso disponible normalmente en el gimnasio para ese ejercicio. */
    fun increment(exercise: Exercise): Double = when {
        exercise.isBodyweight || !exercise.loadable -> 0.0
        exercise.timed && exercise.pattern != MovementPattern.CARRY -> 0.0
        Equipment.BARBELL in exercise.equipment || Equipment.EZ_BAR in exercise.equipment -> 2.5
        Equipment.MACHINE in exercise.equipment || Equipment.CABLE in exercise.equipment -> 2.5
        Equipment.DUMBBELL in exercise.equipment || Equipment.KETTLEBELL in exercise.equipment -> 1.0
        else -> 0.0
    }

    fun roundTo(value: Double, step: Double): Double {
        if (step <= 0.0) return value
        return (value / step).roundToInt() * step
    }

    private fun isolationRatio(exercise: Exercise): Double = when (exercise.primaryMuscles.firstOrNull()) {
        Muscle.BICEPS, Muscle.TRICEPS -> 0.3
        Muscle.CHEST, Muscle.LATS -> 0.25
        Muscle.QUADS -> 0.45
        Muscle.HAMSTRINGS, Muscle.GLUTES -> 0.35
        Muscle.CALVES -> 0.6
        Muscle.TRAPS -> 0.5
        Muscle.MIDDLE_BACK -> 0.2
        else -> 0.15
    }

    /**
     * Peso inicial conservador. Pensado para dejar 2–3 repeticiones en reserva.
     * Para mancuernas y pesas rusas es el peso de CADA pesa.
     */
    fun initialWeight(exercise: Exercise, profile: TrainingProfile): Double {
        if (exercise.isBodyweight || !exercise.loadable) return 0.0
        if (exercise.timed && exercise.pattern != MovementPattern.CARRY) return 0.0
        if (exercise.equipment.all { it == Equipment.BANDS || it == Equipment.BENCH || it == Equipment.PULLUP_BAR }) return 0.0
        if (exercise.pattern == MovementPattern.CARDIO || exercise.pattern == MovementPattern.MOBILITY) return 0.0

        val isolationLike = exercise.pattern == MovementPattern.ISOLATION ||
            (!exercise.compound && exercise.pattern != MovementPattern.CARRY && exercise.pattern != MovementPattern.CORE)
        val ratio = if (isolationLike) isolationRatio(exercise) else when (exercise.pattern) {
            MovementPattern.SQUAT -> 0.8
            MovementPattern.HINGE -> if (exercise.compound) 0.9 else 0.25
            MovementPattern.HORIZONTAL_PUSH -> 0.6
            MovementPattern.VERTICAL_PUSH -> 0.4
            MovementPattern.HORIZONTAL_PULL -> 0.5
            MovementPattern.VERTICAL_PULL -> 0.6
            MovementPattern.LUNGE -> 0.35
            MovementPattern.ISOLATION -> 0.15
            MovementPattern.CORE -> 0.1
            MovementPattern.CARRY -> 0.8
            else -> 0.0
        }
        val equipmentFactor = when {
            Equipment.BARBELL in exercise.equipment || Equipment.EZ_BAR in exercise.equipment -> 1.0
            Equipment.DUMBBELL in exercise.equipment || Equipment.KETTLEBELL in exercise.equipment -> 0.35
            Equipment.MACHINE in exercise.equipment || Equipment.CABLE in exercise.equipment -> 0.8
            else -> 0.0
        }
        val levelFactor = when (profile.level) {
            Level.BEGINNER -> 0.5
            Level.INTERMEDIATE -> 1.0
            Level.ADVANCED -> 1.3
        }
        val sexFactor = if (profile.sex == Sex.FEMALE) 0.7 else 1.0
        val ageFactor = when {
            profile.age >= 65 -> 0.7
            profile.age >= 50 -> 0.85
            profile.age < 16 -> 0.6
            else -> 1.0
        }
        val goalFactor = if (profile.goal == Goal.REHAB) 0.6 else 1.0
        val unilateralFactor = if (exercise.unilateral && equipmentFactor == 1.0) 0.6 else 1.0

        var weight = profile.bodyWeightKg * ratio * equipmentFactor * levelFactor * sexFactor *
            ageFactor * goalFactor * unilateralFactor

        val step = increment(exercise)
        weight = roundTo(weight, if (step == 0.0) 1.0 else step)
        weight = when {
            Equipment.BARBELL in exercise.equipment && exercise.pattern != MovementPattern.ISOLATION -> max(weight, 20.0)
            Equipment.BARBELL in exercise.equipment || Equipment.EZ_BAR in exercise.equipment -> max(weight, 10.0)
            Equipment.DUMBBELL in exercise.equipment || Equipment.KETTLEBELL in exercise.equipment -> max(weight, 2.0)
            else -> max(weight, 5.0)
        }
        val cap = profile.maxDumbbellKg
        if (cap != null && Equipment.DUMBBELL in exercise.equipment && weight > cap) weight = cap
        return weight
    }
}
