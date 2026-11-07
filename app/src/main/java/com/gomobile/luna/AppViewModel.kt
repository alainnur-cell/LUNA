package com.gomobile.luna

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gomobile.luna.data.Broadcast
import com.gomobile.luna.data.ReportMeta
import com.gomobile.luna.data.Repository
import com.gomobile.luna.data.Shop
import com.gomobile.luna.data.UserProfile
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Session {
    data object Loading : Session
    data object NotConfigured : Session
    data object SignedOut : Session
    /** Signed in to Firebase, but the admin has not created a users/{uid} record yet. */
    data class NoProfile(val uid: String) : Session
    data class SignedIn(val profile: UserProfile) : Session
}

data class UiState(
    val session: Session = Session.Loading,
    val shops: List<Shop> = emptyList(),
    val shopsLoaded: Boolean = false,
    val meta: ReportMeta? = null,
    val broadcasts: List<Broadcast> = emptyList(),
    val lastSeenBroadcast: Long = 0L,
    val busy: Boolean = false,
    val message: String? = null,
) {
    val unreadBroadcasts: Int
        get() = broadcasts.count { (it.createdAt?.time ?: 0L) > lastSeenBroadcast }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository()
    private val prefs = app.getSharedPreferences("luna", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(UiState(lastSeenBroadcast = prefs.getLong(KEY_LAST_SEEN, 0L)))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var listeners: Job? = null

    init {
        if (!repo.isConfigured(app)) {
            _state.update { it.copy(session = Session.NotConfigured) }
        } else {
            val uid = repo.currentUid
            if (uid == null) _state.update { it.copy(session = Session.SignedOut) } else openSession(uid)
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(message = "Enter your email and password.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                repo.signIn(email, password)
                repo.currentUid?.let { openSession(it) }
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _state.update { it.copy(message = "Enter your email first.") }
            return
        }
        viewModelScope.launch {
            try {
                repo.sendPasswordReset(email)
                _state.update { it.copy(message = "Password reset email sent.") }
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun signOut() {
        listeners?.cancel()
        repo.signOut()
        _state.update { UiState(session = Session.SignedOut, lastSeenBroadcast = it.lastSeenBroadcast) }
    }

    fun retry() {
        repo.currentUid?.let { openSession(it) }
    }

    fun sendBroadcast(title: String, body: String, onSent: () -> Unit) {
        val profile = (state.value.session as? Session.SignedIn)?.profile ?: return
        if (title.isBlank() || body.isBlank()) {
            _state.update { it.copy(message = "Add a title and a message.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                repo.sendBroadcast(profile, title, body)
                _state.update { it.copy(message = "Broadcast sent to the team.") }
                onSent()
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun deleteBroadcast(id: String) {
        viewModelScope.launch {
            try {
                repo.deleteBroadcast(id)
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun markBroadcastsSeen() {
        val newest = state.value.broadcasts.maxOfOrNull { it.createdAt?.time ?: 0L } ?: return
        if (newest <= state.value.lastSeenBroadcast) return
        prefs.edit().putLong(KEY_LAST_SEEN, newest).apply()
        _state.update { it.copy(lastSeenBroadcast = newest) }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun openSession(uid: String) {
        listeners?.cancel()
        _state.update { it.copy(session = Session.Loading) }
        listeners = viewModelScope.launch {
            val profile = try {
                repo.loadProfile(uid)
            } catch (e: Exception) {
                _state.update { it.copy(session = Session.NoProfile(uid), message = friendly(e)) }
                return@launch
            }
            if (profile == null) {
                _state.update { it.copy(session = Session.NoProfile(uid)) }
                return@launch
            }
            _state.update { it.copy(session = Session.SignedIn(profile)) }

            launch {
                repo.shops(profile)
                    .catch { e -> _state.update { it.copy(message = friendly(e), shopsLoaded = true) } }
                    .collect { shops -> _state.update { it.copy(shops = shops, shopsLoaded = true) } }
            }
            launch {
                repo.reportMeta()
                    .catch { e -> _state.update { it.copy(message = friendly(e)) } }
                    .collect { meta -> _state.update { it.copy(meta = meta) } }
            }
            launch {
                repo.broadcasts()
                    .catch { e -> _state.update { it.copy(message = friendly(e)) } }
                    .collect { list -> _state.update { it.copy(broadcasts = list) } }
            }
        }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> "Wrong email or password."
        is FirebaseFirestoreException -> when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "You don't have access to this data. Ask the admin to check your account."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "No connection. Showing the last data saved on this phone."
            else -> e.localizedMessage ?: "Something went wrong."
        }
        else -> e.localizedMessage ?: "Something went wrong."
    }

    private companion object {
        const val KEY_LAST_SEEN = "last_seen_broadcast"
    }
}
