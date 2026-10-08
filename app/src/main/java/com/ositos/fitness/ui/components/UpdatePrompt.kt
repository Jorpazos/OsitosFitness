package com.ositos.fitness.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ositos.fitness.data.UpdateInfo
import com.ositos.fitness.data.Updater
import kotlinx.coroutines.launch
import java.io.File

/** Para que cualquier pantalla (ej. Perfil) pueda pedir "Buscar actualizaciones". */
val LocalCheckForUpdates = compositionLocalOf<() -> Unit> { {} }

private sealed interface UpdateUi {
    data object Hidden : UpdateUi
    data class Available(val info: UpdateInfo) : UpdateUi
    data class Downloading(val info: UpdateInfo) : UpdateUi
    data class Ready(val file: File) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

/**
 * Busca versiones nuevas en GitHub al abrir la app (como mucho cada 6 h) y ofrece instalarlas.
 * [content] recibe la función para chequear a mano.
 */
@Composable
fun UpdatePrompt(updater: Updater, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<UpdateUi>(UpdateUi.Hidden) }
    var progress by remember { mutableFloatStateOf(0f) }

    fun check(manual: Boolean) {
        scope.launch {
            try {
                val info = updater.check()
                if (info != null) {
                    ui = UpdateUi.Available(info)
                } else if (manual) {
                    Toast.makeText(ctx, "Estás al día ✅ (versión ${updater.currentVersionName})", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                if (manual) ui = UpdateUi.Failed(e.message ?: "No se pudo buscar")
            }
        }
    }

    fun download(info: UpdateInfo) {
        ui = UpdateUi.Downloading(info)
        progress = 0f
        scope.launch {
            ui = try {
                UpdateUi.Ready(updater.download(info) { progress = it })
            } catch (e: Exception) {
                UpdateUi.Failed(e.message ?: "No se pudo descargar")
            }
        }
    }

    fun install(file: File) {
        if (!updater.canInstall()) {
            Toast.makeText(
                ctx,
                "Activá \"Permitir de esta fuente\" para Ositos Fitness y volvé a tocar Instalar",
                Toast.LENGTH_LONG,
            ).show()
            updater.openInstallPermissionSettings()
        } else {
            updater.install(file)
        }
    }

    LaunchedEffect(Unit) { if (updater.shouldAutoCheck()) check(manual = false) }

    androidx.compose.runtime.CompositionLocalProvider(LocalCheckForUpdates provides { check(manual = true) }) {
        content()
    }

    when (val s = ui) {
        UpdateUi.Hidden -> Unit
        is UpdateUi.Available -> AlertDialog(
            onDismissRequest = { ui = UpdateUi.Hidden },
            title = { Text("🆕 ¡Hay una versión nueva!") },
            text = {
                Column {
                    Text("Ositos Fitness ${s.info.versionName} (tenés la ${updater.currentVersionName}).")
                    if (s.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Novedades: ${s.info.notes}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Se instala encima: no perdés nada, tus datos están en la nube.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { download(s.info) }) { Text("Actualizar") } },
            dismissButton = { TextButton(onClick = { ui = UpdateUi.Hidden }) { Text("Después") } },
        )
        is UpdateUi.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Descargando ${s.info.versionName}…") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { progress })
                    Spacer(Modifier.height(6.dp))
                    Text("${(progress * 100).toInt()}%")
                }
            },
            confirmButton = {},
        )
        is UpdateUi.Ready -> AlertDialog(
            onDismissRequest = { ui = UpdateUi.Hidden },
            title = { Text("✅ Lista para instalar") },
            text = { Text("Tocá Instalar y confirmá en la pantalla de Android.") },
            confirmButton = { TextButton(onClick = { install(s.file) }) { Text("Instalar") } },
            dismissButton = { TextButton(onClick = { ui = UpdateUi.Hidden }) { Text("Cancelar") } },
        )
        is UpdateUi.Failed -> AlertDialog(
            onDismissRequest = { ui = UpdateUi.Hidden },
            title = { Text("😵 No se pudo actualizar") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { ui = UpdateUi.Hidden }) { Text("Ok") } },
        )
    }
}
