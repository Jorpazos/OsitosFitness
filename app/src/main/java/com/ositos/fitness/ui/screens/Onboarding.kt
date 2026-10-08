package com.ositos.fitness.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.ositos.fitness.R
import com.ositos.fitness.data.Profile
import com.ositos.fitness.domain.ActivityLevel
import com.ositos.fitness.domain.Defaults
import com.ositos.fitness.domain.HealthCalculator
import com.ositos.fitness.domain.Measures
import com.ositos.fitness.domain.Sex
import com.ositos.fitness.ui.components.Avatar
import com.ositos.fitness.ui.components.BouncyButton
import com.ositos.fitness.ui.components.GameCard
import kotlinx.coroutines.launch

@Composable
private fun StepDots(step: Int, total: Int = 3) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .height(8.dp)
                    .width(if (i == step) 28.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

suspend fun googleIdToken(context: Context): String {
    val option = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val result = CredentialManager.create(context).getCredential(context, request)
    val cred = result.credential
    if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return GoogleIdTokenCredential.createFrom(cred.data).idToken
    }
    error("Credencial inesperada")
}

/** Pantalla 1 de 3: bienvenida + login con Google. */
@Composable
fun WelcomeScreen(onToken: (String) -> Unit, onError: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val inf = rememberInfiniteTransition(label = "bear")
    val bounce by inf.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "b")

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StepDots(0)
        Spacer(Modifier.weight(1f))
        Text("🐻🐻", fontSize = 84.sp, modifier = Modifier.scale(bounce))
        Spacer(Modifier.height(16.dp))
        Text("Ositos Fitness", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "El juego cooperativo (y un poquito competitivo) para cuidarse de a dos. " +
                "Sin culpa, con mucho XP.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (busy) {
            CircularProgressIndicator()
        } else {
            BouncyButton(
                "Entrar con Google",
                emoji = "🔑",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            onToken(googleIdToken(ctx))
                        } catch (e: Exception) {
                            onError(e.message ?: "No se pudo entrar con Google")
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Después de entrar te damos un PIN para armar tu dúo con quien quieras.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Pantallas 2 y 3: identidad (apodo/avatar/color) y datos del cuerpo con resultados. */
@Composable
fun OnboardingFlow(uid: String, displayName: String?, draft: Profile?, onDone: (Profile) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    var profile by remember {
        mutableStateOf(
            draft ?: Profile(uid = uid, nickname = displayName?.substringBefore(" ")?.take(14) ?: ""),
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepDots(step)
            Spacer(Modifier.weight(1f))
            if (step == 2) TextButton(onClick = { step = 1 }) { Text("Atrás") }
        }
        AnimatedContent(
            targetState = step,
            transitionSpec = { slideInHorizontally { it } togetherWith slideOutHorizontally { -it } },
            label = "step",
            modifier = Modifier.weight(1f),
        ) { s ->
            if (s == 1) {
                IdentityStep(profile, onChange = { profile = it }, onNext = { step = 2 })
            } else {
                BodyStep(
                    profile,
                    onChange = { profile = it },
                    onNext = { onDone(it.copy(startWeightKg = it.weightKg)) },
                    cta = "¡Arrancar! 🚀",
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdentityPicker(profile: Profile, onChange: (Profile) -> Unit) {
    OutlinedTextField(
        value = profile.nickname,
        onValueChange = { onChange(profile.copy(nickname = it.take(14))) },
        label = { Text("Apodo") },
        placeholder = { Text("Ej: Osita, Gordi, Chiqui…") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Text("Avatar", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Defaults.avatars.forEach { a ->
            val selected = a == profile.avatar
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color(profile.color).copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant)
                    .then(if (selected) Modifier.border(3.dp, Color(profile.color), CircleShape) else Modifier)
                    .clickable { onChange(profile.copy(avatar = a)) },
                contentAlignment = Alignment.Center,
            ) { Text(a, fontSize = 26.sp) }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("Tu color", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Defaults.colors.forEach { c ->
            val selected = c == profile.color
            Box(
                Modifier
                    .size(if (selected) 46.dp else 40.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .then(if (selected) Modifier.border(4.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onChange(profile.copy(color = c)) },
            )
        }
    }
}

@Composable
private fun IdentityStep(profile: Profile, onChange: (Profile) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(profile.avatar, Color(profile.color), 72.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("¿Cómo te decimos?", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Tu compa va a ver este apodo en todo: \"${profile.nickname.ifBlank { "Osito" }} te pinchó 📌\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        IdentityPicker(profile, onChange)
        Spacer(Modifier.height(28.dp))
        BouncyButton(
            "Siguiente",
            onClick = onNext,
            enabled = profile.nickname.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            color = Color(profile.color),
        )
    }
}

private fun String.toDoubleOrNullLocale() = replace(',', '.').toDoubleOrNull()

/** Formulario de datos físicos con resultados en vivo. Se reusa en "Editar perfil". */
@Composable
fun BodyStep(profile: Profile, onChange: (Profile) -> Unit, onNext: (Profile) -> Unit, cta: String) {
    var age by remember { mutableStateOf(profile.age.toString()) }
    var height by remember { mutableStateOf(profile.heightCm.fmt()) }
    var weight by remember { mutableStateOf(profile.weightKg.fmt()) }
    var target by remember { mutableStateOf(profile.targetWeightKg.fmt()) }
    var waist by remember { mutableStateOf(profile.measures.waistCm?.fmt() ?: "") }
    var hip by remember { mutableStateOf(profile.measures.hipCm?.fmt() ?: "") }
    var chest by remember { mutableStateOf(profile.measures.chestCm?.fmt() ?: "") }
    var arm by remember { mutableStateOf(profile.measures.armCm?.fmt() ?: "") }
    var thigh by remember { mutableStateOf(profile.measures.thighCm?.fmt() ?: "") }
    var showMeasures by remember { mutableStateOf(profile.measures.waistCm != null) }

    val ageN = age.toIntOrNull()
    val heightN = height.toDoubleOrNullLocale()
    val weightN = weight.toDoubleOrNullLocale()
    val targetN = target.toDoubleOrNullLocale()
    val valid = ageN != null && ageN in 14..100 && heightN != null && heightN in 120.0..230.0 &&
        weightN != null && weightN in 30.0..300.0 && targetN != null && targetN in 30.0..300.0
    val targetOk = valid && HealthCalculator.isTargetAllowed(targetN!!, heightN!!)

    fun build(): Profile? {
        if (!valid || !targetOk) return null
        val p = profile.copy(
            age = ageN!!,
            heightCm = heightN!!,
            weightKg = weightN!!,
            targetWeightKg = targetN!!,
            measures = Measures(
                waist.toDoubleOrNullLocale(), hip.toDoubleOrNullLocale(), chest.toDoubleOrNullLocale(),
                arm.toDoubleOrNullLocale(), thigh.toDoubleOrNullLocale(),
            ),
        )
        val bmr = HealthCalculator.bmr(p.sex, p.weightKg, p.heightCm, p.age)
        val tdee = HealthCalculator.tdee(bmr, p.activity)
        return p.copy(goalKcal = HealthCalculator.dailyGoalKcal(p.sex, tdee, HealthCalculator.goalType(p.weightKg, p.targetWeightKg)))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        Text("Tus números", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Solo para calcular. Nadie juzga acá 🤝",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Sex.entries.forEachIndexed { i, s ->
                SegmentedButton(
                    selected = profile.sex == s,
                    onClick = { onChange(profile.copy(sex = s)) },
                    shape = SegmentedButtonDefaults.itemShape(i, Sex.entries.size),
                ) { Text(s.label) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Edad", age, { age = it }, Modifier.weight(1f))
            NumField("Altura (cm)", height, { height = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField("Peso actual (kg)", weight, { weight = it }, Modifier.weight(1f))
            NumField(
                "Peso objetivo (kg)", target, { target = it }, Modifier.weight(1f),
                isError = valid && !targetOk,
            )
        }
        if (valid && !targetOk) {
            Text(
                "Ese objetivo queda con IMC menor a 18,5. El mínimo saludable para tu altura es " +
                    "${HealthCalculator.minTargetWeight(heightN!!).fmt()} kg 💚",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Nivel de actividad", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        ActivityLevel.entries.forEach { a ->
            val sel = profile.activity == a
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onChange(profile.copy(activity = a)) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (sel) "🟠" else "⚪", fontSize = 14.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(a.label, style = MaterialTheme.typography.titleSmall)
                    Text(a.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { showMeasures = !showMeasures }) {
            Text(if (showMeasures) "Ocultar medidas" else "📏 Agregar medidas (opcional)")
        }
        if (showMeasures) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Cintura", waist, { waist = it }, Modifier.weight(1f))
                NumField("Cadera", hip, { hip = it }, Modifier.weight(1f))
                NumField("Pecho", chest, { chest = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Brazo", arm, { arm = it }, Modifier.weight(1f))
                NumField("Muslo", thigh, { thigh = it }, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
        }
        val preview = build()
        if (preview != null) {
            Spacer(Modifier.height(12.dp))
            GameCard(accent = Color(preview.color)) {
                HealthResults(preview)
            }
        }
        Spacer(Modifier.height(20.dp))
        BouncyButton(
            cta,
            onClick = { build()?.let(onNext) },
            enabled = preview != null,
            modifier = Modifier.fillMaxWidth(),
            color = Color(profile.color),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun NumField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }.take(6)) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

fun Double.fmt(): String = if (this % 1.0 == 0.0) this.toInt().toString() else "%.1f".format(this)
