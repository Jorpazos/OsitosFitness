package com.ositos.fitness.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ositos.fitness.domain.Dates
import com.ositos.fitness.domain.Games
import com.ositos.fitness.ui.DuoState
import com.ositos.fitness.ui.components.FillBar
import com.ositos.fitness.ui.components.GameCard
import com.ositos.fitness.ui.components.Pill
import com.ositos.fitness.ui.theme.OsitoColors

/** 🗒️ Misiones del día: 3 objetivos chiquitos, iguales para los dos. */
@Composable
fun QuestsCard(s: DuoState, compact: Boolean = false) {
    val me = s.me ?: return
    val todayKey = Dates.todayKey()
    val quests = Games.questsFor(todayKey)
    val partner = s.partner
    GameCard(accent = OsitoColors.Blue) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🗒️ Misiones de hoy", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Pill("${Games.questsDone(me.today)}/3", OsitoColors.Blue)
        }
        if (!compact) {
            Text(
                "+${Games.QUEST_XP} XP cada una. Si los dos completan las 3: ¡combo del dúo! (+${Games.COMBO_POINTS} 🤝)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        quests.forEach { q ->
            val done = q.done(me.today)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(if (done) "✅" else q.emoji, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(q.title, style = MaterialTheme.typography.titleSmall)
                    if (q.target > 1) {
                        FillBar(q.progress(me.today).toFloat() / q.target, if (done) OsitoColors.Good else OsitoColors.Blue, height = 8.dp)
                    }
                }
                if (partner != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (q.done(partner.today)) "${partner.profile.avatar}✅" else "${partner.profile.avatar}⏳",
                        fontSize = 14.sp,
                    )
                }
            }
        }
        if (partner != null && Games.allQuestsDone(me.today) && Games.allQuestsDone(partner.today)) {
            Spacer(Modifier.height(6.dp))
            Pill("🎉 ¡Combo del dúo! +${Games.COMBO_POINTS} puntos de equipo", OsitoColors.Good)
        }
    }
}

/** ⚡ Duelo relámpago: un mini duelo distinto cada día. */
@Composable
fun FlashDuelCard(s: DuoState) {
    val me = s.me ?: return
    val partner = s.partner ?: return
    val flash = Games.flashFor(Dates.todayKey())
    val mine = flash.value(me.today)
    val theirs = flash.value(partner.today)
    GameCard(accent = OsitoColors.Yellow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚡ Duelo relámpago", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Pill("+${Games.FLASH_XP} XP", OsitoColors.Yellow)
        }
        Text("${flash.emoji} ${flash.title}", style = MaterialTheme.typography.titleSmall)
        Text(
            "Se define a medianoche. ¡Cada día es un duelo nuevo!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        val max = maxOf(mine, theirs, 1)
        listOf(me to mine, partner to theirs).forEach { (p, v) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Text(p.profile.avatar, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) { FillBar(v.toFloat() / max, Color(p.profile.color), height = 14.dp) }
                Spacer(Modifier.width(8.dp))
                Text("$v ${flash.unit}", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                mine == theirs -> "Empatados 🤝 ¡Desempatá!"
                mine > theirs -> "Vas ganando 😎"
                else -> "${partner.name} va ganando… ¡no aflojes! 💨"
            },
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

/** 🏠 La Osera: el nivel compartido que suben con los juegos cooperativos. */
@Composable
fun OseraCard(s: DuoState) {
    val o = s.osera ?: return
    GameCard(accent = OsitoColors.Mint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(o.emoji, fontSize = 44.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("La Osera · nivel ${o.level}", style = MaterialTheme.typography.labelLarge, color = OsitoColors.Mint)
                Text(o.name, style = MaterialTheme.typography.titleLarge)
                Text("${o.points} puntos de equipo 🤝", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        FillBar(o.progress, OsitoColors.Mint, height = 14.dp)
        Text(
            o.next?.let { "Faltan ${it - o.points} para mejorar la osera" } ?: "¡Llegaron al máximo! 👑",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Se gana JUNTOS: combo del dúo (+${Games.COMBO_POINTS}), líneas de bingo (+${Games.BINGO_LINE_POINTS}), " +
                "cartón lleno (+${Games.BINGO_FULL_POINTS}) y desafío semanal en equipo (+${Games.COOP_WEEK_POINTS}).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 🎱 Bingo semanal del dúo: 9 casilleros que se completan entre los dos. */
@Composable
fun BingoCard(s: DuoState) {
    val bingo = s.bingo ?: return
    GameCard(accent = OsitoColors.Pink) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎱 Bingo del dúo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Pill(
                if (bingo.full) "¡CARTÓN LLENO!" else "${bingo.lines} ${if (bingo.lines == 1) "línea" else "líneas"}",
                OsitoColors.Pink,
            )
        }
        Text(
            "Cartón nuevo cada lunes. Se completa entre los dos: cada línea suma a La Osera.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        bingo.cells.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                row.forEach { cell ->
                    Column(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (cell.done) OsitoColors.Pink.copy(alpha = 0.28f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            .then(if (cell.done) Modifier.border(2.dp, OsitoColors.Pink, MaterialTheme.shapes.medium) else Modifier)
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(if (cell.done) "⭐" else cell.emoji, fontSize = 20.sp)
                        Text(
                            cell.label,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
