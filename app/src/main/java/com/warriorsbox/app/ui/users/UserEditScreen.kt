package com.warriorsbox.app.ui.users

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.warriorsbox.app.AppContainer
import com.warriorsbox.app.data.AppSettings
import com.warriorsbox.app.data.db.HealthCondition
import com.warriorsbox.app.data.db.InjuryEntity
import com.warriorsbox.app.data.db.UserEntity
import com.warriorsbox.app.ui.components.AppBackground
import com.warriorsbox.app.ui.components.ChoiceChips
import com.warriorsbox.app.ui.components.NumberField
import com.warriorsbox.app.ui.components.TipBox
import com.warriorsbox.app.ui.components.WbCard
import com.warriorsbox.app.ui.components.WbTopBar
import com.warriorsbox.app.ui.components.container
import com.warriorsbox.app.ui.theme.WbRed
import com.warriorsbox.core.engine.BodyMetrics
import com.warriorsbox.core.engine.Units
import com.warriorsbox.core.model.BodyZone
import com.warriorsbox.core.model.Equipment
import com.warriorsbox.core.model.Goal
import com.warriorsbox.core.model.Level
import com.warriorsbox.core.model.Sex
import com.warriorsbox.core.model.TrainingLocation
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Estado editable del formulario. Los números se guardan como texto mientras se escriben. */
class UserFormViewModel(private val c: AppContainer, private val userId: Long) : ViewModel() {
    var loaded by mutableStateOf(userId == 0L)
    var original: UserEntity? = null
    var name by mutableStateOf("")
    var photoPath by mutableStateOf<String?>(null)
    var birthDate by mutableStateOf<LocalDate?>(null)
    var sex by mutableStateOf(Sex.UNSPECIFIED)
    var weight by mutableStateOf("")
    var height by mutableStateOf("")
    var bodyFat by mutableStateOf("")
    var waist by mutableStateOf("")
    var level by mutableStateOf(Level.BEGINNER)
    var days by mutableStateOf(3)
    var minutes by mutableStateOf(60)
    var location by mutableStateOf(TrainingLocation.GYM)
    var equipment by mutableStateOf(Equipment.defaultsFor(TrainingLocation.GYM))
    var maxDumbbell by mutableStateOf("")
    var goal by mutableStateOf(Goal.HEALTH)
    val injuries = mutableStateListOf<InjuryEntity>()
    var conditions by mutableStateOf(setOf<HealthCondition>())
    var surgeries by mutableStateOf("")
    var medications by mutableStateOf("")
    var doctorRestriction by mutableStateOf(false)
    var sleep by mutableStateOf("")
    var stress by mutableStateOf(3f)
    var activity by mutableStateOf("Sedentario")
    var emergency by mutableStateOf("")

    init {
        if (userId != 0L) viewModelScope.launch {
            val u = c.users.get(userId)
            val lb = c.settings.current().useLb
            fun mass(kg: Double) = if (lb) Math.round(Units.kgToLb(kg) * 10) / 10.0 else kg
            if (u != null) {
                original = u
                name = u.name
                photoPath = u.photoPath
                birthDate = u.birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                sex = u.sexEnum
                weight = u.weightKg?.let { trim(mass(it)) }.orEmpty()
                height = u.heightCm?.let { trim(it) }.orEmpty()
                bodyFat = u.bodyFatPct?.let { trim(it) }.orEmpty()
                waist = u.waistCm?.let { trim(it) }.orEmpty()
                level = u.levelEnum
                days = u.daysPerWeek
                minutes = u.minutesPerSession
                location = u.locationEnum
                equipment = u.equipmentSet.ifEmpty { Equipment.defaultsFor(u.locationEnum) }
                maxDumbbell = u.maxDumbbellKg?.let { trim(mass(it)) }.orEmpty()
                goal = u.goalEnum
                conditions = u.conditionSet
                surgeries = u.surgeries
                medications = u.medications
                doctorRestriction = u.doctorRestriction
                sleep = u.sleepHours?.let { trim(it) }.orEmpty()
                stress = (u.stressLevel ?: 3).toFloat()
                activity = u.dailyActivity ?: "Sedentario"
                emergency = u.emergencyContact.orEmpty()
                injuries.clear()
                injuries.addAll(c.users.injuriesNow(userId))
            }
            loaded = true
        }
    }

