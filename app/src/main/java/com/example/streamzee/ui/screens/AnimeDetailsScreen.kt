package com.example.streamzee.ui.screens

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
import com.example.streamzee.data.AnikotoShow
import com.example.streamzee.data.AnikotoEpisode

private val Purple = Color(0xFFA855F7)
private val DarkBg = Color(0xFF000000)
private val CardBg = Color(0xFF161622)
private val TextSec = Color(0xFF8E8E9F)

@Composable
fun animeDetailsScreen(
    show: AnikotoShow,
    episodes: List<AnikotoEpisode>,
    selectedTranslation: String,
    onTranslationChange: (String) -> Unit,
    onBack: () -> Unit,
    onPlayEpisode: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean, // Ensure this is passed from uiState.isLoading
    errorMessage: String? // Optional error message to display
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
                    animeHeroSection(show, episodes.size ,onBack)
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    translationToggle(selectedTranslation, onTranslationChange)
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Episodes",
                        color = Color.White,
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
                    .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(message, color = Color.White, fontSize = 14.sp)
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
private fun animeHeroSection(show: AnikotoShow, episodeCount: Int, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
        // Backdrop Image
        AsyncImage(
            model = show.thumbnail,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Gradient Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, DarkBg)))
        )
        
        // Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(16.dp).background(Color.Black.copy(0.4f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
        }

        // Info Overlay
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(show.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = Purple, shape = RoundedCornerShape(4.dp)) {
                    Text("ANIME", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Star, null, tint = Color.Yellow, modifier = Modifier.size(14.dp))
                    Text(show.score ?: "N/A", color = TextSec, fontSize = 12.sp)
                }
                Text("${show.episodeCount ?: episodeCount} Episodes", color = TextSec, fontSize = 12.sp)
            }
        }
    }
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
                    color = if (isSelected) Color.White else TextSec,
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
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(num, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("EP", color = Purple, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}