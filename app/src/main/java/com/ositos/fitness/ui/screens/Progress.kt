package com.ositos.fitness.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ositos.fitness.domain.Achievements
import com.ositos.fitness.domain.FatBurn
import com.ositos.fitness.domain.GoalType
import com.ositos.fitness.ui.DuoState
import com.ositos.fitness.ui.PersonSummary
import com.ositos.fitness.ui.Summaries
import com.ositos.fitness.ui.components.FillBar
import com.ositos.fitness.ui.components.GameCard
import com.ositos.fitness.ui.theme.OsitoColors
import kotlin.math.floor
import kotlin.math.roundToInt

private fun Double.kgOrGrams(): String =
    if (kotlin.math.abs(this) < 1.0) "${(this * 1000).roundToInt()} g" else "${"%.2f".format(this)} kg"

/**
 * 🔥 Barra de grasa quemada en la semana: el déficit calórico pasado a grasa (7700 kcal ≈ 1 kg).
 * Mitad = 500 g, final = 1 kg; si te pasás del kilo aparece una segunda barra violeta.
 */
@Composable
fun FatBurnCard(me: PersonSummary) {
    if (me.goalType == GoalType.GAIN) return
    val todayKg = (me.todayDeficit ?: 0).coerceAtLeast(0) / FatBurn.KCAL_PER_KG
    val week = me.weekFatKg.coerceAtLeast(0.0)
    val withToday = week + todayKg
    val nextKg = floor(me.fatKg.coerceAtLeast(0.0)).toInt() + 1

    GameCard(accent = OsitoColors.Fire) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔥 Grasa quemada esta semana", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(withToday.kgOrGrams(), style = MaterialTheme.typography.titleMedium, color = OsitoColors.Fire, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(10.dp))
        // Barra principal 0 → 1 kg: lo de la semana (fuego) + lo de hoy en curso (más clarito).
        Box(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(withToday.coerceIn(0.0, 1.0).toFloat())
                    .fillMaxHeight()
                    .background(OsitoColors.Fire.copy(alpha = 0.45f)),
            )
            Box(
                Modifier
                    .fillMaxWidth(week.coerceIn(0.0, 1.0).toFloat())
                    .fillMaxHeight()
                    .background(OsitoColors.Fire),
            )
            // Marca de la mitad: 500 g.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.8f)),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("500 g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("1 kg 🏆", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (withToday > 1.0) {
            Spacer(Modifier.height(6.dp))
            Text("¡Pasaste el kilo! Extra:", style = MaterialTheme.typography.labelMedium, color = OsitoColors.Purple)
            FillBar(((withToday - 1.0) / 1.0).toFloat(), OsitoColors.Purple, height = 12.dp)
        }
        Spacer(Modifier.height(10.dp))
        val d = me.todayDeficit
        Text(
            when {
                d == null -> "Cargá tus comidas de hoy y te digo cuánta grasa vas quemando."
                d >= 0 -> "Hoy (por ahora): déficit de $d kcal ≈ ${FatBurn.grams(d)} g de grasa 🔥"
                else -> "Hoy vas con superávit de ${-d} kcal (≈ ${-FatBurn.grams(d)} g). Un paseo lo compensa 🚶"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "📉 A este ritmo estarías bajando ~${week.kgOrGrams()} de grasa esta semana " +
                "(sin contar hoy). Total acumulado: ${me.fatKg.coerceAtLeast(0.0).kgOrGrams()}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "🏆 Al llegar a $nextKg kg: +${FatBurn.XP_PER_KG} XP, premio y aviso a tu compa",
            style = MaterialTheme.typography.labelMedium,
            color = OsitoColors.Purple,
        )
        Text(
            "Estimación: 7700 kcal de déficit ≈ 1 kg de grasa. La balanza también cambia por agua y sal.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Ya casi…": los logros que tenés más cerca, con su barrita. Dan ganas de completarlos 😏 */
@Composable
fun NextAchievementsCard(s: DuoState, me: PersonSummary, onOpen: () -> Unit) {
    val ctx = Summaries.achievementContext(me, s, s.duo)
    val next = Achievements.closest(ctx, me.profile.achievements.keys, 3)
    if (next.isEmpty()) return
    GameCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏅 Ya casi…", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${me.profile.achievements.size}/${Achievements.all.size} ›",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        next.forEach { a ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.emoji, fontSize = 26.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(a.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(a.progressLabel(ctx), style = MaterialTheme.typography.labelMedium, color = OsitoColors.Purple)
                    }
                    Spacer(Modifier.height(3.dp))
                    FillBar(a.progress(ctx), OsitoColors.Purple, height = 8.dp)
                    Text(
                        a.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
