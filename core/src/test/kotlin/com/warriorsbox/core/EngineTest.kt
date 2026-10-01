package com.warriorsbox.core

import com.warriorsbox.core.engine.Alternatives
import com.warriorsbox.core.engine.BodyMetrics
import com.warriorsbox.core.engine.Calories
import com.warriorsbox.core.engine.Motivation
import com.warriorsbox.core.engine.Progression
import com.warriorsbox.core.engine.Units
import com.warriorsbox.core.engine.UpdateInfo
import com.warriorsbox.core.engine.Updates
import com.warriorsbox.core.engine.WeightMath
import com.warriorsbox.core.model.BodyZone
import com.warriorsbox.core.model.Equipment
import com.warriorsbox.core.model.Level
import com.warriorsbox.core.model.LoggedSet
import com.warriorsbox.core.model.PlannedExercise
import com.warriorsbox.core.model.SkipReason
import com.warriorsbox.core.model.TrainingLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class EngineTest {

    private val catalog = TestCatalog.exercises
    private val squat = TestCatalog.byId("sentadilla-barra")
    private val bench = TestCatalog.byId("press-banca")
    private val plank = TestCatalog.byId("plancha")

    private fun item(weight: Double, sets: Int = 3, min: Int = 6, max: Int = 10, target: Int = 6) =
        PlannedExercise("x", sets, min, max, target, weight, 90)

    // ---------- Progresión ----------

    @Test
    fun progressionIncreasesWeightWhenTopRepsWithReserve() {
        val logs = List(3) { LoggedSet(100.0, 10, true, rir = 2) }
        val r = Progression.next(item(100.0), logs, squat)
        assertEquals(Progression.Outcome.INCREASE_WEIGHT, r.outcome)
        assertEquals(105.0, r.next.weightKg, 0.0) // tren inferior +5 %
        assertEquals(6, r.next.targetReps)
    }

    @Test
    fun upperBodyIncreaseIsSmallerAndRoundedToPlates() {
        val logs = List(3) { LoggedSet(60.0, 10, true) }
        val r = Progression.next(item(60.0), logs, bench)
        assertEquals(62.5, r.next.weightKg, 0.0) // max(61.5, 62.5) redondeado a 2.5
    }

    @Test
    fun progressionAddsRepWhenTargetMetButNotTop() {
        val logs = List(3) { LoggedSet(100.0, 7, true, rir = 2) }
        val r = Progression.next(item(100.0, target = 7), logs, squat)
        assertEquals(Progression.Outcome.INCREASE_REPS, r.outcome)
        assertEquals(8, r.next.targetReps)
        assertEquals(100.0, r.next.weightKg, 0.0)
    }

    @Test
    fun progressionRepeatsWhenSetsFailedOrNoReserve() {
        val failed = listOf(LoggedSet(100.0, 6, true), LoggedSet(100.0, 4, true), LoggedSet(100.0, 3, true))
        assertEquals(Progression.Outcome.REPEAT, Progression.next(item(100.0), failed, squat).outcome)
        val noReserve = List(3) { LoggedSet(100.0, 10, true, rir = 0) }
        assertEquals(Progression.Outcome.INCREASE_REPS, Progression.next(item(100.0), noReserve, squat).outcome)
        assertEquals(Progression.Outcome.NO_DATA, Progression.next(item(100.0), emptyList(), squat).outcome)
    }

    @Test
    fun bodyweightAndTimedProgressWithRepsOrSeconds() {
        val logs = List(3) { LoggedSet(0.0, 30, true) }
        val r = Progression.next(item(0.0, min = 20, max = 30, target = 30), logs, plank)
        assertEquals(35, r.next.repsMax)
    }

    @Test
    fun deloadFromUsesActualWeight() {
        val d = Progression.deloadFrom(item(100.0, sets = 4), listOf(LoggedSet(110.0, 8, true)))
        assertEquals(3, d.sets)
        assertEquals(100.0, d.weightKg, 0.0) // 110 × 0.9 = 99 → 100 (redondeo a 2.5)
    }

    // ---------- Alternativas ----------

    @Test
    fun alternativesTrainSameMuscleAndRespectReason() {
        val profile = TestCatalog.profile()
        val noBarbell = Alternatives.find(squat, SkipReason.NO_EQUIPMENT, profile, catalog)
        assertTrue(noBarbell.size in 3..5)
        noBarbell.forEach {
            assertTrue(it.exercise.primaryMuscles.any { m -> m in squat.primaryMuscles })
            assertFalse(Equipment.BARBELL in it.exercise.equipment)
            assertTrue(it.why.isNotBlank())
        }
        val pain = Alternatives.find(bench, SkipReason.PAIN, profile, catalog, painZone = BodyZone.SHOULDER)
        assertTrue(pain.isNotEmpty())
        pain.forEach { assertTrue(it.exercise.isSafeFor(setOf(BodyZone.SHOULDER, BodyZone.WRIST))) }

        val hard = Alternatives.find(TestCatalog.byId("dominadas"), SkipReason.TOO_HARD, profile, catalog)
        assertTrue(hard.isNotEmpty())
        hard.forEach { assertTrue(it.exercise.level.rank <= Level.INTERMEDIATE.rank) }
        assertTrue(hard.any { it.exercise.id == "dominadas-asistidas" || it.exercise.id == "jalon-pecho" })
    }

    @Test
    fun alternativesAtHomeOnlyUseAvailableEquipment() {
        val home = TestCatalog.profile(location = TrainingLocation.HOME_DUMBBELLS)
        val alts = Alternatives.find(TestCatalog.byId("remo-una-mano"), SkipReason.BUSY, home, catalog)
        assertTrue(alts.isNotEmpty())
        alts.forEach { assertTrue(it.exercise.isAvailableWith(home.equipment)) }
    }

    // ---------- Calorías ----------

    @Test
    fun caloriesFollowMetFormula() {
        assertEquals(263, Calories.kcal(3.5, 75.0, 60.0))
        val session = Calories.session(80.0, 60.0, averageRir = 3.0, cardioBlocks = listOf("bici-estatica" to 10.0))
        // 50 min pesas (3.5) + 10 min bici (5.5)
        assertEquals(233 + 73, session)
        assertTrue(Calories.session(80.0, 60.0, 0.5) > Calories.session(80.0, 60.0, 3.0))
    }

    // ---------- Frases ----------

    @Test
    fun motivationPicksContextAndAvoidsRepeat() {
        assertEquals(Motivation.Context.LEGS, Motivation.contextFor(squat))
        assertEquals(Motivation.Context.PUSH, Motivation.contextFor(bench))
        assertEquals(Motivation.Context.CORE, Motivation.contextFor(plank))
        assertEquals(Motivation.Context.RECORD, Motivation.contextFor(squat, isRecord = true))
        assertEquals(Motivation.Context.ARMS, Motivation.contextFor(TestCatalog.byId("curl-martillo")))
        val first = Motivation.pick(Motivation.Context.RECORD, Random(1))
        repeat(20) { assertNotEquals(first, Motivation.pick(Motivation.Context.RECORD, Random(it), avoid = first)) }
        assertTrue(Motivation.phrases.values.sumOf { it.size } >= 50)
    }

    // ---------- Actualizaciones ----------

    @Test
    fun versionCodesAndNewerCheck() {
        assertEquals(10203, Updates.versionCodeOf("v1.2.3"))
        assertEquals(10000, Updates.versionCodeOf("1.0"))
        val info = Updates.parse(
            """{"versionCode":10001,"versionName":"1.0.1","apkUrl":"https://x/a.apk","sha256":"ab","notas":"Nuevo","extra":1}""",
        )
        assertEquals("Nuevo", info.notas)
        assertTrue(Updates.isNewer(info, 10000))
        assertFalse(Updates.isNewer(info, 10001))
        assertFalse(Updates.isNewer(info, 10000, skippedVersionCode = 10001))
        assertTrue(Updates.isNewer(info.copy(obligatoria = true), 10000, skippedVersionCode = 10001))
        assertTrue(Updates.isNewer(UpdateInfo(20000, "2.0.0", "u", "s"), 10999))
    }

    // ---------- Medidas y unidades ----------

    @Test
    fun bodyMetricsAndUnits() {
        assertEquals(30, BodyMetrics.age(LocalDate.of(1995, 10, 1), LocalDate.of(2026, 9, 30)))
        assertEquals(31, BodyMetrics.age(LocalDate.of(1995, 9, 30), LocalDate.of(2026, 9, 30)))
        assertEquals(24.2, BodyMetrics.bmi(70.0, 170.0)!!, 0.05)
        assertEquals("Normal", BodyMetrics.bmiCategory(22.0))
        assertEquals(22.05, Units.kgToLb(10.0), 0.01)
        assertEquals(4.536, Units.lbToKg(10.0), 0.001)
        assertEquals("5' 7\"", Units.cmToFeetText(170.0))
        assertEquals("10 min", Units.formatDuration(600))
        assertEquals("45 s", Units.formatDuration(45))
        assertEquals("22 lb", Units.formatWeight(10.0, useLb = true))
    }

    @Test
    fun initialWeightsAreConservativeAndRounded() {
        val beginner = TestCatalog.profile(level = Level.BEGINNER, weight = 80.0)
        val w = WeightMath.initialWeight(squat, beginner)
        assertTrue(w in 20.0..40.0)
        assertEquals(0.0, w % 2.5, 0.0)
        val capped = beginner.copy(maxDumbbellKg = 4.5)
        assertEquals(4.5, WeightMath.initialWeight(TestCatalog.byId("press-banca-mancuernas"), capped), 0.0)
        assertEquals(0.0, WeightMath.initialWeight(plank, beginner), 0.0)
    }
}
