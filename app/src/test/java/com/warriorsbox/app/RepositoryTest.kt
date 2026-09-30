package com.warriorsbox.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.warriorsbox.app.data.db.AppDatabase
import com.warriorsbox.app.data.db.InjuryEntity
import com.warriorsbox.app.data.db.UserEntity
import com.warriorsbox.core.engine.PlanGenerator
import com.warriorsbox.core.model.BodyZone
import com.warriorsbox.core.model.Goal
import com.warriorsbox.core.model.Level
import com.warriorsbox.core.model.TrainingLocation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class RepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var c: AppContainer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<WarriorsApp>()
        db = AppDatabase.inMemory(context)
        c = AppContainer(context, db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun newUser(name: String = "Ana", injuries: List<InjuryEntity> = emptyList()): Long =
        c.users.save(
            UserEntity(
                name = name, weightKg = 70.0, heightCm = 170.0, level = Level.INTERMEDIATE.name, daysPerWeek = 4,
                minutesPerSession = 60, location = TrainingLocation.GYM.name, goal = Goal.MUSCLE.name,
            ),
            injuries,
        )

    @Test
    fun catalogLoadsFromAssets() = runBlocking {
        val all = c.exercises.allNow()
        assertTrue(all.size > 800)
        assertNotNull(all.firstOrNull { it.id == "sentadilla-goblet" })
    }

    @Test
    fun generatePlanCreatesFiveWeeksOfSixDays() = runBlocking {
        val userId = newUser()
        val (planId, generated) = c.plans.generate(userId)
        val days = c.plans.daysNow(planId)
        assertEquals(PlanGenerator.WEEKS * PlanGenerator.DAYS, days.size)
        assertTrue(days.all { c.plans.itemsNow(it.id).isNotEmpty() })
        assertTrue(generated.summary.isNotBlank())
        assertEquals(1, c.plans.currentWeek(days, emptyList()))
    }

    @Test
    fun injuriesAreRespectedWhenGenerating() = runBlocking {
        val userId = newUser(injuries = listOf(InjuryEntity(userId = 0, zone = BodyZone.KNEE.name, active = true)))
        val (planId, _) = c.plans.generate(userId)
        val catalog = c.exercises.byId()
        c.plans.daysNow(planId).flatMap { c.plans.itemsNow(it.id) }.forEach {
            assertTrue(it.exerciseId, BodyZone.KNEE !in catalog.getValue(it.exerciseId).contraindications)
        }
    }

    @Test
    fun sessionFlowAppliesProgressionAndCalories() = runBlocking {
        val userId = newUser()
        val (planId, _) = c.plans.generate(userId)
        val day = c.plans.daysNow(planId).first { it.week == 1 && it.dayIndex == 0 }
        val sessionId = c.sessions.startSession(userId, day.id)
        // Retomar devuelve la misma sesión.
        assertEquals(sessionId, c.sessions.startSession(userId, day.id))
        val items = c.plans.itemsNow(day.id)
        val sets = db.sessions().sets(sessionId)
        assertEquals(items.sumOf { it.sets }, sets.size)
        // Todas las series al máximo de repeticiones con reserva → debe subir el peso la semana 2.
        sets.forEach { s ->
            val item = items.first { it.id == s.itemId }
            c.sessions.toggleDone(s.copy(reps = item.repsMax, rir = 3), true, userId)
        }
        val summary = c.sessions.finish(sessionId)
        assertTrue(summary.kcal > 0)
        assertEquals(items.size, summary.lines.size)
        val next = c.plans.itemsNow(c.plans.daysNow(planId).first { it.week == 2 && it.dayIndex == 0 }.id)
        val loaded = items.filter { it.weightKg > 0 }
        assertTrue(loaded.isNotEmpty())
        loaded.forEach { original ->
            val n = next.first { it.position == original.position }
            assertTrue("${original.exerciseId}: ${n.weightKg} > ${original.weightKg}", n.weightKg > original.weightKg)
        }
        val completed = c.sessions.sessionsNow(userId).filter { it.completed }
        assertEquals(1, completed.size)
    }

    @Test
    fun recordsAreDetectedAgainstHistory() = runBlocking {
        val userId = newUser()
        val (planId, _) = c.plans.generate(userId)
        val days = c.plans.daysNow(planId).filter { it.dayIndex == 0 }
        val s1 = c.sessions.startSession(userId, days[0].id)
        val first = db.sessions().sets(s1).first { it.weightKg > 0 }
        assertTrue(!c.sessions.toggleDone(first, true, userId)) // primera vez: no es récord
        c.sessions.finish(s1)
        val s2 = c.sessions.startSession(userId, days[1].id)
        val again = db.sessions().sets(s2).first { it.exerciseId == first.exerciseId }
        assertTrue(c.sessions.toggleDone(again.copy(weightKg = first.weightKg + 20), true, userId))
    }

    @Test
    fun substituteForWholePlanChangesLaterWeeks() = runBlocking {
        val userId = newUser()
        val (planId, _) = c.plans.generate(userId)
        val day1 = c.plans.daysNow(planId).first { it.week == 1 && it.dayIndex == 1 }
        val item = c.plans.itemsNow(day1.id).first()
        val replacement = c.exercises.get("prensa-piernas") ?: c.exercises.allNow().first { it.id != item.exerciseId && it.curated }
        c.plans.substitute(item, replacement, wholePlan = true, sessionId = null, userId = userId)
        c.plans.daysNow(planId).filter { it.dayIndex == 1 }.forEach { d ->
            val items = c.plans.itemsNow(d.id)
            assertTrue(items.any { it.exerciseId == replacement.id })
            assertTrue(items.none { it.exerciseId == item.exerciseId && it.position == item.position })
        }
    }

    @Test
    fun editorOperations() = runBlocking {
        val userId = newUser()
        val (planId, _) = c.plans.generate(userId)
        val day = c.plans.daysNow(planId).first { it.week == 1 && it.dayIndex == 0 }
        val before = c.plans.itemsNow(day.id)
        c.plans.addItem(day.id, c.exercises.get("plancha")!!, userId)
        assertEquals(before.size + 1, c.plans.itemsNow(day.id).size)
        val last = c.plans.itemsNow(day.id).last()
        c.plans.moveItem(last, -1)
        assertEquals("plancha", c.plans.itemsNow(day.id)[before.size - 1].exerciseId)
        c.plans.copyDayToWeeks(day.id, listOf(2, 5))
        assertEquals(before.size + 1, c.plans.itemsNow(c.plans.daysNow(planId).first { it.week == 2 && it.dayIndex == 0 }.id).size)
        val deloadItems = c.plans.itemsNow(c.plans.daysNow(planId).first { it.week == 5 && it.dayIndex == 0 }.id)
        assertEquals(before.size + 1, deloadItems.size)
        c.plans.removeItem(c.plans.itemsNow(day.id).first())
        assertEquals(before.size, c.plans.itemsNow(day.id).size)
        assertEquals((0 until before.size).toList(), c.plans.itemsNow(day.id).map { it.position })
    }

    @Test
    fun backupRoundTripRestoresEverything() = runBlocking {
        val userId = newUser("Carlos", listOf(InjuryEntity(userId = 0, zone = BodyZone.SHOULDER.name)))
        val (planId, _) = c.plans.generate(userId)
        val day = c.plans.daysNow(planId).first()
        val sessionId = c.sessions.startSession(userId, day.id)
        db.sessions().sets(sessionId).take(2).forEach { c.sessions.toggleDone(it, true, userId) }
        c.sessions.finish(sessionId)
        val before = c.backup.snapshot()

        val out = ByteArrayOutputStream()
        c.backup.export(out)
        c.users.delete(c.users.get(userId)!!)
        assertEquals(0, c.users.count())

        c.backup.import(ByteArrayInputStream(out.toByteArray()))
        val after = c.backup.snapshot()
        assertEquals(before.users.map { it.name }, after.users.map { it.name })
        assertEquals(before.items.size, after.items.size)
        assertEquals(before.sets.size, after.sets.size)
        assertEquals(before.sessions.size, after.sessions.size)
        assertEquals(before.injuries.size, after.injuries.size)
    }

    @Test
    fun deletingUserCascades() = runBlocking {
        val userId = newUser()
        val (planId, _) = c.plans.generate(userId)
        c.sessions.startSession(userId, c.plans.daysNow(planId).first().id)
        c.users.delete(c.users.get(userId)!!)
        assertNull(c.plans.plan(planId))
        assertTrue(c.backup.snapshot().sets.isEmpty())
    }

    @Test
    fun planExportImportBetweenUsers() = runBlocking {
        val coach = newUser("Coach")
        c.plans.generate(coach)
        val text = c.plans.exportPlan(coach)!!
        val student = newUser("Alumno")
        val unknown = c.plans.importPlan(student, text)
        assertEquals(0, unknown)
        val a = db.plans().itemsOfPlan(c.plans.activePlanNow(coach)!!.id)
        val b = db.plans().itemsOfPlan(c.plans.activePlanNow(student)!!.id)
        assertEquals(a.map { it.exerciseId }, b.map { it.exerciseId })
    }

    @Test
    fun trainerPanelComputesAlerts() = runBlocking {
        newUser("Sin sesiones")
        val students = c.stats.students()
        assertEquals(1, students.size)
        assertTrue(students[0].alerts.any { it.contains("no registra") })
    }
}
