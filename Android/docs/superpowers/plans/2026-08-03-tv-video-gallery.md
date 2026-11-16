# TV Video Gallery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a native TV Videos tab that browses `/api/videos` by category and plays videos with ExoPlayer over Bunny HLS.

**Architecture:** New `VideoApi` (HttpURLConnection + org.json, mirroring `NeuroKaraokeApi`) exposes paged videos per category. A new `TvTab.VIDEOS` opens `TvVideosScreen` (category rails with lazy paging). Selecting a card opens a fullscreen `TvVideoPlayerScreen` overlay hosting a Media3 `PlayerView` bound to a dedicated `ExoPlayer` playing the derived HLS URL.

**Tech Stack:** Kotlin, Jetpack Compose (androidx.tv), Media3 ExoPlayer + HLS, Coil.

## Global Constraints

- API base for videos: `https://api.neurokaraoke.com`. Endpoint: `GET /api/videos?category=<int>&startIndex=<int>&pageSize=<int>` → `{ items:[...], totalCount, page, pageSize }`.
- HLS URL: `https://vz-26de8a11-dde.b-cdn.net/<cloudflareId>/playlist.m3u8` (no token).
- Categories: `2 → "Watchalongs"`, `0 → "Karaoke Videos"`; display order `[2, 0]`; category `1` is empty → skip.
- Media3 version ref `media3` = `1.3.1` (already in `gradle/libs.versions.toml`).
- Page size for lazy paging = 30.
- Follow existing TV idioms: `tvFocusScale`/`tvCoverFocus` (in `ui/tv/TvFocus.kt`), rail style from `TvHomeScreen.kt`, overlay pattern from `TvApp.kt` (`selectedPlaylist`).

---

### Task 1: VideoApi (data + parsing) with unit tests

**Files:**
- Create: `app/src/main/java/com/soul/neurokaraoke/data/api/VideoApi.kt`
- Test: `app/src/test/java/com/soul/neurokaraoke/VideoApiTest.kt`

**Interfaces:**
- Produces:
  - `data class Video(val id:String, val name:String, val description:String, val cloudflareId:String, val thumbnailUrl:String, val category:Int, val views:Int, val upvotes:Int, val songId:String?, val songTitle:String?, val createdBy:String?, val creatorAvatarUrl:String?)` with `val hlsUrl: String get() = "https://vz-26de8a11-dde.b-cdn.net/$cloudflareId/playlist.m3u8"`
  - `data class VideoPage(val items: List<Video>, val totalCount: Int)`
  - `object VideoCategories { val ORDER = listOf(2, 0); fun label(id: Int): String }`
  - `class VideoApi { fun parseVideoPage(json: String): VideoPage; suspend fun fetchVideos(category: Int, startIndex: Int, pageSize: Int): Result<VideoPage> }`

- [ ] **Step 1: Write failing tests** `VideoApiTest.kt`:

```kotlin
package com.soul.neurokaraoke
import com.soul.neurokaraoke.data.api.VideoApi
import com.soul.neurokaraoke.data.api.VideoCategories
import org.junit.Assert.*
import org.junit.Test

class VideoApiTest {
    private val sample = """
      {"items":[
        {"id":"A1","name":"Shrek","description":"d","cloudflareId":"guid-1",
         "thumbnailUrl":"http://t/1.jpg","category":2,"views":694,"upvotes":24,
         "songId":null,"songTitle":null,"createdBy":"flashfire8","creatorAvatarUrl":null}
      ],"totalCount":1368,"page":1,"pageSize":1}
    """.trimIndent()

    @Test fun parses_page() {
        val page = VideoApi().parseVideoPage(sample)
        assertEquals(1368, page.totalCount)
        assertEquals(1, page.items.size)
        val v = page.items[0]
        assertEquals("Shrek", v.name)
        assertEquals(2, v.category)
        assertNull(v.songId)
        assertEquals("flashfire8", v.createdBy)
    }
    @Test fun builds_hls_url() {
        val v = VideoApi().parseVideoPage(sample).items[0]
        assertEquals("https://vz-26de8a11-dde.b-cdn.net/guid-1/playlist.m3u8", v.hlsUrl)
    }
    @Test fun category_labels_and_order() {
        assertEquals(listOf(2, 0), VideoCategories.ORDER)
        assertEquals("Watchalongs", VideoCategories.label(2))
        assertEquals("Karaoke Videos", VideoCategories.label(0))
        assertEquals("Videos", VideoCategories.label(1))
    }
}
```

