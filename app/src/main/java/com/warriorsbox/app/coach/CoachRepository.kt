package com.warriorsbox.app.coach

import com.warriorsbox.app.data.ExerciseRepository
import com.warriorsbox.app.data.PhotoStore
import com.warriorsbox.app.data.PlanRepository
import com.warriorsbox.app.data.UserRepository
import com.warriorsbox.app.data.db.InjuryEntity
import com.warriorsbox.app.data.db.UserEntity
import com.warriorsbox.core.coach.CoachMessage
import com.warriorsbox.core.coach.CoachProtocol
import com.warriorsbox.core.coach.Invite
import com.warriorsbox.core.coach.MessageType
import com.warriorsbox.core.coach.Role
import com.warriorsbox.core.model.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.util.Base64

/** Algo que merece una notificación en el celular. */
sealed interface CoachEvent {
    data class NewPlan(val coachName: String, val userName: String) : CoachEvent
    data class Alert(val title: String, val text: String) : CoachEvent
}

/**
 * Modo coach a distancia.
 *
 * Alumno: crea una invitación (QR) → recibe planes → los acepta o rechaza → si los cambia, se avisa al coach.
 * Coach: escanea el QR (crea al alumno en su celular) → arma el plan → lo envía → recibe confirmaciones y avisos.
 */
