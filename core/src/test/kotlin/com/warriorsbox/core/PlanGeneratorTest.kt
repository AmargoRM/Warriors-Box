package com.warriorsbox.core

import com.warriorsbox.core.engine.PlanGenerator
import com.warriorsbox.core.model.BodyZone
import com.warriorsbox.core.model.Equipment
import com.warriorsbox.core.model.Goal
import com.warriorsbox.core.model.Level
import com.warriorsbox.core.model.TrainingLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanGeneratorTest {

    private val catalog = TestCatalog.exercises
    private val byId = catalog.associateBy { it.id }

    @Test
    fun catalogIsLargeAndHasCuratedSpanishExercises() {
        assertTrue(catalog.size > 800)
        assertTrue(catalog.count { it.curated } >= 140)
        assertTrue(catalog.filter { it.curated }.all { it.steps.size == 3 })
        // Los 8 ejercicios de la imagen de referencia.
        listOf(
            "sentadilla-goblet", "remo-una-mano", "press-pecho-suelo", "peso-muerto-rumano-mancuernas",
            "press-hombro-una-mano", "curl-biceps-mancuerna", "puente-gluteos", "plancha-elevada",
        ).forEach { assertTrue(it, it in byId) }
    }

    @Test
    fun everyCombinationProducesFiveWeeksOfSixDays() {
        for (level in Level.entries) for (goal in Goal.entries) for (days in 3..6)
            for (location in TrainingLocation.entries) for (minutes in listOf(30, 45, 60, 90)) {
                val profile = TestCatalog.profile(level, goal, days, minutes, location)
                val plan = PlanGenerator.generate(profile, catalog)
                val label = "$level $goal $days $location $minutes"
                assertEquals(label, 5, plan.weeks.size)
                plan.weeks.forEach { w ->
                    assertEquals(label, 6, w.days.size)
                    assertEquals(label, days, w.days.count { !it.optional })
                    w.days.forEach { d ->
                        assertTrue("$label día ${d.dayIndex} vacío", d.exercises.isNotEmpty())
                        assertEquals("$label repetido en un día", d.exercises.size, d.exercises.map { it.exerciseId }.toSet().size)
                        d.exercises.forEach { item ->
                            val ex = byId.getValue(item.exerciseId)
                            assertTrue("$label ${ex.id} sin equipo", ex.isAvailableWith(profile.equipment))
                            assertTrue(item.sets >= 1)
                            assertTrue(item.repsMin <= item.repsMax)
                            assertTrue(item.weightKg >= 0.0)
                        }
                    }
                }
                assertTrue(plan.weeks.last().deload)
                assertFalse(plan.weeks.first().deload)
            }
    }

    @Test
    fun trainingDaysRespectSessionLength() {
        val short = PlanGenerator.generate(TestCatalog.profile(minutes = 30), catalog)
        val long = PlanGenerator.generate(TestCatalog.profile(minutes = 90), catalog)
        val shortMax = short.weeks[0].days.filter { !it.optional }.maxOf { it.exercises.size }
        val longMax = long.weeks[0].days.filter { !it.optional }.maxOf { it.exercises.size }
        assertTrue(shortMax <= 4)
        assertTrue(longMax >= 6)
    }

    @Test
    fun injuriesExcludeContraindicatedExercises() {
        val zones = setOf(BodyZone.SHOULDER, BodyZone.LOWER_BACK, BodyZone.KNEE)
        val profile = TestCatalog.profile(days = 6).copy(activeInjuries = zones)
        val plan = PlanGenerator.generate(profile, catalog)
        plan.weeks.flatMap { it.days }.flatMap { it.exercises }.forEach {
            val ex = byId.getValue(it.exerciseId)
            assertTrue("${ex.id} contraindicado", ex.contraindications.none { z -> z in zones })
        }
        assertTrue(plan.warnings.any { it.contains("excluyeron") })
    }

    @Test
    fun homeBodyweightOnlyUsesBodyweight() {
        val profile = TestCatalog.profile(location = TrainingLocation.HOME_BODYWEIGHT, days = 5)
        val plan = PlanGenerator.generate(profile, catalog)
        plan.weeks.flatMap { it.days }.flatMap { it.exercises }.forEach {
            assertTrue(it.exerciseId, byId.getValue(it.exerciseId).equipment.all { e -> e == Equipment.NONE })
            assertEquals(0.0, it.weightKg, 0.0)
        }
    }

    @Test
    fun beginnersNeverGetAdvancedExercisesAndPreferCurated() {
        val profile = TestCatalog.profile(level = Level.BEGINNER, days = 6)
        val items = PlanGenerator.generate(profile, catalog).weeks.flatMap { it.days }.flatMap { it.exercises }
        items.forEach { assertEquals(it.exerciseId, Level.BEGINNER, byId.getValue(it.exerciseId).level) }
        items.forEach { assertTrue(it.exerciseId, byId.getValue(it.exerciseId).curated) }
    }

    @Test
    fun deloadWeekHasLessVolumeAndWeight() {
        val plan = PlanGenerator.generate(TestCatalog.profile(), catalog)
        val w1 = plan.weeks[0].days.flatMap { it.exercises }
        val w5 = plan.weeks[4].days.flatMap { it.exercises }
        assertTrue(w5.sumOf { it.sets } < w1.sumOf { it.sets })
        w1.zip(w5).forEach { (a, b) -> assertTrue(b.weightKg <= a.weightKg) }
    }

    @Test
    fun strengthUsesLowRepsAndFatLossAddsCardio() {
        val strength = PlanGenerator.generate(TestCatalog.profile(goal = Goal.STRENGTH), catalog)
        val firstCompound = strength.weeks[0].days[0].exercises.first()
        assertTrue(firstCompound.repsMax <= 6)
        val fat = PlanGenerator.generate(TestCatalog.profile(goal = Goal.FAT_LOSS, minutes = 60), catalog)
        fat.weeks[0].days.filter { !it.optional }.forEach { d ->
            assertTrue(d.exercises.any { byId.getValue(it.exerciseId).pattern.name == "CARDIO" })
        }
    }

    @Test
    fun previousWeightsAreReusedForNewCycle() {
        val plan = PlanGenerator.generate(TestCatalog.profile(), catalog)
        val first = plan.weeks[0].days[0].exercises[0]
        val next = PlanGenerator.generate(TestCatalog.profile(), catalog, previousWeights = mapOf(first.exerciseId to 99.0))
        val same = next.weeks[0].days.flatMap { it.exercises }.firstOrNull { it.exerciseId == first.exerciseId }
        if (same != null) assertEquals(99.0, same.weightKg, 0.0)
    }
}
