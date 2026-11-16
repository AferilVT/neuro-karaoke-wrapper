package com.soul.neurokaraoke.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.soul.neurokaraoke.R
import com.soul.neurokaraoke.data.api.NeuroKaraokeApi
import com.soul.neurokaraoke.data.model.Playlist
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.repository.LocaleManager
import com.soul.neurokaraoke.data.repository.SongRepository
import com.soul.neurokaraoke.data.repository.UserPlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Playlist detail: header (cover+title+meta) + Play/Shuffle action strip
 * + numbered tracks. Mimics Apple Music CarPlay style.
 */
class PlaylistDetailCarScreen(
    carContext: CarContext,
    private val playlist: Playlist,
    private val carPlayer: CarPlayer,
    private val coverCache: CarCoverCache,
    private val allSongs: List<Song>
) : Screen(carContext) {

    private val res get() = LocaleManager.wrapContext(carContext)

    private val songRepository = SongRepository(NeuroKaraokeApi())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val invalidateRunnable = Runnable { invalidate() }
    private fun invalidateOnMain() {
        mainHandler.removeCallbacks(invalidateRunnable)
        mainHandler.postDelayed(invalidateRunnable, 100)
    }

    private var songs: List<Song> = playlist.songs
    private var loaded = playlist.songs.isNotEmpty()
    private var loadJob: Job? = null

    init {
        if (!loaded) {
            loadJob = scope.launch {
                // If it's a personal playlist and empty, try repository load first
                if (playlist.id.contains("-") || playlist.id.startsWith("user_") || playlist.songs.isEmpty()) {
                    val userRepo = UserPlaylistRepository(carContext)
                    userRepo.loadPlaylistSongs(playlist.id)
                    val updated = userRepo.getPlaylist(playlist.id)
                    if (updated != null && updated.songs.isNotEmpty()) {
                        this@PlaylistDetailCarScreen.songs = updated.songs
                        loaded = true
                    }
                }

                if (!loaded) {
                    val fetched = songRepository.getPlaylistSongs(playlist.id).getOrNull().orEmpty()
                    this@PlaylistDetailCarScreen.songs = fetched.ifEmpty {
                        // Fallback: filter all-songs by playlist preview cover URLs (best-effort)
                        allSongs
                    }
                    loaded = true
                }

                coverCache.prefetch(songs.take(40).map { it.coverUrl }) {
                    invalidateOnMain()
                }
                withContext(Dispatchers.Main) { invalidate() }
            }
        }
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                loadJob?.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        if (!loaded) {
            return ListTemplate.Builder()
                .setTitle(playlist.title)
                .setLoading(true)
                .setHeaderAction(Action.BACK)
                .build()
        }

        val limit = listLimit()
        // Capping at limit - 1 to account for the "Shuffle Play" row
        val displayed = songs.take(limit - 1)
        val items = ItemList.Builder()

        items.addItem(
            Row.Builder()
                .setTitle("Shuffle Play")
                .setImage(
                    CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_browse)).build(),
                    Row.IMAGE_TYPE_SMALL
                )
                .setOnClickListener {
                    val shuffled = displayed.shuffled()
                    carPlayer.playSongs(shuffled, 0, playlist.title)
                    screenManager.push(NowPlayingCarScreen(carContext, carPlayer))
                }
                .build()
        )

        displayed.forEachIndexed { idx, song ->
            items.addItem(trackRow(song, idx, displayed))
        }

        val playAction = Action.Builder()
            .setTitle(res.getString(R.string.car_playlist_button_play))
            .setIcon(
                CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_song))
                    .build()
            )
            .setOnClickListener { 
                carPlayer.playSongs(displayed, 0, playlist.title) 
                screenManager.push(NowPlayingCarScreen(carContext, carPlayer))
            }
            .build()

        return ListTemplate.Builder()
            .setTitle(playlist.title)
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(playAction)
                    .build()
            )
            .setSingleList(items.build())
            .build()
    }

    private fun trackRow(song: Song, index: Int, list: List<Song>): Row {
        val builder = Row.Builder()
            .setTitle("${index + 1}. ${song.title}")
            .addText("${song.artist} • ${song.coverArtist}")
            .setOnClickListener { 
                carPlayer.playSongs(list, index, playlist.title) 
                screenManager.push(NowPlayingCarScreen(carContext, carPlayer))
            }

        val bmp = coverCache.get(song.coverUrl)
        val icon = if (bmp != null) {
            CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
        } else {
            CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_car_song)
            ).build()
        }
        builder.setImage(icon, Row.IMAGE_TYPE_SMALL)
        return builder.build()
    }

    private fun listLimit(): Int = try {
        carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
            .coerceAtMost(30)
    } catch (_: Throwable) {
        30
    }
}
