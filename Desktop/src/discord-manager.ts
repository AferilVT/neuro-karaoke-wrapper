import { Site } from "./main";

const DiscordRPC = require('discord-rpc');

type SiteConfig = { label: string, url: string };
const SITE_CONFIG: Record<Site, SiteConfig> = {
  neuro:  { label: 'Listen on Neuro Karaoke',  url: 'https://www.neurokaraoke.com/' },
  evil:   { label: 'Listen on Evil Karaoke',   url: 'https://www.evilkaraoke.com/' },
  smocus: { label: 'Listen on Smocus Karaoke', url: 'https://twinskaraoke.com/' },
  test:   { label: 'Listen on Neuro Karaoke',  url: 'https://www.neurokaraoke.com/' },
};

/**
 * - 0 = playing 
 * - 2 = listening
 */ 
export type ActivityType = 0 | 2;

export type Activity = {
  type: ActivityType,
  details: string,
  state: string,
  largeImageKey: string,
  largeImageText: string,
  smallImageKey: 'play' | 'pause',
  smallImageText: 'Playing' | 'Paused',
  instance: boolean,
  buttons: [SiteConfig],
  startTimestamp?: number,
  endTimestamp?: number,
};

/**
 * Manages Discord Rich Presence integration
 */
export class DiscordManager {
  clientId: string;
  client: any;
  currentSong: string = '';
  currentArtist: string = '';
  isPlaying: boolean = false;
  songStartTime?: number = undefined;
  songDuration?: number = undefined;
  songElapsed: number = 0; // Actual elapsed time from player
  pausedElapsed: number = 0; // Track elapsed time when paused
  albumArtUrl?: string = undefined; // Store album art URL
  albumArtText?: string = undefined; // Store album art credit text
  albumArtCredit?: string = undefined;
  lastPresenceUpdate: number = 0;
  minPresenceIntervalMs: number = 15000;
  pendingPresenceTimer?: NodeJS.Timeout = undefined;
  isUpdatingPresence: boolean = false;
  pendingPresenceRequested: boolean = false;
  pendingPresenceForce: boolean = false;
  activityType: ActivityType = 2; // 0 = Playing, 2 = Listening
  songUrl?: string = undefined; // Current page URL for Discord button
  currentSite: Site = 'neuro';
  coalesceTimer?: NodeJS.Timeout = undefined;
  /** How long to gather state changes before pushing one presence update. */
  coalesceDelayMs: number = 750;

  constructor(clientId: string) {
    this.clientId = clientId;
  }

  /**
   * Push a forced presence update once the current burst of changes settles.
   * A song change reports title, duration, album art, art credit and song URL
   * separately within about a second; sending each one immediately ran into
   * Discord's SET_ACTIVITY rate limit (~5 per 20 s), so updates were dropped
   * and the presence could show stale art or the previous song.
   */
  schedulePresence() {
    if (this.coalesceTimer) clearTimeout(this.coalesceTimer);
    this.coalesceTimer = setTimeout(() => {
      this.coalesceTimer = undefined;
      this.updatePresence(true);
    }, this.coalesceDelayMs);
  }

  /**
   * Initialize and connect to Discord RPC
   */
  async init() {
    try {
      DiscordRPC.register(this.clientId);
      this.client = new DiscordRPC.Client({ transport: 'ipc' });

      this.client.on('ready', () => {
        this.updatePresence();
      });

      this.client.on('error', (error: any) => {
        console.error('Discord RPC error:', error);
      });

      await this.client.login({ clientId: this.clientId });
    } catch (error) {
      console.error('❌ Failed to initialize Discord RPC:', error);
      console.error('Make sure Discord desktop app is running!');
      this.client = null;
    }
  }

  /**
   * Update song information
   */
  updateSong(title: string, artist: string = '') {
    const songChanged = this.currentSong !== title || this.currentArtist !== artist;
    this.currentSong = title;
    this.currentArtist = artist;

    if (songChanged && title) {
      // Reset state for new song
      this.isPlaying = true;
      this.songStartTime = Date.now();
      this.songElapsed = 0;
      this.pausedElapsed = 0;
      this.albumArtUrl = undefined; // Reset album art for new song
      this.albumArtText = undefined; // Reset album art credit
      this.albumArtCredit = undefined;
      this.lastPresenceUpdate = 0; // Force an immediate sync for new songs
      if (this.pendingPresenceTimer) {
        clearTimeout(this.pendingPresenceTimer);
        this.pendingPresenceTimer = undefined;
      }
    }

    this.schedulePresence();
  }

  /**
   * Update playback state (playing/paused)
   */
  updatePlaybackState(playing: boolean) {
    const wasPlaying = this.isPlaying;
    this.isPlaying = playing;

    if (playing && !wasPlaying) {
      // Resuming playback - elapsed time will update via updateElapsed()
      if (!this.songStartTime) {
        // Starting fresh
        this.songStartTime = Date.now();
      }
      if (this.pausedElapsed) {
        this.songStartTime = Date.now() - (this.pausedElapsed * 1000);
      }
    } else if (!playing && wasPlaying) {
      // Pausing playback. Elapsed is no longer reported every second (only on
      // jumps), so derive it from the start time rather than the last report.
      this.pausedElapsed = this.songStartTime
        ? Math.max(0, (Date.now() - this.songStartTime) / 1000)
        : this.songElapsed;
      this.songElapsed = this.pausedElapsed;
    }

    this.schedulePresence();
  }

  /**
   * Update song duration
   */
  updateDuration(durationInSeconds: number) {
    this.songDuration = durationInSeconds;
    this.schedulePresence();
  }

