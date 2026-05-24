package com.example.streamzee.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.streamzee.data.AllAnimeShow
import com.example.streamzee.data.TmdbMovie
import com.example.streamzee.viewmodel.SearchMode

private const val TMDB_IMAGE_W500 = "https://image.tmdb.org/t/p/w500"
private val Purple = Color(0xFFA855F7)
private val CardBg = Color(0xFF161622)
private val TextSec = Color(0xFF8E8E9F)
private val ScreenBg = Color(0xFF050508)

@Composable
fun searchScreen(
    query: String,
    searchMode: SearchMode,
    searchResults: List<TmdbMovie>,
    animeSearchResults: List<AllAnimeShow>,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onModeSelected: (SearchMode) -> Unit,
    onMovieClicked: (TmdbMovie) -> Unit,
    onAnimeClicked: (AllAnimeShow) -> Unit,
    onBack: () -> Unit,
    isSearching: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        // ── Top bar ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Explore",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
        }

        // ── Search Field ─────────────────────────────────────
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    "Search movies, TV, anime...",
                    color = Color(0xFF5A5A6E),
                    fontSize = 15.sp
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Purple
            ),
            leadingIcon = {
                Icon(Icons.Default.Search, "Search", tint = TextSec)
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, "Clear", tint = TextSec)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchSubmit(query) })
        )

        Spacer(Modifier.height(14.dp))

        // ── Category Chips ───────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SearchMode.entries.toList()) { mode ->
                val isSelected = searchMode == mode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected)
                                Brush.horizontalGradient(listOf(Purple, Color(0xFF6366F1)))
                            else
                                Brush.horizontalGradient(listOf(CardBg, CardBg))
                        )
                        .clickable { onModeSelected(mode) }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = when (mode) {
                            SearchMode.MOVIES -> "🎬 Movies"
                            SearchMode.TV -> "📺 TV Shows"
                            SearchMode.ANIME -> "🎌 Anime"
                        },
                        color = if (isSelected) Color.White else TextSec,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Search button ────────────────────────────────────
        Button(
            onClick = { onSearchSubmit(query) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Search, "Search", modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Search", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(8.dp))

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
        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Purple)
            }
        }

        // ── Results ──────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (searchMode == SearchMode.ANIME) {
                // Show empty state
                if (animeSearchResults.isEmpty() && !isSearching && query.isBlank()) {
                    item {
                        emptyStateMessage("Search for your favorite anime")
                    }
                }
                items(animeSearchResults) { show ->
                    animeResultCardPremium(show = show, onClick = { onAnimeClicked(show) })
                }
            } else {
                if (searchResults.isEmpty() && !isSearching && query.isBlank()) {
                    item {
                        emptyStateMessage(
                            when (searchMode) {
                                SearchMode.MOVIES -> "Discover new movies to watch"
                                SearchMode.TV -> "Find trending TV shows"
                                else -> "Search for content"
                            }
                        )
                    }
                }
                items(searchResults) { movie ->
                    searchResultCardPremium(movie = movie, onClick = { onMovieClicked(movie) })
                }
            }
        }
    }
}

@Composable
private fun emptyStateMessage(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = Color(0xFF2C2C3E),
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            color = TextSec,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun animeResultCardPremium(show: AllAnimeShow, onClick: () -> Unit) {
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
        // Anime thumbnail
        Box(
            modifier = Modifier
                .size(width = 70.dp, height = 100.dp)
                .clip(RoundedCornerShape(8.dp))
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
                    tint = TextSec,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                show.name,
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Purple.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("ANIME", color = Purple, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                show.episodeCount?.let {
                    Text("$it episodes", color = TextSec, fontSize = 12.sp)
                }
            }
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextSec,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun searchResultCardPremium(movie: TmdbMovie, onClick: () -> Unit) {
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
            Text(
                movie.overview?.take(80)?.plus("…") ?: "No description",
                color = TextSec,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rating
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(12.dp))
                    Text(
                        "${movie.voteAverage?.let { String.format("%.1f", it) } ?: "N/A"}",
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

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextSec,
            modifier = Modifier.size(24.dp)
        )
    }
}
