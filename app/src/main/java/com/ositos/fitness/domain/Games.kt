package com.ositos.fitness.domain

import java.time.LocalDate
import kotlin.math.abs

/**
 * Juegos extra. Todo se calcula a partir de los contadores diarios (DayStats) de los dos,
 * así es determinístico: los dos celulares ven exactamente lo mismo sin backend.
 *
 * Competitivos: misiones del día (XP personal) y duelo relámpago (+XP al que gana el día).
 * Cooperativos: combo del dúo, bingo semanal y "La Osera", el nivel compartido del equipo.
 */
object Games {
    const val QUEST_XP = 10
    const val FLASH_XP = 15
    const val COMBO_POINTS = 20
    const val BINGO_LINE_POINTS = 25
    const val BINGO_FULL_POINTS = 100
    const val COOP_WEEK_POINTS = 100

    private fun seed(key: String, salt: Int): Int = abs((key.hashCode() * 31 + salt * 7919) % 100_000)

    // ------------------------------------------------------------ Misiones del día

    data class Quest(
        val id: String,
        val emoji: String,
        val title: String,
        val target: Int,
        val value: (DayStats) -> Int,
    ) {
        fun progress(d: DayStats?): Int = d?.let(value)?.coerceAtMost(target) ?: 0
        fun done(d: DayStats?): Boolean = d != null && value(d) >= target
    }

    private val foodQuests = listOf(
        Quest("meals3", "🍽️", "Registrá 3 comidas", 3) { it.meals },
        Quest("meals4", "🍽️", "Registrá 4 comidas", 4) { it.meals },
        Quest("goal", "🎯", "Cumplí tu meta de kcal", 1) { if (Xp.goalMet(it)) 1 else 0 },
        Quest("weigh", "⚖️", "Pesate", 1) { if (it.weighed) 1 else 0 },
    )
    private val waterQuests = listOf(
        Quest("water5", "💧", "Tomá 5 vasos de agua", 5) { it.water },
        Quest("water6", "💧", "Tomá 6 vasos de agua", 6) { it.water },
        Quest("water8", "🐪", "Tomá 8 vasos de agua", 8) { it.water },
    )
    private val moveQuests = listOf(
        Quest("move", "👟", "Hacé algo de ejercicio", 1) { it.exercises },
        Quest("burn200", "🔥", "Quemá 200 kcal entrenando", 200) { it.kcalOut },
        Quest("burn350", "🔥", "Quemá 350 kcal entrenando", 350) { it.kcalOut },
        Quest("move2", "💪", "Entrená 2 veces", 2) { it.exercises },
    )

    /** Las 3 misiones del día: una de comida, una de agua y una de movimiento. Iguales para los dos. */
    fun questsFor(dayKey: String): List<Quest> = listOf(
        foodQuests[seed(dayKey, 1) % foodQuests.size],
        waterQuests[seed(dayKey, 2) % waterQuests.size],
        moveQuests[seed(dayKey, 3) % moveQuests.size],
    )

    fun questsDone(d: DayStats?): Int = d?.let { day -> questsFor(day.dayKey).count { it.done(day) } } ?: 0

    fun allQuestsDone(d: DayStats?): Boolean = d != null && questsDone(d) == 3

    fun questXp(d: DayStats): Int = questsDone(d) * QUEST_XP

    // ------------------------------------------------------------ Duelo relámpago

    enum class Flash(val emoji: String, val title: String, val unit: String, val value: (DayStats) -> Int) {
        WATER("💧", "¿Quién toma más agua hoy?", "vasos", { it.water }),
        BURN("🔥", "¿Quién quema más kcal entrenando hoy?", "kcal", { it.kcalOut }),
        MEALS("🍽️", "¿Quién registra más comidas hoy?", "comidas", { minOf(it.meals, 6) }),
        XP("⭐", "¿Quién junta más XP de hábitos hoy?", "XP", { Xp.forDay(it) }),
    }

    fun flashFor(dayKey: String): Flash = Flash.entries[seed(dayKey, 4) % Flash.entries.size]

    /** uid del ganador del duelo relámpago de ese día (null si empate o nadie hizo nada). */
    fun flashWinner(dayKey: String, a: DayStats?, b: DayStats?): String? {
        val f = flashFor(dayKey)
        val va = a?.let(f.value) ?: 0
        val vb = b?.let(f.value) ?: 0
        return when {
            va == vb -> null
            va > vb -> a?.uid
            else -> b?.uid
        }
    }

    // ------------------------------------------------------------ XP personal con bonus

