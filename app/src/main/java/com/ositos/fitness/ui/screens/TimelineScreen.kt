package com.ositos.fitness.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ositos.fitness.data.LogEntry
import com.ositos.fitness.data.LogType
import com.ositos.fitness.domain.Achievements
import com.ositos.fitness.domain.Dates
import com.ositos.fitness.ui.DuoState
import com.ositos.fitness.ui.components.Avatar
import com.ositos.fitness.ui.components.Haptics
import com.ositos.fitness.ui.theme.OsitoColors
import com.ositos.fitness.domain.Xp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

val REACTIONS = listOf("👏", "🔥", "💪", "😂", "❤️")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    s: DuoState,
    padding: PaddingValues,
    logsFor: (String) -> Flow<List<LogEntry>>,
    onReact: (LogEntry, String) -> Unit,
    onDelete: (LogEntry, List<LogEntry>) -> Unit,
) {
    var toDelete by remember { mutableStateOf<LogEntry?>(null) }
    var day by rememberSaveable { mutableStateOf(Dates.todayKey()) }
    val dayLogs by remember(day) { logsFor(day) }.collectAsStateWithLifecycle(emptyList())
    val entries = dayLogs.sortedBy { it.ts }
    val date = runCatching { LocalDate.parse(day) }.getOrDefault(LocalDate.now())
    val me = s.me
    val accent = me?.profile?.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp, end = 12.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Historial", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(4.dp))
        }
        item { DayStrip(day, me?.doneDays ?: emptySet(), accent) { day = it } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { day = Dates.key(date.minusDays(1)) }) { Text("‹ Día anterior") }
                if (date.isBefore(LocalDate.now())) {
                    TextButton(onClick = { day = Dates.key(date.plusDays(1)) }) { Text("Día siguiente ›") }
                }
            }
        }
        item { DaySummary(s, day) }
        if (entries.isEmpty()) item {
            Text(
                if (day == Dates.todayKey()) "Todavía no hay nada hoy. ¡El primero que registra algo gana el honor! 🏅"
                else "Ese día no hay registros 🦗",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
        }
        items(entries, key = { it.id }) { e ->
            Bubble(
                e, s,
                mine = e.uid == s.myUid,
                onReact = { onReact(e, it) },
                onLongPress = { if (e.uid == s.myUid && e.type in deletable) toDelete = e },
            )
        }
        if (entries.any { it.uid == s.myUid && it.type in deletable }) item {
            Text(
                "Mantené apretado un registro tuyo para borrarlo.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar registro?") },
            text = { Text("\"${e.title}\" se va a borrar y se descuenta del día.") },
            confirmButton = { TextButton(onClick = { onDelete(e, dayLogs); toDelete = null }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

/** Resumen del día de los dos: comido, quemado, agua, XP y si cumplieron. */
@Composable
private fun DaySummary(s: DuoState, day: String) {
    val people = listOfNotNull(s.me, s.partner)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        people.forEach { p ->
            val d = p.days.firstOrNull { it.dayKey == day }
            val c = Color(p.profile.color)
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.copy(alpha = 0.14f))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.profile.avatar, c, 30.dp, ring = false)
                    Spacer(Modifier.width(6.dp))
                    Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.weight(1f))
                    if (d != null && Xp.dayDone(d)) Text("✅")
                }
                Spacer(Modifier.height(6.dp))
                Text("🍽️ ${d?.kcalIn ?: 0} kcal", style = MaterialTheme.typography.bodySmall)
                Text("🔥 ${d?.kcalOut ?: 0} kcal quemadas", style = MaterialTheme.typography.bodySmall)
                Text("💧 ${d?.water ?: 0} vasos", style = MaterialTheme.typography.bodySmall)
                Text(
                    "⭐ +${d?.let { Xp.forDay(it) } ?: 0} XP",
                    style = MaterialTheme.typography.labelLarge,
                    color = c,
                )
            }
        }
    }
}

private val deletable = setOf(LogType.MEAL, LogType.EXERCISE, LogType.WEIGHT)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(e: LogEntry, s: DuoState, mine: Boolean, onReact: (String) -> Unit, onLongPress: () -> Unit) {
    val person = s.personOf(e.uid)
    val color = person?.profile?.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val time = Instant.ofEpochMilli(e.ts).atZone(ZoneId.systemDefault()).toLocalTime()
    val (emoji, title, detail) = when (e.type) {
        LogType.ACHIEVEMENT -> Achievements.byId(e.detail).let {
            Triple(it?.emoji ?: "🏅", "Desbloqueó: ${it?.name ?: e.detail}", it?.description ?: "")
        }
        LogType.MEAL -> Triple(
            e.emoji.ifBlank { "🍽️" },
            e.title,
            listOf("${e.mealSlot.emoji} ${e.mealSlot.label}", e.detail, "${e.kcal} kcal").filter { it.isNotBlank() }.joinToString(" · "),
        )
        LogType.EXERCISE -> Triple(e.emoji.ifBlank { "💪" }, e.title, "${e.detail} · 🔥 ${e.kcal} kcal")
        else -> Triple(e.emoji, e.title, e.detail)
    }
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!mine) {
            Avatar(person?.profile?.avatar ?: "🐻", color, 34.dp, ring = false)
            Spacer(Modifier.width(6.dp))
        }
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp,
                    ),
                )
                .background(color.copy(alpha = if (mine) 0.22f else 0.12f))
                .combinedClickable(onClick = {}, onLongClick = { Haptics.tick(ctx); onLongPress() })
                .padding(12.dp)
                .animateContentSize(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 20.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${if (mine) "Vos" else person?.name ?: "Tu compa"} · ${"%02d:%02d".format(time.hour, time.minute)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            typeLabel(e.type)?.let { (label, c) ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = c,
                    modifier = Modifier
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(c.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 1.dp),
                )
            }
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            ReactionsRow(e, s, canReact = !mine, onReact = onReact)
        }
    }
}

private fun typeLabel(t: LogType): Pair<String, Color>? = when (t) {
    LogType.MEAL -> "COMIDA" to OsitoColors.Orange
    LogType.EXERCISE -> "EJERCICIO" to OsitoColors.Mint
    LogType.WEIGHT -> "PESO" to OsitoColors.Purple
    LogType.MEASURE -> "MEDIDAS" to OsitoColors.Yellow
    LogType.POKE -> "PINCHAZO" to OsitoColors.Pink
    LogType.ACHIEVEMENT -> "LOGRO" to OsitoColors.Yellow
    LogType.DUEL -> "DUELO" to OsitoColors.Fire
}

@Composable
private fun ReactionsRow(e: LogEntry, s: DuoState, canReact: Boolean, onReact: (String) -> Unit) {
    val ctx = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (canReact) {
            REACTIONS.take(3).forEach { r ->
                val selected = e.reactions[s.myUid] == r
                val scale by animateFloatAsState(if (selected) 1.25f else 1f, spring(Spring.DampingRatioHighBouncy), label = "r")
                Text(
                    r,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .scale(scale)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent)
                        .clickable { Haptics.tick(ctx); onReact(r) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        } else {
            e.reactions.values.forEach { r ->
                Text(r, fontSize = 18.sp)
            }
            if (e.reactions.isNotEmpty()) {
                Text(
                    "de ${s.partner?.name ?: "tu compa"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
