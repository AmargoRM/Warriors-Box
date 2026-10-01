package com.warriorsbox.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.warriorsbox.core.model.BodyZone
import com.warriorsbox.core.model.Equipment
import com.warriorsbox.core.model.Goal
import com.warriorsbox.core.model.Level
import com.warriorsbox.core.model.Sex
import com.warriorsbox.core.model.TrainingLocation
import kotlinx.serialization.Serializable

/** Condiciones de salud que se preguntan en el perfil. */
enum class HealthCondition(val label: String) {
    HYPERTENSION("Hipertensión"),
    DIABETES("Diabetes"),
    ASTHMA("Asma"),
    CARDIAC("Problemas cardíacos"),
    PREGNANCY("Embarazo"),
}

@Serializable
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val photoPath: String? = null,
    /** Fecha ISO yyyy-MM-dd. */
    val birthDate: String? = null,
    val sex: String = Sex.UNSPECIFIED.name,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val waistCm: Double? = null,
    val level: String = Level.BEGINNER.name,
    val daysPerWeek: Int = 3,
    val minutesPerSession: Int = 60,
    val location: String = TrainingLocation.GYM.name,
    /** Nombres de [Equipment] separados por coma. */
    val equipment: String = "",
    val maxDumbbellKg: Double? = null,
    val goal: String = Goal.HEALTH.name,
    /** Nombres de [HealthCondition] separados por coma. */
    val conditions: String = "",
    val surgeries: String = "",
    val medications: String = "",
    val doctorRestriction: Boolean = false,
    val sleepHours: Double? = null,
    val stressLevel: Int? = null,
    val dailyActivity: String? = null,
    val emergencyContact: String? = null,
    val backgroundPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val sexEnum: Sex get() = runCatching { Sex.valueOf(sex) }.getOrDefault(Sex.UNSPECIFIED)
    val levelEnum: Level get() = runCatching { Level.valueOf(level) }.getOrDefault(Level.BEGINNER)
    val goalEnum: Goal get() = runCatching { Goal.valueOf(goal) }.getOrDefault(Goal.HEALTH)
    val locationEnum: TrainingLocation get() = runCatching { TrainingLocation.valueOf(location) }.getOrDefault(TrainingLocation.GYM)
    val equipmentSet: Set<Equipment>
        get() = equipment.split(',').mapNotNull { runCatching { Equipment.valueOf(it.trim()) }.getOrNull() }.toSet()
    val conditionSet: Set<HealthCondition>
        get() = conditions.split(',').mapNotNull { runCatching { HealthCondition.valueOf(it.trim()) }.getOrNull() }.toSet()
    val medicalRestriction: Boolean get() = doctorRestriction || HealthCondition.CARDIAC in conditionSet
}

@Serializable
@Entity(
    tableName = "injuries",
    foreignKeys = [ForeignKey(UserEntity::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("userId")],
)
data class InjuryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val zone: String,
    val description: String = "",
    val approxDate: String? = null,
    /** true = sigue doliendo; el motor evita ejercicios que la afecten. */
    val active: Boolean = true,
) {
    val zoneEnum: BodyZone get() = runCatching { BodyZone.valueOf(zone) }.getOrDefault(BodyZone.OTHER)
}

@Serializable
@Entity(
    tableName = "measurements",
    foreignKeys = [ForeignKey(UserEntity::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("userId")],
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val epochDay: Long,
    val weightKg: Double? = null,
    val waistCm: Double? = null,
    val bodyFatPct: Double? = null,
)

@Serializable
@Entity(
    tableName = "plans",
    foreignKeys = [ForeignKey(UserEntity::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("userId")],
)
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val cycle: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val active: Boolean = true,
    val summary: String = "",
    /** Avisos separados por salto de línea. */
    val warnings: String = "",
)

@Serializable
@Entity(
    tableName = "plan_days",
    foreignKeys = [ForeignKey(PlanEntity::class, ["id"], ["planId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("planId"), Index(value = ["planId", "week", "dayIndex"], unique = true)],
)
data class PlanDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val week: Int,
    val dayIndex: Int,
    val focus: String,
    val optional: Boolean = false,
    val deload: Boolean = false,
)

@Serializable
@Entity(
    tableName = "plan_items",
    foreignKeys = [ForeignKey(PlanDayEntity::class, ["id"], ["dayId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dayId")],
)
data class PlanItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val position: Int,
    val exerciseId: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val targetReps: Int,
    val weightKg: Double,
    val restSec: Int,
    val reason: String = "",
    val trainerNote: String? = null,
    /** Si se reemplazó con "No puedo hacerlo", el ejercicio original. */
    val originalExerciseId: String? = null,
)

@Serializable
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(UserEntity::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(PlanDayEntity::class, ["id"], ["dayId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("userId"), Index("dayId")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val dayId: Long?,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val notes: String? = null,
    val kcal: Int? = null,
    val completed: Boolean = false,
    /** Resumen para mostrar aunque el plan se borre. */
    val title: String = "",
)

@Serializable
@Entity(
    tableName = "set_logs",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val itemId: Long?,
    val exerciseId: String,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val rir: Int? = null,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val isRecord: Boolean = false,
)

@Serializable
@Entity(
    tableName = "exercise_notes",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class ExerciseNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val itemId: Long?,
    val exerciseId: String,
    val note: String,
)

@Serializable
@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(UserEntity::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["userId"], unique = true)],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    /** Bit 0 = lunes … bit 6 = domingo. */
    val daysMask: Int,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    /** Avisar si pasaron 2 días programados sin entrenar. */
    val missedAlert: Boolean = true,
)

/** Ejercicios propios o sincronizados de internet (se guardan como JSON del modelo del núcleo). */
@Serializable
@Entity(tableName = "exercise_extras")
data class ExerciseExtraEntity(
    @PrimaryKey val id: String,
    val json: String,
    val source: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
