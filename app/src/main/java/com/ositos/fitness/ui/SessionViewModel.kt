package com.ositos.fitness.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ositos.fitness.data.DuoRepository
import com.ositos.fitness.data.JoinResult
import com.ositos.fitness.data.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface Session {
    data object Loading : Session
    data object ConfigMissing : Session
    data object SignedOut : Session
    data object DuoFull : Session
    data class NeedsProfile(val uid: String, val displayName: String?, val draft: Profile?) : Session
    data class Ready(val uid: String) : Session
    data class Error(val message: String) : Session
}

class SessionViewModel(
    private val repo: DuoRepository,
    private val placeholder: Boolean,
) : ViewModel() {

    private val _session = MutableStateFlow<Session>(Session.Loading)
    val session: StateFlow<Session> = _session.asStateFlow()

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
            _session.value = Session.SignedOut
            return
        }
        viewModelScope.launch {
            _session.value = Session.Loading
            try {
                val joined = try {
                    repo.joinOrCreateDuo()
                } catch (e: FirebaseFirestoreException) {
                    // Las reglas no dejan ni leer el dúo a un tercero (o a un mail no permitido).
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) JoinResult.FULL else throw e
                }
                if (joined == JoinResult.FULL) {
                    _session.value = Session.DuoFull
                    return@launch
                }
                val profile = repo.getMyProfile()
                _session.value = if (profile?.onboarded == true) {
                    runCatching { repo.saveFcmToken() }
                    Session.Ready(user.uid)
                } else {
                    Session.NeedsProfile(user.uid, user.displayName, profile)
                }
            } catch (e: Exception) {
                _session.value = Session.Error(e.message ?: "Algo salió mal")
            }
        }
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
                _session.value = Session.Ready(profile.uid)
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo guardar")
            }
        }
    }

    fun signOut() {
        repo.signOut()
    }

    override fun onCleared() {
        repo.auth.removeAuthStateListener(authListener)
    }
}
