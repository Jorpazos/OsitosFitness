package com.ositos.fitness.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ositos.fitness.data.Account
import com.ositos.fitness.data.DuoKind
import com.ositos.fitness.data.DuoRepository
import com.ositos.fitness.data.PinInfo
import com.ositos.fitness.data.Profile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed interface Session {
    data object Loading : Session
    data object ConfigMissing : Session
    data object SignedOut : Session
    /** Todavía sin compañero/a: muestra tu PIN y pide el del otro. */
    data class NeedsPartner(val account: Account, val canGoBack: Boolean = false) : Session
    data class NeedsProfile(val uid: String, val displayName: String?, val draft: Profile?) : Session
    data class Ready(val uid: String, val duoId: String) : Session
    data class Error(val message: String) : Session
}

/** Estado de la búsqueda de PIN en la pantalla de emparejar. */
sealed interface PairState {
    data object Idle : PairState
    data object Searching : PairState
    data class Found(val info: PinInfo) : PairState
    data object Pairing : PairState
    data class Error(val message: String) : PairState
}

class SessionViewModel(
    private val repo: DuoRepository,
    private val placeholder: Boolean,
) : ViewModel() {

    private val _session = MutableStateFlow<Session>(Session.Loading)
    val session: StateFlow<Session> = _session.asStateFlow()

    private val _pair = MutableStateFlow<PairState>(PairState.Idle)
    val pair: StateFlow<PairState> = _pair.asStateFlow()

    private var accountWatch: Job? = null

    /** Mi cuenta (PIN y dúo actual), siempre actualizada. */
    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { refresh() }

    init {
        if (placeholder) {
            _session.value = Session.ConfigMissing
        } else {
            repo.auth.addAuthStateListener(authListener)
        }
    }

    fun refresh() {
        val user = repo.auth.currentUser
        if (user == null) {
            accountWatch?.cancel()
            _session.value = Session.SignedOut
            return
        }
        viewModelScope.launch {
            _session.value = Session.Loading
            try {
                val account = repo.ensureAccount()
                _account.value = account
                val duoId = account.duoId
                if (duoId == null) {
                    _session.value = Session.NeedsPartner(account)
                } else {
                    enterDuo(duoId)
                }
                watchAccount()
            } catch (e: Exception) {
                _session.value = Session.Error(e.message ?: "Algo salió mal")
            }
        }
    }

    private suspend fun enterDuo(duoId: String) {
        val user = repo.auth.currentUser ?: return
        // Si venía de modo solo, sus registros se copian al dúo nuevo (una sola vez).
        _account.value?.let { acc -> runCatching { repo.migrateSoloIfNeeded(acc, duoId) } }
        repo.duoId = duoId
        val profile = repo.getMyProfile()
        _session.value = if (profile?.onboarded == true) {
            runCatching { repo.saveFcmToken() }
            Session.Ready(user.uid, duoId)
        } else {
            Session.NeedsProfile(user.uid, user.displayName, profile)
        }
    }

    /**
     * Escucha mi cuenta todo el tiempo:
     *  - si el otro pone MI PIN, entro al dúo sin hacer nada;
     *  - si el otro se desempareja, vuelvo a la pantalla de emparejar.
     */
    private fun watchAccount() {
        accountWatch?.cancel()
        accountWatch = viewModelScope.launch {
            repo.accountFlow().catch { }.collect { acc ->
                if (acc == null) return@collect
                _account.value = acc
                val duoId = acc.duoId
                val current = _session.value
                val realDuo = duoId != null && duoId != acc.soloDuoId
                val shouldEnter = when {
                    _pair.value is PairState.Pairing -> false
                    current is Session.NeedsPartner -> realDuo
                    current is Session.Ready -> duoId != null && duoId != current.duoId
                    else -> false
                }
                if (duoId == null && (current is Session.Ready || current is Session.NeedsProfile)) {
                    repo.duoId = null
                    _session.value = Session.NeedsPartner(acc)
                } else if (shouldEnter && duoId != null) {
                    runCatching { enterDuo(duoId) }
                        .onFailure { _session.value = Session.Error(it.message ?: "Algo salió mal") }
                }
            }
        }
    }

    /** "Empezar solo por ahora": usa la app sin compañero; el PIN sigue libre para emparejar. */
    fun startSolo(onError: (String) -> Unit) {
        val acc = _account.value ?: return
        viewModelScope.launch {
            try {
                _session.value = Session.Loading
                val id = repo.startSolo(acc)
                _account.value = acc.copy(duoId = id, soloDuoId = id)
                enterDuo(id)
            } catch (e: Exception) {
                _session.value = Session.NeedsPartner(acc)
                onError(e.message ?: "No se pudo empezar en modo solo")
            }
        }
    }

    /** Desde el modo solo: ir a la pantalla del PIN para armar un dúo. */
    fun openPairing() {
        val acc = _account.value ?: return
        _pair.value = PairState.Idle
        _session.value = Session.NeedsPartner(acc, canGoBack = true)
    }

    /** Volver al modo solo sin emparejar. */
    fun backToSolo() {
        val id = _account.value?.duoId ?: return
        viewModelScope.launch { runCatching { enterDuo(id) } }
    }

    fun leaveDuo(onError: (String) -> Unit) {
        val acc = _account.value ?: return
        viewModelScope.launch {
            try {
                repo.leaveDuo(acc)
                val updated = acc.copy(duoId = null)
                _account.value = updated
                _pair.value = PairState.Idle
                _session.value = Session.NeedsPartner(updated)
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo desemparejar")
            }
        }
    }

    fun searchPin(pin: String) {
        _pair.value = PairState.Searching
        viewModelScope.launch {
            _pair.value = try {
                PairState.Found(repo.findPin(pin))
            } catch (e: Exception) {
                PairState.Error(e.message ?: "No se pudo buscar")
            }
        }
    }

    fun confirmPair(kind: DuoKind) {
        val found = (_pair.value as? PairState.Found)?.info ?: return
        val account = (_session.value as? Session.NeedsPartner)?.account ?: return
        _pair.value = PairState.Pairing
        viewModelScope.launch {
            try {
                val duoId = repo.pairWith(account, found.pin, kind)
                _pair.value = PairState.Idle
                enterDuo(duoId)
            } catch (e: Exception) {
                _pair.value = PairState.Error(e.message ?: "No se pudo emparejar")
            }
        }
    }

    fun resetPair() {
        _pair.value = PairState.Idle
    }

    fun signInWithGoogleToken(idToken: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                _session.value = Session.Loading
                repo.signInWithGoogle(idToken)
            } catch (e: Exception) {
                _session.value = Session.SignedOut
                onError(e.message ?: "No se pudo iniciar sesión")
            }
        }
    }

    fun completeOnboarding(profile: Profile, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                repo.saveProfile(profile.copy(onboarded = true))
                // Primer pesaje: arranca el gráfico y el día con XP.
                repo.addWeight(profile.weightKg, profile.goalKcal)
                runCatching { repo.saveFcmToken() }
                _session.value = Session.Ready(profile.uid, repo.duoId ?: return@launch)
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo guardar")
            }
        }
    }

    fun signOut() {
        accountWatch?.cancel()
        repo.signOut()
    }

    override fun onCleared() {
        repo.auth.removeAuthStateListener(authListener)
    }
}
