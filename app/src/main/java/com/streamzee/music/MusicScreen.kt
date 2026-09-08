package com.streamzee.music

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun MusicScreen(controller: MediaController?, modifier: Modifier = Modifier,
    section: String = "Home", onSectionChanged: (String) -> Unit = {}) {
    val context = LocalContext.current
    val library = remember { MusicLibrary.get(context) }
    val libraryState by library.state.collectAsState()
    LaunchedEffect(section, libraryState.favorites, libraryState.recent, libraryState.playlists, libraryState.hiddenRecommendations) {
        if (section == "Home") library.refreshRecommendations()
    }
    val playbackError by MusicStatus.error.collectAsState()
    val cacheBytes by MusicStatus.cachedBytes.collectAsState()
    var showLicenses by remember { mutableStateOf(false) }
    var licenseText by remember { mutableStateOf("") }
    val sleepUntil by MusicStatus.sleepUntil.collectAsState()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val tab = section
    var libraryFilter by rememberSaveable { mutableStateOf("Playlists") }
    var selectedPlaylist by rememberSaveable { mutableStateOf<String?>(null) }
    var playlistEditor by remember { mutableStateOf(false) }
    var renamingPlaylist by remember { mutableStateOf<String?>(null) }
    var playlistName by remember { mutableStateOf("") }
    var addTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var deletePlaylist by remember { mutableStateOf<String?>(null) }
    val playlist = libraryState.playlists.firstOrNull { it.id == selectedPlaylist }
    LaunchedEffect(section) { if (section != "Library") selectedPlaylist = null }
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
    if (playlistEditor) AlertDialog(
        onDismissRequest = { playlistEditor = false },
        title = { Text(if (renamingPlaylist == null) "New playlist" else "Rename playlist") },
        text = { OutlinedTextField(playlistName, { playlistName = it.take(60) }, singleLine = true, label = { Text("Playlist name") }) },
        confirmButton = { TextButton(enabled = playlistName.isNotBlank(), onClick = {
            val id = renamingPlaylist
            if (id != null) library.renamePlaylist(id, playlistName) else {
                library.createPlaylist(playlistName)?.let { newId ->
                    addTrack?.let { library.addToPlaylist(newId, it); addTrack = null }
                }
            }
            playlistEditor = false
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { playlistEditor = false }) { Text("Cancel") } },
    )
    if (addTrack != null && !playlistEditor) AlertDialog(
        onDismissRequest = { addTrack = null }, title = { Text("Add to playlist") },
        text = { LazyColumn { items(libraryState.playlists, key = { it.id }) { item ->
            TextButton(onClick = { addTrack?.let { library.addToPlaylist(item.id, it) }; addTrack = null }) { Text(item.name) }
        } } },
        confirmButton = { TextButton(onClick = { playlistName = ""; renamingPlaylist = null; playlistEditor = true }) { Text("New playlist") } },
        dismissButton = { TextButton(onClick = { addTrack = null }) { Text("Cancel") } },
    )
    deletePlaylist?.let { id -> AlertDialog(
        onDismissRequest = { deletePlaylist = null }, title = { Text("Delete playlist?") },
        text = { Text("Your downloaded songs and favorites will stay.") },
        confirmButton = { TextButton(onClick = { library.deletePlaylist(id); selectedPlaylist = null; deletePlaylist = null }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deletePlaylist = null }) { Text("Cancel") } },
    ) }
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
                if (tab == "Home") {
                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    Text(when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" },
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    Text("Sound picked around you", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(tab, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Your soundtrack, anywhere", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (current != null && controller != null && (tab == "Now playing" || tab == "Home")) item {
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
                        val nowTrack = MusicTrack(current.mediaId, current.mediaMetadata.title.toString(),
                            current.mediaMetadata.artist.toString(), current.mediaMetadata.artworkUri?.toString(),
                            controller.duration.coerceAtLeast(0) / 1000)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            IconToggleButton(libraryState.favorites.any { it.id == nowTrack.id }, { library.favorite(nowTrack) }) {
                                Icon(if (libraryState.favorites.any { it.id == nowTrack.id }) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like current song")
                            }
                            IconButton(onClick = { addTrack = nowTrack }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add current song to playlist") }
                            IconButton(onClick = { library.download(nowTrack) }, enabled = libraryState.downloads.none { it.track.id == nowTrack.id }) { Icon(Icons.Default.Download, "Download current song") }
                            TextButton(onClick = { onSectionChanged("Queue") }) { Text("Queue") }
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
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AllInclusive, null,
                                tint = if (libraryState.autoplayEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text("Autoplay", fontWeight = FontWeight.SemiBold)
                                Text("Keep similar music playing", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(libraryState.autoplayEnabled, library::setAutoplay)
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
            if (tab == "Now playing" && current == null) item {
                Text("Your next favorite is waiting.")
                Button(onClick = { onSectionChanged("Search") }) { Text("Find music") }
            }
            if (tab == "Home") {
                val featured = libraryState.recommendations.firstOrNull()?.track
                    ?: libraryState.recent.firstOrNull()
                    ?: libraryState.favorites.firstOrNull()
                item {
                    Box(
                        Modifier.fillMaxWidth().height(190.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiaryContainer,
                                Color(0xFF15101F),
                            )))
                            .clickable(enabled = featured != null) { featured?.let(::play) }
                            .padding(20.dp)
                    ) {
                        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.72f)) {
                            Text("YOUR DAILY SOUND", color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.labelMedium)
                            Text(featured?.title ?: "Start your soundtrack", color = Color.White,
                                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(featured?.artist ?: "Play or like songs to shape your mixes", color = Color.White.copy(alpha = .8f), maxLines = 1)
                        }
                        FilledIconButton(onClick = { featured?.let(::play) }, enabled = featured != null,
                            modifier = Modifier.align(Alignment.BottomEnd).size(56.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)) {
                            Icon(Icons.Default.PlayArrow, "Play daily sound", Modifier.size(30.dp))
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AllInclusive, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("Autoplay", fontWeight = FontWeight.Bold)
                            Text("Similar songs continue when your queue ends", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(libraryState.autoplayEnabled, library::setAutoplay)
                    }
                }
                item { Text("Browse your vibe", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Chill" to Color(0xFF355C7D), "Workout" to Color(0xFFE8505B), "Focus" to Color(0xFF5C4B99),
                        "Jazz" to Color(0xFFB7791F), "Classical" to Color(0xFF476A6F), "Pop" to Color(0xFFC94B8C))) { (mood, color) ->
                        Surface(onClick = { query = "$mood music"; onSectionChanged("Search") }, color = color,
                            shape = RoundedCornerShape(18.dp), modifier = Modifier.width(130.dp).height(76.dp)) {
                            Box(Modifier.padding(14.dp)) { Text(mood, color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.BottomStart)) }
                        }
                    }
                } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ElevatedCard(onClick = { libraryFilter = "Favorites"; onSectionChanged("Library") }, modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(16.dp)) { Icon(Icons.Default.Favorite, null); Text("Liked songs"); Text("${libraryState.favorites.size} songs") }
                        }
                        ElevatedCard(onClick = { onSectionChanged("Downloads") }, modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(16.dp)) { Icon(Icons.Default.Download, null); Text("Offline music"); Text("${libraryState.downloads.size} songs") }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Made for you", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        TextButton(enabled = !libraryState.isLoadingRecommendations, onClick = { library.refreshRecommendations(force = true) }) { Text("Refresh") }
                    }
                    Text("Based on your liked songs, recent listening and playlists", style = MaterialTheme.typography.bodySmall)
                    if (libraryState.isLoadingRecommendations) LinearProgressIndicator(Modifier.fillMaxWidth())
                    libraryState.recommendationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!libraryState.isLoadingRecommendations && libraryState.recommendations.isEmpty()) {
                        Text(if (recommendationSeeds(libraryState).isEmpty()) "Like or play some songs to get personalized picks."
                            else "No new picks yet. Try another artist or refresh.", Modifier.padding(vertical = 8.dp))
                    }
                }
                if (libraryState.recommendations.isNotEmpty()) item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(libraryState.recommendations, key = { it.track.id }) { recommendation ->
                            val track = recommendation.track
                            ElevatedCard(Modifier.width(220.dp)) {
                                Column(Modifier.clickable { play(track) }.padding(12.dp)) {
                                    AsyncImage(track.artwork, null, Modifier.fillMaxWidth().height(140.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                    Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(recommendation.reason, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Row {
                                    IconButton(onClick = { library.favorite(track) }) { Icon(Icons.Default.FavoriteBorder, "Like recommendation") }
                                    IconButton(onClick = { controller?.addMediaItem(track.mediaItem()) }, enabled = controller != null) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue recommendation") }
                                    IconButton(onClick = { library.hideRecommendation(track.id) }) { Icon(Icons.Default.Close, "Not interested") }
                                }
                            }
                        }
                    }
                }
                if (libraryState.hiddenRecommendations.isNotEmpty()) item {
                    TextButton(onClick = library::resetRecommendationFeedback) { Text("Reset hidden recommendations") }
                }
                item { Text("Jump back in", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                if (libraryState.recent.isEmpty()) item { Text("Play a song and it will appear here.") }
                if (libraryState.recent.isNotEmpty()) item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(libraryState.recent.take(12), key = { "recent_${it.id}" }) { track ->
                            Column(Modifier.width(148.dp).clickable { play(track) }) {
                                AsyncImage(track.artwork, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 8.dp))
                                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (tab == "Library") {
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(libraryFilter == "Playlists", { libraryFilter = "Playlists"; selectedPlaylist = null }, { Text("Playlists") })
                    FilterChip(libraryFilter == "Favorites", { libraryFilter = "Favorites"; selectedPlaylist = null }, { Text("Liked songs") })
                } }
                if (libraryFilter == "Playlists") {
                    if (playlist == null) {
                        item { Button(onClick = { playlistName = ""; renamingPlaylist = null; playlistEditor = true }) { Text("Create playlist") } }
                        items(libraryState.playlists, key = { "playlist_${it.id}" }) { item ->
                            ElevatedCard(onClick = { selectedPlaylist = item.id }) {
                                ListItem(headlineContent = { Text(item.name) }, supportingContent = { Text("${item.tracks.size} songs") },
                                    leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) })
                            }
                        }
                    } else {
                        item {
                            TextButton(onClick = { selectedPlaylist = null }) { Text("All playlists") }
                            Text(playlist.name, style = MaterialTheme.typography.headlineSmall)
                            Row {
                                Button(enabled = playlist.tracks.isNotEmpty() && controller != null, onClick = {
                                    controller?.setMediaItems(playlist.tracks.map { it.mediaItem() }); controller?.prepare(); controller?.play()
                                }) { Text("Play all") }
                                TextButton(onClick = { playlistName = playlist.name; renamingPlaylist = playlist.id; playlistEditor = true }) { Text("Rename") }
                                TextButton(onClick = { deletePlaylist = playlist.id }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
            if (tab == "Search") {
                item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search songs and artists") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } }) }
                if (searching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                searchError?.let { message -> item { Text(message); TextButton(onClick = { refresh++ }) { Text("Retry") } } }
                if (!searching && searchError == null && results.isEmpty()) item { Text(if (query.isBlank()) "Find a song to start listening. No TMDB token needed." else "No songs found.") }
            }
            if (tab == "Queue") {
                if (queue.isNotEmpty()) item { Text("${queue.size} songs in your queue", style = MaterialTheme.typography.titleMedium) }
                if (queue.isEmpty()) item { Text("Your queue is empty. Add songs using the queue button.") }
                items(queue.size, key = { it }) { index ->
                    val item = queue[index]
                    ListItem(headlineContent = { Text(item.mediaMetadata.title?.toString().orEmpty(), maxLines = 2) },
                        supportingContent = { Text(item.mediaMetadata.artist?.toString().orEmpty()) },
                        modifier = Modifier.clickable { controller?.seekToDefaultPosition(index); controller?.prepare(); controller?.play() },
                        trailingContent = { Row {
                            IconButton(onClick = { controller?.moveMediaItem(index, index - 1) }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move song up") }
                            IconButton(onClick = { controller?.moveMediaItem(index, index + 1) }, enabled = index < queue.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Move song down") }
                            IconButton(onClick = { controller?.removeMediaItem(index) }) { Icon(Icons.Default.Close, "Remove from queue") }
                        } })
                }
            } else {
                val tracks = when (tab) {
                    "Home" -> emptyList()
                    "Library" -> if (libraryFilter == "Favorites") libraryState.favorites else playlist?.tracks.orEmpty()
                    "Downloads" -> libraryState.downloads.map { it.track }
                    "Search" -> results
                    else -> emptyList()
                }
                if (tracks.isNotEmpty() && (tab == "Downloads" || (tab == "Library" && libraryFilter == "Favorites"))) item {
                    val playable = if (tab == "Downloads") tracks.filter { libraryState.downloadStatus[it.id] == "Saved" } else tracks
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(enabled = controller != null && playable.isNotEmpty(), onClick = {
                            controller?.shuffleModeEnabled = false
                            controller?.setMediaItems(playable.map { it.mediaItem() }); controller?.prepare(); controller?.play()
                        }) { Text("Play all") }
                        OutlinedButton(enabled = controller != null && playable.isNotEmpty(), onClick = {
                            controller?.shuffleModeEnabled = true
                            controller?.setMediaItems(playable.map { it.mediaItem() }); controller?.prepare(); controller?.play()
                        }) { Text("Shuffle") }
                    }
                }
                if (tracks.isEmpty() && (tab == "Downloads" || (tab == "Library" && (playlist != null || libraryFilter == "Favorites")))) item { Text("No ${tab.lowercase(Locale.ROOT)} yet.") }
                items(tracks, key = { it.id }) { track ->
                    Card {
                        ListItem(headlineContent = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(track.artist, maxLines = 1) },
                            leadingContent = { AsyncImage(track.artwork, null, Modifier.size(52.dp)) }, modifier = Modifier.clickable { play(track) })
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconToggleButton(libraryState.favorites.any { it.id == track.id }, { library.favorite(track) }) { Icon(if (libraryState.favorites.any { it.id == track.id }) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite") }
                            IconButton(onClick = { controller?.addMediaItem(track.mediaItem()); library.message("Added to queue") }, enabled = controller != null) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue") }
                            IconButton(onClick = { addTrack = track }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist") }
                            if (tab == "Downloads") {
                                Text(libraryState.downloadStatus[track.id] ?: "Queued", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                if (libraryState.downloadStatus[track.id] == "Saved") IconButton(enabled = !exporting && pendingExport == null, onClick = {
                                    pendingExport = track.id
                                    export.launch(library.exportName(track))
                                }) { Icon(Icons.Default.SaveAlt, "Save audio to files") }
                                IconButton(onClick = { library.removeDownload(track) }) { Icon(Icons.Default.Delete, "Remove download") }
                            } else {
                                IconButton(enabled = controller != null, onClick = {
                                    controller?.let { it.addMediaItem((it.currentMediaItemIndex + 1).coerceIn(0, it.mediaItemCount), track.mediaItem()) }
                                    library.message("Song will play next")
                                }) { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, "Play next") }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { library.download(track) }, enabled = libraryState.downloads.none { it.track.id == track.id }) { Icon(Icons.Default.Download, "Download audio") }
                            }
                        }
                        if (tab == "Library" && playlist != null) Row {
                            TextButton(onClick = { library.movePlaylistTrack(playlist.id, track.id, -1) }, enabled = playlist.tracks.firstOrNull()?.id != track.id) { Text("Move up") }
                            TextButton(onClick = { library.movePlaylistTrack(playlist.id, track.id, 1) }, enabled = playlist.tracks.lastOrNull()?.id != track.id) { Text("Move down") }
                            TextButton(onClick = { library.removeFromPlaylist(playlist.id, track.id) }) { Text("Remove") }
                        }
                    }
                }
            }
            if (tab == "Downloads" || tab == "Library") item {
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
