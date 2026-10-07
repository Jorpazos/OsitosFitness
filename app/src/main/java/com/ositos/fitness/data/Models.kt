package com.ositos.fitness.data

import com.google.firebase.firestore.DocumentSnapshot
import com.ositos.fitness.domain.ActivityLevel
import com.ositos.fitness.domain.DayStats
import com.ositos.fitness.domain.Defaults
import com.ositos.fitness.domain.Measures
import com.ositos.fitness.domain.Sex

data class Profile(
    val uid: String,
    val nickname: String = "",
    val avatar: String = "🐻",
    val color: Long = 0xFFFF7A45,
    val sex: Sex = Sex.FEMALE,
    val age: Int = 30,
    val heightCm: Double = 165.0,
    val weightKg: Double = 70.0,
    val startWeightKg: Double = 70.0,
    val targetWeightKg: Double = 65.0,
    val activity: ActivityLevel = ActivityLevel.LIGHT,
    val measures: Measures = Measures(),
    val goalKcal: Int = 2000,
    val pokesMutedUntil: Long = 0,
    val pokesSent: Int = 0,
    val achievements: Map<String, Long> = emptyMap(),
    val onboarded: Boolean = false,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "nickname" to nickname,
        "avatar" to avatar,
        "color" to color,
        "sex" to sex.name,
        "age" to age,
        "heightCm" to heightCm,
        "weightKg" to weightKg,
        "startWeightKg" to startWeightKg,
        "targetWeightKg" to targetWeightKg,
        "activity" to activity.name,
        "measures" to mapOf(
            "waistCm" to measures.waistCm,
            "hipCm" to measures.hipCm,
            "chestCm" to measures.chestCm,
            "armCm" to measures.armCm,
            "thighCm" to measures.thighCm,
        ),
        "goalKcal" to goalKcal,
        "onboarded" to onboarded,
    )

    companion object {
        fun from(d: DocumentSnapshot): Profile? {
            if (!d.exists()) return null
            @Suppress("UNCHECKED_CAST")
            val m = d.get("measures") as? Map<String, Any?> ?: emptyMap()
            fun md(k: String) = (m[k] as? Number)?.toDouble()
            @Suppress("UNCHECKED_CAST")
            val ach = (d.get("achievements") as? Map<String, Any?>)
                ?.mapValues { (it.value as? Number)?.toLong() ?: 0L } ?: emptyMap()
            return Profile(
                uid = d.id,
                nickname = d.getString("nickname") ?: "",
                avatar = d.getString("avatar") ?: "🐻",
                color = d.getLong("color") ?: 0xFFFF7A45,
                sex = runCatching { Sex.valueOf(d.getString("sex") ?: "") }.getOrDefault(Sex.FEMALE),
                age = d.getLong("age")?.toInt() ?: 30,
                heightCm = d.getDouble("heightCm") ?: 165.0,
                weightKg = d.getDouble("weightKg") ?: 70.0,
                startWeightKg = d.getDouble("startWeightKg") ?: d.getDouble("weightKg") ?: 70.0,
                targetWeightKg = d.getDouble("targetWeightKg") ?: 65.0,
                activity = runCatching { ActivityLevel.valueOf(d.getString("activity") ?: "") }
                    .getOrDefault(ActivityLevel.LIGHT),
                measures = Measures(md("waistCm"), md("hipCm"), md("chestCm"), md("armCm"), md("thighCm")),
                goalKcal = d.getLong("goalKcal")?.toInt() ?: 2000,
                pokesMutedUntil = d.getLong("pokesMutedUntil") ?: 0,
                pokesSent = d.getLong("pokesSent")?.toInt() ?: 0,
                achievements = ach,
                onboarded = d.getBoolean("onboarded") ?: false,
            )
        }
    }
}

data class Duo(
    val members: List<String> = emptyList(),
    val pokeMessages: List<String> = Defaults.pokeMessages,
    val forfeits: List<String> = Defaults.forfeits,
    val duelsWon: Map<String, Int> = emptyMap(),
    val ties: Int = 0,
    val coopDone: Int = 0,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(d: DocumentSnapshot): Duo? {
            if (!d.exists()) return null
            return Duo(
                members = (d.get("members") as? List<String>) ?: emptyList(),
                pokeMessages = (d.get("pokeMessages") as? List<String>)?.takeIf { it.isNotEmpty() } ?: Defaults.pokeMessages,
                forfeits = (d.get("forfeits") as? List<String>)?.takeIf { it.isNotEmpty() } ?: Defaults.forfeits,
                duelsWon = (d.get("duelsWon") as? Map<String, Any?>)
                    ?.mapValues { (it.value as? Number)?.toInt() ?: 0 } ?: emptyMap(),
                ties = d.getLong("ties")?.toInt() ?: 0,
                coopDone = d.getLong("coopDone")?.toInt() ?: 0,
            )
        }
    }
}

