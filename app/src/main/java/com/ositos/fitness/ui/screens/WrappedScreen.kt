package com.ositos.fitness.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ositos.fitness.domain.Dates
import com.ositos.fitness.ui.DuoState
import com.ositos.fitness.ui.Wrapped
import com.ositos.fitness.ui.WrappedBuilder
import com.ositos.fitness.ui.WrappedPerson
import com.ositos.fitness.ui.components.AnimatedCounter
import com.ositos.fitness.ui.components.ConfettiOverlay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

private const val PAGE_MS = 5500

/** Resumen semanal animado estilo "wrapped": historias que avanzan solas. */
@Composable
fun WrappedScreen(s: DuoState, onClose: () -> Unit) {
    val today = LocalDate.now()
    val date = if (today.dayOfWeek == DayOfWeek.SUNDAY) today else today.minusWeeks(1)
    val w = remember(s) { WrappedBuilder.build(s, date) }
    val pages = remember(w) { buildPages(w) }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var confetti by remember { mutableStateOf(0) }

    LaunchedEffect(pager.currentPage) {
        progress.snapTo(0f)
        if (pages[pager.currentPage].celebrate) confetti++
        progress.animateTo(1f, tween(PAGE_MS, easing = LinearEasing))
        if (pager.currentPage < pages.size - 1) pager.animateScrollToPage(pager.currentPage + 1)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(pages[pager.currentPage].colors)),
    ) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            scope.launch {
                                if (offset.x < size.width / 3f) {
                                    if (pager.currentPage > 0) pager.animateScrollToPage(pager.currentPage - 1)
                                } else if (pager.currentPage < pages.size - 1) {
                                    pager.animateScrollToPage(pager.currentPage + 1)
                                } else onClose()
                            }
                        }
                    }
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                pages[page].content(pager.currentPage == page)
            }
        }
        Row(
            Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.indices.forEach { i ->
                val frac = when {
                    i < pager.currentPage -> 1f
                    i == pager.currentPage -> progress.value
                    else -> 0f
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.3f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(frac)
                            .height(4.dp)
                            .background(Color.White),
                    )
                }
            }
            TextButton(onClick = onClose) { Text("✕", color = Color.White, fontSize = 18.sp) }
        }
        ConfettiOverlay(confetti)
    }
}

private class Page(
    val colors: List<Color>,
    val celebrate: Boolean = false,
    val content: @Composable (visible: Boolean) -> Unit,
)

@Composable
private fun Reveal(visible: Boolean, delayMs: Int = 0, content: @Composable () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            kotlinx.coroutines.delay(delayMs.toLong())
            show = true
        }
    }
    AnimatedVisibility(show, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 } + scaleIn(initialScale = 0.85f)) {
        content()
    }
}

@Composable
private fun Big(text: String) = Text(text, color = Color.White, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)

@Composable
private fun Small(text: String) = Text(
    text, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
)

@Composable
private fun PersonStat(p: WrappedPerson, value: Int, unit: String, visible: Boolean, delay: Int) {
    Reveal(visible, delay) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.16f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(p.avatar, fontSize = 36.sp)
            Spacer(Modifier.width(12.dp))
            Text(p.name, color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            AnimatedCounter(value, style = MaterialTheme.typography.headlineMedium, color = Color.White, suffix = " $unit")
        }
    }
}