    private fun trim(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

    fun toEntity(useLb: Boolean): UserEntity {
        val w = weight.toDoubleOrNull()?.let { if (useLb) Units.lbToKg(it) else it }
        val dumbbell = maxDumbbell.toDoubleOrNull()?.let { if (useLb) Units.lbToKg(it) else it }
        val base = original ?: UserEntity(name = name)
        return base.copy(
            name = name.trim(),
            photoPath = photoPath,
            birthDate = birthDate?.toString(),
            sex = sex.name,
            weightKg = w,
            heightCm = height.toDoubleOrNull(),
            bodyFatPct = bodyFat.toDoubleOrNull(),
            waistCm = waist.toDoubleOrNull(),
            level = level.name,
            daysPerWeek = days,
            minutesPerSession = minutes,
            location = location.name,
            equipment = equipment.joinToString(",") { it.name },
            maxDumbbellKg = if (Equipment.DUMBBELL in equipment && location != TrainingLocation.GYM) dumbbell else null,
            goal = goal.name,
            conditions = conditions.joinToString(",") { it.name },
            surgeries = surgeries.trim(),
            medications = medications.trim(),
            doctorRestriction = doctorRestriction,
            sleepHours = sleep.toDoubleOrNull(),
            stressLevel = stress.toInt(),
            dailyActivity = activity,
            emergencyContact = emergency.trim().ifBlank { null },
        )
    }

    fun save(useLb: Boolean, onSaved: (Long) -> Unit) = viewModelScope.launch {
        val id = c.users.save(toEntity(useLb), injuries.toList())
        onSaved(id)
    }
}

private val STEPS = listOf("Datos básicos", "Medidas", "Experiencia", "Objetivo", "Salud", "Hábitos")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserEditScreen(userId: Long, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val c = container()
    val vm: UserFormViewModel = viewModel(key = "usuario-$userId") { UserFormViewModel(c, userId) }
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var step by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDisclaimer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun validate(): Boolean {
        error = when (step) {
            0 -> if (vm.name.isBlank()) "Escribe el nombre." else null
            1 -> when {
                (vm.weight.toDoubleOrNull() ?: 0.0) <= 0 -> "Escribe el peso (se usa para sugerir cargas y calcular calorías)."
                (vm.height.toDoubleOrNull() ?: 0.0) <= 0 -> "Escribe la altura."
                else -> null
            }
            else -> null
        }
        return error == null
    }

    fun doSave() = vm.save(settings.useLb) { id -> onSaved(id) }

    AppBackground(settings.backgroundPath, settings.veil) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            topBar = { WbTopBar(if (userId == 0L) "Nuevo usuario" else "Editar usuario", onBack) },
            bottomBar = {
                Row(Modifier.fillMaxWidth().padding(16.dp).imePadding(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (step > 0) OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) { Text("Atrás") }
                    Button(
                        onClick = {
                            if (!validate()) return@Button
                            if (step < STEPS.lastIndex) {
                                step++
                            } else if (!settings.disclaimerShown) {
                                showDisclaimer = true
                            } else {
                                doSave()
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("siguiente"),
                    ) { Text(if (step < STEPS.lastIndex) "Siguiente" else "Guardar") }
                }
            },
        ) { padding ->
            if (!vm.loaded) return@Scaffold
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Paso ${step + 1} de ${STEPS.size}: ${STEPS[step]}", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { (step + 1f) / STEPS.size }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = WbRed) }
                WbCard {
                    when (step) {
                        0 -> StepBasics(vm)
                        1 -> StepMeasures(vm, settings.useLb)
                        2 -> StepExperience(vm, settings.useLb)
                        3 -> StepGoal(vm)
                        4 -> StepHealth(vm)
                        else -> StepHabits(vm)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Antes de empezar") },
            text = {
                Text(
                    "Esta app no reemplaza la opinión de un médico o entrenador certificado. " +
                        "Las rutinas y cargas son sugerencias generales: ajústalas a cómo te sientes y detente si aparece " +
                        "dolor agudo, mareo, dolor de pecho o falta de aire fuera de lo normal.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisclaimer = false
                    scope.launch { c.settings.setDisclaimerShown() }
                    doSave()
                }) { Text("Entendido") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepBasics(vm: UserFormViewModel) {
    val c = container()
    val scope = rememberCoroutineScope()
    var showDate by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { c.photos.save(uri, "perfil", 800)?.let { vm.photoPath = it } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = vm.name, onValueChange = { vm.name = it }, label = { Text("Nombre *") },
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("campo_nombre"),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(vm.toEntity(false), 64)
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.padding(start = 12.dp),
            ) { Text(if (vm.photoPath == null) "Agregar foto (opcional)" else "Cambiar foto") }
        }
        OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
            val age = vm.birthDate?.let { BodyMetrics.age(it) }
            Text(vm.birthDate?.let { "Nacimiento: $it ($age años)" } ?: "Fecha de nacimiento")
        }
        Text("Sexo (solo para cálculos de carga y calorías)", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(Sex.entries, setOf(vm.sex), { it.label }, { vm.sex = it })
    }
    if (showDate) {
        val initial = (vm.birthDate ?: LocalDate.now().minusYears(25)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.birthDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancelar") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun StepMeasures(vm: UserFormViewModel, useLb: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(vm.weight, { vm.weight = it }, "Peso *", Modifier.fillMaxWidth().testTag("campo_peso"), if (useLb) "lb" else "kg")
        NumberField(vm.height, { vm.height = it }, "Altura *", Modifier.fillMaxWidth().testTag("campo_altura"), "cm")
        vm.height.toDoubleOrNull()?.takeIf { it > 0 }?.let { Text("= ${Units.cmToFeetText(it)}", style = MaterialTheme.typography.labelMedium) }
        NumberField(vm.bodyFat, { vm.bodyFat = it }, "% de grasa (opcional)", Modifier.fillMaxWidth(), "%")
        NumberField(vm.waist, { vm.waist = it }, "Cintura (opcional)", Modifier.fillMaxWidth(), "cm")
        val kg = vm.weight.toDoubleOrNull()?.let { if (useLb) Units.lbToKg(it) else it }
        val bmi = if (kg != null) BodyMetrics.bmi(kg, vm.height.toDoubleOrNull() ?: 0.0) else null
        if (bmi != null) {
            TipBox("IMC: ${BodyMetrics.format(bmi)} (${BodyMetrics.bmiCategory(bmi)}). ${BodyMetrics.BMI_DISCLAIMER}")
        }
    }
}

@Composable
private fun StepExperience(vm: UserFormViewModel, useLb: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nivel", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(Level.entries, setOf(vm.level), { it.label }, { vm.level = it })
        Text(
            when (vm.level) {
                Level.BEGINNER -> "Menos de 6 meses entrenando con constancia."
                Level.INTERMEDIATE -> "Entre 6 meses y 2 años entrenando."
                Level.ADVANCED -> "Más de 2 años entrenando con técnica sólida."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Días disponibles por semana", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(listOf(3, 4, 5, 6), setOf(vm.days), { "$it días" }, { vm.days = it })
        Text("Minutos por sesión", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(listOf(30, 45, 60, 90), setOf(vm.minutes), { "$it min" }, { vm.minutes = it })
        Text("¿Dónde entrenas?", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(TrainingLocation.entries, setOf(vm.location), { it.label }, {
            vm.location = it
            vm.equipment = Equipment.defaultsFor(it)
        })
        Text("Equipo disponible", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(Equipment.entries.filter { it != Equipment.NONE }, vm.equipment, { it.label }, { e ->
            vm.equipment = if (e in vm.equipment) vm.equipment - e else vm.equipment + e
        })
        if (Equipment.DUMBBELL in vm.equipment && vm.location != TrainingLocation.GYM) {
            NumberField(vm.maxDumbbell, { vm.maxDumbbell = it }, "Peso de tu mancuerna más pesada", Modifier.fillMaxWidth(), if (useLb) "lb" else "kg")
        }
    }
}

@Composable
private fun StepGoal(vm: UserFormViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("¿Cuál es tu objetivo principal?", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(Goal.entries, setOf(vm.goal), { it.label }, { vm.goal = it })
        TipBox(
            when (vm.goal) {
                Goal.FAT_LOSS -> "Circuitos de 10–15 repeticiones con descansos cortos y cardio al final."
                Goal.MUSCLE -> "Hipertrofia: 6–12 repeticiones, 3–4 series, descansos de 1–2 minutos."
                Goal.STRENGTH -> "Fuerza: pocas repeticiones (4–6) con más peso y descansos largos."
                Goal.ENDURANCE -> "Resistencia: 12–15 repeticiones y descansos cortos."
                Goal.HEALTH -> "Salud general: equilibrio entre fuerza, movilidad y cardio."
                Goal.REHAB -> "Rehabilitación ligera: ejercicios sencillos, cargas bajas. Consulta a tu fisioterapeuta."
            },
        )
    }
}

@Composable
private fun StepHealth(vm: UserFormViewModel) {
    var zone by remember { mutableStateOf(BodyZone.KNEE) }
    var desc by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var stillHurts by remember { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Historial de lesiones", style = MaterialTheme.typography.titleMedium)
        if (vm.injuries.isEmpty()) Text("Sin lesiones registradas.", style = MaterialTheme.typography.bodySmall)
        vm.injuries.forEachIndexed { i, inj ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(inj.zoneEnum.label + if (inj.active) " (sigue doliendo)" else " (recuperada)", fontWeight = FontWeight.Bold)
                    val extra = listOfNotNull(inj.description.ifBlank { null }, inj.approxDate?.ifBlank { null }).joinToString(" · ")
                    if (extra.isNotBlank()) Text(extra, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = inj.active, onCheckedChange = { vm.injuries[i] = inj.copy(active = it) })
                IconButton(onClick = { vm.injuries.removeAt(i) }) { Icon(Icons.Filled.Delete, "Quitar") }
            }
        }
        Text("Agregar lesión o molestia", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(BodyZone.entries, setOf(zone), { it.label }, { zone = it })
        OutlinedTextField(desc, { desc = it }, label = { Text("Descripción (ej. esguince, tendinitis)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(date, { date = it }, label = { Text("¿Cuándo fue? (aprox.)") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("¿Sigue doliendo?", Modifier.weight(1f))
            Switch(checked = stillHurts, onCheckedChange = { stillHurts = it })
        }
        OutlinedButton(onClick = {
            vm.injuries.add(InjuryEntity(userId = 0, zone = zone.name, description = desc.trim(), approxDate = date.trim().ifBlank { null }, active = stillHurts))
            desc = ""
            date = ""
        }) { Text("Agregar lesión") }

        Text("Condiciones de salud", style = MaterialTheme.typography.titleMedium)
        ChoiceChips(HealthCondition.entries, vm.conditions, { it.label }, { h ->
            vm.conditions = if (h in vm.conditions) vm.conditions - h else vm.conditions + h
        })
        OutlinedTextField(vm.surgeries, { vm.surgeries = it }, label = { Text("Cirugías") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vm.medications, { vm.medications = it }, label = { Text("Medicamentos") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("¿Un médico te restringió el ejercicio?", Modifier.weight(1f))
            Switch(checked = vm.doctorRestriction, onCheckedChange = { vm.doctorRestriction = it })
        }
        if (vm.doctorRestriction || HealthCondition.CARDIAC in vm.conditions) {
            TipBox(
                "Importante: con una restricción médica o un problema cardíaco, consulta a un profesional antes de empezar " +
                    "a entrenar. La app te dejará continuar, pero las sugerencias no reemplazan esa evaluación.",
                color = WbRed,
            )
        }
        if (HealthCondition.PREGNANCY in vm.conditions) {
            TipBox("Durante el embarazo, sigue siempre las indicaciones de tu médico y evita ejercicios boca abajo o con riesgo de caída.", color = WbRed)
        }
    }
}

@Composable
private fun StepHabits(vm: UserFormViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(vm.sleep, { vm.sleep = it }, "Horas de sueño por noche", Modifier.fillMaxWidth(), "h")
        Text("Nivel de estrés: ${vm.stress.toInt()} de 5", style = MaterialTheme.typography.labelLarge)
        Slider(value = vm.stress, onValueChange = { vm.stress = it }, valueRange = 1f..5f, steps = 3)
        Text("Actividad diaria (fuera del gimnasio)", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(listOf("Sedentario", "Ligera", "Activa", "Muy activa"), setOf(vm.activity), { it }, { vm.activity = it })
        OutlinedTextField(vm.emergency, { vm.emergency = it }, label = { Text("Contacto de emergencia (opcional)") }, modifier = Modifier.fillMaxWidth())
        val sleep = vm.sleep.toDoubleOrNull()
        if ((sleep != null && sleep < 6) || vm.stress >= 4f) {
            TipBox("Dormir poco o tener mucho estrés reduce la recuperación: prioriza la técnica y no subas peso en días malos.")
        }
    }
}
