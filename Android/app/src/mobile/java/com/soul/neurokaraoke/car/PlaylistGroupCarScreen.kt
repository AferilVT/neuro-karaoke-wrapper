package com.soul.neurokaraoke.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.soul.neurokaraoke.R
import com.soul.neurokaraoke.data.model.Playlist
import com.soul.neurokaraoke.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PlaylistGroupCarScreen(
    carContext: CarContext,
    private val title: String,
    private val playlists: List<Playlist>,
    private val carPlayer: CarPlayer,
    private val coverCache: CarCoverCache,
    private val allSongs: List<Song>
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val invalidateRunnable = Runnable { invalidate() }

    private fun invalidateOnMain() {
        mainHandler.removeCallbacks(invalidateRunnable)
        mainHandler.postDelayed(invalidateRunnable, 100)
    }

    init {
        scope.launch {
            val covers = playlists.flatMap { it.previewCovers.take(1) + listOf(it.coverUrl) }
            coverCache.prefetch(covers) {
                invalidateOnMain()
            }
        }
    }

    override fun onGetTemplate(): Template {
        if (playlists.isEmpty()) {
            return GridTemplate.Builder()
                .setTitle(title)
                .setSingleList(ItemList.Builder().setNoItemsMessage("No playlists found").build())
                .setHeaderAction(Action.BACK)
                .build()
        }

        val listBuilder = ItemList.Builder()
        playlists.forEach { pl ->
            val gridItem = GridItem.Builder()
                .setTitle(pl.title.ifBlank { "Untitled Playlist" })
                .setText("${pl.songCount} songs")
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
            val bmp = coverCache.get(coverUrl)
            val icon = if (bmp != null) {
                CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
            } else {
                CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_browse)).build()
            }
            gridItem.setImage(icon, GridItem.IMAGE_TYPE_LARGE)
            
            listBuilder.addItem(gridItem.build())
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_radio)).build())
                    .setOnClickListener {
                        screenManager.push(NowPlayingCarScreen(carContext, carPlayer))
                    }
                    .build()
            )
            .build()

        return GridTemplate.Builder()
            .setTitle(title)
            .setSingleList(listBuilder.build())
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .build()
    }
}
