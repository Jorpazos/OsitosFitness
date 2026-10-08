package com.ositos.fitness.data

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.messaging
import com.ositos.fitness.domain.DayStats
import com.ositos.fitness.domain.Dates
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Lo que guarda cada persona en users/{uid}: su PIN y (si ya se emparejó) su dúo. */
data class Account(val uid: String, val pin: String, val name: String, val duoId: String?)

/** Lo que se ve al buscar un PIN. */
data class PinInfo(val pin: String, val uid: String, val name: String, val duoId: String?)

class PairException(message: String) : Exception(message)

/**
 * Acceso a Firestore. Cada persona tiene users/{uid} con un PIN propio (pins/{pin}).
 * Poniendo el PIN del otro se crea duos/{duoId} con los dos, y todo lo del dúo vive ahí adentro.
 */
class DuoRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
    val auth: FirebaseAuth = Firebase.auth,
) {
    companion object {
        /** Dúo de la primera versión de la app (antes de los PINs): se migra solo. */
        const val LEGACY_DUO_ID = "main"
        private const val PIN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // sin 0/O ni 1/I
        const val PIN_LENGTH = 6
    }

    /** Dúo activo; lo fija la sesión al entrar. */
    @Volatile
    var duoId: String? = null

    private val duoRef: DocumentReference
        get() = db.collection("duos").document(duoId ?: error("Todavía no tenés dúo"))
    private fun profiles() = duoRef.collection("profiles")
    private fun days() = duoRef.collection("days")
    private fun weights() = duoRef.collection("weights")
    private fun logs() = duoRef.collection("logs")
    private fun weeks() = duoRef.collection("weeks")
    private fun pokes() = duoRef.collection("pokes")
    private fun users() = db.collection("users")
    private fun pins() = db.collection("pins")

    val uid: String? get() = auth.currentUser?.uid

    private fun requireUid(): String = uid ?: error("No hay sesión iniciada")

    // ---------- Auth ----------

    suspend fun signInWithGoogle(idToken: String) {
        val cred = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(cred).await()
    }

    fun signOut() {
        duoId = null
        auth.signOut()
    }

    // ---------- Cuenta, PIN y emparejamiento ----------

    private fun accountFrom(d: com.google.firebase.firestore.DocumentSnapshot): Account? {
        if (!d.exists()) return null
        return Account(
            uid = d.id,
            pin = d.getString("pin") ?: return null,
            name = d.getString("name") ?: "",
            duoId = d.getString("duoId"),
        )
    }

    private fun randomPin(): String = (1..PIN_LENGTH).map { PIN_ALPHABET.random() }.joinToString("")

    /**
     * Devuelve la cuenta, creándola con un PIN único si es la primera vez.
     * Si la persona ya estaba en el dúo de la versión anterior, la migra a ese dúo.
     */
    suspend fun ensureAccount(): Account {
        val me = requireUid()
        val name = auth.currentUser?.displayName?.substringBefore(" ")?.take(20) ?: ""
        var account = accountFrom(users().document(me).get().await())
        if (account == null) {
            for (attempt in 1..8) {
                val pin = randomPin()
                val created = db.runTransaction { tx ->
                    if (tx.get(pins().document(pin)).exists()) return@runTransaction false
                    tx.set(
                        users().document(me),
                        mapOf(
                            "uid" to me, "pin" to pin, "name" to name, "duoId" to null,
                            "email" to auth.currentUser?.email, "createdAt" to System.currentTimeMillis(),
                        ),
                    )
                    tx.set(pins().document(pin), mapOf("uid" to me, "name" to name, "duoId" to null))
                    true
                }.await()
                if (created) {
                    account = Account(me, pin, name, null)
                    break
                }
            }
        }
        val acc = account ?: error("No pude generar tu PIN, probá de nuevo")
        if (acc.duoId != null) return acc

        // Migración: ¿estaba en el dúo único de la versión anterior?
        val legacy = runCatching { db.collection("duos").document(LEGACY_DUO_ID).get().await() }.getOrNull()
        @Suppress("UNCHECKED_CAST")
        val legacyMembers = legacy?.get("members") as? List<String>
        if (legacyMembers != null && me in legacyMembers) {
            val batch = db.batch()
            batch.update(users().document(me), "duoId", LEGACY_DUO_ID)
            batch.update(pins().document(acc.pin), "duoId", LEGACY_DUO_ID)
            batch.commit().await()
            return acc.copy(duoId = LEGACY_DUO_ID)
        }
        return acc
    }

    /** Escucha mi cuenta: así me entero cuando el otro pone mi PIN y quedamos emparejados. */
    fun accountFlow(): Flow<Account?> = callbackFlow {
        val reg = users().document(requireUid()).addSnapshotListener { s, e ->
            if (e != null) { close(e); return@addSnapshotListener }
            trySend(s?.let { accountFrom(it) })
        }
        awaitClose { reg.remove() }
    }

    suspend fun findPin(raw: String): PinInfo {
        val pin = raw.uppercase().filter { it.isLetterOrDigit() }
        if (pin.length != PIN_LENGTH) throw PairException("El PIN tiene $PIN_LENGTH caracteres")
        val d = pins().document(pin).get().await()
        if (!d.exists()) throw PairException("No encontramos ese PIN. Revisalo con tu compañero/a 🔍")
        val info = PinInfo(pin, d.getString("uid") ?: "", d.getString("name") ?: "Alguien", d.getString("duoId"))
        if (info.uid == uid) throw PairException("¡Ese es tu propio PIN! 😅 Poné el de tu compañero/a")
        if (info.duoId != null) throw PairException("${info.name} ya está en un dúo")
        return info
    }

    /** Crea el dúo con la persona del PIN. Los dos quedan emparejados al instante. */
    suspend fun pairWith(me: Account, partnerPin: String): String {
        val newDuo = db.collection("duos").document()
        db.runTransaction { tx ->
            val pinSnap = tx.get(pins().document(partnerPin))
            if (!pinSnap.exists()) throw PairException("No encontramos ese PIN")
            val other = pinSnap.getString("uid") ?: throw PairException("PIN inválido")
            if (other == me.uid) throw PairException("¡Ese es tu propio PIN! 😅")
            if (pinSnap.getString("duoId") != null) throw PairException("Esa persona ya está en un dúo")
            val mine = tx.get(users().document(me.uid))
            if (mine.getString("duoId") != null) throw PairException("Ya estás en un dúo")
            tx.set(
                newDuo,
                mapOf("members" to listOf(me.uid, other), "createdAt" to System.currentTimeMillis(), "pairedBy" to me.uid),
            )
            tx.update(users().document(me.uid), "duoId", newDuo.id)
            tx.update(users().document(other), "duoId", newDuo.id)
            tx.update(pins().document(me.pin), "duoId", newDuo.id)
            tx.update(pins().document(partnerPin), "duoId", newDuo.id)
            null
        }.await()
        return newDuo.id
    }

    // ---------- Dúo ----------

    fun duoFlow(): Flow<Duo?> = callbackFlow {
        val reg = duoRef.addSnapshotListener { s, e ->
            if (e != null) { close(e); return@addSnapshotListener }
            trySend(s?.let { Duo.from(it) })
        }
        awaitClose { reg.remove() }
    }

    suspend fun updateDuoLists(pokeMessages: List<String>? = null, forfeits: List<String>? = null) {
        val data = mutableMapOf<String, Any>()
        pokeMessages?.let { data["pokeMessages"] = it }
        forfeits?.let { data["forfeits"] = it }
        if (data.isNotEmpty()) duoRef.update(data).await()
    }

    // ---------- Perfiles ----------

    fun profilesFlow(): Flow<Map<String, Profile>> = callbackFlow {
        val reg = profiles().addSnapshotListener { s, e ->
            if (e != null) { close(e); return@addSnapshotListener }
            trySend(s?.documents?.mapNotNull { Profile.from(it) }?.associateBy { it.uid } ?: emptyMap())
        }
        awaitClose { reg.remove() }
    }

    suspend fun getMyProfile(): Profile? = Profile.from(profiles().document(requireUid()).get().await())

    suspend fun saveProfile(p: Profile) {
        profiles().document(p.uid).set(p.toMap(), SetOptions.merge()).await()
    }

    suspend fun setPokesMutedUntil(untilMs: Long) {
        profiles().document(requireUid()).update("pokesMutedUntil", untilMs).await()
    }

    suspend fun saveFcmToken() {
        val me = uid ?: return
        val token = runCatching { Firebase.messaging.token.await() }.getOrNull() ?: return
        profiles().document(me).set(mapOf("fcmToken" to token), SetOptions.merge()).await()
    }

    suspend fun saveFcmToken(token: String) {
        val me = uid ?: return
        profiles().document(me).set(mapOf("fcmToken" to token), SetOptions.merge()).await()
    }

    suspend fun unlockAchievements(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val me = requireUid()
        val now = System.currentTimeMillis()
        val batch = db.batch()
        batch.set(
            profiles().document(me),
            mapOf("achievements" to ids.associateWith { now }),
            SetOptions.merge(),
        )
        ids.forEach { id ->
            batch.set(logs().document(), logMap(me, "ACHIEVEMENT", "¡Logro desbloqueado!", id, "🏅"))
        }
        batch.commit().await()
    }

    // ---------- Días / registros ----------

    fun daysFlow(): Flow<List<DayStats>> = callbackFlow {
        val reg = days().addSnapshotListener { s, e ->
            if (e != null) { close(e); return@addSnapshotListener }
            trySend(s?.documents?.mapNotNull { dayStatsFrom(it) } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    fun weightsFlow(): Flow<List<WeightEntry>> = callbackFlow {
        val reg = weights().addSnapshotListener { s, e ->
            if (e != null) { close(e); return@addSnapshotListener }
            trySend(
                s?.documents?.mapNotNull { d ->
                    val u = d.getString("uid") ?: return@mapNotNull null
                    val k = d.getString("dayKey") ?: return@mapNotNull null
                    val kg = d.getDouble("kg") ?: return@mapNotNull null
                    WeightEntry(u, k, kg)
                } ?: emptyList(),
            )
        }
        awaitClose { reg.remove() }
    }

    fun logsFlow(limit: Long = 200): Flow<List<LogEntry>> = callbackFlow {
        val reg = logs().orderBy("ts", Query.Direction.DESCENDING).limit(limit)
            .addSnapshotListener { s, e ->
                if (e != null) { close(e); return@addSnapshotListener }
                trySend(s?.documents?.mapNotNull { LogEntry.from(it) } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    private fun dayRef(uid: String, dayKey: String) = days().document("${uid}_$dayKey")

    private fun logMap(
        uid: String,
        type: String,
        title: String,
        detail: String,
        emoji: String,
        kcal: Int = 0,
        weightKg: Double? = null,
        dayKey: String = Dates.todayKey(),
    ): Map<String, Any?> = mapOf(
        "uid" to uid,
        "type" to type,
        "ts" to System.currentTimeMillis(),
        "dayKey" to dayKey,
        "title" to title,
        "detail" to detail,
        "emoji" to emoji,
        "kcal" to kcal,
        "weightKg" to weightKg,
        "reactions" to emptyMap<String, String>(),
    )

    private fun dayBase(uid: String, dayKey: String, goalKcal: Int) = mapOf(
        "uid" to uid,
        "dayKey" to dayKey,
        "goalKcal" to goalKcal,
    )

    suspend fun addMeal(name: String, kcal: Int, detail: String, emoji: String, goalKcal: Int) {
        val me = requireUid()
        val day = Dates.todayKey()
        val batch = db.batch()
        batch.set(
            dayRef(me, day),
            dayBase(me, day, goalKcal) + mapOf(
                "meals" to FieldValue.increment(1),
                "kcalIn" to FieldValue.increment(kcal.toLong()),
            ),
            SetOptions.merge(),
        )
        batch.set(logs().document(), logMap(me, "MEAL", name, detail, emoji, kcal = kcal))
        batch.commit().await()
    }

    suspend fun addExercise(name: String, kcal: Int, detail: String, emoji: String, goalKcal: Int) {
        val me = requireUid()
        val day = Dates.todayKey()
        val batch = db.batch()
        batch.set(
            dayRef(me, day),
            dayBase(me, day, goalKcal) + mapOf(
                "exercises" to FieldValue.increment(1),
                "kcalOut" to FieldValue.increment(kcal.toLong()),
            ),
            SetOptions.merge(),
        )
        batch.set(logs().document(), logMap(me, "EXERCISE", name, detail, emoji, kcal = kcal))
        batch.commit().await()
    }

    /**
     * Guarda el peso del día (uno por día, el último pisa al anterior) y recalcula
     * la meta de kcal con el peso nuevo.
     */
    suspend fun addWeight(kg: Double, newGoalKcal: Int) {
        val me = requireUid()
        val day = Dates.todayKey()
        val batch = db.batch()
        batch.set(weights().document("${me}_$day"), mapOf("uid" to me, "dayKey" to day, "kg" to kg))
        batch.set(dayRef(me, day), dayBase(me, day, newGoalKcal) + mapOf("weighed" to true), SetOptions.merge())
        batch.set(
            profiles().document(me),
            mapOf("weightKg" to kg, "goalKcal" to newGoalKcal),
            SetOptions.merge(),
        )
        batch.set(logs().document(), logMap(me, "WEIGHT", "Se pesó", "%.1f kg".format(kg), "⚖️", weightKg = kg))
        batch.commit().await()
    }

    suspend fun addWater(delta: Int, goalKcal: Int) {
        val me = requireUid()
        val day = Dates.todayKey()
        dayRef(me, day).set(
            dayBase(me, day, goalKcal) + mapOf("water" to FieldValue.increment(delta.toLong())),
            SetOptions.merge(),
        ).await()
    }

    suspend fun deleteLog(entry: LogEntry) {
        val me = requireUid()
        if (entry.uid != me) return
        val batch = db.batch()
        batch.delete(logs().document(entry.id))
        when (entry.type) {
            LogType.MEAL -> batch.set(
                dayRef(me, entry.dayKey),
                mapOf("meals" to FieldValue.increment(-1), "kcalIn" to FieldValue.increment(-entry.kcal.toLong())),
                SetOptions.merge(),
            )
            LogType.EXERCISE -> batch.set(
                dayRef(me, entry.dayKey),
                mapOf("exercises" to FieldValue.increment(-1), "kcalOut" to FieldValue.increment(-entry.kcal.toLong())),
                SetOptions.merge(),
            )
            LogType.WEIGHT -> {
                batch.delete(weights().document("${me}_${entry.dayKey}"))
                batch.set(dayRef(me, entry.dayKey), mapOf("weighed" to false), SetOptions.merge())
            }
            else -> Unit
        }
        batch.commit().await()
    }

    suspend fun react(logId: String, emoji: String?) {
        val me = requireUid()
        val value: Any = emoji ?: FieldValue.delete()
        logs().document(logId).update("reactions.$me", value).await()
    }

    // ---------- Pinchazos ----------

    fun pokesToMeFlow(): Flow<List<Poke>> = callbackFlow {
        val me = requireUid()
        val reg = pokes().whereEqualTo("to", me).whereEqualTo("delivered", false)
            .addSnapshotListener { s, e ->
                if (e != null) { close(e); return@addSnapshotListener }
                trySend(s?.documents?.mapNotNull { Poke.from(it) } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    suspend fun undeliveredPokesToMe(): List<Poke> {
        val me = uid ?: return emptyList()
        return pokes().whereEqualTo("to", me).whereEqualTo("delivered", false).get().await()
            .documents.mapNotNull { Poke.from(it) }
    }

    suspend fun markPokeDelivered(id: String) {
        pokes().document(id).update("delivered", true).await()
    }

    suspend fun sendPoke(to: String, toNickname: String, myNickname: String, message: String) {
        val me = requireUid()
        val batch = db.batch()
        batch.set(
            pokes().document(),
            mapOf(
                "from" to me,
                "to" to to,
                "fromName" to myNickname,
                "message" to message,
                "ts" to System.currentTimeMillis(),
                "delivered" to false,
            ),
        )
        batch.set(logs().document(), logMap(me, "POKE", "Pinchó a $toNickname", message, "📌"))
        batch.set(profiles().document(me), mapOf("pokesSent" to FieldValue.increment(1)), SetOptions.merge())
        batch.commit().await()
    }

    // ---------- Semanas (duelo + desafío cooperativo) ----------

    fun weekFlow(key: String): Flow<Week> = callbackFlow {
        val reg = weeks().document(key).addSnapshotListener { s, e ->
            if (e != null) { close(e); return@addSnapshotListener }
            trySend(Week.from(key, s))
        }
        awaitClose { reg.remove() }
    }

    fun allWeeksFlow(): Flow<List<Week>> = callbackFlow {
        val reg = weeks().addSnapshotListener { s, e ->
            if (e != null) { close(e); return@addSnapshotListener }
            trySend(s?.documents?.map { Week.from(it.id, it) } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    suspend fun setWeekForfeit(key: String, forfeit: String) {
        weeks().document(key).set(
            mapOf("forfeit" to forfeit, "forfeitBy" to requireUid()),
            SetOptions.merge(),
        ).await()
    }

    suspend fun setCoopChallenge(key: String, type: CoopType, target: Int) {
        weeks().document(key).set(
            mapOf("coopType" to type.name, "coopTarget" to target),
            SetOptions.merge(),
        ).await()
    }

    /**
     * Cierra un duelo semanal (idempotente, en transacción): guarda el ganador y suma
     * victorias al marcador del dúo. Lo hace el primer celu que abre la app después del domingo.
     */
    suspend fun closeWeek(
        key: String,
        xp: Map<String, Int>,
        coopAchieved: Boolean,
        winnerName: String?,
        loserName: String?,
    ) {
        val me = requireUid()
        val weekRef = weeks().document(key)
        db.runTransaction { tx ->
            val snap = tx.get(weekRef)
            if (snap.getBoolean("closed") == true) return@runTransaction null
            val sorted = xp.entries.sortedByDescending { it.value }
            val winner = if (sorted.size == 2 && sorted[0].value > sorted[1].value) sorted[0].key else null
            tx.set(
                weekRef,
                mapOf(
                    "closed" to true,
                    "winnerUid" to winner,
                    "xp" to xp,
                    "coopAchieved" to coopAchieved,
                    "closedAt" to System.currentTimeMillis(),
                ),
                SetOptions.merge(),
            )
            val duoUpdate = mutableMapOf<String, Any>()
            if (winner != null) duoUpdate["duelsWon.$winner"] = FieldValue.increment(1)
            else duoUpdate["ties"] = FieldValue.increment(1)
            if (coopAchieved) duoUpdate["coopDone"] = FieldValue.increment(1)
            tx.update(duoRef, duoUpdate)
            val forfeit = snap.getString("forfeit")
            val title = if (winner != null) "Duelo $key: ganó $winnerName 🏆" else "Duelo $key: ¡empate! 🤝"
            val detail = when {
                winner != null && forfeit != null -> "$loserName debe cumplir: $forfeit"
                winner != null -> "$loserName, la próxima es tuya"
                else -> "Nadie cumple prenda… o los dos 😏"
            }
            tx.set(logs().document(), logMap(me, "DUEL", title, detail, "🥊"))
            null
        }.await()
    }
}
