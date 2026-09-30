package com.warriorsbox.app.data

import com.warriorsbox.app.data.db.AppDatabase
import com.warriorsbox.app.data.db.SetHistoryRow
import com.warriorsbox.app.data.db.UserEntity
import com.warriorsbox.core.engine.Stats
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class ExerciseProgressPoint(val date: LocalDate, val maxWeight: Double, val volume: Double, val bestReps: Int, val e1rm: Double)

data class WeeklySummary(val sessions: Int, val volumeKg: Double, val streak: Int, val kcal: Int)

data class StudentStatus(
    val user: UserEntity,
    val lastSession: LocalDate?,
    val sessionsThisWeek: Int,
    val compliance: Int,
    val recentRecords: Int,
    val alerts: List<String>,
)

class StatsRepository(private val db: AppDatabase) {
    private val zone = ZoneId.systemDefault()
    private fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    fun progress(rows: List<SetHistoryRow>, exerciseId: String): List<ExerciseProgressPoint> =
        rows.filter { it.exerciseId == exerciseId }.groupBy { it.sessionId }.values.map { sets ->
            ExerciseProgressPoint(
                date = sets.first().startedAt.toDate(),
                maxWeight = sets.maxOf { it.weightKg },
                volume = sets.sumOf { it.weightKg * it.reps },
                bestReps = sets.maxOf { it.reps },
                e1rm = sets.maxOf { Stats.estimatedOneRepMax(it.weightKg, it.reps) },
            )
        }.sortedBy { it.date }

    suspend fun weekly(userId: Long, today: LocalDate = LocalDate.now()): WeeklySummary {
        val sessions = db.sessions().sessions(userId).filter { it.completed }
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val thisWeek = sessions.filter { !it.startedAt.toDate().isBefore(monday) }
        val ids = thisWeek.map { it.id }.toSet()
        val volume = db.sessions().allHistory(userId).filter { it.sessionId in ids }.sumOf { it.weightKg * it.reps }
        return WeeklySummary(
            sessions = thisWeek.size,
            volumeKg = volume,
            streak = Stats.streak(sessions.map { it.startedAt.toDate() }.toSet(), today),
            kcal = thisWeek.sumOf { it.kcal ?: 0 },
        )
    }

    /** Panel del modo entrenador. */
    suspend fun students(today: LocalDate = LocalDate.now()): List<StudentStatus> {
        val users = db.users().all()
        return users.map { user ->
            val sessions = db.sessions().sessions(user.id).filter { it.completed }
            val last = sessions.maxOfOrNull { it.startedAt }?.toDate()
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val plan = db.plans().activePlan(user.id)
            val compliance = if (plan == null) 0 else {
                val days = db.plans().days(plan.id).filter { !it.optional }
                val doneIds = db.sessions().sessionsOfPlan(plan.id).filter { it.completed }.mapNotNull { it.dayId }.toSet()
                // Esperado: días obligatorios de las semanas transcurridas desde que se creó el plan.
                val weeksElapsed = ((today.toEpochDay() - plan.createdAt.toDate().toEpochDay()) / 7 + 1).toInt().coerceIn(1, 5)
                val expected = days.count { it.week <= weeksElapsed }
                Stats.compliancePercent(days.count { it.id in doneIds && it.week <= weeksElapsed }, expected)
            }
            val history = db.sessions().allHistory(user.id)
            val recentRecords = history.count { it.isRecord && !it.startedAt.toDate().isBefore(today.minusDays(14)) }
            val alerts = buildList {
                val since = Stats.daysSince(last, today)
                if (since == null) add("Todavía no registra sesiones")
                else if (since >= 5) add("No entrena hace $since días")
                val recentPain = db.users().injuries(user.id).filter { it.active && it.approxDate != null }
                    .filter { runCatching { !LocalDate.parse(it.approxDate).isBefore(today.minusDays(14)) }.getOrDefault(false) }
                if (recentPain.isNotEmpty()) add("Molestias registradas: " + recentPain.joinToString { it.zoneEnum.label.lowercase() })
                if (user.medicalRestriction) add("Tiene restricción médica o problema cardíaco")
            }
            StudentStatus(
                user = user,
                lastSession = last,
                sessionsThisWeek = sessions.count { !it.startedAt.toDate().isBefore(monday) },
                compliance = compliance,
                recentRecords = recentRecords,
                alerts = alerts,
            )
        }
    }
}