enum class LogType { MEAL, EXERCISE, WEIGHT, POKE, ACHIEVEMENT, DUEL }

data class LogEntry(
    val id: String,
    val uid: String,
    val type: LogType,
    val ts: Long,
    val dayKey: String,
    val title: String,
    val detail: String = "",
    val emoji: String = "",
    val kcal: Int = 0,
    val weightKg: Double? = null,
    val reactions: Map<String, String> = emptyMap(),
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(d: DocumentSnapshot): LogEntry? {
            if (!d.exists()) return null
            return LogEntry(
                id = d.id,
                uid = d.getString("uid") ?: return null,
                type = runCatching { LogType.valueOf(d.getString("type") ?: "") }.getOrNull() ?: return null,
                ts = d.getLong("ts") ?: 0,
                dayKey = d.getString("dayKey") ?: "",
                title = d.getString("title") ?: "",
                detail = d.getString("detail") ?: "",
                emoji = d.getString("emoji") ?: "",
                kcal = d.getLong("kcal")?.toInt() ?: 0,
                weightKg = d.getDouble("weightKg"),
                reactions = (d.get("reactions") as? Map<String, String>) ?: emptyMap(),
            )
        }
    }
}

fun dayStatsFrom(d: DocumentSnapshot): DayStats? {
    if (!d.exists()) return null
    return DayStats(
        uid = d.getString("uid") ?: return null,
        dayKey = d.getString("dayKey") ?: return null,
        meals = d.getLong("meals")?.toInt() ?: 0,
        exercises = d.getLong("exercises")?.toInt() ?: 0,
        water = d.getLong("water")?.toInt() ?: 0,
        weighed = d.getBoolean("weighed") ?: false,
        kcalIn = d.getLong("kcalIn")?.toInt() ?: 0,
        kcalOut = d.getLong("kcalOut")?.toInt() ?: 0,
        goalKcal = d.getLong("goalKcal")?.toInt() ?: 2000,
    )
}

data class WeightEntry(val uid: String, val dayKey: String, val kg: Double)

enum class CoopType(val label: String, val unit: String, val emoji: String, val defaultTarget: Int) {
    BURN("Quemar kcal entre los dos", "kcal", "🔥", 3000),
    WORKOUTS("Entrenamientos entre los dos", "entrenamientos", "💪", 8),
    WATER("Vasos de agua entre los dos", "vasos", "💧", 80),
    MEALS("Comidas registradas entre los dos", "comidas", "🍽️", 35),
}

data class Week(
    val key: String,
    val forfeit: String? = null,
    val forfeitBy: String? = null,
    val coopType: CoopType = CoopType.BURN,
    val coopTarget: Int = CoopType.BURN.defaultTarget,
    val closed: Boolean = false,
    val winnerUid: String? = null,
    val xp: Map<String, Int> = emptyMap(),
    val coopAchieved: Boolean = false,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(key: String, d: DocumentSnapshot?): Week {
            if (d == null || !d.exists()) return Week(key)
            val type = runCatching { CoopType.valueOf(d.getString("coopType") ?: "") }.getOrDefault(CoopType.BURN)
            return Week(
                key = key,
                forfeit = d.getString("forfeit"),
                forfeitBy = d.getString("forfeitBy"),
                coopType = type,
                coopTarget = d.getLong("coopTarget")?.toInt() ?: type.defaultTarget,
                closed = d.getBoolean("closed") ?: false,
                winnerUid = d.getString("winnerUid"),
                xp = (d.get("xp") as? Map<String, Any?>)?.mapValues { (it.value as? Number)?.toInt() ?: 0 } ?: emptyMap(),
                coopAchieved = d.getBoolean("coopAchieved") ?: false,
            )
        }
    }
}

data class Poke(
    val id: String,
    val from: String,
    val to: String,
    val message: String,
    val ts: Long,
    val delivered: Boolean,
) {
    companion object {
        fun from(d: DocumentSnapshot): Poke? {
            if (!d.exists()) return null
            return Poke(
                id = d.id,
                from = d.getString("from") ?: return null,
                to = d.getString("to") ?: return null,
                message = d.getString("message") ?: "",
                ts = d.getLong("ts") ?: 0,
                delivered = d.getBoolean("delivered") ?: false,
            )
        }
    }
}

data class AiFood(val name: String, val portion: String, val kcal: Int)
data class AiFoodResult(val foods: List<AiFood>, val totalKcal: Int, val confidence: String)
