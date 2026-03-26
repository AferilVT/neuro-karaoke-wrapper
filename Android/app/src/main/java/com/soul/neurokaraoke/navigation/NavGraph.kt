package com.soul.neurokaraoke.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.soul.neurokaraoke.data.model.Artist
import com.soul.neurokaraoke.data.model.Playlist
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.ui.screens.about.AboutScreen
import com.soul.neurokaraoke.ui.screens.artists.ArtistDetailScreen
import com.soul.neurokaraoke.ui.screens.artists.ArtistsScreen
import com.soul.neurokaraoke.ui.screens.explore.ExploreScreen
import com.soul.neurokaraoke.ui.screens.explore.PlaylistDetailScreen
import com.soul.neurokaraoke.ui.screens.home.HomeScreen
import com.soul.neurokaraoke.data.repository.DownloadedSong
import com.soul.neurokaraoke.ui.screens.library.DownloadsScreen
import com.soul.neurokaraoke.ui.screens.library.FavoritesScreen
import com.soul.neurokaraoke.ui.screens.library.PlaylistsScreen
import com.soul.neurokaraoke.ui.screens.library.UserPlaylistDetailScreen
import com.soul.neurokaraoke.ui.screens.radio.RadioScreen
import com.soul.neurokaraoke.ui.screens.search.SearchScreen
import com.soul.neurokaraoke.ui.screens.soundbites.SoundbiteScreen
import com.soul.neurokaraoke.ui.screens.setlist.SetlistDetailScreen
import com.soul.neurokaraoke.ui.screens.setlist.SetlistScreen
import com.soul.neurokaraoke.data.repository.FavoritesRepository
import com.soul.neurokaraoke.data.repository.UserPlaylistRepository

