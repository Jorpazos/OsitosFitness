package com.ositos.fitness.ui.components

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Subí este número si cambian las bases: vuelve a pedir la aceptación. */
private const val TERMS_VERSION = 1
private const val PREFS = "terms"

private fun accepted(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("acceptedVersion", 0) >= TERMS_VERSION

private fun markAccepted(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putInt("acceptedVersion", TERMS_VERSION).apply()
}

/** Texto completo de las bases. Breve, claro y honesto. */
const val TERMS_TITLE = "Bases y condiciones"
const val TERMS_BODY =
    "Ositos Fitness es una app privada para cuidarse de a dos.\n\n" +
        "Qué guarda:\n" +
        "• Lo que registrás vos: apodo, datos del perfil, peso, comida, ejercicio, medidas y agua.\n" +
        "• Se comparte únicamente con la persona de tu dúo.\n\n" +
        "Fotos de comida:\n" +
        "• Si usás la estimación por foto, la imagen se envía a la IA solo para calcular las calorías.\n\n" +
        "Ubicación (próximamente):\n" +
        "• Más adelante vamos a sumar funciones con ubicación (por ejemplo, ver si tu compa está cerca).\n" +
        "• Cuando las actives, Android te va a pedir permiso y vos decidís. Sin tu permiso, la app no usa tu ubicación.\n\n" +
        "Tus datos son tuyos: podés pedir que se borren cuando quieras.\n" +
        "Más detalles técnicos: en el repositorio del proyecto en GitHub."

/**
 * Cuadro de bases y condiciones: se muestra una vez (hasta que cambien).
 * "No acepto" cierra la app; "Aceptar" la deja usarla. "Leer" muestra el texto completo.
 */
@Composable
fun TermsGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var ok by remember { mutableStateOf(accepted(context)) }
    var expanded by remember { mutableStateOf(false) }

    content()

    if (!ok) {
        AlertDialog(
            onDismissRequest = { /* no se puede cerrar sin decidir */ },
            title = { Text("$TERMS_TITLE 🐻") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (expanded) {
                        Text(TERMS_BODY, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            "Antes de empezar, aceptá las bases y condiciones de uso.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { expanded = true }) { Text("Leer bases y condiciones") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    markAccepted(context)
                    ok = true
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { (context as? ComponentActivity)?.finish() }) { Text("No acepto") }
            },
        )
    }
}
