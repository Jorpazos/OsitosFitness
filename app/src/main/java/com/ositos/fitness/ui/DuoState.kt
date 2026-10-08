package com.ositos.fitness.ui

import com.ositos.fitness.data.CoopType
import com.ositos.fitness.data.Duo
import com.ositos.fitness.data.LogEntry
import com.ositos.fitness.data.LogType
import com.ositos.fitness.data.Profile
import com.ositos.fitness.data.Week
import com.ositos.fitness.data.WeightEntry
import com.ositos.fitness.domain.AchievementContext
import com.ositos.fitness.domain.DayStats
import com.ositos.fitness.domain.Dates
import com.ositos.fitness.domain.Eta
import com.ositos.fitness.domain.Games
import com.ositos.fitness.data.MeasureEntry
import com.ositos.fitness.domain.GoalType
import com.ositos.fitness.domain.HealthCalculator
import com.ositos.fitness.domain.Level
import com.ositos.fitness.domain.Levels
import com.ositos.fitness.domain.Streaks
import com.ositos.fitness.domain.WeightPoint
import com.ositos.fitness.domain.Xp
import java.time.LocalDate
import kotlin.math.abs

/** Todo lo que la UI necesita saber de una persona, ya calculado. */
data class PersonSummary(
    val profile: Profile,
    val today: DayStats,
    val totalXp: Int,
    val level: Level,
    val levelProgress: Float,
    val streak: Int,
    val bestStreak: Int,
    val weekXp: Int,
    val doneDays: Set<Long>,
    val weights: List<WeightPoint>,
    val trend: List<WeightPoint>,
    val eta: Eta?,
    val kgProgress: Double,
    val days: List<DayStats>,
    val measures: List<MeasureEntry> = emptyList(),
    /** Días con las 3 misiones completas y duelos relámpago ganados (para logros). */
    val questDays: Int = 0,
    val flashWins: Int = 0,
) {
    val name get() = profile.nickname.ifBlank { "Osito" }
    val budget get() = today.goalKcal + today.kcalOut
    val remaining get() = budget - today.kcalIn
    val goalType get() = profile.goal
}

data class CoopProgress(val type: CoopType, val target: Int, val current: Int) {
    val fraction get() = if (target <= 0) 1f else (current.toFloat() / target).coerceIn(0f, 1f)
    val done get() = current >= target
}

data class DuoState(
    val loading: Boolean = true,
    val myUid: String = "",
    val duo: Duo? = null,
    val me: PersonSummary? = null,
    val partner: PersonSummary? = null,
    val logs: List<LogEntry> = emptyList(),
    val weeks: List<Week> = emptyList(),
    val currentWeek: Week = Week(Dates.weekKey(LocalDate.now())),
    val coop: CoopProgress? = null,
    val coupleStreak: Int = 0,
    val bestCoupleStreak: Int = 0,
    val coupleAtRisk: Boolean = false,
    val pokesSentToday: Int = 0,
    val bingo: Games.Bingo? = null,
    val osera: Games.Osera? = null,
    val bingoLinesEver: Int = 0,
) {
    val hasPartner get() = partner != null
    fun nameOf(uid: String?): String = when (uid) {
        me?.profile?.uid -> me?.name ?: "Vos"
        partner?.profile?.uid -> partner?.name ?: "Tu compa"
        else -> "Alguien"
    }
    fun personOf(uid: String?): PersonSummary? = when (uid) {
        me?.profile?.uid -> me
        partner?.profile?.uid -> partner
        else -> null
    }
}

object Summaries {

