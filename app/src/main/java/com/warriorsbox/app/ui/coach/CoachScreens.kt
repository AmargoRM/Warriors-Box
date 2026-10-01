package com.warriorsbox.app.ui.coach

import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.warriorsbox.app.coach.CoachState
import com.warriorsbox.app.data.AppSettings
import com.warriorsbox.app.ui.components.AppBackground
import com.warriorsbox.app.ui.components.ConfirmDialog
import com.warriorsbox.app.ui.components.SectionTitle
import com.warriorsbox.app.ui.components.TipBox
import com.warriorsbox.app.ui.components.WbCard
import com.warriorsbox.app.ui.components.WbTopBar
import com.warriorsbox.app.ui.components.container
import com.warriorsbox.app.ui.theme.WbGold
import com.warriorsbox.app.ui.theme.WbGreen
import com.warriorsbox.app.ui.theme.WbRed
import com.warriorsbox.core.coach.Role
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Dibuja el QR (negro sobre blanco, con margen, para que la cámara lo lea fácil). */
fun qrBitmap(text: String, size: Int = 800): ImageBitmap {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2)
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix.get(i % matrix.width, i / matrix.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun formatDate(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))

// ---------------------------------------------------------------------------- alumno: mostrar QR

@Composable
fun CoachInviteScreen(userId: Long, onBack: () -> Unit) {
    val c = container()
    val context = LocalContext.current
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val state by c.coach.state.collectAsStateWithLifecycle()
    val user by c.users.observe(userId).collectAsStateWithLifecycle(null)
    val code by produceState<String?>(null, userId) {
        value = runCatching { c.coach.createInvite(userId) }.getOrElse {
            Toast.makeText(context, it.message ?: "No se pudo crear el código", Toast.LENGTH_LONG).show()
            null
        }
    }
    val qr = remember(code) { code?.let { runCatching { qrBitmap(it) }.getOrNull() } }
    val link = state.linkOf(userId)
    val linked = link != null && link.peerName.isNotBlank()

    // Mientras se muestra el QR, se revisa el buzón cada 5 segundos para saber si el coach ya lo escaneó.
    LaunchedEffect(code, linked) {
        if (code == null || linked) return@LaunchedEffect
        while (true) {
            delay(5_000)
            runCatching { c.coach.sync() }
        }
    }

    AppBackground(user?.backgroundPath ?: settings.backgroundPath, settings.veil) {
        Scaffold(containerColor = Color.Transparent, contentColor = Color.White, topBar = { WbTopBar("Vincular con mi coach", onBack) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (linked) {
                    WbCard(highlight = true) {
                        Text("✓ Vinculado", style = MaterialTheme.typography.titleLarge, color = WbGreen)
                        Text("${link?.peerName} es tu coach. Cuando te mande un plan, te va a aparecer al abrir la app para que lo aceptes.")
                        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Listo") }
                    }
                }
                WbCard {
                    Text("1. Tu coach abre Warriors Box en su celular → Usuarios → \"Vincular alumno\".")
                    Text("2. Escanea este código con la cámara.")
                    Text("3. Arma tu plan y te lo envía. Te llega aquí para aceptarlo.")
                }
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qr != null) {
                        Image(qr, contentDescription = "Código QR de vinculación", filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize().testTag("qr"))
                    } else {
                        CircularProgressIndicator()
                    }
                }
                if (!linked && code != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
                        Text("Esperando que tu coach escanee el código…")
                    }
                }
                OutlinedButton(
                    enabled = code != null,
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Código de Warriors Box para ser mi coach:\n$code\n\nCópialo y en la app ve a Usuarios → Vincular alumno → Pegar.",
                            )
                        }
                        context.startActivity(Intent.createChooser(send, "Compartir código"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Share, null)
                    Text("  Compartir el código (si no están juntos)")
                }
                TipBox(
                    "El código incluye tu perfil (edad, peso, nivel, equipo y lesiones) para que tu coach arme el plan. " +
                        "Compártelo solo con tu coach: quien lo tenga puede mandarte planes.",
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------- coach: escanear

@Composable
fun CoachLinkScreen(onBack: () -> Unit, onLinked: (Long) -> Unit) {
    val c = container()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val state by c.coach.state.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(state.myName) }
    var code by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun link(text: String) {
        if (working) return
        working = true
        error = null
        scope.launch {
            if (name.isNotBlank()) c.coach.setMyName(name)
            runCatching { c.coach.acceptInvite(text) }
                .onSuccess { id ->
                    Toast.makeText(context, "Alumno vinculado", Toast.LENGTH_SHORT).show()
                    onLinked(id)
                }
                .onFailure { error = it.message }
            working = false
        }
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { link(it) }
    }

    AppBackground(settings.backgroundPath, settings.veil) {
        Scaffold(containerColor = Color.Transparent, contentColor = Color.White, topBar = { WbTopBar("Vincular alumno", onBack) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WbCard {
                    Text("Eres el coach", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Tu alumno abre Warriors Box en su celular → su perfil → \"Vincular con mi coach\" y te muestra un QR. " +
                            "Al escanearlo, el alumno aparece aquí en Usuarios con su perfil, y puedes armarle y enviarle el plan.",
                    )
                    OutlinedTextField(
                        name, { name = it }, singleLine = true, label = { Text("Tu nombre (lo verá el alumno)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("nombre_coach"),
                    )
                }
                Button(
                    enabled = !working,
                    onClick = {
                        scanner.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("Apunta al QR de tu alumno")
                                .setBeepEnabled(false)
                                .setOrientationLocked(false),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Filled.QrCodeScanner, null)
                    Text("  Escanear QR del alumno")
                }
                WbCard {
                    SectionTitle("¿No están juntos?")
                    Text("Pídele que te mande el código por WhatsApp, cópialo y pégalo aquí.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        code, { code = it }, label = { Text("Código (empieza con WB1.)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp).testTag("codigo_coach"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        OutlinedButton(onClick = {
                            val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                            code = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        }) {
                            Icon(Icons.Filled.ContentPaste, null)
                            Text(" Pegar")
                        }
                        Button(enabled = code.isNotBlank() && !working, onClick = { link(code) }, modifier = Modifier.testTag("vincular")) { Text("Vincular") }
                    }
                }
                error?.let { Text(it, color = WbRed, fontWeight = FontWeight.SemiBold) }
                if (working) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

// ---------------------------------------------------------------------------- tarjeta en el perfil

@Composable
fun CoachCard(userId: Long, userName: String, onInvite: () -> Unit, onPlan: () -> Unit) {
    val c = container()
    val scope = rememberCoroutineScope()
    val state by c.coach.state.collectAsStateWithLifecycle()
    val link = state.linkOf(userId)
    var confirmUnlink by remember { mutableStateOf(false) }
    WbCard(Modifier.testTag("tarjeta_coach")) {
        SectionTitle("Coach a distancia")
        when {
            link == null -> {
                Text("¿Tu coach usa Warriors Box? Muéstrale tu QR: podrá armarte el plan (ejercicios, series, repeticiones y peso) y te llegará aquí para aceptarlo.")
                Button(onClick = onInvite, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("vincular_coach")) { Text("Vincular con mi coach (QR)") }
                Text(
                    "¿Tú eres el coach? En Usuarios toca \"Vincular alumno\" y escanea el QR de tu alumno.",
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp),
                )
            }
            link.role == Role.STUDENT -> {
                if (link.peerName.isBlank()) {
                    Text("Esperando que tu coach escanee tu QR.")
                    OutlinedButton(onClick = onInvite, modifier = Modifier.fillMaxWidth()) { Text("Mostrar QR") }
                } else {
                    Text("Tu coach: ${link.peerName}", fontWeight = FontWeight.Bold, color = WbGold)
                    Text("Los planes que te mande te aparecen al abrir la app.", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { confirmUnlink = true }) { Text("Desvincular", color = WbRed) }
            }
            else -> {
                Text("$userName es tu alumno a distancia (vinculado por QR).", fontWeight = FontWeight.Bold, color = WbGold)
                SentStatusText(state, userId)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = onPlan, modifier = Modifier.weight(1f)) { Text("Armar plan") }
                    SendPlanButton(userId, userName, Modifier.weight(1f))
                }
                TextButton(onClick = { confirmUnlink = true }) { Text("Desvincular", color = WbRed) }
            }
        }
    }
    if (confirmUnlink && link != null) {
        ConfirmDialog(
            "¿Desvincular?",
            if (link.role == Role.COACH) "Ya no podrás enviarle planes a $userName. El usuario queda en tu celular." else "Tu coach ya no podrá enviarte planes. Tu plan actual se conserva.",
            "Desvincular",
            {
                confirmUnlink = false
                scope.launch { c.coach.unlink(userId) }
            },
            { confirmUnlink = false },
        )
    }
}

@Composable
private fun SentStatusText(state: CoachState, userId: Long) {
    val last = state.lastSent(userId)
    if (last == null) {
        Text("Todavía no le enviaste un plan. Ármalo con \"Armar plan\" y luego toca \"Enviar plan\".", style = MaterialTheme.typography.bodySmall)
    } else {
        Text("Último plan enviado: ${formatDate(last.sentAt)}", style = MaterialTheme.typography.bodySmall)
        Text(last.status.label, fontWeight = FontWeight.SemiBold)
    }
}

/** Botón "Enviar plan" con confirmación (coach). */
@Composable
fun SendPlanButton(userId: Long, userName: String, modifier: Modifier = Modifier) {
    val c = container()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    Button(onClick = { confirm = true }, enabled = !sending, modifier = modifier.testTag("enviar_plan")) {
        Icon(Icons.AutoMirrored.Filled.Send, null)
        Text(if (sending) " Enviando…" else " Enviar plan")
    }
    if (confirm) {
        ConfirmDialog(
            "Enviar plan a $userName",
            "Se envía el plan actual completo (5 semanas, con series, repeticiones, peso y notas). " +
                "Le llega cuando abra la app, o en menos de una hora si tiene internet. Si le habías mandado otro y no lo aceptó, este lo reemplaza.",
            "Enviar",
            {
                confirm = false
                sending = true
                scope.launch {
                    runCatching { c.coach.sendPlan(userId) }
                        .onSuccess { Toast.makeText(context, "Plan enviado a $userName", Toast.LENGTH_LONG).show() }
                        .onFailure { Toast.makeText(context, it.message ?: "No se pudo enviar", Toast.LENGTH_LONG).show() }
                    sending = false
                }
            },
            { confirm = false },
        )
    }
}

/** Aviso arriba del plan: alumno a distancia (coach) o plan armado por tu coach (alumno). */
@Composable
fun CoachPlanBanner(userId: Long, planId: Long?, userName: String) {
    val c = container()
    val state by c.coach.state.collectAsStateWithLifecycle()
    val link = state.linkOf(userId)
    val fromCoach = state.coachPlan(planId)
    when {
        link?.role == Role.COACH -> WbCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), highlight = true) {
            Text("Alumno a distancia: $userName", fontWeight = FontWeight.Bold, color = WbGold)
            if (planId == null) {
                Text("Genera el plan o ármalo a mano. Cuando esté listo, toca \"Enviar plan\".", style = MaterialTheme.typography.bodySmall)
            } else {
                SentStatusText(state, userId)
                SendPlanButton(userId, userName, Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
        fromCoach != null -> WbCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("Plan armado por tu coach ${fromCoach.coachName}", fontWeight = FontWeight.Bold, color = WbGold)
            Text("Puedes cambiarlo; si lo haces, a tu coach le llega un aviso con los cambios.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------------------------------------------------------------------- avisos al abrir la app

/**
 * Al abrir (o volver a) la app: revisa el buzón y muestra los planes que llegaron y los avisos nuevos.
 */
@Composable
fun CoachDialogsHost(onOpenPlan: (Long) -> Unit) {
    val c = container()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by c.coach.state.collectAsStateWithLifecycle()
    var later by remember { mutableStateOf(setOf<String>()) }
    var confirmReject by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { runCatching { c.coach.sync() } }
    }

    val pending = state.inbox.firstOrNull { it.planUuid !in later }
    if (pending != null) {
        val user by c.users.observe(pending.userId).collectAsStateWithLifecycle(null)
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Plan nuevo de tu coach") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${pending.coachName} te mandó un plan${user?.let { " para ${it.name}" } ?: ""}.", fontWeight = FontWeight.Bold)
                    Text("${pending.trainingDays} días de entrenamiento y ${pending.exerciseCount} ejercicios en total (5 semanas), con series, repeticiones y peso.")
                    Text("Si lo aceptas, reemplaza tu plan actual. Tu historial se conserva.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, modifier = Modifier.testTag("aceptar_plan"), onClick = {
                    busy = true
                    scope.launch {
                        runCatching { c.coach.acceptPlan(pending.planUuid) }
                            .onSuccess {
                                Toast.makeText(context, "Plan aceptado", Toast.LENGTH_SHORT).show()
                                onOpenPlan(pending.userId)
                            }
                            .onFailure { Toast.makeText(context, it.message ?: "No se pudo aceptar", Toast.LENGTH_LONG).show() }
                        busy = false
                    }
                }) { Text("Aceptar") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmReject = pending.planUuid }) { Text("Rechazar", color = WbRed) }
                    TextButton(onClick = { later = later + pending.planUuid }) { Text("Más tarde") }
                }
            },
        )
    } else {
        val unseen = state.alerts.filter { !it.seen }
        if (unseen.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { scope.launch { c.coach.markAlertsSeen() } },
                title = { Text(if (unseen.size == 1) unseen.first().title else "Novedades de coach") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        unseen.takeLast(10).forEach { a ->
                            if (unseen.size > 1) Text(a.title, fontWeight = FontWeight.Bold)
                            Text(a.text)
                            Text(formatDate(a.at), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(modifier = Modifier.testTag("entendido"), onClick = { scope.launch { c.coach.markAlertsSeen() } }) { Text("Entendido") }
                },
            )
        }
    }
    confirmReject?.let { uuid ->
        ConfirmDialog(
            "¿Rechazar el plan?", "Tu coach recibirá un aviso de que lo rechazaste. Tu plan actual no cambia.", "Rechazar",
            {
                confirmReject = null
                scope.launch { c.coach.rejectPlan(uuid) }
            },
            { confirmReject = null },
        )
    }
}
