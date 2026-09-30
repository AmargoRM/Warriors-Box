package com.warriorsbox.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Sex(val label: String) {
    MALE("Masculino"),
    FEMALE("Femenino"),
    UNSPECIFIED("Prefiero no decir"),
}

@Serializable
enum class Level(val label: String, val rank: Int) {
    BEGINNER("Principiante", 0),
    INTERMEDIATE("Intermedio", 1),
    ADVANCED("Avanzado", 2),
}

@Serializable
enum class Goal(val label: String) {
    FAT_LOSS("Perder grasa"),
    MUSCLE("Ganar músculo"),
    STRENGTH("Fuerza"),
    ENDURANCE("Resistencia"),
    HEALTH("Salud general"),
    REHAB("Rehabilitación ligera"),
}

@Serializable
enum class TrainingLocation(val label: String) {
    GYM("Gimnasio completo"),
    HOME_DUMBBELLS("Casa con mancuernas"),
    HOME_BODYWEIGHT("Casa sin equipo"),
}

@Serializable
enum class Equipment(val label: String) {
    NONE("Peso corporal"),
    DUMBBELL("Mancuernas"),
    BARBELL("Barra"),
    EZ_BAR("Barra Z"),
    KETTLEBELL("Pesa rusa"),
    MACHINE("Máquinas"),
    CABLE("Poleas"),
    BANDS("Bandas elásticas"),
    BENCH("Banco"),
    PULLUP_BAR("Barra de dominadas"),
    BIKE("Bicicleta"),
    TREADMILL("Caminadora"),
    OTHER("Otro (balón, rueda, etc.)"),
    ;

    companion object {
        /** Equipo que se asume disponible según el lugar de entrenamiento. */
        fun defaultsFor(location: TrainingLocation): Set<Equipment> = when (location) {
            TrainingLocation.GYM -> entries.toSet()
            TrainingLocation.HOME_DUMBBELLS -> setOf(NONE, DUMBBELL)
            TrainingLocation.HOME_BODYWEIGHT -> setOf(NONE)
        }
    }
}

@Serializable
enum class BodyZone(val label: String) {
    SHOULDER("Hombro"),
    KNEE("Rodilla"),
    LOWER_BACK("Espalda baja"),
    WRIST("Muñeca"),
    ELBOW("Codo"),
    NECK("Cuello"),
    ANKLE("Tobillo"),
    HIP("Cadera"),
    OTHER("Otra"),
}

@Serializable
enum class Muscle(val label: String) {
    CHEST("Pecho"),
    LATS("Dorsales"),
    MIDDLE_BACK("Espalda media"),
    LOWER_BACK("Espalda baja"),
    TRAPS("Trapecio"),
    SHOULDERS("Hombros"),
    BICEPS("Bíceps"),
    TRICEPS("Tríceps"),
    FOREARMS("Antebrazos"),
    QUADS("Cuádriceps"),
    HAMSTRINGS("Isquiotibiales"),
    GLUTES("Glúteos"),
    CALVES("Pantorrillas"),
    ADDUCTORS("Aductores"),
    ABDUCTORS("Abductores"),
    ABS("Abdomen"),
    NECK("Cuello"),
    CARDIO("Cardio"),
}

@Serializable
enum class MovementPattern(val label: String) {
    HORIZONTAL_PUSH("Empuje horizontal"),
    VERTICAL_PUSH("Empuje vertical"),
    HORIZONTAL_PULL("Tracción horizontal"),
    VERTICAL_PULL("Tracción vertical"),
    SQUAT("Sentadilla"),
    HINGE("Bisagra de cadera"),
    LUNGE("Zancada / unilateral"),
    CORE("Core"),
    ISOLATION("Aislamiento"),
    CARRY("Cargadas / transporte"),
    CARDIO("Cardio"),
    MOBILITY("Movilidad / estiramiento"),
}

/** Motivos del botón "No puedo hacerlo". */
@Serializable
enum class SkipReason(val label: String) {
    NO_EQUIPMENT("No tengo el equipo"),
    BUSY("Máquina ocupada"),
    PAIN("Me duele o molesta"),
    DONT_KNOW("No sé hacerlo"),
    TOO_HARD("Muy difícil"),
}
