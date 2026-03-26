# Neuro Karaoke Project Overview

This document provides a comprehensive overview of the Neuro Karaoke Android application, including its structure, key technologies, and instructions on how to build and run the app.

## High-Level Description

The Neuro Karaoke app is a music player application that allows users to browse and play karaoke songs from the Neuro Karaoke website (neurokaraoke.com). It features a modern user interface built with Jetpack Compose and uses ExoPlayer (Media3) for audio playback with media notification support. The app connects to the Neuro Karaoke API to fetch playlists and songs.

## Package Name

`com.soul.neurokaraoke`

## Project Structure

The project is a single-module Android application with the following structure:

-   **`app` module:** The main application module.
    -   **`src/main/java/com/soul/neurokaraoke`:** The root package for the application's source code.
        -   **`MainActivity.kt`:** The main entry point of the application.
        -   **`data`:** Contains the data layer of the application.
            -   **`api/NeuroKaraokeApi.kt`:** Handles communication with the Neuro Karaoke API.
            -   **`api/LyricsApi.kt`:** Fetches lyrics from lrclib.net API.
            -   **`model/`:** Defines the data models:
                -   `Song.kt` - Song data with Singer enum (NEURO, EVIL, DUET, OTHER)
                -   `Playlist.kt` - Playlist with id, title, coverUrl, previewCovers
                -   `User.kt` - Discord user data for authentication
            -   **`repository/`:** Manages data sources:
                -   `SongRepository.kt` - Song data management
                -   `AuthRepository.kt` - Discord OAuth authentication
                -   `UserPlaylistRepository.kt` - User-created playlists (local storage)
                -   `ArtistImageRepository.kt` - Maps artist names to Last.fm image URLs
            -   **`PlaylistCatalog.kt`:** Manages local playlist catalog.
        -   **`audio`:** Audio processing components.
            -   **`AudioCacheManager.kt`:** Manages 500MB audio cache for offline/smooth playback.
            -   **`EqualizerManager.kt`:** Equalizer and bass boost controls.
        -   **`navigation`:** Defines the navigation graph and screens.
        -   **`service`:**
            -   **`MediaPlaybackService.kt`:** Background media service with notification support and audio caching.
        -   **`ui`:** Contains the UI layer.
            -   **`MainScreen.kt`:** Main screen with navigation drawer, scaffold, and mini player.
            -   **`components/`:** Reusable UI components.
                -   `AddToPlaylistSheet.kt` - Bottom sheet for adding songs to user playlists
            -   **`screens/`:** The different screens:
                -   `home/HomeScreen.kt` - Main home with Cover Distribution & Top Genres
                -   `setlist/SetlistScreen.kt` - Playlist grid with 2x2 cover previews
                -   `setlist/SetlistDetailScreen.kt` - Detailed playlist view (website-style)
                -   `player/PlayerScreen.kt` - Full player with lyrics, equalizer, queue
                -   `search/SearchScreen.kt` - Search across ALL playlists
                -   `library/FavoritesScreen.kt` - Favorites with Discord sign-in
                -   `library/PlaylistsScreen.kt` - User-created playlists
                -   `library/UserPlaylistDetailScreen.kt` - User playlist detail with Play/Shuffle/Download All
            -   **`theme/`:** Theme colors and styling (Neuro/Evil themes).
        -   **`viewmodel/`:**
            -   `PlayerViewModel.kt` - Manages player state via MediaController
            -   `AuthViewModel.kt` - Manages Discord authentication

## Key Technologies and Libraries

-   **Kotlin:** The primary programming language
-   **Jetpack Compose:** Modern Android UI toolkit
-   **ExoPlayer (Media3):** Audio playback with MediaSession support
-   **MediaSessionService:** Background playback with notification controls
-   **Media3 DataSource:** Audio caching with SimpleCache and CacheDataSource
-   **Coil:** Image loading library
-   **Jetpack Navigation:** Screen navigation
-   **Coroutines and Flow:** Async programming
-   **Android AudioFX:** Equalizer and BassBoost effects

## API Integration

