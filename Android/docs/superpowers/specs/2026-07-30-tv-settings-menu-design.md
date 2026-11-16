# Android TV — Settings Menu

**Date:** 2026-07-30
**Status:** Approved (design)
**Scope:** Add a settings menu to the Android TV UI, reachable from a corner gear icon, bound to the app's existing shared settings state.

## Goal

The Android TV UI (`ui/tv/`) has no settings surface. Add a 10-foot-friendly settings menu covering the settings that make sense on a TV music app, opened from a gear icon in the top-right corner of the header — deliberately **separate** from the centered nav pill.

## Non-goals

- No theme picker. The TV accent already tracks the current song via the Auto theme, so a manual theme selector is redundant.
- No equalizer. Most TVs have no system EQ UI; the phone shows it as an info-only row, which adds nothing on TV.
- No sign-out here. Sign-in/out stays in the Account tab.
- No new persistence layer. The screen reads and writes the existing `SettingsRepository` and `LocaleManager` singletons, so changes stay in sync with the phone UI.

## Entry point — corner gear

- A focusable gear icon in the TV header, pinned to the **right corner** as a sibling of the centered nav pill (not a 7th tab inside it). The header `Box` in `TvNavBar.kt` already spans full width and centers the pill; the gear aligns to `CenterEnd` of that same header row so it sits vertically centered with the pill.
- Reachable by pressing **D-pad Right** past the last tab (Now Playing). Scales up and shows the accent focus ring on focus, consistent with every other TV control (`tvFocusScale`).
- Hidden when `fullscreen` is active, alongside the nav bar (same visibility condition).
- Activating it (Enter / DPad-center / tap) opens the settings panel.

## Settings panel

- New composable `TvSettingsScreen` rendered as a full-screen overlay, following the exact pattern already used by the playlist-detail overlay in `TvApp`:
  - Opaque panel drawn over the blurred immersive backdrop (`TvImmersiveBackground`).
  - The main content column's focus is gated with `focusProperties { canFocus = !showSettings }` + `focusGroup()` so D-pad focus cannot leak to the nav/content behind the panel.
  - `BackHandler` closes the panel.
  - On open, initial focus lands on the first interactive row. On close, focus returns to the gear (hoisted `FocusRequester`, mirroring how the detail overlay re-requests nav focus on close).
- Layout: left-aligned, vertically scrollable column of sections with generous 10-foot spacing. Section headers in the accent color (matching the phone `SettingsScreen` visual language, restyled for TV).

## Content

All controls bind directly to the shared singletons — no local copies, no new keys.

### Playback (`SettingsRepository`)
- **Crossfade** — focusable row showing the current value (`Off` or `Ns`); **D-pad Left/Right** steps the value across `0..12` seconds via `setCrossfadeDuration`.
- **Gapless playback** — toggle row, flips on Enter via `setGaplessPlayback`.
- **Autoplay** — toggle row via `setAutoPlay`.
- **Normalize volume** — toggle row via `setNormalizeVolume`.

### Language (`LocaleManager`)
- List of `LocaleManager.SUPPORTED_LOCALES`, each a selectable row showing the locale's native name with a check on the active one; selecting calls `LocaleManager.setLanguage`.

### About
- Non-interactive rows: app name, version name/code (from `BuildConfig`), and a single "Unofficial fan app" line. No links or actions.

## TV-styled controls

- Reuses the existing TV focus helpers (`tvFocusScale`, accent ring). No new focus infrastructure.
- **Toggle rows:** focusable row; Enter flips the bound boolean. Shows an on/off indicator (Switch-style or check) tinted with the accent when on.
- **Selectable rows** (language): focusable row; Enter selects. Accent background highlight on focus, matching the detail screen's song rows; a check marks the active choice.
- **Crossfade row:** focusable; Left/Right adjust the stepped value, with the current value rendered on the right.

## Files

- `ui/tv/TvSettingsScreen.kt` — **new.** The panel plus its section/row composables (toggle row, selectable row, crossfade row, about rows).
- `ui/tv/TvControls.kt` — add a small `TvSettingsButton` (focusable gear) alongside the existing `TvFullscreenButton`.
- `ui/tv/TvApp.kt` — add `showSettings` state, render the gear in the header, wire it to open the overlay, render `TvSettingsScreen` as an overlay, and gate the main column's focus while it's open (extend the existing `canFocus` condition, which currently only accounts for `selectedPlaylist`).
- `ui/tv/TvNavBar.kt` — host the gear in the header row's `CenterEnd` (either by having `TvNavBar` accept a trailing slot, or by placing the gear as a sibling of `TvNavBar` in `TvApp`'s header). Implementation plan picks the cleaner of the two.
- Reused as-is: `SettingsRepository`, `LocaleManager`.

## Verification (emulator, `emulator-5554`)

1. Gear is reachable by pressing D-pad Right from the Now Playing tab; it shows the focus ring.
2. Enter on the gear opens the settings panel; Back closes it and focus returns to the gear.
3. Toggling Gapless / Autoplay / Normalize persists: reopen the panel and the new state is shown.
4. Crossfade steps up/down with Left/Right and shows the value.
5. Selecting a different language applies (locale row check moves; UI language changes where applicable).
6. About section shows the correct version.
7. With the panel open, D-pad presses never move focus to the nav/content behind it.
8. The gear (and nav) are hidden while Radio / Now Playing are in fullscreen.