@Composable
fun NavGraph(
    navController: NavHostController,
    songs: List<Song> = emptyList(),
    allSongs: List<Song> = emptyList(),
    isLoadingAllSongs: Boolean = false,
    playlists: List<Playlist> = emptyList(),
    currentPlaylistId: String? = null,
    isLoading: Boolean = false,
    favoriteSongs: List<Song> = emptyList(),
    currentSong: Song? = null,
    onSongClick: (String) -> Unit,
    onSearchSongClick: (String) -> Unit = onSongClick,
    onPlaySongWithQueue: (Song, List<Song>) -> Unit = { _, _ -> },
    onPlaylistSelect: (Playlist) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onExpandPlayer: () -> Unit,
    onLoadAllSongs: () -> Unit = {},
    apiArtists: List<Artist> = emptyList(),
    isLoadingArtists: Boolean = false,
    onLoadArtists: () -> Unit = {},
    onPlayClick: () -> Unit = {},
    onShuffleClick: () -> Unit = {},
    // Download-related
    downloadedSongs: List<DownloadedSong> = emptyList(),
    downloadProgress: Map<String, Float> = emptyMap(),
    downloadTotalSize: String = "0 B",
    onDownloadSong: (Song) -> Unit = {},
    onRemoveDownload: (String) -> Unit = {},
    onRemoveAllDownloads: () -> Unit = {},
    isDownloaded: (String) -> Boolean = { false },
    onPlayDownloaded: (String) -> Unit = {},
    onPlayAllDownloads: () -> Unit = {},
    onShuffleDownloads: () -> Unit = {},
    // User playlist related
    userPlaylistRepository: UserPlaylistRepository? = null,
    onAddToPlaylist: (Song) -> Unit = {},
    // Favorites
    favoritesRepository: FavoritesRepository? = null,
    accessToken: String? = null,
    isRefreshingFavorites: Boolean = false,
    onRefreshFavorites: () -> Unit = {},
    // Playlists sync
    isSyncingPlaylists: Boolean = false,
    onRefreshPlaylists: () -> Unit = {},
    // Radio
    isRadioPlaying: Boolean = false,
    onRadioListen: () -> Unit = {},
    onRadioStop: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                songs = allSongs.ifEmpty { songs },
                latestPlaylist = playlists.firstOrNull(),
                isLoading = isLoading,
                onSongClick = onSearchSongClick,
                onSeeAllClick = { navController.navigate(Screen.Search.route) },
                onSetlistClick = { playlistId ->
                    val playlist = playlists.find { it.id == playlistId }
                    if (playlist != null) {
                        onPlaylistSelect(playlist)
                        navController.navigate(Screen.SetlistDetail.createRoute(playlistId))
                    }
                }
            )
        }

        composable(Screen.Search.route) {
            // Trigger loading all songs when search screen is opened
            androidx.compose.runtime.LaunchedEffect(Unit) {
                onLoadAllSongs()
            }
            SearchScreen(
                songs = allSongs.ifEmpty { songs },
                isLoading = isLoadingAllSongs,
                onSongClick = onSearchSongClick,
                onAddToPlaylist = onAddToPlaylist
            )
        }

        composable(Screen.Explore.route) {
            ExploreScreen(
                onPlaylistClick = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                }
            )
        }

        composable(Screen.PlaylistDetail.route) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
            PlaylistDetailScreen(
                playlistId = playlistId,
                onBackClick = { navController.popBackStack() },
                onPlaySong = onPlaySongWithQueue
            )
        }

        composable(Screen.Artists.route) {
            // Load artists from API + all songs for detail screen
            androidx.compose.runtime.LaunchedEffect(Unit) {
                onLoadArtists()
                onLoadAllSongs()
            }
            ArtistsScreen(
                songs = allSongs.ifEmpty { songs },
                apiArtists = apiArtists,
                isLoading = isLoadingArtists || isLoadingAllSongs,
                onArtistClick = { artistName ->
                    navController.navigate(Screen.ArtistDetail.createRoute(java.net.URLEncoder.encode(artistName, "UTF-8")))
                }
            )
        }

        composable(Screen.ArtistDetail.route) { backStackEntry ->
            val artistName = backStackEntry.arguments?.getString("artistId")?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: return@composable

            val apiArtistImageUrl = apiArtists.find { it.name == artistName }?.imageUrl

            ArtistDetailScreen(
                artistName = artistName,
                songs = allSongs.ifEmpty { songs },
                onBackClick = { navController.popBackStack() },
                onSongClick = onSearchSongClick,
                onAddToPlaylist = onAddToPlaylist,
                apiArtistImageUrl = apiArtistImageUrl
            )
        }

        composable(Screen.Setlists.route) {
            SetlistScreen(
                playlists = playlists,
                currentPlaylistId = currentPlaylistId,
                onPlaylistSelect = { playlist ->
                    onPlaylistSelect(playlist)
                    navController.navigate(Screen.SetlistDetail.createRoute(playlist.id))
                }
            )
        }

        composable(Screen.SetlistDetail.route) { backStackEntry ->
            val setlistId = backStackEntry.arguments?.getString("setlistId") ?: return@composable
            val playlist = playlists.find { it.id == setlistId }

            if (playlist != null) {
                SetlistDetailScreen(
                    playlist = playlist,
                    songs = if (currentPlaylistId == setlistId) songs else emptyList(),
                    isLoading = isLoading && currentPlaylistId == setlistId,
                    currentSong = currentSong,
                    onBackClick = { navController.popBackStack() },
                    onPlayClick = onPlayClick,
                    onShuffleClick = onShuffleClick,
                    onSongClick = onSongClick,
                    onDownloadAll = { songList ->
                        songList.forEach { song -> onDownloadSong(song) }
                    }
                )
            }
        }

        composable(Screen.Radio.route) {
            RadioScreen(
                isRadioPlaying = isRadioPlaying,
                onListenClick = onRadioListen,
                onStopClick = onRadioStop
            )
        }

        composable(Screen.Soundbites.route) {
            SoundbiteScreen()
        }

        composable(Screen.About.route) {
            AboutScreen()
        }

        composable(Screen.Favorites.route) {
            FavoritesScreen(
                favoriteSongs = favoriteSongs,
                onSongClick = { song, queue -> onPlaySongWithQueue(song, queue) },
                onToggleFavorite = { song -> favoritesRepository?.toggleFavorite(song, accessToken) },
                onAddToPlaylist = onAddToPlaylist,
                isRefreshing = isRefreshingFavorites,
                onRefresh = onRefreshFavorites
            )
        }

        composable(Screen.Playlists.route) {
            PlaylistsScreen(
                onPlaylistClick = { playlistId ->
                    navController.navigate(Screen.UserPlaylistDetail.createRoute(playlistId))
                },
                externalRepository = userPlaylistRepository,
                accessToken = accessToken,
                isSyncing = isSyncingPlaylists,
                onRefresh = onRefreshPlaylists
            )
        }

        composable(Screen.UserPlaylistDetail.route) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
            if (userPlaylistRepository != null) {
                UserPlaylistDetailScreen(
                    playlistId = playlistId,
                    repository = userPlaylistRepository,
                    onBackClick = { navController.popBackStack() },
                    onPlaySong = onPlaySongWithQueue,
                    onDownloadSong = onDownloadSong,
                    onRemoveDownload = onRemoveDownload,
                    onDownloadAll = { songList -> songList.forEach { onDownloadSong(it) } },
                    isDownloaded = isDownloaded,
                    downloadProgress = downloadProgress,
                    onAddToPlaylist = onAddToPlaylist
                )
            }
        }

        composable(Screen.Downloads.route) {
            DownloadsScreen(
                downloads = downloadedSongs,
                downloadProgress = downloadProgress,
                totalSize = downloadTotalSize,
                onSongClick = onPlayDownloaded,
                onPlayAll = onPlayAllDownloads,
                onShuffleAll = onShuffleDownloads,
                onRemoveDownload = onRemoveDownload,
                onRemoveAll = onRemoveAllDownloads,
                onAddToPlaylist = onAddToPlaylist
            )
        }
    }
}
