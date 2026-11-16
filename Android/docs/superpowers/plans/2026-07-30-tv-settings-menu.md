# TV Settings Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a settings menu to the Android TV UI, opened from a gear icon in the top-right corner (separate from the centered nav pill), bound to the app's existing shared settings state.

**Architecture:** A new `TvSettingsScreen` composable renders as a full-screen overlay following the exact pattern already used by the playlist-detail overlay in `TvApp` (opaque panel over the blurred backdrop, focus gated behind it, Back to close). All controls read/write the existing `SettingsRepository` and `LocaleManager` singletons — no new persistence. The stepping/labelling logic for crossfade is extracted into a pure, unit-tested object (`TvSettingsLogic`), mirroring the `TvQueryEditor` pattern.

**Tech Stack:** Kotlin, Jetpack Compose (androidx.compose.material3), JUnit4 (JVM unit tests), Android TV D-pad focus (`focusable` / `onKeyEvent` / `clickable` / `tvFocusScale`).

## Global Constraints

- Build env (bash): `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`, `export ANDROID_HOME="/c/Users/Aferil/AppData/Local/Android/Sdk"`; run gradle from the `Android/` directory.
- Compile gate: `./gradlew :app:assembleDebug`. Unit-test gate: `./gradlew :app:testDebugUnitTest`.
- Bind only to the existing `SettingsRepository` and `LocaleManager` — do NOT add new SharedPreferences keys or a new persistence layer.
- No theme picker, no equalizer, no sign-out in TV settings (sign-out stays in the Account tab).
- TV D-pad idioms: `Modifier.clickable { }` makes a row focusable AND fires on Enter / DPad-center / tap; use it for toggle and selectable rows. Use `Modifier.focusable()` + `Modifier.onKeyEvent { }` (filter `KeyEventType.KeyUp` + `Key.DirectionLeft`/`Key.DirectionRight`) only for the crossfade row, which needs Left/Right. Show focus with `tvFocusScale` and/or an accent background highlight, matching existing TV screens.
- Package for `BuildConfig`: `com.soul.neurokaraoke.BuildConfig` (`VERSION_NAME`: String, `VERSION_CODE`: Int). `buildConfig = true` is already enabled.

---

### Task 1: Crossfade step/label pure logic (`TvSettingsLogic`)

**Files:**
- Create: `Android/app/src/main/java/com/soul/neurokaraoke/ui/tv/TvSettingsLogic.kt`
- Test: `Android/app/src/test/java/com/soul/neurokaraoke/TvSettingsLogicTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `object TvSettingsLogic`
  - `const val CROSSFADE_MIN: Int = 0`, `const val CROSSFADE_MAX: Int = 12`
  - `fun stepCrossfade(current: Int, delta: Int): Int` — returns `(current + delta)` clamped to `CROSSFADE_MIN..CROSSFADE_MAX`.
  - `fun crossfadeLabel(seconds: Int): String` — `"Off"` when `seconds <= 0`, else `"${seconds}s"`.

- [ ] **Step 1: Write the failing test**

Create `Android/app/src/test/java/com/soul/neurokaraoke/TvSettingsLogicTest.kt`:

```kotlin
package com.soul.neurokaraoke

import com.soul.neurokaraoke.ui.tv.TvSettingsLogic
import org.junit.Assert.assertEquals
import org.junit.Test

class TvSettingsLogicTest {
    @Test fun step_up_increments() = assertEquals(1, TvSettingsLogic.stepCrossfade(0, 1))
    @Test fun step_down_decrements() = assertEquals(4, TvSettingsLogic.stepCrossfade(5, -1))
    @Test fun step_down_clamps_at_min() = assertEquals(0, TvSettingsLogic.stepCrossfade(0, -1))
    @Test fun step_up_clamps_at_max() = assertEquals(12, TvSettingsLogic.stepCrossfade(12, 1))
    @Test fun label_zero_is_off() = assertEquals("Off", TvSettingsLogic.crossfadeLabel(0))
    @Test fun label_nonzero_has_suffix() = assertEquals("5s", TvSettingsLogic.crossfadeLabel(5))
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd Android && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew :app:testDebugUnitTest --tests "com.soul.neurokaraoke.TvSettingsLogicTest"
```
Expected: FAIL — unresolved reference `TvSettingsLogic` (compilation error).

- [ ] **Step 3: Write minimal implementation**

Create `Android/app/src/main/java/com/soul/neurokaraoke/ui/tv/TvSettingsLogic.kt`:

```kotlin
package com.soul.neurokaraoke.ui.tv

/**
 * Pure, unit-testable helpers for the TV settings crossfade row. Kept separate from the
 * composable (mirrors [TvQueryEditor]) so the clamping/formatting is covered by fast JVM tests.
 */
object TvSettingsLogic {
    const val CROSSFADE_MIN: Int = 0
    const val CROSSFADE_MAX: Int = 12

