package com.soul.neurokaraoke.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.soul.neurokaraoke.R
import com.soul.neurokaraoke.data.PlaylistCatalog
import com.soul.neurokaraoke.data.SongCache
import com.soul.neurokaraoke.data.api.NeuroKaraokeApi
import com.soul.neurokaraoke.data.model.Playlist
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.model.Singer
import com.soul.neurokaraoke.data.repository.FavoritesRepository
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
 * AA home screen: Library / Playlists / Radio tabs.
 * Apple Music style — recently added grid w/ real cover art.
 */
class HomeCarScreen(carContext: CarContext) : Screen(carContext) {

    private val res get() = LocaleManager.wrapContext(carContext)

    val carPlayer = CarPlayer(carContext)
    val coverCache = CarCoverCache(carContext)

    private val cache = SongCache(carContext)
    private val catalog = PlaylistCatalog(carContext)
    private val userRepo by lazy { UserPlaylistRepository(carContext) }
    private val favoritesRepo by lazy { FavoritesRepository(carContext) }
    private val karaokeApi = NeuroKaraokeApi()
    private val songRepository = SongRepository(karaokeApi)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var allSongs: List<Song> = emptyList()
    private var setlists: List<Playlist> = emptyList()
    private var loadJob: Job? = null
    private var initialLoaded = false
    private var activeTab: String = TAB_RADIO
    private var librarySingerFilter: Singer? = null

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    fun invalidateOnMain() = mainHandler.post { invalidate() }

    init {
        carPlayer.ensureConnected()
        loadJob = scope.launch {
            allSongs = cache.getCachedSongs()
            if (allSongs.isEmpty()) {
                // Fresh AAOS install — fetch from API + cache
                val fetched = songRepository.getAllSongs().getOrNull().orEmpty()
                if (fetched.isNotEmpty()) {
                    cache.cacheSongs(fetched, 0)
                    allSongs = fetched
                }
            }
            setlists = catalog.getPlaylists()
            
            initialLoaded = true
            // Prefetch first batch of covers
            coverCache.prefetch(
                allSongs.take(40).map { it.coverUrl } +
                    setlists.take(20).flatMap { it.previewCovers.take(1) + listOf(it.coverUrl) }
            ) { invalidateOnMain() }
            withContext(Dispatchers.Main) { invalidate() }
        }
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                carPlayer.release()
                loadJob?.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val tabSupported = try { carContext.carAppApiLevel >= 6 } catch (_: Throwable) { false }
        return if (tabSupported) tabTemplate() else libraryContent()
    }

    private fun tabTemplate(): Template {
        val builder = TabTemplate.Builder(object : TabTemplate.TabCallback {
            override fun onTabSelected(tabContentId: String) {
                activeTab = tabContentId
                invalidate()
            }
        })
        builder.setHeaderAction(Action.APP_ICON)
        builder.setActiveTabContentId(activeTab)

        builder.addTab(tab(TAB_RADIO, res.getString(R.string.car_tab_radio), R.drawable.ic_car_radio))
        builder.addTab(tab(TAB_LIBRARY, res.getString(R.string.car_tab_library), R.drawable.ic_car_library))
        builder.addTab(tab(TAB_PLAYLISTS, res.getString(R.string.car_tab_playlists), R.drawable.ic_car_browse))
        builder.addTab(tab(TAB_MORE, res.getString(R.string.car_tab_more), R.drawable.ic_car_persona))

        val content = when (activeTab) {
            TAB_PLAYLISTS -> playlistsContent()
            TAB_RADIO -> radioContent()
            TAB_MORE -> moreContent()
            else -> libraryContent()
        }
        builder.setTabContents(TabContents.Builder(content).build())
        return builder.build()
    }

