package com.warriorsbox.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warriorsbox.app.R
import com.warriorsbox.app.data.AppSettings
import com.warriorsbox.app.ui.components.AppBackground
import com.warriorsbox.app.ui.components.container
import com.warriorsbox.app.ui.theme.ButtonText
import com.warriorsbox.app.ui.theme.WbGold
import java.io.File

@Composable
fun HomeScreen(onUsers: () -> Unit, onRoutines: () -> Unit, onSettings: () -> Unit) {
    val settings by container().settings.settings.collectAsStateWithLifecycle(AppSettings())
    val customBackground = settings.backgroundPath?.let { File(it).exists() } == true
    AppBackground(settings.backgroundPath, if (customBackground) settings.veil else 0.1f) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            IconButton(onClick = onSettings, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("ajustes")) {
                Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = Color.White)
            }
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (customBackground) {
                    // Con foto propia se muestra el logo encima; con el fondo original el logo ya está en la imagen.
                    Spacer(Modifier.weight(1f))
                    Image(painterResource(R.drawable.ic_logo), contentDescription = "Warriors Box", modifier = Modifier.size(150.dp))
                    Text("WARRIORS BOX", style = MaterialTheme.typography.displaySmall, color = Color.White)
                    Spacer(Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = onUsers,
                    modifier = Modifier.fillMaxWidth().height(72.dp).testTag("boton_usuarios"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Group, contentDescription = null)
                        Text("  USUARIOS", style = ButtonText)
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onRoutines,
                    modifier = Modifier.fillMaxWidth().height(72.dp).testTag("boton_rutinas"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Black.copy(alpha = 0.55f), contentColor = WbGold),
                    border = BorderStroke(2.dp, WbGold),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FitnessCenter, contentDescription = null)
                        Text("  RUTINAS", style = ButtonText)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
