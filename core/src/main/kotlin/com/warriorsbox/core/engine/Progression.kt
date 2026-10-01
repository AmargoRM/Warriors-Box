package com.warriorsbox.core.engine

import com.warriorsbox.core.model.Exercise
import com.warriorsbox.core.model.LoggedSet
import com.warriorsbox.core.model.PlannedExercise
import kotlin.math.max

/**
 * Progresión semana a semana.
 *
 * - Si completó todas las series con las repeticiones máximas y dejó 2+ en reserva: sube el peso
 *   (~2.5 % tren superior, ~5 % tren inferior; al menos un salto de disco) y vuelve al mínimo de repeticiones.
 * - Si completó todas las series con al menos el objetivo: +1 repetición (hasta el máximo).
 * - Si no completó: repite la misma carga.
 */
object Progression {

    enum class Outcome { INCREASE_WEIGHT, INCREASE_REPS, REPEAT, NO_DATA }

    data class Result(val next: PlannedExercise, val outcome: Outcome, val message: String)

    fun next(current: PlannedExercise, logs: List<LoggedSet>, exercise: Exercise): Result {
        val done = logs.filter { it.done }
        if (done.isEmpty()) return Result(current, Outcome.NO_DATA, "Sin series registradas: se mantiene el objetivo.")

        val usedWeight = done.maxOf { it.weightKg }
        val allSetsDone = done.size >= current.sets
        val hitTarget = allSetsDone && done.all { it.reps >= current.targetReps }
        val hitTop = allSetsDone && done.all { it.reps >= current.repsMax }
        val rirValues = done.mapNotNull { it.rir }
        val hadReserve = rirValues.isEmpty() || rirValues.average() >= 2.0
        val step = WeightMath.increment(exercise)

        return when {
            hitTop && hadReserve && step > 0.0 -> {
                val pct = if (exercise.isUpperBody) 0.025 else 0.05
                val raw = max(usedWeight * (1 + pct), usedWeight + step)
                val newWeight = WeightMath.roundTo(raw, step).coerceAtLeast(usedWeight + step)
                Result(
                    current.copy(weightKg = newWeight, targetReps = current.repsMin),
                    Outcome.INCREASE_WEIGHT,
                    "¡Subes a ${fmt(newWeight)} kg!",
                )
            }
            hitTop && step == 0.0 -> {
                // Peso corporal o por tiempo: se progresa con más repeticiones / segundos.
                val extra = if (exercise.timed) 5 else 2
                val newMax = current.repsMax + extra
                Result(
                    current.copy(repsMax = newMax, targetReps = minOf(current.targetReps + extra, newMax)),
                    Outcome.INCREASE_REPS,
                    if (exercise.timed) "+$extra segundos la próxima semana." else "+$extra repeticiones la próxima semana.",
                )
            }
            hitTarget -> {
                val reps = minOf(current.targetReps + 1, current.repsMax)
                Result(
                    current.copy(weightKg = usedWeight, targetReps = reps),
                    Outcome.INCREASE_REPS,
                    "Mismo peso, objetivo $reps repeticiones.",
                )
            }
            else -> Result(
                current.copy(weightKg = usedWeight),
                Outcome.REPEAT,
                "Repite la carga hasta completar todas las series.",
            )
        }
    }

    /** Semana de descarga a partir de lo que se hizo en la semana anterior. */
    fun deloadFrom(previous: PlannedExercise, logs: List<LoggedSet>): PlannedExercise {
        val used = logs.filter { it.done }.maxOfOrNull { it.weightKg } ?: previous.weightKg
        return PlanGenerator.deloadOf(previous.copy(weightKg = used))
    }

    private fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
}
