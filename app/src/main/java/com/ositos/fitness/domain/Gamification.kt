package com.ositos.fitness.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/** Resumen de hábitos de una persona en un día. */
data class DayStats(
    val uid: String,
    val dayKey: String,
    val meals: Int = 0,
    val exercises: Int = 0,
    val water: Int = 0,
    val weighed: Boolean = false,
    val kcalIn: Int = 0,
    val kcalOut: Int = 0,
    val goalKcal: Int = 2000,
) {
    val epochDay: Long get() = Dates.epochDay(dayKey)
}

object Xp {
    const val PER_MEAL = 10
    const val MAX_MEALS = 5
    const val PER_EXERCISE = 20
    const val MAX_EXERCISES = 2
    const val WEIGH_IN = 15
    const val PER_WATER = 2
    const val MAX_WATER = 8
    const val GOAL_MET = 30
    const val DAY_DONE_MIN_XP = 40

    /** La meta de kcal se cumple si registró al menos 2 comidas y quedó en rango (el ejercicio suma margen). */
    fun goalMet(d: DayStats): Boolean =
        d.meals >= 2 && d.kcalIn >= (d.goalKcal * 0.6) && d.kcalIn <= d.goalKcal + d.kcalOut

    /** XP por hábitos, NO por kilos: así compiten parejo aunque tengan metas distintas. */
    fun forDay(d: DayStats): Int =
        minOf(d.meals, MAX_MEALS) * PER_MEAL +
            minOf(d.exercises, MAX_EXERCISES) * PER_EXERCISE +
            (if (d.weighed) WEIGH_IN else 0) +
            minOf(d.water, MAX_WATER) * PER_WATER +
            (if (goalMet(d)) GOAL_MET else 0)

    /** "Día cumplido": registró al menos una comida y juntó 40 XP. */
    fun dayDone(d: DayStats): Boolean = d.meals >= 1 && forDay(d) >= DAY_DONE_MIN_XP

    val rules = listOf(
        "🍽️ Registrar una comida" to "+$PER_MEAL XP (hasta $MAX_MEALS por día)",
        "💪 Hacer ejercicio" to "+$PER_EXERCISE XP (hasta $MAX_EXERCISES por día)",
        "⚖️ Pesarte" to "+$WEIGH_IN XP (1 por día)",
        "💧 Tomar un vaso de agua" to "+$PER_WATER XP (hasta $MAX_WATER por día)",
        "🎯 Cumplir la meta de kcal" to "+$GOAL_MET XP",
        "✅ Día cumplido" to "1 comida + $DAY_DONE_MIN_XP XP → suma a la racha",
    )
}

data class Level(val number: Int, val name: String, val emoji: String, val minXp: Int, val nextXp: Int?)

object Levels {
    private val table = listOf(
        Triple(0, "Osito dormilón", "😴"),
        Triple(150, "Osito de peluche", "🧸"),
        Triple(400, "Osito curioso", "🐻"),
        Triple(800, "Oso caminante de góndola", "🛒"),
        Triple(1400, "Oso trotador de plaza", "🏃"),
        Triple(2200, "Oso cebador de mates fit", "🧉"),
        Triple(3200, "Oso pardo", "🐻"),
        Triple(4500, "Oso polar imparable", "🐻‍❄️"),
        Triple(6000, "Oso grizzly", "💪"),
        Triple(8000, "Oso panda zen", "🐼"),
        Triple(10500, "Oso legendario", "🏆"),
        Triple(13500, "Oso Olímpico", "🥇"),
    )

    fun forXp(xp: Int): Level {
        val idx = table.indexOfLast { xp >= it.first }.coerceAtLeast(0)
        val (min, name, emoji) = table[idx]
        return Level(idx + 1, name, emoji, min, table.getOrNull(idx + 1)?.first)
    }

    fun progress(xp: Int): Float {
        val l = forXp(xp)
        val next = l.nextXp ?: return 1f
        return ((xp - l.minXp).toFloat() / (next - l.minXp)).coerceIn(0f, 1f)
    }
}

object Streaks {
    /**
     * Racha actual: días cumplidos consecutivos hasta hoy. Si hoy todavía no se cumplió,
     * se cuenta desde ayer (el día no terminó, la racha sigue viva).
     */
    fun current(doneDays: Set<Long>, today: Long): Int {
        var day = if (today in doneDays) today else today - 1
        var count = 0
        while (day in doneDays) {
            count++
            day--
        }
        return count
    }

    fun best(doneDays: Set<Long>): Int {
        if (doneDays.isEmpty()) return 0
        val sorted = doneDays.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1] + 1) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    /** Racha de pareja: solo cuentan los días que cumplieron LOS DOS. */
    fun coupleDays(a: Set<Long>, b: Set<Long>): Set<Long> = a intersect b

    /** ¿La racha de pareja está en peligro hoy? (venía viva y alguno todavía no cumplió hoy). */
    fun coupleAtRisk(a: Set<Long>, b: Set<Long>, today: Long): Boolean {
        val both = coupleDays(a, b)
        return (today - 1) in both && !(today in a && today in b)
    }
}

object Dates {
    fun today(): LocalDate = LocalDate.now()
    fun key(date: LocalDate): String = date.toString() // yyyy-MM-dd
    fun todayKey(): String = key(today())
    fun epochDay(key: String): Long = LocalDate.parse(key).toEpochDay()
    fun fromEpochDay(day: Long): LocalDate = LocalDate.ofEpochDay(day)

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weekKey(date: LocalDate): String {
        val y = date.get(IsoFields.WEEK_BASED_YEAR)
        val w = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return "%d-W%02d".format(y, w)
    }

