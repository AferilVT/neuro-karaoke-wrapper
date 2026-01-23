# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Neuro Karaoke Player is an Electron desktop wrapper for neurokaraoke.com that adds native features like system tray integration, media key support, and song title detection.

## Development Commands

```bash
# Run in development mode
npm start

# Build for all platforms
npm run build

# Platform-specific builds
npm run build:win     # Windows (NSIS installer)
npm run build:linux   # Linux (AppImage + deb)
npm run build:mac     # macOS (dmg)
```

Built artifacts are output to the `dist/` directory.

## Architecture

### Core Files

The application follows a modular architecture with clear separation of concerns:

**Main Process:**
- **[main.js](main.js)** - Application entry point. Coordinates window lifecycle, managers, and IPC handlers.
- **[config.js](config.js)** - Centralized configuration for Discord client ID, URLs, window settings, and app metadata.
- **[discord-manager.js](discord-manager.js)** - Manages Discord Rich Presence integration. Handles connection, presence updates, and state tracking.
- **[tray-manager.js](tray-manager.js)** - Manages system tray icon, menu, and window visibility controls.

**Renderer Process:**
- **[preload.js](preload.js)** - Context bridge with class-based detection system. Contains `SongTitleDetector`, `PlaybackStateDetector`, `SongDurationDetector`, and `SongDetectionManager`.

**Configuration:**
- **[package.json](package.json)** - Project metadata and electron-builder settings for all platforms.

### Code Organization

The codebase follows a modular, class-based architecture:

**Main Process Managers:**
- `DiscordManager` - Self-contained Discord RPC client with state management
- `TrayManager` - System tray icon with window visibility controls

**Renderer Detectors:**
- `SongTitleDetector` - Multi-strategy song title detection (global player → mobile player → document title)
- `PlaybackStateDetector` - Play/pause detection via SVG icons and ARIA labels
- `SongDurationDetector` - Song duration extraction from player UI
- `SongDetectionManager` - Orchestrates all detectors and handles IPC communication

**Benefits:**
- Clear separation of concerns
- Easily testable individual components
- Maintainable and extensible codebase
- Centralized configuration in [config.js](config.js)

### IPC Communication Flow

The preload script communicates with the main process via three IPC channels:

1. **`update-title`** - Sends detected song title + artist from DOM to main process. Updates window title, tray tooltip, and Discord presence.
2. **`playback-state`** - Sends playback state (playing/paused) detected from DOM elements to update Discord presence.
3. **`song-duration`** - Sends song duration in seconds to enable Discord progress bar.

### Song Detection Strategy

[preload.js](preload.js) implements a class-based detection system:

**SongTitleDetector** uses multiple strategies in priority order:
1. **Primary**: `detectFromGlobalPlayer()` - Queries `.global-player` for song title (`p.mud-typography-body2`) and artist (`span.mud-typography-caption`)
2. **Fallback**: `detectFromMobilePlayer()` - Checks `.mobile-player` container
3. **Last resort**: `detectFromDocumentTitle()` - Parses `document.title` with regex patterns

**PlaybackStateDetector** uses:
1. **Primary**: `detectFromSvgIcon()` - Analyzes SVG path data to identify pause icon (playing) vs play icon (paused)
2. **Fallback**: `detectFromAriaLabel()` - Checks button ARIA labels

**SongDurationDetector** uses:
1. **Direct**: Queries `.desktop-duration-time` selector
2. **Fallback**: Searches `.desktop-progress-container` for time-formatted spans (e.g., "3:21")

**SongDetectionManager** orchestrates detection via:
- MutationObserver watching `document.body` for DOM changes
- Fallback polling every 1.5 seconds
- Separate MutationObserver on `<title>` element
- Automatic IPC message sending when state changes

### Media Controls

