package com.soul.neurokaraoke.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.soul.neurokaraoke.BuildConfig
import com.soul.neurokaraoke.data.repository.LocaleManager
import com.soul.neurokaraoke.data.repository.SettingsRepository
import com.soul.neurokaraoke.ui.tv.neurolings.NeurolingsCounts

/**
 * TV settings panel — rendered as a full-screen overlay by [TvApp]. Binds directly to the shared
 * [SettingsRepository] / [LocaleManager] singletons so changes stay in sync with the phone UI.
 * Back closes it (via [onClose]).
 */
@Composable
fun TvSettingsScreen(onClose: () -> Unit) {
    BackHandler { onClose() }

    val crossfade by SettingsRepository.crossfadeDuration.collectAsState()
    val gapless by SettingsRepository.gaplessPlayback.collectAsState()
    val autoPlay by SettingsRepository.autoPlay.collectAsState()
    val normalize by SettingsRepository.normalizeVolume.collectAsState()
    val currentLanguage by LocaleManager.currentLanguage.collectAsState()
    val devUnlocked by SettingsRepository.devOptionsUnlocked.collectAsState()
    val neurolingsCounts by SettingsRepository.neurolingsCounts.collectAsState()

    val context = LocalContext.current
    // Tap timestamps for the "7 taps in 5s" version-row unlock (see TvDevUnlock).
    var versionTaps by remember { mutableStateOf(emptyList<Long>()) }

    // First interactive row grabs D-pad focus when the panel opens (nothing else in this
    // overlay would otherwise hold focus).
    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstRowFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().padding(horizontal = 80.dp, vertical = 48.dp)) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(24.dp))

            TvSettingsSection("Playback")
            TvCrossfadeRow(
                seconds = crossfade,
                onStep = { delta ->
                    SettingsRepository.setCrossfadeDuration(TvSettingsLogic.stepCrossfade(crossfade, delta))
                },
                focusRequester = firstRowFocus
            )
            TvSettingsToggleRow(
                title = "Gapless playback",
                subtitle = "No silence between tracks",
                checked = gapless
            ) { SettingsRepository.setGaplessPlayback(it) }
            TvSettingsToggleRow(
                title = "Autoplay",
                subtitle = "Keep playing when the queue ends",
                checked = autoPlay
            ) { SettingsRepository.setAutoPlay(it) }
            TvSettingsToggleRow(
                title = "Normalize volume",
                subtitle = "Even out loudness across tracks",
                checked = normalize
            ) { SettingsRepository.setNormalizeVolume(it) }

            Spacer(Modifier.height(24.dp))
            TvSettingsSection("Language")
            LocaleManager.SUPPORTED_LOCALES.forEach { locale ->
                TvSettingsSelectRow(
                    label = locale.nativeName,
                    selected = currentLanguage == locale.code
                ) { LocaleManager.setLanguage(locale.code) }
            }

            Spacer(Modifier.height(24.dp))
            TvSettingsSection("About")
            // The version row is the hidden dev-options unlock: 7 D-pad-center presses in 5s.
            TvClickableRow(
                title = "Neuro Karaoke",
                subtitle = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
            ) {
                if (devUnlocked) {
                    Toast.makeText(context, "Developer options are already enabled", Toast.LENGTH_SHORT).show()
                    return@TvClickableRow
                }
                val result = TvDevUnlock.registerTap(versionTaps, System.currentTimeMillis())
                versionTaps = result.taps
                when {
                    result.unlocked -> {
                        SettingsRepository.setDevOptionsUnlocked(true)
                        Toast.makeText(context, "You are now a developer!", Toast.LENGTH_SHORT).show()
                    }
                    result.remaining in 1..3 -> {
                        Toast.makeText(
                            context,
                            "You are now ${result.remaining} step(s) away from being a developer",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            // Focusable (no-op) so the disclaimer stays reachable by D-pad below the version row.
            var disclaimerFocused by remember { mutableStateOf(false) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .onFocusChanged { disclaimerFocused = it.isFocused }
                    .focusable()
                    .background(if (disclaimerFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            ) {
                TvAboutText(
                    title = "Unofficial fan app",
                    subtitle = "Not affiliated with neurokaraoke.com or its creators"
                )
            }

            if (devUnlocked) {
                Spacer(Modifier.height(24.dp))
                TvSettingsSection("Developer Options")
                TvAboutText(
                    title = "Neurolings",
                    subtitle = "Shimeji mascots by the Neuro-sama community — dev-only toy"
                )
                NeurolingsCounts.CHARACTERS.forEach { character ->
                    TvStepperRow(
                        label = character,
                        value = neurolingsCounts[character] ?: 0
                    ) { delta ->
                        SettingsRepository.setNeurolingsCount(
                            character,
                            NeurolingsCounts.step(neurolingsCounts[character] ?: 0, delta)
                        )
                    }
                }
                TvClickableRow(
                    title = "Lock developer options",
                    subtitle = "Hide this section (also switches Neurolings off)"
                ) {
                    SettingsRepository.setDevOptionsUnlocked(false)
                    versionTaps = emptyList()
                    Toast.makeText(context, "Developer options locked", Toast.LENGTH_SHORT).show()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A focusable, D-pad-activatable row (title + subtitle) that fires [onClick] on Enter/center/tap. */
@Composable
private fun TvClickableRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TvSettingsSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 12.dp)
    )
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        thickness = 0.5.dp
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun TvSettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onToggle(!checked) }
            .background(if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Display-only: the whole row is the focusable/clickable control.
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun TvSettingsSelectRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onSelect() }
            .background(if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TvCrossfadeRow(
    seconds: Int,
    onStep: (Int) -> Unit,
    focusRequester: FocusRequester
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp) {
                    when (e.key) {
                        Key.DirectionLeft -> { onStep(-1); true }
                        Key.DirectionRight -> { onStep(1); true }
                        else -> false
                    }
                } else false
            }
            .background(if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Crossfade", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Left / Right to adjust · fades between songs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            TvSettingsLogic.crossfadeLabel(seconds),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** A focusable D-pad stepper row (Left/Right adjust) showing `−  N  +` for a Neuroling character. */
@Composable
private fun TvStepperRow(label: String, value: Int, onStep: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp) {
                    when (e.key) {
                        Key.DirectionLeft -> { onStep(-1); true }
                        Key.DirectionRight -> { onStep(1); true }
                        else -> false
                    }
                } else false
            }
            .background(if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "−",
                style = MaterialTheme.typography.titleLarge,
                color = if (value > NeurolingsCounts.MIN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "$value",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Text(
                "+",
                style = MaterialTheme.typography.titleLarge,
                color = if (value < NeurolingsCounts.MAX) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TvAboutText(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
