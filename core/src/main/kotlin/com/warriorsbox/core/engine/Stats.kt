package com.warriorsbox.core.engine

import java.time.LocalDate
import kotlin.math.roundToInt

/** Estadísticas de entrenamiento: récords, volumen, rachas y cumplimiento. */
object Stats {

    /** Fórmula de Epley para estimar 1 repetición máxima. */
    fun estimatedOneRepMax(weightKg: Double, reps: Int): Double =
        if (reps <= 0) 0.0 else if (reps == 1) weightKg else weightKg * (1 + reps / 30.0)

    data class SetPerf(val weightKg: Double, val reps: Int)

    /**
     * ¿La serie es un récord personal frente al historial previo del ejercicio?
     * Con carga: mayor 1RM estimado. Sin carga: más repeticiones.
     */
    fun isRecord(set: SetPerf, history: List<SetPerf>): Boolean {
        if (set.reps <= 0) return false
        if (history.isEmpty()) return false // la primera vez no cuenta como récord
        return if (set.weightKg > 0.0) {
            val best = history.filter { it.weightKg > 0 }.maxOfOrNull { estimatedOneRepMax(it.weightKg, it.reps) } ?: 0.0
            estimatedOneRepMax(set.weightKg, set.reps) > best + 0.01
        } else {
            val best = history.filter { it.weightKg == 0.0 }.maxOfOrNull { it.reps } ?: 0
            set.reps > best
        }
    }

    fun volume(sets: List<SetPerf>): Double = sets.sumOf { it.weightKg * it.reps }

    /** Racha: días consecutivos (terminando hoy o ayer) con entrenamiento, ignorando domingos. */
    fun streak(trainedDays: Set<LocalDate>, today: LocalDate): Int {
        var day = today
        if (day !in trainedDays) day = day.minusDays(1)
        var count = 0
        while (true) {
            if (day.dayOfWeek == java.time.DayOfWeek.SUNDAY && day !in trainedDays) {
                day = day.minusDays(1)
                continue
            }
            if (day !in trainedDays) break
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun compliancePercent(completed: Int, expected: Int): Int =
        if (expected <= 0) 0 else ((completed.toDouble() / expected) * 100).roundToInt().coerceIn(0, 100)

    fun daysSince(last: LocalDate?, today: LocalDate): Long? =
        last?.let { java.time.temporal.ChronoUnit.DAYS.between(it, today) }
}
