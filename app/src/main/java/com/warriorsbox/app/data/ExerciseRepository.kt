package com.warriorsbox.app.data

import android.content.Context
import com.warriorsbox.app.data.db.ExerciseExtraEntity
import com.warriorsbox.app.data.db.ExtraDao
import com.warriorsbox.core.engine.Catalog
import com.warriorsbox.core.engine.CatalogSource
import com.warriorsbox.core.model.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Biblioteca de ejercicios = catálogo de fábrica (assets) + ejercicios propios + sincronizados de internet.
 */
class ExerciseRepository(private val context: Context, private val extraDao: ExtraDao) {

    private val mutex = Mutex()
    private var builtIn: List<Exercise>? = null
    var sources: List<CatalogSource> = emptyList()
        private set

    private suspend fun builtIn(): List<Exercise> = mutex.withLock {
        builtIn ?: withContext(Dispatchers.IO) {
            val text = context.assets.open("catalog/exercises.json").bufferedReader().use { it.readText() }
            val file = Catalog.parse(text)
            sources = file.sources
            file.exercises
        }.also { builtIn = it }
    }

    private val photosDir get() = java.io.File(context.filesDir, "photos")

    private fun decodeExtras(list: List<ExerciseExtraEntity>): List<Exercise> =
        list.mapNotNull { runCatching { Catalog.json.decodeFromString(Exercise.serializer(), it.json) }.getOrNull() }
            .map { e -> if (e.imageUrls.none { it.startsWith("file:") }) e else e.copy(imageUrls = e.imageUrls.map(::localPhoto)) }

    /** Las fotos propias guardan la ruta completa; si la carpeta cambió (copia restaurada), se corrige. */
    private fun localPhoto(url: String): String {
        if (!url.startsWith("file:")) return url
        val file = java.io.File(url.removePrefix("file://").removePrefix("file:"))
        if (file.exists()) return url
        return "file://" + java.io.File(photosDir, file.name).absolutePath
    }

    /** true si el ejercicio viene con la app (todos los celulares lo tienen). */
    suspend fun isBuiltIn(id: String): Boolean = builtIn().any { it.id == id }

    /** Fotos propias (archivos del celular) de un ejercicio. */
    fun localPhotoFiles(exercise: Exercise): List<java.io.File> =
        exercise.imageUrls.filter { it.startsWith("file:") }
            .map { java.io.File(localPhoto(it).removePrefix("file://")) }
            .filter { it.exists() }

    /** Todos los ejercicios. Los propios y sincronizados que repitan id reemplazan al de fábrica. */
    val all: Flow<List<Exercise>> = combine(
        flow { emit(builtIn()) }.flowOn(Dispatchers.IO),
        extraDao.observeAll(),
    ) { base, extras ->
        merge(base, decodeExtras(extras))
    }

    suspend fun allNow(): List<Exercise> = merge(builtIn(), decodeExtras(extraDao.all()))

    suspend fun byId(): Map<String, Exercise> = allNow().associateBy { it.id }

    suspend fun get(id: String): Exercise? = allNow().firstOrNull { it.id == id }

    private fun merge(base: List<Exercise>, extras: List<Exercise>): List<Exercise> {
        val extraIds = extras.map { it.id }.toSet()
        val baseNames = base.flatMap { listOfNotNull(it.name.lowercase(), it.nameEn?.lowercase()) }.toSet()
        // Los sincronizados que ya existen con el mismo nombre se omiten para no duplicar.
        val baseIds = base.map { it.id }.toSet()
        val cleanExtras = extras.filter { it.custom || it.id in baseIds || it.name.lowercase() !in baseNames }
        return withFallbackImages(base.filter { it.id !in extraIds } + cleanExtras)
    }

    suspend fun saveCustom(exercise: Exercise) {
        val stored = exercise.copy(custom = true, source = "propio")
        extraDao.upsert(listOf(ExerciseExtraEntity(stored.id, Catalog.json.encodeToString(Exercise.serializer(), stored), "propio")))
    }

    suspend fun deleteCustom(id: String) {
        get(id)?.let { e -> localPhotoFiles(e).filter { it.parentFile == photosDir }.forEach { it.delete() } }
        extraDao.delete(id)
    }

    suspend fun saveSynced(list: List<Exercise>, source: String) {
        if (list.isEmpty()) return
        val existing = extraDao.all().filter { it.source == "propio" }.map { it.id }.toSet()
        extraDao.upsert(
            list.filter { it.id !in existing }.map {
                ExerciseExtraEntity(it.id, Catalog.json.encodeToString(Exercise.serializer(), it), source)
            },
        )
    }

    suspend fun countBySource(): Map<String, Int> = extraDao.all().groupingBy { it.source }.eachCount()

    suspend fun first(): List<Exercise> = all.first()

    companion object {
        private fun words(s: String?): Set<String> =
            s.orEmpty().lowercase().split(Regex("[^a-záéíóúñü0-9]+")).filter { it.length > 3 }.toSet()

        /**
         * Ejercicios sin imagen (propios o de internet): usan la imagen del ejercicio más parecido
         * (palabras del nombre, mismo movimiento y mismo músculo principal).
         */
        fun withFallbackImages(list: List<Exercise>): List<Exercise> {
            if (list.all { it.image != null || it.imageUrls.isNotEmpty() }) return list
            val withImages = list.filter { it.image != null || it.imageUrls.isNotEmpty() }
                .map { it to (words(it.name) + words(it.nameEn)) }
            if (withImages.isEmpty()) return list
            return list.map { e ->
                if (e.image != null || e.imageUrls.isNotEmpty()) return@map e
                val names = words(e.name) + words(e.nameEn)
                val best = withImages.maxByOrNull { (o, oWords) ->
                    val shared = oWords.count { it in names }
                    shared * 10 +
                        (if (o.pattern == e.pattern) 6 else 0) +
                        (if (o.primaryMuscles.firstOrNull() == e.primaryMuscles.firstOrNull()) 5 else 0) +
                        (if (o.curated) 2 else 0) +
                        (if (o.equipment == e.equipment) 1 else 0)
                }?.first ?: return@map e
                e.copy(image = best.image, imageUrls = best.imageUrls)
            }
        }
    }
}
