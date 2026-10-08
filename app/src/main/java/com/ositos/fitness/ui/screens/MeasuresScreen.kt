package com.ositos.fitness.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ositos.fitness.data.BodyPart
import com.ositos.fitness.data.MeasureEntry
import com.ositos.fitness.domain.Dates
import com.ositos.fitness.domain.GoalType
import com.ositos.fitness.domain.HealthCalculator
import com.ositos.fitness.ui.DuoState
import com.ositos.fitness.ui.PersonSummary
import com.ositos.fitness.ui.components.BearMeasureIllustration
import com.ositos.fitness.ui.components.BouncyButton
import com.ositos.fitness.ui.components.GameCard
import com.ositos.fitness.ui.components.Pill
import com.ositos.fitness.ui.components.SectionTitle
import com.ositos.fitness.ui.theme.OsitoColors
import java.time.LocalDate
import kotlin.math.abs

/** -1 si conviene que baje, +1 si conviene que suba, 0 neutro. */
private fun goodDirection(part: BodyPart, goal: GoalType): Int = when {
    part == BodyPart.WAIST -> -1
    goal == GoalType.LOSE -> -1
    goal == GoalType.GAIN && part != BodyPart.HIP -> 1
    else -> 0
}

@Composable
fun MeasuresScreen(s: DuoState, onBack: () -> Unit, onSave: (MeasureEntry) -> Unit) {
    val me = s.me ?: return
    var selected by rememberSaveable { mutableStateOf(BodyPart.WAIST) }
    val last = me.measures.lastOrNull()
    val values = remember(last) {
        mutableStateMapOf<BodyPart, String>().apply {
            BodyPart.entries.forEach { p -> put(p, last?.value(p)?.fmt() ?: "") }
        }
    }
    val measuredToday = last?.dayKey == Dates.todayKey()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                Text("Medidas 📏", style = MaterialTheme.typography.headlineMedium)
            }
            Text(
                "La balanza no cuenta todo: a veces bajan los centímetros aunque el peso no se mueva " +
                    "(¡sobre todo si estás ganando músculo!).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            GameCard(accent = OsitoColors.Yellow) {
                Text("🧸 Antes de medir", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "Usá una cinta métrica de costura (flexible), no la de la ferretería.",
                    "Siempre a la misma hora: ideal a la mañana, en ayunas y después del baño.",
                    "En ropa interior o ropa bien ajustada.",
                    "La cinta apoyada, sin apretar la piel y paralela al piso.",
                    "Medí cada 1 o 2 semanas: día a día no se nota y desanima.",
                    "Siempre del mismo lado (brazo y pierna).",
                ).forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        item {
            SectionTitle("¿Cómo y dónde medir?")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(BodyPart.entries) { part ->
                    FilterChip(selected == part, { selected = part }, { Text("${part.emoji} ${part.label}") })
                }
            }
        }

        item {
            GameCard {
                Row {
                    BearMeasureIllustration(selected, Modifier.width(130.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(selected.label, style = MaterialTheme.typography.titleLarge)
                        Pill(selected.where, OsitoColors.Orange)
                        Spacer(Modifier.height(8.dp))
                        selected.steps.forEachIndexed { i, step ->
                            Text("${i + 1}. $step", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp))
                        }
                    }
                }
            }
        }

        item {
            GameCard(accent = OsitoColors.Mint) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Registrar medidas de hoy", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (!measuredToday) Pill("+20 XP", OsitoColors.Purple)
                }
                Text(
                    "En centímetros. Podés dejar vacío lo que no midas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                BodyPart.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        row.forEach { part ->
                            NumField(
                                "${part.emoji} ${part.label}",
                                values[part] ?: "",
                                { values[part] = it },
                                Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                fun v(p: BodyPart) = values[p]?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 10.0..250.0 }
                val any = BodyPart.entries.any { v(it) != null }
                BouncyButton(
                    if (measuredToday) "Actualizar las de hoy" else "Guardar",
                    {
                        onSave(
                            MeasureEntry(
                                uid = me.profile.uid, dayKey = Dates.todayKey(),
                                waistCm = v(BodyPart.WAIST), hipCm = v(BodyPart.HIP), chestCm = v(BodyPart.CHEST),
                                armCm = v(BodyPart.ARM), thighCm = v(BodyPart.THIGH),
                            ),
                        )
                    },
                    Modifier.fillMaxWidth(),
                    enabled = any,
                    color = OsitoColors.Mint,
                    emoji = "📏",
                )
            }
        }

        item { SectionTitle("Tu progreso") }
        if (me.measures.isEmpty()) {
            item {
                Text(
                    "Todavía no hay medidas. Tomá las primeras hoy: son tu punto de partida 🐾",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(BodyPart.entries.toList()) { part -> PartProgress(me, part) }
            item {
                val m = me.measures.last()
                val whr = HealthCalculator.waistHipRatio(com.ositos.fitness.domain.Measures(m.waistCm, m.hipCm))
                if (whr != null) {
                    Text(
                        "Cintura/cadera: ${"%.2f".format(whr)} — ${HealthCalculator.waistHipRisk(whr, me.profile.sex)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        s.partner?.let { partner ->
            item { SectionTitle("${partner.profile.avatar} ${partner.name}") }
            item { PartnerSummary(partner) }
        }
    }
}

@Composable
private fun PartProgress(p: PersonSummary, part: BodyPart) {
    val points = p.measures.mapNotNull { m -> m.value(part)?.let { LocalDate.parse(m.dayKey).toEpochDay() to it } }
    if (points.isEmpty()) return
    val first = points.first().second
    val lastV = points.last().second
    val prev = points.getOrNull(points.size - 2)?.second
    val total = lastV - first
    val dir = goodDirection(part, p.profile.goal)
    val color = when {
        abs(total) < 0.2 || dir == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        (total < 0 && dir < 0) || (total > 0 && dir > 0) -> OsitoColors.Good
        else -> OsitoColors.Warn
    }
    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(part.emoji)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(part.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${lastV.fmt1()} cm" + (prev?.let { "  (antes ${it.fmt1()})" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (points.size >= 2) {
                Text(
                    "${if (total > 0) "+" else ""}${total.fmt1()} cm",
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                )
            }
        }
        if (points.size >= 2) {
            Spacer(Modifier.height(6.dp))
            Sparkline(points.map { it.second }, color)
            if (color == OsitoColors.Good) {
                Text(
                    "¡Se nota el avance! 🎉 Desde el ${Dates.pretty(LocalDate.ofEpochDay(points.first().first))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = OsitoColors.Good,
                )
            }
        }
    }
}

@Composable
private fun Sparkline(values: List<Double>, color: Color) {
    val min = values.min()
    val max = values.max()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(36.dp),
    ) {
        val range = (max - min).takeIf { it > 0.01 } ?: 1.0
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = if (values.size == 1) 0f else i * size.width / (values.size - 1)
            val y = (size.height - ((v - min) / range * size.height)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        val lastX = size.width
        val lastY = (size.height - ((values.last() - min) / range * size.height)).toFloat()
        drawCircle(color, 4.dp.toPx(), Offset(lastX, lastY))
    }
}

@Composable
private fun PartnerSummary(p: PersonSummary) {
    GameCard(accent = Color(p.profile.color)) {
        if (p.measures.size < 2) {
            Text(
                if (p.measures.isEmpty()) "Todavía no se tomó medidas. ¡Pasale este apartado! 📏"
                else "Ya tiene su primera medición. En una o dos semanas se va a ver el avance.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@GameCard
        }
        BodyPart.entries.forEach { part ->
            val vals = p.measures.mapNotNull { it.value(part) }
            if (vals.size >= 2) {
                val d = vals.last() - vals.first()
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text("${part.emoji} ${part.label}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${if (d > 0) "+" else ""}${d.fmt1()} cm", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Box { Text("¡Reaccioná en el historial para festejarlo! 👏", style = MaterialTheme.typography.labelSmall) }
    }
}
