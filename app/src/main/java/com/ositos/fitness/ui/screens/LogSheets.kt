package com.ositos.fitness.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ositos.fitness.data.AiFood
import com.ositos.fitness.data.AiRepository
import com.ositos.fitness.domain.Activities
import com.ositos.fitness.domain.ActivityType
import com.ositos.fitness.domain.Foods
import com.ositos.fitness.domain.HealthCalculator
import com.ositos.fitness.domain.Intensity
import com.ositos.fitness.ui.PhotoState
import com.ositos.fitness.ui.components.BouncyButton
import com.ositos.fitness.ui.components.Haptics
import com.ositos.fitness.ui.components.Pill
import com.ositos.fitness.ui.theme.OsitoColors
import java.io.File
import kotlin.math.roundToInt

enum class Sheet { MENU, MEAL, PHOTO, EXERCISE, WEIGHT, MEASURES }

/** Selector principal: 1 toque para abrir, 1 para elegir tipo, 1 para guardar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheetHost(
    sheet: Sheet?,
    onDismiss: () -> Unit,
    onSelect: (Sheet) -> Unit,
    content: @Composable (Sheet) -> Unit,
) {
    if (sheet == null) return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = sheet != Sheet.MENU)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Box(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            if (sheet == Sheet.MENU) MenuSheet(onSelect) else content(sheet)
        }
    }
}

@Composable
private fun MenuSheet(onSelect: (Sheet) -> Unit) {
    val ctx = LocalContext.current
    Column {
        Text("¿Qué registramos?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(14.dp))
        val options = listOf(
            Triple(Sheet.MEAL, "🍽️", "Comida") to OsitoColors.Orange,
            Triple(Sheet.PHOTO, "📸", "Foto IA") to OsitoColors.Pink,
            Triple(Sheet.EXERCISE, "💪", "Ejercicio") to OsitoColors.Mint,
            Triple(Sheet.WEIGHT, "⚖️", "Peso") to OsitoColors.Purple,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { (o, color) ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.large)
                        .background(color.copy(alpha = 0.16f))
                        .clickable { Haptics.tick(ctx); onSelect(o.first) }
                        .padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(o.second, fontSize = 32.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(o.third, style = MaterialTheme.typography.labelLarge, color = color)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(OsitoColors.Yellow.copy(alpha = 0.16f))
                .clickable { Haptics.tick(ctx); onSelect(Sheet.MEASURES) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📏", fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Medidas", style = MaterialTheme.typography.labelLarge, color = OsitoColors.Orange)
                Text("Panza, cadera, brazos, piernas… y cómo medirte", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ------------------------------------------------------------------ Comida manual

@Composable
fun MealSheet(onSave: (name: String, kcal: Int, detail: String, emoji: String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var manualName by rememberSaveable { mutableStateOf("") }
    var manualKcal by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { Foods.search(query) }
    Column {
        Text("Comida", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Tocá un alimento y listo. ¿Comiste el doble? Mantené apretado… mentira, cargalo dos veces 😅",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar: milanesa, empanada, mate…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.heightIn(max = 300.dp)) {
            items(results, key = { it.name }) { f ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onSave(f.name, f.kcal, f.portion, f.emoji) }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(f.emoji, fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.titleSmall)
                        Text(f.portion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Pill("${f.kcal} kcal", OsitoColors.Orange)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("¿No está? Cargalo a mano", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = manualName,
                onValueChange = { manualName = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.weight(1.6f),
            )
            OutlinedTextField(
                value = manualKcal,
                onValueChange = { v -> manualKcal = v.filter { it.isDigit() }.take(5) },
                label = { Text("kcal") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        BouncyButton(
            "Guardar",
            onClick = {
                val name = manualName.ifEmpty { query }.ifBlank { "Comida" }
                onSave(name, manualKcal.toIntOrNull() ?: 0, "Carga manual", "🍽️")
            },
            enabled = (manualKcal.toIntOrNull() ?: 0) > 0,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ------------------------------------------------------------------ Foto con IA

private fun newPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "fotos").apply { mkdirs() }
    val file = File(dir, "comida_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
fun PhotoSheet(
    state: PhotoState,
    usedToday: Int,
    onAnalyze: (Uri) -> Unit,
    onReset: () -> Unit,
    onSave: (name: String, kcal: Int, detail: String) -> Unit,
) {
    val ctx = LocalContext.current
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) onAnalyze(uri)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onAnalyze(uri)
    }
    fun openCamera() {
        val uri = newPhotoUri(ctx)
        pendingUri = uri
        camera.launch(uri)
    }
    // Abre la cámara directo al entrar: un toque menos.
    LaunchedEffect(Unit) {
        if (state is PhotoState.Idle && usedToday < AiRepository.DAILY_LIMIT) openCamera()
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Foto con IA 📸", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Estimación aproximada. Podés corregir todo antes de guardar. (${usedToday}/${AiRepository.DAILY_LIMIT} hoy)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        when (state) {
            PhotoState.Idle -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BouncyButton("Sacar foto", { openCamera() }, Modifier.weight(1f), emoji = "📷",
                        enabled = usedToday < AiRepository.DAILY_LIMIT)
                    BouncyButton("Galería", {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, Modifier.weight(1f), emoji = "🖼️", color = OsitoColors.Purple,
                        enabled = usedToday < AiRepository.DAILY_LIMIT)
                }
            }
            PhotoState.Analyzing -> Analyzing()
            is PhotoState.Error -> {
                Text("😵 ${state.message}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                BouncyButton("Probar de nuevo", onReset, Modifier.fillMaxWidth())
            }
            is PhotoState.Result -> EditableResult(state, onSave, onRetry = onReset)
        }
    }
}

@Composable
private fun Analyzing() {
    val inf = rememberInfiniteTransition(label = "ai")
    val rot by inf.animateFloat(-12f, 12f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "r")
    val phrases = listOf("Mirando el plato…", "Contando milanesas…", "Pesando con los ojos…", "Consultando a la abuela…")
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1400)
            i = (i + 1) % phrases.size
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🔍", fontSize = 56.sp, modifier = Modifier.rotate(rot))
        Spacer(Modifier.height(12.dp))
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(phrases[i], style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Puede tardar hasta un minuto si la IA está muy pedida 🐢",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EditableResult(
    state: PhotoState.Result,
    onSave: (name: String, kcal: Int, detail: String) -> Unit,
    onRetry: () -> Unit,
) {
    val items = remember(state) {
        mutableStateListOf<AiFood>().apply { addAll(state.result.foods) }
    }
    val total = items.sumOf { it.kcal }
    val confColor = when (state.result.confidence) {
        "alta" -> OsitoColors.Good
        "baja" -> OsitoColors.Bad
        else -> OsitoColors.Warn
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Detecté esto:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Pill("Confianza ${state.result.confidence}", confColor)
    }
    Spacer(Modifier.height(8.dp))
    if (items.isEmpty()) {
        Text("No reconocí comida en la foto 🤔. Cargala a mano o probá otra.", style = MaterialTheme.typography.bodyMedium)
    }
    items.forEachIndexed { idx, f ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            OutlinedTextField(
                value = f.name,
                onValueChange = { items[idx] = f.copy(name = it) },
                label = { Text(f.portion.ifBlank { "Alimento" }, maxLines = 1) },
                singleLine = true,
                modifier = Modifier.weight(1.7f),
            )
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                value = f.kcal.toString(),
                onValueChange = { v -> items[idx] = f.copy(kcal = v.filter { it.isDigit() }.take(5).toIntOrNull() ?: 0) },
                label = { Text("kcal") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { items.removeAt(idx) }) { Icon(Icons.Default.Delete, "Quitar") }
        }
    }
    TextButton(onClick = { items.add(AiFood("", "", 0)) }) { Text("+ Agregar alimento") }
    Spacer(Modifier.height(6.dp))
    Text("Total: $total kcal", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BouncyButton("Otra foto", onRetry, Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface)
        BouncyButton(
            "Guardar",
            onClick = {
                val valid = items.filter { it.name.isNotBlank() }
                val name = valid.joinToString(", ") { it.name }.take(80).ifBlank { "Comida (foto)" }
                val detail = "📸 " + valid.joinToString(" · ") { "${it.name} ${it.kcal}" }
                onSave(name, total, detail)
            },
            modifier = Modifier.weight(1f),
            enabled = total > 0,
            emoji = "✅",
        )
    }
}

// ------------------------------------------------------------------ Ejercicio

@Composable
fun ExerciseSheet(
    weightKg: Double,
    lastActivityId: String?,
    onSave: (name: String, kcal: Int, detail: String, emoji: String) -> Unit,
) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<ActivityType?>(null) }
    var minutes by remember { mutableFloatStateOf(30f) }
    var intensity by remember { mutableStateOf(Intensity.MEDIUM) }
    var directName by rememberSaveable { mutableStateOf("") }
    var directKcal by rememberSaveable { mutableStateOf("") }

    Column {
        Text("Ejercicio", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Actividad + tiempo", "Kcal del reloj").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = mode == i,
                    onClick = { mode = i },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (mode == 1) {
            OutlinedTextField(
                value = directName, onValueChange = { directName = it.take(40) },
                label = { Text("¿Qué hiciste?") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = directKcal, onValueChange = { v -> directKcal = v.filter { it.isDigit() }.take(4) },
                label = { Text("kcal quemadas") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            BouncyButton(
                "Guardar", onClick = {
                    onSave(directName.ifBlank { "Entrenamiento" }, directKcal.toInt(), "⌚ Dato del reloj", "⌚")
                },
                enabled = (directKcal.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.fillMaxWidth(), color = OsitoColors.Mint,
            )
            return@Column
        }

        val sel = selected
        if (sel == null) {
            Activities.byId(lastActivityId ?: "")?.let { last ->
                val kcal = HealthCalculator.exerciseKcal(last.met(Intensity.MEDIUM), weightKg, 30)
                BouncyButton(
                    "Repetir: ${last.name} 30 min (~$kcal kcal)",
                    onClick = { onSave(last.name, kcal, "30 min · ${Intensity.MEDIUM.label}", last.emoji) },
                    emoji = last.emoji,
                    color = OsitoColors.Mint,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
            }
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Activities.all, key = { it.id }) { a ->
                    Column(
                        Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { selected = a }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(a.emoji, fontSize = 26.sp)
                        Text(a.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        } else {
            val met = sel.met(intensity)
            val kcal = HealthCalculator.exerciseKcal(met, weightKg, minutes.roundToInt())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sel.emoji, fontSize = 40.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(sel.name, style = MaterialTheme.typography.titleLarge)
                    if (sel.hint.isNotBlank()) Text(sel.hint, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { selected = null }) { Text("Cambiar") }
            }
            Spacer(Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Intensity.entries.forEachIndexed { i, lvl ->
                    SegmentedButton(
                        selected = intensity == lvl,
                        onClick = { intensity = lvl },
                        shape = SegmentedButtonDefaults.itemShape(i, Intensity.entries.size),
                    ) { Text(lvl.label) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("${minutes.roundToInt()} minutos", style = MaterialTheme.typography.titleMedium)
            Slider(value = minutes, onValueChange = { minutes = (it / 5).roundToInt() * 5f }, valueRange = 5f..180f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(OsitoColors.Fire.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) { Text("🔥", fontSize = 30.sp) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("$kcal kcal", style = MaterialTheme.typography.headlineMedium, color = OsitoColors.Fire)
                    Text(
                        "MET ${"%.1f".format(met)} × ${weightKg.fmt1()} kg × ${"%.2f".format(minutes / 60f)} h",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            BouncyButton(
                "Guardar", onClick = {
                    onSave(sel.name, kcal, "${minutes.roundToInt()} min · ${intensity.label}", sel.emoji)
                },
                modifier = Modifier.fillMaxWidth(), color = OsitoColors.Mint, emoji = "💪",
            )
        }
    }
}

// ------------------------------------------------------------------ Peso

@Composable
fun WeightSheet(lastKg: Double, color: Color, onSave: (Double) -> Unit) {
    var kg by remember { mutableFloatStateOf(((lastKg * 10).roundToInt() / 10.0).toFloat()) }
    val ctx = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Pesaje", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Ideal: a la mañana, en ayunas. Lo que importa es la tendencia, no el número de hoy 🌊",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stepper("−") { kg = ((kg - 0.1f) * 10).roundToInt() / 10f; Haptics.tick(ctx) }
            Spacer(Modifier.width(18.dp))
            Text("%.1f".format(kg), style = MaterialTheme.typography.displayMedium, color = color)
            Text(" kg", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(18.dp))
            Stepper("+") { kg = ((kg + 0.1f) * 10).roundToInt() / 10f; Haptics.tick(ctx) }
        }
        Slider(
            value = kg,
            onValueChange = { kg = (it * 10).roundToInt() / 10f },
            valueRange = (lastKg - 5).toFloat()..(lastKg + 5).toFloat(),
        )
        val diff = kg - lastKg
        if (kotlin.math.abs(diff) >= 0.05) {
            Text(
                "${if (diff > 0) "+" else ""}${"%.1f".format(diff)} kg vs. el último registro",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        BouncyButton("Guardar", { onSave(kg.toDouble()) }, Modifier.fillMaxWidth(), color = color, emoji = "⚖️")
    }
}

@Composable
private fun Stepper(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.headlineMedium) }
}
