# TV Library — Collections (design)

Date: 2026-07-31
Branch: feat/android-tv

## Goal

Give the Android TV Library four browsable collections behind a segment bar, all
D-pad-native: **Setlists**, **Public**, **Favourites**, **Your Playlists**.

## Structure

`TvLibraryScreen` = segment bar (top) + one grid (below). Selecting a segment swaps
the grid. All grids are `LazyVerticalGrid`, 5 columns; lazy virtualization is the
"infinite scroll" (each source returns everything in one call — no pagination UI).

| Segment | Source | Card action | Login |
|---|---|---|---|
| Setlists (default) | `PlayerViewModel.uiState.availablePlaylists` | open detail overlay | no |
| Public | `NeuroKaraokeApi.fetchPublicPlaylists()` (~500) | open detail overlay | no |
| Favourites | `FavoritesRepository.favorites` (server-synced) | play song, queue = favourites | yes (sync) |
| Your Playlists | `UserPlaylistRepository.playlists` (server-synced) | open detail overlay | yes (sync) |

Setlists / Public / Your Playlists = playlist-cover grid. Favourites = song-cover
grid (favourites are songs, not playlists — mirrors phone `onSongClick(song, favorites)`).

## Navigation / focus

Segment bar = focusable chip row, switches **on focus** (same idiom as `TvNavBar`).
Up from top grid row lands on chips; Left/Right switches segment; Down re-enters grid.
Default segment = Setlists (always populated, works logged-out).

## Signed-out state

Favourites / Your Playlists show inline sign-in prompt (reuse `TvSignInPrompt` idiom)
instead of empty grid. Setlists / Public never gate.

## Components

New / changed:
- `TvLibrarySegment` enum (labels) + `TvLibrarySegmentBar` composable (chip row).
- `TvLibraryScreen` refactored to host segment state + switch content.
- Extract `TvPlaylistGrid` (current 5-col card grid) — reused by Setlists / Public / Your Playlists.
- New `TvSongGrid` (5-col song covers, play-on-select) for Favourites.
- Data hoisted into `TvApp` so state survives tab switches: `favoritesRepo` +
  `userPlaylistRepo` (remembered w/ context, flows collected) and a remembered
  `publicPlaylists` filled by a `LaunchedEffect`. When `accessToken != null`, call
  `syncFromServer` once for both repos.

## Integration detail

`TvDetailScreen` loads songs via `SongRepository.getPlaylistSongs` (public endpoint) —
works for Setlists + Public, but private user playlists need auth. Add optional
`songLoader: (suspend (Playlist) -> List<Song>)?` param (defaults to current behavior);
Your Playlists passes a loader backed by `UserPlaylistRepository.loadPlaylistSongs`.

## Out of scope (v1)

Per-segment search/sort (Search tab covers song search); create/edit playlists or
toggle favourites from TV (browse + play only — creation stays on phone/web);
Home-screen changes.
