package com.ositos.fitness.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.ositos.fitness.data.DuoRepository
import com.ositos.fitness.data.LogEntry
import com.ositos.fitness.data.LogType
import com.ositos.fitness.data.Profile
import com.ositos.fitness.data.Story
import com.ositos.fitness.data.dayStatsFrom
import com.ositos.fitness.domain.Dates
import com.ositos.fitness.domain.MealSlot
import com.ositos.fitness.domain.Xp
import kotlinx.coroutines.tasks.await
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * Trabajo periódico (cada ~30 min), 100% gratis, sin backend:
 *  - Respaldo de pinchazos: si la Cloud Function de push no está desplegada (plan Spark),
 *    muestra igual los pinchazos pendientes (con botón "Pincharle de vuelta").
 *  - Agua: de 9 a 21 h, si venís atrasado con los vasos, un recordatorio cada ~2 h con "+1 vaso".
 *  - Comidas: si no cargaste el desayuno / almuerzo / merienda / cena a su hora, un empujoncito.
 *  - Historias: cuando tu compa sube una, y si la racha de historias está por apagarse.
 *  - Alertas a la noche: "No registró nada hoy" y "La racha de los dos está en peligro".
 * Cada recordatorio se puede apagar desde el Perfil.
 */
class DuoWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        private const val NAME = "duo-worker"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<DuoWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** Vasos que "deberías" llevar a esta hora (8 vasos repartidos de 9 a 21 h). */
        fun expectedWater(now: LocalTime): Int {
            val minutes = (now.hour * 60 + now.minute) - 9 * 60
            if (minutes < 0) return 0
            return ceil(minutes / (12.0 * 60) * Xp.MAX_WATER).toInt().coerceIn(0, Xp.MAX_WATER)
        }

        /** Ventana en la que se recuerda cada comida si todavía no se cargó. */
        private val mealWindows = mapOf(
            MealSlot.BREAKFAST to (10 * 60..11 * 60 + 30),
            MealSlot.LUNCH to (14 * 60..15 * 60 + 30),
            MealSlot.SNACK to (18 * 60..19 * 60 + 30),
            MealSlot.DINNER to (21 * 60 + 30..23 * 60),
        )

        private val mealTexts = mapOf(
            MealSlot.BREAKFAST to ("☕ ¿Ya desayunaste?" to "Cargalo en 3 toques y arrancá el día sumando XP."),
            MealSlot.LUNCH to ("🍝 ¿Qué almorzaste hoy?" to "Anotalo ahora que te acordás: con foto IA es un toque."),
            MealSlot.SNACK to ("🧉 Hora de la merienda" to "¿Mate con algo? Registralo así no se pierde."),
            MealSlot.DINNER to ("🍲 ¿Qué cenaste?" to "Último registro del día: cerrá el diario y cuidá la racha."),
        )

        private val waterTexts = listOf(
            "💧 Hora de un vasito de agua" to "Vas %d de %d vasos. ¡Un traguito y seguimos!",
            "🐪 Ni los camellos aguantan tanto" to "Llevás %d de %d vasos hoy. Hidratate, osito.",
            "💧 Tu cuerpo te pide agua" to "Van %d de %d vasos. Un vaso ahora y te ponés al día.",
            "🥤 Recordatorio de agua" to "%d de %d vasos. Tocá \"+1 vaso\" cuando lo tomes.",
        )
    }

    override suspend fun doWork(): Result {
        val me = Firebase.auth.currentUser?.uid ?: return Result.success()
        val repo = DuoRepository()
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences("worker", Context.MODE_PRIVATE)

        runCatching {
            val duoId = Firebase.firestore.collection("users").document(me).get().await()
                .getString("duoId") ?: return Result.success()
            repo.duoId = duoId
            val duoRef = Firebase.firestore.collection("duos").document(duoId)
            val profiles = duoRef.collection("profiles").get().await().documents.mapNotNull { Profile.from(it) }
            val partner = profiles.firstOrNull { it.uid != me }
            val mine = profiles.firstOrNull { it.uid == me }
            val muted = (mine?.pokesMutedUntil ?: 0) > System.currentTimeMillis()

            // 1) Pinchazos pendientes (respaldo del push).
            repo.undeliveredPokesToMe().forEach { poke ->
                if (!muted) {
                    val who = partner?.nickname ?: "Tu compa"
                    val id = poke.id.hashCode()
                    Notifications.show(
                        ctx, id, "📌 $who te pinchó", poke.message,
                        actions = listOf(Notifications.pokeBackAction(ctx, id)),
                    )
                }
                runCatching { repo.markPokeDelivered(poke.id) }
            }

            val now = LocalTime.now()
            val today = Dates.todayKey()
            val days = duoRef.collection("days").whereEqualTo("dayKey", today).get().await()
                .documents.mapNotNull { dayStatsFrom(it) }
            val myDay = days.firstOrNull { it.uid == me }
            val partnerDay = partner?.let { p -> days.firstOrNull { it.uid == p.uid } }

            // 2) Agua: si venís atrasado, cada ~2 h (de 9 a 21).
            if (Reminders.isOn(ctx, Reminders.WATER) && now.hour in 9..21) {
                val water = myDay?.water ?: 0
                val expected = expectedWater(now)
                val last = prefs.getLong("water_last", 0)
                if (water < expected && System.currentTimeMillis() - last > 2 * 3_600_000L) {
                    val (title, body) = waterTexts[(now.hour + water) % waterTexts.size]
                    Notifications.show(
                        ctx, Notifications.ID_WATER, title, body.format(water, Xp.MAX_WATER),
                        Notifications.CHANNEL_WATER, listOf(Notifications.waterAction(ctx)),
                    )
                    prefs.edit().putLong("water_last", System.currentTimeMillis()).apply()
                }
            }

            // 3) Comidas: si a su hora todavía no cargaste esa comida (una vez por día cada una).
            if (Reminders.isOn(ctx, Reminders.MEALS)) {
                val minuteOfDay = now.hour * 60 + now.minute
                val due = mealWindows.filter { (slot, window) ->
                    minuteOfDay in window && prefs.getString("meal_${slot.name}", "") != today
                }.keys
                if (due.isNotEmpty()) {
                    val loggedSlots = duoRef.collection("logs")
                        .whereEqualTo("uid", me).whereEqualTo("dayKey", today).get().await()
                        .documents.mapNotNull { LogEntry.from(it) }
                        .filter { it.type == LogType.MEAL }
                        .map { it.mealSlot }.toSet()
                    due.forEach { slot ->
                        if (slot !in loggedSlots) {
                            val (title, body) = mealTexts.getValue(slot)
                            Notifications.show(ctx, 9400 + slot.ordinal, title, body, Notifications.CHANNEL_REMINDERS)
                        }
                        prefs.edit().putString("meal_${slot.name}", today).apply()
                    }
                }
            }

            // 4) Historias: aviso cuando tu compa sube una + racha en peligro.
            if (Reminders.isOn(ctx, Reminders.STORIES)) {
                val lastSeen = prefs.getLong("story_last_ts", -1L)
                if (lastSeen < 0) {
                    // Primera vez: arrancamos desde ahora (sin avisar de historias viejas).
                    prefs.edit().putLong("story_last_ts", System.currentTimeMillis()).apply()
                } else {
                    val since = maxOf(lastSeen, System.currentTimeMillis() - Story.TTL_MS)
                    val fresh = repo.partnerStoriesSince(since)
                    if (fresh.isNotEmpty()) {
                        val who = partner?.nickname ?: "Tu compa"
                        val caption = fresh.last().caption
                        Notifications.show(
                            ctx, Notifications.ID_STORY,
                            "📸 $who subió ${if (fresh.size > 1) "${fresh.size} historias" else "una historia"}",
                            caption.ifBlank { "Entrá a verla antes de que desaparezca (dura 24 h) 👀" },
                            Notifications.CHANNEL_STORIES,
                        )
                        prefs.edit().putLong("story_last_ts", fresh.maxOf { it.ts }).apply()
                    }
                }
                if (now.hour in 19..22 && prefs.getString("story_risk", "") != today) {
                    val yesterday = Dates.key(Dates.today().minusDays(1))
                    val yDays = duoRef.collection("days").whereEqualTo("dayKey", yesterday).get().await()
                        .documents.mapNotNull { dayStatsFrom(it) }
                    val members = listOfNotNull(me, partner?.uid)
                    val aliveYesterday = members.all { u -> yDays.any { it.uid == u && it.story } }
                    val doneToday = members.all { u -> days.any { it.uid == u && it.story } }
                    if (aliveYesterday && !doneToday) {
                        val iPosted = myDay?.story == true
                        Notifications.show(
                            ctx, 9004,
                            "🔥 La racha de historias se apaga hoy",
                            if (iPosted) "Vos ya subiste la tuya. Falta ${partner?.nickname ?: "tu compa"}: ¿un pinchacito?"
                            else "Subí una foto del día (lo que sea: tu mate, tu perro, tu entreno) y salvá el fueguito.",
                            Notifications.CHANNEL_STORIES,
                        )
                        prefs.edit().putString("story_risk", today).apply()
                    }
                }
            }

            // 5) Alertas nocturnas, una vez por día cada una.
            if (partner != null && now.hour in 20..22) {
                val partnerLogged = partnerDay != null && (partnerDay.meals + partnerDay.exercises) > 0
                val iDone = myDay != null && Xp.dayDone(myDay)
                val partnerDone = partnerDay != null && Xp.dayDone(partnerDay)

                if (!partnerLogged && prefs.getString("nudge_partner", "") != today) {
                    Notifications.show(
                        ctx, 9001,
                        "${partner.avatar} ${partner.nickname} no registró nada hoy",
                        "¿Le das un empujón? Un pinchazo a tiempo salva una racha 📌",
                        Notifications.CHANNEL_REMINDERS,
                    )
                    prefs.edit().putString("nudge_partner", today).apply()
                }
                if (!iDone && prefs.getString("nudge_me", "") != today) {
                    Notifications.show(
                        ctx, 9002,
                        if (partnerDone) "🔥 La racha de los dos en peligro" else "🐻 ¡Te falta cumplir el día!",
                        if (partnerDone) "${partner.nickname} ya cumplió. Falta tu parte, osito."
                        else "Registrá tus comidas y sumá XP antes de que termine el día.",
                        Notifications.CHANNEL_REMINDERS,
                    )
                    prefs.edit().putString("nudge_me", today).apply()
                }
            }
        }
        return Result.success()
    }
}
