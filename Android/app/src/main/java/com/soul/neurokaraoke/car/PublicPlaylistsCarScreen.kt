package com.soul.neurokaraoke.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.soul.neurokaraoke.R
import com.soul.neurokaraoke.data.api.NeuroKaraokeApi
import com.soul.neurokaraoke.data.api.ApiPublicPlaylist
import com.soul.neurokaraoke.data.model.Playlist
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.model.Singer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PublicPlaylistsCarScreen(
    carContext: CarContext,
    private val carPlayer: CarPlayer,
    private val coverCache: CarCoverCache,
    private val allSongs: List<Song>
) : Screen(carContext) {

    private val karaokeApi = NeuroKaraokeApi()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var playlists: List<ApiPublicPlaylist> = emptyList()
    private var loaded = false
    private var loadJob: Job? = null
    
    private var currentSort = SortOrder.NEWEST

    enum class SortOrder(val labelRes: Int) {
        NEWEST(R.string.explore_sort_newest),
        OLDEST(R.string.explore_sort_oldest),
        PLAYS_HIGH(R.string.explore_sort_plays_high),
        PLAYS_LOW(R.string.explore_sort_plays_low)
    }

    init {
        loadJob = scope.launch {
            val result = karaokeApi.fetchPublicPlaylists()
            playlists = result.getOrNull().orEmpty()
            loaded = true
            
            // Prefetch covers
            val covers = playlists.take(20).mapNotNull { it.coverUrl }
            coverCache.prefetch(covers) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { invalidate() }
            }
            
            withContext(Dispatchers.Main) { invalidate() }
        }
        
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                loadJob?.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        if (!loaded) {
            return GridTemplate.Builder()
                .setTitle("Public Playlists")
                .setLoading(true)
                .setHeaderAction(Action.BACK)
                .build()
        }

        if (playlists.isEmpty()) {
            return GridTemplate.Builder()
                .setTitle("Public Playlists")
                .setSingleList(ItemList.Builder().setNoItemsMessage("No public playlists found").build())
                .setHeaderAction(Action.BACK)
                .build()
        }

        val sortedPlaylists = when (currentSort) {
            SortOrder.NEWEST -> playlists.sortedByDescending { it.updatedAt }
            SortOrder.OLDEST -> playlists.sortedBy { it.updatedAt }
            SortOrder.PLAYS_HIGH -> playlists.sortedByDescending { it.playCount }
            SortOrder.PLAYS_LOW -> playlists.sortedBy { it.playCount }
        }

        val items = ItemList.Builder()
        sortedPlaylists.forEach { apiPlaylist ->
            items.addItem(
                GridItem.Builder()
                    .setTitle(apiPlaylist.name)
                    .setText("${apiPlaylist.songCount} songs")
                    .setOnClickListener {
                        navigateToPlaylist(apiPlaylist)
                    }
                    .setImage(
                        getIcon(apiPlaylist.coverUrl),
                        if (apiPlaylist.coverUrl != null) GridItem.IMAGE_TYPE_LARGE else GridItem.IMAGE_TYPE_ICON
                    )
                    .build()
            )
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(currentSort.labelRes))
                    .setOnClickListener {
                        showSortPicker()
                    }
                    .build()
            )
            .build()

        return GridTemplate.Builder()
            .setTitle("Public Playlists")
            .setSingleList(items.build())
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .build()
    }

    private fun showSortPicker() {
        screenManager.push(SortPickerScreen(carContext) { selectedSort ->
            currentSort = selectedSort
            invalidate()
        })
    }

    private inner class SortPickerScreen(
        carContext: CarContext,
        private val onSelected: (SortOrder) -> Unit
    ) : Screen(carContext) {
        override fun onGetTemplate(): Template {
            val listBuilder = ItemList.Builder()
            SortOrder.entries.forEach { sort ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(sort.labelRes))
                        .setOnClickListener {
                            onSelected(sort)
                            screenManager.pop()
                        }
                        .build()
                )
            }

            return ListTemplate.Builder()
                .setSingleList(listBuilder.build())
                .setTitle("Select Sort Order")
                .setHeaderAction(Action.BACK)
                .build()
        }
    }

    private fun getIcon(url: String?): CarIcon {
        val bmp = url?.let { coverCache.get(it) }
        return if (bmp != null) {
            CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
        } else {
            CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_browse)).build()
        }
    }

    private fun navigateToPlaylist(apiPlaylist: ApiPublicPlaylist) {
        // Convert ApiPublicPlaylist to Playlist model
        val playlist = Playlist(
            id = apiPlaylist.id,
            title = apiPlaylist.name,
            coverUrl = apiPlaylist.coverUrl ?: "",
            previewCovers = apiPlaylist.mosaicCovers,
            songs = emptyList(), // Will be loaded in detail screen
            songCount = apiPlaylist.songCount,
            isPublic = true
        )
        
        screenManager.push(
            PlaylistDetailCarScreen(
                carContext,
                playlist = playlist,
                carPlayer = carPlayer,
                coverCache = coverCache,
                allSongs = allSongs
            )
        )
    }
}
