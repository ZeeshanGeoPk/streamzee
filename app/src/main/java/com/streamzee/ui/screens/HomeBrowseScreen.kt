package com.streamzee.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamzee.data.MegaPlayShow
import com.streamzee.data.TmdbMovie
import com.streamzee.viewmodel.HomeBrowseUiState

private const val TMDB_IMAGE_W500 = "https://image.tmdb.org/t/p/w500"
private val Purple = Color(0xFFA855F7)
private val CardBg = Color(0xFF161622)
private val TextSecondary = Color(0xFF8E8E9F)
private val ScreenBg = Color(0xFF050508)

@Composable
fun homeBrowseScreen(
    browseState: HomeBrowseUiState,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onMovieClicked: (TmdbMovie) -> Unit,
    onAnimeClicked: (MegaPlayShow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 6
        }
    }

    LaunchedEffect(shouldLoadMore, browseState.isLoading, browseState.endReached, browseState.errorMessage) {
        if (shouldLoadMore && !browseState.isLoading && !browseState.endReached && browseState.errorMessage == null) {
            onLoadMore()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                browseState.section?.title.orEmpty(),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (browseState.errorMessage != null && browseState.movies.isEmpty() && browseState.anime.isEmpty()) {
            BrowseErrorState(
                message = browseState.errorMessage,
                onRetry = onLoadMore,
                modifier = Modifier.fillMaxSize()
            )
            return@Column
        }

        val isInitialLoading = browseState.isLoading && browseState.movies.isEmpty() && browseState.anime.isEmpty()
        if (isInitialLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Purple)
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 130.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (browseState.section?.isAnime == true) {
                items(browseState.anime, key = { it.animeID }) { anime ->
                    BrowseAnimeCard(show = anime, onClick = { onAnimeClicked(anime) })
                }
            } else {
                items(browseState.movies, key = { it.tmdbID }) { movie ->
                    BrowseMovieCard(movie = movie, onClick = { onMovieClicked(movie) })
                }
            }

            item {
                when {
                    browseState.isLoading -> LoadingFooter()
                    browseState.errorMessage != null -> RetryFooter(
                        message = browseState.errorMessage,
                        onRetry = onLoadMore,
                    )
                    browseState.endReached -> EndFooter()
                }
            }
        }
    }
}

@Composable
private fun BrowseMovieCard(movie: TmdbMovie, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        PosterImage(
            imageUrl = movie.posterPath?.let { TMDB_IMAGE_W500 + it },
            contentDescription = movie.displayTitle,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            movie.displayTitle,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(12.dp))
            Text(
                movie.voteAverage?.let { String.format("%.1f", it) } ?: "N/A",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun BrowseAnimeCard(show: MegaPlayShow, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        PosterImage(
            imageUrl = show.thumbnail,
            contentDescription = show.name,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            show.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            listOfNotNull(show.animeType, show.episodeCount?.takeIf { it > 0 }?.let { "$it eps" })
                .joinToString(" / "),
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PosterImage(imageUrl: String?, contentDescription: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Default.Movie,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
private fun BrowseErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = Color(0xFFEF4444), fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Purple),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun LoadingFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Purple, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun RetryFooter(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = Color(0xFFEF4444), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "Retry",
            color = Purple,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onRetry)
        )
    }
}

@Composable
private fun EndFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("End of list", color = TextSecondary, fontSize = 12.sp)
    }
}
