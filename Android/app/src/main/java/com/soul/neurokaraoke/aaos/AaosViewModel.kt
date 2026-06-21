package com.soul.neurokaraoke.aaos

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soul.neurokaraoke.data.PlaylistCatalog
import com.soul.neurokaraoke.data.SongCache
import com.soul.neurokaraoke.data.api.NeuroKaraokeApi
import com.soul.neurokaraoke.data.model.Playlist
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.model.User
import com.soul.neurokaraoke.data.repository.AuthRepository
import com.soul.neurokaraoke.data.repository.FavoritesRepository
import com.soul.neurokaraoke.data.repository.SongRepository
import com.soul.neurokaraoke.data.repository.UserPlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AaosViewModel(app: Application) : AndroidViewModel(app) {

    private val authRepo = AuthRepository(app)
    val favoritesRepo = FavoritesRepository(app)
    val userPlaylistRepo = UserPlaylistRepository(app)

    val favorites: StateFlow<List<Song>> = favoritesRepo.favorites
    val userPlaylists: StateFlow<List<Playlist>> = userPlaylistRepo.playlists
    val isLoggedIn: StateFlow<Boolean> = authRepo.isLoggedIn
    val currentUser: StateFlow<User?> = authRepo.currentUser

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _setlists = MutableStateFlow<List<Playlist>>(emptyList())
    val setlists: StateFlow<List<Playlist>> = _setlists.asStateFlow()

    private val _trending = MutableStateFlow<List<Song>>(emptyList())
    val trending: StateFlow<List<Song>> = _trending.asStateFlow()

    private val _dailyMix = MutableStateFlow<List<Song>>(emptyList())
    val dailyMix: StateFlow<List<Song>> = _dailyMix.asStateFlow()

    private val _communityPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val communityPlaylists: StateFlow<List<Playlist>> = _communityPlaylists.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var bootstrapped = false

    fun bootstrap(context: Context) {
        if (bootstrapped) return
        bootstrapped = true

        // Share-with-phone server sync: if user is signed into Discord,
        // pull favorites + playlists from neurokaraoke.com (same flow phone uses).
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val cache = SongCache(context)
                val cached = cache.getCachedSongs()
                if (cached.isNotEmpty()) {
                    _songs.value = cached
                } else {
                    val repo = SongRepository(NeuroKaraokeApi())
                    val fetched = repo.getAllSongs().getOrNull().orEmpty()
                    if (fetched.isNotEmpty()) {
                        cache.cacheSongs(fetched, 0)
                        _songs.value = fetched
                    }
                }
                _setlists.value = PlaylistCatalog(context).getPlaylists()

                // Daily mix: deterministic shuffle by today's date so it stays
                // stable across screen reopens within the same day.
                val seed = java.time.LocalDate.now().toEpochDay()
                _dailyMix.value = _songs.value.shuffled(java.util.Random(seed)).take(15)

                // Trending: server-side weekly chart
                val api = NeuroKaraokeApi()
                api.fetchTrendingSongs(days = 7).onSuccess { apiSongs ->
                    _trending.value = apiSongs.mapIndexedNotNull { idx, s ->
                        com.soul.neurokaraoke.data.model.Song(
                            id = "trend_$idx",
                            title = s.title,
                            artist = s.originalArtists.orEmpty(),
                            coverUrl = s.getCoverArtUrl().orEmpty(),
                            audioUrl = s.audioUrl.orEmpty(),
                            singer = com.soul.neurokaraoke.data.model.Singer.fromCoverArtists(s.coverArtists),
                            coverArtists = s.coverArtists.orEmpty(),
                            artCredit = s.artCredit
                        )
                    }.take(15)
                }

                // Community playlists
                api.fetchPublicPlaylists().onSuccess { apiPls ->
                    _communityPlaylists.value = apiPls.shuffled().take(15).map {
                        Playlist(
                            id = it.id,
                            title = it.name,
                            coverUrl = it.coverUrl.orEmpty(),
                            previewCovers = it.mosaicCovers,
                            songCount = it.songCount
                        )
                    }
                }

                accessToken()?.let { token ->
                    favoritesRepo.syncFromServer(token)
                    userPlaylistRepo.syncFromServer(token)
                }
            }
            _loading.value = false
        }
    }

    fun refreshFromServer() {
        val token = accessToken() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            favoritesRepo.syncFromServer(token)
            userPlaylistRepo.syncFromServer(token)
        }
    }

    private fun accessToken(): String? =
        authRepo.currentUser.value?.apiToken ?: authRepo.currentUser.value?.accessToken

    private val syncApi = com.soul.neurokaraoke.data.api.SyncApi()
    private val _pairingStatus = MutableStateFlow<String?>(null)
    val pairingStatus: StateFlow<String?> = _pairingStatus.asStateFlow()

    fun redeemPairingCode(code: String) {
        _pairingStatus.value = "Redeeming…"
        viewModelScope.launch(Dispatchers.IO) {
            syncApi.redeemPairingCode(code).fold(
                onSuccess = { jwt ->
                    if (authRepo.parseJwtAndSaveUser(jwt)) {
                        _pairingStatus.value = "Signed in"
                        refreshFromServer()
                    } else {
                        _pairingStatus.value = "Error: invalid token"
                    }
                },
                onFailure = { _pairingStatus.value = "Error: ${it.message}" }
            )
        }
    }

    fun clearPairingStatus() { _pairingStatus.value = null }

    fun logout() {
        authRepo.logout()
    }
}
