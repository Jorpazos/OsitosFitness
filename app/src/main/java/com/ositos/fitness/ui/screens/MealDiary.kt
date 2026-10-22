package com.ositos.fitness.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ositos.fitness.data.LogEntry
import com.ositos.fitness.data.LogType
import com.ositos.fitness.domain.DayStats
import com.ositos.fitness.domain.Dates
import com.ositos.fitness.domain.HealthCalculator
import com.ositos.fitness.domain.MealPlan
import com.ositos.fitness.domain.MealSlot
import com.ositos.fitness.domain.OsitoChef
import com.ositos.fitness.ui.DuoState
import com.ositos.fitness.ui.components.BouncyButton
import com.ositos.fitness.ui.components.FillBar
import com.ositos.fitness.ui.components.GameCard
import com.ositos.fitness.ui.components.Haptics
import com.ositos.fitness.ui.components.Pill
import com.ositos.fitness.ui.theme.OsitoColors

/**
 * Diario del día (estilo YAZIO): desayuno, almuerzo, merienda y cena, cada una con las kcal
 * recomendadas según tu meta, lo que ya comiste y lo que te queda. Cada alimento se puede borrar.
 */
@Composable
fun MealDiaryCard(
    s: DuoState,
    day: DayStats,
    logs: List<LogEntry>,
    editable: Boolean,
    onAdd: (MealSlot) -> Unit,
    onDelete: (LogEntry) -> Unit,
    onChefIdea: (OsitoChef.Idea, MealSlot) -> Unit,
    onAddExercise: () -> Unit = {},
) {
    val me = s.me ?: return
    val isToday = day.dayKey == Dates.todayKey()
    val budget = day.goalKcal + day.kcalOut
    val mine = logs.filter { it.uid == me.profile.uid }
    val bySlot = mine.filter { it.type == LogType.MEAL }.sortedBy { it.ts }.groupBy { it.mealSlot }
    val exercises = mine.filter { it.type == LogType.EXERCISE }.sortedBy { it.ts }
    val eaten = day.kcalIn
    val left = budget - eaten
    var toDelete by remember { mutableStateOf<LogEntry?>(null) }
    var chef by remember { mutableStateOf(false) }
    val p = me.profile
    val currentKg = me.weights.lastOrNull()?.kg ?: p.weightKg
    val bmi = HealthCalculator.bmi(currentKg, p.heightCm)

    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isToday) "📒 Diario de hoy" else "📒 Diario del día",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Pill("$eaten / $budget kcal", if (left < 0) OsitoColors.Bad else OsitoColors.Orange)
        }
        Text(
            when {
                !isToday && left >= 0 -> "Ese día te sobraron $left kcal"
                !isToday -> "Ese día te pasaste ${-left} kcal"
                left >= 0 -> "Te quedan $left kcal para hoy"
                else -> "Te pasaste ${-left} kcal hoy (mañana se compensa 💪)"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (left >= 0) MaterialTheme.colorScheme.onSurfaceVariant else OsitoColors.Bad,
        )
        Spacer(Modifier.height(8.dp))
        MealSlot.entries.forEachIndexed { i, slot ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            SlotSection(
                slot = slot,
                recommended = MealPlan.recommended(slot, budget),
                entries = bySlot[slot].orEmpty(),
                editable = editable,
                onAdd = { onAdd(slot) },
                onDelete = { toDelete = it },
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        ExerciseSection(exercises, day.kcalOut, editable, onAddExercise, onDelete = { toDelete = it })
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Recomendado según tu meta de ${p.goalKcal} kcal + lo que entrenaste " +
                    "(sale de tu peso, altura, edad y actividad · IMC ${"%.1f".format(bmi)}).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (isToday) {
                Spacer(Modifier.width(8.dp))
                BouncyButton("Osito Chef", { chef = true }, color = OsitoColors.Purple, emoji = "🎲")
            }
        }
    }

    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar del diario?") },
            text = {
                Text(
                    if (e.type == LogType.MEAL) "\"${e.title}\" (${e.kcal} kcal) se borra de ${e.mealSlot.label.lowercase()}."
                    else "\"${e.title}\" (${e.kcal} kcal) se borra del día.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(e); toDelete = null }) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }

    if (chef) {
        OsitoChefDialog(
            s = s,
            onDismiss = { chef = false },
            onLoad = { idea, slot -> onChefIdea(idea, slot); chef = false },
        )
    }
}

@Composable
private fun SlotSection(
    slot: MealSlot,
    recommended: Int,
    entries: List<LogEntry>,
    editable: Boolean,
    onAdd: () -> Unit,
    onDelete: (LogEntry) -> Unit,
) {
    val ctx = LocalContext.current
    val eaten = entries.sumOf { it.kcal }
    val frac = if (recommended > 0) eaten.toFloat() / recommended else 0f
    val over = eaten > recommended * 1.15
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(OsitoColors.Orange.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Text(slot.emoji, fontSize = 22.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(slot.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        "$eaten / $recommended kcal",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (over) OsitoColors.Bad else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                FillBar(frac, if (over) OsitoColors.Bad else OsitoColors.Orange, height = 8.dp)
                Text(
                    when {
                        entries.isEmpty() -> "Recomendado: ~$recommended kcal"
                        eaten < recommended -> "Te quedan ${recommended - eaten} kcal en ${slot.label.lowercase()}"
                        over -> "Te pasaste ${eaten - recommended} kcal en ${slot.label.lowercase()}"
                        else -> "¡${slot.label} completo! ✅"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (editable) {
                Spacer(Modifier.width(10.dp))
                AddCircle(OsitoColors.Orange) { Haptics.tick(ctx); onAdd() }
            }
        }
        entries.forEach { e -> EntryRow(e, editable) { onDelete(e) } }
    }
}

@Composable
private fun AddCircle(color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("+", style = MaterialTheme.typography.titleLarge, color = Color.White) }
}

/** Ejercicios del día, con su "+" para sumar y borrar (como las comidas). */
@Composable
private fun ExerciseSection(
    entries: List<LogEntry>,
    kcalOut: Int,
    editable: Boolean,
    onAdd: () -> Unit,
    onDelete: (LogEntry) -> Unit,
) {
    val ctx = LocalContext.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(OsitoColors.Mint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Text("💪", fontSize = 22.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Ejercicio", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (entries.isEmpty()) "Nada todavía · cada kcal que quemás te suma margen"
                    else "🔥 $kcalOut kcal quemadas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (editable) {
                Spacer(Modifier.width(10.dp))
                AddCircle(OsitoColors.Mint) { Haptics.tick(ctx); onAdd() }
            }
        }
        entries.forEach { e -> EntryRow(e, editable) { onDelete(e) } }
    }
}

@Composable
private fun EntryRow(e: LogEntry, editable: Boolean, onDelete: () -> Unit) {
    Row(
        Modifier.padding(start = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(e.emoji.ifBlank { "🍽️" }, fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (e.detail.isNotBlank()) {
                Text(
                    e.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text("${e.kcal}", style = MaterialTheme.typography.labelLarge)
        if (editable) {
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Borrar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** Osito Chef 🎲: una idea de comida que entra en lo que te queda. "Otra idea" la cambia. */
@Composable
private fun OsitoChefDialog(s: DuoState, onDismiss: () -> Unit, onLoad: (OsitoChef.Idea, MealSlot) -> Unit) {
    val me = s.me ?: return
    var slot by remember { mutableStateOf(MealSlot.now()) }
    var seed by remember { mutableIntStateOf((System.currentTimeMillis() / 1000).toInt()) }
    val eatenInSlot = s.mealsOf(me.profile.uid)[slot].orEmpty().sumOf { it.kcal }
    val slotLeft = MealPlan.recommended(slot, me.budget) - eatenInSlot
    val dayLeft = me.remaining
    val target = minOf(slotLeft, dayLeft)
    val idea = remember(slot, seed, target) { OsitoChef.suggest(slot, target, seed) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("👨‍🍳 Osito Chef") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SlotPicker(slot) { slot = it }
                Text(
                    if (target > 0) "Para ${slot.label.lowercase()} te quedan ~$target kcal. Te propongo:"
                    else "Ya llegaste a lo recomendado: si tenés hambre, algo liviano:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                idea.items.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.emoji, fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.titleSmall)
                            Text(f.portion, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${f.kcal}", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(
                    "Total: ${idea.kcal} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    color = OsitoColors.Purple,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onLoad(idea, slot) }) { Text("Cargar en ${slot.label.lowercase()}") }
        },
        dismissButton = {
            TextButton(onClick = { seed += 1 }) { Text("Otra idea 🎲") }
        },
    )
}
