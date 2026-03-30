const { app, BrowserWindow, WebContentsView, ipcMain, shell, clipboard, nativeImage, Menu, net } = require('electron');
const path = require('path');
const fs = require('fs');
const config = require('./config');
const DiscordManager = require('./discord-manager');
const TrayManager = require('./tray-manager');
const NeuroKaraokeAPI = require('./neurokaraoke-api');
const { checkForUpdates } = require('./update-checker');

// Lock colour profile to sRGB to prevent oversaturation after long sessions
// (Chromium GPU colour-management can drift over extended uptime)
app.commandLine.appendSwitch('force-color-profile', 'srgb');

const isDev = !app.isPackaged;

// Custom titlebar height in pixels
const TITLEBAR_HEIGHT = 32;

// Application state
let mainWindow = null;
let titlebarView = null;
let isQuitting = false;
let trayAvailable = false;

// Managers
let discordManager = null;
let trayManager = null;
let apiClient = null;

// Current state
let currentPlaylistId = null;

// One persistent WebContentsView per site (lazily created)
const views = {};
let currentView = null;

const SITE_MAP = {
  neuro: config.URL.NEURO,
  evil: config.URL.EVIL,
  smocus: config.URL.SMOCUS
};

// Set app ID for Windows taskbar grouping
app.setAppUserModelId(config.APP.ID);

// Persistent state file for remembering user preferences (e.g. last site)
const STATE_FILE = path.join(app.getPath('userData'), 'app-state.json');

function loadState() {
  try {
    return JSON.parse(fs.readFileSync(STATE_FILE, 'utf-8'));
  } catch {
    return {};
  }
}

function saveState(patch) {
  const state = { ...loadState(), ...patch };
  fs.writeFileSync(STATE_FILE, JSON.stringify(state), 'utf-8');
}

/**
 * Get asset path (works in both dev and production)
 * Uses packaged assets from inside the .asar
 */
function getAssetPath(filename) {
  return path.join(app.getAppPath(), 'assets', filename);
}

/** @type {string} */
const iconPath = process.platform === 'win32'
  ? getAssetPath('neurokaraoke.ico')
  : getAssetPath('neurokaraoke.png');

// The theme button label each site should have auto-selected on load
const THEME_LABELS = { neuro: 'NEURO', evil: 'EVIL', smocus: 'SMOCUS' };

/**
 * After a site loads, programmatically click its matching theme button.
 * Retries a few times to handle slow Blazor/SPA render times.
 * event.isTrusted=false on these synthetic clicks, so our preload
 * listener ignores them and won't trigger another site switch.
 */
function autoSelectTheme(view, label) {
  const safeLabel = JSON.stringify(label);
  const script = `
    (function() {
      const target = ${safeLabel};
      const btns = Array.from(document.querySelectorAll('button, [role="button"]'));
      const t = btns.find(b => (b.textContent || '').trim().replace(/\\s+/g, '').toUpperCase() === target);
      if (t) { t.click(); return true; }
      return false;
    })()
  `;
  let attempts = 0;
  const tryClick = () => {
    if (!view.webContents || view.webContents.isDestroyed()) return;
    view.webContents.executeJavaScript(script).then(found => {
      if (!found && attempts++ < 6) setTimeout(tryClick, 1500);
    }).catch(() => {});
  };
  setTimeout(tryClick, 2000); // initial delay for SPA to render
}

/**
 * Wire up webContents events for a view
 */
