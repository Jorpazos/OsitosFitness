package com.ositos.fitness.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Sex(val label: String) { MALE("Hombre"), FEMALE("Mujer") }

enum class ActivityLevel(val factor: Double, val label: String, val detail: String) {
    SEDENTARY(1.2, "Sedentario", "Oficina, sillón y Netflix"),
    LIGHT(1.375, "Liviano", "Ejercicio 1–3 días por semana"),
    MODERATE(1.55, "Moderado", "Ejercicio 3–5 días por semana"),
    ACTIVE(1.725, "Activo", "Ejercicio fuerte 6–7 días"),
    VERY_ACTIVE(1.9, "Muy activo", "Laburo físico + entrenamiento"),
}

enum class GoalType(val label: String, val emoji: String, val detail: String) {
    LOSE("Bajar de peso", "🔥", "Déficit moderado para bajar grasa sin perder energía"),
    GAIN("Ganar músculo", "💪", "Superávit leve + fuerza para sumar músculo"),
    MAINTAIN("Mantener / tonificar", "⚖️", "Comer lo que gastás y mejorar la forma"),
}

data class BmiCategory(val label: String, val emoji: String)

data class Measures(
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val chestCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
)

data class WeightPoint(val epochDay: Long, val kg: Double)

data class Eta(
    /** kg por semana (negativo = bajando) según la regresión de los últimos 14 días. */
    val kgPerWeek: Double,
    /** Días estimados hasta el objetivo, o null si el ritmo no va hacia el objetivo / faltan datos. */
    val daysToGoal: Int?,
    val tooFastWarning: Boolean,
    /** Subiendo más de 0,5 kg/semana: probablemente más grasa que músculo. */
    val tooFastGain: Boolean = false,
)

/**
 * Todas las cuentas de salud. Funciones puras, sin Android, testeadas en src/test.
 */
object HealthCalculator {

    const val MIN_KCAL_FEMALE = 1200
    const val MIN_KCAL_MALE = 1500
    const val MAX_DEFICIT = 500
    const val SURPLUS = 300
    const val HEALTHY_BMI_MIN = 18.5
    const val HEALTHY_BMI_MAX = 24.9
    const val REFERENCE_BMI = 22.0

    fun bmi(weightKg: Double, heightCm: Double): Double {
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    fun bmiCategory(bmi: Double): BmiCategory = when {
        bmi < 18.5 -> BmiCategory("Bajo peso", "🪶")
        bmi < 25.0 -> BmiCategory("Saludable", "💚")
        bmi < 30.0 -> BmiCategory("Sobrepeso", "🟡")
        bmi < 35.0 -> BmiCategory("Obesidad I", "🟠")
        bmi < 40.0 -> BmiCategory("Obesidad II", "🔴")
        else -> BmiCategory("Obesidad III", "🔴")
    }

    fun weightForBmi(bmi: Double, heightCm: Double): Double = bmi * (heightCm / 100.0).pow(2)

    fun healthyRange(heightCm: Double): ClosedFloatingPointRange<Double> =
        weightForBmi(HEALTHY_BMI_MIN, heightCm)..weightForBmi(HEALTHY_BMI_MAX, heightCm)

    fun referenceWeight(heightCm: Double): Double = weightForBmi(REFERENCE_BMI, heightCm)

    /** Peso objetivo mínimo permitido (IMC 18,5), redondeado hacia arriba a 0,1 kg. */
    fun minTargetWeight(heightCm: Double): Double =
        ceil(weightForBmi(HEALTHY_BMI_MIN, heightCm) * 10.0) / 10.0

    fun isTargetAllowed(targetKg: Double, heightCm: Double): Boolean =
        bmi(targetKg, heightCm) >= HEALTHY_BMI_MIN - 1e-9

    /** Mifflin-St Jeor. */
    fun bmr(sex: Sex, weightKg: Double, heightCm: Double, age: Int): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * age
        return if (sex == Sex.MALE) base + 5 else base - 161
    }

    fun tdee(bmr: Double, activity: ActivityLevel): Double = bmr * activity.factor

    fun goalType(currentKg: Double, targetKg: Double): GoalType = when {
        targetKg < currentKg - 0.5 -> GoalType.LOSE
        targetKg > currentKg + 0.5 -> GoalType.GAIN
        else -> GoalType.MAINTAIN
    }