    fun person(
        profile: Profile,
        allDays: List<DayStats>,
        allWeights: List<WeightEntry>,
        partnerUid: String?,
        allMeasures: List<MeasureEntry> = emptyList(),
        today: LocalDate = LocalDate.now(),
    ): PersonSummary {
        val todayKey = Dates.key(today)
        val todayEpoch = today.toEpochDay()
        val days = allDays.filter { it.uid == profile.uid }.sortedBy { it.dayKey }
        val partnerDays = allDays.filter { it.uid == partnerUid }.associateBy { it.dayKey }
        val todayStats = days.firstOrNull { it.dayKey == todayKey }
            ?: DayStats(profile.uid, todayKey, goalKcal = profile.goalKcal)
        val totalXp = days.sumOf { Games.dayXp(it, partnerDays[it.dayKey], today) }
        val doneDays = days.filter { Xp.dayDone(it) }.map { it.epochDay }.toSet()
        val weekKeys = Dates.weekDays(today).map { Dates.key(it) }.toSet()
        val weekXp = days.filter { it.dayKey in weekKeys }.sumOf { Games.dayXp(it, partnerDays[it.dayKey], today) }
        val flashWins = days.count {
            it.epochDay < todayEpoch && Games.flashWinner(it.dayKey, it, partnerDays[it.dayKey]) == profile.uid
        }
        val weights = allWeights.filter { it.uid == profile.uid }
            .map { WeightPoint(Dates.epochDay(it.dayKey), it.kg) }
            .sortedBy { it.epochDay }
        val current = weights.lastOrNull()?.kg ?: profile.weightKg
        val kgProgress = when (profile.goal) {
            GoalType.LOSE -> profile.startWeightKg - current
            GoalType.GAIN -> current - profile.startWeightKg
            GoalType.MAINTAIN -> 0.0
        }
        return PersonSummary(
            profile = profile,
            today = todayStats.copy(goalKcal = profile.goalKcal),
            totalXp = totalXp,
            level = Levels.forXp(totalXp),
            levelProgress = Levels.progress(totalXp),
            streak = Streaks.current(doneDays, todayEpoch),
            bestStreak = Streaks.best(doneDays),
            weekXp = weekXp,
            doneDays = doneDays,
            weights = weights,
            trend = HealthCalculator.movingAverage7(weights),
            eta = HealthCalculator.eta(weights, current, profile.targetWeightKg, todayEpoch),
            kgProgress = kgProgress,
            days = days,
            measures = allMeasures.filter { it.uid == profile.uid }.sortedBy { it.dayKey },
            questDays = days.count { Games.allQuestsDone(it) },
            flashWins = flashWins,
        )
    }

    fun coop(week: Week, days: List<DayStats>, date: LocalDate): CoopProgress {
        val keys = Dates.weekDays(date).map { Dates.key(it) }.toSet()
        val inWeek = days.filter { it.dayKey in keys }
        val current = when (week.coopType) {
            CoopType.BURN -> inWeek.sumOf { it.kcalOut }
            CoopType.WORKOUTS -> inWeek.sumOf { it.exercises }
            CoopType.WATER -> inWeek.sumOf { it.water }
            CoopType.MEALS -> inWeek.sumOf { it.meals }
        }
        return CoopProgress(week.coopType, week.coopTarget, current)
    }

    /** XP de la semana (con bonus de misiones y duelo relámpago). [days] = días de los dos. */
    fun weekXp(uid: String, days: List<DayStats>, date: LocalDate, today: LocalDate = LocalDate.now()): Int {
        val keys = Dates.weekDays(date).map { Dates.key(it) }.toSet()
        val partner = days.filter { it.uid != uid }.associateBy { it.dayKey }
        return days.filter { it.uid == uid && it.dayKey in keys }.sumOf { Games.dayXp(it, partner[it.dayKey], today) }
    }

    /** Puntos de equipo: combos diarios + bingos semanales + desafíos cooperativos cumplidos. */
    fun teamPoints(a: List<DayStats>, b: List<DayStats>, weeks: List<Week>, currentCoopDone: Boolean, today: LocalDate): Pair<Int, Int> {
        val combos = Games.comboDays(a, b) * Games.COMBO_POINTS
        val weekStarts = (a + b).map { Dates.weekStart(LocalDate.parse(it.dayKey)) }.toSet()
        var bingoLines = 0
        val bingo = weekStarts.sumOf { start ->
            val bg = Games.bingo(start, a, b)
            bingoLines += bg.lines
            bg.points
        }
        val currentKey = Dates.weekKey(today)
        val coop = weeks.count { it.closed && it.coopAchieved && it.key != currentKey } * Games.COOP_WEEK_POINTS +
            (if (currentCoopDone) Games.COOP_WEEK_POINTS else 0)
        return (combos + bingo + coop) to bingoLines
    }

    fun achievementContext(p: PersonSummary, s: DuoState, duo: Duo?): AchievementContext = AchievementContext(
        totalMeals = p.days.sumOf { it.meals },
        totalWorkouts = p.days.sumOf { it.exercises },
        daysLogged = p.days.count { it.meals + it.exercises > 0 },
        kgProgress = p.kgProgress,
        bestStreak = p.bestStreak,
        bestCoupleStreak = s.bestCoupleStreak,
        duelsWon = duo?.duelsWon?.get(p.profile.uid) ?: 0,
        pokesSent = p.profile.pokesSent,
        maxWaterInADay = p.days.maxOfOrNull { it.water } ?: 0,
        goalDays = p.days.count { Xp.goalMet(it) },
        level = p.level.number,
        coopChallengesDone = duo?.coopDone ?: 0,
        weighIns = p.weights.size,
        measuresCount = p.measures.size,
        questDays = p.questDays,
        flashWins = p.flashWins,
        bingoLines = s.bingoLinesEver,
        oseraLevel = s.osera?.level ?: 1,
    )

