package com.ositos.fitness.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ositos.fitness.domain.Dates
import com.ositos.fitness.domain.HealthCalculator
import com.ositos.fitness.domain.WeightPoint
import com.ositos.fitness.ui.theme.OsitoColors

/**
 * Anillo del día: kcal consumidas vs. presupuesto (meta + ejercicio).
 * Si te pasás, el excedente se dibuja en una segunda vuelta rojiza (sin culpa, solo info).
 */
@Composable
fun KcalRing(
    consumed: Int,
    goal: Int,
    exercise: Int,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    stroke: Dp = 22.dp,
) {
    val budget = (goal + exercise).coerceAtLeast(1)
    val target = consumed.toFloat() / budget
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) {
        anim.animateTo(target, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow))
    }
    val exerciseFrac by animateFloatAsState(exercise.toFloat() / budget, tween(900), label = "ex")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            drawArc(track, -90f, 360f, false, tl, arcSize, style = Stroke(s, cap = StrokeCap.Round))
            // margen que suma el ejercicio, al final del anillo
            if (exerciseFrac > 0f) {
                drawArc(
                    OsitoColors.Mint.copy(alpha = 0.35f), -90f + 360f * (1f - exerciseFrac), 360f * exerciseFrac,
                    false, tl, arcSize, style = Stroke(s, cap = StrokeCap.Round),
                )
            }
            val v = anim.value
            val main = v.coerceAtMost(1f)
            drawArc(
                Brush.sweepGradient(listOf(color.copy(alpha = 0.7f), color, color.copy(alpha = 0.7f))),
                -90f, 360f * main, false, tl, arcSize, style = Stroke(s, cap = StrokeCap.Round),
            )
            if (v > 1f) {
                drawArc(
                    OsitoColors.Bad, -90f, 360f * (v - 1f).coerceAtMost(1f), false, tl, arcSize,
                    style = Stroke(s * 0.55f, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val remaining = budget - consumed
            AnimatedCounter(
                value = kotlin.math.abs(remaining),
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                if (remaining >= 0) "kcal disponibles" else "kcal de más",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$consumed / $budget",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Barra horizontal que se llena con animación. */
@Composable
fun FillBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) { anim.animateTo(fraction.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing)) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50)),
    ) {
        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
        if (anim.value > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color)),
                size = Size(size.width * anim.value, size.height),
                cornerRadius = CornerRadius(size.height / 2),
            )
        }
    }
}

/** Barra de rangos de IMC con marcador animado. */
@Composable
fun BmiBar(bmi: Double, modifier: Modifier = Modifier) {
    val min = 15.0
    val max = 40.0
    val pos by animateFloatAsState(((bmi - min) / (max - min)).toFloat().coerceIn(0f, 1f), tween(1000), label = "bmi")
    val ranges = listOf(
        15.0 to OsitoColors.Blue,
        18.5 to OsitoColors.Good,
        25.0 to OsitoColors.Yellow,
        30.0 to OsitoColors.Orange,
        35.0 to OsitoColors.Bad,
    )
    val marker = MaterialTheme.colorScheme.onSurface
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp),
        ) {
            val barTop = 10.dp.toPx()
            val barH = 12.dp.toPx()
            ranges.forEachIndexed { i, (start, c) ->
                val end = ranges.getOrNull(i + 1)?.first ?: max
                val x0 = ((start - min) / (max - min)).toFloat() * size.width
                val x1 = ((end - min) / (max - min)).toFloat() * size.width
                drawRect(c, Offset(x0, barTop), Size(x1 - x0, barH))
            }
            val x = pos * size.width
            val tri = Path().apply {
                moveTo(x - 7.dp.toPx(), 0f)
                lineTo(x + 7.dp.toPx(), 0f)
                lineTo(x, barTop + 2.dp.toPx())
                close()
            }
            drawPath(tri, marker)
            drawLine(marker, Offset(x, barTop), Offset(x, barTop + barH), strokeWidth = 3.dp.toPx())
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("Bajo", "Saludable", "Sobrepeso", "Obesidad").forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Gráfico de peso: puntos diarios tenues + línea de tendencia (media móvil 7 días) bien marcada,
 * para que las fluctuaciones del día a día no desanimen. Línea punteada = objetivo.
 */
@Composable
fun WeightChart(
    series: List<Pair<Color, List<WeightPoint>>>,
    targets: List<Pair<Color, Double>> = emptyList(),
    modifier: Modifier = Modifier,
    days: Int = 60,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(series.sumOf { it.second.size }) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
    }
    val today = Dates.today().toEpochDay()
    val from = today - days
    val visible = series.map { (c, pts) -> c to pts.filter { it.epochDay >= from } }
    val allKg = visible.flatMap { it.second.map { p -> p.kg } } + targets.map { it.second }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    if (visible.all { it.second.isEmpty() }) {
        Box(modifier.height(160.dp), contentAlignment = Alignment.Center) {
            Text("Pesate para ver tu curva 📈", color = labelColor)
        }
        return
    }
    val minKg = (allKg.minOrNull() ?: 0.0) - 1.0
    val maxKg = (allKg.maxOrNull() ?: 1.0) + 1.0
    val startDay = visible.flatMap { it.second }.minOf { it.epochDay }.coerceAtMost(today - 7)

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            val w = size.width
            val h = size.height
            fun x(day: Long) = ((day - startDay).toFloat() / (today - startDay).coerceAtLeast(1)) * w
            fun y(kg: Double) = (h - ((kg - minKg) / (maxKg - minKg)).toFloat() * h)

            for (i in 0..3) {
                val yy = h * i / 3f
                drawLine(gridColor, Offset(0f, yy), Offset(w, yy), strokeWidth = 1f)
            }
            targets.forEach { (c, kg) ->
                if (kg in minKg..maxKg) {
                    drawLine(
                        c.copy(alpha = 0.6f), Offset(0f, y(kg)), Offset(w, y(kg)), strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                    )
                }
            }
            visible.forEach { (c, pts) ->
                if (pts.isEmpty()) return@forEach
                pts.forEach { p ->
                    drawCircle(c.copy(alpha = 0.35f), 4.dp.toPx(), Offset(x(p.epochDay), y(p.kg)))
                }
                val trend = HealthCalculator.movingAverage7(pts)
                if (trend.size >= 2) {
                    val path = Path()
                    val n = (trend.size * progress.value).toInt().coerceAtLeast(1)
                    trend.take(n).forEachIndexed { i, p ->
                        if (i == 0) path.moveTo(x(p.epochDay), y(p.kg)) else path.lineTo(x(p.epochDay), y(p.kg))
                    }
                    drawPath(path, c, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                }
                val last = (if (trend.isNotEmpty()) trend else pts).last()
                drawCircle(c, 6.dp.toPx() * progress.value, Offset(x(last.epochDay), y(last.kg)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                Dates.pretty(Dates.fromEpochDay(startDay)),
                style = MaterialTheme.typography.labelSmall, color = labelColor,
            )
            Spacer(Modifier.weight(1f))
            Text("hoy", style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
        Text(
            "• puntos: pesajes  — línea: tendencia 7 días  - - objetivo",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
