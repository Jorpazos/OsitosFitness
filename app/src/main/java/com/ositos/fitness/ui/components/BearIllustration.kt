package com.ositos.fitness.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ositos.fitness.data.BodyPart
import kotlin.math.sqrt

private val Fur = Color(0xFF8B5A3C)
private val FurDark = Color(0xFF6B3E26)
private val Belly = Color(0xFFF3D2B3)
private val Ink = Color(0xFF2B1A12)
private val Tape = Color(0xFFFFC23D)
private val TapeEdge = Color(0xFFFF7A45)

/**
 * Osito de frente con una cinta métrica marcando dónde medir [part].
 * Dibujado con Canvas (sin imágenes), en coordenadas relativas al ancho.
 */
@Composable
fun BearMeasureIllustration(part: BodyPart?, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "tape")
    val glow by inf.animateFloat(0.55f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "g")
    Canvas(modifier.aspectRatio(0.75f)) {
        val w = size.width
        fun p(x: Float, y: Float) = Offset(x * w, y * w)
        fun r(v: Float) = v * w

        // Piernas
        drawLine(Fur, p(0.40f, 0.88f), p(0.38f, 1.20f), strokeWidth = r(0.15f), cap = StrokeCap.Round)
        drawLine(Fur, p(0.60f, 0.88f), p(0.62f, 1.20f), strokeWidth = r(0.15f), cap = StrokeCap.Round)
        drawOval(FurDark, p(0.27f, 1.20f), Size(r(0.18f), r(0.09f)))
        drawOval(FurDark, p(0.55f, 1.20f), Size(r(0.18f), r(0.09f)))
        // Brazos
        drawLine(Fur, p(0.29f, 0.42f), p(0.17f, 0.72f), strokeWidth = r(0.12f), cap = StrokeCap.Round)
        drawLine(Fur, p(0.71f, 0.42f), p(0.83f, 0.72f), strokeWidth = r(0.12f), cap = StrokeCap.Round)
        // Cuerpo y panza
        drawOval(Fur, p(0.23f, 0.33f), Size(r(0.54f), r(0.62f)))
        drawOval(Belly, p(0.34f, 0.47f), Size(r(0.32f), r(0.40f)))
        drawCircle(FurDark, r(0.012f), p(0.5f, 0.64f)) // ombligo
        // Orejas
        drawCircle(Fur, r(0.075f), p(0.36f, 0.07f))
        drawCircle(Fur, r(0.075f), p(0.64f, 0.07f))
        drawCircle(Belly, r(0.04f), p(0.36f, 0.07f))
        drawCircle(Belly, r(0.04f), p(0.64f, 0.07f))
        // Cabeza
        drawCircle(Fur, r(0.17f), p(0.5f, 0.19f))
        drawOval(Belly, p(0.42f, 0.205f), Size(r(0.16f), r(0.10f)))
        drawOval(Ink, p(0.475f, 0.215f), Size(r(0.05f), r(0.03f)))
        drawCircle(Ink, r(0.018f), p(0.44f, 0.16f))
        drawCircle(Ink, r(0.018f), p(0.56f, 0.16f))
        drawArc(
            Ink, 20f, 140f, false, p(0.47f, 0.235f), Size(r(0.06f), r(0.04f)),
            style = Stroke(r(0.008f), cap = StrokeCap.Round),
        )

        when (part) {
            BodyPart.CHEST -> horizontalTape(p(0.24f, 0.47f), p(0.76f, 0.47f), glow, w)
            BodyPart.WAIST -> horizontalTape(p(0.235f, 0.64f), p(0.765f, 0.64f), glow, w)
            BodyPart.HIP -> horizontalTape(p(0.24f, 0.82f), p(0.76f, 0.82f), glow, w)
            BodyPart.ARM -> {
                // Banda perpendicular al brazo izquierdo, en su mitad
                val a = p(0.29f, 0.42f)
                val b = p(0.17f, 0.72f)
                val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
                val dx = b.x - a.x
                val dy = b.y - a.y
                val len = sqrt(dx * dx + dy * dy)
                val nx = dy / len
                val ny = -dx / len
                val half = r(0.09f)
                horizontalTape(
                    Offset(mid.x - nx * half, mid.y - ny * half),
                    Offset(mid.x + nx * half, mid.y + ny * half),
                    glow, w,
                )
            }
            BodyPart.THIGH -> horizontalTape(p(0.30f, 1.00f), p(0.48f, 1.00f), glow, w)
            null -> Unit
        }
    }
}

/** Cinta métrica: banda amarilla con marquitas, levemente curva como si envolviera. */
private fun DrawScope.horizontalTape(from: Offset, to: Offset, glow: Float, w: Float) {
    val sag = w * 0.025f
    val mid = Offset((from.x + to.x) / 2, (from.y + to.y) / 2 + sag)
    val path = Path().apply {
        moveTo(from.x, from.y)
        quadraticTo(mid.x, mid.y, to.x, to.y)
    }
    drawPath(path, TapeEdge.copy(alpha = glow), style = Stroke(w * 0.055f, cap = StrokeCap.Round))
    drawPath(path, Tape, style = Stroke(w * 0.035f, cap = StrokeCap.Round))
    drawPath(
        path, Ink.copy(alpha = 0.7f),
        style = Stroke(w * 0.012f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.008f, w * 0.022f))),
    )
    // Flechita que señala
    drawCircle(TapeEdge.copy(alpha = glow), w * 0.03f, to)
}
