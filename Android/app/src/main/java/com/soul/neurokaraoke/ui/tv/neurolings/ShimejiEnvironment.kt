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

data class Anchor(val x: Double, val y: Double)

/** Screen/work-area rect. Borders: floor=bottom, ceiling=top, walls=left|right. */
class ShimejiEnvironment(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width get() = right - left
    val height get() = bottom - top

    fun onFloor(a: Anchor, eps: Double = 1.0): Boolean = a.y >= bottom - eps

    fun onCeiling(a: Anchor, eps: Double = 1.0): Boolean = a.y <= top + eps

    fun onLeftWall(a: Anchor, eps: Double = 1.0): Boolean = a.x <= left + eps

    fun onRightWall(a: Anchor, eps: Double = 1.0): Boolean = a.x >= right - eps

    fun onWall(a: Anchor, eps: Double = 1.0): Boolean = onLeftWall(a, eps) || onRightWall(a, eps)
}
