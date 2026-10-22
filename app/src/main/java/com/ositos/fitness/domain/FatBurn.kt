package com.ositos.fitness.domain

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Déficit calórico → grasa estimada. Regla clásica: ~7700 kcal de déficit ≈ 1 kg de grasa.
 * Es una estimación orientativa: el peso real también cambia por agua, sal, etc.
 *
 * Déficit del día = lo que gastás (gasto diario TDEE + ejercicio) − lo que comiste.
 * Solo cuentan los días con al menos una comida cargada (si no, no sabemos cuánto comiste).
 */
object FatBurn {
    const val KCAL_PER_KG = 7700.0
    /** XP por cada kilo de grasa "quemado" (premio por llegar al kilo). */
    const val XP_PER_KG = 100
    /** Tope por día: si te olvidaste de cargar la cena no queremos inventar 2000 kcal de déficit. */
    private const val MAX_DAY_DEFICIT = 1500
    private const val MAX_DAY_SURPLUS = 2000

    fun dayDeficit(d: DayStats, tdee: Int): Int? {
        if (d.meals <= 0) return null
        return (tdee + d.kcalOut - d.kcalIn).coerceIn(-MAX_DAY_SURPLUS, MAX_DAY_DEFICIT)
    }

    fun grams(deficitKcal: Int): Int = (deficitKcal * 1000 / KCAL_PER_KG).roundToInt()

    fun kg(deficitKcal: Int): Double = deficitKcal / KCAL_PER_KG

    /** Kg de grasa acumulados en esos días (puede dar negativo si hubo superávit). */
    fun totalKg(days: List<DayStats>, tdee: Int): Double = kg(days.sumOf { dayDeficit(it, tdee) ?: 0 })

    /** Kilos completos alcanzados (para premios). Nunca negativo. */
    fun milestones(totalKg: Double): Int = floor(totalKg.coerceAtLeast(0.0)).toInt()
}
