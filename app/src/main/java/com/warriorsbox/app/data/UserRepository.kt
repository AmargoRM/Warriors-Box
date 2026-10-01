package com.warriorsbox.app.data

import androidx.room.withTransaction
import com.warriorsbox.app.data.db.AppDatabase
import com.warriorsbox.app.data.db.InjuryEntity
import com.warriorsbox.app.data.db.MeasurementEntity
import com.warriorsbox.app.data.db.ReminderEntity
import com.warriorsbox.app.data.db.UserEntity
import com.warriorsbox.core.engine.BodyMetrics
import com.warriorsbox.core.model.BodyZone
import com.warriorsbox.core.model.Equipment
import com.warriorsbox.core.model.TrainingProfile
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class UserRepository(private val db: AppDatabase, private val photos: PhotoStore) {

    private val dao = db.users()

    val users: Flow<List<UserEntity>> = dao.observeAll()

    fun observe(id: Long): Flow<UserEntity?> = dao.observe(id)
    suspend fun get(id: Long): UserEntity? = dao.get(id)
    suspend fun all(): List<UserEntity> = dao.all()
    suspend fun count(): Int = dao.count()

    fun injuries(userId: Long): Flow<List<InjuryEntity>> = dao.observeInjuries(userId)
    suspend fun injuriesNow(userId: Long): List<InjuryEntity> = dao.injuries(userId)
    fun measurements(userId: Long): Flow<List<MeasurementEntity>> = dao.observeMeasurements(userId)
    fun reminder(userId: Long): Flow<ReminderEntity?> = dao.observeReminder(userId)

    /** Crea o actualiza el usuario con sus lesiones. Devuelve el id. */
    suspend fun save(user: UserEntity, injuries: List<InjuryEntity>): Long = db.withTransaction {
        val id = if (user.id == 0L) dao.insert(user) else user.id.also { dao.update(user) }
        dao.deleteInjuries(id)
        dao.insertInjuries(injuries.map { it.copy(id = 0, userId = id) })
        // Registrar el peso como primera medida (o una nueva si cambió).
        val weight = user.weightKg
        if (weight != null) {
            val today = LocalDate.now().toEpochDay()
            dao.insertMeasurement(MeasurementEntity(userId = id, epochDay = today, weightKg = weight, waistCm = user.waistCm, bodyFatPct = user.bodyFatPct))
        }
        id
    }

    /** Se llama antes de borrar un usuario (el modo coach avisa al otro celular). */
    var deleteListener: (suspend (Long) -> Unit)? = null

    suspend fun delete(user: UserEntity) {
        runCatching { deleteListener?.invoke(user.id) }
        photos.delete(user.photoPath)
        photos.delete(user.backgroundPath)
        dao.delete(user.id)
    }

    suspend fun addMeasurement(m: MeasurementEntity) {
        dao.insertMeasurement(m)
        val user = dao.get(m.userId) ?: return
        dao.update(user.copy(weightKg = m.weightKg ?: user.weightKg, waistCm = m.waistCm ?: user.waistCm, bodyFatPct = m.bodyFatPct ?: user.bodyFatPct))
    }

    suspend fun deleteMeasurement(m: MeasurementEntity) = dao.deleteMeasurement(m)

    /** Registra una molestia desde el botón "No puedo hacerlo → Me duele". */
    suspend fun addPain(userId: Long, zone: BodyZone, description: String) {
        val existing = dao.injuries(userId).firstOrNull { it.zoneEnum == zone && it.active }
        if (existing == null) {
            dao.upsertInjury(InjuryEntity(userId = userId, zone = zone.name, description = description, approxDate = LocalDate.now().toString(), active = true))
        }
    }

    suspend fun updateUser(user: UserEntity) = dao.update(user)

    suspend fun saveReminder(reminder: ReminderEntity) = dao.saveReminder(reminder)
    suspend fun reminderNow(userId: Long) = dao.reminder(userId)
    suspend fun allReminders() = dao.allReminders()

    suspend fun trainingProfile(userId: Long): TrainingProfile? {
        val user = dao.get(userId) ?: return null
        return trainingProfile(user, dao.injuries(userId))
    }

    companion object {
        fun ageOf(user: UserEntity): Int? =
            user.birthDate?.let { runCatching { BodyMetrics.age(LocalDate.parse(it)) }.getOrNull() }

        fun trainingProfile(user: UserEntity, injuries: List<InjuryEntity>): TrainingProfile {
            val equipment = user.equipmentSet.ifEmpty { Equipment.defaultsFor(user.locationEnum) } + Equipment.NONE
            return TrainingProfile(
                level = user.levelEnum,
                goal = user.goalEnum,
                daysPerWeek = user.daysPerWeek,
                minutesPerSession = user.minutesPerSession,
                equipment = equipment,
                activeInjuries = injuries.filter { it.active }.map { it.zoneEnum }.filter { it != BodyZone.OTHER }.toSet(),
                bodyWeightKg = user.weightKg ?: 70.0,
                sex = user.sexEnum,
                age = ageOf(user) ?: 30,
                maxDumbbellKg = user.maxDumbbellKg,
                medicalRestriction = user.medicalRestriction,
            )
        }
    }
}
