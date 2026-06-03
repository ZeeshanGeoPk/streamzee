@file:Suppress("DEPRECATION")

package com.streamzee.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.streamzee.data.MegaPlayShow
import com.streamzee.data.TmdbMovie
import com.streamzee.viewmodel.ContinueWatchingItem
import com.streamzee.viewmodel.HomeSection
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private const val TMDB_IMAGE_W500 = "https://image.tmdb.org/t/p/w500"
private const val TMDB_IMAGE_W780 = "https://image.tmdb.org/t/p/w780"
private val Purple = Color(0xFFA855F7)
private val CardBg = Color(0xFF161622)
private val TextSecondary = Color(0xFF8E8E9F)
private val ScreenBg = Color(0xFF050508)

private sealed interface HomeHeroItem {
    val key: String
    val title: String
    val imageUrl: String?
    val metadata: String
    val score: String
    val overview: String?
    val watchlistKey: String

    data class Tmdb(val movie: TmdbMovie) : HomeHeroItem {
        override val key: String = "${movie.mediaType}_${movie.tmdbID}"
        override val title: String = movie.displayTitle
        override val imageUrl: String? = (movie.backdropPath ?: movie.posterPath)?.let { TMDB_IMAGE_W780 + it }
        override val metadata: String = listOfNotNull(
            if (movie.isTv) "TV Series" else "Movie",
            movie.displayYear.takeIf { it.isNotBlank() },
        ).joinToString(" / ")
        override val score: String = movie.voteAverage?.let { String.format("%.1f", it) } ?: "N/A"
        override val overview: String? = movie.overview
        override val watchlistKey: String = movie.watchlistKey
    }

    data class Anime(val show: MegaPlayShow) : HomeHeroItem {
        override val key: String = "anime_${show.animeID}"
        override val title: String = show.name
        override val imageUrl: String? = show.thumbnail
        override val metadata: String = listOfNotNull(
            "Anime",
            show.animeType,
            show.episodeCount?.takeIf { it > 0 }?.let { "$it eps" },
        ).joinToString(" / ")
        override val score: String = show.score?.takeIf { it.isNotBlank() } ?: "N/A"
        override val overview: String? = null
        override val watchlistKey: String = "anime_${show.animeID}"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun homeScreen(
    trendingMovies: List<TmdbMovie>,
    trendingTv: List<TmdbMovie>,
    trendingAnime: List<MegaPlayShow>,
    recentMovies: List<TmdbMovie>,
    recentTv: List<TmdbMovie>,
    recentAnime: List<MegaPlayShow>,
    topMovies: List<TmdbMovie>,
    topTv: List<TmdbMovie>,
    topAnime: List<MegaPlayShow>,
    continueWatching: List<ContinueWatchingItem>,
    savedIds: Set<String>,
    onSearchClicked: () -> Unit,
    onLibraryClicked: () -> Unit,
    onMovieClicked: (TmdbMovie) -> Unit,
    onAnimeClicked: (MegaPlayShow) -> Unit,
    onSeeAllClicked: (HomeSection) -> Unit,
    onToggleSave: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    // Split trending into hero (first 5) and rows
    val heroItems = buildList {
        addAll(trendingMovies.take(2).map { HomeHeroItem.Tmdb(it) })
        addAll(trendingTv.take(2).map { HomeHeroItem.Tmdb(it) })
        addAll(trendingAnime.take(2).map { HomeHeroItem.Anime(it) })
        addAll(topAnime.take(1).map { HomeHeroItem.Anime(it) })
    }
        .filter { !it.imageUrl.isNullOrBlank() }
        .distinctBy { it.key }
        .take(6)

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
        if (heroItems.isNotEmpty()) {
            item {
                val pagerState = rememberPagerState(pageCount = { heroItems.size })

                // Auto-scroll
                LaunchedEffect(pagerState) {
                    while (true) {
                        delay(4.seconds)
                        val next = (pagerState.currentPage + 1) % heroItems.size
                        pagerState.animateScrollToPage(next)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) { page ->
                        val heroItem = heroItems[page]
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    when (heroItem) {
                                        is HomeHeroItem.Tmdb -> onMovieClicked(heroItem.movie)
                                        is HomeHeroItem.Anime -> onAnimeClicked(heroItem.show)
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = heroItem.imageUrl,
                                contentDescription = heroItem.title,
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
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Purple.copy(alpha = 0.24f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        heroItem.metadata,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    heroItem.title,
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
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
                                            heroItem.score,
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                                if (!heroItem.overview.isNullOrBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        heroItem.overview.orEmpty(),
                                        color = Color.White.copy(alpha = 0.84f),
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(0.92f)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            when (heroItem) {
                                                is HomeHeroItem.Tmdb -> onMovieClicked(heroItem.movie)
                                                is HomeHeroItem.Anime -> onAnimeClicked(heroItem.show)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Purple),
                                        shape = RoundedCornerShape(24.dp),
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Details", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { onToggleSave(heroItem.watchlistKey) },
                                        shape = RoundedCornerShape(24.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                    ) {
                                        Icon(
                                            if (savedIds.contains(heroItem.watchlistKey)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
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
                        repeat(heroItems.size) { index ->
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
            item { sectionHeader("Continue Watching") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(continueWatching, key = { "${it.movie.mediaType}_${it.movie.tmdbID}" }) { item ->
                        continueWatchingCard(
                            item = item,
                            onClick = { onMovieClicked(item.movie) }
                        )
                    }
                }
            }
        }

        // ── Trending ─────────────────────────────────────────────
        if (trendingMovies.isNotEmpty()) {
            item { sectionHeader("Trending Movies", onActionClick = { onSeeAllClicked(HomeSection.TRENDING_MOVIES) }) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(trendingMovies) { movie ->
                        posterCard(movie = movie, onClick = { onMovieClicked(movie) })
                    }
                }
            }
        }

        if (trendingTv.isNotEmpty()) {
            item { sectionHeader("Trending TV Shows", onActionClick = { onSeeAllClicked(HomeSection.TRENDING_TV) }) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(trendingTv) { show ->
                        posterCard(movie = show, onClick = { onMovieClicked(show) })
                    }
                }
            }
        }

        if (trendingAnime.isNotEmpty()) {
            item { sectionHeader("Trending Anime", onActionClick = { onSeeAllClicked(HomeSection.TRENDING_ANIME) }) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(trendingAnime) { anime ->
                        animePosterCard(show = anime, onClick = { onAnimeClicked(anime) })
                    }
                }
            }
        }

        // ── New ─────────────────────────────────────────────────
        if (recentMovies.isNotEmpty()) {
            item { sectionHeader("New Movies", onActionClick = { onSeeAllClicked(HomeSection.RECENT_MOVIES) }) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentMovies) { movie ->
                        posterCard(movie = movie, onClick = { onMovieClicked(movie) })
                    }
                }
            }
        }

        if (recentTv.isNotEmpty()) {
            item { sectionHeader("New TV Shows", onActionClick = { onSeeAllClicked(HomeSection.RECENT_TV) }) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentTv) { show ->
                        posterCard(movie = show, onClick = { onMovieClicked(show) })
                    }
                }
            }
        }

        if (recentAnime.isNotEmpty()) {
            item { sectionHeader("New Anime", onActionClick = { onSeeAllClicked(HomeSection.RECENT_ANIME) }) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentAnime) { anime ->
                        animePosterCard(show = anime, onClick = { onAnimeClicked(anime) })
                    }
                }
            }
        }

        // ── Top Rated ────────────────────────────────────────────
        if (topMovies.isNotEmpty()) {
            item { sectionHeader("Top Movies", onActionClick = { onSeeAllClicked(HomeSection.TOP_MOVIES) }) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(topMovies) { movie ->
                        topRatedCard(movie = movie, onClick = { onMovieClicked(movie) })
                    }
                }
            }
        }

