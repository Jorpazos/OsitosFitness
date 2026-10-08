package com.ositos.fitness.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ositos.fitness.data.AiFoodResult
import com.ositos.fitness.data.AiRepository
import com.ositos.fitness.data.CoopType
import com.ositos.fitness.data.Duo
import com.ositos.fitness.data.DuoRepository
import com.ositos.fitness.data.LogEntry
import com.ositos.fitness.data.Profile
import com.ositos.fitness.data.Week
import com.ositos.fitness.domain.Achievement
import com.ositos.fitness.domain.Achievements
import com.ositos.fitness.domain.Dates
import com.ositos.fitness.domain.HealthCalculator
import com.ositos.fitness.domain.Level
import com.ositos.fitness.domain.Xp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface UiEvent {
    data class Toast(val message: String) : UiEvent
    data class XpGained(val amount: Int) : UiEvent
    data class AchievementUnlocked(val achievement: Achievement) : UiEvent
    data class LevelUp(val level: Level) : UiEvent
    data class PokeReceived(val fromName: String, val message: String) : UiEvent
    data object Confetti : UiEvent
}

sealed interface PhotoState {
    data object Idle : PhotoState
    data object Analyzing : PhotoState
    data class Result(val result: AiFoodResult) : PhotoState
    data class Error(val message: String) : PhotoState
}

