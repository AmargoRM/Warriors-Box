package com.warriorsbox.core.engine

import kotlin.random.Random

/**
 * "Modo sin filtro": recordatorios groseros, en jerga tica, para quien los active en su perfil.
 * Vienen apagados; cada usuario decide si los quiere.
 * {dia} = lo que toca hoy (ej. "Pierna"), {n} = días sin entrenar.
 */
object Roasts {

    val reminders = listOf(
        "Vamos gordo carepicha, esas pesas no se van a levantar solas.",
        "Mae, ¿otra vez pegado al sillón? Levantá ese culo, que hoy toca {dia}.",
        "Hoy toca {dia}. Movete, gordo, que el pantalón ya está pidiendo auxilio.",
        "Carepicha, el gimnasio te extraña. Bueno, no tanto, pero andá igual.",
        "Dejá de comer como si se acabara el mundo y andá a sudar, mae. Toca {dia}.",
        "Tu panza es más constante que vos. Hoy toca {dia}, no seas vago.",
        "¿Vas a entrenar o vas a seguir de adorno en el sillón, carepicha?",
        "Arriba, gordo. Si esto fuera un casado ya estarías ahí sentado.",
        "Mae, hasta la mancuerna de 2 kilos se ríe de vos. Andá a callarla. Toca {dia}.",
        "Hoy no hay excusa, carepicha: ni lluvia, ni sueño, ni pereza. {dia}.",
        "Las lonjas no se van con memes, gordo. Hoy toca {dia}.",
        "Movete, vago. El único six-pack que tenés es de birras.",
        "Diay, ¿y el cuerpo de verano? Ni de este verano ni del otro. Toca {dia}.",
        "Gordo carepicha, soltá el celular y agarrá la barra. {dia} te espera.",
        "Si sudar fuera pecado, vos serías un santo. Andá a pecar un rato: {dia}.",
    )

    val missed = listOf(
        "{n} días sin entrenar, carepicha. El sillón ya tiene la forma de tu culo.",
        "Mae, llevás {n} días de vacaciones. ¿Te jubilaste o qué?",
        "{n} días sin ir, gordo. Las pesas ya te dieron por muerto.",
        "{n} días sin mover el culo. Hasta tu sombra hace más ejercicio que vos.",
        "Carepicha, {n} días sin gimnasio. El entrenador ya puso tu foto en \"se busca\".",
    )

    fun fill(template: String, day: String? = null, days: Long? = null): String =
        template.replace("{dia}", day ?: "entrenar").replace("{n}", (days ?: 2).toString())

    fun reminder(day: String?, random: Random = Random.Default): String = fill(reminders[random.nextInt(reminders.size)], day = day)

    fun missed(days: Long, random: Random = Random.Default): String = fill(missed[random.nextInt(missed.size)], days = days)
}
