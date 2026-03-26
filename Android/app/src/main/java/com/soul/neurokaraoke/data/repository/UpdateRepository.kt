package com.soul.neurokaraoke.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.soul.neurokaraoke.BuildConfig
import com.soul.neurokaraoke.data.api.GitHubApi
import com.soul.neurokaraoke.data.api.GitHubRelease

class UpdateRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val gitHubApi = GitHubApi(
        owner = BuildConfig.GITHUB_REPO_OWNER,
        repo = BuildConfig.GITHUB_REPO_NAME
    )

    /**
     * Fetch the latest release from GitHub
     */
    suspend fun fetchLatestRelease(): Result<GitHubRelease> {
        return gitHubApi.fetchLatestRelease()
    }

    /**
     * Get the current app version
     */
    fun getCurrentVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    /**
     * Compare two version strings (semantic versioning)
     * Returns true if remote is newer than current
     */
    fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = parseVersion(remote)
        val currentParts = parseVersion(current)

        for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val remotePart = remoteParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }

            if (remotePart > currentPart) return true
            if (remotePart < currentPart) return false
        }

        return false
    }

    private fun parseVersion(version: String): List<Int> {
        // Remove 'v' prefix, then extract all numeric sequences
        // e.g. "1.4alpha" → [1, 4], "1.4.2-beta3" → [1, 4, 2, 3]
        return Regex("\\d+")
            .findAll(version.removePrefix("v").removePrefix("V"))
            .map { it.value.toInt() }
            .toList()
    }

    /**
     * Check if we should show the update dialog for a given version
     * Returns false if the version was dismissed less than 7 days ago
     */
    fun shouldShowUpdateFor(version: String): Boolean {
        val dismissedVersion = prefs.getString(KEY_DISMISSED_VERSION, null)
        val dismissedAt = prefs.getLong(KEY_DISMISSED_AT, 0)

        if (dismissedVersion != version) {
            return true
        }

        // Check if 7 days have passed since dismissal
        val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() - dismissedAt > sevenDaysMs
    }

    /**
     * Check if enough time has passed since the last update check
     * Returns true if we should check (1 hour cooldown)
     */
    fun shouldCheckForUpdates(): Boolean {
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
        val oneHourMs = 60 * 60 * 1000L
        return System.currentTimeMillis() - lastCheck > oneHourMs
    }

    /**
     * Record that an update check was performed
     */
    fun recordUpdateCheck() {
        prefs.edit()
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .apply()
    }

    /**
     * Dismiss a version - won't show again for 7 days
     */
    fun dismissVersion(version: String) {
        prefs.edit()
            .putString(KEY_DISMISSED_VERSION, version)
            .putLong(KEY_DISMISSED_AT, System.currentTimeMillis())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "neurokaraoke_updates"
        private const val KEY_DISMISSED_VERSION = "dismissed_version"
        private const val KEY_DISMISSED_AT = "dismissed_at"
        private const val KEY_LAST_CHECK = "last_check"
    }
}