class CoachRepository(
    private val store: CoachStore,
    private val relay: Relay,
    private val users: UserRepository,
    private val plans: PlanRepository,
    private val exercises: ExerciseRepository,
    private val photos: PhotoStore,
    private val onEvent: (CoachEvent) -> Unit = {},
    /** Pide una sincronización en segundo plano dentro de N segundos (con internet). */
    private val requestSync: (delaySeconds: Long) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val state: StateFlow<CoachState> = store.state
    private val syncMutex = Mutex()
    private val profileJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    init {
        plans.changeListener = { planId, change -> onPlanChanged(planId, change) }
    }

    fun linkOf(userId: Long): CoachLink? = state.value.linkOf(userId)

    suspend fun setMyName(name: String) {
        store.update { it.copy(myName = name.trim()) }
    }

    // ------------------------------------------------------------------ alumno: invitación

    /** Código para el QR. Si el usuario ya tiene una vinculación, se reutiliza. */
    suspend fun createInvite(userId: Long): String {
        val user = users.get(userId) ?: error("Usuario no encontrado")
        val link = linkOf(userId)?.also { require(it.role == Role.STUDENT) { "Este usuario es un alumno a distancia." } }
            ?: CoachLink(CoachProtocol.newChannel(), CoachProtocol.newKey(), Role.STUDENT, userId, createdAt = clock()).also { l ->
                store.update { it.copy(links = it.links + l) }
            }
        return CoachProtocol.encodeInvite(Invite(link.channel, link.key, user.name, profileOf(user, users.injuriesNow(userId))))
    }

    private fun profileOf(user: UserEntity, injuries: List<InjuryEntity>): JsonObject {
        val clean = user.copy(id = 0, photoPath = null, backgroundPath = null, emergencyContact = null, createdAt = 0)
        val base = profileJson.encodeToJsonElement(UserEntity.serializer(), clean).jsonObject
        val list = profileJson.encodeToJsonElement(ListSerializer(InjuryEntity.serializer()), injuries.map { it.copy(id = 0, userId = 0) })
        return JsonObject(base + ("lesiones" to list))
    }

    // ------------------------------------------------------------------ coach: escanear

    /** El coach escanea (o pega) el código del alumno. Crea al alumno en este celular y devuelve su id. */
    suspend fun acceptInvite(code: String): Long {
        val invite = CoachProtocol.decodeInvite(code) ?: error("El código no es válido. Pídele al alumno que te lo vuelva a mostrar.")
        state.value.links.firstOrNull { it.channel == invite.channel && it.active }?.let { existing ->
            require(existing.role == Role.COACH) { "Ese código es de este mismo celular." }
            return existing.userId
        }
        val profile = invite.profile
        val user = profile?.let {
            runCatching { profileJson.decodeFromJsonElement(UserEntity.serializer(), JsonObject(it - "lesiones")) }.getOrNull()
        } ?: UserEntity(name = invite.name)
        val injuries = (profile?.get("lesiones") as? JsonArray)?.let {
            runCatching { profileJson.decodeFromJsonElement(ListSerializer(InjuryEntity.serializer()), it) }.getOrNull()
        }.orEmpty()
        val userId = users.save(user.copy(id = 0, name = invite.name, createdAt = clock()), injuries)
        val link = CoachLink(invite.channel, invite.key, Role.COACH, userId, peerName = invite.name, createdAt = clock())
        store.update { it.copy(links = it.links + link) }
        enqueue(link, CoachMessage(type = MessageType.HELLO, from = Role.COACH, name = coachName()))
        runCatching { flushOutbox() }
        return userId
    }

    private fun coachName(): String = state.value.myName.ifBlank { "Tu coach" }

    // ------------------------------------------------------------------ coach: enviar plan

    /** Envía el plan activo del alumno a distancia. Devuelve el id del envío. */
    suspend fun sendPlan(userId: Long): String {
        val link = linkOf(userId)?.takeIf { it.role == Role.COACH } ?: error("Este usuario no está vinculado como alumno a distancia.")
        val plan = plans.activePlanNow(userId) ?: error("Todavía no hay plan para enviar.")
        val coachPlan = plans.toCoachPlan(plan.id)
        require(coachPlan.exerciseCount > 0) { "El plan no tiene ejercicios." }
        // Ejercicios que el otro celular quizás no tiene (propios o bajados de internet), con sus fotos.
        val ids = coachPlan.days.flatMap { d -> d.items.map { it.exerciseId } }.toSet()
        val extra = ids.filterNot { exercises.isBuiltIn(it) }.mapNotNull { exercises.get(it) }
        val photoData = withContext(Dispatchers.IO) {
            extra.flatMap { exercises.localPhotoFiles(it) }.associate { it.name to Base64.getEncoder().encodeToString(it.readBytes()) }
        }
        val uuid = CoachProtocol.newId()
        val message = CoachMessage(
            type = MessageType.PLAN, from = Role.COACH, name = coachName(), planUuid = uuid,
            plan = coachPlan.copy(exercises = extra, photos = photoData),
        )
        // Un plan nuevo reemplaza al que estaba esperando entrega.
        state.value.outbox.filter { it.channel == link.channel && it.type == MessageType.PLAN }.forEach { store.deleteBlob(outName(it.id)) }
        store.update { s ->
            s.copy(
                outbox = s.outbox.filterNot { it.channel == link.channel && it.type == MessageType.PLAN },
                sent = s.sent + SentPlan(uuid, link.channel, userId, clock(), SentStatus.SENDING),
            )
        }
        enqueue(link, message, resend = true)
        runCatching { flushOutbox() }.onFailure { requestSync(0) }
        return uuid
    }

    // ------------------------------------------------------------------ alumno: aceptar o rechazar

    suspend fun acceptPlan(planUuid: String): Long {
        val pending = state.value.inbox.firstOrNull { it.planUuid == planUuid } ?: error("El plan ya no está disponible.")
        val text = store.readBlob(inName(planUuid)) ?: error("El plan ya no está disponible.")
        val message = CoachProtocol.json.decodeFromString(CoachMessage.serializer(), text)
        val plan = message.plan ?: error("El mensaje no trae un plan.")
        importExercises(plan.exercises, plan.photos)
        val planId = plans.importCoachPlan(pending.userId, plan, pending.coachName)
        store.deleteBlob(inName(planUuid))
        store.update { s ->
            s.copy(
                inbox = s.inbox.filterNot { it.planUuid == planUuid },
                coachPlans = s.coachPlans + CoachPlanRef(planId, pending.channel, pending.coachName),
            )
        }
        state.value.links.firstOrNull { it.channel == pending.channel && it.active }?.let {
            enqueue(it, CoachMessage(type = MessageType.ACCEPTED, from = Role.STUDENT, planUuid = planUuid))
            runCatching { flushOutbox() }.onFailure { requestSync(0) }
        }
        return planId
    }

    suspend fun rejectPlan(planUuid: String) {
        val pending = state.value.inbox.firstOrNull { it.planUuid == planUuid } ?: return
        store.deleteBlob(inName(planUuid))
        store.update { s -> s.copy(inbox = s.inbox.filterNot { it.planUuid == planUuid }) }
        state.value.links.firstOrNull { it.channel == pending.channel && it.active }?.let {
            enqueue(it, CoachMessage(type = MessageType.REJECTED, from = Role.STUDENT, planUuid = planUuid))
            runCatching { flushOutbox() }.onFailure { requestSync(0) }
        }
    }

    /** Guarda los ejercicios propios del coach (con sus fotos) en este celular. */
    private suspend fun importExercises(list: List<Exercise>, photoData: Map<String, String>) {
        if (list.isEmpty()) return
        val saved = withContext(Dispatchers.IO) {
            photoData.mapNotNull { (name, data) ->
                val safe = File(name).name.takeIf { it.isNotBlank() && !it.contains("..") } ?: return@mapNotNull null
                val file = File(photos.dir, "coach-$safe")
                runCatching { file.writeBytes(Base64.getDecoder().decode(data)) }.getOrNull() ?: return@mapNotNull null
                safe to "file://" + file.absolutePath
            }.toMap()
        }
        list.forEach { e ->
            val urls = e.imageUrls.map { url ->
                if (url.startsWith("file:")) saved[File(url.removePrefix("file://")).name] ?: url else url
            }.filter { !it.startsWith("file:") || File(it.removePrefix("file://")).exists() }
            val copy = e.copy(imageUrls = urls)
            if (e.custom) exercises.saveCustom(copy) else exercises.saveSynced(listOf(copy), e.source)
        }
    }

    // ------------------------------------------------------------------ alumno: cambios al plan del coach

    private suspend fun onPlanChanged(planId: Long, change: String) {
        val ref = state.value.coachPlan(planId) ?: return
        val link = state.value.links.firstOrNull { it.channel == ref.channel && it.active && it.role == Role.STUDENT } ?: return
        // Los cambios seguidos se juntan en un solo aviso mientras no se haya enviado.
        val pending = state.value.outbox.firstOrNull { it.channel == link.channel && it.type == MessageType.CHANGED && it.lastSentAt == null }
        val old = pending?.let { store.readBlob(outName(it.id)) }?.let { CoachProtocol.json.decodeFromString(CoachMessage.serializer(), it) }
        if (pending != null && old != null) {
            store.writeBlob(outName(pending.id), CoachProtocol.json.encodeToString(CoachMessage.serializer(), old.copy(lines = (old.lines + change).takeLast(60))))
        } else {
            val user = users.get(link.userId)
            enqueue(link, CoachMessage(type = MessageType.CHANGED, from = Role.STUDENT, name = user?.name.orEmpty(), lines = listOf(change)))
        }
        // Se espera un minuto por si sigue editando, y se manda todo junto.
        requestSync(60)
    }

    // ------------------------------------------------------------------ desvincular

    suspend fun unlink(userId: Long) {
        val link = linkOf(userId) ?: return
        enqueue(link, CoachMessage(type = MessageType.UNLINK, from = link.role, name = if (link.role == Role.COACH) coachName() else users.get(userId)?.name.orEmpty()))
        runCatching { flushOutbox() }
        store.update { s -> s.copy(links = s.links.map { if (it.channel == link.channel) it.copy(active = false) else it }) }
    }

    /** Al borrar un usuario: se avisa al otro celular y se limpia lo pendiente. */
    suspend fun onUserDeleted(userId: Long) {
        runCatching { unlink(userId) }
        state.value.inbox.filter { it.userId == userId }.forEach { store.deleteBlob(inName(it.planUuid)) }
        store.update { s ->
            s.copy(
                links = s.links.map { if (it.userId == userId) it.copy(active = false) else it },
                inbox = s.inbox.filterNot { it.userId == userId },
                sent = s.sent.filterNot { it.userId == userId },
                alerts = s.alerts.filterNot { it.userId == userId },
            )
        }
    }

    suspend fun markAlertsSeen() {
        store.update { s -> s.copy(alerts = s.alerts.map { it.copy(seen = true) }) }
    }

    // ------------------------------------------------------------------ sincronizar

    data class SyncResult(val received: Int, val sent: Int, val offline: Boolean)

    /** Envía lo pendiente y recoge lo nuevo de todos los buzones. Seguro de llamar seguido. */
    suspend fun sync(): SyncResult = syncMutex.withLock {
        if (state.value.links.none { it.active } && state.value.outbox.isEmpty()) return@withLock SyncResult(0, 0, false)
        val sent = try {
            flushOutboxLocked()
        } catch (_: IOException) {
            return@withLock SyncResult(0, 0, true)
        }
        var received = 0
        for (link in state.value.links.filter { it.active }) {
            val messages = try {
                relay.poll(link.channel)
            } catch (_: IOException) {
                return@withLock SyncResult(received, sent, true)
            }
            for (m in messages) {
                if (m.id in state.value.seenRelay) continue
                val sealed = when {
                    m.attachmentUrl != null -> try {
                        relay.download(m.attachmentUrl!!)
                    } catch (_: IOException) {
                        null // adjunto vencido: el coach lo reenvía solo
                    }
                    else -> m.text
                }
                val message = sealed?.let { CoachProtocol.open(link.key, it) }
                val current = state.value.links.firstOrNull { it.channel == link.channel } ?: link
                if (message != null && message.from != link.role && message.id !in state.value.seenMessages) {
                    handle(current, message)
                    received++
                    markSeen(relayId = m.id, messageId = message.id)
                } else {
                    markSeen(relayId = m.id, messageId = null)
                }
            }
        }
        // Lo que se generó al procesar (confirmaciones) sale en la misma pasada.
        val more = runCatching { flushOutboxLocked() }.getOrDefault(0)
        SyncResult(received, sent + more, false)
    }

    private suspend fun markSeen(relayId: String, messageId: String?) {
        store.update { s ->
            s.copy(
                seenRelay = (s.seenRelay + relayId).takeLast(1000),
                seenMessages = if (messageId == null) s.seenMessages else (s.seenMessages + messageId).takeLast(1000),
            )
        }
    }

    private suspend fun handle(link: CoachLink, m: CoachMessage) {
        when (m.type) {
            MessageType.HELLO -> {
                setPeer(link, m.name)
                alert(link.userId, "Coach vinculado", "${m.name} ya es tu coach. Cuando te mande un plan te va a aparecer para aceptarlo.")
            }
            MessageType.PLAN -> {
                val plan = m.plan ?: return
                val uuid = m.planUuid ?: m.id
                setPeer(link, m.name)
                // Si había otro plan del mismo coach sin aceptar, el nuevo lo reemplaza.
                state.value.inbox.filter { it.channel == link.channel }.forEach { store.deleteBlob(inName(it.planUuid)) }
                store.writeBlob(inName(uuid), CoachProtocol.json.encodeToString(CoachMessage.serializer(), m))
                val pending = PendingPlan(
                    planUuid = uuid, channel = link.channel, userId = link.userId, coachName = m.name.ifBlank { link.peerName },
                    receivedAt = clock(), exerciseCount = plan.exerciseCount, trainingDays = plan.days.count { !it.optional && it.items.isNotEmpty() },
                )
                store.update { s -> s.copy(inbox = s.inbox.filterNot { it.channel == link.channel } + pending) }
                enqueue(link, CoachMessage(type = MessageType.RECEIVED, from = Role.STUDENT, planUuid = uuid))
                onEvent(CoachEvent.NewPlan(pending.coachName, users.get(link.userId)?.name.orEmpty()))
            }
            MessageType.RECEIVED, MessageType.ACCEPTED, MessageType.REJECTED -> {
                val uuid = m.planUuid ?: return
                val status = when (m.type) {
                    MessageType.ACCEPTED -> SentStatus.ACCEPTED
                    MessageType.REJECTED -> SentStatus.REJECTED
                    else -> SentStatus.RECEIVED
                }
                // Ya le llegó: se deja de reenviar.
                state.value.outbox.filter { it.planUuid == uuid }.forEach { store.deleteBlob(outName(it.id)) }
                store.update { s ->
                    s.copy(
                        outbox = s.outbox.filterNot { it.planUuid == uuid },
                        sent = s.sent.map {
                            // Un "recibido" que llega tarde no pisa un "aceptado".
                            if (it.planUuid == uuid && (status != SentStatus.RECEIVED || it.status == SentStatus.SENDING)) it.copy(status = status) else it
                        },
                    )
                }
                if (m.type == MessageType.ACCEPTED) alert(link.userId, "Plan aceptado", "${link.peerName} aceptó el plan que le mandaste.")
                if (m.type == MessageType.REJECTED) alert(link.userId, "Plan rechazado", "${link.peerName} rechazó el plan que le mandaste.")
            }
            MessageType.CHANGED -> alert(
                link.userId, "${link.peerName} cambió su plan",
                m.lines.joinToString("\n") { "• $it" },
            )
            MessageType.UNLINK -> {
                store.update { s -> s.copy(links = s.links.map { if (it.channel == link.channel) it.copy(active = false) else it }) }
                alert(link.userId, "Vinculación terminada", "${m.name.ifBlank { link.peerName }} terminó la vinculación.")
            }
        }
    }

    private suspend fun setPeer(link: CoachLink, name: String) {
        if (name.isBlank() || name == link.peerName) return
        store.update { s -> s.copy(links = s.links.map { if (it.channel == link.channel) it.copy(peerName = name) else it }) }
    }

    private suspend fun alert(userId: Long, title: String, text: String) {
        store.update { s -> s.copy(alerts = (s.alerts + CoachAlert(CoachProtocol.newId(), userId, title, text, clock())).takeLast(100)) }
        onEvent(CoachEvent.Alert(title, text))
    }

    // ------------------------------------------------------------------ bandeja de salida

    private suspend fun enqueue(link: CoachLink, message: CoachMessage, resend: Boolean = false) {
        store.writeBlob(outName(message.id), CoachProtocol.json.encodeToString(CoachMessage.serializer(), message))
        store.update { s ->
            s.copy(outbox = s.outbox + Outgoing(message.id, link.channel, message.type, message.planUuid, resend = resend))
        }
    }

    private suspend fun flushOutbox(): Int = syncMutex.withLock { flushOutboxLocked() }

    /** Publica lo pendiente. Lanza IOException si no hay internet (lo pendiente queda para después). */
    private suspend fun flushOutboxLocked(): Int {
        var count = 0
        val now = clock()
        for (o in state.value.outbox) {
            val link = state.value.links.firstOrNull { it.channel == o.channel }
            val text = store.readBlob(outName(o.id))
            if (link == null || text == null) {
                drop(o)
                continue
            }
            if (o.resend && o.firstSentAt != null && now - o.firstSentAt > CoachProtocol.GIVE_UP_AFTER_MS) {
                drop(o)
                store.update { s -> s.copy(sent = s.sent.map { if (it.planUuid == o.planUuid) it.copy(status = SentStatus.FAILED) else it }) }
                alert(link.userId, "Plan no entregado", "${link.peerName} no abrió la app en 14 días. Prueba enviarlo de nuevo.")
                continue
            }
            val due = o.lastSentAt == null || (o.resend && now - o.lastSentAt >= CoachProtocol.RESEND_EVERY_MS)
            if (!due) continue
            val message = CoachProtocol.json.decodeFromString(CoachMessage.serializer(), text)
            relay.publish(o.channel, CoachProtocol.seal(link.key, message))
            count++
            if (o.resend) {
                store.update { s -> s.copy(outbox = s.outbox.map { if (it.id == o.id) it.copy(firstSentAt = it.firstSentAt ?: now, lastSentAt = now) else it }) }
            } else {
                drop(o)
            }
        }
        return count
    }

    private suspend fun drop(o: Outgoing) {
        store.deleteBlob(outName(o.id))
        store.update { s -> s.copy(outbox = s.outbox.filterNot { it.id == o.id }) }
    }

    /** true si hay planes esperando que el alumno los reciba (para seguir sincronizando en segundo plano). */
    fun hasPendingDeliveries(): Boolean = state.value.outbox.isNotEmpty()

    /** Contenido del plan pendiente (para mostrar un resumen antes de aceptar). */
    suspend fun pendingPlanMessage(planUuid: String): CoachMessage? =
        store.readBlob(inName(planUuid))?.let { runCatching { CoachProtocol.json.decodeFromString(CoachMessage.serializer(), it) }.getOrNull() }

    private fun outName(id: String) = "out-$id.json"
    private fun inName(uuid: String) = "in-${uuid.replace(Regex("[^A-Za-z0-9-]"), "")}.json"
}
