package com.streamzee.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.streamzee.data.exportOfflineVideo
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamzee.data.DownloadItem
import com.streamzee.data.DownloadMediaType
import com.streamzee.data.DownloadQuality
import com.streamzee.data.DownloadSettings
import com.streamzee.data.DownloadStatus
import com.streamzee.viewmodel.MainUiState
import java.util.Locale

private enum class DownloadFilter(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    TV("TV"),
    ANIME("Anime"),
}

@Composable
fun downloadsScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onSettingsChange: (DownloadSettings) -> Unit,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exportScope = rememberCoroutineScope()
    var pendingExportId by rememberSaveable { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        val item = uiState.downloadsQueue.firstOrNull { it.id == pendingExportId }
        pendingExportId = null
        if (uri != null && item != null) {
            exporting = true
            exportJob = exportScope.launch {
                try {
                    exportOfflineVideo(context, item, uri)
                    exportMessage = "Video saved. Open the folder you selected in your Files app to play or share it."
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    exportMessage = "Could not save this video. Check available space and that the download plays offline."
                } finally {
                    exporting = false
                }
            }
        }
    }
    val saveToFiles: (String) -> Unit = { id ->
        if (!exporting && pendingExportId == null) {
            uiState.downloadsQueue.firstOrNull { it.id == id }?.let { item ->
                pendingExportId = id
                val name = "${item.title} - ${item.subtitle}".replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(100)
                exportLauncher.launch("$name.mp4")
            }
        }
    }
    if (exporting) AlertDialog(
        onDismissRequest = {},
        title = { Text("Saving video") },
        text = { Column { CircularProgressIndicator(); Text("Keep this screen open while your offline video is saved.") } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { exportJob?.cancel() }) { Text("Cancel") } },
    )
    exportMessage?.let { message -> AlertDialog(
        onDismissRequest = { exportMessage = null },
        title = { Text("Save to files") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { exportMessage = null }) { Text("OK") } },
    ) }
    var selectedFilter by remember { mutableStateOf(DownloadFilter.ALL) }
    var showSettings by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val filteredDownloads = remember(uiState.downloadsQueue, selectedFilter) {
        uiState.downloadsQueue.filter { item ->
            when (selectedFilter) {
                DownloadFilter.ALL -> true
                DownloadFilter.MOVIES -> item.mediaType == DownloadMediaType.MOVIE
                DownloadFilter.TV -> item.mediaType == DownloadMediaType.TV_EPISODE
                DownloadFilter.ANIME -> item.mediaType == DownloadMediaType.ANIME_EPISODE
            }
        }
    }
    val active = filteredDownloads.filter {
        it.status == DownloadStatus.RESOLVING ||
            it.status == DownloadStatus.QUEUED ||
            it.status == DownloadStatus.DOWNLOADING ||
            it.status == DownloadStatus.REMOVING
    }
    val paused = filteredDownloads.filter { it.status == DownloadStatus.PAUSED }
    val completed = filteredDownloads.filter { it.status == DownloadStatus.COMPLETED }
    val failed = filteredDownloads.filter { it.status == DownloadStatus.FAILED }

    if (showSettings) {
        downloadSettingsDialog(
            settings = uiState.downloadSettings,
            onDismiss = { showSettings = false },
            onSave = {
                onSettingsChange(it)
                showSettings = false
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Downloads",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "${uiState.downloadsQueue.size} saved or queued",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row {
                IconButton(
                    onClick = if (uiState.downloadsPaused) onResumeAll else onPauseAll,
                    enabled = uiState.downloadsQueue.any {
                        it.status == DownloadStatus.DOWNLOADING ||
                            it.status == DownloadStatus.QUEUED ||
                            it.status == DownloadStatus.PAUSED
                    },
                ) {
                    Icon(
                        if (uiState.downloadsPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription =
                            if (uiState.downloadsPaused) "Resume all" else "Pause all",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Download settings",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DownloadFilter.entries.forEach { filter ->
                val selected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        filter.label,
                        color =
                            if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (uiState.downloadSettings.wifiOnly && active.isNotEmpty()) {
            Text(
                "Wi-Fi only is enabled. Queued downloads wait while the network is metered.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (filteredDownloads.isEmpty()) {
                item {
                    emptyDownloadsState(selectedFilter)
                }
            }
            downloadGroup(
                title = "Active",
                items = active,
                onPause = onPause,
                onResume = onResume,
                onRetry = onRetry,
                onRemove = onRemove,
                onPlay = onPlay,
                onExport = saveToFiles,
            )
            downloadGroup(
                title = "Paused",
                items = paused,
                onPause = onPause,
                onResume = onResume,
                onRetry = onRetry,
                onRemove = onRemove,
                onPlay = onPlay,
                onExport = saveToFiles,
            )
            downloadGroup(
                title = "Completed",
                items = completed,
                onPause = onPause,
                onResume = onResume,
                onRetry = onRetry,
                onRemove = onRemove,
                onPlay = onPlay,
                onExport = saveToFiles,
            )
            downloadGroup(
                title = "Needs attention",
                items = failed,
                onPause = onPause,
                onResume = onResume,
                onRetry = onRetry,
                onRemove = onRemove,
                onPlay = onPlay,
                onExport = saveToFiles,
            )
        }

        storageCard(uiState)
        Spacer(Modifier.height(12.dp))
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.downloadGroup(
    title: String,
    items: List<DownloadItem>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    onPlay: (String) -> Unit,
    onExport: (String) -> Unit,
) {
    if (items.isEmpty()) return
    item {
        Text(
            "$title (${items.size})",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    items(items, key = { it.id }) { item ->
        downloadCard(
            item = item,
            onPause = { onPause(item.id) },
            onResume = { onResume(item.id) },
            onRetry = { onRetry(item.id) },
            onRemove = { onRemove(item.id) },
            onPlay = { onPlay(item.id) },
            onExport = { onExport(item.id) },
        )
    }
}

@Composable
private fun downloadCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onPlay: () -> Unit,
    onExport: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier
                        .size(width = 72.dp, height = 64.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    Text(
                        downloadStatusText(item),
                        color =
                            if (item.status == DownloadStatus.FAILED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                downloadStatusIcon(item)
            }

            if (
                item.status == DownloadStatus.DOWNLOADING ||
                item.status == DownloadStatus.QUEUED ||
                item.status == DownloadStatus.PAUSED
            ) {
                LinearProgressIndicator(
                    progress = { item.percentDownloaded / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (item.status) {
                    DownloadStatus.DOWNLOADING,
                    DownloadStatus.QUEUED -> TextButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, null, modifier = Modifier.size(18.dp))
                        Text("Pause")
                    }
                    DownloadStatus.PAUSED -> TextButton(onClick = onResume) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Text("Resume")
                    }
                    DownloadStatus.FAILED -> TextButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Text("Retry")
                    }
                    DownloadStatus.COMPLETED -> Button(onClick = onPlay) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Text("Play")
                    }
                    DownloadStatus.RESOLVING,
                    DownloadStatus.REMOVING -> Unit
                }
                if (item.status == DownloadStatus.COMPLETED) {
                    TextButton(onClick = onExport) { Text("Save to files") }
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete download",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun downloadStatusIcon(item: DownloadItem) {
    Box(
        modifier = Modifier.size(42.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (item.status) {
            DownloadStatus.RESOLVING,
            DownloadStatus.QUEUED,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.REMOVING -> {
                CircularProgressIndicator(
                    progress = {
                        if (item.status == DownloadStatus.DOWNLOADING) {
                            item.percentDownloaded / 100f
                        } else {
                            0f
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 3.dp,
                )
                if (item.status == DownloadStatus.DOWNLOADING) {
                    Text(
                        "${item.percentDownloaded.toInt()}%",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            DownloadStatus.PAUSED -> Icon(Icons.Default.Pause, null)
            DownloadStatus.COMPLETED -> Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            DownloadStatus.FAILED -> Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun downloadStatusText(item: DownloadItem): String {
    val bytes = when {
        item.sizeBytes > 0L ->
            "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.sizeBytes)}"
        item.downloadedBytes > 0L -> formatBytes(item.downloadedBytes)
        else -> ""
    }
    return when (item.status) {
        DownloadStatus.RESOLVING -> "Finding the provider media stream..."
        DownloadStatus.QUEUED -> listOf("Queued", bytes).filter { it.isNotBlank() }.joinToString(" - ")
        DownloadStatus.DOWNLOADING ->
            listOf("Downloading", bytes).filter { it.isNotBlank() }.joinToString(" - ")
        DownloadStatus.PAUSED ->
            listOf("Paused", bytes).filter { it.isNotBlank() }.joinToString(" - ")
        DownloadStatus.COMPLETED ->
            "Available offline - ${formatBytes(maxOf(item.sizeBytes, item.downloadedBytes))}"
        DownloadStatus.FAILED -> item.errorMessage ?: "Download failed."
        DownloadStatus.REMOVING -> "Deleting offline data..."
    }
}

@Composable
private fun emptyDownloadsState(filter: DownloadFilter) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.Download,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (filter == DownloadFilter.ALL) "No downloads yet" else "No ${filter.label.lowercase()} downloads",
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Use the download button on a movie or episode details screen.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun storageCard(uiState: MainUiState) {
    val storage = uiState.downloadStorage
    val fraction = if (storage.totalBytes > 0L) {
        (storage.usedBytes.toDouble() / storage.totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Offline storage", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${formatBytes(storage.usedBytes)} used",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Text(
                "${formatBytes(storage.availableBytes)} available on this device",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun downloadSettingsDialog(
    settings: DownloadSettings,
    onDismiss: () -> Unit,
    onSave: (DownloadSettings) -> Unit,
) {
    var wifiOnly by remember(settings) { mutableStateOf(settings.wifiOnly) }
    var maxParallel by remember(settings) {
        mutableIntStateOf(settings.maxParallelDownloads)
    }
    var quality by remember(settings) { mutableStateOf(settings.quality) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wi-Fi only", fontWeight = FontWeight.Bold)
                        Text(
                            "Pause downloads on metered networks.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = wifiOnly,
                        onCheckedChange = { wifiOnly = it },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Simultaneous downloads", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..3).forEach { count ->
                            if (maxParallel == count) {
                                Button(onClick = { maxParallel = count }) {
                                    Text(count.toString())
                                }
                            } else {
                                OutlinedButton(onClick = { maxParallel = count }) {
                                    Text(count.toString())
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Video quality", fontWeight = FontWeight.Bold)
                    DownloadQuality.entries.forEach { option ->
                        val selected = quality == option
                        if (selected) {
                            Button(
                                onClick = { quality = option },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(option.label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { quality = option },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(option.label)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        DownloadSettings(
                            wifiOnly = wifiOnly,
                            maxParallelDownloads = maxParallel,
                            quality = quality,
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.1f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.0f MB", mb)
    }
}
