# NeuroKaraoke iOS Port

This folder contains the SwiftUI iOS port of the Android `neuro-karaoke-wrapper` app.

## Current Status

The iOS app already includes:

- [x] Home, Search, Explore, Library, Radio, Soundbites, Setlists, and Artists screens
- [x] Song playback, mini player, full player, queue view, repeat/shuffle, and sleep timer
- [x] Lyrics loading and synced lyric highlighting
- [x] Favorites, downloads, and local user playlists
- [x] Discord sign-in and library sync
- [x] Background audio session and now-playing / remote command integration
- [x] Explore/community playlists and About screen
- [x] Audio Effects UI with persisted equalizer and bass boost state
- [x] Hybrid playback backend for local/normal playback
- [x] Songs and soundbites use `AVAudioEngine`
- [x] Radio still uses `AVPlayer`

## Remaining Android Features To Migrate


### 2. Auth Parity // COMPLETE

Current iOS auth uses Discord PKCE and deep-link callback handling.

- [x] Embedded WebView auth fallback
- [x] JWT parsing / save flow from WebView login path
- [ ] Setup-time login-page warmup / preload behavior

### 3. Setup Flow Parity // LOW PRIORITY

Current iOS startup is faster and background-loads the catalog, but it is still simpler than Android.

Still missing:

- [ ] Android-style multi-step setup UX
- [ ] More explicit setup diagnostics / progress stages
- [ ] Login warmup integration during setup

### 4. Library / Download Parity // MEDIUM PRIORITY

- [x] Bulk playlist download actions
- [x] More Android-style download management polish
- [x] Stronger cache invalidation / staleness handling for downloaded and cached media

### 5. Data / Cache Parity // MEDIUM PRIORITY

Android has more specialized cache handling through things like:

- `PlaylistCatalog`
- `SongCache`
- `LyricsCache`
- setlist staleness refresh logic

iOS still needs:

- [ ] Fuller stale-cache detection
- [ ] More explicit catalog versioning / refresh strategy
- [ ] Parity review for all cached playback/catalog/auth data
