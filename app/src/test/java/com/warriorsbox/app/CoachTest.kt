package com.warriorsbox.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.warriorsbox.app.coach.Relay
import com.warriorsbox.app.coach.SentStatus
import com.warriorsbox.app.data.db.AppDatabase
import com.warriorsbox.app.data.db.InjuryEntity
import com.warriorsbox.app.data.db.UserEntity
import com.warriorsbox.core.coach.CoachProtocol
import com.warriorsbox.core.coach.Role
import com.warriorsbox.core.model.BodyZone
import com.warriorsbox.core.model.Exercise
import com.warriorsbox.core.model.Goal
import com.warriorsbox.core.model.Level
import com.warriorsbox.core.model.MovementPattern
import com.warriorsbox.core.model.Muscle
import com.warriorsbox.core.model.TrainingLocation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** Buzón en memoria que se comporta como ntfy.sh (los mensajes grandes van como adjunto). */
class FakeRelay : Relay {
    data class Entry(val id: String, val text: String)

    val channels = mutableMapOf<String, MutableList<Entry>>()
    var offline = false
    var publishCount = 0
    private var next = 0

    override suspend fun publish(channel: String, text: String) {
        if (offline) throw IOException("sin internet")
        publishCount++
        channels.getOrPut(channel) { mutableListOf() } += Entry("m${next++}", text)
    }

    override suspend fun poll(channel: String): List<CoachProtocol.RelayMessage> {
        if (offline) throw IOException("sin internet")
        return channels[channel].orEmpty().map {
            if (it.text.length > CoachProtocol.MAX_INLINE) {
                CoachProtocol.RelayMessage(it.id, 0, "You received a file", "fake://${it.id}")
            } else {
                CoachProtocol.RelayMessage(it.id, 0, it.text, null)
            }
        }
    }

    override suspend fun download(url: String): String =
        channels.values.flatten().first { "fake://${it.id}" == url }.text

    /** Simula que ntfy.sh borró los mensajes viejos. */
    fun expireAll() = channels.values.forEach { it.clear() }
}

/** Dos "celulares" (alumno y coach) con bases y carpetas separadas, unidos por el mismo buzón. */
@RunWith(AndroidJUnit4::class)
class CoachTest {

