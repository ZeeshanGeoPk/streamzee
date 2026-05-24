package com.example.streamzee.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.streamzee.viewmodel.DownloadItem
import com.example.streamzee.viewmodel.MainUiState

@Composable
fun downloadsScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf("All") }
    val tabs = listOf("All", "Movies", "Episodes", "Anime")
    
    // Cache clearing dynamic states
    var cachedClearedSuccess by remember { mutableStateOf(false) }

    val filteredQueue = remember(uiState.downloadsQueue, selectedTab) {
        when (selectedTab) {
            "Movies" -> uiState.downloadsQueue.filter { it.subtitle.contains("Movie", ignoreCase = true) }
            "Episodes" -> uiState.downloadsQueue.filter { it.subtitle.contains("E", ignoreCase = true) && !it.title.contains("Demon Slayer") && !it.title.contains("Jujutsu") }
            "Anime" -> uiState.downloadsQueue.filter { it.title.contains("Demon Slayer") || it.title.contains("Jujutsu") || it.title.contains("Attack on Titan") }
            else -> uiState.downloadsQueue
        }
    }

    val downloading = filteredQueue.filter { it.status == "Downloading" }
    val paused = filteredQueue.filter { it.status == "Paused" }
    val completed = filteredQueue.filter { it.status == "Completed" }
    val failed = filteredQueue.filter { it.status == "Failed" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050508))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Downloads",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(onClick = { cachedClearedSuccess = true }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Controls",
                    tint = Color(0xFFA855F7)
                )
            }
        }

        if (cachedClearedSuccess) {
            Text(
                text = "Cache cleared successfully!",
                color = Color(0xFFA855F7),
                fontSize = 14.sp
            )
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2000)
                cachedClearedSuccess = false
            }
        }

        // Horizontal Category Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEach { tab ->
                val isSelected = selectedTab == tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) Color(0xFFA855F7) else Color(0xFF161622))
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else Color(0xFF8E8E9F),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Downloads List Grouped
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (downloading.isNotEmpty()) {
                item {
                    Text("Downloading (${downloading.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                items(downloading) { item ->
                    downloadCard(item)
                }
            }

            if (paused.isNotEmpty()) {
                item {
                    Text("Paused (${paused.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                items(paused) { item ->
                    downloadCard(item)
                }
            }

            if (completed.isNotEmpty()) {
                item {
                    Text("Completed (${completed.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                items(completed) { item ->
                    downloadCard(item)
                }
            }

            if (failed.isNotEmpty()) {
                item {
                    Text("Failed (${failed.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                items(failed) { item ->
                    downloadCard(item)
                }
            }
        }

        // Storage used indicator
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161622))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Storage Used", color = Color(0xFF8E8E9F), fontSize = 14.sp)
                    Text(
                        "${uiState.storageUsedGb} GB / ${uiState.storageTotalGb} GB",
                        color = Color(0xFFA855F7),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                LinearProgressIndicator(
                    progress = (uiState.storageUsedGb / uiState.storageTotalGb).toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFFA855F7),
                    trackColor = Color(0xFF2C2C3E)
                )
            }
        }
    }
}

@Composable
private fun downloadCard(item: DownloadItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF161622))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail image
        AsyncImage(
            model = item.imageUrl,
            contentDescription = item.title,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        // Title and size details
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = item.subtitle,
                color = Color(0xFF8E8E9F),
                fontSize = 12.sp
            )
            val sizeMb = item.sizeBytes / (1024 * 1024)
            val dlMb = item.downloadedBytes / (1024 * 1024)
            Text(
                text = if (item.status == "Downloading") "$dlMb MB / $sizeMb MB" else "$sizeMb MB",
                color = Color(0xFF8E8E9F),
                fontSize = 12.sp
            )
        }

        // Circular progress or icon status
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            when (item.status) {
                "Downloading" -> {
                    val progressRatio = item.downloadedBytes.toFloat() / item.sizeBytes.toFloat()
                    CircularProgressIndicator(
                        progress = progressRatio,
                        color = Color(0xFFA855F7),
                        strokeWidth = 3.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(
                        "${(progressRatio * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                "Paused" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF2C2C3E), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Paused",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                "Completed" -> {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF1E3A24), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = Color(0xFF4ADE80),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                "Failed" -> {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF451A1A), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Failed",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
