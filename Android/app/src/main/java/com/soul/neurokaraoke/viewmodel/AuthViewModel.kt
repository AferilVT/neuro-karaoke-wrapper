package com.soul.neurokaraoke.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soul.neurokaraoke.data.model.User
import com.soul.neurokaraoke.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AuthUiState(
    val user: User? = null,
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSyncing: Boolean = false
)

/** Phase of the TV QR device-login flow. */
enum class QrPhase { Loading, Waiting, Approved, Error }

/** UI state for the TV QR login panel. [imageDataUrl] is a `data:image/png;base64,…` URL. */
data class QrLoginUi(
    val imageDataUrl: String? = null,
    val phase: QrPhase = QrPhase.Loading,
    val message: String? = null
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository(application)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.value = _uiState.value.copy(
                    user = user,
                    isLoggedIn = user != null
                )
            }
        }
    }

    /**
     * Get intent to open Discord authorization in browser
     */
    fun getSignInIntent(): Intent {
        val authUrl = authRepository.getAuthorizationUrl()
        return Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
    }

    /**
     * Handle OAuth callback (when redirected back from Discord)
     */
    fun handleAuthCallback(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            authRepository.handleAuthCallback(code).fold(
                onSuccess = { user ->
                    _uiState.value = _uiState.value.copy(
                        user = user,
                        isLoggedIn = true,
                        isLoading = false
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Authentication failed"
                    )
                }
            )
        }
    }

    /**
     * Handle user data received from deep link (after website OAuth callback)
     */
    fun handleUserFromDeepLink(user: User) {
        authRepository.saveUser(user)
        _uiState.value = _uiState.value.copy(
            user = user,
            isLoggedIn = true,
            isLoading = false
        )
    }

    /**
     * Handle JWT obtained from WebView login flow.
     */
    fun handleJwtFromWebView(jwt: String) {
        authRepository.parseJwtAndSaveUser(jwt)
    }

    /**
     * Sign in with a NeuroKaraoke username + password. On success the collected
     * currentUser flow flips isLoggedIn; on failure [AuthUiState.error] is set.
     */
    fun loginWithPassword(username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            authRepository.loginWithPassword(username.trim(), password).fold(
                onSuccess = { user ->
                    _uiState.value = _uiState.value.copy(
                        user = user, isLoggedIn = true, isLoading = false, error = null
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, error = e.message ?: "Sign-in failed"
                    )
                }
            )
        }
    }

    // ---- QR device login (TV shows a QR, phone approves) ----

    private val _qrLogin = MutableStateFlow(QrLoginUi())
    val qrLogin: StateFlow<QrLoginUi> = _qrLogin.asStateFlow()
    private var qrJob: Job? = null

    /**
     * Start the QR login loop: create a session, show its QR, poll for approval every
     * ~4s, and regenerate a fresh QR when one expires. On approval the token is saved
     * (currentUser flips isLoggedIn) and the loop ends. Idempotent while active.
     */
    fun startQrLogin() {
        if (qrJob?.isActive == true) return
        qrJob = viewModelScope.launch {
            while (isActive) {
                _qrLogin.value = QrLoginUi(phase = QrPhase.Loading)
                val session = authRepository.createQrSession().getOrNull()
                if (session == null) {
                    _qrLogin.value = QrLoginUi(phase = QrPhase.Error, message = "Couldn't load QR code")
                    delay(4000)
                    continue // retry with a fresh session
                }
                _qrLogin.value = QrLoginUi(imageDataUrl = session.qrImageDataUrl, phase = QrPhase.Waiting)

                val expiresAt = parseIsoUtcMillis(session.expiresAt) ?: (System.currentTimeMillis() + 5 * 60_000L)
                var approvedToken: String? = null
                while (isActive && System.currentTimeMillis() < expiresAt) {
                    delay(4000)
                    val poll = authRepository.pollQrSession(session.sessionId).getOrNull() ?: continue
                    if (poll.status.equals("approved", true) && !poll.token.isNullOrBlank()) {
                        approvedToken = poll.token
                        break
                    }
                    if (poll.status.equals("expired", true)) break
                }
                if (approvedToken != null) {
                    authRepository.finalizeQrLogin(approvedToken)
                    _qrLogin.value = QrLoginUi(phase = QrPhase.Approved)
                    return@launch
                }
                // expired without approval → outer loop regenerates a fresh QR
            }
        }
    }

    /** Stop the QR loop (call when the login screen leaves composition). */
    fun stopQrLogin() {
        qrJob?.cancel()
        qrJob = null
        _qrLogin.value = QrLoginUi()
    }

    /** Parse an ISO-8601 UTC timestamp (e.g. "2026-01-01T12:05:00Z") without java.time. */
    private fun parseIsoUtcMillis(s: String): Long? = try {
        if (s.isBlank()) null
        else java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .parse(s.substringBefore('.').removeSuffix("Z"))?.time
    } catch (e: Exception) {
        null
    }

    /**
     * Get the NeuroKaraoke API token for authenticated API calls.
     * Prefers the API JWT; falls back to Discord token.
     */
    fun getAccessToken(): String? =
        _uiState.value.user?.apiToken ?: _uiState.value.user?.accessToken

    /**
     * Log out the current user
     */
    fun logout() {
        authRepository.logout()
        _uiState.value = AuthUiState()
    }

    /**
     * Clear error
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
