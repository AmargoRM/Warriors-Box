package com.warriorsbox.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<UserEntity>

    @Query("SELECT * FROM users WHERE id = :id")
    fun observe(id: Long): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun get(id: Long): UserEntity?

    @Insert
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    // Lesiones
    @Query("SELECT * FROM injuries WHERE userId = :userId ORDER BY active DESC, id DESC")
    fun observeInjuries(userId: Long): Flow<List<InjuryEntity>>

    @Query("SELECT * FROM injuries WHERE userId = :userId")
    suspend fun injuries(userId: Long): List<InjuryEntity>

    @Upsert
    suspend fun upsertInjury(injury: InjuryEntity): Long

    @Query("DELETE FROM injuries WHERE userId = :userId")
    suspend fun deleteInjuries(userId: Long)

    @Insert
    suspend fun insertInjuries(list: List<InjuryEntity>)

    // Medidas
    @Query("SELECT * FROM measurements WHERE userId = :userId ORDER BY epochDay")
    fun observeMeasurements(userId: Long): Flow<List<MeasurementEntity>>

    @Insert
    suspend fun insertMeasurement(m: MeasurementEntity): Long

    @Delete
    suspend fun deleteMeasurement(m: MeasurementEntity)

    // Recordatorios
    @Query("SELECT * FROM reminders WHERE userId = :userId")
    fun observeReminder(userId: Long): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminders")
    suspend fun allReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE userId = :userId")
    suspend fun reminder(userId: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReminder(reminder: ReminderEntity): Long
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans WHERE userId = :userId AND active = 1 ORDER BY id DESC LIMIT 1")
    fun observeActivePlan(userId: Long): Flow<PlanEntity?>

    @Query("SELECT * FROM plans WHERE userId = :userId AND active = 1 ORDER BY id DESC LIMIT 1")
    suspend fun activePlan(userId: Long): PlanEntity?

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun plan(id: Long): PlanEntity?

    @Query("SELECT MAX(cycle) FROM plans WHERE userId = :userId")
    suspend fun maxCycle(userId: Long): Int?

    @Query("UPDATE plans SET active = 0 WHERE userId = :userId")
    suspend fun deactivatePlans(userId: Long)

    @Insert
    suspend fun insertPlan(plan: PlanEntity): Long

    @Insert
    suspend fun insertDay(day: PlanDayEntity): Long

    @Update
    suspend fun updateDay(day: PlanDayEntity)

    @Insert
    suspend fun insertItem(item: PlanItemEntity): Long

    @Insert
    suspend fun insertItems(items: List<PlanItemEntity>)

    @Update
    suspend fun updateItem(item: PlanItemEntity)

    @Update
    suspend fun updateItems(items: List<PlanItemEntity>)

    @Delete
    suspend fun deleteItem(item: PlanItemEntity)

    @Query("DELETE FROM plan_items WHERE dayId = :dayId")
    suspend fun deleteItemsOfDay(dayId: Long)

    @Query("SELECT * FROM plan_days WHERE planId = :planId ORDER BY week, dayIndex")
    fun observeDays(planId: Long): Flow<List<PlanDayEntity>>

    @Query("SELECT * FROM plan_days WHERE planId = :planId ORDER BY week, dayIndex")
    suspend fun days(planId: Long): List<PlanDayEntity>

    @Query("SELECT * FROM plan_days WHERE id = :dayId")
    suspend fun day(dayId: Long): PlanDayEntity?

    @Query("SELECT * FROM plan_days WHERE id = :dayId")
    fun observeDay(dayId: Long): Flow<PlanDayEntity?>

    @Query("SELECT * FROM plan_days WHERE planId = :planId AND week = :week AND dayIndex = :dayIndex")
    suspend fun dayAt(planId: Long, week: Int, dayIndex: Int): PlanDayEntity?

    @Query("SELECT * FROM plan_items WHERE dayId = :dayId ORDER BY position")
    fun observeItems(dayId: Long): Flow<List<PlanItemEntity>>

    @Query("SELECT * FROM plan_items WHERE dayId = :dayId ORDER BY position")
    suspend fun items(dayId: Long): List<PlanItemEntity>

    @Query("SELECT * FROM plan_items WHERE id = :id")
    suspend fun item(id: Long): PlanItemEntity?

    @Query(
        "SELECT plan_items.* FROM plan_items INNER JOIN plan_days ON plan_items.dayId = plan_days.id " +
            "WHERE plan_days.planId = :planId ORDER BY plan_days.week, plan_days.dayIndex, plan_items.position",
    )
    suspend fun itemsOfPlan(planId: Long): List<PlanItemEntity>

    @Query(
        "SELECT plan_items.* FROM plan_items INNER JOIN plan_days ON plan_items.dayId = plan_days.id " +
            "WHERE plan_days.planId = :planId ORDER BY plan_days.week, plan_days.dayIndex, plan_items.position",
    )
    fun observeItemsOfPlan(planId: Long): Flow<List<PlanItemEntity>>
}