function setupViewEvents(view, theme) {
  view.webContents.setUserAgent(config.APP.USER_AGENT);


  // Block navigation away from allowed hosts
  const allowedHostnames = new Set([
    'www.neurokaraoke.com', 'neurokaraoke.com',
    'www.evilkaraoke.com', 'evilkaraoke.com',
    'www.twinskaraoke.com', 'twinskaraoke.com',
    'eu.twinskaraoke.com',
    'cn.neurokaraoke.com',
    'discord.com', 'www.discord.com'
  ]);

  const isSafeExternalUrl = (u) => {
    try {
      const parsed = new URL(u);
      return parsed.protocol === 'https:' || parsed.protocol === 'http:';
    } catch { return false; }
  };

  view.webContents.on('will-navigate', (event, url) => {
    console.log('[will-navigate]', url);
    try {
      const parsed = new URL(url);
      if (!allowedHostnames.has(parsed.hostname)) {
        console.log('[will-navigate] BLOCKED, opening externally:', parsed.hostname);
        event.preventDefault();
        if (isSafeExternalUrl(url)) shell.openExternal(url);
      }
    } catch {
      event.preventDefault();
    }
  });

  // Handle popups — open Discord OAuth in a child window, everything else externally
  view.webContents.setWindowOpenHandler(({ url }) => {
    console.log('[setWindowOpenHandler]', url);
    try {
      const parsed = new URL(url);
      if (parsed.hostname === 'discord.com' || parsed.hostname === 'www.discord.com') {
        // Open Discord OAuth in a child BrowserWindow so the flow stays in-app
        const authWin = new BrowserWindow({
          width: 500,
          height: 750,
          parent: mainWindow,
          modal: true,
          webPreferences: {
            partition: config.APP.PARTITION
          }
        });
        authWin.setMenuBarVisibility(false);
        authWin.loadURL(url);

        // When the auth window navigates to a karaoke callback, push it into the main view
        authWin.webContents.on('will-navigate', (_event, cbUrl) => {
          try {
            const cb = new URL(cbUrl);
            if (allowedHostnames.has(cb.hostname)) {
              view.webContents.loadURL(cbUrl);
              authWin.close();
            }
          } catch {}
        });

        // Also catch redirects that happen via new-window inside the auth window
        authWin.webContents.setWindowOpenHandler(({ url: innerUrl }) => {
          try {
            const inner = new URL(innerUrl);
            if (allowedHostnames.has(inner.hostname)) {
              view.webContents.loadURL(innerUrl);
              authWin.close();
            } else if (isSafeExternalUrl(innerUrl)) {
              shell.openExternal(innerUrl);
            }
          } catch {}
          return { action: 'deny' };
        });

        return { action: 'deny' };
      }
    } catch {}

    if (isSafeExternalUrl(url)) shell.openExternal(url);
    return { action: 'deny' };
  });

  // Capture song ID from the playCount API request the Blazor app makes
  if (theme === 'neuro') {
    view.webContents.session.webRequest.onCompleted(
      { urls: ['*://api.neurokaraoke.com/api/songs/playCount/*'] },
      (details) => {
        if (details.method !== 'PUT') return;
        const match = details.url.match(/\/playCount\/([0-9a-f-]{36})$/i);
        if (match) {
          const songUrl = `https://www.neurokaraoke.com/song/${match[1]}`;
          discordManager?.updateSongUrl(songUrl);
        }
      }
    );

    view.webContents.once('did-finish-load', () => {
      setTimeout(() => checkForUpdates(mainWindow), 3000);
    });
  }
}

/**
 * Get or lazily create a persistent WebContentsView for the given site
 */
function getOrCreateView(theme) {
  if (views[theme]) return views[theme];

  const view = new WebContentsView({
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      partition: config.APP.PARTITION,
      preload: path.join(__dirname, 'preload.js'),
      backgroundThrottling: false
    }
  });

  setupViewEvents(view, theme);
  view.webContents.loadURL(SITE_MAP[theme]);

  // Auto-select the matching theme button once the site has rendered
  view.webContents.once('did-finish-load', () => {
    autoSelectTheme(view, THEME_LABELS[theme]);
  });

  views[theme] = view;
  return view;
}

/**
 * Update titlebar and content view bounds to fill the window
 */
