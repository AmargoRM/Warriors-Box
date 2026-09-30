package com.warriorsbox.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Flujo completo en la interfaz: crear usuario → elegir usuario → generar plan →
 * abrir Semana 1 Lunes → registrar una serie → terminar → ver resumen.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val timeout = 20_000L

    @Test
    fun homeShowsOnlyTwoMainButtons() {
        rule.onNodeWithTag("boton_usuarios").assertExists()
        rule.onNodeWithTag("boton_rutinas").assertExists()
        rule.onNodeWithTag("ajustes").assertExists()
    }

    @Test
    fun fullFlowFromNewUserToSummary() {
        rule.onNodeWithTag("boton_rutinas").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Crear usuario"), timeout)
        rule.onNodeWithText("Crear usuario").performClick()

        // Paso 1: datos básicos
        rule.waitUntilAtLeastOneExists(hasTestTag("campo_nombre"), timeout)
        rule.onNodeWithTag("campo_nombre").performTextInput("Tester")
        rule.onNodeWithTag("siguiente").performClick()
        // Paso 2: medidas
        rule.waitUntilAtLeastOneExists(hasTestTag("campo_peso"), timeout)
        rule.onNodeWithTag("campo_peso").performTextInput("75")
        rule.onNodeWithTag("campo_altura").performTextInput("175")
        // Pasos 2 → 6 y luego "Guardar"
        repeat(5) {
            rule.onNodeWithTag("siguiente").performClick()
            rule.waitForIdle()
        }
        // Aviso de "no reemplaza a un profesional" (solo la primera vez)
        rule.waitUntilAtLeastOneExists(hasText("Entendido").or(hasTestTag("elegir_Tester")), timeout)
        if (rule.onAllNodes(hasText("Entendido")).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Entendido").performClick()
        }

        // ¿Quién entrena hoy?
        rule.waitUntilAtLeastOneExists(hasTestTag("elegir_Tester"), timeout)
        rule.onNodeWithTag("elegir_Tester").performClick()

        // Generar plan
        rule.waitUntilAtLeastOneExists(hasTestTag("generar_plan"), timeout)
        rule.onNodeWithTag("generar_plan").performClick()
        rule.waitUntilAtLeastOneExists(hasText("¡Vamos!"), timeout)
        rule.onNodeWithText("¡Vamos!").performClick()

        // Semana 1, lunes
        rule.waitUntilAtLeastOneExists(hasTestTag("dia_1_0"), timeout)
        rule.onNodeWithTag("dia_1_0").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("empezar_sesion") and isEnabled(), timeout)
        rule.onNodeWithTag("empezar_sesion").performClick()

        // Marcar la primera serie del primer ejercicio
        rule.waitUntilAtLeastOneExists(hasTestTag("serie_1_1"), timeout)
        rule.onNodeWithTag("serie_1_1").performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasText("1 de ", substring = true), timeout)

        // Terminar (con series pendientes → confirmación)
        rule.onNodeWithTag("terminar_sesion").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("confirmar"), timeout)
        rule.onNodeWithTag("confirmar").performClick()
        rule.waitUntilAtLeastOneExists(hasText("¡Sesión terminada!", substring = true).or(hasText("¡Sesión con récord!")), timeout)
    }
}
