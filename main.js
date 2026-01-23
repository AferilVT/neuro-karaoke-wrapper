const { app, BrowserWindow, ipcMain, globalShortcut, shell } = require('electron');
const path = require('path');
const config = require('./config');
const DiscordManager = require('./discord-manager');
const TrayManager = require('./tray-manager');
const NeuroKaraokeAPI = require('./neurokaraoke-api');

const isDev = !app.isPackaged;

// Application state
let mainWindow = null;
let isQuitting = false;

// Managers
let discordManager = null;
let trayManager = null;
let apiClient = null;

// Current state
let currentPlaylistId = null;

// Set app ID for Windows taskbar grouping
app.setAppUserModelId(config.APP.ID);

/**
 * Get asset path (works in both dev and production)
 */
function getAssetPath(filename) {
  if (isDev) {
    return path.join(__dirname, 'assets', filename);
  }
  return path.join(process.resourcesPath, 'assets', filename);
}

/**
 * Create the main application window
 */
function createWindow() {
  mainWindow = new BrowserWindow({
    width: config.WINDOW.WIDTH,
    height: config.WINDOW.HEIGHT,
    minWidth: config.WINDOW.MIN_WIDTH,
    minHeight: config.WINDOW.MIN_HEIGHT,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      partition: config.APP.PARTITION,
      preload: path.join(__dirname, 'preload.js')
    },
    backgroundColor: config.WINDOW.BACKGROUND_COLOR,
    autoHideMenuBar: true,
    icon: getAssetPath('neurokaraoke.ico')
  });

  // Set custom user agent
  mainWindow.webContents.setUserAgent(config.APP.USER_AGENT);

  // Hide menu bar
  mainWindow.setMenuBarVisibility(false);

  // Load the site
  mainWindow.loadURL(config.URL.SITE);

  // Minimize to tray instead of closing
  mainWindow.on('close', (event) => {
    if (!isQuitting) {
      event.preventDefault();
      mainWindow.hide();
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  // Block navigation away from allowed hosts
  mainWindow.webContents.on('will-navigate', (event, url) => {
    const isAllowed = config.URL.ALLOWED_HOSTS.some(host => url.startsWith(host));
    if (!isAllowed) {
      event.preventDefault();
      shell.openExternal(url);
    }
  });

  // Open external links in default browser
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });

  return mainWindow;
}

/**
 * Register global media control shortcuts
 */
function registerMediaControls() {
  const executeInRenderer = (script) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.executeJavaScript(script);
    }
  };

  // Media play/pause
  globalShortcut.register('MediaPlayPause', () => {
    executeInRenderer(`
      const playButton = document.querySelector('[aria-label*="play"], [aria-label*="pause"], .play-button, .pause-button');
      if (playButton) playButton.click();
    `);
  });

  // Media next track
  globalShortcut.register('MediaNextTrack', () => {
    executeInRenderer(`
      const nextButton = document.querySelector('[aria-label*="next"], .next-button');
      if (nextButton) nextButton.click();
    `);
  });

  // Media previous track
  globalShortcut.register('MediaPreviousTrack', () => {
    executeInRenderer(`
      const prevButton = document.querySelector('[aria-label*="previous"], .prev-button');
      if (prevButton) prevButton.click();
    `);
  });
}

/**
 * Setup IPC handlers for communication with preload script
 */
