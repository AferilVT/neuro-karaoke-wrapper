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
            exchangeConn.doOutput = true

            val exchangeBody = JSONObject().put("accessToken", accessToken).toString()
            exchangeConn.outputStream.use { it.write(exchangeBody.toByteArray()) }

            if (exchangeConn.responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = exchangeConn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                exchangeConn.disconnect()
                Log.w("AuthRepository", "Token exchange failed (${exchangeConn.responseCode}): $errorBody")
                // Fall back to Discord-only auth if exchange fails
            }

            val exchangeResponse = exchangeConn.inputStream.bufferedReader().readText()
            exchangeConn.disconnect()

            // Response may be a raw JWT or JSON with a token field
            val jwt = exchangeResponse.trim().let { raw ->
                if (raw.startsWith("{")) {
                    JSONObject(raw).optString("token", JSONObject(raw).optString("accessToken", ""))
                } else {
                    raw.removeSurrounding("\"")
                }
            }

            // 3. Fetch user info from Discord
            val userConn = URL(DISCORD_USER_URL).openConnection() as HttpURLConnection
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