private fun buildPages(w: Wrapped): List<Page> {
    val pages = mutableListOf<Page>()
    pages += Page(listOf(Color(0xFF9B5CFF), Color(0xFFFF4F7B))) { v ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Reveal(v) { Text("🐻🎁🐻", fontSize = 72.sp) }
            Reveal(v, 300) { Big("Su semana en Ositos") }
            Reveal(v, 700) { Small("del ${Dates.pretty(w.from)} al ${Dates.pretty(w.to)}") }
        }
    }
    pages += Page(listOf(Color(0xFFFF7A45), Color(0xFFFFC23D)), celebrate = w.winner != null) { v ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Reveal(v) { Small("XP de hábitos") }
            w.people.forEachIndexed { i, p -> PersonStat(p, p.xp, "XP", v, 300 + i * 400) }
            Reveal(v, 1400) {
                Big(if (w.winner != null) "🏆 ¡Ganó ${w.winner}!" else "🤝 ¡Empate!")
            }
            if (w.winner != null && w.forfeit != null) Reveal(v, 2000) { Small("Prenda a cumplir: ${w.forfeit}") }
        }
    }
    pages += Page(listOf(Color(0xFF1EC8A5), Color(0xFF3D8BFF))) { v ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Reveal(v) { Text("💪", fontSize = 64.sp) }
            Reveal(v, 200) { Small("Entrenamientos") }
            w.people.forEachIndexed { i, p -> PersonStat(p, p.workouts, "", v, 400 + i * 300) }
            Reveal(v, 1200) { Small("Calorías quemadas 🔥") }
            w.people.forEachIndexed { i, p -> PersonStat(p, p.kcalBurned, "kcal", v, 1500 + i * 300) }
        }
    }
    val coop = w.coop
    if (coop != null) {
        pages += Page(listOf(Color(0xFF3D8BFF), Color(0xFF9B5CFF)), celebrate = coop.done) { v ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Reveal(v) { Text(coop.type.emoji, fontSize = 64.sp) }
                Reveal(v, 200) { Small("Desafío en equipo: ${coop.type.label}") }
                Reveal(v, 500) {
                    AnimatedCounter(coop.current, style = MaterialTheme.typography.displayLarge, color = Color.White)
                }
                Reveal(v, 800) { Small("de ${coop.target} ${coop.type.unit}") }
                Reveal(v, 1200) { Big(if (coop.done) "¡Lo lograron juntos! 🎉" else "Casi casi… ¡la próxima sale! 💪") }
            }
        }
    }
    pages += Page(listOf(Color(0xFFFF4F7B), Color(0xFFFF7A45)), celebrate = w.coupleDaysDone >= 5) { v ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Reveal(v) { Text("💞", fontSize = 64.sp) }
            Reveal(v, 200) { Small("Días que cumplieron LOS DOS") }
            Reveal(v, 500) { AnimatedCounter(w.coupleDaysDone, style = MaterialTheme.typography.displayLarge, color = Color.White, suffix = " / 7") }
            Reveal(v, 1000) { Small("Comidas registradas 🍽️") }
            w.people.forEachIndexed { i, p -> PersonStat(p, p.meals, "", v, 1300 + i * 300) }
            Reveal(v, 2000) { Small("Vasos de agua 💧") }
            w.people.forEachIndexed { i, p -> PersonStat(p, p.water, "", v, 2300 + i * 300) }
        }
    }
    pages += Page(listOf(Color(0xFFFFC23D), Color(0xFFFF4F7B))) { v ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Reveal(v) { Text("🥇", fontSize = 64.sp) }
            Reveal(v, 200) { Small("Lo más registrado") }
            w.people.forEachIndexed { i, p ->
                Reveal(v, 500 + i * 400) {
                    Small("${p.avatar} ${p.name}: ${p.topFood ?: "nada todavía 👀"}")
                }
            }
            Reveal(v, 1500) { Small("Tendencia de peso ⚖️") }
            w.people.forEachIndexed { i, p ->
                Reveal(v, 1800 + i * 300) {
                    val d = p.weightDelta
                    Small(
                        "${p.avatar} ${p.name}: " + when {
                            d == null -> "pesate al menos 2 veces 😉"
                            d < -0.05 -> "%.1f kg ⬇️".format(d)
                            d > 0.05 -> "+%.1f kg ⬆️".format(d)
                            else -> "estable 🌊"
                        },
                    )
                }
            }
        }
    }
    pages += Page(listOf(Color(0xFF9B5CFF), Color(0xFF1EC8A5)), celebrate = true) { v ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Reveal(v) { Text("🐻🤝🐻", fontSize = 72.sp) }
            Reveal(v, 300) { Big("¡Gran semana, ositos!") }
            Reveal(v, 700) { Small("Lo importante no es la balanza: es que se cuidan de a dos. Nueva semana, nuevo duelo 🥊") }
            Reveal(v, 1100) { Small("Tocá para cerrar") }
        }
    }
    return pages
}