    fun weekDays(date: LocalDate): List<LocalDate> {
        val start = weekStart(date)
        return (0..6).map { start.plusDays(it.toLong()) }
    }

    private val dayNames = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    private val monthNames = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    fun pretty(date: LocalDate): String = "${date.dayOfMonth} ${monthNames[date.monthValue - 1]}"
    fun prettyLong(date: LocalDate): String = "${dayNames[date.dayOfWeek.value - 1]} ${pretty(date)}"
    fun prettyWithYear(date: LocalDate): String = "${pretty(date)} ${date.year}"
}

/** Datos agregados para evaluar logros. */
data class AchievementContext(
    val totalMeals: Int,
    val totalWorkouts: Int,
    val daysLogged: Int,
    val kgProgress: Double,
    val bestStreak: Int,
    val bestCoupleStreak: Int,
    val duelsWon: Int,
    val pokesSent: Int,
    val maxWaterInADay: Int,
    val goalDays: Int,
    val level: Int,
    val coopChallengesDone: Int,
    val weighIns: Int,
)

data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val check: (AchievementContext) -> Boolean,
)

object Achievements {
    val all = listOf(
        Achievement("primer_bocado", "Primer bocado", "Registraste tu primera comida", "🍽️") { it.totalMeals >= 1 },
        Achievement("primera_semana", "Primera semana", "7 días registrando", "📅") { it.daysLogged >= 7 },
        Achievement("primer_entreno", "A mover el esqueleto", "Tu primer entrenamiento", "👟") { it.totalWorkouts >= 1 },
        Achievement("entrenos_10", "10 entrenamientos", "Ya es costumbre, ¿eh?", "💪") { it.totalWorkouts >= 10 },
        Achievement("entrenos_50", "50 entrenamientos", "Bestia total", "🦾") { it.totalWorkouts >= 50 },
        Achievement("primer_kilo", "Primer kilo", "1 kg más cerca del objetivo", "⚖️") { it.kgProgress >= 1.0 },
        Achievement("cinco_kilos", "Cinco kilos", "5 kg de progreso. ¡Aplausos!", "🎉") { it.kgProgress >= 5.0 },
        Achievement("racha_7", "Racha de 7", "Una semana sin aflojar", "🔥") { it.bestStreak >= 7 },
        Achievement("racha_30", "Racha de 30", "Un mes entero. Leyenda.", "🌋") { it.bestStreak >= 30 },
        Achievement("pareja_7", "Dúo dinámico", "Racha de pareja de 7 días", "👫") { it.bestCoupleStreak >= 7 },
        Achievement("pareja_30", "Inseparables", "Racha de pareja de 30 días", "💞") { it.bestCoupleStreak >= 30 },
        Achievement("duelo_1", "Primera victoria", "Ganaste un duelo semanal", "🥊") { it.duelsWon >= 1 },
        Achievement("duelo_5", "Campeón del living", "5 duelos ganados", "👑") { it.duelsWon >= 5 },
        Achievement("camello", "Camello", "8 vasos de agua en un día", "🐪") { it.maxWaterInADay >= 8 },
        Achievement("pinchador", "Pinchador serial", "Mandaste 20 pinchazos", "📌") { it.pokesSent >= 20 },
        Achievement("equilibrista", "Equilibrista", "7 días cumpliendo la meta de kcal", "🎯") { it.goalDays >= 7 },
        Achievement("balanza", "Amigo de la balanza", "Te pesaste 10 veces", "📉") { it.weighIns >= 10 },
        Achievement("nivel_5", "Oso con experiencia", "Llegaste al nivel 5", "⭐") { it.level >= 5 },
        Achievement("coop_1", "Equipo dinamita", "Completaron un desafío cooperativo", "🤝") { it.coopChallengesDone >= 1 },
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }

    fun unlocked(ctx: AchievementContext): Set<String> = all.filter { it.check(ctx) }.map { it.id }.toSet()
}

object Defaults {
    val pokeMessages = listOf(
        "Tu racha está llorando 😢",
        "¿Esa medialuna fue registrada? 🥐👀",
        "El sillón te extraña, pero el XP más 🛋️➡️💪",
        "Psst… tomá un vaso de agua 💧",
        "Te estoy sacando ventaja en el duelo 😏",
        "Si no registrás, no pasó… pero sí pasó 🍕",
        "Dale que la prenda la cumplís vos 😈",
        "Un osito sin registrar es un osito triste 🐻",
        "¿Caminata juntos? Yo pongo el mate 🧉",
        "Mové esas patitas, osito 🐾",
    )

    val forfeits = listOf(
        "Cocinar la cena 🍳",
        "Masajes 15 minutos 💆",
        "El ganador elige la peli 🎬",
        "Lavar los platos toda la semana 🍽️",
        "Desayuno en la cama ☕",
        "Cebar mate todo el finde 🧉",
        "Elegir la salida del sábado 🎉",
    )

    val avatars = listOf("🐻", "🐼", "🐨", "🐻‍❄️", "🦊", "🐯", "🦁", "🐰", "🐱", "🐶", "🐸", "🦄")

    /** Colores ARGB para elegir. */
    val colors = listOf(
        0xFFFF7A45, 0xFFFF4F7B, 0xFF9B5CFF, 0xFF3D8BFF,
        0xFF1EC8A5, 0xFFFFC23D, 0xFF00B8D9, 0xFF7ED957,
    )
}
