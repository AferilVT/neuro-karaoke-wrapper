package com.soul.neurokaraoke.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.soul.neurokaraoke.R
import com.soul.neurokaraoke.data.model.Playlist
import com.soul.neurokaraoke.data.model.Song

class PlaylistGroupCarScreen(
    carContext: CarContext,
    private val title: String,
    private val playlists: List<Playlist>,
    private val carPlayer: CarPlayer,
    private val coverCache: CarCoverCache,
    private val allSongs: List<Song>
) : Screen(carContext) {

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
            if (bmp != null) {
                gridItem.setImage(CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build(), GridItem.IMAGE_TYPE_LARGE)
            } else {
                gridItem.setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_browse)).build(), GridItem.IMAGE_TYPE_ICON)
            }
            
            listBuilder.addItem(gridItem.build())
        }

        return GridTemplate.Builder()
            .setTitle(title)
            .setSingleList(listBuilder.build())
            .setHeaderAction(Action.BACK)
            .build()
    }
}