    /** Next crossfade value after a D-pad Left (delta -1) / Right (delta +1), clamped to bounds. */
    fun stepCrossfade(current: Int, delta: Int): Int =
        (current + delta).coerceIn(CROSSFADE_MIN, CROSSFADE_MAX)

    /** Right-hand value label for the crossfade row. */
    fun crossfadeLabel(seconds: Int): String =
        if (seconds <= 0) "Off" else "${seconds}s"
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd Android && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew :app:testDebugUnitTest --tests "com.soul.neurokaraoke.TvSettingsLogicTest"
```
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add Android/app/src/main/java/com/soul/neurokaraoke/ui/tv/TvSettingsLogic.kt Android/app/src/test/java/com/soul/neurokaraoke/TvSettingsLogicTest.kt
git commit -m "feat(tv): crossfade step/label logic for TV settings"
```

---

### Task 2: `TvSettingsScreen` composable (the panel)

**Files:**
- Create: `Android/app/src/main/java/com/soul/neurokaraoke/ui/tv/TvSettingsScreen.kt`

**Interfaces:**
- Consumes: `TvSettingsLogic.stepCrossfade`, `TvSettingsLogic.crossfadeLabel` (Task 1); `SettingsRepository` (`crossfadeDuration`/`setCrossfadeDuration`, `gaplessPlayback`/`setGaplessPlayback`, `autoPlay`/`setAutoPlay`, `normalizeVolume`/`setNormalizeVolume`); `LocaleManager` (`currentLanguage`, `SUPPORTED_LOCALES`, `setLanguage`); `BuildConfig`.
- Produces: `@Composable fun TvSettingsScreen(onClose: () -> Unit)` — consumed by Task 3.

Notes:
- No unit test — this is a composable; the gate is `assembleDebug` compiling plus the fresh-reviewer read. Interactive/focus behavior is verified end-to-end in Task 3 on the emulator.
- The screen does NOT draw its own opaque background; Task 3 wraps it in an opaque overlay `Box` (same as the detail overlay). Language selection persists to shared state (and syncs to the phone); the TV screens themselves are hardcoded English, so it does not relocalize the TV UI — this is expected.

- [ ] **Step 1: Create the file**

Create `Android/app/src/main/java/com/soul/neurokaraoke/ui/tv/TvSettingsScreen.kt`:

```kotlin
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soul.neurokaraoke.BuildConfig
import com.soul.neurokaraoke.data.repository.LocaleManager
import com.soul.neurokaraoke.data.repository.SettingsRepository

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
            TvAboutText(
                title = "Neuro Karaoke",
                subtitle = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
            )
            TvAboutText(
                title = "Unofficial fan app",
                subtitle = "Not affiliated with neurokaraoke.com or its creators"
            )
            Spacer(Modifier.height(24.dp))
        }
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

@Composable
private fun TvAboutText(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

```bash
cd Android && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && export ANDROID_HOME="/c/Users/Aferil/AppData/Local/Android/Sdk" && ./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add Android/app/src/main/java/com/soul/neurokaraoke/ui/tv/TvSettingsScreen.kt
git commit -m "feat(tv): settings panel composable (playback, language, about)"
```

---

### Task 3: Corner gear button + `TvApp` integration + focus gating

**Files:**
- Modify: `Android/app/src/main/java/com/soul/neurokaraoke/ui/tv/TvControls.kt` (add `TvSettingsButton`)
- Modify: `Android/app/src/main/java/com/soul/neurokaraoke/ui/tv/TvApp.kt`

**Interfaces:**
- Consumes: `TvSettingsScreen(onClose)` (Task 2); existing `tvFocusScale`; existing private `TvImmersiveBackground` (same file).
- Produces: `@Composable fun TvSettingsButton(onClick: () -> Unit, modifier: Modifier, focusRequester: FocusRequester?)`.

- [ ] **Step 1: Add `TvSettingsButton` to `TvControls.kt`**

Add these imports to the existing import block in `TvControls.kt`:

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Settings
```

Append this composable to `TvControls.kt` (after `TvFullscreenButton`):

```kotlin
/** Focusable gear button for the TV header's top-right corner — opens the settings panel. */
@Composable
fun TvSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(CircleShape)
            .background(
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .tvFocusScale(focused, scale = 1.1f)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
    }
}
```

- [ ] **Step 2: Wire settings state + gear + overlay into `TvApp.kt`**

In `TvApp.kt`, add this import to the layout imports:

```kotlin
import androidx.compose.foundation.layout.fillMaxWidth
```

Add settings state alongside the existing `fullscreen` state (just after the `BackHandler(enabled = fullscreen)` line at `TvApp.kt:39`):

```kotlin
    // Settings overlay (corner gear). Gated the same way as the detail overlay.
    var showSettings by remember { mutableStateOf(false) }
    val gearFocusRequester = remember { FocusRequester() }
    // Only re-grab focus onto the gear when the panel actually *closes* (not on first
    // composition, where a nav tab should hold focus instead).
    var settingsWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(showSettings) {
        if (showSettings) {
            settingsWasOpen = true
        } else if (settingsWasOpen) {
            settingsWasOpen = false
            runCatching { gearFocusRequester.requestFocus() }
        }
    }
```

Extend the main column's focus gate at `TvApp.kt:74` — change:

```kotlin
                .focusProperties { canFocus = selectedPlaylist == null }
```
to:
```kotlin
                .focusProperties { canFocus = selectedPlaylist == null && !showSettings }
```

Replace the header `TvNavBar(...)` block (currently `TvApp.kt:77-84`, inside `if (!fullscreen) { ... }`) with a header row that hosts the centered pill and the corner gear:

```kotlin
            if (!fullscreen) {
                Box(Modifier.fillMaxWidth().padding(24.dp)) {
                    TvNavBar(
                        selected = tab,
                        onSelect = { tab = it },
                        focusRequester = navFocusRequester
                    )
                    TvSettingsButton(
                        onClick = { showSettings = true },
                        focusRequester = gearFocusRequester,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
```

Add the settings overlay as the top-most layer — place it after the existing `selectedPlaylist?.let { ... }` block, still inside the root `Box` (just before the root `Box`'s closing brace at `TvApp.kt:132`):

```kotlin
        if (showSettings) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                TvImmersiveBackground(playerViewModel)
                TvSettingsScreen(onClose = { showSettings = false })
            }
        }
```

- [ ] **Step 3: Build to verify it compiles**

```bash
cd Android && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && export ANDROID_HOME="/c/Users/Aferil/AppData/Local/Android/Sdk" && ./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Install and verify on the TV emulator**

Start the TV emulator (AVD `neuro_tv`) if not running, then:

```bash
export ANDROID_HOME="/c/Users/Aferil/AppData/Local/Android/Sdk" && export PATH="$PATH:$ANDROID_HOME/platform-tools" && cd Android && ./gradlew :app:installDebug && adb -s emulator-5554 shell monkey -p com.neurokaraoke -c android.intent.category.LEANBACK_LAUNCHER 1
```

Verify with D-pad keyevents (`adb -s emulator-5554 shell input keyevent <code>`; 19=up 20=down 21=left 22=right 23=center) and screenshots (`adb -s emulator-5554 exec-out screencap -p > shot.png`):

1. From the nav bar, press **Right** repeatedly to the last tab (Now Playing), then **Right** once more → the corner gear shows its focus ring.
2. Press **center** → the settings panel opens.
3. Navigate to **Crossfade**, press **Right** a few times → the value increases (`Off`→`1s`→`2s`…); **Left** decreases and stops at `Off`.
4. Toggle **Gapless** / **Autoplay** / **Normalize** with **center** → the Switch flips.
5. Press **Back** → panel closes and the gear regains focus.
6. Reopen the panel → the toggles show the values you set (persistence via shared repository).
7. With the panel open, press D-pad directions → focus never lands on the nav/content behind it.
8. Play something on Radio or Now Playing and enter fullscreen → the gear (and nav) are hidden.

- [ ] **Step 5: Commit**

```bash
git add Android/app/src/main/java/com/soul/neurokaraoke/ui/tv/TvControls.kt Android/app/src/main/java/com/soul/neurokaraoke/ui/tv/TvApp.kt
git commit -m "feat(tv): corner gear opens settings panel with D-pad focus gating"
```

---

## Self-Review

**Spec coverage:**
- Corner gear separate from nav pill → Task 3 (header `Box`, `CenterEnd`). ✅
- D-pad Right reaches it → Task 3 Step 4 verification. ✅
- Slide-in overlay, focus gated, Back closes, focus returns to gear → Task 3 (`showSettings`, `canFocus` gate, `TvSettingsScreen` `BackHandler`, `gearFocusRequester`). ✅
- Playback: crossfade (Left/Right 0–12), gapless, autoplay, normalize → Task 2. ✅
- Language list with check → Task 2. ✅
- About with version + unofficial line → Task 2. ✅
- Bound to shared `SettingsRepository`/`LocaleManager`, no new persistence → Task 2 (direct singleton calls). ✅
- No theme / no EQ / no sign-out → none added. ✅
- Hidden in fullscreen → Task 3 (gear inside `if (!fullscreen)`). ✅

**Placeholder scan:** No TBD/TODO; all code blocks are complete. ✅

**Type consistency:** `TvSettingsLogic.stepCrossfade(Int, Int): Int` and `crossfadeLabel(Int): String` are defined in Task 1 and called with matching signatures in Task 2. `TvSettingsScreen(onClose: () -> Unit)` defined in Task 2, called in Task 3. `TvSettingsButton(onClick, modifier, focusRequester)` defined and called in Task 3. `SettingsRepository` / `LocaleManager` member names verified against source. ✅
