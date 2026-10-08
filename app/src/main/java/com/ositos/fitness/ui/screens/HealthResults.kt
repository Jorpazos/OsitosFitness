package com.ositos.fitness.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ositos.fitness.data.Profile
import com.ositos.fitness.domain.Dates
import com.ositos.fitness.domain.Eta
import com.ositos.fitness.domain.GoalType
import com.ositos.fitness.domain.HealthCalculator
import com.ositos.fitness.ui.components.BmiBar
import com.ositos.fitness.ui.components.GameCard
import com.ositos.fitness.ui.components.StatTile
import com.ositos.fitness.ui.theme.OsitoColors
import kotlin.math.abs
import kotlin.math.roundToInt

/** Todos los cálculos automáticos del perfil. */
@Composable
fun HealthResults(p: Profile, eta: Eta? = null) {
    val bmi = HealthCalculator.bmi(p.weightKg, p.heightCm)
    val cat = HealthCalculator.bmiCategory(bmi)
    val range = HealthCalculator.healthyRange(p.heightCm)
    val ref = HealthCalculator.referenceWeight(p.heightCm)
    val bmr = HealthCalculator.bmr(p.sex, p.weightKg, p.heightCm, p.age)
    val tdee = HealthCalculator.tdee(bmr, p.activity)
    val goalType = p.goal
    val goal = HealthCalculator.dailyGoalKcal(p.sex, tdee, goalType)
    val floorHit = goal == HealthCalculator.minKcal(p.sex) && goalType == GoalType.LOSE &&
        (tdee - HealthCalculator.MAX_DEFICIT) < goal
    val whr = HealthCalculator.waistHipRatio(p.measures)
    val accent = Color(p.color)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("IMC", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%.1f".format(bmi), style = MaterialTheme.typography.headlineMedium, color = accent)
            }
            Column(Modifier.weight(2f)) {
                Text("${cat.emoji} ${cat.label}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Rango saludable: ${range.start.fmt1()}–${range.endInclusive.fmt1()} kg · Referencia (IMC 22): ${ref.fmt1()} kg",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        BmiBar(bmi)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("🔋", "${bmr.roundToInt()}", "Basal (kcal)", Modifier.weight(1f), OsitoColors.Purple)
            StatTile("⚡", "${tdee.roundToInt()}", "Gasto diario", Modifier.weight(1f), OsitoColors.Blue)
            StatTile("🎯", "$goal", "Tu meta", Modifier.weight(1f), accent)
        }
        Text(
            when (goalType) {
                GoalType.LOSE -> "🔥 Objetivo: bajar a ${p.targetWeightKg.fmt1()} kg con déficit moderado (≈0,5 kg/semana)."
                GoalType.GAIN -> "💪 Objetivo: ganar músculo hasta ${p.targetWeightKg.fmt1()} kg con superávit leve " +
                    "(≈0,25 kg/semana). Clave: entrenar fuerza 3–4 veces por semana."
                GoalType.MAINTAIN -> "⚖️ Objetivo: mantener tu peso y tonificar. Comé lo que gastás y sumá fuerza y movimiento."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        val protein = HealthCalculator.proteinRange(goalType, p.weightKg)
        Text(
            "🥚 Proteína sugerida: ${protein.first}–${protein.last} g por día (repartida en las comidas).",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (floorHit) {
            Text(
                "🛟 Tu meta quedó en el piso de seguridad (${HealthCalculator.minKcal(p.sex)} kcal). " +
                    "Nunca recomendamos menos que eso.",
                style = MaterialTheme.typography.bodySmall,
                color = OsitoColors.Warn,
            )
        }
        if (whr != null) {
            Text(
                "Cintura/cadera: ${"%.2f".format(whr)} — ${HealthCalculator.waistHipRisk(whr, p.sex)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (eta != null) EtaInfo(eta, p)
    }
}

@Composable
fun EtaInfo(eta: Eta, p: Profile) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val pace = eta.kgPerWeek
        Text(
            "Ritmo real (últimos 14 días): ${if (pace > 0) "+" else ""}${"%.2f".format(pace)} kg/semana",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        val days = eta.daysToGoal
        Text(
            when {
                days == 0 -> "¡Llegaste al objetivo! 🏆"
                days != null -> "A este ritmo llegás a ${p.targetWeightKg.fmt1()} kg el " +
                    Dates.prettyWithYear(Dates.today().plusDays(days.toLong())) + " 📅"
                abs(pace) < 0.05 -> "Ritmo estable. La constancia es lo que suma 💪"
                else -> "Esta semana la tendencia va para el otro lado. Tranqui, es normal que fluctúe 🌊"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (eta.tooFastGain && p.goal == GoalType.GAIN) {
            GameCard(accent = OsitoColors.Warn, modifier = Modifier.padding(top = 4.dp)) {
                Text("🐻 Tranqui con la subida", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Estás subiendo más de 0,5 kg por semana. Para que sea músculo y no grasa, " +
                        "conviene ir más despacio (≈0,25 kg/semana) y priorizar el entrenamiento de fuerza.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (eta.tooFastWarning) {
            GameCard(accent = OsitoColors.Warn, modifier = Modifier.padding(top = 4.dp)) {
                Text("🐢 ¡Despacito!", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Venís bajando más de 1 kg por semana de forma sostenida. Está buenísimo el entusiasmo, " +
                        "pero si sigue así conviene consultar con un/a nutricionista o médico/a para hacerlo seguro.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

fun Double.fmt1(): String = "%.1f".format(this)
