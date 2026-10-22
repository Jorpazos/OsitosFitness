package com.ositos.fitness.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FatBurnTest {

    private fun day(key: String, kcalIn: Int, out: Int = 0, meals: Int = 3) =
        DayStats("u", key, meals = meals, kcalIn = kcalIn, kcalOut = out)

    @Test
    fun deficitAGramos() {
        // Gasto 2500, comí 1730 → 770 kcal de déficit ≈ 100 g de grasa.
        assertEquals(770, FatBurn.dayDeficit(day("2026-10-01", 1730), 2500))
        assertEquals(100, FatBurn.grams(770))
        // El ejercicio suma al gasto.
        assertEquals(1000, FatBurn.dayDeficit(day("2026-10-01", 1800, out = 300), 2500))
        // Sin comidas cargadas no se cuenta.
        assertNull(FatBurn.dayDeficit(day("2026-10-01", 0, meals = 0), 2500))
        // Tope por día (si se olvidó de cargar la cena).
        assertEquals(1500, FatBurn.dayDeficit(day("2026-10-01", 200), 2500))
    }

    @Test
    fun kilosYPremios() {
        val days = (1..10).map { day("2026-10-%02d".format(it), 1730) } // 10 × 770 = 7700
        assertEquals(1.0, FatBurn.totalKg(days, 2500), 0.001)
        assertEquals(1, FatBurn.milestones(FatBurn.totalKg(days, 2500)))
        assertEquals(0, FatBurn.milestones(-0.5))
    }

    @Test
    fun logrosConProgreso() {
        val a = Achievements.byId("entrenos_10")!!
        val ctx = AchievementContext(
            totalMeals = 0, totalWorkouts = 7, daysLogged = 0, kgProgress = 0.0, bestStreak = 0,
            bestCoupleStreak = 0, duelsWon = 0, pokesSent = 0, maxWaterInADay = 0, goalDays = 0,
            level = 1, coopChallengesDone = 0, weighIns = 0, fatKg = 0.4,
        )
        assertEquals(0.7f, a.progress(ctx), 0.001f)
        assertEquals("7 / 10", a.progressLabel(ctx))
        assertTrue(Achievements.closest(ctx, emptySet()).isNotEmpty())
        assertEquals("0.4 / 1.0", Achievements.byId("grasa_1")!!.progressLabel(ctx).replace(',', '.'))
    }
}
