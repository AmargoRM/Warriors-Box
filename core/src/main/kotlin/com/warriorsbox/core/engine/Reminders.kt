package com.warriorsbox.core.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Recordatorios: los días se guardan como máscara de bits (bit 0 = lunes … bit 6 = domingo).
 */
object Reminders {

    fun maskOf(days: Collection<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }

    fun daysOf(mask: Int): List<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }

    fun isActiveOn(mask: Int, day: DayOfWeek): Boolean = mask and (1 shl (day.value - 1)) != 0

    /** Próximo momento (estrictamente posterior a [now]) que coincide con los días y la hora. */
    fun nextTrigger(now: LocalDateTime, mask: Int, hour: Int, minute: Int): LocalDateTime? {
        if (mask == 0) return null
        val time = LocalTime.of(hour, minute)
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val candidate = LocalDateTime.of(date, time)
            if (candidate.isAfter(now) && isActiveOn(mask, date.dayOfWeek)) return candidate
        }
        return null
    }

    /** Índice de día del plan (0 = lunes … 5 = sábado); domingo = null (descanso). */
    fun planDayIndex(date: LocalDate): Int? = if (date.dayOfWeek == DayOfWeek.SUNDAY) null else date.dayOfWeek.value - 1

    fun dayLabel(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Lun"
        DayOfWeek.TUESDAY -> "Mar"
        DayOfWeek.WEDNESDAY -> "Mié"
        DayOfWeek.THURSDAY -> "Jue"
        DayOfWeek.FRIDAY -> "Vie"
        DayOfWeek.SATURDAY -> "Sáb"
        DayOfWeek.SUNDAY -> "Dom"
    }

    val PLAN_DAY_NAMES = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")
}
