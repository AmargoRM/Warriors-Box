package com.warriorsbox.core.engine

import com.warriorsbox.core.model.BodyZone
import com.warriorsbox.core.model.Equipment
import com.warriorsbox.core.model.Exercise
import com.warriorsbox.core.model.Level
import com.warriorsbox.core.model.MovementPattern
import com.warriorsbox.core.model.Muscle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Conversión de las fuentes públicas de ejercicios al modelo de la app.
 *  - free-exercise-db (dominio público): https://github.com/yuhonas/free-exercise-db
 *  - wger (CC-BY-SA): https://wger.de/api/v2/
 * Sin inteligencia artificial: solo reglas fijas.
 */
object ExternalSources {

    const val FEDB_JSON_URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json"
    const val FEDB_IMAGE_BASE = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"
    const val WGER_URL = "https://wger.de/api/v2/exerciseinfo/?limit=100&offset="
    const val WGER_LANG_ENGLISH = 2
    const val WGER_LANG_SPANISH = 4

    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.str(key: String): String? = this[key].str()
    private fun JsonObject.arr(key: String): JsonArray = (this[key] as? JsonArray) ?: JsonArray(emptyList())

    // ---------------------------------------------------------------- free-exercise-db

    private val fedbMuscles = mapOf(
        "abdominals" to Muscle.ABS, "abductors" to Muscle.ABDUCTORS, "adductors" to Muscle.ADDUCTORS,
        "biceps" to Muscle.BICEPS, "calves" to Muscle.CALVES, "chest" to Muscle.CHEST, "forearms" to Muscle.FOREARMS,
        "glutes" to Muscle.GLUTES, "hamstrings" to Muscle.HAMSTRINGS, "lats" to Muscle.LATS,
        "lower back" to Muscle.LOWER_BACK, "middle back" to Muscle.MIDDLE_BACK, "neck" to Muscle.NECK,
        "quadriceps" to Muscle.QUADS, "shoulders" to Muscle.SHOULDERS, "traps" to Muscle.TRAPS, "triceps" to Muscle.TRICEPS,
    )

    private val fedbEquipment = mapOf(
        "barbell" to Equipment.BARBELL, "dumbbell" to Equipment.DUMBBELL, "cable" to Equipment.CABLE,
        "machine" to Equipment.MACHINE, "kettlebells" to Equipment.KETTLEBELL, "bands" to Equipment.BANDS,
        "medicine ball" to Equipment.OTHER, "exercise ball" to Equipment.OTHER, "foam roll" to Equipment.OTHER,
        "other" to Equipment.OTHER, "e-z curl bar" to Equipment.EZ_BAR,
    )

    fun parseFreeExerciseDb(text: String): List<Exercise> =
        json.parseToJsonElement(text).jsonArray.mapNotNull { runCatching { fedbExercise(it.jsonObject) }.getOrNull() }

    private fun fedbExercise(o: JsonObject): Exercise? {
        val id = o.str("id") ?: return null
        val name = o.str("name") ?: return null
        val primary = o.arr("primaryMuscles").mapNotNull { fedbMuscles[it.str()] }
        if (primary.isEmpty()) return null
        val category = o.str("category")
        val mechanicCompound = o.str("mechanic") == "compound"
        val pattern = patternFor(name, primary.first(), category, mechanicCompound, o.str("force"))
        val equipment = buildSet {
            fedbEquipment[o.str("equipment")]?.let { add(it) }
            addAll(equipmentHints(name))
        }
        return Exercise(
            id = "fedb-" + id.lowercase(),
            name = name,
            nameEn = name,
            pattern = pattern,
            primaryMuscles = primary,
            secondaryMuscles = o.arr("secondaryMuscles").mapNotNull { fedbMuscles[it.str()] },
            equipment = equipment,
            level = when (o.str("level")) {
                "beginner" -> Level.BEGINNER
                "expert" -> Level.ADVANCED
                else -> Level.INTERMEDIATE
            },
            contraindications = contraindicationsFor(name, pattern, equipment, category),
            compound = mechanicCompound,
            steps = o.arr("instructions").mapNotNull { it.str() },
            imageUrls = o.arr("images").mapNotNull { it.str()?.let { p -> FEDB_IMAGE_BASE + p } },
            curated = false,
            unilateral = listOf("one-arm", "one arm", "single", "one-leg", "one leg", "alternat").any { it in name.lowercase() },
            timed = category == "cardio" || category == "stretching",
            source = "free-exercise-db",
        )
    }

    // ---------------------------------------------------------------- wger

    data class WgerPage(val exercises: List<Exercise>, val hasNext: Boolean)

