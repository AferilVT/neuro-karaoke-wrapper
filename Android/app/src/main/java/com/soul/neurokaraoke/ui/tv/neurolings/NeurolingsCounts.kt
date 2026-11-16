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

object NeurolingsCounts {
    const val MIN = 0
    const val MAX = 10
    val CHARACTERS = listOf("Neuron", "Weuron", "Eviling", "Vedaling", "Cerber", "Tuteling")

    fun clamp(n: Int): Int = n.coerceIn(MIN, MAX)

    fun step(current: Int, delta: Int): Int = clamp(current + delta)

    fun serialize(counts: Map<String, Int>): String {
        return counts
            .filter { (_, count) -> count > 0 }
            .map { (name, count) -> "$name=$count" }
            .joinToString(";")
    }

    fun parse(s: String?): Map<String, Int> {
        if (s.isNullOrEmpty()) return emptyMap()
        return s.split(";")
            .mapNotNull { entry ->
                val parts = entry.split("=")
                if (parts.size == 2) {
                    val name = parts[0]
                    val countStr = parts[1]
                    val count = countStr.toIntOrNull()
                    if (name in CHARACTERS && count != null) {
                        val clamped = clamp(count)
                        if (clamped > 0) {
                            name to clamped
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            .toMap()
    }

    fun total(counts: Map<String, Int>): Int = counts.values.sum()
}
