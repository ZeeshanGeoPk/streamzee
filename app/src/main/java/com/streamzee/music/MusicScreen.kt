package com.streamzee.music

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import java.util.Locale

@Composable
fun rememberMusicController(): MediaController? {
    val context = LocalContext.current.applicationContext
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        var disposed = false
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, MusicService::class.java))).buildAsync()
        future.addListener({
            if (!disposed) {
                controller = runCatching { future.get() }.onFailure {
                    MusicStatus.mutable.value = "Music service could not connect. Restart the app to retry."
                }.getOrNull()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; controller = null; MediaController.releaseFuture(future) }
    }
    return controller
}

@Composable
private fun playbackRevision(controller: MediaController?): Int {
    var revision by remember(controller) { mutableIntStateOf(0) }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { revision++ }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }
    LaunchedEffect(controller) { while (controller != null) { delay(1000); revision++ } }
    return revision
}

@Composable
fun MusicMiniPlayer(controller: MediaController?, onOpen: () -> Unit) {
    val revision = playbackRevision(controller)
    val track = remember(revision, controller) { controller?.currentMediaItem }
    if (track != null && controller != null) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(track.mediaMetadata.artworkUri, null, Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.mediaMetadata.title?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(track.mediaMetadata.artist?.toString().orEmpty(), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { togglePlayback(controller) }) {
                    Icon(if (controller.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause")
                }
                IconButton(onClick = { controller.seekToNextMediaItem() }, enabled = controller.hasNextMediaItem()) { Icon(Icons.Default.SkipNext, "Next song") }
            }
        }
    }
}

private fun togglePlayback(controller: MediaController) {
    if (controller.isPlaying) controller.pause() else {
        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
        if (controller.playbackState == Player.STATE_ENDED) controller.seekToDefaultPosition()
        controller.play()
    }
}

