package com.soul.neurokaraoke.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null
) {
    // Main navigation screens
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Search : Screen("search", "Search", Icons.Default.Search)
    data object Explore : Screen("explore", "Browse", Icons.Default.Explore)
    data object Artists : Screen("artists", "Artists", Icons.Default.Person)
    data object Setlists : Screen("setlists", "Karaoke Setlist", Icons.AutoMirrored.Filled.QueueMusic)
    data object Radio : Screen("radio", "Radio", Icons.Default.Radio)
    data object Soundbites : Screen("soundbites", "Soundbites", Icons.Default.GraphicEq)
    data object About : Screen("about", "About", Icons.Default.Info)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    data object UploadSongs : Screen("upload_songs", "Local Music", Icons.Default.FileUpload)

    // Bottom nav screens
    data object Library : Screen("library", "Library", Icons.Default.LibraryMusic)
    data object More : Screen("more", "More", Icons.Default.MoreHoriz)

    // Library sub-screens
    data object Downloads : Screen("downloads", "Downloads", Icons.Default.Download)
    data object Favorites : Screen("favorites", "Favorites", Icons.Default.Favorite)
    data object Playlists : Screen("playlists", "Your Playlists", Icons.Default.LibraryMusic)

    // Detail screens
    data object PlaylistDetail : Screen("playlist/{playlistId}", "Playlist") {
        fun createRoute(playlistId: String) = "playlist/$playlistId"
    }
    data object ArtistDetail : Screen("artist/{artistId}", "Artist") {
        fun createRoute(artistId: String) = "artist/$artistId"
    }
    data object SetlistDetail : Screen("setlist/{setlistId}", "Setlist") {
        fun createRoute(setlistId: String) = "setlist/$setlistId"
    }
    data object UserPlaylistDetail : Screen("user_playlist/{playlistId}", "Playlist") {
        fun createRoute(playlistId: String) = "user_playlist/$playlistId"
    }

    // Player screen
    data object Player : Screen("player", "Now Playing")

    companion object {
        // Bottom navigation bar tabs (max 5)
        val bottomNavItems by lazy { listOf(Home, Radio, Search, Library, More) }

        // Items accessible from within Explore (browse) tab
        val exploreSubItems by lazy { listOf(Setlists, Artists) }

        // Library tab items
        val libraryItems by lazy { listOf(Favorites, Playlists, Downloads) }

        // More screen items
        val moreItems by lazy { listOf(Radio, Soundbites, Setlists, Artists, About) }
    }
}
