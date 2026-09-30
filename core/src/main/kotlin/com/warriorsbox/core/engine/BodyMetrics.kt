package com.warriorsbox.core.engine

import java.time.LocalDate
import java.time.Period
import java.util.Locale

object BodyMetrics {

    fun age(birthDate: LocalDate, today: LocalDate = LocalDate.now()): Int =
        Period.between(birthDate, today).years.coerceAtLeast(0)

    fun bmi(weightKg: Double, heightCm: Double): Double? {
        if (weightKg <= 0 || heightCm <= 0) return null
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    fun bmiCategory(bmi: Double): String = when {
        bmi < 18.5 -> "Bajo peso"
        bmi < 25.0 -> "Normal"
        bmi < 30.0 -> "Sobrepeso"
        else -> "Obesidad"
    }

    const val BMI_DISCLAIMER =
        "El IMC no distingue músculo de grasa: en personas musculosas puede marcar \"sobrepeso\" sin serlo."

    fun format(value: Double, decimals: Int = 1): String = String.format(Locale.US, "%.${decimals}f", value)
}

object Units {
    const val LB_PER_KG = 2.2046226218
    const val CM_PER_INCH = 2.54

    fun kgToLb(kg: Double) = kg * LB_PER_KG
    fun lbToKg(lb: Double) = lb / LB_PER_KG
    fun cmToInches(cm: Double) = cm / CM_PER_INCH
    fun inchesToCm(inches: Double) = inches * CM_PER_INCH

    /** 170 cm → "5' 7\"" */
    fun cmToFeetText(cm: Double): String {
        val totalInches = Math.round(cmToInches(cm)).toInt()
        return "${totalInches / 12}' ${totalInches % 12}\""
    }

    fun formatWeight(kg: Double, useLb: Boolean): String {
        val v = Math.round((if (useLb) kgToLb(kg) else kg) * 10) / 10.0
        val unit = if (useLb) "lb" else "kg"
        val text = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
        return "$text $unit"
    }

    /** Muestra segundos como "45 s" o "12 min". */
    fun formatDuration(seconds: Int): String = when {
        seconds >= 120 && seconds % 60 == 0 -> "${seconds / 60} min"
        seconds >= 120 -> "${seconds / 60} min ${seconds % 60} s"
        else -> "$seconds s"
    }
}
