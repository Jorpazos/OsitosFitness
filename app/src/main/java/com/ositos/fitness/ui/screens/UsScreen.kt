package com.ositos.fitness.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ositos.fitness.domain.Xp
import com.ositos.fitness.ui.DuoState
import com.ositos.fitness.ui.components.AnimatedCounter
import com.ositos.fitness.ui.components.Avatar
import com.ositos.fitness.ui.components.BouncyButton
import com.ositos.fitness.ui.components.FillBar
import com.ositos.fitness.ui.components.GameCard
import com.ositos.fitness.ui.components.StatTile
import com.ositos.fitness.ui.components.WeightChart
import com.ositos.fitness.ui.theme.OsitoColors

/** Pantalla "Nosotros": estadísticas conjuntas. */
@Composable
fun UsScreen(s: DuoState, padding: PaddingValues, onOpenWrapped: () -> Unit) {
    val me = s.me ?: return
    val partner = s.partner
    val people = listOfNotNull(me, partner)
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
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(me.profile.avatar, Color(me.profile.color), 72.dp)
                    Text(" 💞 ", fontSize = 30.sp)
                    if (partner != null) Avatar(partner.profile.avatar, Color(partner.profile.color), 72.dp)
                    else Avatar("❔", MaterialTheme.colorScheme.outline, 72.dp)
                }
            }
        }
        item {
            Text(
                if (partner != null) "${me.name} & ${partner.name}" else "${me.name} & …",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            val kgTogether = people.sumOf { it.kgProgress.coerceAtLeast(0.0) }
            val workouts = people.sumOf { p -> p.days.sumOf { it.exercises } }
            val burned = people.sumOf { p -> p.days.sumOf { it.kcalOut } }
            val meals = people.sumOf { p -> p.days.sumOf { it.meals } }
            val xp = people.sumOf { it.totalXp }
            val doneDays = people.sumOf { p -> p.days.count { Xp.dayDone(it) } }
            GameCard {
                Text("Entre los dos", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("⚖️", "%.1f".format(kgTogether), "kg de progreso", Modifier.weight(1f), OsitoColors.Purple)
                    StatTile("💪", "$workouts", "entrenamientos", Modifier.weight(1f), OsitoColors.Mint)
                    StatTile("🔥", "$burned", "kcal quemadas", Modifier.weight(1f), OsitoColors.Fire)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("🍽️", "$meals", "comidas", Modifier.weight(1f), OsitoColors.Orange)
                    StatTile("⭐", "$xp", "XP total", Modifier.weight(1f), OsitoColors.Yellow)
                    StatTile("✅", "$doneDays", "días cumplidos", Modifier.weight(1f), OsitoColors.Good)
                }
            }
        }
        if (partner != null) {
            item {
                GameCard(accent = OsitoColors.Pink) {
                    Text("Racha de pareja", style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedCounter(s.coupleStreak, style = MaterialTheme.typography.displayMedium, color = OsitoColors.Pink)
                        Text(" días", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Récord", style = MaterialTheme.typography.labelMedium)
                            Text("${s.bestCoupleStreak} 🏆", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Text(
                        "Solo sube si cumplen los dos. Si uno afloja, se pierde para ambos 😬",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                val wonMe = s.duo?.duelsWon?.get(me.profile.uid) ?: 0
                val wonPartner = s.duo?.duelsWon?.get(partner.profile.uid) ?: 0
                val total = (wonMe + wonPartner).coerceAtLeast(1)
                GameCard {
                    Text("🥊 Duelos ganados", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${me.profile.avatar} $wonMe", style = MaterialTheme.typography.headlineSmall, color = Color(me.profile.color))
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) { FillBar(wonMe.toFloat() / total, Color(me.profile.color), height = 18.dp) }
                        Spacer(Modifier.width(10.dp))
                        Text("$wonPartner ${partner.profile.avatar}", style = MaterialTheme.typography.headlineSmall, color = Color(partner.profile.color))
                    }
                    Text(
                        "Empates: ${s.duo?.ties ?: 0} · Desafíos en equipo completados: ${s.duo?.coopDone ?: 0}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            GameCard {
                Text("Progreso de peso", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                WeightChart(
                    people.map { Color(it.profile.color) to it.weights },
                    people.map { Color(it.profile.color) to it.profile.targetWeightKg },
                    days = 180,
                )
                Spacer(Modifier.height(8.dp))
                people.forEach { p ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("${p.profile.avatar} ${p.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            "${p.profile.startWeightKg.fmt1()} → ${p.profile.weightKg.fmt1()} kg (obj. ${p.profile.targetWeightKg.fmt1()})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item {
            BouncyButton("Ver resumen de la semana", onOpenWrapped, Modifier.fillMaxWidth(), emoji = "🎁", color = OsitoColors.Purple)
        }
    }
}
