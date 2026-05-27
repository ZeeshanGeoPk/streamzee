@file:Suppress("DEPRECATION")

package com.example.streamzee.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import coil.compose.AsyncImage
import com.example.streamzee.data.TmdbMovie
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private const val TMDB_IMAGE_W500 = "https://image.tmdb.org/t/p/w500"
private const val TMDB_IMAGE_W780 = "https://image.tmdb.org/t/p/w780"
private val Purple = Color(0xFFA855F7)
private val CardBg = Color(0xFF161622)
private val TextSecondary = Color(0xFF8E8E9F)
private val ScreenBg = Color(0xFF050508)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun homeScreen(
    trendingMovies: List<TmdbMovie>,
    savedIds: Set<String>,
    onSearchClicked: () -> Unit,
    onLibraryClicked: () -> Unit,
    onMovieClicked: (TmdbMovie) -> Unit,
    onToggleSave: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    // Split trending into hero (first 5) and rows
    val heroMovies = trendingMovies.take(5)
    val continueWatching = trendingMovies.drop(5).take(6) // Simulated continue watching
    val trendingNow = trendingMovies.drop(2).take(8)
    val popularAnime = trendingMovies.filter { 
        it.displayTitle.contains("anime", ignoreCase = true) ||
        it.genreIds?.containsAll(listOf(16)) == true
    }.ifEmpty { trendingMovies.drop(8).take(6) }
    val newMovies = trendingMovies.drop(4).take(8)
    val topRated = trendingMovies.sortedByDescending { it.voteAverage ?: 0.0 }.take(8)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // ── Top App Bar ──────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Streamzee",
                    color = Purple,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onSearchClicked) {
                        Icon(Icons.Default.Search, "Search", tint = Color.White)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Notifications, "Notifications", tint = Color.White)
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C3E))
                            .clickable { onLibraryClicked() },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = "https://i.pinimg.com/736x/9e/2b/e4/9e2be4f1a241a8be8d4836d5fbbe2ee2.jpg",
                            contentDescription = "Profile",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        // ── Hero Banner Carousel ─────────────────────────────────
        if (heroMovies.isNotEmpty()) {
            item {
                val pagerState = rememberPagerState(pageCount = { heroMovies.size })

                // Auto-scroll
                LaunchedEffect(pagerState) {
                    while (true) {
                        delay(4.seconds)
                        val next = (pagerState.currentPage + 1) % heroMovies.size
                        pagerState.animateScrollToPage(next)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    ) { page ->
                        val movie = heroMovies[page]
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onMovieClicked(movie) }
                        ) {
                            AsyncImage(
                                model = TMDB_IMAGE_W780 + (movie.backdropPath ?: movie.posterPath),
                                contentDescription = movie.displayTitle,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Gradient scrim
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color(0xCC050508)),
                                            startY = 80f
                                        )
                                    )
                            )
                            // Movie info overlay
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    movie.displayTitle,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Rating
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                                        Text(
                                            movie.voteAverage?.let { String.format("%.1f", it) } ?: "N/A",
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Text("•", color = TextSecondary, fontSize = 13.sp)
                                    Text(
                                        movie.releaseDate?.take(4) ?: "",
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onMovieClicked(movie) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Purple),
                                        shape = RoundedCornerShape(24.dp),
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Play", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { onToggleSave(movie.tmdbID.toString()) },
                                        shape = RoundedCornerShape(24.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                    ) {
                                        Icon(
                                            if (savedIds.contains(movie.tmdbID.toString())) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Watchlist", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Pager dots
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(heroMovies.size) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == index) Purple else Color(0xFF5A5A6E)
                                    )
                            )
                        }
                    }
                }
            }
        }

        // ── Error / Loading States ───────────────────────────────
        if (errorMessage != null) {
            item {
                Text(
                    text = errorMessage,
                    color = Color(0xFFEF4444),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 14.sp
                )
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Purple)
                }
            }
        }

        // ── Continue Watching ────────────────────────────────────
        if (continueWatching.isNotEmpty()) {
            item { sectionHeader("Continue Watching", "See all") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(continueWatching) { index, movie ->
                        continueWatchingCard(
                            movie = movie,
                            progress = (0.2f + index * 0.15f).coerceAtMost(0.9f),
                            onClick = { onMovieClicked(movie) }
                        )
                    }
                }
            }
        }

        // ── Trending Now ─────────────────────────────────────────
        if (trendingNow.isNotEmpty()) {
            item { sectionHeader("🔥 Trending Now", "See all") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(trendingNow) { movie ->
                        posterCard(movie = movie, onClick = { onMovieClicked(movie) })
                    }
                }
            }
        }

        // ── Popular Anime ────────────────────────────────────────
        if (popularAnime.isNotEmpty()) {
            item { sectionHeader("🎌 Popular Anime", "See all") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(popularAnime) { movie ->
                        posterCard(movie = movie, onClick = { onMovieClicked(movie) })
                    }
                }
            }
        }

        // ── New Movies ───────────────────────────────────────────
        if (newMovies.isNotEmpty()) {
            item { sectionHeader("🆕 New Movies", "See all") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(newMovies) { movie ->
                        posterCard(movie = movie, onClick = { onMovieClicked(movie) })
                    }
                }
            }
        }

        // ── Top Rated ────────────────────────────────────────────
        if (topRated.isNotEmpty()) {
            item { sectionHeader("⭐ Top Rated Series", "See all") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(topRated) { movie ->
                        topRatedCard(movie = movie, onClick = { onMovieClicked(movie) })
                    }
                }
            }
        }
    }
}

// ── Section Header ───────────────────────────────────────────────
@Composable
private fun sectionHeader(title: String, action: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(action, color = Purple, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Continue Watching Card ───────────────────────────────────────
@Composable
private fun continueWatchingCard(movie: TmdbMovie, progress: Float, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = TMDB_IMAGE_W500 + (movie.backdropPath ?: movie.posterPath),
                contentDescription = movie.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Play icon overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x44000000)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xAA000000), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        // Progress bar
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Purple,
            trackColor = Color(0xFF2C2C3E)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            movie.displayTitle,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "Episode ${(1..12).random()} • ${(20..45).random()} min left",
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

// ── Standard Poster Card ─────────────────────────────────────────
@Composable
private fun posterCard(movie: TmdbMovie, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = TMDB_IMAGE_W500 + movie.posterPath,
                contentDescription = movie.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            movie.displayTitle,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(12.dp))
            Text(
                movie.voteAverage?.let { String.format("%.1f", it) } ?: "",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

// ── Top Rated Card with rank ─────────────────────────────────────
@Composable
private fun topRatedCard(movie: TmdbMovie, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = TMDB_IMAGE_W500 + movie.posterPath,
                contentDescription = movie.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Rating badge
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopEnd)
                    .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(12.dp))
                    Text(
                        movie.voteAverage?.let { String.format("%.1f", it) } ?: "",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            movie.displayTitle,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            movie.releaseDate?.take(4) ?: "",
            color = TextSecondary,
            fontSize = 11.sp
        )
    }
}
