package com.ositos.fitness.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ositos.fitness.data.CoopType
import com.ositos.fitness.domain.Achievements
import com.ositos.fitness.domain.Dates
import com.ositos.fitness.domain.Xp
import com.ositos.fitness.ui.DuoState
import com.ositos.fitness.ui.PersonSummary
import com.ositos.fitness.ui.components.FillBar
import com.ositos.fitness.ui.components.GameCard
import com.ositos.fitness.ui.components.Pill
import com.ositos.fitness.ui.components.SectionTitle
import com.ositos.fitness.ui.components.StreakFlame
import com.ositos.fitness.ui.theme.OsitoColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Composable
fun GameScreen(
    s: DuoState,
    padding: PaddingValues,
    onForfeit: (String) -> Unit,
    onEditForfeits: () -> Unit,
    onCoop: (CoopType, Int) -> Unit,
) {
    val me = s.me ?: return
    var showCoopEditor by remember { mutableStateOf(false) }
    var achievementsOf by remember { mutableStateOf(me.profile.uid) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("El juego", style = MaterialTheme.typography.headlineMedium) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StreakCard("Tu racha", me.streak, "récord ${me.bestStreak}", Modifier.weight(1f), "🔥")
                if (s.partner != null) {
                    StreakCard("Racha del dúo", s.coupleStreak, "récord ${s.bestCoupleStreak}", Modifier.weight(1f), "💞")
                }
            }
        }

        item { QuestsCard(s) }

        if (s.partner != null) {
            item { FlashDuelCard(s) }
            item { OseraCard(s) }
            item { BingoCard(s) }
        }

        val partner = s.partner
        if (partner != null) {
            item {
                val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), Dates.weekDays(LocalDate.now()).last()).toInt()
                GameCard(accent = OsitoColors.Pink) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🥊 Duelo semanal", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Pill(if (daysLeft == 0) "¡Último día!" else "Quedan $daysLeft días", OsitoColors.Pink)
                    }
                    Text(
                        "Gana quien junte más XP de hábitos. El que pierde cumple la prenda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    DuelBars(me, partner)
                    Spacer(Modifier.height(8.dp))
                    val diff = me.weekXp - partner.weekXp
                    Text(
                        when {
                            diff > 0 -> "Vas ganando por $diff XP 😎"
                            diff < 0 -> "${partner.name} te saca ${-diff} XP. ¡A remontar! 💨"
                            else -> "Empate técnico 🤝"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    ForfeitPicker(s, onForfeit, onEditForfeits)
                }
            }

            item {
                val c = s.coop
                GameCard(accent = OsitoColors.Mint) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤝 Desafío en equipo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showCoopEditor = true }) { Text("Cambiar") }
                    }
                    if (c != null) {
                        Text("${c.type.emoji} ${c.type.label}: ${c.target} ${c.type.unit}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        FillBar(c.fraction, OsitoColors.Mint, height = 20.dp)
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Text("${c.current} / ${c.target}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(
                                if (c.done) "¡Completado! 🎉" else "${(c.fraction * 100).roundToInt()}%",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (c.done) OsitoColors.Good else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        item {
            GameCard {
                Text("¿Cómo se gana XP?", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Por hábitos, no por kilos: así compiten parejo aunque tengan cuerpos y metas distintas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Xp.rules.forEach { (a, b) ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(a, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(b, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item {
            SectionTitle("Logros") {
                if (partner != null) {
                    FilterChip(achievementsOf == me.profile.uid, { achievementsOf = me.profile.uid }, { Text("Míos") })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(achievementsOf == partner.profile.uid, { achievementsOf = partner.profile.uid }, { Text(partner.name) })
                }
            }
        }
        item {
            val who: PersonSummary = s.personOf(achievementsOf) ?: me
            AchievementsGrid(who)
        }

        val closed = s.weeks.filter { it.closed }.sortedByDescending { it.key }
        if (closed.isNotEmpty()) {
            item { SectionTitle("Duelos anteriores") }
            closed.take(12).forEach { w ->
                item(key = "w_${w.key}") {
                    GameCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (w.winnerUid == null) "🤝" else "🏆", fontSize = 24.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (w.winnerUid == null) "Empate" else "Ganó ${s.nameOf(w.winnerUid)}",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    "${w.key} · " + w.xp.entries.joinToString(" vs ") { "${s.nameOf(it.key)} ${it.value}" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                w.forfeit?.let { Text("Prenda: $it", style = MaterialTheme.typography.bodySmall) }
                            }
                            if (w.coopAchieved) Text("🤝✅")
                        }
                    }
                }
            }
        }
    }

    if (showCoopEditor) CoopEditor(s, onDismiss = { showCoopEditor = false }, onSave = { t, n ->
        onCoop(t, n)
        showCoopEditor = false
    })
}

@Composable
private fun StreakCard(title: String, days: Int, sub: String, modifier: Modifier, emoji: String) {
    GameCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (emoji == "🔥") StreakFlame(days, size = 34.dp) else Text(emoji, fontSize = 30.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("$days días", style = MaterialTheme.typography.titleLarge)
                Text(title, style = MaterialTheme.typography.labelMedium)
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForfeitPicker(s: DuoState, onForfeit: (String) -> Unit, onEdit: () -> Unit) {
    val chosen = s.currentWeek.forfeit
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Prenda de la semana", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onEdit) { Text("Editar lista") }
    }
    if (chosen != null) {
        Text(
            "🎲 $chosen (eligió ${s.nameOf(s.currentWeek.forfeitBy)})",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(6.dp))
    } else {
        Text("Elijan la prenda para esta semana 👇", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (s.duo?.forfeits ?: emptyList()).forEach { f ->
            FilterChip(selected = f == chosen, onClick = { onForfeit(f) }, label = { Text(f) })
        }
    }
}

@Composable
private fun AchievementsGrid(p: PersonSummary) {
    val unlocked = p.profile.achievements
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${unlocked.size} de ${Achievements.all.size} desbloqueados",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Achievements.all.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { a ->
                    val has = a.id in unlocked
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (has) Color(p.profile.color).copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surfaceContainer,
                            )
                            .padding(10.dp)
                            .alpha(if (has) 1f else 0.45f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (has) a.emoji else "🔒", fontSize = 28.sp)
                        Text(a.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
                        Text(
                            a.description,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CoopEditor(s: DuoState, onDismiss: () -> Unit, onSave: (CoopType, Int) -> Unit) {
    var typeIdx by remember { mutableIntStateOf(CoopType.entries.indexOf(s.currentWeek.coopType)) }
    val type = CoopType.entries[typeIdx]
    var target by remember(typeIdx) {
        mutableFloatStateOf(if (type == s.currentWeek.coopType) s.currentWeek.coopTarget.toFloat() else type.defaultTarget.toFloat())
    }
    val range = when (type) {
        CoopType.BURN -> 500f..10000f
        CoopType.WORKOUTS -> 2f..30f
        CoopType.WATER -> 20f..140f
        CoopType.MEALS -> 10f..60f
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Desafío en equipo") },
        text = {
            Column {
                CoopType.entries.forEachIndexed { i, t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { typeIdx = i }
                            .background(if (i == typeIdx) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .padding(10.dp),
                    ) { Text("${t.emoji} ${t.label}") }
                }
                Spacer(Modifier.height(10.dp))
                Text("Meta: ${target.roundToInt()} ${type.unit}", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = target.coerceIn(range.start, range.endInclusive),
                    onValueChange = { v ->
                        val step = if (type == CoopType.BURN) 100 else 1
                        target = ((v / step).roundToInt() * step).toFloat()
                    },
                    valueRange = range,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(type, target.roundToInt()) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Editor genérico de listas (mensajes de pinchazo, prendas). */
@Composable
fun ListEditorDialog(title: String, initial: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    val items = remember { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(initial) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.height(380.dp)) {
                items.indices.forEach { i ->
                    item(key = "i$i") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.OutlinedTextField(
                                value = items[i],
                                onValueChange = { items[i] = it.take(80) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).padding(vertical = 3.dp),
                            )
                            TextButton(onClick = { items.removeAt(i) }) { Text("✕") }
                        }
                    }
                }
                item { TextButton(onClick = { items.add("") }) { Text("+ Agregar") } }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(items.toList()) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
