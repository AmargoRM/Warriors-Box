package com.warriorsbox.app.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.warriorsbox.app.data.AppSettings
import com.warriorsbox.app.ui.components.AppBackground
import com.warriorsbox.app.ui.components.ChoiceChips
import com.warriorsbox.app.ui.components.WbCard
import com.warriorsbox.app.ui.components.WbTopBar
import com.warriorsbox.app.ui.components.container
import com.warriorsbox.core.model.Equipment
import com.warriorsbox.core.model.Exercise
import com.warriorsbox.core.model.Level
import com.warriorsbox.core.model.MovementPattern
import com.warriorsbox.core.model.Muscle
import kotlinx.coroutines.launch
import java.io.File

private const val MAX_PHOTOS = 3

/**
 * Ejercicio que no está en la biblioteca: nombre, músculos, equipo, instrucciones y hasta 3 fotos
 * (de la galería o de la cámara). Si el coach lo usa en un plan, viaja con sus fotos al alumno.
 */
@Composable
fun CustomExerciseScreen(onBack: () -> Unit) {
    val c = container()
    val context = LocalContext.current
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf(MovementPattern.ISOLATION) }
    var muscle by remember { mutableStateOf(Muscle.CHEST) }
    var secondary by remember { mutableStateOf(setOf<Muscle>()) }
    var equipment by remember { mutableStateOf(setOf<Equipment>()) }
    var level by remember { mutableStateOf(Level.BEGINNER) }
    var steps by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("") }
    var timed by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf(listOf<String>()) }
    var saving by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun addPhoto(uri: Uri) = scope.launch {
        val path = c.photos.save(uri, "ejercicio", maxSide = 1080)
        if (path == null) Toast.makeText(context, "No se pudo leer la foto", Toast.LENGTH_SHORT).show()
        else photos = (photos + path).take(MAX_PHOTOS)
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) addPhoto(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) addPhoto(uri)
    }

    fun openCamera() {
        val file = File(context.cacheDir, "camera/foto-${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        cameraUri = uri
        camera.launch(uri)
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else Toast.makeText(context, "Sin permiso de cámara: usa la galería.", Toast.LENGTH_LONG).show()
    }

    AppBackground(settings.backgroundPath, settings.veil) {
        Scaffold(containerColor = Color.Transparent, contentColor = Color.White, topBar = { WbTopBar("Ejercicio propio", onBack) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WbCard {
                    OutlinedTextField(name, { name = it }, label = { Text("Nombre *") }, modifier = Modifier.fillMaxWidth().testTag("nombre_ejercicio"))
                    Text("Fotos (hasta $MAX_PHOTOS)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        photos.forEach { path ->
                            Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))) {
                                AsyncImage(model = File(path), contentDescription = "Foto del ejercicio", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                IconButton(
                                    onClick = {
                                        c.photos.delete(path)
                                        photos = photos - path
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                                ) { Icon(Icons.Filled.Close, "Quitar foto", tint = Color.White) }
                            }
                        }
                    }
                    if (photos.size < MAX_PHOTOS) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.AddPhotoAlternate, null)
                                Text(" Galería")
                            }
                            OutlinedButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
                                    else cameraPermission.launch(Manifest.permission.CAMERA)
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.CameraAlt, null)
                                Text(" Cámara")
                            }
                        }
                    }
                    Text("Tipo de movimiento", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    ChoiceChips(MovementPattern.entries, setOf(pattern), { it.label }, { pattern = it })
                    Text("Músculo principal", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    ChoiceChips(Muscle.entries, setOf(muscle), { it.label }, { muscle = it; secondary = secondary - it })
                    Text("Músculos secundarios (opcional)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    ChoiceChips(Muscle.entries.filter { it != muscle }, secondary, { it.label }, { m -> secondary = if (m in secondary) secondary - m else secondary + m })
                    Text("Equipo (vacío = sin equipo)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    ChoiceChips(Equipment.entries.filter { it != Equipment.NONE }, equipment, { it.label }, { e -> equipment = if (e in equipment) equipment - e else equipment + e })
                    Text("Nivel", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    ChoiceChips(Level.entries, setOf(level), { it.label }, { level = it })
                    ChoiceChips(listOf(false, true), setOf(timed), { if (it) "Por tiempo" else "Por repeticiones" }, { timed = it })
                    OutlinedTextField(steps, { steps = it }, label = { Text("Instrucciones (una por línea)") }, modifier = Modifier.fillMaxWidth().height(140.dp))
                    OutlinedTextField(tip, { tip = it }, label = { Text("Consejo técnico (opcional)") }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Las series, repeticiones y el peso se eligen al agregarlo al plan (lápiz ✎ de cada día).",
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                    OutlinedButton(onClick = {
                        photos.forEach { c.photos.delete(it) }
                        onBack()
                    }, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                    Button(
                        enabled = name.isNotBlank() && !saving,
                        onClick = {
                            saving = true
                            val id = "propio-" + normalize(name).replace(Regex("[^a-z0-9]+"), "-").trim('-') + "-" + (System.currentTimeMillis() % 100000)
                            scope.launch {
                                c.exercises.saveCustom(
                                    Exercise(
                                        id = id, name = name.trim(), pattern = pattern, primaryMuscles = listOf(muscle), secondaryMuscles = secondary.toList(),
                                        equipment = equipment, level = level,
                                        compound = pattern !in setOf(MovementPattern.ISOLATION, MovementPattern.CORE, MovementPattern.CARDIO),
                                        steps = steps.lines().map { it.trim() }.filter { it.isNotEmpty() }, tip = tip.trim().ifBlank { null },
                                        imageUrls = photos.map { "file://$it" }, timed = timed, custom = true, rank = 5000,
                                    ),
                                )
                                Toast.makeText(context, "Ejercicio guardado", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("guardar_ejercicio"),
                    ) { Text("Guardar") }
                }
            }
        }
    }
}
