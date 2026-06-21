# Neuro Karaoke Player

![Page Views](https://count.getloli.com/get/@aferilvt:neuro-karaoke-wrapper?theme=booru-jaypee)

<p align="center">⚠️ <strong>Translators needed for Android app! Check <a href="https://github.com/AferilVT/neuro-karaoke-wrapper/blob/main/Android/TRANSLATING.md">TRANSLATING.md</a> for more details</strong> ⚠️</p>

A cross-platform karaoke player for [neurokaraoke.com](https://neurokaraoke.com) — available as a desktop app (Electron) and an Android app (Jetpack Compose).
All credits go to the website creator "Soul". These are companion apps for the website with extra features.

Check [wiki](https://github.com/AferilVT/neuro-karaoke-wrapper/wiki) for API documentation

## Features

### Desktop
<details>
  <summary>Click to expand!</summary>
  <ul>
    <li><strong>System Tray Integration</strong> — Minimize to tray, single-click to show/hide</li>
    <li><strong>Media Key Support</strong> — Control playback with your keyboard's media keys</li>
    <li><strong>Always on Top</strong> — Pin the window above other applications</li>
    <li><strong>Song Title Detection</strong> — Shows current song in window title and tray tooltip</li>
    <li><strong>Discord Rich Presence</strong> — Shows your current song as an activity in Discord (requires login with Discord)</li>
    <li><strong>Auto-Updater</strong> — Automatic update detection with splash screen and progress bar</li>
  </ul>
</details>

### Android
<details>
  <summary>Click to expand!</summary>
  <ul>
    <li><strong>Browse Setlists &amp; Playlists</strong> — Grid view with 2x2 cover previews, detailed playlist screens</li>
    <li><strong>Search All Songs</strong> — Search across all playlists with real-time results</li>
    <li><strong>Media Playback</strong> — Background playback with lock screen controls and media notifications</li>
    <li><strong>Queue Management</strong> — View and interact with the playback queue</li>
    <li><strong>Synced Lyrics</strong> — Auto-scrolling lyrics from NeuroKaraoke API with lrclib.net fallback</li>
    <li><strong>Audio Caching</strong> — 500MB disk cache for smooth offline-capable playback</li>
    <li><strong>Equalizer &amp; Bass Boost</strong> — 5-band EQ with presets (Normal, Bass, Rock, Pop, Jazz, Classical)</li>
    <li><strong>User Playlists</strong> — Create custom playlists, add/remove songs, play all or shuffle</li>
    <li><strong>Artists Browser</strong> — Browse songs by original artist, sorted by cover count, with Last.fm images</li>
    <li><strong>Library Tab</strong> — Favorites, Playlists, and Downloads unified in one screen with user profile</li>
    <li><strong>Local Music</strong> — Import and play .mp3 files from your device</li>
    <li><strong>Settings</strong> — Crossfade, gapless playback, volume normalization, autoplay</li>
    <li><strong>Download Notifications</strong> — Progress and completion notifications for song downloads</li>
    <li><strong>Playback Persistence</strong> — Remembers last played song and position across app restarts</li>
    <li><strong>Theme Support</strong> — Neuro (cyan), Evil (pink), Duet (purple), and Auto (switches based on current song)</li>
    <li><strong>Discord Sign-in</strong> — OAuth2 authentication with token exchange</li>
  </ul>
</details>

## Installation

### Windows
Download and run `Neuro.Karaoke.Setup.exe` from the [latest release](../../releases/latest).

### macOS
Download `Neuro.Karaoke.Player.Setup.dmg` from the [latest release](../../releases/latest), open it, and drag the app to your Applications folder.

Apple Silicon users can also download `Neuro.Karaoke.Player.Setup.Apple.Silicon.zip`.

> **Note:** The app is unsigned. On first launch, right-click the app and select "Open" to bypass Gatekeeper, or go to System Settings → Privacy & Security and click "Open Anyway".

### Linux

Available for both **x86_64** and **arm64** architectures in many formats. Download from the [latest release](../../releases/latest).

```bash
# Debian/Ubuntu
sudo dpkg -i ./Neuro.Karaoke.Player-*.deb
# Fedora/RHEL
sudo rpm -i ./Neuro.Karaoke.Player-*.rpm
# Arch/Manjaro
yay -Syu neuro-karaoke-app


# Flatpak
sudo flatpak install ./Neuro.Karaoke.Player-*.flatpak
# AppImage
chmod +x ./Neuro.Karaoke.Player-*.AppImage
./Neuro.Karaoke.Player-*.AppImage
```

### Android
Download `Neuro.Karaoke.Player.apk` from the [latest release](../../releases/latest) and install it on your device.

## Development
### Desktop (`./Desktop/`)
#### Prerequisites
- Node.js 18+
- yarn
#### Setup
```bash
# install dependencies
yarn install
```
### Building & Running
```bash
# build unpackaged
yarn build:pre

# start unpackaged
yarn start

# build
yarn build

# build for specific target
yarn build:win
yarn build:linux
yarn build:mac
```
### Structure
#### Code
| File | Description |
|-|-|
| `assets/` | Application icons/resources/scripts |
| `src/` | TypeScript source code |
| `src/main.ts` | Main Electron process (window management, IPC handling) |
| `assets/preload.js` | Bridge script for secure renderer communication |
| `src/tray-manager.ts` | System tray icon and menu logic |
| `src/discord-manager.ts` | Discord Rich Presence integration |
| `src/neurokaraoke-api.ts` | NeuroKaraoke Playback API client |
| `src/config.ts` | Static Application configuration |
#### Environment Variables
| Environment Variable | Usage |
|-|-|
| `DEVTOOLS` | if set, enables chromium DevTools |
| `DISABLE_CUSTOM_TITLEBAR` | if set, disables the custom titlebar (useful where you may not want a titlebar, like some compositors on linux like hyprland) |
| `TEST_SITE_LINK` | if set, allows accessing the test site with the provided link (its a secret) |
| `DISABLE_AUTOUPDATE` | if set, disables the automatic updater |
#### Tech Stack
- **TypeScript** - Language
- **Electron** - Desktop app framework
- **Node.js** - Runtime
- **discord-rpc** - Discord Rich Presence integration

### Android (`./Android/`)
#### Prerequisites
- Android Studio
- Android device or emulator
#### Setup
1. Open the project in Android Studio
2. Connect a device or start an emulator
3. Click **Run**, or build from the command line:
   ```bash
   ./gradlew assembleDebug
   ```
#### Building
```bash
./gradlew assembleDebug
```
#### Tech Stack
- **Kotlin** - Primary language
- **Jetpack Compose** - UI toolkit with Material3
- **Media3 / ExoPlayer** - Audio playback with MediaSession
- **MediaSessionService** - Background playback and notification controls
- **Coil** - Image loading
- **Jetpack Navigation** - Screen navigation
- **Coroutines & Flow** - Asynchronous programming
- **Android AudioFX** - Equalizer and BassBoost effects
## License
MIT
