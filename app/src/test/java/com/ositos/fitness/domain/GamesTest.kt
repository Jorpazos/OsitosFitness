package com.ositos.fitness.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GamesTest {

    private fun day(uid: String, key: String, meals: Int = 0, ex: Int = 0, water: Int = 0, kOut: Int = 0, weighed: Boolean = false) =
        DayStats(uid, key, meals = meals, exercises = ex, water = water, kcalOut = kOut, weighed = weighed, kcalIn = meals * 500, goalKcal = 2000)

    @Test
    fun questsAreDeterministicAndVaried() {
        val q1 = Games.questsFor("2026-10-08")
        val q2 = Games.questsFor("2026-10-08")
        assertEquals(q1.map { it.id }, q2.map { it.id })
        assertEquals(3, q1.size)
        assertEquals(3, q1.map { it.id }.toSet().size)
    }

    @Test
    fun allQuestsDoneWithAFullDay() {
        val full = day("a", "2026-10-08", meals = 4, ex = 2, water = 8, kOut = 400, weighed = true)
        assertTrue(Games.allQuestsDone(full))
        assertEquals(3 * Games.QUEST_XP, Games.questXp(full))
        assertFalse(Games.allQuestsDone(day("a", "2026-10-08")))
    }

    @Test
    fun flashWinnerOnlyCountsFinishedDays() {
        val key = "2026-10-07"
        val a = day("a", key, meals = 5, ex = 2, water = 8, kOut = 500)
        val b = day("b", key, meals = 1)
        assertEquals("a", Games.flashWinner(key, a, b))
        assertNull(Games.flashWinner(key, day("a", key), day("b", key)))
        val todayIsSameDay = Games.dayXp(a, b, LocalDate.parse(key))
        val nextDay = Games.dayXp(a, b, LocalDate.parse(key).plusDays(1))
        assertEquals(Games.FLASH_XP, nextDay - todayIsSameDay)
    }

    @Test
    fun bingoCountsLinesAndFull() {
        val monday = LocalDate.of(2026, 10, 5)
        val empty = Games.bingo(monday, emptyList(), emptyList())
        assertEquals(9, empty.cells.size)
        assertEquals(0, empty.lines)
        val keys = (0..6).map { Dates.key(monday.plusDays(it.toLong())) }
        val a = keys.map { day("a", it, meals = 4, ex = 2, water = 8, kOut = 400, weighed = true) }
        val b = keys.map { day("b", it, meals = 4, ex = 2, water = 8, kOut = 400, weighed = true) }
        val full = Games.bingo(monday, a, b)
        assertTrue(full.full)
        assertEquals(8, full.lines)
        assertEquals(8 * Games.BINGO_LINE_POINTS + Games.BINGO_FULL_POINTS, full.points)
    }

    @Test
    fun oseraLevels() {
        assertEquals(1, Games.osera(0).level)
        assertEquals(3, Games.osera(300).level)
        assertNotNull(Games.osera(99_999).name)
        assertNull(Games.osera(99_999).next)
    }

    @Test
    fun objectivesAndProtein() {
        assertEquals(null, HealthCalculator.targetProblem(GoalType.GAIN, 70.0, 75.0, 175.0))
        assertNotNull(HealthCalculator.targetProblem(GoalType.GAIN, 70.0, 65.0, 175.0))
        assertNotNull(HealthCalculator.targetProblem(GoalType.LOSE, 70.0, 72.0, 175.0))
        assertEquals(112..154, HealthCalculator.proteinRange(GoalType.GAIN, 70.0))
    }
}
