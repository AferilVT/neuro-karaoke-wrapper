# Neuro Karaoke Player

A desktop wrapper application for [neurokaraoke.com](https://www.neurokaraoke.com) built with Electron.

## Features

- **System Tray Integration**: Minimize to tray, single-click to show/hide
- **Media Key Support**: Control playback with your keyboard's media keys
- **Always on Top**: Pin the window above other applications
- **Song Title Detection**: Shows current song in window title and tray tooltip
- **Custom Shortcuts**:
  - `Ctrl+K` - Focus search
  - `Ctrl+Shift+T` - Toggle always on top
  - Media keys for play/pause, next, previous

## Installation

### Prerequisites
- Node.js 18+ 
- npm or yarn

### Setup

```bash
# Install dependencies
npm install

# Run in development mode
npm start

# Build for Windows
npm run build:win

# Build for Linux
npm run build:linux

# Build for macOS
npm run build:mac
```

## Usage

1. Launch the application
2. Log in to your neurokaraoke.com account
3. Use the tray icon to show/hide the window
4. Control playback with media keys or on-screen controls

### Tray Menu Options

- **Show Window** - Bring window to foreground
- **Hide to Tray** - Minimize to system tray
- **Always on Top** - Toggle window pinning
- **Quit** - Exit the application

## Development

The application consists of:

- `main.js` - Main Electron process (window management, tray, shortcuts)
- `preload.js` - Bridge script for song detection
- `assets/` - Application icons

## License

MIT