    fun build(
        myUid: String,
        duo: Duo?,
        profiles: Map<String, Profile>,
        days: List<DayStats>,
        weights: List<WeightEntry>,
        logs: List<LogEntry>,
        weeks: List<Week>,
        measures: List<MeasureEntry> = emptyList(),
        today: LocalDate = LocalDate.now(),
    ): DuoState {
        val myProfile = profiles[myUid]
        val partnerUid = duo?.members?.firstOrNull { it != myUid }
        val partnerProfile = partnerUid?.let { profiles[it] }?.takeIf { it.onboarded }
        val me = myProfile?.let { person(it, days, weights, partnerUid, measures, today) }
        val partner = partnerProfile?.let { person(it, days, weights, myUid, measures, today) }
        val weekKey = Dates.weekKey(today)
        val currentWeek = weeks.firstOrNull { it.key == weekKey } ?: Week(weekKey)
        val todayEpoch = today.toEpochDay()
        val coupleDays = if (me != null && partner != null) Streaks.coupleDays(me.doneDays, partner.doneDays) else emptySet()
        val todayKey = Dates.key(today)
        val coopNow = if (partner != null) coop(currentWeek, days, today) else null
        val team = if (me != null && partner != null) {
            teamPoints(me.days, partner.days, weeks, coopNow?.done == true, today)
        } else null
        return DuoState(
            loading = false,
            myUid = myUid,
            duo = duo,
            me = me,
            partner = partner,
            logs = logs,
            weeks = weeks,
            currentWeek = currentWeek,
            coop = coopNow,
            bingo = if (me != null && partner != null) Games.bingo(Dates.weekStart(today), me.days, partner.days) else null,
            osera = team?.let { Games.osera(it.first) },
            bingoLinesEver = team?.second ?: 0,
            coupleStreak = Streaks.current(coupleDays, todayEpoch),
            bestCoupleStreak = Streaks.best(coupleDays),
            coupleAtRisk = me != null && partner != null && Streaks.coupleAtRisk(me.doneDays, partner.doneDays, todayEpoch),
            pokesSentToday = logs.count { it.uid == myUid && it.type == LogType.POKE && it.dayKey == todayKey },
        )
    }
}

/** Datos para el resumen semanal tipo "wrapped". */
data class WrappedPerson(
    val name: String,
    val avatar: String,
    val color: Long,
    val xp: Int,
    val meals: Int,
    val workouts: Int,
    val kcalBurned: Int,
    val water: Int,
    val daysDone: Int,
    val weightDelta: Double?,
    val topFood: String?,
)

data class Wrapped(
    val weekKey: String,
    val from: LocalDate,
    val to: LocalDate,
    val people: List<WrappedPerson>,
    val winner: String?,
    val forfeit: String?,
    val coop: CoopProgress?,
    val coupleDaysDone: Int,
)

object WrappedBuilder {
    fun build(s: DuoState, anyDayOfWeek: LocalDate): Wrapped {
        val weekDays = Dates.weekDays(anyDayOfWeek)
        val keys = weekDays.map { Dates.key(it) }.toSet()
        val weekKey = Dates.weekKey(anyDayOfWeek)
        val week = s.weeks.firstOrNull { it.key == weekKey } ?: Week(weekKey)
        val people = listOfNotNull(s.me, s.partner).map { p ->
            val d = p.days.filter { it.dayKey in keys }
            val ws = p.weights.filter { Dates.key(Dates.fromEpochDay(it.epochDay)) in keys }
            val delta = if (ws.size >= 2) ws.last().kg - ws.first().kg else null
            val topFood = s.logs.filter { it.uid == p.profile.uid && it.type == LogType.MEAL && it.dayKey in keys }
                .groupingBy { it.title }.eachCount().maxByOrNull { it.value }?.key
            WrappedPerson(
                name = p.name,
                avatar = p.profile.avatar,
                color = p.profile.color,
                xp = d.sumOf { Xp.forDay(it) },
                meals = d.sumOf { it.meals },
                workouts = d.sumOf { it.exercises },
                kcalBurned = d.sumOf { it.kcalOut },
                water = d.sumOf { it.water },
                daysDone = d.count { Xp.dayDone(it) },
                weightDelta = delta,
                topFood = topFood,
            )
        }
        val winner = when {
            people.size < 2 -> null
            abs(people[0].xp - people[1].xp) == 0 -> null
            else -> people.maxBy { it.xp }.name
        }
        val allDays = listOfNotNull(s.me, s.partner).flatMap { it.days }
        val coupleDone = if (s.me != null && s.partner != null) {
            weekDays.count { it.toEpochDay() in s.me.doneDays && it.toEpochDay() in s.partner.doneDays }
        } else 0
        return Wrapped(
            weekKey = weekKey,
            from = weekDays.first(),
            to = weekDays.last(),
            people = people,
            winner = winner,
            forfeit = week.forfeit,
            coop = if (s.partner != null) Summaries.coop(week, allDays, anyDayOfWeek) else null,
            coupleDaysDone = coupleDone,
        )
    }
}
