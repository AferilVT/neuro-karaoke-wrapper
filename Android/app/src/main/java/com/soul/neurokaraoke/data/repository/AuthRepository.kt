package com.soul.neurokaraoke.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.soul.neurokaraoke.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

/** A QR device-login session created on the server (TV → phone approval flow). */
data class QrSession(val sessionId: String, val qrImageDataUrl: String, val expiresAt: String)

/** One poll of a QR session: `status` is "pending"/"approved"/"expired"; `token` set once approved. */
data class QrPoll(val status: String, val token: String?)

class AuthRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // PKCE code verifier — held in memory during the auth flow
    private var pendingCodeVerifier: String? = null

    init {
        loadSavedUser()
    }

    private fun loadSavedUser() {
        val userId = prefs.getString(KEY_USER_ID, null)
        val username = prefs.getString(KEY_USERNAME, null)
        val discriminator = prefs.getString(KEY_DISCRIMINATOR, "0")
        val avatar = prefs.getString(KEY_AVATAR, null)
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val apiToken = prefs.getString(KEY_API_TOKEN, null)

        if (userId != null && username != null) {
            _currentUser.value = User(
                id = userId,
                username = username,
                discriminator = discriminator ?: "0",
                avatar = avatar,
                accessToken = accessToken,
                apiToken = apiToken
            )
            _isLoggedIn.value = true
        }
    }

    /**
     * Build the Discord OAuth2 authorization URL with PKCE.
     * Generates a fresh code verifier/challenge each time.
     */
    fun getAuthorizationUrl(): String {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        pendingCodeVerifier = verifier

        return Uri.parse("https://discord.com/oauth2/authorize").buildUpon()
            .appendQueryParameter("client_id", DISCORD_CLIENT_ID)
            .appendQueryParameter("redirect_uri", DISCORD_REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "identify")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()
    }

    /**
     * Exchange authorization code for access token using PKCE (no client secret),
     * then fetch user info from Discord.
     */
    suspend fun handleAuthCallback(code: String): Result<User> = withContext(Dispatchers.IO) {
        val verifier = pendingCodeVerifier
        pendingCodeVerifier = null

        if (verifier == null) {
            return@withContext Result.failure(Exception("No pending PKCE verifier — try signing in again"))
        }

        try {
            // 1. Exchange code for access token with PKCE verifier
            val tokenConn = URL(DISCORD_TOKEN_URL).openConnection() as HttpURLConnection
            tokenConn.requestMethod = "POST"
            tokenConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            tokenConn.connectTimeout = 15000
            tokenConn.readTimeout = 15000
            tokenConn.doOutput = true

            val postData = "client_id=$DISCORD_CLIENT_ID" +
                    "&grant_type=authorization_code" +
                    "&code=$code" +
                    "&redirect_uri=${Uri.encode(DISCORD_REDIRECT_URI)}" +
                    "&code_verifier=$verifier"

            tokenConn.outputStream.use { it.write(postData.toByteArray()) }

            if (tokenConn.responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = tokenConn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                tokenConn.disconnect()
                return@withContext Result.failure(Exception("Token exchange failed (${tokenConn.responseCode}): $errorBody"))
            }

            val tokenResponse = tokenConn.inputStream.bufferedReader().readText()
            tokenConn.disconnect()

            val tokenJson = JSONObject(tokenResponse)
            val accessToken = tokenJson.getString("access_token")

            // 2. Exchange Discord access token for NeuroKaraoke JWT
            val exchangeConn = URL(NEUROKARAOKE_TOKEN_EXCHANGE_URL).openConnection() as HttpURLConnection
            exchangeConn.requestMethod = "POST"
            exchangeConn.setRequestProperty("Content-Type", "application/json")
            exchangeConn.connectTimeout = 15000
            exchangeConn.readTimeout = 15000
            exchangeConn.doOutput = true

            val exchangeBody = JSONObject().put("accessToken", accessToken).toString()
            exchangeConn.outputStream.use { it.write(exchangeBody.toByteArray()) }

            var jwt = ""
            if (exchangeConn.responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = exchangeConn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                exchangeConn.disconnect()
                Log.w("AuthRepository", "Token exchange failed (${exchangeConn.responseCode}): $errorBody")
                // Fall back to Discord-only auth if exchange fails
            } else {
                val exchangeResponse = exchangeConn.inputStream.bufferedReader().readText()
                exchangeConn.disconnect()

                // Response may be a raw JWT or JSON with a token field
                jwt = exchangeResponse.trim().let { raw ->
                    if (raw.startsWith("{")) {
                        JSONObject(raw).optString("token", JSONObject(raw).optString("accessToken", ""))
                    } else {
                        raw.removeSurrounding("\"")
                    }
                }
            }

            // 3. Fetch user info from Discord
            val userConn = URL(DISCORD_USER_URL).openConnection() as HttpURLConnection
            userConn.connectTimeout = 15000
            userConn.readTimeout = 15000
            userConn.setRequestProperty("Authorization", "Bearer $accessToken")

            if (userConn.responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = userConn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                userConn.disconnect()
                return@withContext Result.failure(Exception("Failed to fetch user info (${userConn.responseCode}): $errorBody"))
            }

            val userResponse = userConn.inputStream.bufferedReader().readText()
            userConn.disconnect()

            val userJson = JSONObject(userResponse)
            val user = User(
                id = userJson.getString("id"),
                username = if (!userJson.isNull("global_name")) userJson.getString("global_name")
                           else userJson.getString("username"),
                discriminator = userJson.optString("discriminator", "0"),
                avatar = if (userJson.isNull("avatar")) null else userJson.getString("avatar"),
                accessToken = accessToken,
                apiToken = jwt.ifBlank { null }
            )

            saveUser(user)
            Log.d("AuthRepository", "Saved user: ${user.username} (apiToken=${if (jwt.isNotBlank()) "present" else "missing"})")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Save user after successful authentication
     */
    fun saveUser(user: User) {
        prefs.edit().apply {
            putString(KEY_USER_ID, user.id)
            putString(KEY_USERNAME, user.username)
            putString(KEY_DISCRIMINATOR, user.discriminator)
            putString(KEY_AVATAR, user.avatar)
            putString(KEY_ACCESS_TOKEN, user.accessToken)
            putString(KEY_API_TOKEN, user.apiToken)
            apply()
        }
        _currentUser.value = user
        _isLoggedIn.value = true
    }

    /**
     * Parse a NeuroKaraoke JWT and save the user.
     * The JWT payload contains Discord user info in ASP.NET Core claims.
     */
    fun parseJwtAndSaveUser(jwt: String): Boolean {
        try {
            val parts = jwt.split(".")
            if (parts.size != 3) return false

            val payload = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING),
                Charsets.UTF_8
            )
            val json = JSONObject(payload)

            val userId = json.optString(
                "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier", ""
            )
            val username = json.optString(
                "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name", ""
            )
            val avatar = json.optString("urn:discord:avatar", "").ifBlank { null }

            if (userId.isBlank() || username.isBlank()) return false

            val user = User(
                id = userId,
                username = username,
                discriminator = "0",
                avatar = avatar,
                apiToken = jwt
            )
            saveUser(user)
            Log.d("AuthRepository", "Saved user from JWT: $username (id=$userId)")
            return true
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to parse JWT", e)
            return false
        }
    }

    /**
     * Sign in with a NeuroKaraoke username + password (the "merged" login the iOS app
     * and website use). Yields the same JWT as the Discord flow. The password-login JWT
     * carries no Discord claims, so we build a minimal [User] from the entered username;
     * avatar/level/badges are enriched later by ProfileViewModel.load(token).
     */
    suspend fun loginWithPassword(username: String, password: String): Result<User> =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(LOGIN_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = true
                }
                val body = JSONObject()
                    .put("username", username)
                    .put("password", password)
                    .toString()
                conn.outputStream.use { it.write(body.toByteArray()) }

                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                    val msg = runCatching { JSONObject(err).optString("message") }
                        .getOrNull()?.takeIf { it.isNotBlank() } ?: "Sign-in failed ($code)"
                    return@withContext Result.failure(Exception(msg))
                }

                val jwt = parseLoginToken(conn.inputStream.bufferedReader().readText())
                if (jwt.isBlank()) {
                    return@withContext Result.failure(Exception("No token in response"))
                }
                val user = User(
                    id = username,
                    username = username,
                    discriminator = "0",
                    avatar = null,
                    apiToken = jwt,
                )
                saveUser(user)
                Log.d("AuthRepository", "Password login OK for $username")
                Result.success(user)
            } catch (e: Exception) {
                Log.e("AuthRepository", "Password login failed", e)
                Result.failure(e)
            } finally {
                conn?.disconnect()
            }
        }

    /**
     * Create a QR device-login session (TV shows the QR, phone scans + approves).
     * POST /api/auth/qr-session -> { sessionId, qrCodeImageDataUrl, expiresAt }.
     */
    suspend fun createQrSession(): Result<QrSession> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(QR_SESSION_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
                // Server requires a Content-Length (returns 411 without one); send an
                // explicit empty body so Content-Length: 0 is set on the bodyless POST.
                doOutput = true
                setFixedLengthStreamingMode(0)
            }
            conn.outputStream.use { /* empty body */ }
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_CREATED) {
                return@withContext Result.failure(Exception("QR session failed ($code)"))
            }
            val o = JSONObject(conn.inputStream.bufferedReader().readText())
            Result.success(
                QrSession(
                    sessionId = o.getString("sessionId"),
                    qrImageDataUrl = o.getString("qrCodeImageDataUrl"),
                    expiresAt = o.optString("expiresAt", "")
                )
            )
        } catch (e: Exception) {
            Log.e("AuthRepository", "createQrSession failed", e)
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Poll a QR session for approval.
     * GET /api/auth/qr-session/{sessionId} -> { status, token, expiresAt }.
     */
    suspend fun pollQrSession(sessionId: String): Result<QrPoll> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("$QR_SESSION_URL/$sessionId").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("Poll failed (${conn.responseCode})"))
            }
            val o = JSONObject(conn.inputStream.bufferedReader().readText())
            Result.success(
                QrPoll(
                    status = o.optString("status", "pending"),
                    token = o.optString("token", "").ifBlank { null }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Finalize a QR login with the approved NeuroKaraoke JWT. Reuses the JWT-claims
     * parser (same backend as Discord); falls back to a minimal user if the token
     * carries no claims (profile is enriched later by ProfileViewModel.load).
     */
    fun finalizeQrLogin(token: String) {
        if (!parseJwtAndSaveUser(token)) {
            saveUser(
                User(id = "qr_user", username = "Account", discriminator = "0", avatar = null, apiToken = token)
            )
        }
    }

    /**
     * Log out the current user
     */
    fun logout() {
        prefs.edit().clear().apply()
        _currentUser.value = null
        _isLoggedIn.value = false
    }

    /**
     * Check if user is logged in
     */
    fun isUserLoggedIn(): Boolean = _isLoggedIn.value

    companion object {
        /** Extract the JWT from a login response (`token`, falling back to `accessToken`). */
        fun parseLoginToken(json: String): String {
            val o = JSONObject(json)
            return o.optString("token", o.optString("accessToken", ""))
        }

        private const val PREFS_NAME = "neurokaraoke_auth"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_DISCRIMINATOR = "discriminator"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_API_TOKEN = "api_token"

        private const val DISCORD_CLIENT_ID = "1447802634621943850"
        private const val DISCORD_REDIRECT_URI = "neurokaraoke://auth"
        private const val DISCORD_TOKEN_URL = "https://discord.com/api/oauth2/token"
        private const val DISCORD_USER_URL = "https://discord.com/api/users/@me"
        private const val NEUROKARAOKE_TOKEN_EXCHANGE_URL = "https://idk.neurokaraoke.com/api/auth/discord-token"
        private const val LOGIN_URL = "https://idk.neurokaraoke.com/api/auth/login"
        private const val QR_SESSION_URL = "https://api.neurokaraoke.com/api/auth/qr-session"

        private fun generateCodeVerifier(): String {
            val bytes = ByteArray(64)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

        private fun generateCodeChallenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
    }
}
