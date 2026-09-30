package com.warriorsbox.core.engine

import com.warriorsbox.core.model.BodyZone
import com.warriorsbox.core.model.Equipment
import com.warriorsbox.core.model.Exercise
import com.warriorsbox.core.model.SkipReason
import com.warriorsbox.core.model.TrainingProfile

/** Botón "No puedo hacerlo": alternativas que trabajan el mismo músculo y patrón. */
object Alternatives {

    data class Suggestion(val exercise: Exercise, val why: String)

    fun find(
        original: Exercise,
        reason: SkipReason,
        profile: TrainingProfile,
        catalog: List<Exercise>,
        painZone: BodyZone? = null,
        limit: Int = 5,
    ): List<Suggestion> {
        val injuries = buildSet {
            addAll(profile.activeInjuries)
            if (reason == SkipReason.PAIN) {
                addAll(original.contraindications)
                if (painZone != null) add(painZone)
            }
        }
        val originalMachines = original.equipment - setOf(Equipment.NONE, Equipment.BENCH)

        fun eligible(e: Exercise): Boolean {
            if (e.id == original.id) return false
            if (!e.isAvailableWith(profile.equipment)) return false
            if (!e.isSafeFor(injuries)) return false
            return when (reason) {
                SkipReason.NO_EQUIPMENT -> originalMachines.isEmpty() || e.equipment.none { it in originalMachines }
                SkipReason.BUSY -> e.equipment != original.equipment
                SkipReason.PAIN -> e.level.rank <= original.level.rank
                SkipReason.DONT_KNOW -> e.level.rank <= original.level.rank && e.curated
                SkipReason.TOO_HARD -> e.level.rank < original.level.rank ||
                    (e.level.rank == original.level.rank && e.isBodyweight.not() && original.isBodyweight) ||
                    (original.level.rank == 0 && e.level.rank == 0 && (e.isBodyweight || Equipment.MACHINE in e.equipment))
            }
        }

        fun score(e: Exercise): Int {
            var s = 0
            val sharedPrimary = e.primaryMuscles.count { it in original.primaryMuscles }
            s += sharedPrimary * 30
            s += e.secondaryMuscles.count { it in original.primaryMuscles + original.secondaryMuscles } * 3
            if (e.pattern == original.pattern) s += 40
            if (e.compound == original.compound) s += 10
            if (e.curated) s += 25
            if (e.unilateral == original.unilateral) s += 2
            when (reason) {
                SkipReason.PAIN -> {
                    if (Equipment.MACHINE in e.equipment || Equipment.CABLE in e.equipment) s += 8
                    s += (original.level.rank - e.level.rank) * 4
                }
                SkipReason.TOO_HARD, SkipReason.DONT_KNOW -> s += (original.level.rank - e.level.rank) * 6
                else -> Unit
            }
            return s
        }

        val sameMuscle = catalog.filter { e -> eligible(e) && e.primaryMuscles.any { it in original.primaryMuscles } }
        val ranked = sameMuscle.sortedWith(compareByDescending<Exercise> { score(it) }.thenBy { it.rank }.thenBy { it.id })
        return ranked.take(limit).map { Suggestion(it, explain(it, original, reason)) }
    }

    private fun explain(e: Exercise, original: Exercise, reason: SkipReason): String {
        val parts = mutableListOf<String>()
        val shared = e.primaryMuscles.filter { it in original.primaryMuscles }
        if (shared.isNotEmpty()) parts += "trabaja ${shared.joinToString { it.label.lowercase() }}"
        if (e.pattern == original.pattern) parts += "mismo movimiento (${e.pattern.label.lowercase()})"
        parts += when (reason) {
            SkipReason.NO_EQUIPMENT, SkipReason.BUSY ->
                if (e.isBodyweight) "sin equipo" else "con ${e.equipment.joinToString { it.label.lowercase() }}"
            SkipReason.PAIN -> "menor carga para la zona que molesta"
            SkipReason.DONT_KNOW -> "técnica más sencilla, con instrucciones"
            SkipReason.TOO_HARD -> "versión más fácil (${e.level.label.lowercase()})"
        }
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
    }
}
