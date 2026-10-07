package com.ositos.fitness.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GamificationTest {

    private fun day(meals: Int = 0, ex: Int = 0, water: Int = 0, weighed: Boolean = false, kIn: Int = 0, kOut: Int = 0) =
        DayStats("u", "2026-10-07", meals, ex, water, weighed, kIn, kOut, goalKcal = 2000)

    @Test
    fun xpCaps() {
        assertEquals(50, Xp.forDay(day(meals = 9)))
        assertEquals(40, Xp.forDay(day(ex = 5)))
        assertEquals(16, Xp.forDay(day(water = 20)))
        assertEquals(15, Xp.forDay(day(weighed = true)))
    }

    @Test
    fun goalMetCountsExerciseMargin() {
        assertTrue(Xp.goalMet(day(meals = 3, kIn = 2300, kOut = 400)))
        assertFalse(Xp.goalMet(day(meals = 3, kIn = 2500, kOut = 400)))
        assertFalse(Xp.goalMet(day(meals = 1, kIn = 1800))) // con 1 sola comida no cuenta
        assertFalse(Xp.goalMet(day(meals = 2, kIn = 500))) // registro incompleto
    }

    @Test
    fun dayDone() {
        assertTrue(Xp.dayDone(day(meals = 4))) // 40 XP
        assertFalse(Xp.dayDone(day(ex = 2))) // sin comidas no cuenta
        assertFalse(Xp.dayDone(day(meals = 1, water = 3)))
    }

    @Test
    fun levels() {
        assertEquals(1, Levels.forXp(0).number)
        assertEquals(2, Levels.forXp(150).number)
        assertEquals(12, Levels.forXp(99_999).number)
        assertEquals(0.5f, Levels.progress(275), 0.001f)
    }

    @Test
    fun streaks() {
        val today = 100L
        val done = setOf(97L, 98L, 99L)
        assertEquals(3, Streaks.current(done, today)) // hoy en curso, la racha sigue viva
        assertEquals(4, Streaks.current(done + 100L, today))
        assertEquals(0, Streaks.current(setOf(90L), today))
        assertEquals(3, Streaks.best(setOf(1L, 2L, 3L, 7L, 8L)))
    }

    @Test
    fun coupleStreakNeedsBoth() {
        val a = setOf(97L, 98L, 99L)
        val b = setOf(98L, 99L)
        val both = Streaks.coupleDays(a, b)
        assertEquals(2, Streaks.current(both, 100L))
        assertTrue(Streaks.coupleAtRisk(a, b, 100L))
        assertFalse(Streaks.coupleAtRisk(a + 100L, b + 100L, 100L))
    }

    @Test
    fun weekKeys() {
        assertEquals("2026-W41", Dates.weekKey(LocalDate.of(2026, 10, 7)))
        assertEquals(LocalDate.of(2026, 10, 5), Dates.weekStart(LocalDate.of(2026, 10, 11)))
        assertEquals(7, Dates.weekDays(LocalDate.of(2026, 10, 7)).size)
    }

    @Test
    fun foodSearchIgnoresAccents() {
        assertTrue(Foods.search("ñoquis").isNotEmpty())
        assertTrue(Foods.search("FAINA").any { it.name == "Fainá" })
        assertTrue(Foods.search("medialuna").size >= 2)
    }

    @Test
    fun achievements() {
        val ctx = AchievementContext(
            totalMeals = 1, totalWorkouts = 10, daysLogged = 7, kgProgress = 1.2, bestStreak = 7,
            bestCoupleStreak = 0, duelsWon = 0, pokesSent = 0, maxWaterInADay = 8, goalDays = 0,
            level = 1, coopChallengesDone = 0, weighIns = 0,
        )
        val ids = Achievements.unlocked(ctx)
        assertTrue("primer_bocado" in ids)
        assertTrue("entrenos_10" in ids)
        assertTrue("primer_kilo" in ids)
        assertTrue("camello" in ids)
        assertFalse("racha_30" in ids)
    }
}
