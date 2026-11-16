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

object ShimejiPhysics {
    const val TICK_MS = 40L
    const val GRAVITY = 2.0
    const val DRAG_X = 0.05
    const val DRAG_Y = 0.1
    const val MAX_VEL_Y = 20.0

    data class Vel(val x: Double, val y: Double)

    /** One falling integration step: apply drag then gravity, clamp vy to terminal. */
    fun fallStep(v: Vel): Vel {
        val vx = v.x * (1 - DRAG_X)
        val vy = minOf(v.y * (1 - DRAG_Y) + GRAVITY, MAX_VEL_Y)
        return Vel(vx, vy)
    }
}
