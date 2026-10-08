package com.ositos.fitness.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ositos.fitness.data.Account
import com.ositos.fitness.data.DuoKind
import com.ositos.fitness.data.DuoRepository
import com.ositos.fitness.ui.PairState
import com.ositos.fitness.ui.components.BouncyButton
import com.ositos.fitness.ui.components.GameCard
import com.ositos.fitness.ui.components.Haptics
import com.ositos.fitness.ui.theme.Nunito
import com.ositos.fitness.ui.theme.OsitoColors

/** Emparejar: mostrás tu PIN y ponés el de tu compañero/a. Si el otro pone el tuyo, entran solos. */
@Composable
fun PairScreen(
    account: Account,
    state: PairState,
    onSearch: (String) -> Unit,
    onConfirm: (DuoKind) -> Unit,
    onReset: () -> Unit,
    onSignOut: () -> Unit,
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var pin by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(DuoKind.PAREJA) }
    val inf = rememberInfiniteTransition(label = "pair")
    val pulse by inf.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🐻 💞 🐻", fontSize = 52.sp, modifier = Modifier.scale(pulse))
        Spacer(Modifier.height(8.dp))
        Text("Armá tu dúo", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            "Puede ser tu pareja, tu amiga, tu hermano… alguien con quien cuidarse de a dos.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        GameCard(accent = OsitoColors.Purple) {
            Text("Tu PIN", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                account.pin.forEach { c ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(OsitoColors.Purple.copy(alpha = 0.18f))
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            c.toString(),
                            style = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 28.sp),
                            color = OsitoColors.Purple,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BouncyButton(
                    "Copiar",
                    {
                        clipboard.setText(AnnotatedString(account.pin))
                        Haptics.tick(ctx)
                    },
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    emoji = "📋",
                )
                BouncyButton(
                    "Compartir",
                    {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "¡Sumate a mi dúo en Ositos Fitness! 🐻💪 Abrí la app y poné mi PIN: ${account.pin}",
                            )
                        }
                        ctx.startActivity(Intent.createChooser(send, "Compartir PIN"))
                    },
                    Modifier.weight(1f),
                    color = OsitoColors.Purple,
                    emoji = "📤",
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Si tu compa pone este PIN en su celu, entran los dos solos. No tenés que hacer nada más.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("— o —", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        GameCard(accent = OsitoColors.Orange) {
            Text("Poné el PIN de tu compañero/a", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "pairState",
            ) { st ->
                Column(Modifier.fillMaxWidth()) {
                    when (st) {
                        is PairState.Found -> {
                            Text(
                                "¡Encontramos a ${st.info.name.ifBlank { "tu compa" }}! 🎉",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                "PIN ${st.info.pin}. ¿Qué tipo de dúo son?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            DuoKindPicker(kind) { kind = it }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BouncyButton(
                                    "No es",
                                    onReset,
                                    Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                )
                                BouncyButton(
                                    "Emparejar",
                                    {
                                        Haptics.success(ctx)
                                        onConfirm(kind)
                                    },
                                    Modifier.weight(1.4f),
                                    color = OsitoColors.Pink,
                                    emoji = "💞",
                                )
                            }
                        }
                        PairState.Searching, PairState.Pairing -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(28.dp))
                                Spacer(Modifier.size(12.dp))
                                Text(
                                    if (st == PairState.Searching) "Buscando…" else "Armando el dúo…",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = pin,
                                onValueChange = { v ->
                                    pin = v.uppercase().filter { it.isLetterOrDigit() }.take(DuoRepository.PIN_LENGTH)
                                    if (st is PairState.Error) onReset()
                                },
                                placeholder = { Text("Ej: K7M2QX") },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 24.sp,
                                    textAlign = TextAlign.Center,
                                ),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Search,
                                ),
                                keyboardActions = KeyboardActions(onSearch = {
                                    if (pin.length == DuoRepository.PIN_LENGTH) onSearch(pin)
                                }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (st is PairState.Error) {
                                Spacer(Modifier.height(6.dp))
                                Text(st.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(12.dp))
                            BouncyButton(
                                "Buscar",
                                { onSearch(pin) },
                                Modifier.fillMaxWidth(),
                                enabled = pin.length == DuoRepository.PIN_LENGTH,
                                emoji = "🔍",
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onSignOut) { Text("Cerrar sesión") }
    }
}

/** Chips para elegir el tipo de dúo (pareja, amigos, hermanos, súper ositos, equipo rocket). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DuoKindPicker(selected: DuoKind, onSelect: (DuoKind) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DuoKind.entries.forEach { k ->
            androidx.compose.material3.FilterChip(
                selected = k == selected,
                onClick = { onSelect(k) },
                label = { Text("${k.emoji} ${k.label}") },
            )
        }
    }
    Text(
        selected.tagline,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}