  /**
   * Update song elapsed time (from player)
   */
  updateElapsed(elapsedSeconds: number) {
    this.songElapsed = elapsedSeconds;

    // Update start time based on current elapsed time
    this.songStartTime = Date.now() - (elapsedSeconds * 1000);

    // Elapsed now arrives only when playback jumps (seek, resume), so the
    // progress bar needs correcting promptly.
    if (this.isPlaying) {
      this.schedulePresence();
    }
  }

  /**
   * Update album art
   */
  updateAlbumArt(imageUrl: string, artText?: string) {
    if (imageUrl) {
      this.albumArtUrl = imageUrl;
    }
    if (artText) {
      this.albumArtText = artText;
    }
    this.schedulePresence();
  }

  /**
   * Update the active site
   */
  updateSite(site: Site) {
    if (this.currentSite === site) return;
    this.currentSite = site;
    this.songUrl = undefined;
    this.schedulePresence();
  }

  /**
   * Update the current page URL for the Discord button
   */
  updateSongUrl(url: string) {
    if (!url) return;
    const siteBase = SITE_CONFIG[this.currentSite].url;
    if (!url.startsWith(siteBase)) return;
    if (url === this.songUrl) return;
    this.songUrl = url;
    this.schedulePresence();
  }

  updateAlbumArtCredit(credit: string) {
    if (!credit || typeof credit !== 'string') return;
    const cleaned = credit.replace(/^Art by:\s*/i, '').trim();
    this.albumArtCredit = cleaned;
    this.albumArtText = cleaned;
    this.schedulePresence();
  }

  /**
   * Update Discord Rich Presence with current state
   */
  async updatePresence(force: boolean = false) {
    if (!this.client) {
      return;
    }

    const now = Date.now();
    if (!force && now - this.lastPresenceUpdate < this.minPresenceIntervalMs) {
      if (!this.pendingPresenceTimer) {
        const delay = this.minPresenceIntervalMs - (now - this.lastPresenceUpdate);
        this.pendingPresenceTimer = setTimeout(() => {
          this.pendingPresenceTimer = undefined;
          this.updatePresence();
        }, delay);
      }
      return;
    }

    if (this.isUpdatingPresence) {
      this.pendingPresenceRequested = true;
      this.pendingPresenceForce = this.pendingPresenceForce || force;
      return;
    }

    this.isUpdatingPresence = true;

    try {
      if (!this.currentSong || this.currentSong.trim() === '') {
        await this.client.clearActivity();
        this.lastPresenceUpdate = Date.now();
        return;
      }

      const largeImageKey = this.albumArtUrl || 'neurokaraoke';

      const truncate = (value: string, max: number) => {
        if (!value || value.length <= max) return value;
        return `${value.slice(0, max - 1)}…`;
      };

      const detailsText = this.currentSong;
      const stateText = this.currentArtist || 'Listening';
      const cfg = SITE_CONFIG[this.currentSite];

      const activity: Activity = {
        type: this.activityType,
        details: truncate(detailsText, 128),
        state: truncate(stateText, 128),
        largeImageKey,
        largeImageText: this.albumArtText || "Neuro Karaoke",
        smallImageKey: this.isPlaying ? 'play' : 'pause',
        smallImageText: this.isPlaying ? 'Playing' : 'Paused',
        instance: false,
        buttons: [
          { label: cfg.label, url: this.songUrl || cfg.url }
        ]
      };

      // Only show progress bar when playing
      if (this.isPlaying && this.songStartTime && this.songDuration) {
        const startTimestamp = Math.floor(this.songStartTime / 1000);
        activity.startTimestamp = startTimestamp;
        activity.endTimestamp = startTimestamp + this.songDuration;
      } else if (!this.isPlaying) {
        // Discord RPC has no pause state for the progress bar; omit timestamps to prevent countdown.
        activity.state = this.currentArtist ? `${this.currentArtist} · Paused` : 'Paused';
      }

      const pid = process.pid;
      if (typeof this.client.request === 'function') {
        const payload = {
          pid,
          activity: {
            type: activity.type,
            state: activity.state,
            details: activity.details,
            timestamps: activity.startTimestamp || activity.endTimestamp ? {
              start: activity.startTimestamp,
              end: activity.endTimestamp
            } : undefined,
            assets: activity.largeImageKey || activity.largeImageText || activity.smallImageKey || activity.smallImageText ? {
              large_image: activity.largeImageKey,
              large_text: activity.largeImageText,
              small_image: activity.smallImageKey,
              small_text: activity.smallImageText
            } : undefined,
            buttons: activity.buttons,
            instance: !!activity.instance
          }
        };
        await this.client.request('SET_ACTIVITY', payload);
      } else {
        await this.client.setActivity(activity);
      }
      this.lastPresenceUpdate = Date.now();
    } catch (error) {
      console.error('❌ Failed to update Discord presence:', error);
    } finally {
      this.isUpdatingPresence = false;
      if (this.pendingPresenceRequested) {
        const forceNext = this.pendingPresenceForce;
        this.pendingPresenceRequested = false;
        this.pendingPresenceForce = false;
        this.updatePresence(forceNext);
      }
    }
  }

  /**
   * Clean up Discord RPC connection
   */
  destroy() {
    if (this.coalesceTimer) {
      clearTimeout(this.coalesceTimer);
      this.coalesceTimer = undefined;
    }
    if (this.pendingPresenceTimer) {
      clearTimeout(this.pendingPresenceTimer);
      this.pendingPresenceTimer = undefined;
    }
    if (this.client) {
      this.client.removeAllListeners();
      this.client.clearActivity();
      this.client.destroy();
      this.client = null;
    }
  }
}
