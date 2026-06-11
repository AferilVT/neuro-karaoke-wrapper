import { app, BrowserWindow, WebContentsView, ipcMain, shell, clipboard, nativeImage, Menu, net } from 'electron';
import { join } from 'path';
import { readFileSync, writeFileSync } from 'fs';
import config from './config.js';
import { DiscordManager } from './discord-manager.js';
import { TrayManager } from './tray-manager.js';
import NeuroKaraokeAPI from './neurokaraoke-api.js';
import { runSplashUpdater } from './auto-updater.js';
import _ from 'lodash'
import { getAssetPath } from './util.js';

export type CloseToTrayFunction = (closed: boolean) => void;
export type Theme = 'neuro' | 'evil' | 'smocus';
export type Site = Theme | 'test';
export type ChangeSiteFunction = (site: Site) => void;

export type State = {
  closeToTray: boolean,
  lastSite: Site,
};
const defaultState: State = {
  closeToTray: true,
  lastSite: 'neuro',
}

// Just adding a comment cause stupid draft-release wont work like I want to :)
// Lock colour profile to sRGB to prevent oversaturation after long sessions
// (Chromium GPU colour-management can drift over extended uptime)
app.commandLine.appendSwitch('force-color-profile', 'srgb');

const isDev: boolean = process.env.DEVTOOLS !== undefined;

const TITLEBAR_HEIGHT: number = 32;

let mainWindow: BrowserWindow | undefined = undefined;
let titlebarView: WebContentsView | undefined = undefined;
let isQuitting: boolean = false;
let trayAvailable: boolean = false;

let discordManager: DiscordManager | undefined = undefined;
let trayManager: TrayManager | undefined = undefined;
let apiClient: NeuroKaraokeAPI | undefined = undefined;

let currentPlaylistId: string | undefined = undefined;

const views: Record<string, WebContentsView> = {};
let currentView: WebContentsView | undefined = undefined;

// Set app ID for Windows taskbar grouping
app.setAppUserModelId(config.app.id);

// Only allow one instance of the app at a time
const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.show();
      mainWindow.focus();
    }
  });
}

// Persistent state file for remembering user preferences (e.g. last site)
const STATE_FILE = join(app.getPath('userData'), 'app-state.json');

function loadState(): State {
  try {
    return _.merge({}, defaultState, JSON.parse(readFileSync(STATE_FILE, 'utf-8')));
  } catch {
    return _.cloneDeep(defaultState);
  }
}

function saveState(patch: Partial<State>) {
  const state = _.merge(loadState(), patch);
  writeFileSync(STATE_FILE, JSON.stringify(state), 'utf-8');
}

let closeToTray = loadState().closeToTray;

function setCloseToTray(value: boolean) {
  closeToTray = value;
  saveState({ closeToTray: value });
}

const iconPath = process.platform === 'win32'
  ? getAssetPath('neurokaraoke.ico')
  : getAssetPath('neurokaraoke.png');

// The theme button label each site should have auto-selected on load
const THEME_LABELS: Record<Site, string> = { neuro: 'NEURO', evil: 'EVIL', smocus: 'SMOCUS', test: 'TEST' };

/**
 * After a site loads, programmatically click its matching theme button.
 * Retries a few times to handle slow Blazor/SPA render times.
 * event.isTrusted=false on these synthetic clicks, so our preload
 * listener ignores them and won't trigger another site switch.
 */
