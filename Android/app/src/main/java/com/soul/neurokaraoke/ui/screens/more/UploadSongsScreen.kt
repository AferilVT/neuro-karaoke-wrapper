package com.soul.neurokaraoke.ui.screens.more

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.soul.neurokaraoke.R
import com.soul.neurokaraoke.data.api.ProfileApi
import com.soul.neurokaraoke.data.api.UploadHttpException
import com.soul.neurokaraoke.data.api.UserSongsApi
import com.soul.neurokaraoke.data.model.Singer
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.repository.LocalMusicStore
import com.soul.neurokaraoke.data.repository.LocalSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun UploadSongsScreen(
    onBackClick: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    modifier: Modifier = Modifier,
    /** Bearer token when signed in; enables uploading songs to the user's account. */
    accessToken: String? = null
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val localSongs = remember { mutableStateListOf<LocalSong>() }
    var editing by remember { mutableStateOf<LocalSong?>(null) }
    val uploading = remember { mutableStateListOf<String>() }

    fun replaceSong(updated: LocalSong) {
        val i = localSongs.indexOfFirst { it.fileName == updated.fileName }
        if (i >= 0) localSongs[i] = updated
    }

    fun startUpload(song: LocalSong) {
        val token = accessToken ?: return
        if (song.fileName in uploading) return
        uploading += song.fileName
        scope.launch {
            val result = uploadLocalSong(appContext, token, song)
            uploading -= song.fileName
            result.onSuccess { updated ->
                replaceSong(updated)
                toast(appContext, appContext.getString(R.string.upload_toast_uploaded, updated.title))
            }.onFailure { e ->
                toast(appContext, uploadErrorMessage(appContext, e))
            }
        }
    }

    // Load previously imported songs from internal storage
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { LocalMusicStore.loadAll(appContext) }
        localSongs.clear()
        localSongs.addAll(loaded)
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                uris.mapNotNull { LocalMusicStore.import(appContext, it) }
            }
            localSongs.addAll(imported)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.upload_content_description_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(R.string.upload_header_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )

            if (localSongs.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.upload_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.upload_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { filePicker.launch(arrayOf("audio/mpeg")) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.upload_button_import))
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Play / Shuffle buttons
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val songs = localSongs.map { it.toSong() }
                                    songs.firstOrNull()?.let { onPlaySong(it, songs) }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.upload_button_play), fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val songs = localSongs.map { it.toSong() }.shuffled()
                                    songs.firstOrNull()?.let { onPlaySong(it, songs) }
                                },
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.upload_button_shuffle), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.upload_label_song_count, localSongs.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    itemsIndexed(localSongs, key = { _, s -> s.fileName }) { index, song ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val songs = localSongs.map { it.toSong() }
                                    onPlaySong(songs[index], songs)
                                }
                                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                        ) {
                            CoverThumb(song.coverPath, size = 44.dp)

                            Spacer(modifier = Modifier.width(12.dp))

                            // Song info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (song.coverArtists.isBlank()) song.artist
                                    else "${song.artist} • ${song.coverArtists}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            when {
                                song.fileName in uploading -> CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                song.serverId != null -> Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = stringResource(R.string.upload_edit_uploaded),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .size(18.dp)
                                )
                            }

                            IconButton(onClick = { editing = song }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.upload_content_description_edit),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    localSongs.remove(song)
                                    scope.launch(Dispatchers.IO) {
                                        LocalMusicStore.delete(appContext, song.fileName)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.upload_content_description_remove),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(88.dp)) }
                }
            }
        }

        // FAB for adding songs (only show when list is non-empty)
        if (localSongs.isNotEmpty()) {
            FloatingActionButton(
                onClick = { filePicker.launch(arrayOf("audio/mpeg")) },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.upload_content_description_import))
            }
        }
    }

    editing?.let { song ->
        EditLocalSongDialog(
            song = song,
            canUpload = accessToken != null,
            isUploading = song.fileName in uploading,
            onDismiss = { editing = null },
            onSave = { title, artist, coverArtists, newCover, removeCover, upload ->
                editing = null
                scope.launch {
                    val updated = withContext(Dispatchers.IO) {
                        LocalMusicStore.saveDetails(
                            appContext, song.fileName, title, artist, coverArtists, newCover, removeCover
                        )
                    } ?: return@launch
                    replaceSong(updated)

                    val token = accessToken
                    val serverId = updated.serverId
                    if (upload && serverId == null) {
                        startUpload(updated)
                    } else if (token != null && serverId != null &&
                        (updated.title != song.title || updated.artist != song.artist)
                    ) {
                        // Keep the uploaded copy in step. Only title and artist exist server-side.
                        UserSongsApi.update(token, serverId, updated.title, updated.artist)
                            .onFailure { e ->
                                toast(
                                    appContext,
                                    appContext.getString(
                                        R.string.upload_toast_sync_failed,
                                        uploadErrorMessage(appContext, e)
                                    )
                                )
                            }
                    }
                }
            }
        )
    }
}

