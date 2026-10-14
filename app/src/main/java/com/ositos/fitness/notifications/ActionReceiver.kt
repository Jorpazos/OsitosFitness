package com.ositos.fitness.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.ositos.fitness.data.Duo
import com.ositos.fitness.data.DuoRepository
import com.ositos.fitness.data.LogType
import com.ositos.fitness.data.Profile
import com.ositos.fitness.domain.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Botones de las notificaciones, sin abrir la app:
 *  - "💧 +1 vaso" en el recordatorio de agua.
 *  - "📌 Pincharle de vuelta" en los pinchazos.
 */
class ActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_WATER = "com.ositos.fitness.WATER_PLUS_ONE"
        const val ACTION_POKE_BACK = "com.ositos.fitness.POKE_BACK"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val pending = goAsync()
        scope.launch {
            try {
                withTimeout(9_000) {
                    when (intent.action) {
                        ACTION_WATER -> addWater(app, notificationId)
                        ACTION_POKE_BACK -> pokeBack(app, notificationId)
                    }
                }
            } catch (e: Exception) {
                Notifications.cancel(app, notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    /** Repositorio apuntando a mi dúo actual (o null si no hay sesión o dúo). */
    private suspend fun repoForMe(): Pair<DuoRepository, String>? {
        val me = Firebase.auth.currentUser?.uid ?: return null
        val duoId = Firebase.firestore.collection("users").document(me).get().await()
            .getString("duoId") ?: return null
        return DuoRepository().apply { this.duoId = duoId } to duoId
    }

    private suspend fun addWater(context: Context, notificationId: Int) {
        val (repo, _) = repoForMe() ?: return
        val goal = repo.getMyProfile()?.goalKcal ?: 2000
        repo.addWater(1, goal)
        Notifications.cancel(context, notificationId)
        Notifications.showQuiet(context, Notifications.ID_DONE, "💧 ¡Anotado! +1 vaso", "Seguí así, camellito 🐪")
    }

    private suspend fun pokeBack(context: Context, notificationId: Int) {
        val (repo, duoId) = repoForMe() ?: return
        val me = repo.uid ?: return
        val duoRef = Firebase.firestore.collection("duos").document(duoId)
        val profiles = duoRef.collection("profiles").get().await().documents.mapNotNull { Profile.from(it) }
        val mine = profiles.firstOrNull { it.uid == me } ?: return
        val partner = profiles.firstOrNull { it.uid != me } ?: return
        Notifications.cancel(context, notificationId)
        if (partner.pokesMutedUntil > System.currentTimeMillis()) {
            Notifications.showQuiet(context, Notifications.ID_DONE, "🤫 ${partner.nickname} silenció los pinchazos")
            return
        }
        // Mismo tope que en la app: 3 pinchazos por día.
        val sentToday = duoRef.collection("logs")
            .whereEqualTo("uid", me).whereEqualTo("dayKey", Dates.todayKey()).get().await()
            .documents.count { it.getString("type") == LogType.POKE.name }
        if (sentToday >= 3) {
            Notifications.showQuiet(context, Notifications.ID_DONE, "Ya pinchaste 3 veces hoy 😅")
            return
        }
        val messages = Duo.from(duoRef.get().await())?.pokeMessages ?: Duo().pokeMessages
        repo.sendPoke(partner.uid, partner.nickname, mine.nickname, messages.random())
        Notifications.showQuiet(context, Notifications.ID_DONE, "📌 ¡Pinchazo enviado a ${partner.nickname}!")
    }
}
