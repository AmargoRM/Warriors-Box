package com.warriorsbox.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.warriorsbox.app.data.db.AppDatabase
import com.warriorsbox.app.data.db.ExerciseExtraEntity
import com.warriorsbox.app.data.db.ExerciseNoteEntity
import com.warriorsbox.app.data.db.InjuryEntity
import com.warriorsbox.app.data.db.MeasurementEntity
import com.warriorsbox.app.data.db.PlanDayEntity
import com.warriorsbox.app.data.db.PlanEntity
import com.warriorsbox.app.data.db.PlanItemEntity
import com.warriorsbox.app.data.db.ReminderEntity
import com.warriorsbox.app.data.db.SessionEntity
import com.warriorsbox.app.data.db.SetLogEntity
import com.warriorsbox.app.data.db.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupData(
    val format: String = "warriors-box-backup",
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val users: List<UserEntity>,
    val injuries: List<InjuryEntity>,
    val measurements: List<MeasurementEntity>,
    val plans: List<PlanEntity>,
    val days: List<PlanDayEntity>,
    val items: List<PlanItemEntity>,
    val sessions: List<SessionEntity>,
    val sets: List<SetLogEntity>,
    val notes: List<ExerciseNoteEntity>,
    val reminders: List<ReminderEntity>,
    val extras: List<ExerciseExtraEntity>,
    val settings: Map<String, String> = emptyMap(),
)

/**
 * Copias de seguridad en un archivo .zip: data.json + carpeta photos/.
 * Restaurar REEMPLAZA todos los datos actuales.
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val photos: PhotoStore,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun snapshot(): BackupData {
        val b = db.backup()
        return BackupData(
            users = b.users(), injuries = b.injuries(), measurements = b.measurements(), plans = b.plans(),
            days = b.days(), items = b.items(), sessions = b.sessions(), sets = b.sets(), notes = b.notes(),
            reminders = b.reminders(), extras = b.extras(), settings = settings.exportable(),
        )
    }

    suspend fun export(out: OutputStream) = withContext(Dispatchers.IO) {
        val data = snapshot()
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(json.encodeToString(BackupData.serializer(), data).toByteArray())
            zip.closeEntry()
            photos.dir.listFiles()?.filter { it.isFile }?.forEach { file ->
                zip.putNextEntry(ZipEntry("photos/" + file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        settings.setLastBackupAt(System.currentTimeMillis())
    }

    suspend fun exportTo(uri: Uri) {
        val stream = context.contentResolver.openOutputStream(uri) ?: error("No se pudo abrir el archivo")
        stream.use { export(it) }
    }

    /** Copia automática local (por ejemplo, antes de instalar una actualización). Guarda las 3 últimas. */
    suspend fun autoBackup(): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "auto-$stamp.zip")
        file.outputStream().use { export(it) }
        dir.listFiles()?.filter { it.name.startsWith("auto-") }?.sortedByDescending { it.name }?.drop(3)?.forEach { it.delete() }
        file
    }

    fun autoBackups(): List<File> =
        File(context.filesDir, "backups").listFiles()?.filter { it.name.startsWith("auto-") }?.sortedByDescending { it.name }.orEmpty()

    suspend fun importFrom(uri: Uri) {
        val stream = context.contentResolver.openInputStream(uri) ?: error("No se pudo abrir el archivo")
        stream.use { import(it) }
    }

    suspend fun import(input: InputStream) = withContext(Dispatchers.IO) {
        var data: BackupData? = null
        val photoFiles = mutableMapOf<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == "data.json" -> data = json.decodeFromString(BackupData.serializer(), zip.readBytes().decodeToString())
                    entry.name.startsWith("photos/") && !entry.isDirectory -> {
                        val name = File(entry.name).name
                        if (name.isNotBlank() && !name.contains("..")) photoFiles[name] = zip.readBytes()
                    }
                }
                entry = zip.nextEntry
            }
        }
        val backup = data ?: error("El archivo no contiene una copia de Warriors Box.")
        require(backup.format == "warriors-box-backup") { "El archivo no es una copia de Warriors Box." }
        restore(backup, photoFiles)
    }

    suspend fun restore(backup: BackupData, photoFiles: Map<String, ByteArray> = emptyMap()) {
        // Las rutas de fotos apuntan a la carpeta interna; se reescriben por si cambió el celular.
        fun fix(path: String?): String? = path?.let { File(photos.dir, File(it).name).absolutePath }
        val b = db.backup()
        db.withTransaction {
            b.deleteUsers() // borra en cascada planes, sesiones, lesiones, medidas y recordatorios
            b.deleteExtras()
            b.insertUsers(backup.users.map { it.copy(photoPath = fix(it.photoPath), backgroundPath = fix(it.backgroundPath)) })
            b.insertInjuries(backup.injuries)
            b.insertMeasurements(backup.measurements)
            b.insertPlans(backup.plans)
            b.insertDays(backup.days)
            b.insertItems(backup.items)
            b.insertSessions(backup.sessions)
            b.insertSets(backup.sets)
            b.insertNotes(backup.notes)
            b.insertReminders(backup.reminders)
            b.insertExtras(backup.extras)
        }
        withContext(Dispatchers.IO) {
            photoFiles.forEach { (name, bytes) -> File(photos.dir, name).writeBytes(bytes) }
        }
        settings.import(backup.settings)
    }
}
