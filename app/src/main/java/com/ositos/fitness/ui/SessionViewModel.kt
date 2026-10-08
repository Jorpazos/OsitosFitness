package com.ositos.fitness.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ositos.fitness.data.Account
import com.ositos.fitness.data.DuoRepository
import com.ositos.fitness.data.PinInfo
import com.ositos.fitness.data.Profile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

sealed interface Session {
    data object Loading : Session
    data object ConfigMissing : Session
    data object SignedOut : Session
    /** Todavía sin compañero/a: muestra tu PIN y pide el del otro. */
    data class NeedsPartner(val account: Account) : Session
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
                val duoId = account.duoId
                if (duoId == null) {
                    _session.value = Session.NeedsPartner(account)
                    watchForPairing()
                    return@launch
                }
                enterDuo(duoId)
            } catch (e: Exception) {
                _session.value = Session.Error(e.message ?: "Algo salió mal")
            }
        }
    }

    private suspend fun enterDuo(duoId: String) {
        val user = repo.auth.currentUser ?: return
        repo.duoId = duoId
        val profile = repo.getMyProfile()
        _session.value = if (profile?.onboarded == true) {
            runCatching { repo.saveFcmToken() }
            Session.Ready(user.uid, duoId)
        } else {
            Session.NeedsProfile(user.uid, user.displayName, profile)
        }
    }

    /** Si el otro pone MI PIN, mi cuenta cambia sola: entramos al dúo sin hacer nada. */
    private fun watchForPairing() {
        accountWatch?.cancel()
        accountWatch = viewModelScope.launch {
            val acc = repo.accountFlow().catch { }.firstOrNull { it?.duoId != null } ?: return@launch
            val duoId = acc.duoId ?: return@launch
            if (_session.value is Session.NeedsPartner) {
                runCatching { enterDuo(duoId) }
                    .onFailure { _session.value = Session.Error(it.message ?: "Algo salió mal") }
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

    fun confirmPair() {
        val found = (_pair.value as? PairState.Found)?.info ?: return
        val account = (_session.value as? Session.NeedsPartner)?.account ?: return
        _pair.value = PairState.Pairing
        accountWatch?.cancel()
        viewModelScope.launch {
            try {
                val duoId = repo.pairWith(account, found.pin)
                _pair.value = PairState.Idle
                enterDuo(duoId)
            } catch (e: Exception) {
                _pair.value = PairState.Error(e.message ?: "No se pudo emparejar")
                watchForPairing()
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