function autoSelectTheme(view: WebContentsView, label: string) {
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
function setupViewEvents(view: WebContentsView, site: Site) {
  view.webContents.setUserAgent(config.app.userAgent);

  const isSafeExternalUrl = (url: string) => {
    try {
      const parsed = new URL(url);
      return parsed.protocol === 'https:' || parsed.protocol === 'http:';
    } catch { return false; }
  };

  view.webContents.on('will-navigate', (event, url) => {
    console.log('[will-navigate]', url);
    try {
      const parsed = new URL(url);
      if (!config.allowedHosts.has(parsed.hostname)) {
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
            partition: config.app.partition
          }
        });
        authWin.setMenuBarVisibility(false);
        authWin.loadURL(url);

        // When the auth window navigates to a karaoke callback, push it into the main view
        authWin.webContents.on('will-navigate', (_event, cbUrl) => {
          try {
            const cb = new URL(cbUrl);
            if (config.allowedHosts.has(cb.hostname)) {
              view.webContents.loadURL(cbUrl);
              authWin.close();
            }
          } catch {}
        });

        // Also catch redirects that happen via new-window inside the auth window
        authWin.webContents.setWindowOpenHandler(({ url: innerUrl }) => {
          try {
            const inner = new URL(innerUrl);
            if (config.allowedHosts.has(inner.hostname)) {
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
  view.webContents.session.webRequest.onCompleted(
    { urls: ['*://api.neurokaraoke.com/api/songs/playCount/*'] },
    (details) => {
      if (details.method !== 'PUT') return;
      const match = details.url.match(/\/playCount\/([0-9a-f-]{36})$/i);
      if (match) {
        const baseUrl = config.url[site];
        const songUrl = `${baseUrl}song/${match[1]}`;
        discordManager?.updateSongUrl(songUrl);
      }
    }
  );
}

/**
 * Get or lazily create a persistent WebContentsView for the given site
 */
function getOrCreateView(site: Site) {
  if (views[site]) return views[site];

  const view = new WebContentsView({
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      partition: config.app.partition,
      preload: getAssetPath('preload.js'),
      backgroundThrottling: false
    }
  });

  setupViewEvents(view, site);
  view.webContents.loadURL(config.url[site]);

  // Auto-select the matching theme button once the site has rendered
  view.webContents.once('did-finish-load', () => {
    autoSelectTheme(view, THEME_LABELS[site]);
    
    // Fix chat close button
    if (!process.env.DISABLE_CUSTOM_TITLEBAR) view.webContents.executeJavaScript(`
      function waitForElement(selector, timeout = 5000) {
        return new Promise((resolve, reject) => {
          const interval = 50;
          let elapsed = 0;
          const timer = setInterval(() => {
            const el = document.querySelector(selector);
            if (el) {
              clearInterval(timer);
              resolve(el);
            } else if (elapsed >= timeout) {
              clearInterval(timer);
              reject(new Error("Element not found: " + selector));
            }
            elapsed += interval;
          }, interval);
        });
      }
  
      (async () => {
        try {
          const rightPanel = await waitForElement('#rc-panel .rc-panel-header .rc-panel-header-right');
          const leftPanel = await waitForElement('#rc-panel .rc-panel-header .rc-panel-header-left');
          console.log("Fixing chat panels", leftPanel, rightPanel);
          while (rightPanel.firstChild) leftPanel.appendChild(rightPanel.firstChild);
          leftPanel.style.marginTop = "${TITLEBAR_HEIGHT / 2}px";
        } catch (err) {
          console.error(err);
        }
      })();
    `);
  });
  
  // Hide titlebarView when DevTools is open
  if (!process.env.DISABLE_CUSTOM_TITLEBAR) {
    view.webContents.on('devtools-opened', () => {
      if (titlebarView) {
        mainWindow?.contentView.removeChildView(titlebarView);
      }
    });
    view.webContents.on('devtools-closed', () => {
      if (titlebarView) {
        mainWindow?.contentView.removeChildView(titlebarView);
        mainWindow?.contentView.addChildView(titlebarView);
      }
    });
  }

  views[site] = view;
  return view;
}

/**
 * Update titlebar and content view bounds to fill the window
 */
function updateViewBounds() {
  if (!mainWindow || mainWindow.isDestroyed()) return;
  const [width, height] = mainWindow.getSize();
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
function switchToSite(site: Site) {
  if (!mainWindow || mainWindow.isDestroyed()) return;

  const view = getOrCreateView(site);
  if (view === currentView) return;

  saveState({ lastSite: site });
  discordManager?.updateSite(site);

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
    width: config.window.width,
    height: config.window.height,
    minWidth: config.window.minWidth,
    minHeight: config.window.minHeight,
    backgroundColor: config.window.backgroundColor,
    frame: false,
    autoHideMenuBar: true,
    icon: iconPath
  });
  
  // Hide menu bar
  mainWindow.setMenuBarVisibility(false);
  
  // Enable DevTools if env var is set
  if (isDev) mainWindow.setMenu(Menu.buildFromTemplate([{
    label: "View", submenu: [{
      label: "Toggle DevTools",
      accelerator: process.platform == "darwin" ? "Cmd+Shift+I" : "Ctrl+Shift+I",
      click: () => currentView?.webContents.toggleDevTools(),
    }]
  }]));
  
  if (!process.env.DISABLE_CUSTOM_TITLEBAR) {    
    // Create custom titlebar view (local-only HTML, safe to use nodeIntegration)
    titlebarView = new WebContentsView({
      webPreferences: {
        nodeIntegration: true,
        contextIsolation: false,
      }
    });
    titlebarView.setBackgroundColor('#00000000');
    titlebarView.webContents.loadFile(getAssetPath('titlebar.html'));
    mainWindow.contentView.addChildView(titlebarView);
    
    // Send initial state once titlebar has loaded
    titlebarView.webContents.once('did-finish-load', () => {
      if (mainWindow && !mainWindow.isDestroyed()) {
        titlebarView?.webContents.send('titlebar-maximize-change', mainWindow.isMaximized());
      }
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
  } else titlebarView = undefined; // Use native titlebar if one wasnt advertised

  // Load the last-used site, defaulting to neuro
  const { lastSite } = loadState();
  const initialTheme = config.url[lastSite] ? lastSite : 'neuro';
  switchToSite(initialTheme);

  // Keep the active view filling the window on resize
  mainWindow.on('resize', () => {
    updateViewBounds();
  });

  mainWindow.on('close', (event) => {
    if (!isQuitting && closeToTray && trayAvailable) {
      event.preventDefault();
      mainWindow?.hide();
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = undefined;
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
        await apiClient?.fetchPlaylist(playlistId);
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

      mainWindow.setTitle(`${displayTitle} - ${config.app.name}`);
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
      mainWindow.setTitle(config.app.name);
      trayManager?.updateTooltip(config.app.name);
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
  // Show splash and check for updates before creating the main window.
  // If an update is downloaded, the app restarts — this code won't continue.
  const { lastSite } = loadState();
  const theme = config.url[lastSite] ? lastSite : 'neuro';
  await runSplashUpdater(theme !== "test" ? theme : "neuro", iconPath);

  createWindow();

  trayManager = new TrayManager(iconPath);
  try {
    trayManager.create(mainWindow!, handleQuit, switchToSite, () => {
      if (currentView && !currentView.webContents.isDestroyed()) {
        currentView.webContents.reloadIgnoringCache();
      }
    }, closeToTray, (value: boolean) => {
      setCloseToTray(value);
      trayManager?.setCloseToTray(value);
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
  discordManager = new DiscordManager(config.discordClientId);
  discordManager.init().catch((error) => {
    console.error('Discord RPC initialization failed:', error);
  });
}

// App lifecycle events
app.whenReady().then(initialize);

app.on('window-all-closed', () => {
  app.quit();
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
