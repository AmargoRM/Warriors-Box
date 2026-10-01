package com.warriorsbox.core.coach

import com.warriorsbox.core.model.Exercise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Modo coach a distancia: dos celulares se vinculan con un QR y se mandan mensajes
 * cifrados a través de un buzón público (ntfy.sh). El buzón solo ve texto ilegible:
 * la llave viaja únicamente dentro del QR.
 */
object CoachProtocol {

    const val INVITE_PREFIX = "WB1."

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val random = SecureRandom()
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val b64Decoder = Base64.getUrlDecoder()

    // ------------------------------------------------------------------ vinculación

    /** Nombre del buzón: largo y aleatorio, imposible de adivinar. Solo letras, números y guiones. */
    fun newChannel(): String = "wb-" + b64.encodeToString(ByteArray(18).also(random::nextBytes)).replace('_', '-')

    /** Llave AES de 256 bits. */
    fun newKey(): String = b64.encodeToString(ByteArray(32).also(random::nextBytes))

    fun newId(): String = UUID.randomUUID().toString()

    /** Texto del QR (y del código para compartir): "WB1." + invitación comprimida. */
    fun encodeInvite(invite: Invite): String {
        val raw = json.encodeToString(Invite.serializer(), invite).toByteArray()
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(raw) }
        return INVITE_PREFIX + b64.encodeToString(out.toByteArray())
    }

    /** Acepta el código solo o dentro de un mensaje de WhatsApp. Devuelve null si no es válido. */
    fun decodeInvite(text: String): Invite? = runCatching {
        val token = Regex("""WB1\.[A-Za-z0-9_-]+""").find(text)?.value ?: return null
        val bytes = b64Decoder.decode(token.removePrefix(INVITE_PREFIX))
        val raw = InflaterInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        json.decodeFromString(Invite.serializer(), raw.decodeToString())
            .takeIf { it.channel.matches(Regex("[A-Za-z0-9_-]{8,64}")) && b64Decoder.decode(it.key).size == 32 }
    }.getOrNull()

    // ------------------------------------------------------------------ cifrado

    /** Comprime y cifra con AES-256-GCM. Resultado: base64(iv ‖ cifrado). */
    fun seal(key: String, message: CoachMessage): String {
        val raw = json.encodeToString(CoachMessage.serializer(), message).toByteArray()
        val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(raw) } }.toByteArray()
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(b64Decoder.decode(key), "AES"), GCMParameterSpec(128, iv))
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(zipped))
    }

    /** Descifra; devuelve null si la llave no corresponde o el texto fue alterado. */
    fun open(key: String, sealed: String): CoachMessage? = runCatching {
        val bytes = Base64.getDecoder().decode(sealed.trim())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(b64Decoder.decode(key), "AES"), GCMParameterSpec(128, bytes, 0, 12))
        val zipped = cipher.doFinal(bytes, 12, bytes.size - 12)
        val raw = GZIPInputStream(ByteArrayInputStream(zipped)).use { it.readBytes() }
        json.decodeFromString(CoachMessage.serializer(), raw.decodeToString())
    }.getOrNull()

    // ------------------------------------------------------------------ ntfy

    /** Una línea de la respuesta de ntfy (`/json?poll=1`). */
    data class RelayMessage(val id: String, val time: Long, val text: String?, val attachmentUrl: String?)

    /** Lee la respuesta de ntfy: un objeto JSON por línea. Ignora líneas que no sean mensajes. */
    fun parseNtfy(body: String): List<RelayMessage> = body.lineSequence().mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        runCatching {
            val o = json.parseToJsonElement(line) as JsonObject
            if (o.str("event") != "message") return@runCatching null
            val attachment = o["attachment"] as? JsonObject
            RelayMessage(
                id = o.str("id") ?: return@runCatching null,
                time = o.str("time")?.toLongOrNull() ?: 0,
                text = o.str("message"),
                attachmentUrl = attachment?.str("url"),
            )
        }.getOrNull()
    }.toList()

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content

    /** Los mensajes chicos van en el cuerpo; los grandes (planes con fotos) como archivo adjunto. */
    const val MAX_INLINE = 3500

    /** Reenvío automático de un plan mientras el alumno no confirme (los adjuntos de ntfy.sh duran 3 h). */
    const val RESEND_EVERY_MS = 150 * 60 * 1000L

    /** Después de esto se deja de reenviar y se avisa al coach. */
    const val GIVE_UP_AFTER_MS = 14 * 24 * 3600 * 1000L
}

/** Lo que lleva el QR. [profile] es el perfil del alumno (campos del usuario y sus lesiones). */
@Serializable
data class Invite(
    val channel: String,
    val key: String,
    val name: String,
    val profile: JsonObject? = null,
)

enum class Role { COACH, STUDENT }

enum class MessageType {
    /** Coach → alumno, al escanear el QR. */
    HELLO,

    /** Coach → alumno: plan completo. */
    PLAN,

    /** Alumno → coach: el plan llegó al celular. */
    RECEIVED,

    /** Alumno → coach. */
    ACCEPTED,
    REJECTED,

    /** Alumno → coach: cambios que hizo en el plan. */
    CHANGED,

    /** Cualquiera: se terminó la vinculación. */
    UNLINK,
}

@Serializable
data class CoachMessage(
    val id: String = CoachProtocol.newId(),
    val type: MessageType,
    val from: Role,
    val sentAt: Long = System.currentTimeMillis(),
    val name: String = "",
    val planUuid: String? = null,
    val plan: CoachPlan? = null,
    val lines: List<String> = emptyList(),
)

@Serializable
data class CoachPlan(
    val summary: String = "",
    val days: List<Day>,
    /** Ejercicios que el otro celular quizás no tiene (propios o sincronizados). */
    val exercises: List<Exercise> = emptyList(),
    /** Fotos de esos ejercicios: nombre de archivo → base64. */
    val photos: Map<String, String> = emptyMap(),
) {
    @Serializable
    data class Day(
        val week: Int,
        val dayIndex: Int,
        val focus: String,
        val optional: Boolean,
        val deload: Boolean,
        val items: List<Item>,
    )

    @Serializable
    data class Item(
        val exerciseId: String,
        val sets: Int,
        val repsMin: Int,
        val repsMax: Int,
        val targetReps: Int,
        val weightKg: Double,
        val restSec: Int,
        val note: String? = null,
    )

    val exerciseCount: Int get() = days.sumOf { it.items.size }
}
