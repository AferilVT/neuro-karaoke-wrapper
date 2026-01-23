const { Tray, Menu, app } = require('electron');

/**
 * Manages system tray icon and menu
 */
class TrayManager {
  constructor(iconPath) {
    this.iconPath = iconPath;
    this.tray = null;
    this.mainWindow = null;
  }

  /**
   * Create the system tray icon
   */
  create(mainWindow, onQuit) {
    this.mainWindow = mainWindow;
    this.tray = new Tray(this.iconPath);
    this.tray.setToolTip('Neuro Karaoke');

    const contextMenu = Menu.buildFromTemplate([
      {
        label: 'Open',
        click: () => this.showWindow()
      },
      {
        label: 'Exit',
        click: () => onQuit()
      }
    ]);

    this.tray.setContextMenu(contextMenu);

    // Single click to toggle window visibility (Windows style)
    this.tray.on('click', () => this.toggleWindow());

    // Double click to show and focus
    this.tray.on('double-click', () => this.showWindow());

    return this.tray;
  }

  /**
   * Show the main window
   */
  showWindow() {
    if (!this.mainWindow || this.mainWindow.isDestroyed()) {
      console.warn('Cannot show window: window is destroyed');
      return;
    }
    this.mainWindow.show();
    this.mainWindow.focus();
  }

  /**
   * Toggle window visibility
   */
  toggleWindow() {
    if (!this.mainWindow || this.mainWindow.isDestroyed()) {
      return;
    }

    if (this.mainWindow.isVisible()) {
      this.mainWindow.hide();
    } else {
      this.showWindow();
    }
  }

  /**
   * Update tray tooltip text
   */
  updateTooltip(text) {
    if (this.tray) {
      this.tray.setToolTip(text);
    }
  }

  /**
   * Destroy the tray icon
   */
  destroy() {
    if (this.tray) {
      this.tray.destroy();
      this.tray = null;
    }
  }
}

module.exports = TrayManager;