function setupIpcHandlers() {
  // Playlist ID updates
  ipcMain.on('playlist-id', async (_event, playlistId) => {
    if (playlistId !== currentPlaylistId) {
      currentPlaylistId = playlistId;
      console.log('Playlist changed to:', playlistId);

      // Fetch playlist data
      try {
        await apiClient.fetchPlaylist(playlistId);
        console.log('✓ Playlist data cached');
      } catch (error) {
        console.error('Failed to fetch playlist:', error);
      }
    }
  });

  // Song info updates
  ipcMain.on('update-song', async (_event, songInfo) => {
    if (!mainWindow || mainWindow.isDestroyed()) return;

    console.log('Received song info:', songInfo);

    if (songInfo && songInfo.title && songInfo.title.trim()) {
      const displayTitle = songInfo.artist
        ? `${songInfo.title} - ${songInfo.artist}`
        : songInfo.title;

      mainWindow.setTitle(`${displayTitle} - ${config.APP.NAME}`);
      trayManager?.updateTooltip(displayTitle);
      discordManager?.updateSong(songInfo.title, songInfo.artist);

      // Fetch metadata from API if we have a playlist
      if (currentPlaylistId && apiClient) {
        try {
          console.log('Fetching metadata for:', songInfo.title, '-', songInfo.artist || 'Unknown', 'playlist:', currentPlaylistId);
          const metadata = await apiClient.getCurrentSongMetadata(
            currentPlaylistId,
            songInfo.title,
            songInfo.artist
          );

          if (metadata) {
            console.log('✓ Got metadata from API:', metadata);

            // Update Discord with album art + credit
            if (metadata.artCredit) {
              discordManager?.updateAlbumArtCredit(metadata.artCredit);
            }
            if (metadata.coverArtUrl) {
              discordManager?.updateAlbumArt(metadata.coverArtUrl);
            }

            // Update artist if API has better info
            if (metadata.coverArtist && !songInfo.artist) {
              discordManager?.updateSong(songInfo.title, metadata.coverArtist);
            }
          }
          if (!metadata) {
            console.log('No metadata returned from API for', songInfo.title);
          }
        } catch (error) {
          console.error('Failed to get metadata from API:', error);
        }
      } else {
        console.log('No playlist ID yet; skipping metadata fetch for', songInfo.title);
      }
    } else {
      mainWindow.setTitle(config.APP.NAME);
      trayManager?.updateTooltip(config.APP.NAME);
      discordManager?.updateSong('', '');
    }
  });

  // Playback state updates
  ipcMain.on('playback-state', (_event, playing) => {
    discordManager?.updatePlaybackState(playing);
  });

  // Song duration updates
  ipcMain.on('song-duration', (_event, durationInSeconds) => {
    discordManager?.updateDuration(durationInSeconds);
  });

  // Song elapsed time updates
  ipcMain.on('song-elapsed', (_event, elapsedSeconds) => {
    discordManager?.updateElapsed(elapsedSeconds);
  });

  // Album art updates (from DOM)
  ipcMain.on('album-art', (_event, imageUrl) => {
    // Only use DOM album art if API didn't provide one
    if (imageUrl) {
      console.log('Received album art from DOM:', imageUrl);
      discordManager?.updateAlbumArt(imageUrl);
    }
  });
}

/**
 * Handle application quit
 */
function handleQuit() {
  isQuitting = true;
  app.quit();
}

/**
 * Initialize application
 */
async function initialize() {
  createWindow();

  // Create system tray
  const trayIconName = process.platform === 'darwin'
    ? 'neurokaraoke.png'
    : 'neurokaraoke.ico';
  trayManager = new TrayManager(getAssetPath(trayIconName));
  try {
    trayManager.create(mainWindow, handleQuit);
  } catch (error) {
    console.error('Failed to create tray icon:', error);
  }

  // Register media controls
  registerMediaControls();

  // Initialize API client
  apiClient = new NeuroKaraokeAPI();

  // Setup IPC handlers
  setupIpcHandlers();

  // Initialize Discord RPC (non-blocking)
  discordManager = new DiscordManager(config.DISCORD_CLIENT_ID);
  discordManager.init().catch((error) => {
    console.error('Discord RPC initialization failed:', error);
  });
}

// App lifecycle events
app.whenReady().then(initialize);

app.on('window-all-closed', () => {
  // Don't quit on macOS when windows are closed - keep running in tray
  if (process.platform !== 'darwin') {
    // Keep running in tray
  }
});

app.on('activate', () => {
  // On macOS, re-create window when dock icon is clicked
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  } else if (mainWindow) {
    mainWindow.show();
  }
});

app.on('will-quit', () => {
  // Unregister all shortcuts
  globalShortcut.unregisterAll();

  // Clean up managers
  discordManager?.destroy();
  trayManager?.destroy();
});

app.on('before-quit', () => {
  isQuitting = true;
});
