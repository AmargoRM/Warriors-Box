package com.warriorsbox.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.warriorsbox.app.LaunchRequest
import com.warriorsbox.app.ui.components.container
import com.warriorsbox.app.ui.history.HistoryScreen
import com.warriorsbox.app.ui.history.ProgressScreen
import com.warriorsbox.app.ui.home.HomeScreen
import com.warriorsbox.app.ui.library.CustomExerciseScreen
import com.warriorsbox.app.ui.library.ExerciseDetailScreen
import com.warriorsbox.app.ui.library.LibraryScreen
import com.warriorsbox.app.ui.routines.PickUserScreen
import com.warriorsbox.app.ui.routines.PlanEditorScreen
import com.warriorsbox.app.ui.routines.PlanScreen
import com.warriorsbox.app.ui.routines.SessionScreen
import com.warriorsbox.app.ui.routines.SummaryScreen
import com.warriorsbox.app.ui.settings.AboutScreen
import com.warriorsbox.app.ui.settings.RemindersScreen
import com.warriorsbox.app.ui.settings.SettingsScreen
import com.warriorsbox.app.ui.settings.TrainerPanelScreen
import com.warriorsbox.app.ui.update.UpdateDialogHost
import com.warriorsbox.app.ui.users.UserDetailScreen
import com.warriorsbox.app.ui.users.UserEditScreen
import com.warriorsbox.app.ui.users.UsersScreen

object Routes {
    const val HOME = "inicio"
    const val USERS = "usuarios"
    const val USER_EDIT = "usuario/editar/{userId}"
    const val USER_DETAIL = "usuario/{userId}"
    const val PICK_USER = "rutinas"
    const val PLAN = "plan/{userId}"
    const val SESSION = "sesion/{userId}/{dayId}"
    const val SUMMARY = "resumen/{sessionId}"
    const val EDITOR = "editor/{userId}/{dayId}"
    const val LIBRARY = "biblioteca?pick={pick}&userId={userId}"
    const val EXERCISE = "ejercicio/{exerciseId}?userId={userId}"
    const val CUSTOM_EXERCISE = "ejercicio-propio"
    const val HISTORY = "historial/{userId}"
    const val PROGRESS = "progreso/{userId}/{exerciseId}"
    const val SETTINGS = "ajustes"
    const val REMINDERS = "recordatorios/{userId}"
    const val TRAINER = "entrenador"
    const val ABOUT = "acerca"

    fun userEdit(id: Long) = "usuario/editar/$id"
    fun userDetail(id: Long) = "usuario/$id"
    fun plan(userId: Long) = "plan/$userId"
    fun session(userId: Long, dayId: Long) = "sesion/$userId/$dayId"
    fun summary(sessionId: Long) = "resumen/$sessionId"
    fun editor(userId: Long, dayId: Long) = "editor/$userId/$dayId"
    fun library(pick: String = "", userId: Long = -1) = "biblioteca?pick=$pick&userId=$userId"
    fun exercise(id: String, userId: Long = -1) = "ejercicio/${android.net.Uri.encode(id)}?userId=$userId"
    fun history(userId: Long) = "historial/$userId"
    fun progress(userId: Long, exerciseId: String) = "progreso/$userId/${android.net.Uri.encode(exerciseId)}"
    fun reminders(userId: Long) = "recordatorios/$userId"

    /** Clave del resultado de la biblioteca en modo "elegir". Valor: "<pick>|<exerciseId>". */
    const val PICK_RESULT = "ejercicio_elegido"
}