    /**
     * Valida el peso objetivo según el objetivo elegido. Devuelve null si está bien,
     * o un mensaje amable si no.
     */
    fun targetProblem(goal: GoalType, currentKg: Double, targetKg: Double, heightCm: Double): String? = when {
        !isTargetAllowed(targetKg, heightCm) ->
            "Ese objetivo queda con IMC menor a 18,5. El mínimo saludable para tu altura es ${"%.1f".format(minTargetWeight(heightCm))} kg 💚"
        goal == GoalType.LOSE && targetKg >= currentKg -> "Para bajar, el objetivo tiene que ser menor que tu peso actual"
        goal == GoalType.GAIN && targetKg <= currentKg -> "Para ganar músculo, el objetivo tiene que ser mayor que tu peso actual"
        else -> null
    }

    /** Proteína diaria sugerida (g) según el objetivo: rango orientativo por kg de peso. */
    fun proteinRange(goal: GoalType, weightKg: Double): IntRange {
        val (lo, hi) = when (goal) {
            GoalType.LOSE -> 1.6 to 2.0
            GoalType.GAIN -> 1.6 to 2.2
            GoalType.MAINTAIN -> 1.2 to 1.6
        }
        return (lo * weightKg).roundToInt()..(hi * weightKg).roundToInt()
    }

    fun minKcal(sex: Sex): Int = if (sex == Sex.MALE) MIN_KCAL_MALE else MIN_KCAL_FEMALE

    /**
     * Meta diaria de kcal. Déficit moderado (máx. 500 kcal/día ≈ 0,5 kg/semana),
     * mantenimiento o superávit leve. Nunca por debajo del piso de seguridad.
     */
    fun dailyGoalKcal(sex: Sex, tdee: Double, goal: GoalType): Int {
        val raw = when (goal) {
            GoalType.LOSE -> tdee - MAX_DEFICIT
            GoalType.MAINTAIN -> tdee
            GoalType.GAIN -> tdee + SURPLUS
        }
        return maxOf(raw.roundToInt(), minKcal(sex))
    }

    fun waistHipRatio(m: Measures): Double? {
        val w = m.waistCm ?: return null
        val h = m.hipCm ?: return null
        if (h <= 0) return null
        return w / h
    }

    /** Riesgo según OMS: >0,90 hombres, >0,85 mujeres. */
    fun waistHipRisk(ratio: Double, sex: Sex): String {
        val limit = if (sex == Sex.MALE) 0.90 else 0.85
        return if (ratio > limit) "Riesgo aumentado (OMS > ${"%.2f".format(limit)})" else "Dentro de lo recomendado"
    }

    /**
     * Pendiente (kg/día) por mínimos cuadrados sobre los pesos de los últimos 14 días.
     * Devuelve null si hay menos de 3 registros o si abarcan menos de 5 días.
     */
    fun trendKgPerDay(points: List<WeightPoint>, todayEpochDay: Long): Double? {
        val recent = points.filter { it.epochDay > todayEpochDay - 14 && it.epochDay <= todayEpochDay }
        if (recent.size < 3) return null
        val span = recent.maxOf { it.epochDay } - recent.minOf { it.epochDay }
        if (span < 5) return null
        val n = recent.size.toDouble()
        val mx = recent.sumOf { it.epochDay.toDouble() } / n
        val my = recent.sumOf { it.kg } / n
        var num = 0.0
        var den = 0.0
        for (p in recent) {
            val dx = p.epochDay - mx
            num += dx * (p.kg - my)
            den += dx * dx
        }
        return if (den == 0.0) null else num / den
    }

    fun eta(points: List<WeightPoint>, currentKg: Double, targetKg: Double, todayEpochDay: Long): Eta? {
        val slope = trendKgPerDay(points, todayEpochDay) ?: return null
        val perWeek = slope * 7
        val remaining = targetKg - currentKg
        val days = when {
            abs(remaining) < 0.1 -> 0
            slope == 0.0 -> null
            remaining / slope > 0 -> ceil(remaining / slope - 1e-6).toInt().takeIf { it < 5 * 365 }
            else -> null
        }
        // "Sostenido": la regresión ya cubre ≥ 5 días y ≥ 3 registros.
        val tooFast = perWeek < -1.0
        return Eta(perWeek, days, tooFast, tooFastGain = perWeek > 0.5)
    }

    /** Media móvil de 7 días (calendario) para cada punto. */
    fun movingAverage7(points: List<WeightPoint>): List<WeightPoint> {
        val sorted = points.sortedBy { it.epochDay }
        return sorted.map { p ->
            val window = sorted.filter { it.epochDay in (p.epochDay - 6)..p.epochDay }
            WeightPoint(p.epochDay, window.sumOf { it.kg } / window.size)
        }
    }

    /** kcal = MET × peso (kg) × horas. */
    fun exerciseKcal(met: Double, weightKg: Double, minutes: Int): Int =
        (met * weightKg * (minutes / 60.0)).roundToInt()
}
