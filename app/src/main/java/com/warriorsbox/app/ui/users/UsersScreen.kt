package com.warriorsbox.app.ui.users

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.warriorsbox.app.data.AppSettings
import com.warriorsbox.app.data.UserRepository
import com.warriorsbox.app.data.db.UserEntity
import com.warriorsbox.app.ui.components.AppBackground
import com.warriorsbox.app.ui.components.ConfirmDialog
import com.warriorsbox.app.ui.components.EmptyState
import com.warriorsbox.app.ui.components.WbCard
import com.warriorsbox.app.ui.components.WbTopBar
import com.warriorsbox.app.ui.components.container
import com.warriorsbox.app.ui.components.rememberTrainerGuard
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UserAvatar(user: UserEntity, size: Int = 56) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val photo = user.photoPath?.let { File(it) }?.takeIf { it.exists() }
        if (photo != null) {
            AsyncImage(model = photo, contentDescription = user.name, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp))
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size((size * 0.6).dp))
        }
    }
}

fun userSubtitle(user: UserEntity): String = listOfNotNull(
    UserRepository.ageOf(user)?.let { "$it años" },
    user.goalEnum.label,
    user.levelEnum.label,
).joinToString(" · ")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UsersScreen(onBack: () -> Unit, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val c = container()
    val users by c.users.users.collectAsStateWithLifecycle(emptyList())
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var toDelete by remember { mutableStateOf<UserEntity?>(null) }
    val scope = rememberCoroutineScope()
    val (guard, guardDialog) = rememberTrainerGuard()

    AppBackground(settings.backgroundPath, settings.veil) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            topBar = { WbTopBar("Usuarios", onBack) },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { guard.run(onAdd) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Agregar") },
                    modifier = Modifier.testTag("agregar_usuario"),
                )
            },
        ) { padding ->
            if (users.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    EmptyState("Todavía no hay usuarios.\nToca \"Agregar\" para crear el primero.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(padding),
                ) {
                    items(users, key = { it.id }) { user ->
                        WbCard(
                            Modifier.combinedClickable(
                                onClick = { guard.run { onOpen(user.id) } },
                                onLongClick = { guard.run { toDelete = user } },
                            ),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                UserAvatar(user)
                                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                    Text(user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(userSubtitle(user), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    item { Text("Mantén presionado un usuario para eliminarlo.", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(8.dp).width(300.dp)) }
                }
            }
        }
    }
    guardDialog()
    toDelete?.let { user ->
        ConfirmDialog(
            title = "¿Eliminar a ${user.name}?",
            text = "Se borrará su perfil y TODO su historial: planes, sesiones, récords y medidas. " +
                "Esto no se puede deshacer, salvo que tengas una copia de seguridad.",
            confirm = "Eliminar",
            onConfirm = {
                scope.launch { c.users.delete(user) }
                toDelete = null
            },
            onDismiss = { toDelete = null },
        )
    }
}
