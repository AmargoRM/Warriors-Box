package com.warriorsbox.app

import com.warriorsbox.app.coach.NtfyRelay
import com.warriorsbox.core.coach.CoachMessage
import com.warriorsbox.core.coach.CoachPlan
import com.warriorsbox.core.coach.CoachProtocol
import com.warriorsbox.core.coach.MessageType
import com.warriorsbox.core.coach.Role
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Prueba contra el ntfy.sh REAL: publica un mensaje chico y uno grande (adjunto) en un buzón
 * aleatorio, los recoge y los descifra. Si no hay internet (o ntfy.sh no responde), se omite.
 */
class NtfyLiveTest {

    @Test
    fun publishPollAndDownloadThroughRealNtfy() = runBlocking {
        val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
        val relay = NtfyRelay(http)
        val channel = CoachProtocol.newChannel()
        val key = CoachProtocol.newKey()
        val small = CoachMessage(type = MessageType.HELLO, from = Role.COACH, name = "Prueba")
        // Un plan con "fotos" que no se comprimen, para forzar el adjunto.
        val noise = ByteArray(6000).also { java.security.SecureRandom().nextBytes(it) }
        val big = CoachMessage(
            type = MessageType.PLAN, from = Role.COACH, planUuid = "p1",
            plan = CoachPlan(days = emptyList(), photos = mapOf("foto.jpg" to java.util.Base64.getEncoder().encodeToString(noise))),
        )
        val bigSealed = CoachProtocol.seal(key, big)
        assumeTrue(bigSealed.length > CoachProtocol.MAX_INLINE)
        try {
            relay.publish(channel, CoachProtocol.seal(key, small))
            relay.publish(channel, bigSealed)
        } catch (e: IOException) {
            assumeTrue("ntfy.sh no disponible: ${e.message}", false)
        }
        // ntfy.sh guarda los mensajes en su caché por tandas: puede tardar unos segundos en devolverlos.
        var messages = relay.poll(channel)
        repeat(10) {
            if (messages.size >= 2) return@repeat
            kotlinx.coroutines.delay(2_000)
            messages = relay.poll(channel)
        }
        assertEquals("respuesta de ntfy: $messages", 2, messages.size)
        val opened = messages.map { m ->
            val text = if (m.attachmentUrl != null) relay.download(m.attachmentUrl!!) else m.text!!
            CoachProtocol.open(key, text)
        }
        assertEquals("abiertos: ${opened.map { it?.type }}", listOf(small, big), opened)
    }
}