function updateViewBounds() {
  if (!mainWindow || mainWindow.isDestroyed()) return;
  const [width, height] = mainWindow.getContentSize();
  // Content fills entire window — titlebar overlays on top
  if (currentView) {
    currentView.setBounds({ x: 0, y: 0, width, height });
  }
  if (titlebarView && !titlebarView.webContents.isDestroyed()) {
    titlebarView.setBounds({ x: 0, y: 0, width, height: TITLEBAR_HEIGHT });
  }
}


/**
 * Switch the visible site without reloading — preserves login state
 */
function switchToSite(theme) {
  if (!mainWindow || mainWindow.isDestroyed()) return;

  const view = getOrCreateView(theme);
  if (view === currentView) return;

  saveState({ lastSite: theme });

  if (currentView) {
    // Pause audio, clear cache, and destroy the old view to free RAM
    currentView.webContents.setAudioMuted(true);
    currentView.webContents.executeJavaScript(
      'document.querySelectorAll("audio, video").forEach(m => m.pause())'
    ).catch(() => {});
    mainWindow.contentView.removeChildView(currentView);
    const oldTheme = Object.keys(views).find(k => views[k] === currentView);
    currentView.webContents.session.clearCache().catch(() => {});
    currentView.webContents.close();
    if (oldTheme) delete views[oldTheme];
  }
  mainWindow.contentView.addChildView(view);
  // Ensure titlebar stays on top of the content view
  if (titlebarView) {
    mainWindow.contentView.removeChildView(titlebarView);
    mainWindow.contentView.addChildView(titlebarView);
  }
  view.webContents.setAudioMuted(false);

  currentView = view;
  updateViewBounds();
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
    backgroundColor: config.WINDOW.BACKGROUND_COLOR,
    frame: false,
    autoHideMenuBar: true,
    icon: iconPath
  });

  // Hide menu bar
  mainWindow.setMenuBarVisibility(false);

  // Create custom titlebar view (local-only HTML, safe to use nodeIntegration)
  titlebarView = new WebContentsView({
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false
    }
  });
  titlebarView.setBackgroundColor('#00000000');
  titlebarView.webContents.loadFile(path.join(__dirname, 'titlebar.html'));
  mainWindow.contentView.addChildView(titlebarView);

  // Load the last-used site, defaulting to neuro
  const { lastSite } = loadState();
  const initialTheme = SITE_MAP[lastSite] ? lastSite : 'neuro';

  // Send initial state once titlebar has loaded
  titlebarView.webContents.once('did-finish-load', () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      titlebarView.webContents.send('titlebar-maximize-change', mainWindow.isMaximized());
    }
  });
  switchToSite(initialTheme);

  // Keep the active view filling the window on resize
  mainWindow.on('resize', () => {
    updateViewBounds();
  });

  // Notify titlebar of maximize/restore state changes
  mainWindow.on('maximize', () => {
    if (titlebarView && !titlebarView.webContents.isDestroyed()) {
      titlebarView.webContents.send('titlebar-maximize-change', true);
    }
  });
  mainWindow.on('unmaximize', () => {
    if (titlebarView && !titlebarView.webContents.isDestroyed()) {
      titlebarView.webContents.send('titlebar-maximize-change', false);
    }
  });

  // Minimize to tray instead of closing (only if tray is available)
  mainWindow.on('close', (event) => {
    if (!isQuitting) {
      // On Linux without tray support, quit the app instead of hiding
      if (process.platform === 'linux' && !trayAvailable) {
        isQuitting = true;
        return; // Allow the close to proceed
      }
      event.preventDefault();
      mainWindow.hide();
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  return mainWindow;
}

/**
 * Setup IPC handlers for communication with preload script
 */
function setupIpcHandlers() {
  // Titlebar window controls
  ipcMain.on('titlebar-minimize', () => {
    mainWindow?.minimize();
  });

  ipcMain.on('titlebar-maximize', () => {
    if (mainWindow?.isMaximized()) {
      mainWindow.unmaximize();
    } else {
      mainWindow?.maximize();
    }
  });

  ipcMain.on('titlebar-close', () => {
    mainWindow?.close();
  });

  // Playlist ID updates
  ipcMain.on('playlist-id', async (_event, playlistId) => {
    if (playlistId !== currentPlaylistId) {
      currentPlaylistId = playlistId;

      // Fetch playlist data
      try {
        await apiClient.fetchPlaylist(playlistId);
      } catch (error) {
        console.error('Failed to fetch playlist:', error);
      }
    }
  });

  // Song info updates
  ipcMain.on('update-song', async (_event, songInfo) => {
    if (!mainWindow || mainWindow.isDestroyed()) return;

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
          const metadata = await apiClient.getCurrentSongMetadata(
            currentPlaylistId,
            songInfo.title,
            songInfo.artist
          );

          if (metadata) {
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
        } catch (error) {
          console.error('Failed to get metadata from API:', error);
        }
      }
    } else {
      mainWindow.setTitle(config.APP.NAME);
      trayManager?.updateTooltip(config.APP.NAME);
      discordManager?.updateSong('', '');
    }
  });

  // Song URL updates (for Discord RPC button)
  ipcMain.on('song-url', (_event, url) => {
    discordManager?.updateSongUrl(url);
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

  // Image right-click context menu
  ipcMain.on('show-image-context-menu', (_event, imageUrl) => {
    // Validate URL protocol to prevent fetching file:// or other dangerous schemes
    try {
      const parsed = new URL(imageUrl);
      if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') return;
    } catch { return; }

    const menu = Menu.buildFromTemplate([
      {
        label: 'Copy Image',
        click: async () => {
          try {
            const response = await net.fetch(imageUrl);
            const arrayBuffer = await response.arrayBuffer();
            const image = nativeImage.createFromBuffer(Buffer.from(arrayBuffer));
            if (!image.isEmpty()) {
              clipboard.writeImage(image);
            }
          } catch (error) {
            console.error('Failed to copy image:', error);
          }
        }
      },
      {
        label: 'Copy Image URL',
        click: () => {
          clipboard.writeText(imageUrl);
        }
      }
    ]);
    menu.popup({ window: mainWindow });
  });

  // Album art updates (from DOM)
  ipcMain.on('album-art', (_event, imageUrl) => {
    // Only use DOM album art if API didn't provide one
    if (imageUrl) {
      discordManager?.updateAlbumArt(imageUrl);
    }
  });

  // Site switching — from tray menu
  ipcMain.on('switch-site', (_event, theme) => {
    if (['neuro', 'evil', 'smocus'].includes(theme)) {
      switchToSite(theme);
    }
  });
}

/**
 * Handle application quit
 */
function handleQuit() {
  isQuitting = true;
  // On macOS, app.quit() called directly from a tray menu can be swallowed
  // by the Cocoa event loop while the menu is still dismissing. Destroying
  // the window first (skipping the close-event cycle) and deferring the
  // quit past the current event loop tick fixes this reliably.
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.destroy();
  }
  setImmediate(() => app.quit());
}

/**
 * Initialize application
 */
async function initialize() {
  createWindow();

  trayManager = new TrayManager(iconPath);
  try {
    trayManager.create(mainWindow, handleQuit, switchToSite, () => {
      if (currentView && !currentView.webContents.isDestroyed()) {
        currentView.webContents.reloadIgnoringCache();
      }
    });
    trayAvailable = trayManager.isAvailable();
  } catch (error) {
    console.error('Failed to create tray icon:', error);
    trayAvailable = false;
  }

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
  // On macOS, keep running in tray (standard behavior)
  // On Linux without tray, quit the app
  // On Windows, keep running in tray
  if (process.platform === 'linux' && !trayAvailable) {
    app.quit();
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
  // Clean up managers
  discordManager?.destroy();
  trayManager?.destroy();
});

app.on('before-quit', () => {
  isQuitting = true;
});