    private fun tab(id: String, title: String, iconRes: Int): Tab =
        Tab.Builder()
            .setContentId(id)
            .setTitle(title)
            .setIcon(
                CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes))
                    .setTint(CarColor.DEFAULT)
                    .build()
            )
            .build()

    // ---- Library: grid of recently-added songs --------------------------------

    private fun libraryContent(): Template {
        if (!initialLoaded) {
            return GridTemplate.Builder()
                .setTitle(res.getString(R.string.car_title_library))
                .setLoading(true)
                .setHeaderAction(Action.APP_ICON)
                .build()
        }
        if (allSongs.isEmpty()) {
            return GridTemplate.Builder()
                .setTitle(res.getString(R.string.car_title_library))
                .setSingleList(
                    ItemList.Builder().setNoItemsMessage(res.getString(R.string.car_empty_no_songs)).build()
                )
                .setHeaderAction(Action.APP_ICON)
                .build()
        }
        val limit = gridLimit()
        val items = ItemList.Builder()
        
        val filteredSongs = if (librarySingerFilter == null) {
            allSongs
        } else {
            allSongs.filter { it.singer == librarySingerFilter }
        }

        filteredSongs.take(limit).forEachIndexed { idx, song ->
            items.addItem(songTile(song) { carPlayer.playSongs(filteredSongs, idx) })
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(getSingerLabel(librarySingerFilter))
                    .setOnClickListener { showFilterPicker() }
                    .build()
            )
            .build()

        return GridTemplate.Builder()
            .setTitle(res.getString(R.string.car_title_library))
            .setSingleList(items.build())
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStrip)
            .build()
    }

    private fun getSingerLabel(singer: Singer?): String {
        return when (singer) {
            Singer.NEURO -> "Neuro"
            Singer.EVIL -> "Evil"
            Singer.DUET -> "Duets"
            else -> "All"
        }
    }

    private fun showFilterPicker() {
        screenManager.push(FilterPickerScreen(carContext) { selectedSinger ->
            librarySingerFilter = selectedSinger
            invalidate()
        })
    }

    private inner class FilterPickerScreen(
        carContext: CarContext,
        private val onSelected: (Singer?) -> Unit
    ) : Screen(carContext) {
        override fun onGetTemplate(): Template {
            val listBuilder = ItemList.Builder()
            
            listBuilder.addItem(Row.Builder().setTitle("All Singers").setOnClickListener { onSelected(null); screenManager.pop() }.build())
            listBuilder.addItem(Row.Builder().setTitle("Neuro Only").setOnClickListener { onSelected(Singer.NEURO); screenManager.pop() }.build())
            listBuilder.addItem(Row.Builder().setTitle("Evil Only").setOnClickListener { onSelected(Singer.EVIL); screenManager.pop() }.build())
            listBuilder.addItem(Row.Builder().setTitle("Duets Only").setOnClickListener { onSelected(Singer.DUET); screenManager.pop() }.build())

            return ListTemplate.Builder()
                .setSingleList(listBuilder.build())
                .setTitle("Filter by Singer")
                .setHeaderAction(Action.BACK)
                .build()
        }
    }

    // ---- Playlists tab --------------------------------------------------------

    private fun playlistsContent(): Template {
        val combined = (userRepo.playlists.value + setlists).filter { it.title.isNotBlank() }
        if (!initialLoaded) {
            return GridTemplate.Builder()
                .setTitle(res.getString(R.string.car_title_playlists))
                .setLoading(true)
                .setHeaderAction(Action.APP_ICON)
                .build()
        }
        
        val limit = gridLimit()
        val items = ItemList.Builder()

        // Add Favorites at the top if not empty
        val favorites = favoritesRepo.favorites.value
        if (favorites.isNotEmpty()) {
            val favTitle = res.getString(R.string.aaos_label_favorites)
            val favItem = GridItem.Builder()
                .setTitle(favTitle)
                .setText(res.getString(R.string.common_label_songs_format, favorites.size))
                .setOnClickListener { navigateToFavorites() }
            
            val coverUrl = favorites.firstOrNull()?.coverUrl ?: ""
            attachImage(favItem, coverUrl)
            items.addItem(favItem.build())
        }

        combined.take(limit).forEach { pl ->
            items.addItem(playlistTile(pl))
        }

        return GridTemplate.Builder()
            .setTitle(res.getString(R.string.car_title_playlists))
            .setSingleList(items.build())
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    // ---- Radio tab ------------------------------------------------------------

    private fun radioContent(): Template {
        val radioIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_car_radio)
        ).build()

        val infoRow = Row.Builder()
            .setTitle(res.getString(R.string.car_radio_station_title))
            .addText(res.getString(R.string.car_radio_subtitle))
            .setImage(radioIcon, Row.IMAGE_TYPE_LARGE)
            .build()

        val playAction = Action.Builder()
            .setTitle(res.getString(R.string.car_radio_button_listen))
            .setBackgroundColor(CarColor.PRIMARY)
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, android.R.drawable.ic_media_play)
                ).build()
            )
            .setOnClickListener { carPlayer.playRadio() }
            .build()

        val pane = Pane.Builder()
            .addRow(infoRow)
            .addAction(playAction)
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle(res.getString(R.string.car_title_radio))
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    // ---- More tab -------------------------------------------------------------

    private fun moreContent(): Template {
        val items = ItemList.Builder()

        items.addItem(
            Row.Builder()
                .setTitle(res.getString(R.string.aaos_label_favorites))
                .setImage(
                    CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_library)).build(),
                    Row.IMAGE_TYPE_ICON
                )
                .setOnClickListener { navigateToFavorites() }
                .build()
        )

        items.addItem(
            Row.Builder()
                .setTitle("My Playlists")
                .setImage(
                    CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_library)).build(),
                    Row.IMAGE_TYPE_ICON
                )
                .setOnClickListener { navigateToPlaylistGroup("My Playlists", userRepo.playlists.value) }
                .build()
        )

        items.addItem(
            Row.Builder()
                .setTitle(res.getString(R.string.car_radio_button_listen))
                .setImage(
                    CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_radio)).build(),
                    Row.IMAGE_TYPE_ICON
                )
                .setOnClickListener { carPlayer.playRadio() }
                .build()
        )

        items.addItem(
            Row.Builder()
                .setTitle("Official Setlists")
                .setImage(
                    CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_browse)).build(),
                    Row.IMAGE_TYPE_ICON
                )
                .setOnClickListener { navigateToPlaylistGroup("Official Setlists", setlists) }
                .build()
        )

        items.addItem(
            Row.Builder()
                .setTitle("Neuro Sings")
                .setImage(
                    CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_persona)).build(),
                    Row.IMAGE_TYPE_ICON
                )
                .setOnClickListener { navigateToSingerSongs("Neuro Sings", Singer.NEURO) }
                .build()
        )

        items.addItem(
            Row.Builder()
                .setTitle("Evil Sings")
                .setImage(
                    CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_persona)).build(),
                    Row.IMAGE_TYPE_ICON
                )
                .setOnClickListener { navigateToSingerSongs("Evil Sings", Singer.EVIL) }
                .build()
        )

        items.addItem(
            Row.Builder()
                .setTitle("Duets")
                .setImage(
                    CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_duet)).build(),
                    Row.IMAGE_TYPE_ICON
                )
                .setOnClickListener { navigateToSingerSongs("Duets", Singer.DUET) }
                .build()
        )

        items.addItem(
            Row.Builder()
                .setTitle(res.getString(R.string.car_item_public_playlists))
                .setImage(
                    CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_browse)).build(),
                    Row.IMAGE_TYPE_ICON
                )
                .setOnClickListener {
                    screenManager.push(
                        PublicPlaylistsCarScreen(
                            carContext,
                            carPlayer = carPlayer,
                            coverCache = coverCache,
                            allSongs = allSongs
                        )
                    )
                }
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(items.build())
            .setTitle(res.getString(R.string.car_title_more))
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun navigateToFavorites() {
        val favorites = favoritesRepo.favorites.value
        val playlist = Playlist(
            id = "favorites",
            title = res.getString(R.string.aaos_label_favorites),
            songs = favorites,
            songCount = favorites.size,
            isPublic = false,
            previewCovers = favorites.take(4).map { it.coverUrl }
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

    private fun navigateToSingerSongs(title: String, singer: Singer) {
        val filtered = allSongs.filter { it.singer == singer }
        val playlist = Playlist(
            id = "singer_${singer.name}",
            title = title,
            songs = filtered,
            songCount = filtered.size,
            isPublic = false
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

    private fun navigateToPlaylistGroup(title: String, playlists: List<Playlist>) {
        screenManager.push(PlaylistGroupCarScreen(carContext, title, playlists, carPlayer, coverCache, allSongs))
    }

    // ---- Tile builders --------------------------------------------------------

    private fun songTile(song: Song, onClick: () -> Unit): GridItem {
        val title = song.title.ifBlank { res.getString(R.string.car_label_untitled) }.take(30)
        val builder = GridItem.Builder()
            .setTitle(title)
            .setText(song.artist.ifBlank { res.getString(R.string.car_label_unknown_artist) }.take(30))
            .setOnClickListener(onClick)
        attachImage(builder, song.coverUrl)
        return builder.build()
    }

    private fun playlistTile(pl: Playlist): GridItem {
        val title = pl.title.ifBlank { res.getString(R.string.car_label_untitled_playlist) }.take(30)
        val builder = GridItem.Builder()
            .setTitle(title)
            .setText(if (pl.songCount > 0) res.getString(R.string.common_label_songs_format, pl.songCount) else res.getString(R.string.car_label_playlist))
            .setOnClickListener {
                screenManager.push(
                    PlaylistDetailCarScreen(
                        carContext,
                        playlist = pl,
                        carPlayer = carPlayer,
                        coverCache = coverCache,
                        allSongs = allSongs
                    )
                )
            }
        val coverUrl = pl.coverUrl.ifBlank { pl.previewCovers.firstOrNull() ?: "" }
        attachImage(builder, coverUrl)
        return builder.build()
    }

    private fun attachImage(builder: GridItem.Builder, url: String) {
        val bmp = coverCache.get(url)
        val icon = if (bmp != null) {
            CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
        } else {
            CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_car_song)
            ).build()
        }
        val type = if (bmp != null) GridItem.IMAGE_TYPE_LARGE else GridItem.IMAGE_TYPE_ICON
        builder.setImage(icon, type)
    }

    private fun gridLimit(): Int = try {
        carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID)
    } catch (_: Throwable) {
        24
    }

    companion object {
        const val TAB_LIBRARY = "library"
        const val TAB_PLAYLISTS = "playlists"
        const val TAB_RADIO = "radio"
        const val TAB_MORE = "more"
    }
}
