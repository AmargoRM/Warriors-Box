package com.warriorsbox.core.engine

import com.warriorsbox.core.model.Exercise
import com.warriorsbox.core.model.MovementPattern
import com.warriorsbox.core.model.Muscle
import kotlin.random.Random

/** Frases motivacionales irónicas y satíricas al terminar un ejercicio. */
object Motivation {

    enum class Context { GENERIC, LEGS, PUSH, PULL, ARMS, CORE, CARDIO, RECORD, STRUGGLE, LAST }

    val phrases: Map<Context, List<String>> = mapOf(
        Context.GENERIC to listOf(
            "Felicidades: acabas de hacer lo que tu sofá juraba que era imposible.",
            "Otro ejercicio terminado. Tu yo de la semana pasada está confundido y un poco celoso.",
            "Excelente. Si sigues así, algún día abrirás un frasco de pepinillos sin pedir ayuda.",
            "Tus músculos están procesando lo que acaba de pasar. Dales un momento, están en shock.",
            "Ejercicio completado. Nadie aplaudió, pero imagina que sí. Fuerte. Con lágrimas.",
            "Listo. Ahora puedes decir \"estoy entrenando\" en una conversación sin mentir.",
            "Ni una excusa en todo el ejercicio. ¿Seguro que te sientes bien?",
            "Terminado. El espejo ya te mira distinto. O es la luz del gimnasio, no sé.",
            "Tu entrenador imaginario está orgulloso. El real también, pero jamás lo admitirá.",
            "Increíble. Tu fuerza de voluntad acaba de superar a la del lunes por la mañana.",
            "Uno menos. Tu motivación pidió vacaciones, pero tú viniste igual. Eso es actitud.",
            "Bien hecho. Las pesas no se levantan solas, y aparentemente tú tampoco te rindes solo.",
            "Completado. Si el sudor fuera dinero, ya estarías pagando la mensualidad del gym.",
            "Ejercicio superado. Tu grupo de WhatsApp no lo sabrá, pero tú sí.",
        ),
        Context.LEGS to listOf(
            "Piernas listas. Las escaleras de mañana ya te esperan con una sonrisa malvada.",
            "Felicidades: mañana sentarte será un deporte extremo.",
            "Día de pierna superado. Ya estás por encima de la mitad del gimnasio, que se lo salta.",
            "Tus rodillas quieren presentar una queja formal. Queja rechazada.",
            "Excelente. Caminarás raro, pero con dignidad.",
            "Pierna terminada. El pantalón ajustado acaba de sentir una amenaza.",
        ),
        Context.PUSH to listOf(
            "Empuje terminado. Tu camiseta ya empieza a ponerse nerviosa.",
            "Pecho y hombros listos. Pronto empujarás tus problemas... por ahora, la puerta del gym.",
            "Buen press. Si alguien se queda trabado en el ascensor, ya sabes quién va primero.",
            "Terminado. Ya puedes cruzar los brazos con más autoridad que antes.",
        ),
        Context.PULL to listOf(
            "Espalda trabajada. No la ves, pero ella sabe perfectamente lo que hiciste.",
            "Remaste tanto que ya deberías tener licencia de capitán.",
            "Tracción completada. Las mochilas pesadas acaban de perder su poder sobre ti.",
            "Listo. Tus dorsales se están abriendo como paraguas en día de lluvia.",
        ),
        Context.ARMS to listOf(
            "Brazos listos. A partir de hoy, arremangarse es obligatorio.",
            "Bíceps terminados. Ya puedes saludar de lejos con total autoridad.",
            "Tríceps completados. Las mangas cortas agradecen tu esfuerzo.",
            "Brazos trabajados. Ahora el control remoto pesa menos. Ciencia.",
        ),
        Context.CORE to listOf(
            "Core completado. El six-pack sigue escondido, pero ya escucha pasos.",
            "Abdomen trabajado. Las galletas del fin de semana tiemblan de miedo.",
            "Listo. Reírte te va a doler mañana, así que evita los memes.",
            "Plancha superada. Cada segundo duró un año, pero sobreviviste.",
        ),
        Context.CARDIO to listOf(
            "Cardio terminado. Tu corazón te lo agradece; tus pulmones siguen deliberando.",
            "Sudaste como en una entrevista de trabajo. Pero esta vez salió bien.",
            "Cardio listo. Si alguien te persigue, ahora tienes ventaja. Un poquito.",
            "Terminado. Quemaste lo equivalente a media galleta. Pero es TU media galleta.",
        ),
        Context.RECORD to listOf(
            "¡Récord personal! Tu versión de la semana pasada acaba de ser despedida.",
            "Nuevo récord. Las pesas ya están hablando mal de ti a tus espaldas.",
            "¡Récord! Guarda esta fecha; tus músculos ya la marcaron en su calendario.",
            "Superaste tu marca. La gravedad está revisando si hubo trampa.",
        ),
        Context.STRUGGLE to listOf(
            "No salieron todas las repeticiones, pero la gravedad también tiene sus días buenos.",
            "Hoy ganó la pesa. Mañana revancha: ella no tiene piernas para huir.",
            "Casi. Y \"casi\" es muchísimo más que lo que hizo el sofá hoy.",
            "Serie difícil. Tranquilo: hasta los superhéroes tienen episodios de relleno.",
        ),
        Context.LAST to listOf(
            "Sesión terminada. Ya puedes volver a tu hábitat natural: el sofá.",
            "¡Último ejercicio! Publica la foto antes de que se te pase el bombeo.",
            "Fin del entrenamiento. Tu cama ya preparó el discurso de bienvenida.",
            "Todo listo por hoy. Hidrátate, come algo y presume con moderación. O sin moderación.",
        ),
    )

    fun contextFor(
        exercise: Exercise,
        isRecord: Boolean = false,
        struggled: Boolean = false,
        isLast: Boolean = false,
    ): Context = when {
        isRecord -> Context.RECORD
        isLast -> Context.LAST
        struggled -> Context.STRUGGLE
        exercise.pattern == MovementPattern.CARDIO -> Context.CARDIO
        exercise.pattern == MovementPattern.CORE -> Context.CORE
        exercise.pattern in setOf(MovementPattern.SQUAT, MovementPattern.HINGE, MovementPattern.LUNGE) ||
            exercise.primaryMuscles.any { it in setOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES) } ->
            Context.LEGS
        exercise.primaryMuscles.any { it in setOf(Muscle.BICEPS, Muscle.TRICEPS, Muscle.FOREARMS) } &&
            exercise.pattern == MovementPattern.ISOLATION -> Context.ARMS
        exercise.pattern in setOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH) -> Context.PUSH
        exercise.pattern in setOf(MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PULL) -> Context.PULL
        else -> Context.GENERIC
    }

    /** Elige una frase del contexto (a veces una genérica) evitando repetir la última mostrada. */
    fun pick(context: Context, random: Random = Random.Default, avoid: String? = null): String {
        val pool = phrases.getValue(context) +
            if (context in setOf(Context.RECORD, Context.LAST)) emptyList() else phrases.getValue(Context.GENERIC)
        val options = pool.filter { it != avoid }.ifEmpty { pool }
        return options[random.nextInt(options.size)]
    }
}