@Composable
private fun CoverThumb(path: String?, size: Dp, model: Any? = null) {
    val shape = RoundedCornerShape(6.dp)
    val data = model ?: path?.let { File(it) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (data != null) {
            AsyncImage(
                model = data,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(size / 2)
            )
        }
    }
}

@Composable
private fun EditLocalSongDialog(
    song: LocalSong,
    canUpload: Boolean,
    isUploading: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        artist: String,
        coverArtists: String,
        newCover: Uri?,
        removeCover: Boolean,
        upload: Boolean
    ) -> Unit
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var coverArtists by remember { mutableStateOf(song.coverArtists) }
    var newCover by remember { mutableStateOf<Uri?>(null) }
    var removeCover by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            newCover = uri
            removeCover = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.upload_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverThumb(
                        path = if (removeCover) null else song.coverPath,
                        size = 72.dp,
                        model = newCover
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        TextButton(onClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Text(stringResource(R.string.upload_edit_change_cover))
                        }
                        val hasCustomCover = song.coverPath != null &&
                            !song.coverPath.endsWith(LocalMusicStore.EMBEDDED_COVER_SUFFIX)
                        if (newCover != null || (hasCustomCover && !removeCover)) {
                            TextButton(onClick = {
                                newCover = null
                                removeCover = hasCustomCover
                            }) {
                                Text(stringResource(R.string.upload_edit_reset_cover))
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.upload_edit_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(stringResource(R.string.upload_edit_field_artist)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = coverArtists,
                    onValueChange = { coverArtists = it },
                    label = { Text(stringResource(R.string.upload_edit_field_cover_artist)) },
                    placeholder = { Text("Neuro-sama") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                when {
                    song.serverId != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.upload_edit_uploaded),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    isUploading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.upload_status_uploading),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    canUpload -> OutlinedButton(
                        onClick = { onSave(title, artist, coverArtists, newCover, removeCover, true) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.upload_edit_upload_button))
                    }
                    else -> Text(
                        text = stringResource(R.string.upload_edit_sign_in_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canUpload || song.serverId != null) {
                    Text(
                        text = stringResource(R.string.upload_edit_upload_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, artist, coverArtists, newCover, removeCover, false) }) {
                Text(stringResource(R.string.upload_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.upload_edit_cancel))
            }
        }
    )
}

/**
 * Uploads [song]'s audio to the user's account and records the new song id locally.
 * The upload response has no documented body, so the id is found by diffing
 * `GET /api/user/songs` before and after.
 */
private suspend fun uploadLocalSong(context: Context, token: String, song: LocalSong): Result<LocalSong> =
    runCatching {
        val file = LocalMusicStore.audioFile(context, song.fileName)
        if (!file.exists()) error(context.getString(R.string.upload_toast_missing_file))

        ProfileApi.fetchUploadLimits(token).getOrNull()?.let { limits ->
            if (limits.maxSongs > 0 && limits.currentSongCount >= limits.maxSongs) {
                error(context.getString(R.string.upload_toast_quota_songs, limits.maxSongs))
            }
            if (limits.maxStorageBytes > 0 &&
                limits.usedStorageBytes + file.length() > limits.maxStorageBytes
            ) {
                error(context.getString(R.string.upload_toast_quota_storage))
            }
        }

        val before = UserSongsApi.listUploads(token).getOrNull()?.map { it.id }?.toSet()
        val idFromResponse = UserSongsApi.upload(token, file, song.title, song.artist).getOrThrow()
        val serverId = idFromResponse ?: before?.let { ids ->
            UserSongsApi.listUploads(token).getOrNull()
                ?.let { UserSongsApi.findNewUpload(ids, it, song.title) }?.id
        }

        if (serverId == null) {
            // Uploaded, but we can't tell which entry it is; later edits just won't sync.
            song
        } else {
            withContext(Dispatchers.IO) {
                LocalMusicStore.setServerId(context, song.fileName, serverId)
            } ?: song
        }
    }

private fun uploadErrorMessage(context: Context, e: Throwable): String = when {
    e is UploadHttpException && e.code == 401 -> context.getString(R.string.upload_toast_session_expired)
    e is UploadHttpException && e.code == 413 -> context.getString(R.string.upload_toast_too_large)
    else -> context.getString(R.string.upload_toast_failed, e.message ?: e.javaClass.simpleName)
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

private fun LocalSong.toSong(): Song {
    return Song(
        id = fileName,
        title = title,
        artist = artist,
        coverUrl = coverPath?.let { Uri.fromFile(File(it)).toString() } ?: "",
        audioUrl = uri.toString(),
        singer = Singer.fromCoverArtists(coverArtists),
        coverArtists = coverArtists
    )
}
