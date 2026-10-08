package com.ositos.fitness.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ositos.fitness.BuildConfig
import android.widget.Toast
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.ositos.fitness.data.DuoKind
import com.ositos.fitness.data.Profile
import com.ositos.fitness.ui.DuoState
import com.ositos.fitness.ui.components.Avatar
import com.ositos.fitness.ui.components.BouncyButton
import com.ositos.fitness.ui.components.FillBar
import com.ositos.fitness.ui.components.GameCard
import com.ositos.fitness.ui.components.LocalCheckForUpdates
import com.ositos.fitness.ui.components.SectionTitle
import com.ositos.fitness.ui.theme.OsitoColors
import java.time.Instant
import java.time.ZoneId

enum class ThemeMode(val label: String) { SYSTEM("Sistema"), DARK("Oscuro"), LIGHT("Claro") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    s: DuoState,
    padding: PaddingValues,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onSave: (Profile) -> Unit,
    onMute: (Int) -> Unit,
    onEditPokeMessages: () -> Unit,
    onEditForfeits: () -> Unit,
    onSignOut: () -> Unit,
    onOpenMeasures: () -> Unit = {},
    myPin: String? = null,
    onDuoKind: (DuoKind) -> Unit = {},
    onLeaveDuo: () -> Unit = {},
) {
    val me = s.me ?: return
    val p = me.profile
    var editing by remember { mutableStateOf<Int?>(null) } // 0 = identidad, 1 = cuerpo
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            GameCard(accent = Color(p.color), onClick = { editing = 0 }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.avatar, Color(p.color), 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(me.name, style = MaterialTheme.typography.headlineSmall)
                        Text("${me.level.emoji} ${me.level.name}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        FillBar(me.levelProgress, Color(p.color), height = 10.dp)
                        Text(
                            "${me.totalXp} XP" + (me.level.nextXp?.let { " · faltan ${it - me.totalXp} para el nivel ${me.level.number + 1}" } ?: " · nivel máximo 🥇"),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text("✏️")
                }
            }
        }
        item {
            GameCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tus cálculos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { editing = 1 }) { Text("Editar datos") }
                }
                Text(
                    "${p.sex.label} · ${p.age} años · ${p.heightCm.fmt()} cm · ${p.weightKg.fmt1()} kg → ${p.targetWeightKg.fmt1()} kg · ${p.activity.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                HealthResults(p, me.eta)
                val m = p.measures
                val parts = listOfNotNull(
                    m.waistCm?.let { "Cintura ${it.fmt()}" }, m.hipCm?.let { "Cadera ${it.fmt()}" },
                    m.chestCm?.let { "Pecho ${it.fmt()}" }, m.armCm?.let { "Brazo ${it.fmt()}" },
                    m.thighCm?.let { "Muslo ${it.fmt()}" },
                )
                if (parts.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("📏 " + parts.joinToString(" · ") + " cm", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            BouncyButton(
                "Mis medidas y cómo tomarlas",
                onOpenMeasures,
                Modifier.fillMaxWidth(),
                color = OsitoColors.Yellow,
                contentColor = Color(0xFF3A2600),
                emoji = "📏",
            )
        }
        item { SectionTitle("Tu dúo") }
        item {
            GameCard(accent = OsitoColors.Pink) {
                val kind = s.duo?.kind ?: DuoKind.PAREJA
                Text(
                    "${kind.emoji} ${me.name} & ${s.partner?.name ?: "tu compa"} · ${kind.label}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text("¿Qué son?", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                DuoKindPicker(kind, onDuoKind)
                if (myPin != null) {
                    Spacer(Modifier.height(14.dp))
                    Text("Tu PIN", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            myPin,
                            style = MaterialTheme.typography.headlineMedium,
                            color = OsitoColors.Purple,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(myPin))
                            Toast.makeText(ctx, "PIN copiado 📋", Toast.LENGTH_SHORT).show()
                        }) { Text("Copiar") }
                    }
                    Text(
                        "Sirve para armar un dúo nuevo si se desemparejan. Mientras estés en un dúo, nadie puede usarlo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { confirmLeave = true }) {
                    Text("💔 Desemparejarme", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item { SectionTitle("Pinchazos") }
        item {
            GameCard {
                val muted = p.pokesMutedUntil > System.currentTimeMillis()
                if (muted) {
                    val t = Instant.ofEpochMilli(p.pokesMutedUntil).atZone(ZoneId.systemDefault()).toLocalTime()
                    Text("🤫 Silenciados hasta las %02d:%02d".format(t.hour, t.minute), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    BouncyButton("Reactivar", { onMute(0) }, color = OsitoColors.Mint)
                } else {
                    Text("¿Necesitás paz? Silenciá los pinchazos un rato:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 4, 8, 24).forEach { h ->
                            FilterChip(false, { onMute(h) }, { Text("$h h") })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onEditPokeMessages) { Text("✏️ Editar mensajes de pinchazo") }
                TextButton(onClick = onEditForfeits) { Text("🎲 Editar lista de prendas") }
            }
        }
        item { SectionTitle("Apariencia") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(themeMode == m, { onThemeMode(m) }, { Text(m.label) })
                }
            }
        }
        item {
            Column {
                val checkUpdates = LocalCheckForUpdates.current
                TextButton(onClick = checkUpdates) { Text("🆕 Buscar actualizaciones") }
                TextButton(onClick = { confirmSignOut = true }) { Text("Cerrar sesión") }
                Text(
                    "Ositos Fitness ${BuildConfig.VERSION_NAME} · IA: ${BuildConfig.AI_BACKEND}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }

    editing?.let { which ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var draft by remember(which) { mutableStateOf(p) }
        ModalBottomSheet(onDismissRequest = { editing = null }, sheetState = sheetState) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
                    .fillMaxWidth(),
            ) {
                if (which == 0) {
                    Text("Tu identidad", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    IdentityPicker(draft) { draft = it }
                    Spacer(Modifier.height(16.dp))
                    BouncyButton("Guardar", {
                        onSave(draft)
                        editing = null
                    }, Modifier.fillMaxWidth(), enabled = draft.nickname.isNotBlank(), color = Color(draft.color))
                } else {
                    BodyStep(draft, onChange = { draft = it }, onNext = {
                        onSave(it)
                        editing = null
                    }, cta = "Guardar")
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("¿Desemparejarte?") },
            text = {
                Text(
                    "El dúo termina para los dos y quedan libres para emparejarse con quien quieran. " +
                        "No se borra nada: si algún día vuelven a emparejarse entre ustedes, recuperan " +
                        "todo (rachas, logros, duelos y La Osera).",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onLeaveDuo() }) {
                    Text("Desemparejar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancelar") } },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Tus datos quedan guardados en el dúo. Podés volver a entrar con la misma cuenta de Google.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Salir") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar") } },
        )
    }
}