- **Base URL:** `https://idk.neurokaraoke.com`
- **API URL:** `https://api.neurokaraoke.com`
- **Endpoints:**
  - `GET /public/playlist/{playlistId}` (base URL) - Returns playlist info and songs
  - `GET /api/playlists?startIndex=0&pageSize=200&isSetlist=True&year=0` (API URL) - Official setlists
  - `GET /api/playlist/public` (API URL) - Public playlists
  - `GET /api/stats/cover-distribution` (API URL) - Cover distribution stats (totalSongs, neuroCount, evilCount, duetCount, otherCount)
- **Storage URL:** `https://storage.neurokaraoke.com` - Audio and images
- **Lyrics API (primary):** `GET /api/songs/{songId}/lyrics` (API URL) - Synced lyrics as `[{time, text}]` array
- **Lyrics API (fallback):** `https://lrclib.net/api/search` - Synced lyrics fetching

## Features Implemented

### 1. Search All Songs
- Search screen loads songs from ALL playlists (not just current)
- Shows loading progress as songs are fetched
- Results update in real-time

### 2. Media Notifications
- Background playback via `MediaPlaybackService`
- Lock screen controls
- Notification with play/pause, next, previous
- Album art displayed in notification

### 3. Queue System
- View Queue button in full player
- Shows "Now Playing" and "Up Next" sections
- Tap songs in queue to play them

### 4. Lyrics (NeuroKaraoke API + lrclib.net fallback)
- **Primary:** Fetches synced lyrics from NeuroKaraoke API (`GET /api/songs/{songId}/lyrics`)
  - Song ID lookup via `audioUrl` → `absolutePath` matching against setlist data
  - Lazy-built map of ~1264 songs cached in `NeuroKaraokeApi.songIdMap`
  - TimeSpan format (`HH:mm:ss.fffffff`) parsed into milliseconds
- **Fallback:** lrclib.net API when NeuroKaraoke lyrics unavailable
- Auto-scrolls to current lyric line during playback
- Falls back to plain lyrics if synced not available
- Lyrics cached locally with source tracking (`"neurokaraoke"` or `"lrclib"`)
- Credit text shows source: "Lyrics provided by NeuroKaraoke" or "Lyrics provided by LRCLIB"

### 5. Audio Caching (Spotify-like)
- 500MB disk cache for audio files
- Aggressive buffering: 60s min, 3 min max buffer
- 30 second back-buffer for rewinding
- Seamless playback even with connection drops

### 6. Equalizer & Bass Boost
- 5-band equalizer with presets (Normal, Bass, Rock, Pop, Jazz, Classical)
- Bass boost with adjustable strength
- Settings persist across sessions

### 7. Setlist Detail Screen
- Website-style playlist detail view
- Blurred background with cover art
- 2x2 cover grid, playlist title, song count, duration
- Play, Shuffle, Favorite buttons
- Scrollable song list

### 8. User Playlists
- Create custom playlists with name and cover image
- Delete playlists
- **Add songs to playlists** from any song list via "..." menu → "Add to Playlist"
- Remove songs from playlists in user playlist detail screen
- User playlist detail screen with Play All, Shuffle, Download All buttons
- `AddToPlaylistSheet` bottom sheet with inline "Create New Playlist" option
- `UserPlaylistDetailScreen` with header, action buttons, and song list
- Stored locally via SharedPreferences

### 9. Theme Support
- **Neuro theme** (cyan/teal accent)
- **Evil theme** (pink/magenta gradient)
- **Duet theme** (amethyst purple)
- **Auto theme** - Automatically switches based on current song's singer
- All screens use `MaterialTheme.colorScheme.primary` for consistency
- Theme selector in navigation drawer with 4 options: Auto, Neuro, Evil, Duet

### 12. Artists Screen
- Browse songs by original artist
- Artists derived dynamically from song catalog
- Sorted by song count (most covered artists first)
- Artist detail screen shows all songs by that artist
- Artist images from Last.fm CDN via `ArtistImageRepository`

