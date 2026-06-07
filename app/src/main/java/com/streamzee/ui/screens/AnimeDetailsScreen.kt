package com.streamzee.ui.screens

import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamzee.data.MegaPlayShow
import com.streamzee.data.MegaPlayEpisode

private val Purple: Color
    @Composable get() = MaterialTheme.colorScheme.primary
private val DarkBg: Color
    @Composable get() = MaterialTheme.colorScheme.background
private val CardBg: Color
    @Composable get() = MaterialTheme.colorScheme.surface
private val TextSec: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun animeDetailsScreen(
    show: MegaPlayShow,
    episodes: List<MegaPlayEpisode>,
    selectedTranslation: String,
    onTranslationChange: (String) -> Unit,
    onBack: () -> Unit,
    onPlayEpisode: (Int) -> Unit,
    onResumeEpisode: (Int, Long) -> Unit,
    onToggleSave: (String) -> Unit,
    isSaved: Boolean,
    resumePositionMs: Long,
    lastWatchedEpisode: Int,
    modifier: Modifier = Modifier,
    isLoading: Boolean,
    errorMessage: String?
) {
    // 1. Wrap everything in a Box to allow overlaying
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DarkBg
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    animeHeroSection(
                        show = show,
                        episodeCount = episodes.size,
                        onBack = onBack,
                        isSaved = isSaved,
                        onToggleSave = onToggleSave,
                        resumePositionMs = resumePositionMs,
                        lastWatchedEpisode = lastWatchedEpisode,
                        onResumeEpisode = onResumeEpisode,
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    translationToggle(selectedTranslation, onTranslationChange)
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Episodes",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                items(episodes) { episode ->
                    episodeGridCard(
                        num = episode.number.toString(),
                        onClick = { onPlayEpisode(episode.number) }
                    )
                }
            }
        }
        // Add this in the Box
        errorMessage?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 100.dp) // Below the hero banner
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 14.sp,
                )
            }
        }
        // 2. Add the Loading Overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(enabled = false) {}, // Block clicks to items behind
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Purple)
                    Spacer(Modifier.height(12.dp))
                    Text("Resolving Links...", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun animeHeroSection(
    show: MegaPlayShow,
    episodeCount: Int,
    onBack: () -> Unit,
    isSaved: Boolean,
    onToggleSave: (String) -> Unit,
    resumePositionMs: Long,
    lastWatchedEpisode: Int,
    onResumeEpisode: (Int, Long) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
    ) {

        AsyncImage(
            model = show.thumbnail,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            DarkBg
                        )
                    )
                )
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .background(
                    Color.Black.copy(0.4f),
                    CircleShape
                )
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                null,
                tint = Color.White
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Text(
                show.name,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Surface(
                    color = Purple,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "ANIME",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(
                            horizontal = 6.dp,
                            vertical = 2.dp
                        ),
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        null,
                        tint = Color.Yellow,
                        modifier = Modifier.size(14.dp)
                    )

                    Text(
                        show.score ?: "N/A",
                        color = TextSec,
                        fontSize = 12.sp
                    )
                }

                Text(
                    "${show.episodeCount ?: episodeCount} Episodes",
                    color = TextSec,
                    fontSize = 12.sp
                )
            }

            if (resumePositionMs > 1_000L || lastWatchedEpisode > 1) {
                Button(
                    onClick = {
                        onResumeEpisode(lastWatchedEpisode, resumePositionMs)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            "Resume Episode $lastWatchedEpisode",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            formatAnimeWatchedTime(resumePositionMs),
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { onToggleSave("anime_${show.animeID}") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSaved) Purple else Color.Gray
                )
            ) {

                Icon(
                    if (isSaved)
                        Icons.Default.Check
                    else
                        Icons.Default.Add,
                    null,
                    tint =
                        if (isSaved)
                            Purple
                        else
                            MaterialTheme.colorScheme.onBackground
                )

                Text(
                    if (isSaved)
                        " Saved to Watchlist"
                    else
                        " Add to Watchlist",
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

private fun formatAnimeWatchedTime(positionMs: Long): String {
    val totalMinutes = (positionMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
private fun translationToggle(selected: String, onToggle: (String) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("sub", "dub").forEach { type ->
            val isSelected = selected == type
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Purple else Color.Transparent)
                    .clickable { onToggle(type) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = type.uppercase(),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else TextSec,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun episodeGridCard(num: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .height(60.dp)
            .clickable { onClick() },
        color = CardBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    num,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text("EP", color = Purple, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
