package com.soul.neurokaraoke.ui.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape

object TvQueryEditor {
    fun append(q: String, c: Char): String = q + c
    fun backspace(q: String): String = if (q.isEmpty()) q else q.dropLast(1)
    fun space(q: String): String = "$q "
}

private val ROWS = listOf("abcdef", "ghijkl", "mnopqr", "stuvwx", "yz")

@Composable
fun TvKeyboard(query: String, onQueryChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ch -> KeyCap(ch.toString()) { onQueryChange(TvQueryEditor.append(query, ch)) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyCap("SPACE") { onQueryChange(TvQueryEditor.space(query)) }
            KeyCap("DEL") { onQueryChange(TvQueryEditor.backspace(query)) }
        }
    }
}

@Composable
private fun KeyCap(label: String, onActivate: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(width = if (label.length > 1) 96.dp else 56.dp, height = 56.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyUp &&
                    (it.key == Key.Enter || it.key == Key.DirectionCenter)) { onActivate(); true } else false
            }
            .tvFocusScale(focused, scale = 1.15f)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label) }
    }
}
