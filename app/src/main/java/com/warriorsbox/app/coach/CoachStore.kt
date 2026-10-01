package com.warriorsbox.app.coach

import com.warriorsbox.core.coach.CoachProtocol
import com.warriorsbox.core.coach.MessageType
import com.warriorsbox.core.coach.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/** Vinculación entre este celular y otro. [role] es el papel de ESTE celular. */
@Serializable
data class CoachLink(
    val channel: String,
    val key: String,
    val role: Role,
    /** Alumno: su propio usuario. Coach: el usuario local que representa al alumno. */
    val userId: Long,
    /** Nombre de la otra persona (vacío mientras el coach no escanee el QR). */
    val peerName: String = "",
    val createdAt: Long = 0,
    val active: Boolean = true,
)

/** Mensaje pendiente de enviar. El contenido (sin cifrar) está en blobs/out-<id>.json. */
@Serializable
data class Outgoing(
    val id: String,
    val channel: String,
    val type: MessageType,
    val planUuid: String? = null,
    /** true = se reenvía cada cierto tiempo hasta que el otro confirme (planes). */
    val resend: Boolean = false,
    val firstSentAt: Long? = null,
    val lastSentAt: Long? = null,
)

/** Plan recibido que el alumno todavía no aceptó. El contenido está en blobs/in-<planUuid>.json. */
@Serializable
data class PendingPlan(
    val planUuid: String,
    val channel: String,
    val userId: Long,
    val coachName: String,
    val receivedAt: Long,
    val exerciseCount: Int,
    val trainingDays: Int,
)

enum class SentStatus(val label: String) {
    SENDING("Enviado. Le llega cuando abra la app"),
    RECEIVED("Le llegó; falta que lo acepte"),
    ACCEPTED("Lo aceptó ✓"),
    REJECTED("Lo rechazó"),
    FAILED("No se pudo entregar"),
}

@Serializable
data class SentPlan(val planUuid: String, val channel: String, val userId: Long, val sentAt: Long, val status: SentStatus)

@Serializable
data class CoachAlert(val id: String, val userId: Long, val title: String, val text: String, val at: Long, val seen: Boolean = false)

/** Plan local que vino de un coach (para avisarle si el alumno lo cambia). */
@Serializable
data class CoachPlanRef(val planId: Long, val channel: String, val coachName: String)

@Serializable
data class CoachState(
    /** Nombre con el que este celular firma como coach. */
    val myName: String = "",
    val links: List<CoachLink> = emptyList(),
    val outbox: List<Outgoing> = emptyList(),
    val inbox: List<PendingPlan> = emptyList(),
    val sent: List<SentPlan> = emptyList(),
    val alerts: List<CoachAlert> = emptyList(),
    val coachPlans: List<CoachPlanRef> = emptyList(),
    /** Mensajes del buzón ya procesados (ids de ntfy y de mensaje), para no repetirlos. */
    val seenRelay: List<String> = emptyList(),
    val seenMessages: List<String> = emptyList(),
) {
    fun linkOf(userId: Long): CoachLink? = links.firstOrNull { it.userId == userId && it.active }
    fun lastSent(userId: Long): SentPlan? = sent.filter { it.userId == userId }.maxByOrNull { it.sentAt }
    fun coachPlan(planId: Long?): CoachPlanRef? = coachPlans.firstOrNull { it.planId == planId }
}

/**
 * Guarda el estado del modo coach en un archivo JSON propio (no en la base de datos, así no hace
 * falta migrarla). Se escribe primero a un temporal y luego se renombra, para no dejarlo a medias.
 */
class CoachStore(val dir: File) {

    private val file = File(dir, "estado.json")
    private val blobs = File(dir, "blobs")
    private val mutex = Mutex()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<CoachState> = _state.asStateFlow()

    private fun load(): CoachState = runCatching {
        CoachProtocol.json.decodeFromString(CoachState.serializer(), file.readText())
    }.getOrDefault(CoachState())

    suspend fun update(change: (CoachState) -> CoachState): CoachState = mutex.withLock {
        val next = change(_state.value)
        if (next != _state.value) {
            withContext(Dispatchers.IO) {
                dir.mkdirs()
                val tmp = File(dir, "estado.json.tmp")
                tmp.writeText(CoachProtocol.json.encodeToString(CoachState.serializer(), next))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }
            _state.value = next
        }
        next
    }

    suspend fun writeBlob(name: String, text: String) = withContext(Dispatchers.IO) {
        blobs.mkdirs()
        File(blobs, name).writeText(text)
    }

    suspend fun readBlob(name: String): String? = withContext(Dispatchers.IO) {
        File(blobs, name).takeIf { it.exists() }?.readText()
    }

    suspend fun deleteBlob(name: String) = withContext(Dispatchers.IO) { File(blobs, name).delete() }
}
