package com.warriorsbox.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos local. IMPORTANTE: cada cambio de esquema debe subir [version] y agregar una
 * migración en [MIGRATIONS]. Nunca usar fallbackToDestructiveMigration: borraría los datos al actualizar.
 */
@Database(
    entities = [
        UserEntity::class,
        InjuryEntity::class,
        MeasurementEntity::class,
        PlanEntity::class,
        PlanDayEntity::class,
        PlanItemEntity::class,
        SessionEntity::class,
        SetLogEntity::class,
        ExerciseNoteEntity::class,
        ReminderEntity::class,
        ExerciseExtraEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun users(): UserDao
    abstract fun plans(): PlanDao
    abstract fun sessions(): SessionDao
    abstract fun extras(): ExtraDao
    abstract fun backup(): BackupDao

    companion object {
        const val NAME = "warriors-box.db"

        /** Migraciones futuras: agregar aquí (por ejemplo, Migration(1, 2) { ... }). */
        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
