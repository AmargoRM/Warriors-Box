package com.warriorsbox.core

import com.warriorsbox.core.coach.CoachMessage
import com.warriorsbox.core.coach.CoachPlan
import com.warriorsbox.core.coach.CoachProtocol
import com.warriorsbox.core.coach.Invite
import com.warriorsbox.core.coach.MessageType
import com.warriorsbox.core.coach.Role
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachProtocolTest {

    private fun samplePlan(): CoachPlan = CoachPlan(
        summary = "Plan de prueba",
        days = (1..5).flatMap { w ->
            (0 until 6).map { d ->
                CoachPlan.Day(w, d, "Día $d", optional = d == 5, deload = w == 5, items = (0 until 6).map {
                    CoachPlan.Item("press-banca-$it", 4, 8, 12, 8, 42.5, 90, note = "Codos a 45°")
                })
            }
        },
    )

    @Test
    fun channelsAndKeysAreRandomAndValidTopics() {
        val a = CoachProtocol.newChannel()
        val b = CoachProtocol.newChannel()
        assertNotEquals(a, b)
        assertTrue(a, a.matches(Regex("[A-Za-z0-9-]{20,64}")))
        assertNotEquals(CoachProtocol.newKey(), CoachProtocol.newKey())
    }

    @Test
    fun inviteRoundTripAlsoInsideAWhatsappMessage() {
        val profile = buildJsonObject { put("name", JsonPrimitive("Ana")); put("weightKg", JsonPrimitive(61.5)) }
        val invite = Invite(CoachProtocol.newChannel(), CoachProtocol.newKey(), "Ana", profile)
        val code = CoachProtocol.encodeInvite(invite)
        assertTrue(code.startsWith("WB1."))
        // Debe caber holgado en un QR (máximo ~2900 bytes).
        assertTrue("código de ${code.length} caracteres", code.length < 1200)
        assertEquals(invite, CoachProtocol.decodeInvite(code))
        assertEquals(invite, CoachProtocol.decodeInvite("Hola! Mi código de Warriors Box: $code\nAbrilo en la app."))
        assertNull(CoachProtocol.decodeInvite("hola"))
        assertNull(CoachProtocol.decodeInvite("WB1.basura"))
    }

    @Test
    fun sealAndOpen() {
        val key = CoachProtocol.newKey()
        val msg = CoachMessage(type = MessageType.PLAN, from = Role.COACH, name = "Coach Rafa", planUuid = "p1", plan = samplePlan())
        val sealed = CoachProtocol.seal(key, msg)
        assertEquals(msg, CoachProtocol.open(key, sealed))
        // El texto cifrado no revela nada del contenido.
        assertTrue(!sealed.contains("Rafa") && !sealed.contains("press"))
        // Otra llave o un texto alterado no se pueden abrir.
        assertNull(CoachProtocol.open(CoachProtocol.newKey(), sealed))
        val tampered = sealed.substring(0, 40) + (if (sealed[40] == 'A') 'B' else 'A') + sealed.substring(41)
        assertNull(CoachProtocol.open(key, tampered))
        assertNull(CoachProtocol.open(key, "no es base64 !!"))
        // Un plan completo (30 días × 6 ejercicios) comprimido es chico.
        assertTrue("${sealed.length}", sealed.length < 20_000)
        assertEquals(180, msg.plan!!.exerciseCount)
    }

    @Test
    fun smallMessagesFitInline() {
        val key = CoachProtocol.newKey()
        val sealed = CoachProtocol.seal(key, CoachMessage(type = MessageType.CHANGED, from = Role.STUDENT, lines = listOf("Cambió Press banca por Fondos (semana 2, lunes)")))
        assertTrue(sealed.length < CoachProtocol.MAX_INLINE)
    }

    @Test
    fun parsesNtfyPollResponse() {
        val body = """
            {"id":"abc123","time":1759300000,"event":"message","topic":"wb-x","message":"SEALED=="}
            {"id":"def456","time":1759300100,"event":"message","topic":"wb-x","message":"You received a file: wb.txt","attachment":{"name":"wb.txt","size":5000,"expires":1759310000,"url":"https://ntfy.sh/file/def456.txt"}}
            {"id":"k1","time":1759300200,"event":"keepalive","topic":"wb-x"}
            no-json

        """.trimIndent()
        val list = CoachProtocol.parseNtfy(body)
        assertEquals(2, list.size)
        assertEquals("SEALED==", list[0].text)
        assertNull(list[0].attachmentUrl)
        assertEquals("https://ntfy.sh/file/def456.txt", list[1].attachmentUrl)
        assertEquals(1759300100L, list[1].time)
        assertNotNull(list[1].text)
    }
}
