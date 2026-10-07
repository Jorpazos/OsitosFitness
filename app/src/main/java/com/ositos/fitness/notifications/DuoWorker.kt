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
import com.ositos.fitness.data.Profile
import com.ositos.fitness.data.dayStatsFrom
import com.ositos.fitness.domain.Dates
import com.ositos.fitness.domain.Xp
import kotlinx.coroutines.tasks.await
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Trabajo periódico (cada ~30 min), 100% gratis, sin backend:
 *  - Respaldo de pinchazos: si la Cloud Function de push no está desplegada (plan Spark),
 *    muestra igual los pinchazos pendientes.
 *  - Alertas automáticas a la noche: "No registró nada hoy, ¿le das un empujón?"
 *    y "La racha de los dos está en peligro".
 */
class DuoWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        private const val NAME = "duo-worker"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<DuoWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }

    override suspend fun doWork(): Result {
        val me = Firebase.auth.currentUser?.uid ?: return Result.success()
        val repo = DuoRepository()
        val prefs = applicationContext.getSharedPreferences("worker", Context.MODE_PRIVATE)

        runCatching {
            val profiles = Firebase.firestore.collection("duos").document(DuoRepository.DUO_ID)
                .collection("profiles").get().await().documents.mapNotNull { Profile.from(it) }
            val partner = profiles.firstOrNull { it.uid != me }
            val mine = profiles.firstOrNull { it.uid == me }
            val muted = (mine?.pokesMutedUntil ?: 0) > System.currentTimeMillis()

            // 1) Pinchazos pendientes (respaldo del push).
            repo.undeliveredPokesToMe().forEach { poke ->
                if (!muted) {
                    val who = partner?.nickname ?: "Tu pareja"
                    Notifications.show(applicationContext, poke.id.hashCode(), "📌 $who te pinchó", poke.message)
                }
                runCatching { repo.markPokeDelivered(poke.id) }
            }

            // 2) Alertas nocturnas, una vez por día cada una.
            val now = LocalTime.now()
            val today = Dates.todayKey()
            if (partner != null && now.hour in 20..22) {
                val days = Firebase.firestore.collection("duos").document(DuoRepository.DUO_ID)
                    .collection("days").whereEqualTo("dayKey", today).get().await()
                    .documents.mapNotNull { dayStatsFrom(it) }
                val myDay = days.firstOrNull { it.uid == me }
                val partnerDay = days.firstOrNull { it.uid == partner.uid }
                val partnerLogged = partnerDay != null && (partnerDay.meals + partnerDay.exercises) > 0
                val iDone = myDay != null && Xp.dayDone(myDay)
                val partnerDone = partnerDay != null && Xp.dayDone(partnerDay)

                if (!partnerLogged && prefs.getString("nudge_partner", "") != today) {
                    Notifications.show(
                        applicationContext, 9001,
                        "${partner.avatar} ${partner.nickname} no registró nada hoy",
                        "¿Le das un empujón? Un pinchazo a tiempo salva una racha 📌",
                        Notifications.CHANNEL_REMINDERS,
                    )
                    prefs.edit().putString("nudge_partner", today).apply()
                }
                if (!iDone && prefs.getString("nudge_me", "") != today) {
                    Notifications.show(
                        applicationContext, 9002,
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
