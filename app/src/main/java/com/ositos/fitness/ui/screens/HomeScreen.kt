package com.ositos.fitness.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ositos.fitness.domain.Dates
import com.ositos.fitness.domain.Xp
import com.ositos.fitness.ui.DuoState
import com.ositos.fitness.ui.PersonSummary
import com.ositos.fitness.ui.components.AnimatedCounter
import com.ositos.fitness.ui.components.Avatar
import com.ositos.fitness.ui.components.BouncyButton
import com.ositos.fitness.ui.components.FillBar
import com.ositos.fitness.ui.components.GameCard
import com.ositos.fitness.ui.components.Haptics
import com.ositos.fitness.ui.components.KcalRing
import com.ositos.fitness.ui.components.Pill
import com.ositos.fitness.ui.components.SectionTitle
import com.ositos.fitness.ui.components.StreakFlame
import com.ositos.fitness.ui.components.WeightChart
import com.ositos.fitness.ui.theme.OsitoColors
import java.time.DayOfWeek

@Composable
fun HomeScreen(
    s: DuoState,
    padding: PaddingValues,
    onWater: (Int) -> Unit,
    onPoke: () -> Unit,
    onOpenDuel: () -> Unit,
    onOpenWrapped: () -> Unit,
) {
    val me = s.me ?: return
    val myColor = Color(me.profile.color)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Header(s, me) }

        if (s.coupleAtRisk) item {
            GameCard(accent = OsitoColors.Fire) {
                Text("🔥 La racha de los dos en peligro", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (me.today.let { Xp.dayDone(it) }) "Vos ya cumpliste. Falta ${s.partner?.name}. ¿Un pinchacito?"
                    else "Todavía te falta cumplir el día. ¡Vamos que se pierde para los dos!",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (me.today.let { Xp.dayDone(it) }) {
                    Spacer(Modifier.height(8.dp))
                    BouncyButton("Pinchar", onPoke, emoji = "📌", color = OsitoColors.Fire)
                }
            }
        }

        val dow = Dates.today().dayOfWeek
        if (dow == DayOfWeek.SUNDAY || dow == DayOfWeek.MONDAY) item {
            GameCard(accent = OsitoColors.Purple, onClick = onOpenWrapped) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎁", fontSize = 32.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Su resumen semanal está listo", style = MaterialTheme.typography.titleMedium)
                        Text("Tocá para ver el wrapped de la semana", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            GameCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hoy", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Pill("+${Xp.forDay(me.today)} XP", myColor)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    KcalRing(me.today.kcalIn, me.today.goalKcal, me.today.kcalOut, myColor)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    MiniStat("🍽️", "${me.today.kcalIn}", "comido")
                    MiniStat("🔥", "${me.today.kcalOut}", "ejercicio")
                    MiniStat("🎯", "${me.today.goalKcal}", "meta")
                }
                Spacer(Modifier.height(12.dp))
                WaterRow(me.today.water, onWater)
            }
        }

        item { QuestsCard(s, compact = true) }

        item { SectionTitle("Vos | ${s.partner?.name ?: "Tu compa"}") }
        item { SplitView(s, onPoke) }

        item {
            GameCard {
                Text("Peso (tendencia 7 días)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val series = listOfNotNull(me, s.partner).map { Color(it.profile.color) to it.weights }
                val targets = listOfNotNull(me, s.partner).map { Color(it.profile.color) to it.profile.targetWeightKg }
                WeightChart(series, targets)
                me.eta?.let {
                    Spacer(Modifier.height(8.dp))
                    EtaInfo(it, me.profile)
                }
            }
        }

        if (s.partner != null) item {
            GameCard(onClick = onOpenDuel) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🥊 Duelo de la semana", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("ver ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
                DuelBars(me, s.partner)
                s.currentWeek.forfeit?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("Prenda en juego: $it", style = MaterialTheme.typography.bodySmall)
                }
                s.coop?.let { c ->
                    Spacer(Modifier.height(12.dp))
                    Text("${c.type.emoji} ${c.type.label}", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    FillBar(c.fraction, OsitoColors.Mint)
                    Text("${c.current} / ${c.target} ${c.type.unit}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Header(s: DuoState, me: PersonSummary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(me.profile.avatar, Color(me.profile.color), 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Hola, ${me.name}", style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${me.level.emoji} Nivel ${me.level.number} · ${me.level.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            FillBar(me.levelProgress, Color(me.profile.color), height = 8.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StreakFlame(me.streak)
            Text("${me.streak}", style = MaterialTheme.typography.labelLarge)
        }
        if (s.partner != null) {
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("💞", fontSize = 24.sp)
                Text("${s.coupleStreak}", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun MiniStat(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 20.sp)
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WaterRow(water: Int, onWater: (Int) -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(OsitoColors.Water.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("💧", fontSize = 22.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("Agua: $water vasos", style = MaterialTheme.typography.titleSmall)
            Row {
                repeat(Xp.MAX_WATER) { i ->
                    Box(
                        Modifier
                            .padding(end = 3.dp, top = 4.dp)
                            .size(width = 14.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(if (i < water) OsitoColors.Water else MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { Haptics.tick(ctx); onWater(-1) },
            contentAlignment = Alignment.Center,
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(OsitoColors.Water)
                .clickable { Haptics.tick(ctx); onWater(1) },
            contentAlignment = Alignment.Center,
        ) { Text("+", style = MaterialTheme.typography.titleLarge, color = Color.White) }
    }
}

@Composable
private fun SplitView(s: DuoState, onPoke: () -> Unit) {
    val me = s.me ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PersonColumn(me, Modifier.weight(1f), isMe = true, onPoke = {}, pokesLeft = 0, muted = false)
        if (s.partner != null) {
            PersonColumn(
                s.partner, Modifier.weight(1f), isMe = false, onPoke = onPoke,
                pokesLeft = 3 - s.pokesSentToday,
                muted = s.partner.profile.pokesMutedUntil > System.currentTimeMillis(),
            )
        } else {
            GameCard(Modifier.weight(1f)) {
                Text("⏳", fontSize = 36.sp)
                Text("Esperando a tu compa", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Ya están emparejados: aparece acá apenas termine de armar su perfil.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PersonColumn(
    p: PersonSummary,
    modifier: Modifier,
    isMe: Boolean,
    onPoke: () -> Unit,
    pokesLeft: Int,
    muted: Boolean,
) {
    val c = Color(p.profile.color)
    GameCard(modifier, accent = c) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Avatar(p.profile.avatar, c, 56.dp)
            Spacer(Modifier.height(6.dp))
            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Nv ${p.level.number}", style = MaterialTheme.typography.labelMedium, color = c)
            Spacer(Modifier.height(8.dp))
            val frac = if (p.budget > 0) p.today.kcalIn.toFloat() / p.budget else 0f
            FillBar(frac, if (frac > 1f) OsitoColors.Bad else c, height = 10.dp)
            Text("${p.today.kcalIn} / ${p.budget} kcal", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Text("🍽️${p.today.meals}", style = MaterialTheme.typography.labelMedium)
                Text("💪${p.today.exercises}", style = MaterialTheme.typography.labelMedium)
                Text("💧${p.today.water}", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StreakFlame(p.streak, size = 18.dp)
                AnimatedCounter(p.weekXp, style = MaterialTheme.typography.titleSmall, suffix = " XP sem.")
            }
            Text(
                if (Xp.dayDone(p.today)) "✅ Día cumplido" else "⏳ Día en curso",
                style = MaterialTheme.typography.labelSmall,
                color = if (Xp.dayDone(p.today)) OsitoColors.Good else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isMe) {
                Spacer(Modifier.height(8.dp))
                AnimatedVisibility(true, enter = fadeIn() + expandVertically()) {
                    BouncyButton(
                        when {
                            muted -> "Silenciado 🤫"
                            pokesLeft <= 0 -> "Sin pinchazos"
                            else -> "Pinchar ($pokesLeft)"
                        },
                        onClick = onPoke,
                        emoji = "📌",
                        enabled = !muted && pokesLeft > 0,
                        color = c,
                    )
                }
            }
        }
    }
}

@Composable
fun DuelBars(me: PersonSummary, partner: PersonSummary) {
    val max = maxOf(me.weekXp, partner.weekXp, 1)
    listOf(me, partner).forEach { p ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
            Text(p.profile.avatar, fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) { FillBar(p.weekXp.toFloat() / max, Color(p.profile.color), height = 16.dp) }
            Spacer(Modifier.width(8.dp))
            Text("${p.weekXp}", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
        }
    }
}