### 13. Artist Images
- `ArtistImageRepository.kt` maps artist names to Last.fm image URLs
- Falls back to song cover art if no artist image found
- **Verified working images:** Pinocchio-P, DECO*27, Kanaria, wowaka, Hachi, livetune, Eve, Maretu, Kikuo, Mitchie M, Reol, supercell, Ado
- **To add more artists:** Add entries to the `artistImages` map:
  ```kotlin
  "artist name" to "https://lastfm.freetls.fastly.net/i/u/300x300/[image_hash].jpg"
  ```
- Find image hashes by visiting `https://www.last.fm/music/[ArtistName]` and inspecting the profile image URL

### 14. Playback State Persistence
- Remembers last played song and position across app restarts
- **Save points (3 independent locations for reliability):**
  - `Activity.onStop()` — saves when app goes to background
  - `MediaPlaybackService.onTaskRemoved()` — saves from service when app swiped from recents
  - `PlayerViewModel` — saves on `playSong()`, play/pause, song transitions, every 30s during playback, and `onCleared()`
- **Restore:** Runs immediately in `PlayerViewModel.init()` (before MediaController connects)
  - Mini player shows correct song instantly on app launch
  - Progress bar reflects saved position
- **Queue fallback on resume:** If restored song isn't in current queue, tries `allSongs`, then single-item queue
- SharedPreferences file: `"playback_state"` with keys: `last_song_id`, `last_song_title`, `last_song_artist`, `last_song_cover_url`, `last_song_audio_url`, `last_song_singer`, `last_playlist_id`, `last_position`, `last_duration`

### 10. Discord Sign-in (Prepared)
- Discord OAuth2 flow set up
- Sign-in button in navigation drawer
- User profile display when logged in
- Note: Backend integration needed to complete OAuth token exchange

### 11. Favorites (Prepared)
- Shows sign-in prompt for non-authenticated users
- Ready to display favorites when synced from server
- Heart icon on player (needs backend integration)

## Stats

**Cover Distribution:** Fetched live from `GET /api/stats/cover-distribution`. Falls back to hardcoded defaults while loading.

**Top Genres (Hardcoded):**
- Electronic: 403
- J-Pop: 363
- Alternative Rock: 279
- Vocaloid: 265
- Pop: 263
- Rock: 184
- Anime: 148
- Pop Rock: 139

## Building and Running the App

1.  Open the project in Android Studio.
2.  Connect a device or start an emulator.
3.  Click the "Run" button in the toolbar.

## Key Files for Common Tasks

- **Playback control:** `viewmodel/PlayerViewModel.kt`
- **Media service:** `service/MediaPlaybackService.kt`
- **Audio caching:** `audio/AudioCacheManager.kt`
- **Equalizer:** `audio/EqualizerManager.kt`
- **Lyrics:** `data/api/LyricsApi.kt`
- **API calls:** `data/api/NeuroKaraokeApi.kt`
- **Authentication:** `data/repository/AuthRepository.kt`, `viewmodel/AuthViewModel.kt`
- **User playlists:** `data/repository/UserPlaylistRepository.kt`
- **Add to playlist sheet:** `ui/components/AddToPlaylistSheet.kt`
- **User playlist detail:** `ui/screens/library/UserPlaylistDetailScreen.kt`
- **Setlist grid:** `ui/screens/setlist/SetlistScreen.kt`
- **Setlist detail:** `ui/screens/setlist/SetlistDetailScreen.kt`
- **Home screen:** `ui/screens/home/HomeScreen.kt`
- **Full player:** `ui/screens/player/PlayerScreen.kt`
- **Theme colors:** `ui/theme/Color.kt`
- **Navigation:** `navigation/Screen.kt`, `navigation/NavGraph.kt`

## TODO / Pending Work

1. **Discord OAuth Backend Integration:** The OAuth flow opens Discord authorization, but the token exchange requires backend support at `neurokaraoke.com/signin-discord`

2. **Favorites Sync:** Once authentication works, favorites can be synced from the server

3. **Dynamic Top Genres:** Replace hardcoded Top Genres with API values when endpoint is available

4. **Android Auto & TV Support:** Prepared but disabled for now - requires additional testing and MediaLibraryService implementation
