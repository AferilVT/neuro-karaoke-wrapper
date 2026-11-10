package com.soul.neurokaraoke.ui.components

/**
 * Stable, guaranteed-unique LazyColumn keys from item ids.
 *
 * Compose throws "Key ... was already used" when two items share a key, which crashes the
 * screen as soon as both are composed. Song/playlist lists can legitimately repeat an id
 * (a playlist holding the same song twice, a server response that repeats an entry), so the
 * first occurrence keeps its plain id and later ones get "#2", "#3", ….
 */
fun <T> uniqueLazyKeys(items: List<T>, id: (T) -> String): List<String> {
    val seen = HashMap<String, Int>()
    return items.map { item ->
        val base = id(item)
        val n = (seen[base] ?: 0) + 1
        seen[base] = n
        if (n == 1) base else "$base#$n"
    }
}
