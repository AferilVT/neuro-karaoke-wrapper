/*
 * Copyright (C) 2026 Aferil
 *
 * This file is part of Neuro Karaoke.
 *
 * Neuro Karaoke is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3.
 *
 * Neuro Karaoke is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Neuro Karaoke. If not, see <https://www.gnu.org/licenses/>.
 *
 * This file is part of the Neurolings feature, ported from Shimeji-ee (maintained
 * by Kilkakon; original Shimeji by Yuki Yamada / Group Finity), which is under a
 * permissive BSD/zlib-style license. See THIRD_PARTY_NOTICES.md.
 */

package com.soul.neurokaraoke.ui.tv.neurolings

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Android-only glue: loads a Shimeji mascot pack from `assets/mascots/<name>/` and decodes/
 * caches its frame bitmaps.
 */
class MascotAssets(private val assets: AssetManager) {

    private val frameCache = HashMap<String, ImageBitmap>()

    /** Load & parse a pack from assets/mascots/<name>/. */
    fun loadSet(name: String): MascotSet {
        val actionsStream = assets.open("mascots/$name/actions.xml")
        val behaviorsStream = assets.open("mascots/$name/behaviors.xml")
        return actionsStream.use { actions ->
            behaviorsStream.use { behaviors ->
                ShimejiXmlLoader.parse(name, "mascots/$name/img", actions, behaviors)
            }
        }
    }

    /** Decode a frame PNG (cached) as an ImageBitmap. image is like "/shime1.png". */
    fun frame(setName: String, image: String): ImageBitmap {
        val key = "$setName$image"
        frameCache[key]?.let { return it }

        val fileName = image.removePrefix("/")
        val path = "mascots/$setName/img/$fileName"
        val bitmap = assets.open(path).use { stream ->
            BitmapFactory.decodeStream(stream)
        }
        val imageBitmap = bitmap.asImageBitmap()
        frameCache[key] = imageBitmap
        return imageBitmap
    }
}
