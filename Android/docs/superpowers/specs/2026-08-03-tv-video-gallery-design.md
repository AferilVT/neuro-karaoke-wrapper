# TV Video Gallery (native) — design

Date: 2026-08-03
Branch: feat/android-tv

## Goal

A new **Videos** tab on Android TV that browses the existing `/api/videos` gallery
(1368 videos) by category and plays them natively with ExoPlayer over Bunny HLS —
no WebView.

## API (already live)

- `GET /api/videos?category=<int>&startIndex=<int>&pageSize=<int>`
  → `{ items: [...], totalCount, page, pageSize }`
- `GET /api/videos/categories` → `[0,1,2]` (ids only; names are client-side)
- Category counts observed: `0` = Karaoke Videos (1365), `2` = Watchalongs (3),
  `1` = empty. `isWatchalong` query param is ignored server-side — filter by `category`.
- Playback: Bunny HLS multi-bitrate, no token —
  `https://vz-26de8a11-dde.b-cdn.net/<cloudflareId>/playlist.m3u8` (verified 200, 480p/720p).

### Video item fields (used)

`id, name, description, cloudflareId, thumbnailUrl, category, views, upvotes,
songId, songTitle, createdBy, creatorAvatarUrl`. (`url` is the Bunny embed — ignored;
we build the HLS URL from `cloudflareId`.)

## Components

### Data — `data/api/VideoApi.kt` (new)
- `data class Video(id, name, description, cloudflareId, thumbnailUrl, category:Int,
  views:Int, upvotes:Int, songId:String?, songTitle:String?, createdBy:String?,
  creatorAvatarUrl:String?)` with `val hlsUrl: String get() = "https://vz-26de8a11-dde.b-cdn.net/$cloudflareId/playlist.m3u8"`.
- `data class VideoPage(val items: List<Video>, val totalCount: Int)`.
- `suspend fun fetchVideos(category: Int, startIndex: Int, pageSize: Int): Result<VideoPage>`
  — same `HttpURLConnection` + `org.json` style as `NeuroKaraokeApi`.
- `object VideoCategories { val ORDER = listOf(2, 0); fun label(id:Int): String }` →
  `2→"Watchalongs"`, `0→"Karaoke Videos"`, else `"Videos"`. (Category 1 skipped: empty.)
- Pure helpers (`parseVideoPage(json)`, `hlsUrl`, `label`) are JVM-testable.

### Dependency
- Add `androidx.media3:media3-exoplayer-hls` (version ref `media3` = 1.3.1) to
  `gradle/libs.versions.toml` + `app/build.gradle.kts`. Required — HLS is not on the
  classpath today (only base exoplayer/ui/session/datasource).

### UI (TV) — `ui/tv/`
- `TvNavBar.kt`: add `TvTab.VIDEOS("Videos", Icons.Default.Movie)` (placed before Account).
- `TvVideosScreen.kt` (new): a `LazyColumn` of category rails in `VideoCategories.ORDER`
  (Watchalongs first, then Karaoke Videos). Each rail = a `LazyRow` of thumbnail cards
  (`thumbnailUrl`, name, "N views"), reusing the Home rail focus/scale idiom. Per-rail
  state holds loaded items + total; **lazy paging**: `pageSize = 30`, load the next page
  when focus/scroll reaches the last few cards (`startIndex += 30` until `items >= total`).
- `TvVideoPlayerScreen.kt` (new): fullscreen overlay. `AndroidView` hosting a Media3
  `PlayerView` (`useController = true` → built-in D-pad transport) bound to a dedicated
  `ExoPlayer` built in the composable, `setMediaItem(MediaItem.fromUri(video.hlsUrl))`,
  `prepare()`, `playWhenReady = true`. Title/uploader text overlay at top. `BackHandler`
  closes it. `DisposableEffect` releases the ExoPlayer on dispose. On open, call
  `playerViewModel.pause()` so app audio doesn't play under the video.

### Wiring — `ui/tv/TvApp.kt`
- New state `var selectedVideo by remember { mutableStateOf<Video?>(null) }`.
- `TvTab.VIDEOS -> TvVideosScreen(onPlay = { selectedVideo = it })`.
- `selectedVideo?.let { TvVideoPlayerScreen(video = it, playerViewModel = playerViewModel, onBack = { selectedVideo = null }) }` rendered as an overlay above content (like `selectedPlaylist`), drawn under the neurolings/settings layers.
- Re-request nav focus when `selectedVideo` returns to null (mirror the `selectedPlaylist` focus-restore effect).

## Data flow

`TvVideosScreen` (per-rail `LaunchedEffect`) → `VideoApi.fetchVideos(category, startIndex, 30)`
→ append to rail state. Card select → `onPlay(video)` → `TvApp.selectedVideo` →
`TvVideoPlayerScreen` builds ExoPlayer with `video.hlsUrl`.

## Error handling

- `fetchVideos` failure → rail shows an inline "Couldn't load" text; other rails unaffected.
- Empty category → rail hidden (Watchalongs still shows its 3).
- ExoPlayer `Player.Listener.onPlayerError` → overlay shows "Playback failed" + Back.

## Testing

- JVM unit tests (`app/src/test/.../VideoApiTest.kt`): `parseVideoPage` on a sample JSON
  fixture (items + totalCount), `hlsUrl` builder, `VideoCategories.label` mapping/order.
- Manual/emulator: Videos tab renders both rails; selecting a Watchalong plays HLS with
  working D-pad transport; Back returns; app audio pauses under playback.

## Non-goals (v1)

Search/sort (site has it — later), comments/upvotes/actions, per-song `videoUrl` linking
(Soul's future field), phone & car surfaces (later slices).
