package com.streamzee.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.automirrored.filled.Sort
import coil.compose.AsyncImage
import com.streamzee.data.TmdbMovie

private const val TMDB_IMAGE_W500 = "https://image.tmdb.org/t/p/w500"
private val Purple = Color(0xFFA855F7)
private val CardBg = Color(0xFF161622)
private val TextSec = Color(0xFF8E8E9F)
private val ScreenBg = Color(0xFF050508)

@Composable
fun libraryScreen(
    savedMovies: List<TmdbMovie>,
    savedIds: Set<String>,
    onMovieClicked: (TmdbMovie) -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Movies", "TV Shows", "Anime")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        // ── Header ───────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Watchlist",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = {}) {
                    Icon(Icons.AutoMirrored.Filled.Sort, "Sort", tint = Purple)
                }
            }
        }

        // ── Stats bar ────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            watchlistStatChip(
                label = "${savedIds.size} Items",
                icon = Icons.Default.Favorite,
                modifier = Modifier.weight(1f)
            )
            watchlistStatChip(
                label = "${savedMovies.filter { it.mediaType == "movie" }.size} Movies",
                icon = Icons.Default.Movie,
                modifier = Modifier.weight(1f)
            )
            watchlistStatChip(
                label = "${savedMovies.filter { it.mediaType == "tv" }.size} Shows",
                icon = Icons.Default.Tv,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(14.dp))

        // ── Filter Chips ─────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters) { filter ->
                val isSelected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) Purple else CardBg)
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        filter,
                        color = if (isSelected) Color.White else TextSec,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Error ────────────────────────────────────────────
        if (errorMessage != null) {
            Text(
                errorMessage,
                color = Color(0xFFEF4444),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                fontSize = 14.sp
            )
        }

        // ── Loading ──────────────────────────────────────────
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Purple)
            }
        }

        // ── Empty State ──────────────────────────────────────
        if (savedMovies.isEmpty() && !isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        "Empty",
                        tint = Purple,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Text(
                    "Your watchlist is empty",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Browse movies, TV shows and anime to add to your personal watchlist",
                    color = TextSec,
                    fontSize = 14.sp
                )
            }
            return
        }

        // ── Content List ─────────────────────────────────────
        val filteredMovies = remember(savedMovies, selectedFilter) {
            when (selectedFilter) {
                "Movies" -> savedMovies.filter { it.mediaType == "movie" }
                "TV Shows" -> savedMovies.filter { it.mediaType == "tv" }
                "Anime" -> savedMovies.filter {
                    it.genreIds?.contains(16) == true ||
                    it.displayTitle.contains("anime", ignoreCase = true)
                }
                else -> savedMovies
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(filteredMovies) { movie ->
                watchlistCard(
                    movie = movie,
                    onClick = { onMovieClicked(movie) },
                    onRemove = { onRemove(movie.tmdbID.toString()) }
                )
            }
        }
    }
}

@Composable
private fun watchlistStatChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Purple, modifier = Modifier.size(16.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun watchlistCard(movie: TmdbMovie, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Poster
        Box(
            modifier = Modifier
                .size(width = 70.dp, height = 100.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2C2C3E))
        ) {
            if (!movie.posterPath.isNullOrBlank()) {
                AsyncImage(
                    model = TMDB_IMAGE_W500 + movie.posterPath,
                    contentDescription = movie.displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                movie.displayTitle,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Media type badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Purple.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        (movie.mediaType ?: "movie").uppercase(),
                        color = Purple,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Rating
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(12.dp))
                    Text(
                        movie.voteAverage?.let { String.format("%.1f", it) } ?: "N/A",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
                // Year
                Text(
                    movie.releaseDate?.take(4) ?: "",
                    color = TextSec,
                    fontSize = 12.sp
                )
            }
        }

        // Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                "Remove",
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
