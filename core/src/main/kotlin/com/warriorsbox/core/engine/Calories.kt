package com.warriorsbox.core.engine

import kotlin.math.roundToInt

/**
 * Estimación de calorías con valores MET del Compendium of Physical Activities (2011/2024).
 * kcal = MET × peso (kg) × horas. Es una estimación, no una medición.
 */
object Calories {

    const val MET_STRENGTH_MODERATE = 3.5
    const val MET_STRENGTH_VIGOROUS = 6.0

    private val cardioMet = mapOf(
        "bici-estatica" to 5.5,
        "caminadora" to 4.3,
        "caminata" to 4.3,
        "trote" to 7.0,
        "remo-ergometro" to 7.0,
        "eliptica" to 5.0,
        "saltar-cuerda" to 8.8,
        "jumping-jacks" to 8.0,
        "escaladora" to 9.0,
        "escaladores" to 8.0,
    )

    fun metForCardio(exerciseId: String): Double = cardioMet[exerciseId] ?: 6.0

    /** MET de pesas según el esfuerzo promedio (repeticiones en reserva). */
    fun strengthMet(averageRir: Double?): Double = when {
        averageRir == null -> MET_STRENGTH_MODERATE
        averageRir <= 1.0 -> MET_STRENGTH_VIGOROUS
        averageRir <= 2.0 -> 5.0
        else -> MET_STRENGTH_MODERATE
    }

    fun kcal(met: Double, bodyWeightKg: Double, minutes: Double): Int =
        (met * bodyWeightKg * (minutes / 60.0)).roundToInt().coerceAtLeast(0)

    /**
     * Calorías de una sesión: minutos de pesas con MET según esfuerzo + cada bloque de cardio con su MET.
     * [cardioBlocks] = pares (id del ejercicio, minutos).
     */
    fun session(
        bodyWeightKg: Double,
        totalMinutes: Double,
        averageRir: Double?,
        cardioBlocks: List<Pair<String, Double>> = emptyList(),
    ): Int {
        val cardioMinutes = cardioBlocks.sumOf { it.second }.coerceAtMost(totalMinutes)
        val strengthMinutes = (totalMinutes - cardioMinutes).coerceAtLeast(0.0)
        val cardio = cardioBlocks.sumOf { (id, min) -> kcal(metForCardio(id), bodyWeightKg, min) }
        return kcal(strengthMet(averageRir), bodyWeightKg, strengthMinutes) + cardio
    }
}
