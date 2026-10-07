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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

val REACTIONS = listOf("👏", "🔥", "💪", "😂", "❤️")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(s: DuoState, padding: PaddingValues, onReact: (LogEntry, String) -> Unit, onDelete: (LogEntry) -> Unit) {
    var toDelete by remember { mutableStateOf<LogEntry?>(null) }
    val grouped = s.logs.groupBy { it.dayKey }
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
        if (s.logs.isEmpty()) item {
            Text(
                "Todavía no hay nada. ¡El primero que registra algo gana el honor! 🏅",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
        }
        grouped.forEach { (day, entries) ->
            item(key = "h_$day") { DayHeader(day) }
            items(entries, key = { it.id }) { e ->
                Bubble(
                    e, s,
                    mine = e.uid == s.myUid,
                    onReact = { onReact(e, it) },
                    onLongPress = { if (e.uid == s.myUid && e.type in deletable) toDelete = e },
                )
            }
        }
    }
    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar registro?") },
            text = { Text("\"${e.title}\" se va a borrar y se descuenta del día.") },
            confirmButton = { TextButton(onClick = { onDelete(e); toDelete = null }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

private val deletable = setOf(LogType.MEAL, LogType.EXERCISE, LogType.WEIGHT)

@Composable
private fun DayHeader(dayKey: String) {
    val date = runCatching { LocalDate.parse(dayKey) }.getOrNull()
    val label = when (date) {
        null -> dayKey
        LocalDate.now() -> "Hoy"
        LocalDate.now().minusDays(1) -> "Ayer"
        else -> Dates.prettyLong(date).replaceFirstChar { it.uppercase() }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

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
        LogType.MEAL -> Triple(e.emoji.ifBlank { "🍽️" }, e.title, listOf(e.detail, "${e.kcal} kcal").filter { it.isNotBlank() }.joinToString(" · "))
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
                    "${if (mine) "Vos" else person?.name ?: "Tu pareja"} · ${"%02d:%02d".format(time.hour, time.minute)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            ReactionsRow(e, s, canReact = !mine, onReact = onReact)
        }
    }
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
                    "de ${s.partner?.name ?: "tu pareja"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