[main.js:147-199](main.js#L147-L199) registers global shortcuts using `globalShortcut`:

- **MediaPlayPause/Next/PreviousTrack**: Execute JavaScript in renderer to find and click player buttons
- **Ctrl+K**: Focus search input on the page
- **Ctrl+Shift+T**: Toggle always-on-top window state

Button detection uses generic selectors (`[aria-label*="play"]`, `.play-button`) to work with neurokaraoke.com's structure.

### Tray Integration

[main.js:21-88](main.js#L21-L88) creates a system tray icon with:

- Context menu for show/hide, always-on-top, and quit
- Single-click to toggle window visibility (Windows style)
- Double-click to show and focus window
- Dynamic tooltip showing current song

### Discord Rich Presence

[discord-manager.js](discord-manager.js) implements Discord Rich Presence as a self-contained class:

- **Initialization**: `DiscordManager.init()` connects to Discord IPC on app startup
- **State management**: Tracks `currentSong`, `isPlaying`, `songStartTime`, and `songDuration`
- **Real-time updates**: `updatePresence()` called automatically when state changes
- **Display format** (Music player style):
  - Application: "Neuro Karaoke"
  - Details: Full song title with artist (e.g., "Die for You - Neuro") - clickable, links to neurokaraoke.com
  - State: "Listening"
  - Large image: Neuro Karaoke logo (`neurokaraoke` asset key)
  - Small image: Play/pause indicator (if assets uploaded)
  - Progress bar: Shows elapsed/remaining time with song duration
  - Status text: Shows song title in Discord member list
  - Button: "Listen on Neuro Karaoke" link

**Setup Requirements:**
1. Create Discord application at https://discord.com/developers/applications
2. Copy Application ID and replace `DISCORD_CLIENT_ID` in [config.js](config.js)
3. Upload `neurokaraoke.png` as Rich Presence asset with key `neurokaraoke`
4. Optionally add `play` and `pause` icons for small image states

**Custom Discord Features:**
The app uses patched `discord-rpc` package with support for 2025 Discord API features:
- `detailsUrl`: Makes song title clickable
- `statusField`: Customizes status text in member list
- Modified in [node_modules/discord-rpc/src/client.js](node_modules/discord-rpc/src/client.js) to pass through new fields

### Window Management

Key behaviors:

- **Navigation blocking** [main.js:132-144](main.js#L132-L144): Prevents navigation away from neurokaraoke.com. External links open in system browser.
- **Close behavior** [main.js:120-125](main.js#L120-L125): Closing window hides to tray instead of quitting (unless `isQuitting` flag is set).
- **Persistent session**: Uses `partition: 'persist:neurokaraoke'` to maintain login state across restarts.
- **Custom user agent** [main.js:110-112](main.js#L110-L112): Set to avoid potential blocking.

### Asset Path Handling

[main.js:14-19](main.js#L14-L19) `getAssetPath()` function handles different paths in development vs production:

- **Development**: `__dirname/assets/`
- **Production**: `process.resourcesPath/assets/`

This is critical for icon loading in both modes.

## Important Implementation Details

### Security Configuration

The BrowserWindow uses secure web preferences:
- `nodeIntegration: false` - No Node.js in renderer
- `contextIsolation: true` - Isolated preload context
- Communication only via explicit IPC channels

### DOM Selectors

When modifying song detection in [preload.js](preload.js), note that selectors target neurokaraoke.com's MudBlazor UI components:
- `mud-typography-*` classes for text elements
- `theme-text-primary/secondary` for color variants
- Player containers use `.global-player` or `.mobile-player`

### electron-builder Configuration

[package.json:25-80](package.json#L25-L80) defines build configuration:
- ASAR packaging enabled
- Only essential files bundled: `main.js`, `preload.js`, `package.json`
- Assets copied to `extraResources`
- Platform-specific icons and installers configured

## Common Issues

### Song Title Not Updating

If song titles aren't being detected:
1. Check if neurokaraoke.com changed their DOM structure
2. Update CSS selectors in `SongTitleDetector` class ([preload.js](preload.js))
3. Verify MutationObserver is running (check console logs)
4. Test each detection strategy individually

### Media Keys Not Working

Global shortcuts may conflict with other applications. Check `registerMediaControls()` in [main.js](main.js) and verify selectors match neurokaraoke.com's current button structure.

### Playback State Incorrect

If Discord shows wrong play/pause state:
1. Inspect the play/pause button SVG in browser DevTools
2. Update SVG path patterns in `PlaybackStateDetector.detectFromSvgIcon()` ([preload.js](preload.js))
3. Check console logs for detection messages

### Asset Loading Failures

If icons don't load in production builds, verify `getAssetPath()` logic and ensure assets are in `extraResources` build config.

### Discord Rich Presence Not Showing

If Discord presence doesn't appear:
1. Ensure Discord desktop app is running
2. Verify `DISCORD_CLIENT_ID` is set correctly in [config.js](config.js)
3. Check that Rich Presence assets are uploaded with correct key names (`neurokaraoke`)
4. Verify asset key in `DiscordManager.updatePresence()` matches uploaded asset name
5. Enable "Display current activity as a status message" in Discord Settings → Activity Privacy
6. Check console for Discord RPC connection errors

### Progress Bar Not Working

If the Discord progress bar doesn't show:
1. Open browser DevTools console and look for "Duration element not found" messages
2. Inspect the player UI to locate the duration element
3. Update selectors in `SongDurationDetector.findDurationElement()` ([preload.js](preload.js))
4. Verify duration is being sent via IPC (look for "Received song duration" in console)
