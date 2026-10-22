package com.ositos.fitness.ui

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ositos.fitness.data.AiFoodResult
import com.ositos.fitness.data.AiRepository
import com.ositos.fitness.data.CoopType
import com.ositos.fitness.data.Duo
import com.ositos.fitness.data.DuoRepository
import com.ositos.fitness.data.Images
import com.ositos.fitness.data.LogEntry
import com.ositos.fitness.data.LogType
import com.ositos.fitness.data.Profile
import com.ositos.fitness.data.Story
import com.ositos.fitness.data.Week
import com.ositos.fitness.domain.Achievement
import com.ositos.fitness.domain.Achievements
import com.ositos.fitness.domain.Dates
import com.ositos.fitness.domain.HealthCalculator
import com.ositos.fitness.domain.Level
import com.ositos.fitness.domain.MealSlot
import com.ositos.fitness.domain.OsitoChef
import com.ositos.fitness.domain.Xp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface UiEvent {
    data class Toast(val message: String) : UiEvent
    data class XpGained(val amount: Int) : UiEvent
    data class AchievementUnlocked(val achievement: Achievement) : UiEvent
    data class LevelUp(val level: Level) : UiEvent
    data class PokeReceived(val fromName: String, val message: String, val milestone: Boolean = false) : UiEvent
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
    private val app: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(DuoState(myUid = myUid))
    val state: StateFlow<DuoState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _photo = MutableStateFlow<PhotoState>(PhotoState.Idle)
    val photo: StateFlow<PhotoState> = _photo.asStateFlow()

    private val _stories = MutableStateFlow<List<Story>>(emptyList())
    /** Historias de las últimas 24 h (de los dos). */
    val stories: StateFlow<List<Story>> = _stories.asStateFlow()

    private val _storyUploading = MutableStateFlow(false)
    val storyUploading: StateFlow<Boolean> = _storyUploading.asStateFlow()

    /** Comidas del día que ya sumaron al contador (por si se cargan dos cosas muy rápido). */
    private val countedMeals = mutableSetOf<String>()

    /**
     * Último nivel ya festejado, guardado en el celu (por persona y por dúo). Así "¡Subiste de
     * nivel!" sale una sola vez por nivel, y no cada vez que se abre la app.
     */
    private val prefs = app.getSharedPreferences("ositos", Context.MODE_PRIVATE)
    private val levelKey = "level_seen_${myUid}_${repo.duoId ?: "-"}"
    private val fatKey = "fat_kg_seen_${myUid}_${repo.duoId ?: "-"}"
    private val unlocking = mutableSetOf<String>()
    private val closingWeeks = mutableSetOf<String>()

    init {
        // Días y semanas arrancan en null = "todavía no llegaron". Mientras tanto el XP da 0
        // (nivel 1) y no hay que festejar ni cerrar duelos con datos a medias.
        val base = combine(
            repo.duoFlow().onStart { emit(null) },
            repo.profilesFlow().onStart { emit(emptyMap()) },
            repo.daysFlow().nullUntilLoaded(),
        ) { duo, profiles, days -> Triple(duo, profiles, days) }

        viewModelScope.launch {
            combine(
                base,
                repo.weightsFlow().onStart { emit(emptyList()) },
                repo.logsFlow().onStart { emit(emptyList()) },
                repo.allWeeksFlow().nullUntilLoaded(),
                repo.measuresFlow().onStart { emit(emptyList()) },
            ) { (duo, profiles, days), weights, logs, weeks, measures ->
                Summaries.build(myUid, duo, profiles, days.orEmpty(), weights, logs, weeks.orEmpty(), measures)
                    .copy(dataReady = days != null && weeks != null)
            }
                .catch { _events.tryEmit(UiEvent.Toast("Error de conexión: ${it.message}")) }
                .collect { s ->
                    _state.value = s
                    onNewState(s)
                }
        }

        viewModelScope.launch {
            repo.storiesFlow().catch { }.collect { _stories.value = it }
        }
        viewModelScope.launch { runCatching { repo.cleanupExpiredStories() } }

        viewModelScope.launch {
            repo.pokesToMeFlow().catch { }.collect { pokes ->
                val s = _state.value
                val muted = (s.me?.profile?.pokesMutedUntil ?: 0) > System.currentTimeMillis()
                pokes.forEach { poke ->
                    if (!muted || poke.isMilestone) {
                        _events.tryEmit(UiEvent.PokeReceived(s.nameOf(poke.from), poke.message, poke.isMilestone))
                    }
                    runCatching { repo.markPokeDelivered(poke.id) }
                }
            }
        }
    }

    private fun <T> Flow<List<T>>.nullUntilLoaded(): Flow<List<T>?> =
        map<List<T>, List<T>?> { it }.onStart { emit(null) }

    private fun onNewState(s: DuoState) {
        val me = s.me ?: return
        if (!s.dataReady) return

        checkLevelUp(me)
        checkFatMilestone(me, s)

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

    /**
     * Cada kilo de grasa "quemado" (por déficit acumulado): festejo, +XP (va en el total) y aviso
     * a tu compa. Una sola vez por kilo.
     */
    private fun checkFatMilestone(me: PersonSummary, s: DuoState) {
        val kg = com.ositos.fitness.domain.FatBurn.milestones(me.fatKg)
        val seen = prefs.getInt(fatKey, -1)
        if (seen < 0) {
            prefs.edit().putInt(fatKey, kg).apply()
            return
        }
        if (kg <= seen) return
        prefs.edit().putInt(fatKey, kg).apply()
        _events.tryEmit(UiEvent.XpGained(com.ositos.fitness.domain.FatBurn.XP_PER_KG))
        _events.tryEmit(UiEvent.Toast("🔥 ¡Llegaste a ~$kg kg de grasa quemada! +${com.ositos.fitness.domain.FatBurn.XP_PER_KG} XP"))
        _events.tryEmit(UiEvent.Confetti)
        val partner = s.partner ?: return
        viewModelScope.launch {
            runCatching {
                repo.sendMilestone(
                    partner.profile.uid, me.name,
                    "🔥 ${me.name} llegó a ~$kg kg de grasa quemada con su déficit. ¡Felicitalo/a!",
                )
            }
        }
    }

    /** Festeja la subida de nivel una sola vez (aunque haya pasado con la app cerrada). */
    private fun checkLevelUp(me: PersonSummary) {
        val lvl = me.level.number
        val seen = prefs.getInt(levelKey, 0)
        when {
            // Primera vez en este celu/dúo: se toma el nivel actual como punto de partida, sin festejo.
            seen == 0 -> prefs.edit().putInt(levelKey, lvl).apply()
            lvl > seen -> {
                prefs.edit().putInt(levelKey, lvl).apply()
                _events.tryEmit(UiEvent.LevelUp(me.level))
            }
        }
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

    private fun mealKey(dayKey: String, slot: MealSlot) = "${dayKey}_${slot.name}"

    /** ¿Es lo primero que se carga hoy en esa comida? (solo eso suma una "comida" y XP). */
    private fun claimNewMeal(slot: MealSlot, dayKey: String = Dates.todayKey(), slotHasEntries: Boolean? = null): Boolean {
        val key = mealKey(dayKey, slot)
        val empty = !(slotHasEntries ?: _state.value.mealsOf(myUid, dayKey)[slot].orEmpty().isNotEmpty())
        val isNew = empty && key !in countedMeals
        if (isNew) countedMeals += key
        return isNew
    }

    /** Agrega un alimento a una comida (desayuno, almuerzo…). Dos huevos = 1 comida, no 2. */
    /**
     * [dayKey]: el día al que va (hoy o uno de los últimos 7, por si te olvidaste de cargar algo).
     * [slotHasEntries]: si esa comida ya tenía algo (lo sabe la pantalla del día).
     */
    fun addMeal(
        name: String,
        kcal: Int,
        detail: String = "",
        emoji: String = "🍽️",
        slot: MealSlot = MealSlot.now(),
        dayKey: String = Dates.todayKey(),
        slotHasEntries: Boolean? = null,
    ) {
        val newMeal = claimNewMeal(slot, dayKey, slotHasEntries)
        val isToday = dayKey == Dates.todayKey()
        val xp = if (newMeal && isToday) xpIfMeal() else 0
        launchAction(xp, success = if (isToday) null else "Cargado en ${prettyDay(dayKey)} ✅") {
            try {
                repo.addMeal(name.ifBlank { "Comida" }, kcal, detail, emoji, goal, slot, newMeal, dayKey)
            } catch (e: Exception) {
                if (newMeal) countedMeals -= mealKey(dayKey, slot)
                throw e
            }
        }
    }

    private fun prettyDay(dayKey: String) = runCatching { Dates.prettyLong(java.time.LocalDate.parse(dayKey)) }.getOrDefault(dayKey)

    /** Registros de un día puntual (para el Inicio en días pasados y el Historial). */
    fun logsForDay(dayKey: String): kotlinx.coroutines.flow.Flow<List<LogEntry>> =
        repo.logsOfDayFlow(dayKey).catch { emit(emptyList()) }

    /** Carga de una todos los alimentos de una idea del Osito Chef. */
    fun addChefIdea(idea: OsitoChef.Idea, slot: MealSlot) {
        val newMeal = claimNewMeal(slot)
        val xp = if (newMeal) xpIfMeal() else 0
        launchAction(xp, success = "👨‍🍳 ¡Cargado en ${slot.label.lowercase()}!") {
            idea.items.forEachIndexed { i, f ->
                repo.addMeal(f.name, f.kcal, "${f.portion} · idea del Osito Chef", f.emoji, goal, slot, newMeal && i == 0)
            }
        }
    }

    fun addExercise(name: String, kcal: Int, detail: String, emoji: String, dayKey: String = Dates.todayKey()) {
        val isToday = dayKey == Dates.todayKey()
        launchAction(if (isToday) xpIfExercise() else 0, success = if (isToday) null else "Cargado en ${prettyDay(dayKey)} ✅") {
            repo.addExercise(name, kcal, detail, emoji, goal, dayKey)
        }
    }

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

    fun addWater(delta: Int = 1, dayKey: String = Dates.todayKey()) {
        val me = _state.value.me
        val current = me?.days?.firstOrNull { it.dayKey == dayKey }?.water ?: 0
        if (delta < 0 && current <= 0) return
        val xp = if (delta > 0 && current < Xp.MAX_WATER && dayKey == Dates.todayKey()) Xp.PER_WATER else 0
        launchAction(xp) { repo.addWater(delta, goal, dayKey) }
    }

    /** [dayLogs]: lo registrado ese día (si la pantalla lo tiene), para saber si era lo último de esa comida. */
    fun deleteLog(entry: LogEntry, dayLogs: List<LogEntry>? = null) {
        // En comidas: solo se descuenta "una comida" si era lo último cargado en esa comida.
        val sameDay = dayLogs?.filter { it.uid == entry.uid && it.type == LogType.MEAL }
            ?: _state.value.mealsOf(entry.uid, entry.dayKey).values.flatten()
        val lastOfMeal = entry.type != LogType.MEAL ||
            sameDay.none { it.id != entry.id && it.mealSlot == entry.mealSlot }
        if (entry.type == LogType.MEAL && lastOfMeal) countedMeals -= mealKey(entry.dayKey, entry.mealSlot)
        launchAction(success = "Borrado 🗑️") { repo.deleteLog(entry, lastOfMeal) }
    }

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

    fun setDuoKind(kind: com.ositos.fitness.data.DuoKind) =
        launchAction(success = "${kind.emoji} ¡Ahora son ${kind.label}!") { repo.setDuoKind(kind) }

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

    /** "Contale a la IA qué comiste": misma estimación que con la foto, pero escrita. */
    fun analyzeText(text: String) {
        if (text.isBlank()) return
        _photo.value = PhotoState.Analyzing
        viewModelScope.launch {
            _photo.value = try {
                PhotoState.Result(ai.analyzeText(text))
            } catch (e: Exception) {
                PhotoState.Error(e.message ?: "No pude estimar la comida")
            }
        }
    }

    fun resetPhoto() {
        _photo.value = PhotoState.Idle
    }

    fun celebrate() {
        _events.tryEmit(UiEvent.Confetti)
    }

    // ---------- Historias ----------

    fun postStory(uri: Uri, caption: String) {
        if (_storyUploading.value) return
        _storyUploading.value = true
        val firstToday = _state.value.me?.today?.story != true
        viewModelScope.launch {
            try {
                val b64 = withContext(Dispatchers.IO) {
                    // ~720 px y JPEG 65: entra cómodo en un documento de Firestore (máx. 1 MB).
                    Base64.encodeToString(Images.compressJpeg(app, uri, 720, 65), Base64.NO_WRAP)
                }
                repo.postStory(b64, caption.trim().take(120), goal)
                if (firstToday) _events.tryEmit(UiEvent.XpGained(Xp.STORY))
                _events.tryEmit(UiEvent.Toast("📸 ¡Historia publicada! Dura 24 h"))
            } catch (e: Exception) {
                _events.tryEmit(UiEvent.Toast("No se pudo subir la historia: ${e.message}"))
            } finally {
                _storyUploading.value = false
            }
        }
    }

    fun markStoryViewed(story: Story) {
        if (story.uid == myUid || myUid in story.viewedBy) return
        viewModelScope.launch { runCatching { repo.markStoryViewed(story.id) } }
    }

    fun reactStory(story: Story, emoji: String) {
        val current = story.reactions[myUid]
        launchAction { repo.reactStory(story.id, if (current == emoji) null else emoji) }
    }

    fun deleteStory(story: Story) = launchAction(success = "Historia borrada 🗑️") { repo.deleteStory(story.id) }
}
