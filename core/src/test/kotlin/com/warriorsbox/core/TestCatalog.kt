package com.warriorsbox.core

import com.warriorsbox.core.engine.Catalog
import com.warriorsbox.core.model.Equipment
import com.warriorsbox.core.model.Exercise
import com.warriorsbox.core.model.Goal
import com.warriorsbox.core.model.Level
import com.warriorsbox.core.model.TrainingLocation
import com.warriorsbox.core.model.TrainingProfile
import java.io.File

/** Carga el catálogo real que trae la app (assets). */
object TestCatalog {
    val exercises: List<Exercise> by lazy {
        val file = listOf(
            File("../app/src/main/assets/catalog/exercises.json"),
            File("app/src/main/assets/catalog/exercises.json"),
        ).first { it.exists() }
        Catalog.parse(file.readText()).exercises
    }

    fun byId(id: String): Exercise = exercises.first { it.id == id }

    fun profile(
        level: Level = Level.INTERMEDIATE,
        goal: Goal = Goal.MUSCLE,
        days: Int = 4,
        minutes: Int = 60,
        location: TrainingLocation = TrainingLocation.GYM,
        weight: Double = 75.0,
    ) = TrainingProfile(
        level = level,
        goal = goal,
        daysPerWeek = days,
        minutesPerSession = minutes,
        equipment = Equipment.defaultsFor(location),
        bodyWeightKg = weight,
    )
}