class DuoViewModel(
    private val repo: DuoRepository,
    private val ai: AiRepository,
    private val myUid: String,
) : ViewModel() {

    private val _state = MutableStateFlow(DuoState(myUid = myUid))
    val state: StateFlow<DuoState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _photo = MutableStateFlow<PhotoState>(PhotoState.Idle)
    val photo: StateFlow<PhotoState> = _photo.asStateFlow()

    private var lastLevel: Int? = null
    private val unlocking = mutableSetOf<String>()
    private val closingWeeks = mutableSetOf<String>()

    init {
        val base = combine(
            repo.duoFlow().onStart { emit(null) },
            repo.profilesFlow().onStart { emit(emptyMap()) },
            repo.daysFlow().onStart { emit(emptyList()) },
        ) { duo, profiles, days -> Triple(duo, profiles, days) }

        viewModelScope.launch {
            combine(
                base,
                repo.weightsFlow().onStart { emit(emptyList()) },
                repo.logsFlow().onStart { emit(emptyList()) },
                repo.allWeeksFlow().onStart { emit(emptyList()) },
                repo.measuresFlow().onStart { emit(emptyList()) },
            ) { (duo, profiles, days), weights, logs, weeks, measures ->
                Summaries.build(myUid, duo, profiles, days, weights, logs, weeks, measures)
            }
                .catch { _events.tryEmit(UiEvent.Toast("Error de conexión: ${it.message}")) }
                .collect { s ->
                    _state.value = s
                    onNewState(s)
                }
        }

        viewModelScope.launch {
            repo.pokesToMeFlow().catch { }.collect { pokes ->
                val s = _state.value
                val muted = (s.me?.profile?.pokesMutedUntil ?: 0) > System.currentTimeMillis()
                pokes.forEach { poke ->
                    if (!muted) _events.tryEmit(UiEvent.PokeReceived(s.nameOf(poke.from), poke.message))
                    runCatching { repo.markPokeDelivered(poke.id) }
                }
            }
        }
    }

    private fun onNewState(s: DuoState) {
        val me = s.me ?: return

        // Subida de nivel
        val lvl = me.level.number
        lastLevel?.let { prev -> if (lvl > prev) _events.tryEmit(UiEvent.LevelUp(me.level)) }
        lastLevel = lvl

        // Logros
        val ctx = Summaries.achievementContext(me, s, s.duo)
        val newOnes = Achievements.unlocked(ctx) - me.profile.achievements.keys - unlocking
        if (newOnes.isNotEmpty()) {
            unlocking += newOnes
            viewModelScope.launch {
                runCatching { repo.unlockAchievements(newOnes) }
                    .onSuccess {
                        newOnes.mapNotNull { Achievements.byId(it) }
                            .forEach { _events.tryEmit(UiEvent.AchievementUnlocked(it)) }
                    }
                unlocking -= newOnes
            }
        }

        // Cierre de duelos de semanas anteriores
        if (s.partner != null) closePastWeeks(s)
    }

    private fun closePastWeeks(s: DuoState) {
        val me = s.me ?: return
        val partner = s.partner ?: return
        val today = LocalDate.now()
        for (weeksAgo in 1..4) {
            val date = today.minusWeeks(weeksAgo.toLong())
            val key = Dates.weekKey(date)
            if (key in closingWeeks) continue
            if (s.weeks.any { it.key == key && it.closed }) continue
            val keys = Dates.weekDays(date).map { Dates.key(it) }.toSet()
            val hadActivity = (me.days + partner.days).any { it.dayKey in keys }
            if (!hadActivity) continue
            closingWeeks += key
            val xp = mapOf(
                me.profile.uid to Summaries.weekXp(me.profile.uid, me.days + partner.days, date),
                partner.profile.uid to Summaries.weekXp(partner.profile.uid, me.days + partner.days, date),
            )
            val week = s.weeks.firstOrNull { it.key == key } ?: Week(key)
            val coopDone = Summaries.coop(week, me.days + partner.days, date).done
            val winnerUid = xp.entries.sortedByDescending { it.value }.let { if (it[0].value > it[1].value) it[0].key else null }
            val loserUid = winnerUid?.let { w -> xp.keys.first { it != w } }
            viewModelScope.launch {
                runCatching { repo.closeWeek(key, xp, coopDone, s.nameOf(winnerUid), s.nameOf(loserUid)) }
            }
        }
    }

    private fun launchAction(xp: Int = 0, success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                if (xp > 0) _events.tryEmit(UiEvent.XpGained(xp))
                success?.let { _events.tryEmit(UiEvent.Toast(it)) }
            } catch (e: Exception) {
                _events.tryEmit(UiEvent.Toast("Ups: ${e.message}"))
            }
        }
    }

    private val goal get() = _state.value.me?.profile?.goalKcal ?: 2000

    /** XP marginal que da registrar algo hoy (respeta los topes diarios). */
    private fun xpIfMeal(): Int = (_state.value.me?.today?.meals ?: 0).let { if (it < Xp.MAX_MEALS) Xp.PER_MEAL else 0 }
    private fun xpIfExercise(): Int = (_state.value.me?.today?.exercises ?: 0).let { if (it < Xp.MAX_EXERCISES) Xp.PER_EXERCISE else 0 }

    fun addMeal(name: String, kcal: Int, detail: String = "", emoji: String = "🍽️") =
        launchAction(xpIfMeal()) { repo.addMeal(name.ifBlank { "Comida" }, kcal, detail, emoji, goal) }

    fun addExercise(name: String, kcal: Int, detail: String, emoji: String) =
        launchAction(xpIfExercise()) { repo.addExercise(name, kcal, detail, emoji, goal) }

    fun addWeight(kg: Double) {
        val me = _state.value.me ?: return
        val p = me.profile
        val bmr = HealthCalculator.bmr(p.sex, kg, p.heightCm, p.age)
        val tdee = HealthCalculator.tdee(bmr, p.activity)
        val newGoal = HealthCalculator.dailyGoalKcal(p.sex, tdee, p.goal)
        val xp = if (me.today.weighed) 0 else Xp.WEIGH_IN
        launchAction(xp) { repo.addWeight(kg, newGoal) }
    }

    fun addMeasures(m: com.ositos.fitness.data.MeasureEntry) {
        val xp = if (_state.value.me?.today?.measured == true) 0 else com.ositos.fitness.domain.Xp.MEASURED
        launchAction(xp, success = "📏 Medidas guardadas") { repo.addMeasures(m, goal) }
    }

    fun addWater(delta: Int = 1) {
        val current = _state.value.me?.today?.water ?: 0
        if (delta < 0 && current <= 0) return
        val xp = if (delta > 0 && current < Xp.MAX_WATER) Xp.PER_WATER else 0
        launchAction(xp) { repo.addWater(delta, goal) }
    }

    fun deleteLog(entry: LogEntry) = launchAction(success = "Borrado 🗑️") { repo.deleteLog(entry) }

    fun react(entry: LogEntry, emoji: String) {
        val current = entry.reactions[myUid]
        launchAction { repo.react(entry.id, if (current == emoji) null else emoji) }
    }

    fun sendPoke(message: String? = null): Boolean {
        val s = _state.value
        val partner = s.partner ?: return false
        val me = s.me ?: return false
        if (s.pokesSentToday >= 3) {
            _events.tryEmit(UiEvent.Toast("Ya pinchaste 3 veces hoy. ¡Dejalo respirar! 😅"))
            return false
        }
        if (partner.profile.pokesMutedUntil > System.currentTimeMillis()) {
            _events.tryEmit(UiEvent.Toast("${partner.name} silenció los pinchazos por un rato 🤫"))
            return false
        }
        val msg = message ?: (s.duo?.pokeMessages ?: Duo().pokeMessages).random()
        launchAction(success = "📌 ¡Pinchazo enviado a ${partner.name}!") {
            repo.sendPoke(partner.profile.uid, partner.name, me.name, msg)
        }
        return true
    }

    fun mutePokes(hours: Int) {
        val until = if (hours <= 0) 0L else System.currentTimeMillis() + hours * 3_600_000L
        launchAction(success = if (hours <= 0) "Pinchazos activados 📌" else "Silenciado por $hours h 🤫") {
            repo.setPokesMutedUntil(until)
        }
    }

    fun setForfeit(forfeit: String) = launchAction(success = "Prenda elegida: $forfeit") {
        repo.setWeekForfeit(_state.value.currentWeek.key, forfeit)
    }

    fun setCoop(type: CoopType, target: Int) = launchAction(success = "¡Desafío actualizado!") {
        repo.setCoopChallenge(_state.value.currentWeek.key, type, target)
    }

    fun updatePokeMessages(list: List<String>) = launchAction(success = "Guardado") {
        repo.updateDuoLists(pokeMessages = list.filter { it.isNotBlank() })
    }

    fun updateForfeits(list: List<String>) = launchAction(success = "Guardado") {
        repo.updateDuoLists(forfeits = list.filter { it.isNotBlank() })
    }

    fun saveProfile(p: Profile) {
        val bmr = HealthCalculator.bmr(p.sex, p.weightKg, p.heightCm, p.age)
        val tdee = HealthCalculator.tdee(bmr, p.activity)
        val goalKcal = HealthCalculator.dailyGoalKcal(p.sex, tdee, p.goal)
        launchAction(success = "Perfil actualizado ✨") { repo.saveProfile(p.copy(goalKcal = goalKcal)) }
    }

    // ---------- Foto con IA ----------

    fun aiUsedToday() = ai.usedToday()

    fun analyzePhoto(uri: Uri) {
        _photo.value = PhotoState.Analyzing
        viewModelScope.launch {
            _photo.value = try {
                PhotoState.Result(ai.analyze(uri))
            } catch (e: Exception) {
                PhotoState.Error(e.message ?: "No pude analizar la foto")
            }
        }
    }

    fun resetPhoto() {
        _photo.value = PhotoState.Idle
    }

    fun celebrate() {
        _events.tryEmit(UiEvent.Confetti)
    }
}