/** Fila de historial: una serie hecha, con la fecha y el usuario de su sesión. */
data class SetHistoryRow(
    val sessionId: Long,
    val startedAt: Long,
    val exerciseId: String,
    val weightKg: Double,
    val reps: Int,
    val rir: Int?,
    val isRecord: Boolean,
)

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE dayId = :dayId ORDER BY id DESC")
    suspend fun sessionsOfDay(dayId: Long): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE userId = :userId ORDER BY startedAt DESC")
    fun observeSessions(userId: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE userId = :userId ORDER BY startedAt DESC")
    suspend fun sessions(userId: Long): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE userId = :userId AND completed = 1 ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastCompleted(userId: Long): SessionEntity?

    @Query(
        "SELECT sessions.* FROM sessions INNER JOIN plan_days ON sessions.dayId = plan_days.id " +
            "WHERE plan_days.planId = :planId",
    )
    fun observeSessionsOfPlan(planId: Long): Flow<List<SessionEntity>>

    @Query(
        "SELECT sessions.* FROM sessions INNER JOIN plan_days ON sessions.dayId = plan_days.id " +
            "WHERE plan_days.planId = :planId",
    )
    suspend fun sessionsOfPlan(planId: Long): List<SessionEntity>

    // Series
    @Insert
    suspend fun insertSets(sets: List<SetLogEntity>): List<Long>

    @Insert
    suspend fun insertSet(set: SetLogEntity): Long

    @Update
    suspend fun updateSet(set: SetLogEntity)

    @Delete
    suspend fun deleteSet(set: SetLogEntity)

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY itemId, setIndex")
    fun observeSets(sessionId: Long): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY itemId, setIndex")
    suspend fun sets(sessionId: Long): List<SetLogEntity>

    @Query("UPDATE set_logs SET exerciseId = :exerciseId WHERE sessionId = :sessionId AND itemId = :itemId")
    suspend fun replaceExercise(sessionId: Long, itemId: Long, exerciseId: String)

    @Query(
        "SELECT s.sessionId AS sessionId, se.startedAt AS startedAt, s.exerciseId AS exerciseId, s.weightKg AS weightKg, " +
            "s.reps AS reps, s.rir AS rir, s.isRecord AS isRecord FROM set_logs s INNER JOIN sessions se ON s.sessionId = se.id " +
            "WHERE se.userId = :userId AND s.exerciseId = :exerciseId AND s.done = 1 ORDER BY se.startedAt",
    )
    suspend fun history(userId: Long, exerciseId: String): List<SetHistoryRow>

    @Query(
        "SELECT s.sessionId AS sessionId, se.startedAt AS startedAt, s.exerciseId AS exerciseId, s.weightKg AS weightKg, " +
            "s.reps AS reps, s.rir AS rir, s.isRecord AS isRecord FROM set_logs s INNER JOIN sessions se ON s.sessionId = se.id " +
            "WHERE se.userId = :userId AND s.done = 1 ORDER BY se.startedAt",
    )
    fun observeAllHistory(userId: Long): Flow<List<SetHistoryRow>>

    @Query(
        "SELECT s.sessionId AS sessionId, se.startedAt AS startedAt, s.exerciseId AS exerciseId, s.weightKg AS weightKg, " +
            "s.reps AS reps, s.rir AS rir, s.isRecord AS isRecord FROM set_logs s INNER JOIN sessions se ON s.sessionId = se.id " +
            "WHERE se.userId = :userId AND s.done = 1 ORDER BY se.startedAt",
    )
    suspend fun allHistory(userId: Long): List<SetHistoryRow>

    // Notas
    @Query("SELECT * FROM exercise_notes WHERE sessionId = :sessionId")
    fun observeNotes(sessionId: Long): Flow<List<ExerciseNoteEntity>>

    @Upsert
    suspend fun upsertNote(note: ExerciseNoteEntity): Long
}

@Dao
interface ExtraDao {
    @Query("SELECT * FROM exercise_extras")
    suspend fun all(): List<ExerciseExtraEntity>

    @Query("SELECT * FROM exercise_extras")
    fun observeAll(): Flow<List<ExerciseExtraEntity>>

    @Upsert
    suspend fun upsert(list: List<ExerciseExtraEntity>)

    @Query("DELETE FROM exercise_extras WHERE id = :id")
    suspend fun delete(id: String)
}

/** Acceso completo para exportar e importar copias de seguridad. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM users") suspend fun users(): List<UserEntity>
    @Query("SELECT * FROM injuries") suspend fun injuries(): List<InjuryEntity>
    @Query("SELECT * FROM measurements") suspend fun measurements(): List<MeasurementEntity>
    @Query("SELECT * FROM plans") suspend fun plans(): List<PlanEntity>
    @Query("SELECT * FROM plan_days") suspend fun days(): List<PlanDayEntity>
    @Query("SELECT * FROM plan_items") suspend fun items(): List<PlanItemEntity>
    @Query("SELECT * FROM sessions") suspend fun sessions(): List<SessionEntity>
    @Query("SELECT * FROM set_logs") suspend fun sets(): List<SetLogEntity>
    @Query("SELECT * FROM exercise_notes") suspend fun notes(): List<ExerciseNoteEntity>
    @Query("SELECT * FROM reminders") suspend fun reminders(): List<ReminderEntity>
    @Query("SELECT * FROM exercise_extras") suspend fun extras(): List<ExerciseExtraEntity>

    @Insert suspend fun insertUsers(list: List<UserEntity>)
    @Insert suspend fun insertInjuries(list: List<InjuryEntity>)
    @Insert suspend fun insertMeasurements(list: List<MeasurementEntity>)
    @Insert suspend fun insertPlans(list: List<PlanEntity>)
    @Insert suspend fun insertDays(list: List<PlanDayEntity>)
    @Insert suspend fun insertItems(list: List<PlanItemEntity>)
    @Insert suspend fun insertSessions(list: List<SessionEntity>)
    @Insert suspend fun insertSets(list: List<SetLogEntity>)
    @Insert suspend fun insertNotes(list: List<ExerciseNoteEntity>)
    @Insert suspend fun insertReminders(list: List<ReminderEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExtras(list: List<ExerciseExtraEntity>)

    @Query("DELETE FROM users") suspend fun deleteUsers()
    @Query("DELETE FROM exercise_extras") suspend fun deleteExtras()
}
