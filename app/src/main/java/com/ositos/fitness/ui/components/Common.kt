package com.ositos.fitness.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ositos.fitness.ui.theme.OsitoColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------------- Háptica ----------------

object Haptics {
    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    fun tick(context: Context) = runCatching {
        vibrator(context)?.vibrate(VibrationEffect.createOneShot(18, 120))
    }

    fun success(context: Context) = runCatching {
        vibrator(context)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 50), intArrayOf(0, 160, 0, 255), -1),
        )
    }

    fun poke(context: Context) = runCatching {
        vibrator(context)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 40, 50, 40, 50, 80), intArrayOf(0, 255, 0, 255, 0, 255), -1),
        )
    }
}

// ---------------- Contador que sube ----------------

@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = Color.Unspecified,
    suffix: String = "",
    prefix: String = "",
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) { anim.animateTo(value.toFloat(), tween(900, easing = FastOutSlowInEasing)) }
    Text("$prefix${anim.value.toInt()}$suffix", modifier, style = style, color = color)
}

// ---------------- Avatar ----------------

@Composable
fun Avatar(emoji: String, color: Color, size: Dp = 48.dp, ring: Boolean = true) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.22f))
            .then(if (ring) Modifier.border(3.dp, color, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.5f).sp)
    }
}

// ---------------- Tarjeta de juego ----------------

@Composable
fun GameCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "card")
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .then(
                if (onClick != null) Modifier.clip(MaterialTheme.shapes.large)
                    .clickable(interaction, indication = null) { onClick() } else Modifier,
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = accent?.let { androidx.compose.foundation.BorderStroke(2.dp, it.copy(alpha = 0.5f)) },
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

/** Botón grande con rebote al presionar y vibración. */
@Composable
fun BouncyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    emoji: String? = null,
) {
    val ctx = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(Spring.DampingRatioHighBouncy), label = "btn")
    Box(
        modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) color else color.copy(alpha = 0.35f))
            .clickable(interaction, indication = null, enabled = enabled) {
                Haptics.tick(ctx)
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (emoji != null) "$emoji  $text" else text,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun StatTile(emoji: String, value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(color.copy(alpha = 0.12f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(emoji, fontSize = 22.sp)
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Llama de racha que late. */
@Composable
fun StreakFlame(days: Int, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    var beat by remember { mutableStateOf(false) }
    LaunchedEffect(days) {
        while (days > 0) {
            beat = !beat
            kotlinx.coroutines.delay(650)
        }
    }
    val s by animateFloatAsState(if (beat) 1.15f else 0.95f, tween(600), label = "flame")
    val r by animateFloatAsState(if (beat) 6f else -6f, tween(600), label = "flameRot")
    Text(
        if (days > 0) "🔥" else "🧊",
        modifier = modifier.scale(if (days > 0) s else 1f).rotate(if (days > 0) r else 0f),
        fontSize = size.value.sp,
    )
}

// ---------------- Confeti ----------------

private data class Particle(
    val x: Float, val vx: Float, val vy: Float, val color: Color, val size: Float, val rot: Float, val vr: Float,
)

/** Lluvia de confeti hecha con Canvas (sin librerías). Se dibuja mientras [trigger] cambia. */
@Composable
fun ConfettiOverlay(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val colors = listOf(OsitoColors.Orange, OsitoColors.Pink, OsitoColors.Purple, OsitoColors.Blue, OsitoColors.Mint, OsitoColors.Yellow)
    val particles = remember(trigger) {
        List(120) {
            val angle = Random.nextFloat() * Math.PI.toFloat()
            val speed = 0.6f + Random.nextFloat() * 1.2f
            Particle(
                x = 0.3f + Random.nextFloat() * 0.4f,
                vx = cos(angle) * speed * 0.5f,
                vy = -sin(angle) * speed - 0.4f,
                color = colors.random(),
                size = 6f + Random.nextFloat() * 10f,
                rot = Random.nextFloat() * 360f,
                vr = -360f + Random.nextFloat() * 720f,
            )
        }
    }
    val t = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { t.animateTo(1f, tween(2600, easing = LinearEasing)) }
    if (t.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val time = t.value * 2.6f
        particles.forEach { p ->
            val px = (p.x + p.vx * time) * size.width
            val py = size.height * 0.45f + (p.vy * time + 0.9f * time * time) * size.height * 0.5f
            val alpha = (1f - t.value).coerceIn(0f, 1f)
            rotate(p.rot + p.vr * time, Offset(px, py)) {
                drawRect(
                    p.color.copy(alpha = alpha),
                    topLeft = Offset(px - p.size / 2, py - p.size / 4),
                    size = Size(p.size, p.size / 2),
                )
            }
        }
    }
}