@Composable
fun MusicScreen(controller: MediaController?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val library = remember { MusicLibrary.get(context) }
    val libraryState by library.state.collectAsState()
    val playbackError by MusicStatus.error.collectAsState()
    val cacheBytes by MusicStatus.cachedBytes.collectAsState()
    var showLicenses by remember { mutableStateOf(false) }
    var licenseText by remember { mutableStateOf("") }
    val sleepUntil by MusicStatus.sleepUntil.collectAsState()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("Search") }
    var results by remember { mutableStateOf(emptyList<MusicTrack>()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val revision = playbackRevision(controller)
    val current = remember(revision, controller) { controller?.currentMediaItem }
    val queue = remember(revision, controller) { controller?.let { p -> (0 until p.mediaItemCount).map { p.getMediaItemAt(it) } }.orEmpty() }
    var sliderPosition by remember(current?.mediaId) { mutableStateOf<Float?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/*")) { destination ->
        val id = pendingExport
        pendingExport = null
        if (destination != null && id != null) {
            exporting = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val source = requireNotNull(library.localUri(id)) { "The download is no longer available" }
                        context.contentResolver.openInputStream(source).use { input ->
                            requireNotNull(input)
                            context.contentResolver.openOutputStream(destination, "wt").use { output ->
                                requireNotNull(output)
                                val buffer = ByteArray(128 * 1024)
                                while (true) {
                                    ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                    }
                    library.message("Audio saved to your selected folder")
                } catch (e: Exception) {
                    withContext(NonCancellable + Dispatchers.IO) { runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, destination) } }
                    if (e is CancellationException) throw e
                    library.message("Could not save audio: ${e.message}")
                } finally { exporting = false }
            }
        }
    }
    LaunchedEffect(query, refresh) {
        results = emptyList()
        searchError = null
        searching = query.isNotBlank()
        if (query.isNotBlank()) {
            delay(450)
            try { results = withContext(Dispatchers.IO) { YouTubeMusicSource.search(query.trim()) } }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                searchError = "Music search failed. Try again or check your connection."
            }
            searching = false
        }
    }
    LaunchedEffect(showLicenses) {
        if (showLicenses) licenseText = withContext(Dispatchers.IO) {
            context.assets.open("licenses/NewPipeExtractor-GPL-3.0.txt").bufferedReader().use { it.readText() }
        }
    }
    if (showLicenses) AlertDialog(
        onDismissRequest = { showLicenses = false }, title = { Text("NewPipe Extractor · GPL v3+") },
        text = { Text(licenseText, Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("Close") } },
    )
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(libraryState.message) { libraryState.message?.let { snackbar.showSnackbar(it); library.message(null) } }
    fun play(track: MusicTrack) {
        if (controller == null) { library.message("Music player is connecting. Please try again."); return }
        controller.setMediaItem(track.mediaItem())
        controller.prepare()
        controller.play()
    }
    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Music", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Your soundtrack, anywhere", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (current != null && controller != null) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(current.mediaMetadata.artworkUri, null, Modifier.size(72.dp))
                            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                Text(current.mediaMetadata.title?.toString().orEmpty(), fontWeight = FontWeight.Bold, maxLines = 2)
                                Text(current.mediaMetadata.artist?.toString().orEmpty(), style = MaterialTheme.typography.bodySmall)
                                if (controller.playbackState == Player.STATE_BUFFERING) Text("Loading audio…", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        val duration = controller.duration.coerceAtLeast(1L)
                        Slider(value = sliderPosition ?: controller.currentPosition.toFloat().coerceIn(0f, duration.toFloat()),
                            onValueChange = { sliderPosition = it }, valueRange = 0f..duration.toFloat(),
                            onValueChangeFinished = { sliderPosition?.let { controller.seekTo(it.toLong()) }; sliderPosition = null },
                            enabled = controller.duration > 0)
                        Text("${musicTime(controller.currentPosition)} / ${musicTime(controller.duration)}", style = MaterialTheme.typography.labelSmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconToggleButton(checked = controller.shuffleModeEnabled, onCheckedChange = { controller.shuffleModeEnabled = it }) { Icon(Icons.Default.Shuffle, "Shuffle") }
                            IconButton(onClick = { controller.seekToPreviousMediaItem() }, enabled = controller.hasPreviousMediaItem()) { Icon(Icons.Default.SkipPrevious, "Previous song") }
                            FilledIconButton(onClick = { togglePlayback(controller) }) { Icon(if (controller.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause") }
                            IconButton(onClick = { controller.seekToNextMediaItem() }, enabled = controller.hasNextMediaItem()) { Icon(Icons.Default.SkipNext, "Next song") }
                            IconButton(onClick = { controller.repeatMode = (controller.repeatMode + 1) % 3 }) {
                                Icon(if (controller.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, "Repeat: ${controller.repeatMode}", tint = if (controller.repeatMode == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { TextButton(onClick = {
                                val speed = controller.playbackParameters.speed
                                controller.setPlaybackSpeed(if (speed >= 1.5f) 0.75f else speed + 0.25f)
                            }) { Text("${controller.playbackParameters.speed}× speed") } }
                            items(listOf(15, 30, 60)) { minutes -> TextButton(onClick = {
                                controller.sendCustomCommand(SessionCommand("sleep", Bundle.EMPTY), Bundle().apply { putInt("minutes", minutes) })
                            }) { Text("Sleep ${minutes}m") } }
                            if (sleepUntil != null) item { TextButton(onClick = {
                                controller.sendCustomCommand(SessionCommand("sleep", Bundle.EMPTY), Bundle().apply { putInt("minutes", 0) })
                            }) { Text("Cancel timer") } }
                        }
                        if (sleepUntil != null) Text("Sleep timer active", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            playbackError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Search", "Favorites", "Downloads", "Queue")) { name -> FilterChip(tab == name, { tab = name }, { Text(name) }) }
            } }
            if (tab == "Search") {
                item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search songs and artists") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } }) }
                if (searching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                searchError?.let { message -> item { Text(message); TextButton(onClick = { refresh++ }) { Text("Retry") } } }
                if (!searching && searchError == null && results.isEmpty()) item { Text(if (query.isBlank()) "Find a song to start listening. No TMDB token needed." else "No songs found.") }
            }
            if (tab == "Queue") {
                if (queue.isEmpty()) item { Text("Your queue is empty. Add songs using the queue button.") }
                items(queue.size, key = { it }) { index ->
                    val item = queue[index]
                    ListItem(headlineContent = { Text(item.mediaMetadata.title?.toString().orEmpty(), maxLines = 2) },
                        supportingContent = { Text(item.mediaMetadata.artist?.toString().orEmpty()) },
                        modifier = Modifier.clickable { controller?.seekToDefaultPosition(index); controller?.prepare(); controller?.play() },
                        trailingContent = { IconButton(onClick = { controller?.removeMediaItem(index) }) { Icon(Icons.Default.Close, "Remove from queue") } })
                }
            } else {
                val tracks = when (tab) { "Favorites" -> libraryState.favorites; "Downloads" -> libraryState.downloads.map { it.track }; else -> results }
                if (tracks.isEmpty() && tab != "Search") item { Text("No ${tab.lowercase(Locale.ROOT)} yet.") }
                items(tracks, key = { it.id }) { track ->
                    Card {
                        ListItem(headlineContent = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(track.artist, maxLines = 1) },
                            leadingContent = { AsyncImage(track.artwork, null, Modifier.size(52.dp)) }, modifier = Modifier.clickable { play(track) })
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconToggleButton(libraryState.favorites.any { it.id == track.id }, { library.favorite(track) }) { Icon(if (libraryState.favorites.any { it.id == track.id }) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite") }
                            IconButton(onClick = { controller?.addMediaItem(track.mediaItem()); library.message("Added to queue") }, enabled = controller != null) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue") }
                            if (tab == "Downloads") {
                                Text(libraryState.downloadStatus[track.id] ?: "Queued", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                if (libraryState.downloadStatus[track.id] == "Saved") IconButton(enabled = !exporting && pendingExport == null, onClick = {
                                    pendingExport = track.id
                                    export.launch(library.exportName(track))
                                }) { Icon(Icons.Default.SaveAlt, "Save audio to files") }
                                IconButton(onClick = { library.removeDownload(track) }) { Icon(Icons.Default.Delete, "Remove download") }
                            } else {
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { library.download(track) }, enabled = libraryState.downloads.none { it.track.id == track.id }) { Icon(Icons.Default.Download, "Download audio") }
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Wi-Fi downloads only", Modifier.weight(1f))
                    Switch(libraryState.wifiOnly, library::setWifiOnly)
                }
                Text("Applies to new downloads", style = MaterialTheme.typography.labelSmall)
                Text("Playback cache: ${cacheBytes / (1024 * 1024)} / 256 MB. Downloads are kept until you remove them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { controller?.sendCustomCommand(SessionCommand("clear_cache", Bundle.EMPTY), Bundle.EMPTY) }, enabled = controller != null) { Text("Clear playback cache") }
                TextButton(onClick = { showLicenses = true }) { Text("Open-source licenses") }
            }
            if (exporting) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Saving audio… Keep this screen open.") }
        }
    }
}
internal fun musicTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
}
