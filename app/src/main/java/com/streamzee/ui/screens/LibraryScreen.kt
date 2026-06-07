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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.streamzee.data.JikanAnime

private const val TMDB_IMAGE_W500 = "https://image.tmdb.org/t/p/w500"
private val Purple: Color
    @Composable get() = MaterialTheme.colorScheme.primary
private val CardBg: Color
    @Composable get() = MaterialTheme.colorScheme.surface
private val TextSec: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val ScreenBg: Color
    @Composable get() = MaterialTheme.colorScheme.background

sealed class WatchlistItem {
    data class Movie(val data: TmdbMovie) : WatchlistItem()
    data class Anime(val data: JikanAnime) : WatchlistItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun libraryScreen(
    savedMovies: List<TmdbMovie>,
    savedAnime: List<JikanAnime>,
    savedIds: Set<String>,
    onMovieClicked: (TmdbMovie) -> Unit,
    onAnimeClicked: (JikanAnime) -> Unit,
    onRemove: (String, String) -> Unit,
    onBack: () -> Unit,
    isLoading: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Movies", "TV Shows", "Anime")

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                color = MaterialTheme.colorScheme.onBackground,
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
            watchlistStatChip(
                label = "${savedAnime.size} Anime",
                icon = Icons.Default.Animation, // Use Icons.Default.Animation or Icons.Default.AutoAwesome
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
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else TextSec,
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
                color = MaterialTheme.colorScheme.error,
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
        if (savedMovies.isEmpty() && savedAnime.isEmpty() && !isLoading) {
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
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Browse movies, TV shows and anime to add to your personal watchlist",
                    color = TextSec,
                    fontSize = 14.sp
                )
            }
            return@Column
        }

        // ── Content List ─────────────────────────────────────
        val allItems = remember(savedMovies, savedAnime) {
            savedMovies.map { WatchlistItem.Movie(it) } +
            savedAnime.map { WatchlistItem.Anime(it) }
        }

        val filteredItems = remember(allItems, selectedFilter) {
            when (selectedFilter) {
                "Movies" -> allItems.filterIsInstance<WatchlistItem.Movie>()
                     .filter { it.data.mediaType == "movie" }

                "TV Shows" -> allItems.filterIsInstance<WatchlistItem.Movie>()
                    .filter { it.data.mediaType == "tv" }

                "Anime" -> allItems.filterIsInstance<WatchlistItem.Anime>()

                else -> allItems
            }
        }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filteredItems) { item ->
                        when (item) {

                            is WatchlistItem.Movie -> {
                                watchlistCard(
                                    movie = item.data,
                                    onClick = { onMovieClicked(item.data) },
                                    onRemove = { 
                                            onRemove(item.data.tmdbID.toString(), if (item.data.isTv) "tv" else "movie") 
                                        }
                                )
                            }

                            is WatchlistItem.Anime -> {
                                animeWatchlistCard(
                                    anime = item.data,
                                    onClick = { onAnimeClicked(item.data) },
                                    onRemove = { 
                                        onRemove(item.data.malId.toString(), "anime") 
                                    }
                                )
                            }
                        }
            }
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon, 
            contentDescription = null, 
            tint = Purple, 
            modifier = Modifier.size(20.dp) // Slightly larger icon looks better vertically
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label, 
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp, // Slightly smaller font to fit 4 chips in a row comfortably
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun animeWatchlistCard(
    anime: JikanAnime,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
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
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = anime.images.jpg.imageUrl,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                anime.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Purple.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "ANIME",
                        color = Purple,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    anime.type ?: "",
                    color = TextSec,
                    fontSize = 12.sp
                )

                Text(
                    "⭐ ${anime.score ?: "N/A"}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp
                )
            }
        }

        // Remove button
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.error,
            )
        }
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
                .background(MaterialTheme.colorScheme.surfaceVariant)
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
                color = MaterialTheme.colorScheme.onSurface,
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
                        color = MaterialTheme.colorScheme.onSurface,
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
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