    private val wgerMuscles = mapOf(
        "abs" to Muscle.ABS, "obliques" to Muscle.ABS, "biceps" to Muscle.BICEPS, "brachialis" to Muscle.BICEPS,
        "calves" to Muscle.CALVES, "soleus" to Muscle.CALVES, "chest" to Muscle.CHEST, "glutes" to Muscle.GLUTES,
        "hamstrings" to Muscle.HAMSTRINGS, "lats" to Muscle.LATS, "quads" to Muscle.QUADS,
        "shoulders" to Muscle.SHOULDERS, "serratus anterior" to Muscle.CHEST, "traps" to Muscle.TRAPS,
        "triceps" to Muscle.TRICEPS, "lower back" to Muscle.LOWER_BACK,
    )

    fun parseWgerPage(text: String): WgerPage {
        val root = json.parseToJsonElement(text).jsonObject
        val results = root.arr("results").mapNotNull { runCatching { wgerExercise(it.jsonObject) }.getOrNull() }
        val next = root["next"]
        return WgerPage(results, next != null && next.str() != null)
    }

    private fun wgerMuscle(o: JsonObject): Muscle? {
        val en = (o.str("name_en")?.takeIf { it.isNotBlank() } ?: o.str("name"))?.lowercase() ?: return null
        return wgerMuscles[en] ?: wgerMuscles.entries.firstOrNull { en.contains(it.key) }?.value
    }

    private fun wgerExercise(o: JsonObject): Exercise? {
        val id = (o["id"] as? JsonPrimitive)?.intOrNull ?: return null
        // Versiones nuevas usan "translations"; las antiguas, "exercises".
        val translations = o.arr("translations").ifEmpty { o.arr("exercises") }.map { it.jsonObject }
        val lang = { t: JsonObject -> (t["language"] as? JsonPrimitive)?.intOrNull }
        val es = translations.firstOrNull { lang(it) == WGER_LANG_SPANISH }
        val en = translations.firstOrNull { lang(it) == WGER_LANG_ENGLISH }
        val chosen = es ?: en ?: return null
        val name = chosen.str("name")?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val nameEn = en?.str("name")?.trim()
        val primary = o.arr("muscles").mapNotNull { wgerMuscle(it.jsonObject) }.distinct()
        val category = (o["category"] as? JsonObject)?.str("name")?.lowercase().orEmpty()
        val primaryMuscles = primary.ifEmpty { listOfNotNull(muscleForCategory(category)) }
        if (primaryMuscles.isEmpty()) return null
        val equipmentNames = o.arr("equipment").mapNotNull { (it as? JsonObject)?.str("name")?.lowercase() }
        val equipment = equipmentNames.mapNotNull { wgerEquipment(it) }.toSet() + equipmentHints(nameEn ?: name)
        val englishName = (nameEn ?: name).lowercase()
        val pattern = if (category == "cardio") MovementPattern.CARDIO else patternForWger(englishName, category, primaryMuscles.first())
        val images = o.arr("images").mapNotNull { element ->
            val img = element as? JsonObject ?: return@mapNotNull null
            val url = img.str("image") ?: return@mapNotNull null
            url to ((img["is_main"] as? JsonPrimitive)?.booleanOrNull ?: false)
        }.sortedByDescending { it.second }.map { it.first }
        val description = chosen.str("description").orEmpty()
        return Exercise(
            id = "wger-$id",
            name = name,
            nameEn = nameEn,
            pattern = pattern,
            primaryMuscles = primaryMuscles,
            secondaryMuscles = o.arr("muscles_secondary").mapNotNull { wgerMuscle(it.jsonObject) }.distinct(),
            equipment = equipment - Equipment.NONE,
            level = Level.INTERMEDIATE,
            contraindications = contraindicationsFor(englishName, pattern, equipment, category),
            compound = pattern !in setOf(MovementPattern.ISOLATION, MovementPattern.CORE, MovementPattern.CARDIO),
            steps = htmlToSteps(description),
            imageUrls = images,
            curated = false,
            timed = pattern == MovementPattern.CARDIO,
            source = "wger",
        )
    }

    private fun muscleForCategory(category: String): Muscle? = when (category) {
        "abs" -> Muscle.ABS
        "arms" -> Muscle.BICEPS
        "back" -> Muscle.LATS
        "calves" -> Muscle.CALVES
        "chest" -> Muscle.CHEST
        "legs" -> Muscle.QUADS
        "shoulders" -> Muscle.SHOULDERS
        "cardio" -> Muscle.CARDIO
        else -> null
    }

    private fun wgerEquipment(name: String): Equipment? = when {
        "sz" in name -> Equipment.EZ_BAR
        "barbell" in name -> Equipment.BARBELL
        "dumbbell" in name -> Equipment.DUMBBELL
        "kettlebell" in name -> Equipment.KETTLEBELL
        "pull-up" in name || "pull up" in name -> Equipment.PULLUP_BAR
        "bench" in name -> Equipment.BENCH
        "band" in name -> Equipment.BANDS
        "cable" in name -> Equipment.CABLE
        "machine" in name -> Equipment.MACHINE
        "none" in name || "bodyweight" in name || "mat" in name -> Equipment.NONE
        else -> Equipment.OTHER
    }

