package com.soul.neurokaraoke.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soul.neurokaraoke.data.api.GitHubRelease
import com.soul.neurokaraoke.data.repository.UpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UpdateUiState(
    val showDialog: Boolean = false,
    val latestRelease: GitHubRelease? = null,
    val currentVersion: String = "",
    val isChecking: Boolean = false
)

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val updateRepository = UpdateRepository(application)

    private val _uiState = MutableStateFlow(UpdateUiState(
        currentVersion = updateRepository.getCurrentVersion()
    ))
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /**
     * Check for updates if cooldown has passed
     */
    fun checkForUpdate() {
        if (!updateRepository.shouldCheckForUpdates()) {
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true)

            updateRepository.fetchLatestRelease().fold(
                onSuccess = { release ->
                    updateRepository.recordUpdateCheck()

                    val remoteVersion = release.getVersionString()
                    val currentVersion = updateRepository.getCurrentVersion()

                    val isNewer = updateRepository.isNewerVersion(remoteVersion, currentVersion)
                    val shouldShow = updateRepository.shouldShowUpdateFor(remoteVersion)

                    _uiState.value = _uiState.value.copy(
                        isChecking = false,
                        showDialog = isNewer && shouldShow,
                        latestRelease = if (isNewer && shouldShow) release else null
                    )
                },
                onFailure = {
                    // Silent failure - don't bother the user if update check fails
                    _uiState.value = _uiState.value.copy(isChecking = false)
                }
            )
        }
    }

    /**
     * Dismiss the update dialog and record the dismissal
     */
    fun dismissUpdate() {
        val release = _uiState.value.latestRelease
        if (release != null) {
            updateRepository.dismissVersion(release.getVersionString())
        }
        _uiState.value = _uiState.value.copy(
            showDialog = false,
            latestRelease = null
        )
    }

    /**
     * Get intent to uninstall current app first (fixes signing key conflicts)
     */
    fun getUninstallIntent(): Intent {
        return Intent(Intent.ACTION_DELETE, Uri.parse("package:${getApplication<Application>().packageName}"))
    }

    /**
     * Get intent to open the download URL
     */
    fun getUpdateIntent(): Intent? {
        val release = _uiState.value.latestRelease ?: return null
        val downloadUrl = release.getDownloadUrl()
        return Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
    }

    /**
     * Hide the dialog without recording dismissal (for when user clicks Update)
     */
    fun hideDialog() {
        _uiState.value = _uiState.value.copy(
            showDialog = false
        )
    }
}