        if (topTv.isNotEmpty()) {
            item { sectionHeader("Top TV Shows", onActionClick = { onSeeAllClicked(HomeSection.TOP_TV) }) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(topTv) { show ->
                        topRatedCard(movie = show, onClick = { onMovieClicked(show) })
                    }
                }
            }
        }

        if (topAnime.isNotEmpty()) {
            item { sectionHeader("Top Anime", onActionClick = { onSeeAllClicked(HomeSection.TOP_ANIME) }) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(topAnime) { anime ->
                        animePosterCard(show = anime, onClick = { onAnimeClicked(anime) }, showScore = true)
                    }
                }
            }
        }
    }
}

// ── Section Header ───────────────────────────────────────────────
@Composable
private fun sectionHeader(title: String, action: String? = "See all", onActionClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (action != null && onActionClick != null) {
            Text(
                action,
                color = Purple,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onActionClick)
            )
        }
    }
}

private val TmdbMovie.watchlistKey: String
    get() = if (isTv) "tv_$tmdbID" else "movie_$tmdbID"

// ── Continue Watching Card ───────────────────────────────────────
@Composable
private fun continueWatchingCard(item: ContinueWatchingItem, onClick: () -> Unit) {
    val movie = item.movie
    val subtitle = if (movie.isTv && item.season != null && item.episode != null) {
        "S${item.season} E${item.episode} / ${formatResumeTime(item.positionMs)} watched"
    } else {
        "${formatResumeTime(item.positionMs)} watched"
    }

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
            progress = item.progress,
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
            subtitle,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatResumeTime(positionMs: Long): String {
    val totalMinutes = (positionMs / 60000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
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

// ── Anime Poster Card ───────────────────────────────────────────
@Composable
private fun animePosterCard(show: MegaPlayShow, onClick: () -> Unit, showScore: Boolean = false) {
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
                .background(Color(0xFF2C2C3E)),
            contentAlignment = Alignment.Center
        ) {
            if (!show.thumbnail.isNullOrBlank()) {
                AsyncImage(
                    model = show.thumbnail,
                    contentDescription = show.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Movie,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(32.dp)
                )
            }

            if (showScore && !show.score.isNullOrBlank() && show.score != "N/A") {
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
                            show.score,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            show.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            listOfNotNull(show.animeType, show.episodeCount?.takeIf { it > 0 }?.let { "$it eps" })
                .joinToString(" • "),
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