@Composable
fun WarriorsNavHost(
    launchRequest: LaunchRequest?,
    onLaunchHandled: () -> Unit,
    nav: NavHostController = rememberNavController(),
) {
    val c = container()
    var showUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(launchRequest) {
        val r = launchRequest ?: return@LaunchedEffect
        if (r.openUpdate) showUpdate = true
        if (r.userId != null) {
            nav.navigate(Routes.plan(r.userId))
            if (r.dayId != null) nav.navigate(Routes.session(r.userId, r.dayId))
        }
        onLaunchHandled()
    }

    UpdateDialogHost(forceOpen = showUpdate, onClosed = { showUpdate = false })

    val back: () -> Unit = { nav.popBackStack() }
    val longArg = { name: String -> navArgument(name) { type = NavType.LongType } }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onUsers = { nav.navigate(Routes.USERS) },
                onRoutines = { nav.navigate(Routes.PICK_USER) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.USERS) {
            UsersScreen(
                onBack = back,
                onAdd = { nav.navigate(Routes.userEdit(0)) },
                onOpen = { nav.navigate(Routes.userDetail(it)) },
            )
        }
        composable(Routes.USER_EDIT, listOf(longArg("userId"))) { entry ->
            UserEditScreen(
                userId = entry.arguments?.getLong("userId") ?: 0,
                onBack = back,
                onSaved = { id ->
                    nav.popBackStack()
                    if (nav.currentDestination?.route == Routes.USERS) nav.navigate(Routes.userDetail(id))
                },
            )
        }
        composable(Routes.USER_DETAIL, listOf(longArg("userId"))) { entry ->
            val id = entry.arguments?.getLong("userId") ?: 0
            UserDetailScreen(
                userId = id,
                onBack = back,
                onEdit = { nav.navigate(Routes.userEdit(id)) },
                onPlan = { nav.navigate(Routes.plan(id)) },
                onHistory = { nav.navigate(Routes.history(id)) },
                onReminders = { nav.navigate(Routes.reminders(id)) },
                onDeleted = { nav.popBackStack() },
            )
        }
        composable(Routes.PICK_USER) {
            PickUserScreen(
                onBack = back,
                onPick = { nav.navigate(Routes.plan(it)) },
                onCreateUser = { nav.navigate(Routes.userEdit(0)) },
            )
        }
        composable(Routes.PLAN, listOf(longArg("userId"))) { entry ->
            val userId = entry.arguments?.getLong("userId") ?: 0
            PlanScreen(
                userId = userId,
                onBack = back,
                onOpenDay = { dayId -> nav.navigate(Routes.session(userId, dayId)) },
                onEditDay = { dayId -> nav.navigate(Routes.editor(userId, dayId)) },
                onHistory = { nav.navigate(Routes.history(userId)) },
                onLibrary = { nav.navigate(Routes.library(userId = userId)) },
                onEditProfile = { nav.navigate(Routes.userEdit(userId)) },
            )
        }
        composable(Routes.SESSION, listOf(longArg("userId"), longArg("dayId"))) { entry ->
            val userId = entry.arguments?.getLong("userId") ?: 0
            SessionScreen(
                userId = userId,
                dayId = entry.arguments?.getLong("dayId") ?: 0,
                onBack = back,
                onFinished = { sessionId ->
                    nav.popBackStack()
                    nav.navigate(Routes.summary(sessionId))
                },
                onExerciseInfo = { nav.navigate(Routes.exercise(it, userId)) },
            )
        }
        composable(Routes.SUMMARY, listOf(longArg("sessionId"))) { entry ->
            SummaryScreen(sessionId = entry.arguments?.getLong("sessionId") ?: 0, onBack = back)
        }
        composable(Routes.EDITOR, listOf(longArg("userId"), longArg("dayId"))) { entry ->
            val userId = entry.arguments?.getLong("userId") ?: 0
            val picked = entry.savedStateHandle.getStateFlow<String?>(Routes.PICK_RESULT, null)
            PlanEditorScreen(
                userId = userId,
                dayId = entry.arguments?.getLong("dayId") ?: 0,
                pickedFlow = picked,
                onPickedHandled = { entry.savedStateHandle[Routes.PICK_RESULT] = null },
                onBack = back,
                onPickExercise = { pick -> nav.navigate(Routes.library(pick, userId)) },
            )
        }
        composable(
            Routes.LIBRARY,
            listOf(
                navArgument("pick") { type = NavType.StringType; defaultValue = "" },
                navArgument("userId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            val pick = entry.arguments?.getString("pick").orEmpty()
            val userId = entry.arguments?.getLong("userId") ?: -1
            LibraryScreen(
                pickMode = pick.isNotBlank(),
                userId = userId,
                onBack = back,
                onOpen = { nav.navigate(Routes.exercise(it, userId)) },
                onPicked = { exerciseId ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(Routes.PICK_RESULT, "$pick|$exerciseId")
                    nav.popBackStack()
                },
                onCreateCustom = { nav.navigate(Routes.CUSTOM_EXERCISE) },
            )
        }
        composable(
            Routes.EXERCISE,
            listOf(
                navArgument("exerciseId") { type = NavType.StringType },
                navArgument("userId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            val userId = entry.arguments?.getLong("userId") ?: -1
            val exerciseId = entry.arguments?.getString("exerciseId").orEmpty()
            ExerciseDetailScreen(
                exerciseId = exerciseId,
                onBack = back,
                onProgress = if (userId > 0) ({ nav.navigate(Routes.progress(userId, exerciseId)) }) else null,
            )
        }
        composable(Routes.CUSTOM_EXERCISE) { CustomExerciseScreen(onBack = back) }
        composable(Routes.HISTORY, listOf(longArg("userId"))) { entry ->
            val userId = entry.arguments?.getLong("userId") ?: 0
            HistoryScreen(
                userId = userId,
                onBack = back,
                onExercise = { nav.navigate(Routes.progress(userId, it)) },
                onSession = { nav.navigate(Routes.summary(it)) },
            )
        }
        composable(
            Routes.PROGRESS,
            listOf(longArg("userId"), navArgument("exerciseId") { type = NavType.StringType }),
        ) { entry ->
            ProgressScreen(
                userId = entry.arguments?.getLong("userId") ?: 0,
                exerciseId = entry.arguments?.getString("exerciseId").orEmpty(),
                onBack = back,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = back,
                onReminders = { nav.navigate(Routes.reminders(it)) },
                onTrainerPanel = { nav.navigate(Routes.TRAINER) },
                onLibrary = { nav.navigate(Routes.library()) },
                onAbout = { nav.navigate(Routes.ABOUT) },
                onCheckUpdate = { showUpdate = true },
            )
        }
        composable(Routes.REMINDERS, listOf(longArg("userId"))) { entry ->
            RemindersScreen(userId = entry.arguments?.getLong("userId") ?: 0, onBack = back)
        }
        composable(Routes.TRAINER) {
            TrainerPanelScreen(
                onBack = back,
                onStudent = { nav.navigate(Routes.userDetail(it)) },
                onPlan = { nav.navigate(Routes.plan(it)) },
            )
        }
        composable(Routes.ABOUT) { AboutScreen(onBack = back, sources = c.exercises.sources) }
    }
}