- [ ] **Step 2: Run, verify fail** — `./gradlew :app:testDebugUnitTest --tests "com.soul.neurokaraoke.VideoApiTest"` → FAIL (unresolved).

- [ ] **Step 3: Implement `VideoApi.kt`** — `Video`/`VideoPage`/`VideoCategories` as above. `parseVideoPage` uses `org.json.JSONObject`: read `totalCount`, iterate `items` with `optString`/`optInt`, mapping `null` JSON to Kotlin null via `if (isNull("songId")) null else optString("songId")`. `fetchVideos` mirrors `NeuroKaraokeApi.fetchPublicPlaylists` (open `URL("$API_URL/api/videos?category=$category&startIndex=$startIndex&pageSize=$pageSize")`, `HttpURLConnection` GET, 10s timeouts, `Dispatchers.IO`, `HTTP_OK` → `Result.success(parseVideoPage(body))`, else `Result.failure`). `API_URL = "https://api.neurokaraoke.com"`.

- [ ] **Step 4: Run, verify pass.**

- [ ] **Step 5: Commit** `git add app/src/main/java/.../data/api/VideoApi.kt app/src/test/java/.../VideoApiTest.kt && git commit -m "feat(tv): VideoApi for /api/videos (paged, HLS url, categories)"`

---

### Task 2: Videos tab + TvVideosScreen (rails + lazy paging)

**Files:**
- Modify: `app/src/main/java/com/soul/neurokaraoke/ui/tv/TvNavBar.kt` (add enum entry)
- Create: `app/src/main/java/com/soul/neurokaraoke/ui/tv/TvVideosScreen.kt`
- Modify: `app/src/main/java/com/soul/neurokaraoke/ui/tv/TvApp.kt` (tab branch + state)

**Interfaces:**
- Consumes: `VideoApi`, `Video`, `VideoPage`, `VideoCategories` (Task 1).
- Produces: `@Composable fun TvVideosScreen(onPlay: (Video) -> Unit)`; `TvTab.VIDEOS`.

- [ ] **Step 1: Add nav entry** in `TvNavBar.kt` `enum class TvTab`, before `ACCOUNT`:
```kotlin
VIDEOS("Videos", Icons.Default.Movie),
```
Add import `androidx.compose.material.icons.filled.Movie`.

- [ ] **Step 2: Implement `TvVideosScreen.kt`** — `LazyColumn` over `VideoCategories.ORDER`; one `VideoRail(category)` per id. `VideoRail` holds `remember { mutableStateListOf<Video>() }`, `total` and `startIndex` state; `LaunchedEffect(Unit)` loads first page via `VideoApi().fetchVideos(category, 0, 30)` (skip rail if first page empty). Card = `Column` (Coil `AsyncImage` of `thumbnailUrl`, 16:9 `aspectRatio(16f/9f)`, `tvCoverFocus`; name; "$views views"), focusable + `onKeyEvent` Enter/Center → `onPlay(video)`, `tvFocusScale`. Lazy paging: in `items(...)`, when the rendered index is within 5 of the current list end and `list.size < total`, fire a `LaunchedEffect(list.size)` that fetches the next page (`startIndex = list.size`) and appends. Rail header = `VideoCategories.label(category)` (reuse the `TvRail` title style). On fetch failure, show an inline "Couldn't load" Text for that rail.

- [ ] **Step 3: Wire into `TvApp.kt`** — add `import com.soul.neurokaraoke.data.api.Video`; add state `var selectedVideo by remember { mutableStateOf<Video?>(null) }`; add branch:
```kotlin
TvTab.VIDEOS -> TvVideosScreen(onPlay = { selectedVideo = it })
```
(Overlay rendering added in Task 3; for now selecting sets state with no visible overlay.)