    /**
     * XP personal de un día: hábitos + misiones + duelo relámpago (este último solo
     * cuando el día ya terminó, para no regalar puntos a mitad de camino).
     */
    fun dayXp(mine: DayStats, partner: DayStats?, today: LocalDate): Int {
        val finished = mine.epochDay < today.toEpochDay()
        val flash = if (finished && flashWinner(mine.dayKey, mine, partner) == mine.uid) FLASH_XP else 0
        return Xp.forDay(mine) + questXp(mine) + flash
    }

    // ------------------------------------------------------------ Bingo semanal (cooperativo)

    data class BingoCell(val emoji: String, val label: String, val done: Boolean)
    data class Bingo(val cells: List<BingoCell>, val lines: Int, val full: Boolean) {
        val points get() = lines * BINGO_LINE_POINTS + (if (full) BINGO_FULL_POINTS else 0)
    }

    private val LINES = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
        listOf(0, 4, 8), listOf(2, 4, 6),
    )

    fun bingo(weekStart: LocalDate, a: List<DayStats>, b: List<DayStats>): Bingo {
        val keys = (0..6).map { Dates.key(weekStart.plusDays(it.toLong())) }
        val ma = a.filter { it.dayKey in keys }.associateBy { it.dayKey }
        val mb = b.filter { it.dayKey in keys }.associateBy { it.dayKey }
        val all = ma.values + mb.values
        fun bothSameDay(cond: (DayStats) -> Boolean) = keys.any { k ->
            val x = ma[k]
            val y = mb[k]
            x != null && y != null && cond(x) && cond(y)
        }
        val base = listOf(
            BingoCell("👟", "Los dos entrenan el mismo día", bothSameDay { it.exercises > 0 }),
            BingoCell("🐪", "Alguien toma 8 vasos en un día", all.any { it.water >= 8 }),
            BingoCell("⚖️", "Los dos se pesan el mismo día", bothSameDay { it.weighed }),
            BingoCell("🍽️", "20 comidas entre los dos", all.sumOf { it.meals } >= 20),
            BingoCell("💞", "Los dos cumplen el día, 3 veces", keys.count { k ->
                Xp.dayDone(ma[k] ?: return@count false) && Xp.dayDone(mb[k] ?: return@count false)
            } >= 3),
            BingoCell("🔥", "1500 kcal quemadas entre los dos", all.sumOf { it.kcalOut } >= 1500),
            BingoCell("🎯", "Alguien cumple la meta 3 días", ma.values.count { Xp.goalMet(it) } >= 3 ||
                mb.values.count { Xp.goalMet(it) } >= 3),
            BingoCell("🥗", "Los dos registran 3+ comidas el mismo día", bothSameDay { it.meals >= 3 }),
            BingoCell("🗒️", "Combo del dúo (todas las misiones)", keys.any { k ->
                allQuestsDone(ma[k]) && allQuestsDone(mb[k])
            }),
        )
        // Cada semana el cartón se mezcla distinto (el centro siempre es el difícil).
        val center = base[4]
        val others = (base - center).toMutableList()
        val s = seed(Dates.key(weekStart), 9)
        val shuffled = others.indices.sortedBy { (it * 7 + s) % 11 }.map { others[it] }.toMutableList()
        shuffled.add(4, center)
        val lines = LINES.count { line -> line.all { shuffled[it].done } }
        return Bingo(shuffled, lines, shuffled.all { it.done })
    }

    // ------------------------------------------------------------ La Osera (nivel del equipo)

    data class Osera(val level: Int, val name: String, val emoji: String, val points: Int, val min: Int, val next: Int?) {
        val progress: Float get() = next?.let { ((points - min).toFloat() / (it - min)).coerceIn(0f, 1f) } ?: 1f
    }

    private val oseraTable = listOf(
        Triple(0, "Cueva vacía", "🕳️"),
        Triple(100, "Cueva con mantita", "🧶"),
        Triple(300, "Cueva con fogón", "🔥"),
        Triple(600, "Cabañita en el bosque", "🛖"),
        Triple(1000, "Cabaña con huerta", "🥕"),
        Triple(1500, "Casa de troncos", "🏡"),
        Triple(2200, "Casa con jacuzzi", "🛁"),
        Triple(3000, "Mansión osuna", "🏰"),
        Triple(4000, "Reino de los Ositos", "👑"),
    )

    fun osera(points: Int): Osera {
        val i = oseraTable.indexOfLast { points >= it.first }.coerceAtLeast(0)
        val (min, name, emoji) = oseraTable[i]
        return Osera(i + 1, name, emoji, points, min, oseraTable.getOrNull(i + 1)?.first)
    }

    /** Días en que los dos completaron todas sus misiones. */
    fun comboDays(a: List<DayStats>, b: List<DayStats>): Int {
        val mb = b.associateBy { it.dayKey }
        return a.count { allQuestsDone(it) && allQuestsDone(mb[it.dayKey]) }
    }
}
