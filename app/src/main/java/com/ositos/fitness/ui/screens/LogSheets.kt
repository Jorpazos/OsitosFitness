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
import com.ositos.fitness.domain.FoodItem
import com.ositos.fitness.domain.FoodUnit
import com.ositos.fitness.domain.Foods
import com.ositos.fitness.domain.HealthCalculator
import com.ositos.fitness.domain.Intensity
import com.ositos.fitness.domain.MealSlot
import com.ositos.fitness.domain.Portions
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

// ------------------------------------------------------------------ Comida

/** Lo que se carga en el diario: un alimento (con su cantidad ya calculada) en una comida del día. */
data class MealInput(val name: String, val kcal: Int, val detail: String, val emoji: String, val slot: MealSlot)

/** Selector de comida del día: desayuno, almuerzo, merienda o cena. */
@Composable
fun SlotPicker(selected: MealSlot, onSelect: (MealSlot) -> Unit) {
    val ctx = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        MealSlot.entries.forEach { slot ->
            val on = slot == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (on) OsitoColors.Orange.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { Haptics.tick(ctx); onSelect(slot) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(slot.emoji, fontSize = 20.sp)
                Text(
                    slot.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (on) OsitoColors.Orange else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Cargar comida: elegís la comida del día, buscás el alimento y ajustás la cantidad con − / +
 * (unidades, gramos o mililitros). Podés cargar varias cosas seguidas: 2 huevos + 1 tostada
 * en el desayuno cuentan como UNA comida.
 */
@Composable
fun MealSheet(initialSlot: MealSlot, onAdd: (MealInput) -> Unit, onDone: () -> Unit) {
    var slot by rememberSaveable { mutableStateOf(initialSlot) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<FoodItem?>(null) }
    var manual by rememberSaveable { mutableStateOf(false) }
    val added = remember { mutableStateListOf<String>() }
    val results = remember(query) { Foods.search(query) }

    fun add(input: MealInput) {
        onAdd(input)
        added += "${input.emoji} ${input.name} · ${input.kcal} kcal (${input.slot.label.lowercase()})"
        selected = null
        manual = false
        query = ""
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Comida", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (added.isNotEmpty()) {
                BouncyButton("Listo", onDone, color = OsitoColors.Mint, emoji = "✅")
            }
        }
        Spacer(Modifier.height(8.dp))
        SlotPicker(slot) { slot = it }
        if (added.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(OsitoColors.Mint.copy(alpha = 0.12f))
                    .padding(10.dp),
            ) {
                Text("Ya agregaste:", style = MaterialTheme.typography.labelMedium, color = OsitoColors.Mint)
                added.forEach { Text("✓ $it", style = MaterialTheme.typography.bodySmall) }
                Text(
                    "Podés seguir sumando cosas a la misma comida 😉",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        val sel = selected
        when {
            sel != null -> FoodAmountEditor(sel, slot, onBack = { selected = null }, onAdd = ::add)
            manual -> ManualFoodEditor(query, slot, onBack = { manual = false }, onAdd = ::add)
            else -> {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar: milanesa, huevo, mate…") },
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
                                .clickable { selected = f }
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
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(OsitoColors.Orange),
                                contentAlignment = Alignment.Center,
                            ) { Text("+", color = Color.White, style = MaterialTheme.typography.titleMedium) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                BouncyButton(
                    if (query.isBlank()) "¿No está? Cargalo a mano" else "Cargar \"${query.trim()}\" a mano",
                    { manual = true },
                    Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    emoji = "✍️",
                )
            }
        }
    }
}

/** Cantidad con − y + (y escribible), en la unidad elegida. */
@Composable
private fun AmountRow(amountText: String, unit: FoodUnit, onText: (String) -> Unit, onStep: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        Stepper("−") { Haptics.tick(ctx); onStep(false) }
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = amountText,
            onValueChange = { v -> onText(v.filter { it.isDigit() || it == ',' || it == '.' }.take(6)) },
            singleLine = true,
            suffix = { Text(unit.short) },
            textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(130.dp),
        )
        Spacer(Modifier.width(12.dp))
        Stepper("+") { Haptics.tick(ctx); onStep(true) }
    }
}

private fun parseAmount(text: String): Double = text.replace(',', '.').toDoubleOrNull() ?: 0.0

private fun quickAmounts(unit: FoodUnit): List<Double> = when (unit) {
    FoodUnit.COUNT -> listOf(0.5, 1.0, 2.0, 3.0)
    FoodUnit.GRAMS -> listOf(50.0, 100.0, 150.0, 200.0)
    FoodUnit.ML -> listOf(100.0, 200.0, 250.0, 500.0)
}

@Composable
private fun UnitAndAmount(
    units: List<FoodUnit>,
    unit: FoodUnit,
    amountText: String,
    onUnit: (FoodUnit) -> Unit,
    onAmountText: (String) -> Unit,
) {
    if (units.size > 1) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            units.forEachIndexed { i, u ->
                SegmentedButton(
                    selected = unit == u,
                    onClick = { onUnit(u) },
                    shape = SegmentedButtonDefaults.itemShape(i, units.size),
                ) { Text(u.label, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    AmountRow(amountText, unit, onAmountText) { up ->
        onAmountText(Portions.fmt(Portions.step(parseAmount(amountText), unit, up)))
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
        quickAmounts(unit).forEach { q ->
            Pill(
                "${Portions.fmt(q)} ${unit.short}",
                OsitoColors.Purple,
                Modifier.clickable { onAmountText(Portions.fmt(q)) },
            )
        }
    }
}

/** Un alimento del catálogo: elegís unidad y cantidad, y ves las kcal al toque. */
@Composable
private fun FoodAmountEditor(f: FoodItem, slot: MealSlot, onBack: () -> Unit, onAdd: (MealInput) -> Unit) {
    val units = remember(f) { Portions.unitsFor(f) }
    var unit by remember(f) { mutableStateOf(FoodUnit.COUNT) }
    var amountText by remember(f) { mutableStateOf("1") }
    val amount = parseAmount(amountText)
    val kcal = Portions.kcal(f, unit, amount)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Volver") }
            Spacer(Modifier.width(4.dp))
            Text(f.emoji, fontSize = 28.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(f.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${f.portion} = ${f.kcal} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        UnitAndAmount(units, unit, amountText, onUnit = {
            unit = it
            amountText = Portions.fmt(Portions.defaultAmount(f, it))
        }, onAmountText = { amountText = it })
        Spacer(Modifier.height(12.dp))
        Text(
            "$kcal kcal",
            style = MaterialTheme.typography.headlineMedium,
            color = OsitoColors.Orange,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(10.dp))
        BouncyButton(
            "Agregar a ${slot.label.lowercase()}",
            {
                onAdd(
                    MealInput(
                        name = Portions.title(f.name, unit, amount),
                        kcal = kcal,
                        detail = Portions.describe(f, unit, amount),
                        emoji = f.emoji,
                        slot = slot,
                    ),
                )
            },
            Modifier.fillMaxWidth(),
            enabled = amount > 0,
            color = OsitoColors.Orange,
            emoji = "➕",
        )
    }
}

/** Algo que no está en la lista: nombre, kcal (por unidad o cada 100 g/ml, como en la etiqueta) y cantidad. */
@Composable
private fun ManualFoodEditor(initialName: String, slot: MealSlot, onBack: () -> Unit, onAdd: (MealInput) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName.trim()) }
    var unit by rememberSaveable { mutableStateOf(FoodUnit.COUNT) }
    var kcalText by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("1") }
    val amount = parseAmount(amountText)
    val total = Portions.manualKcal(kcalText.toIntOrNull() ?: 0, unit, amount)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Volver") }
            Text("Carga a mano ✍️", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text("¿Qué comiste?") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = kcalText,
            onValueChange = { v -> kcalText = v.filter { it.isDigit() }.take(5) },
            label = {
                Text(
                    when (unit) {
                        FoodUnit.COUNT -> "kcal por unidad"
                        FoodUnit.GRAMS -> "kcal cada 100 g (mirá la etiqueta)"
                        FoodUnit.ML -> "kcal cada 100 ml (mirá la etiqueta)"
                    },
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        UnitAndAmount(FoodUnit.entries, unit, amountText, onUnit = {
            unit = it
            amountText = when (it) {
                FoodUnit.COUNT -> "1"
                FoodUnit.GRAMS -> "100"
                FoodUnit.ML -> "200"
            }
        }, onAmountText = { amountText = it })
        Spacer(Modifier.height(12.dp))
        Text(
            "$total kcal",
            style = MaterialTheme.typography.headlineMedium,
            color = OsitoColors.Orange,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(10.dp))
        BouncyButton(
            "Agregar a ${slot.label.lowercase()}",
            {
                val n = name.ifBlank { "Comida" }
                onAdd(
                    MealInput(
                        name = Portions.title(n, unit, amount),
                        kcal = total,
                        detail = "Carga manual · " + Portions.describe(null, unit, amount),
                        emoji = "🍽️",
                        slot = slot,
                    ),
                )
            },
            Modifier.fillMaxWidth(),
            enabled = total > 0,
            color = OsitoColors.Orange,
            emoji = "➕",
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
    initialSlot: MealSlot,
    onAnalyze: (Uri) -> Unit,
    onReset: () -> Unit,
    onSave: (name: String, kcal: Int, detail: String, slot: MealSlot) -> Unit,
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
    val canUse = usedToday < AiRepository.DAILY_LIMIT

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
                // Las dos opciones a la vista desde el principio: cámara o galería.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PhotoOption("📷", "Tomar foto", "Con la cámara", OsitoColors.Pink, canUse, Modifier.weight(1f)) {
                        Haptics.tick(ctx); openCamera()
                    }
                    PhotoOption("🖼️", "Abrir galería", "Una foto que ya tenés", OsitoColors.Purple, canUse, Modifier.weight(1f)) {
                        Haptics.tick(ctx)
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                if (!canUse) {
                    Spacer(Modifier.height(10.dp))
                    Text("Llegaste al límite de fotos de hoy. Mañana más 📸", style = MaterialTheme.typography.bodyMedium)
                }
            }
            PhotoState.Analyzing -> Analyzing()
            is PhotoState.Error -> {
                Text("😵 ${state.message}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                BouncyButton("Probar de nuevo", onReset, Modifier.fillMaxWidth())
            }
            is PhotoState.Result -> EditableResult(state, initialSlot, onSave, onRetry = onReset)
        }
    }
}

@Composable
private fun PhotoOption(
    emoji: String,
    title: String,
    subtitle: String,
    color: Color,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(color.copy(alpha = if (enabled) 0.18f else 0.06f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 22.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 40.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = color)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
    initialSlot: MealSlot,
    onSave: (name: String, kcal: Int, detail: String, slot: MealSlot) -> Unit,
    onRetry: () -> Unit,
) {
    val items = remember(state) {
        mutableStateListOf<AiFood>().apply { addAll(state.result.foods) }
    }
    var slot by rememberSaveable { mutableStateOf(initialSlot) }
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
    Spacer(Modifier.height(10.dp))
    Text("¿En qué comida va?", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    SlotPicker(slot) { slot = it }
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
                onSave(name, total, detail, slot)
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