- [ ] **Step 4: Build + emulator check** — `./gradlew :app:assembleDebug`, install, open Videos tab: two rails ("Watchalongs" with 3 cards, "Karaoke Videos" with thumbnails), D-pad scroll of Karaoke loads more.

- [ ] **Step 5: Commit** `feat(tv): Videos tab + TvVideosScreen rails with lazy paging`

---

### Task 3: Native HLS player overlay

**Files:**
- Modify: `gradle/libs.versions.toml` (add hls lib alias)
- Modify: `app/build.gradle.kts` (add hls dependency)
- Create: `app/src/main/java/com/soul/neurokaraoke/ui/tv/TvVideoPlayerScreen.kt`
- Modify: `app/src/main/java/com/soul/neurokaraoke/ui/tv/TvApp.kt` (render overlay + focus restore)

**Interfaces:**
- Consumes: `Video` (Task 1); `selectedVideo` state (Task 2); `PlayerViewModel.pause()`.
- Produces: `@Composable fun TvVideoPlayerScreen(video: Video, playerViewModel: PlayerViewModel, onBack: () -> Unit)`.

- [ ] **Step 1: Add HLS dependency.** In `gradle/libs.versions.toml` under Media3 aliases:
```toml
androidx-media3-exoplayer-hls = { group = "androidx.media3", name = "media3-exoplayer-hls", version.ref = "media3" }
```
In `app/build.gradle.kts` next to the other media3 lines:
```kotlin
implementation(libs.androidx.media3.exoplayer.hls)
```

- [ ] **Step 2: Implement `TvVideoPlayerScreen.kt`:**
```kotlin
@Composable
fun TvVideoPlayerScreen(video: Video, playerViewModel: PlayerViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onBack() }
    LaunchedEffect(Unit) { playerViewModel.pause() }          // don't play app audio under video
    val exo = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(video.hlsUrl))
            prepare(); playWhenReady = true
        }
    }
    var error by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val l = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) { error = true }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l); exo.release() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { androidx.media3.ui.PlayerView(it).apply { player = exo; useController = true } },
            modifier = Modifier.fillMaxSize()
        )
        Text(video.name, color = Color.White, style = MaterialTheme.typography.titleMedium,
             modifier = Modifier.align(Alignment.TopStart).padding(24.dp))
        if (error) Text("Playback failed — press Back", color = Color.White,
             modifier = Modifier.align(Alignment.Center))
    }
}
```
Imports: `androidx.activity.compose.BackHandler`, `androidx.compose.ui.viewinterop.AndroidView`, `androidx.compose.ui.platform.LocalContext`, layout/`graphics.Color`/`material3` as needed.

- [ ] **Step 3: Render overlay in `TvApp.kt`** — after the `selectedPlaylist` overlay block, add:
```kotlin
selectedVideo?.let { v ->
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TvVideoPlayerScreen(video = v, playerViewModel = playerViewModel, onBack = { selectedVideo = null })
    }
}
```
Extend the nav-focus-restore effect to also key on `selectedVideo` (re-request `navFocusRequester` when it returns to null), and include `selectedVideo == null` in the content `focusProperties { canFocus = ... }` guard so the player owns focus while open.

- [ ] **Step 4: Build + emulator check** — open Videos → a Watchalong → HLS plays fullscreen, D-pad play/pause/seek works, app audio paused; Back closes and returns to the rail.

- [ ] **Step 5: Commit** `feat(tv): native HLS video player overlay (Media3 ExoPlayer)`

---

## Self-Review

- **Spec coverage:** VideoApi + model + hlsUrl + categories (Task 1); Videos tab + rails + lazy paging + error state (Task 2); media3-hls dep + PlayerView/ExoPlayer overlay + pause-audio + focus (Task 3). Non-goals (search/sort, upvotes, songId linking, phone/car) omitted intentionally.
- **Placeholder scan:** none — each code step is concrete.
- **Type consistency:** `Video`, `VideoPage`, `VideoCategories.ORDER/label`, `fetchVideos(category,startIndex,pageSize)`, `selectedVideo`, `TvVideoPlayerScreen(video,playerViewModel,onBack)` consistent across tasks. `PlayerViewModel.pause()` exists (used elsewhere in TV).
