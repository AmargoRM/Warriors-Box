package com.warriorsbox.core

import com.warriorsbox.core.engine.ExternalSources
import com.warriorsbox.core.engine.Pin
import com.warriorsbox.core.engine.Reminders
import com.warriorsbox.core.engine.Stats
import com.warriorsbox.core.model.Equipment
import com.warriorsbox.core.model.MovementPattern
import com.warriorsbox.core.model.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class SupportTest {

    @Test
    fun reminderNextTrigger() {
        val mask = Reminders.maskOf(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), Reminders.daysOf(mask))
        // 2026-09-30 es miércoles.
        val wed8 = LocalDateTime.of(2026, 9, 30, 8, 0)
        assertEquals(LocalDateTime.of(2026, 9, 30, 18, 30), Reminders.nextTrigger(wed8, mask, 18, 30))
        val wed19 = LocalDateTime.of(2026, 9, 30, 19, 0)
        assertEquals(LocalDateTime.of(2026, 10, 5, 18, 30), Reminders.nextTrigger(wed19, mask, 18, 30))
        assertNull(Reminders.nextTrigger(wed8, 0, 18, 30))
        assertEquals(2, Reminders.planDayIndex(LocalDate.of(2026, 9, 30)))
        assertNull(Reminders.planDayIndex(LocalDate.of(2026, 10, 4)))
    }

    @Test
    fun pinHashing() {
        assertTrue(Pin.isValidFormat("0420"))
        assertFalse(Pin.isValidFormat("12a4"))
        val stored = Pin.encode("1234")
        assertTrue(Pin.verify("1234", stored))
        assertFalse(Pin.verify("4321", stored))
        assertFalse(Pin.verify("1234", null))
        assertFalse(stored.contains("1234$"))
    }

    @Test
    fun recordsStreakAndCompliance() {
        val history = listOf(Stats.SetPerf(100.0, 5), Stats.SetPerf(90.0, 8))
        assertTrue(Stats.isRecord(Stats.SetPerf(100.0, 6), history))
        assertFalse(Stats.isRecord(Stats.SetPerf(95.0, 5), history))
        assertFalse(Stats.isRecord(Stats.SetPerf(50.0, 5), emptyList()))
        assertTrue(Stats.isRecord(Stats.SetPerf(0.0, 12), listOf(Stats.SetPerf(0.0, 10))))
        assertEquals(1500.0, Stats.volume(listOf(Stats.SetPerf(100.0, 10), Stats.SetPerf(50.0, 10))), 0.0)

        val today = LocalDate.of(2026, 9, 30) // miércoles
        val days = setOf(today, today.minusDays(1), today.minusDays(2), LocalDate.of(2026, 9, 26)) // mié, mar, lun, sáb
        assertEquals(4, Stats.streak(days, today)) // el domingo no rompe la racha
        assertEquals(0, Stats.streak(emptySet(), today))
        assertEquals(50, Stats.compliancePercent(3, 6))
        assertEquals(3L, Stats.daysSince(today.minusDays(3), today))
    }

    @Test
    fun freeExerciseDbMapping() {
        val file = File("../tools/catalogo/.cache/exercises.json")
        val sample = """[{"name":"Barbell Squat","force":"push","level":"beginner","mechanic":"compound","equipment":"barbell",
            "primaryMuscles":["quadriceps"],"secondaryMuscles":["glutes"],"instructions":["a","b"],"category":"strength",
            "images":["Barbell_Squat/0.jpg"],"id":"Barbell_Squat"},
            {"name":"Pullups","force":"pull","level":"beginner","mechanic":"compound","equipment":"body only",
            "primaryMuscles":["lats"],"secondaryMuscles":[],"instructions":[],"category":"strength","images":[],"id":"Pullups"}]"""
        val list = ExternalSources.parseFreeExerciseDb(if (file.exists()) file.readText() else sample)
        val squat = list.first { it.id == "fedb-barbell_squat" }
        assertEquals(MovementPattern.SQUAT, squat.pattern)
        assertEquals(setOf(Equipment.BARBELL), squat.equipment)
        assertTrue(squat.imageUrls.first().endsWith("Barbell_Squat/0.jpg"))
        val pull = list.first { it.id == "fedb-pullups" }
        assertEquals(MovementPattern.VERTICAL_PULL, pull.pattern)
        assertTrue(Equipment.PULLUP_BAR in pull.equipment)
    }

    @Test
    fun wgerMapping() {
        val page = """{"count":2,"next":"https://wger.de/api/v2/exerciseinfo/?limit=100&offset=100","results":[
          {"id":73,"category":{"id":11,"name":"Chest"},"muscles":[{"id":4,"name":"Pectoralis major","name_en":"Chest"}],
           "muscles_secondary":[{"id":5,"name":"Triceps brachii","name_en":"Triceps"}],
           "equipment":[{"id":1,"name":"Barbell"},{"id":8,"name":"Bench"}],
           "images":[{"image":"https://wger.de/media/a.png","is_main":true}],
           "translations":[{"name":"Bench Press","description":"<p>Lie down.</p><p>Press up.</p>","language":2},
                           {"name":"Press de banca","description":"<p>Acuéstate.</p><ol><li>Empuja.</li></ol>","language":4}]},
          {"id":91,"category":{"id":9,"name":"Legs"},"muscles":[],"muscles_secondary":[],"equipment":[{"id":7,"name":"none (bodyweight exercise)"}],
           "images":[],"exercises":[{"name":"Walking Lunges","description":"","language":2}]},
          {"id":5,"category":{"id":10,"name":"Abs"},"muscles":[],"equipment":[],"translations":[{"name":"Solo alemán","language":1}]}
        ]}"""
        val result = ExternalSources.parseWgerPage(page)
        assertTrue(result.hasNext)
        assertEquals(2, result.exercises.size)
        val bench = result.exercises[0]
        assertEquals("wger-73", bench.id)
        assertEquals("Press de banca", bench.name)
        assertEquals("Bench Press", bench.nameEn)
        assertEquals(MovementPattern.HORIZONTAL_PUSH, bench.pattern)
        assertEquals(listOf(Muscle.CHEST), bench.primaryMuscles)
        assertEquals(setOf(Equipment.BARBELL, Equipment.BENCH), bench.equipment)
        assertEquals(listOf("Acuéstate.", "Empuja."), bench.steps)
        val lunge = result.exercises[1]
        assertEquals(MovementPattern.LUNGE, lunge.pattern)
        assertTrue(lunge.isBodyweight)
    }
}

class RoastsTest {
    @Test
    fun roastsFillPlaceholders() {
        val r = com.warriorsbox.core.engine.Roasts
        repeat(50) {
            val text = r.reminder("Pierna", kotlin.random.Random(it))
            org.junit.Assert.assertFalse(text.contains("{"))
            org.junit.Assert.assertFalse(r.missed(4, kotlin.random.Random(it)).contains("{"))
        }
        org.junit.Assert.assertTrue(r.reminders.any { it.contains("carepicha") })
        org.junit.Assert.assertEquals("3 días", r.fill("{n} días", days = 3))
    }
}
