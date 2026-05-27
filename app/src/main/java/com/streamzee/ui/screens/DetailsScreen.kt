package com.streamzee.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamzee.data.TmdbMovie
import com.streamzee.data.TmdbEpisode

private val Purple = Color(0xFFA855F7)
private val DarkBg = Color(0xFF000000)
private val CardBg = Color(0xFF161622)
private val TextSec = Color(0xFF8E8E9F)
private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w780"

@Composable
fun detailsScreen(
    movie: TmdbMovie,
    episodes: List<TmdbEpisode>, // Add this parameter
    lastSeason: Int? = null,
    lastEpisode: Int? = null,
    similarMovies: List<TmdbMovie> = emptyList(), // Default value to prevent missing param error
    resumePositionMs: Long?, // Changed to Int? to match your AppState
    isSaved: Boolean,
    onBack: () -> Unit,
    onToggleSave: (String) -> Unit,
    onPlay: (Int, Int?, Int?, Long) -> Unit, // Corrected signature
    onPlayEpisode: (String) -> Unit = {}, // Added with default
    modifier: Modifier = Modifier,
    onSeasonChange: (Long, Int) -> Unit, // Add this
    onMovieClicked: (TmdbMovie) -> Unit // Ensure this is present
) {
    var selectedSeason by remember { mutableStateOf(1) }
    var isExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { heroSection(movie, onBack) }

        item {
            actionButtonsSection(
                movie = movie,
                lastSeason = lastSeason,
                lastEpisode = lastEpisode,
                resumePositionMs = resumePositionMs ?: 0L, // Handle nullability
                isSaved = isSaved,
                onToggleSave = onToggleSave,
                onPlay = onPlay
            )
        }

        item {
            descriptionSection(movie.overview, isExpanded) { isExpanded = !isExpanded }
        }
        
        // Inside detailsScreen LazyColumn:

        if (movie.isTv) { // Only show for TV shows
            item {
                seasonSelector(
                    movie = movie,
                    selected = selectedSeason,
                    onSelect = { selectedSeason = it },
                    onSeasonChange = onSeasonChange
                )
            }

            items(episodes) { episode ->
                episodeItem(
                    episode = episode,
                    onClick = { onPlay(movie.tmdbID.toInt(), selectedSeason, episode.episodeNumber, 0L) }
                )
            }
        }

        item {
            recommendationsSection(
                list = similarMovies,
                onMovieClick = onMovieClicked
            )
        }
    }
}

@Composable
private fun heroSection(movie: TmdbMovie, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
        AsyncImage(
            model = IMAGE_BASE + movie.backdropPath,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, DarkBg))))
        
        Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onBack, modifier = Modifier.background(Color.Black.copy(0.4f), CircleShape)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
        }

        Row(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.Bottom) {
            Box(modifier = Modifier.size(110.dp, 160.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(12.dp))) {
                AsyncImage(model = IMAGE_BASE + movie.posterPath, contentDescription = null, contentScale = ContentScale.Crop)
                Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(40.dp).align(Alignment.Center))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(movie.displayTitle, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Star, null, tint = Color.Yellow, modifier = Modifier.size(14.dp))
                    Text("${String.format("%.1f", movie.voteAverage ?: 0.0)}/10", color = TextSec, fontSize = 12.sp)
                    Text(movie.releaseDate?.take(4) ?: "", color = TextSec, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun actionButtonsSection(
    movie: TmdbMovie,
    resumePositionMs: Long,
    lastSeason: Int?,
    lastEpisode: Int?,
    isSaved: Boolean,
    onToggleSave: (String) -> Unit,
    onPlay: (Int, Int?, Int?, Long) -> Unit
) {

    val hasProgress = if (movie.isTv) {
        // Show resume if we have watched past S1 E1 OR we have time progress
        lastSeason != null && (lastSeason > 1 || lastEpisode!! > 1 || resumePositionMs > 1000)
    } else {
        // For movies, only show if we have time progress
        resumePositionMs > 1000
    } 

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            
            // --- PLAY BUTTON ---
            // Forces playback to start from the beginning (0ms)
            Button(
                onClick = { 
                    if (movie.isTv) onPlay(movie.tmdbID.toInt(), 1, 1, 0L) 
                    else onPlay(movie.tmdbID.toInt(), null, null, 0L) 
                }, 
                modifier = Modifier.weight(1f).height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Purple),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Text(" Play", fontWeight = FontWeight.Bold)
            }

            // --- RESUME BUTTON ---
            // Only visible if there is saved progress
            if (hasProgress) { // Change to hasProgress if you want to conditionally show/hide the Resume button
                Button(
                    onClick = { 
                        onPlay(movie.tmdbID.toInt(), lastSeason, lastEpisode, resumePositionMs) 
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Resume", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (movie.isTv) "S$lastSeason E$lastEpisode" else formatMillis(resumePositionMs),
                            fontSize = 10.sp, 
                            color = TextSec
                        )
                    }
                }
            }
        }

        // --- WATCHLIST BUTTON ---
        OutlinedButton(
            onClick = { onToggleSave(movie.tmdbID.toString()) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (isSaved) Purple else Color.Gray)
        ) {
            Icon(if (isSaved) Icons.Default.Check else Icons.Default.Add, null, tint = if (isSaved) Purple else Color.White)
            Text(if (isSaved) " Saved to Watchlist" else " Add to Watchlist", color = Color.White)
        }
    }
}

@Composable
private fun descriptionSection(overview: String?, isExpanded: Boolean, onToggle: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(overview ?: "", color = TextSec, fontSize = 14.sp, maxLines = if (isExpanded) 100 else 3, overflow = TextOverflow.Ellipsis)
        Text(if (isExpanded) "Less" else "More", color = Purple, modifier = Modifier.clickable { onToggle() }, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun seasonSelector(
    movie: TmdbMovie, 
    selected: Int, 
    onSelect: (Int) -> Unit, 
    onSeasonChange: (Long, Int) -> Unit
) {
    // Added .horizontalScroll to handle series with 10+ seasons
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val totalSeasons = movie.numberOfSeasons ?: 1
        for (i in 1..totalSeasons) {
            val isSelected = selected == i
            Surface(
                modifier = Modifier.clickable { 
                    onSelect(i)
                    onSeasonChange(movie.tmdbID, i) 
                },
                color = if (isSelected) Purple else CardBg,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Season $i", 
                    color = Color.White, 
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun episodeItem(episode: TmdbEpisode, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${episode.episodeNumber}", color = TextSec, modifier = Modifier.width(20.dp))
        
        Box(modifier = Modifier.size(100.dp, 60.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray)) {
            AsyncImage(
                model = "https://image.tmdb.org/t/p/w500${episode.stillPath}",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(episode.name ?: "Episode ${episode.episodeNumber}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("${episode.runtime ?: 45} min", color = TextSec, fontSize = 12.sp)
        }
        Icon(Icons.Default.FileDownload, null, tint = TextSec)
    }
}

@Composable
private fun recommendationsSection(list: List<TmdbMovie>, onMovieClick: (TmdbMovie) -> Unit) {
    Column {
        Text("More Like This", color = Color.White, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list) { movie ->
                Box(
                    modifier = Modifier
                        .size(110.dp, 160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onMovieClick(movie) } // Navigates to the clicked movie/show
                ) {
                    AsyncImage(model = IMAGE_BASE + movie.posterPath, contentDescription = null, contentScale = ContentScale.Crop)
                }
            }
        }
    } 
}

private fun formatMillis(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}