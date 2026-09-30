package com.warriorsbox.app.ui.routines

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warriorsbox.app.data.AppSettings
import com.warriorsbox.app.data.SessionSummary
import com.warriorsbox.app.share.ShareCardRenderer
import com.warriorsbox.app.ui.components.AppBackground
import com.warriorsbox.app.ui.components.LabeledRow
import com.warriorsbox.app.ui.components.SectionTitle
import com.warriorsbox.app.ui.components.StatTile
import com.warriorsbox.app.ui.components.TipBox
import com.warriorsbox.app.ui.components.WbCard
import com.warriorsbox.app.ui.components.WbTopBar
import com.warriorsbox.app.ui.components.container
import com.warriorsbox.app.ui.theme.WbGold
import com.warriorsbox.core.engine.Units
import kotlinx.coroutines.launch

@Composable
fun SummaryScreen(sessionId: Long, onBack: () -> Unit) {
    val c = container()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var summary by remember { mutableStateOf<SessionSummary?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var format by remember { mutableStateOf(ShareCardRenderer.Format.STORY) }
    var userBackground by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionId) {
        summary = runCatching { c.sessions.summary(sessionId) }.getOrNull()
        val session = c.sessions.get(sessionId)
        userBackground = session?.let { c.users.get(it.userId)?.backgroundPath }
    }

    fun render(f: ShareCardRenderer.Format) {
        val s = summary ?: return
        format = f
        scope.launch { preview = c.share.render(s, f, userBackground ?: settings.backgroundPath, settings.useLb) }
    }

    val saveLegacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val bmp = preview
        if (granted && bmp != null) scope.launch {
            val ok = c.share.saveToGallery(bmp)
            Toast.makeText(context, if (ok) "Guardada en la galería" else "No se pudo guardar", Toast.LENGTH_SHORT).show()
        }
    }

    AppBackground(userBackground ?: settings.backgroundPath, settings.veil) {
        Scaffold(containerColor = Color.Transparent, topBar = { WbTopBar("Resumen", onBack) }) { padding ->
            val s = summary ?: return@Scaffold
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WbCard {
                    Text(if (s.records.isNotEmpty()) "¡Sesión con récord!" else "¡Sesión terminada!", style = MaterialTheme.typography.titleLarge, color = WbGold)
                    Text(s.title)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("≈${s.kcal}", "kcal", Modifier.weight(1f))
                        StatTile("${s.durationMin}", "minutos", Modifier.weight(1f))
                        StatTile(Units.formatWeight(s.volumeKg, settings.useLb).substringBefore(' '), if (settings.useLb) "lb volumen" else "kg volumen", Modifier.weight(1f))
                        StatTile("${s.streak}", "racha", Modifier.weight(1f))
                    }
                    Text(
                        "Calorías estimadas con valores MET (Compendium of Physical Activities): kcal = MET × peso × horas.",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                WbCard {
                    SectionTitle("Lo que hiciste")
                    s.lines.forEach { line ->
                        LabeledRow((if (line.record) "🏆 " else "") + line.name, line.detail)
                    }
                    if (s.lines.isEmpty()) Text("No marcaste series en esta sesión.")
                }
                TipBox("La próxima semana ya tiene los pesos ajustados según cómo te fue hoy.")
                WbCard {
                    SectionTitle("Compartir en Instagram")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { render(ShareCardRenderer.Format.STORY) }, modifier = Modifier.weight(1f)) { Text("Historia") }
                        Button(onClick = { render(ShareCardRenderer.Format.POST) }, modifier = Modifier.weight(1f)) { Text("Post") }
                    }
                    preview?.let { bmp ->
                        Image(
                            bmp.asImageBitmap(), contentDescription = "Vista previa (${format.label})",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(vertical = 12.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { scope.launch { context.startActivity(c.share.share(bmp)) } }, modifier = Modifier.weight(1f)) {
                                Text("Compartir", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                    saveLegacy.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                } else {
                                    scope.launch {
                                        val ok = c.share.saveToGallery(bmp)
                                        Toast.makeText(context, if (ok) "Guardada en la galería" else "No se pudo guardar", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }, modifier = Modifier.weight(1f)) { Text("Guardar") }
                        }
                    }
                }
            }
        }
    }
}
