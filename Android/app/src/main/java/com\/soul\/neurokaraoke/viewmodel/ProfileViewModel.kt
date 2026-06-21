package com.soul.neurokaraoke.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soul.neurokaraoke.data.api.ProfileApi
import com.soul.neurokaraoke.data.model.Badge
import com.soul.neurokaraoke.data.model.Profile
import com.soul.neurokaraoke.data.model.UploadLimits
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: Profile? = null,
    val badges: List<Badge> = emptyList(),
    val uploadLimits: UploadLimits? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ProfileViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun load(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val badgeDeferred = async { ProfileApi.fetchBadgeProfile(token) }
            val limitsDeferred = async { ProfileApi.fetchUploadLimits(token) }

            val badgeResult = badgeDeferred.await()
            val limitsResult = limitsDeferred.await()

            val newState = _uiState.value.copy(isLoading = false)
            _uiState.value = newState.copy(
                profile = badgeResult.getOrNull()?.profile ?: newState.profile,
                badges = badgeResult.getOrNull()?.badges ?: newState.badges,
                uploadLimits = limitsResult.getOrNull() ?: newState.uploadLimits,
                error = badgeResult.exceptionOrNull()?.message
            )
        }
    }

    fun refresh(token: String) = load(token)
}