    private fun patternForWger(name: String, category: String, primary: Muscle): MovementPattern = when (category) {
        "abs" -> MovementPattern.CORE
        "arms", "calves" -> MovementPattern.ISOLATION
        "back" -> when {
            listOf("deadlift", "hyperextension", "good morning").any { it in name } -> MovementPattern.HINGE
            listOf("pull", "chin", "lat").any { it in name } && "row" !in name -> MovementPattern.VERTICAL_PULL
            "row" in name -> MovementPattern.HORIZONTAL_PULL
            else -> MovementPattern.ISOLATION
        }
        "chest" -> if (listOf("press", "push", "dip").any { it in name }) MovementPattern.HORIZONTAL_PUSH else MovementPattern.ISOLATION
        "shoulders" -> if ("press" in name) MovementPattern.VERTICAL_PUSH else MovementPattern.ISOLATION
        "legs" -> when {
            listOf("lunge", "split", "step", "bulgarian", "pistol").any { it in name } -> MovementPattern.LUNGE
            listOf("deadlift", "hip thrust", "bridge", "good morning", "swing").any { it in name } -> MovementPattern.HINGE
            listOf("squat", "leg press").any { it in name } -> MovementPattern.SQUAT
            else -> MovementPattern.ISOLATION
        }
        else -> patternFor(name, primary, null, compound = false, force = null)
    }

    // ---------------------------------------------------------------- reglas compartidas

    fun patternFor(name: String, primary: Muscle, category: String?, compound: Boolean, force: String?): MovementPattern {
        val n = name.lowercase()
        if (category == "cardio") return MovementPattern.CARDIO
        if (category == "stretching") return MovementPattern.MOBILITY
        return when (primary) {
            Muscle.ABS -> MovementPattern.CORE
            Muscle.QUADS -> when {
                listOf("lunge", "step", "split", "single-leg", "one leg", "one-leg", "pistol").any { it in n } -> MovementPattern.LUNGE
                compound -> MovementPattern.SQUAT
                else -> MovementPattern.ISOLATION
            }
            Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.LOWER_BACK ->
                if ("curl" in n || !compound) MovementPattern.ISOLATION else MovementPattern.HINGE
            Muscle.CHEST -> if (compound && force == "push") MovementPattern.HORIZONTAL_PUSH else MovementPattern.ISOLATION
            Muscle.SHOULDERS -> if (compound && force == "push") MovementPattern.VERTICAL_PUSH else MovementPattern.ISOLATION
            Muscle.LATS -> when {
                !compound -> MovementPattern.ISOLATION
                "row" in n -> MovementPattern.HORIZONTAL_PULL
                else -> MovementPattern.VERTICAL_PULL
            }
            Muscle.MIDDLE_BACK -> if (compound) MovementPattern.HORIZONTAL_PULL else MovementPattern.ISOLATION
            Muscle.TRICEPS -> if (compound && force == "push") MovementPattern.HORIZONTAL_PUSH else MovementPattern.ISOLATION
            else -> MovementPattern.ISOLATION
        }
    }

    private fun equipmentHints(name: String): Set<Equipment> {
        val n = name.lowercase()
        return buildSet {
            if ("bench" in n) add(Equipment.BENCH)
            if (listOf("pull-up", "pullup", "chin-up", "chin up", "hanging").any { it in n }) add(Equipment.PULLUP_BAR)
        }
    }

    fun contraindicationsFor(name: String, pattern: MovementPattern, equipment: Set<Equipment>, category: String?): Set<BodyZone> {
        val n = name.lowercase()
        return buildSet {
            if (pattern == MovementPattern.SQUAT || pattern == MovementPattern.LUNGE) add(BodyZone.KNEE)
            if (pattern == MovementPattern.HINGE && Equipment.BARBELL in equipment) add(BodyZone.LOWER_BACK)
            if (pattern == MovementPattern.VERTICAL_PUSH) add(BodyZone.SHOULDER)
            if (category == "plyometrics" || category == "olympic weightlifting") {
                add(BodyZone.KNEE); add(BodyZone.ANKLE); add(BodyZone.LOWER_BACK)
            }
            if ("behind the neck" in n || "behind neck" in n) { add(BodyZone.SHOULDER); add(BodyZone.NECK) }
            if ("neck" in n) add(BodyZone.NECK)
        }
    }

    /** Convierte la descripción HTML de wger en pasos de texto simples. */
    fun htmlToSteps(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val text = html
            .replace(Regex("(?i)<br\\s*/?>|</p>|</li>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        return text.split('\n').map { it.trim() }.filter { it.length > 2 }.take(8)
    }
}