    private val relay = FakeRelay()
    private var now = 1_800_000_000_000L
    private lateinit var studentDb: AppDatabase
    private lateinit var coachDb: AppDatabase
    private lateinit var student: AppContainer
    private lateinit var coach: AppContainer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<WarriorsApp>()
        val root = File(context.cacheDir, "coach-test-${System.nanoTime()}")
        studentDb = AppDatabase.inMemory(context)
        coachDb = AppDatabase.inMemory(context)
        student = AppContainer(context, studentDb, relay, File(root, "alumno")) { now }
        coach = AppContainer(context, coachDb, relay, File(root, "coach")) { now }
    }

    @After
    fun tearDown() {
        studentDb.close()
        coachDb.close()
    }

    private suspend fun newStudent(): Long = student.users.save(
        UserEntity(
            name = "Ana", weightKg = 61.5, heightCm = 165.0, level = Level.INTERMEDIATE.name, daysPerWeek = 4,
            minutesPerSession = 60, location = TrainingLocation.GYM.name, goal = Goal.MUSCLE.name, birthDate = "1995-04-10",
        ),
        listOf(InjuryEntity(userId = 0, zone = BodyZone.KNEE.name, description = "menisco", active = true)),
    )

    /** Vincula y deja al alumno a distancia creado en el celular del coach. */
    private suspend fun link(): Pair<Long, Long> {
        val ana = newStudent()
        val code = student.coach.createInvite(ana)
        coach.coach.setMyName("Rafa")
        val remote = coach.coach.acceptInvite("Mirá, este es mi código: $code")
        student.coach.sync()
        return ana to remote
    }

    @Test
    fun linkingCopiesTheProfileAndGreetsTheStudent() = runBlocking {
        val (ana, remote) = link()
        val copy = coach.users.get(remote)!!
        assertEquals("Ana", copy.name)
        assertEquals(61.5, copy.weightKg!!, 0.001)
        assertEquals(Level.INTERMEDIATE.name, copy.level)
        assertEquals("1995-04-10", copy.birthDate)
        assertEquals(BodyZone.KNEE.name, coach.users.injuriesNow(remote).single().zone)
        assertEquals(Role.COACH, coach.coach.linkOf(remote)!!.role)
        // El alumno ve el nombre del coach y un aviso.
        assertEquals("Rafa", student.coach.linkOf(ana)!!.peerName)
        assertTrue(student.coach.state.value.alerts.any { it.title == "Coach vinculado" })
        // Escanear el mismo QR dos veces no duplica al alumno.
        assertEquals(remote, coach.coach.acceptInvite(student.coach.createInvite(ana)))
        assertEquals(1, coach.users.all().size)
        // El código de otra persona o basura no sirve.
        assertTrue(runCatching { coach.coach.acceptInvite("hola") }.isFailure)
    }

    @Test
    fun coachSendsPlanWithCustomExerciseAndPhotosAndStudentAccepts() = runBlocking {
        val (ana, remote) = link()
        // El coach crea un ejercicio que no existe, con foto.
        val photo = File(coach.photos.dir, "ejercicio-test.jpg").apply { writeBytes(ByteArray(2048) { (it % 251).toByte() }) }
        val custom = Exercise(
            id = "propio-remo-landmine-1", name = "Remo landmine", pattern = MovementPattern.HORIZONTAL_PULL,
            primaryMuscles = listOf(Muscle.LATS), custom = true, imageUrls = listOf("file://" + photo.absolutePath),
        )
        coach.exercises.saveCustom(custom)
        val (planId, _) = coach.plans.generate(remote)
        val monday = coach.plans.daysNow(planId).first { it.week == 1 && !it.optional }
        coach.plans.addItem(monday.id, coach.exercises.get(custom.id)!!, remote)
        val item = coach.plans.itemsNow(monday.id).first { it.exerciseId == custom.id }
        coach.plans.updateItem(item.copy(sets = 5, repsMin = 6, repsMax = 8, weightKg = 42.5, trainerNote = "Pausa arriba"))

        coach.coach.sendPlan(remote)
        assertEquals(SentStatus.SENDING, coach.coach.state.value.lastSent(remote)!!.status)

        // Al alumno le llega para aceptar; el coach ve "le llegó".
        student.coach.sync()
        val pending = student.coach.state.value.inbox.single()
        assertEquals("Rafa", pending.coachName)
        assertEquals(ana, pending.userId)
        coach.coach.sync()
        assertEquals(SentStatus.RECEIVED, coach.coach.state.value.lastSent(remote)!!.status)
        assertTrue("ya no se reenvía", coach.coach.state.value.outbox.isEmpty())

        val newPlan = student.coach.acceptPlan(pending.planUuid)
        assertEquals(newPlan, student.plans.activePlanNow(ana)!!.id)
        val coachItems = coach.plans.daysNow(planId).flatMap { coach.plans.itemsNow(it.id) }
        val studentItems = student.plans.daysNow(newPlan).flatMap { student.plans.itemsNow(it.id) }
        assertEquals(coachItems.size, studentItems.size)
        assertEquals(coachItems.map { it.exerciseId to it.weightKg }, studentItems.map { it.exerciseId to it.weightKg })
        val copied = studentItems.first { it.exerciseId == custom.id }
        assertEquals(5, copied.sets)
        assertEquals(6, copied.repsMin)
        assertEquals(8, copied.repsMax)
        assertEquals("Pausa arriba", copied.trainerNote)
        // El ejercicio propio llegó con su foto.
        val received = student.exercises.get(custom.id)!!
        assertEquals("Remo landmine", received.name)
        val receivedPhoto = File(received.imageUrls.single().removePrefix("file://"))
        assertTrue(receivedPhoto.exists())
        assertTrue(receivedPhoto.readBytes().contentEquals(photo.readBytes()))
        assertNotNull(student.coach.state.value.coachPlan(newPlan))

        coach.coach.sync()
        assertEquals(SentStatus.ACCEPTED, coach.coach.state.value.lastSent(remote)!!.status)
        assertTrue(coach.coach.state.value.alerts.any { it.title == "Plan aceptado" })
    }

    @Test
    fun studentChangesAreReportedToTheCoachInOneMessage() = runBlocking {
        val (ana, remote) = link()
        coach.plans.generate(remote)
        coach.coach.sendPlan(remote)
        student.coach.sync()
        val planId = student.coach.acceptPlan(student.coach.state.value.inbox.single().planUuid)
        coach.coach.sync()

        val day = student.plans.daysNow(planId).first { it.week == 2 && !it.optional }
        val items = student.plans.itemsNow(day.id)
        student.plans.removeItem(items[0])
        student.plans.updateItem(items[1].copy(sets = items[1].sets + 1))
        student.plans.renameDay(day.id, "Mi día")
        // Se juntan en un solo aviso pendiente.
        assertEquals(1, student.coach.state.value.outbox.size)
        student.coach.sync()
        coach.coach.sync()
        val alert = coach.coach.state.value.alerts.last()
        assertEquals("Ana cambió su plan", alert.title)
        assertTrue(alert.text, alert.text.contains("quitó") && alert.text.contains("pasó de") && alert.text.contains("Mi día"))
        assertTrue(alert.text.contains("semana 2"))

        // Cambiar un plan propio (no del coach) no avisa a nadie.
        val before = relay.publishCount
        student.plans.createCustom(ana) // reemplaza el plan del coach: eso sí se avisa
        val own = student.plans.activePlanNow(ana)!!.id
        student.coach.sync()
        val afterReplace = relay.publishCount
        assertTrue(afterReplace > before)
        val ownDay = student.plans.daysNow(own).first()
        student.plans.addItem(ownDay.id, student.exercises.allNow().first { it.curated }, ana)
        student.coach.sync()
        assertEquals(afterReplace, relay.publishCount)
        assertNull(student.coach.state.value.coachPlan(own))
    }

    @Test
    fun planIsResentUntilTheStudentOpensTheApp() = runBlocking {
        val (_, remote) = link()
        coach.plans.generate(remote)
        relay.offline = true
        coach.coach.sendPlan(remote) // sin internet: queda pendiente
        assertEquals(1, coach.coach.state.value.outbox.size)
        relay.offline = false
        coach.coach.sync()
        val afterFirst = relay.publishCount
        // ntfy.sh borra los adjuntos a las 3 h; el coach lo reenvía solo.
        now += 3 * 3600 * 1000L
        relay.expireAll()
        coach.coach.sync()
        assertEquals(afterFirst + 1, relay.publishCount)
        // Aunque llegue dos veces, el alumno lo ve una sola vez.
        now += 3 * 3600 * 1000L
        coach.coach.sync()
        student.coach.sync()
        assertEquals(1, student.coach.state.value.inbox.size)
        coach.coach.sync()
        assertEquals(SentStatus.RECEIVED, coach.coach.state.value.lastSent(remote)!!.status)
    }

    @Test
    fun rejectAndUnlinkAndDeleteProfile() = runBlocking {
        val (ana, remote) = link()
        coach.plans.generate(remote)
        coach.coach.sendPlan(remote)
        student.coach.sync()
        student.coach.rejectPlan(student.coach.state.value.inbox.single().planUuid)
        assertNull(student.plans.activePlanNow(ana))
        coach.coach.sync()
        assertEquals(SentStatus.REJECTED, coach.coach.state.value.lastSent(remote)!!.status)

        // Borrar el perfil en el celular del alumno termina la vinculación en los dos.
        student.users.delete(student.users.get(ana)!!)
        assertNull(student.users.get(ana))
        assertNull(student.coach.linkOf(ana))
        coach.coach.sync()
        assertNull(coach.coach.linkOf(remote))
        assertTrue(coach.coach.state.value.alerts.any { it.title == "Vinculación terminada" })
        // El alumno sigue en el celular del coach (con su historial).
        assertNotNull(coach.users.get(remote))
    }

    @Test
    fun messagesFromOtherKeysAreIgnored() = runBlocking {
        val (ana, _) = link()
        val channel = student.coach.linkOf(ana)!!.channel
        relay.publish(channel, "texto cualquiera")
        relay.publish(channel, CoachProtocol.seal(CoachProtocol.newKey(), com.warriorsbox.core.coach.CoachMessage(type = com.warriorsbox.core.coach.MessageType.UNLINK, from = Role.COACH)))
        student.coach.sync()
        assertNotNull(student.coach.linkOf(ana))
    }
}
