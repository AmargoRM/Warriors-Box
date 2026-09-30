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

    private fun decodeExtras(list: List<ExerciseExtraEntity>): List<Exercise> =
        list.mapNotNull { runCatching { Catalog.json.decodeFromString(Exercise.serializer(), it.json) }.getOrNull() }

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
        return base.filter { it.id !in extraIds } + cleanExtras
    }

    suspend fun saveCustom(exercise: Exercise) {
        val stored = exercise.copy(custom = true, source = "propio")
        extraDao.upsert(listOf(ExerciseExtraEntity(stored.id, Catalog.json.encodeToString(Exercise.serializer(), stored), "propio")))
    }

    suspend fun deleteCustom(id: String) = extraDao.delete(id)

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
}
