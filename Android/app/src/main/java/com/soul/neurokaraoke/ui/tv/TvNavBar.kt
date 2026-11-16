package com.soul.neurokaraoke.ui.tv

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class TvTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    SEARCH("Search", Icons.Default.Search),
    LIBRARY("Library", Icons.Default.LibraryMusic),
    RADIO("Radio", Icons.Default.Radio),
    VIDEOS("Videos", Icons.Default.Movie),
    ACCOUNT("Account", Icons.Default.AccountCircle),
    NOW_PLAYING("Now Playing", Icons.Default.PlayArrow)
}

/**
 * Floating nav pill. Unselected tabs collapse to icon-only; the selected tab expands to
 * icon + label (animated), keeping the bar compact for seven destinations on a 10-foot
 * screen. Selection follows focus (D-pad), matching the rest of the TV UI. Wrap-content
 * width so callers can place a settings button beside it with real spacing.
 */
@Composable
fun TvNavBar(
    selected: TvTab,
    onSelect: (TvTab) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    // Nothing grabs D-pad focus on its own at launch, so seed it onto the selected tab
    // once (keyed on Unit, not selected, so it never fights later tab switches).
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TvTab.entries.forEach { tab ->
            NavTab(
                tab = tab,
                selected = tab == selected,
                onSelect = onSelect,
                modifier = if (tab == selected) Modifier.focusRequester(focusRequester) else Modifier
            )
        }
    }
}

@Composable
private fun NavTab(
    tab: TvTab,
    selected: Boolean,
    onSelect: (TvTab) -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val active = selected || focused
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (active) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onSelect(tab) }
            .focusable()
            .animateContentSize()
            .padding(horizontal = if (active) 16.dp else 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = if (active) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        // Only the active tab shows its label — collapses the rest to icons.
        if (active) {
            Spacer(Modifier.width(8.dp))
            Text(
                tab.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
