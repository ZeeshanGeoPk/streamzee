package com.example.streamzee.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.streamzee.data.TmdbMovie

private const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w300"

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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Saved Library",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = onBack) {
                Text("Home")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Saved items: ${savedIds.size}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(18.dp))

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (isLoading) {
            Text(
                text = "Loading saved movies…",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        if (savedMovies.isEmpty() && !isLoading) {
            Text(
                text = "No saved movies yet. Tap the library icon from Home to add favorites.",
                style = MaterialTheme.typography.bodyLarge,
            )
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(savedMovies) { movie ->
                savedMovieCard(
                    movie = movie,
                    onClick = { onMovieClicked(movie) },
                    onRemove = { onRemove(movie.id.toString()) },
                )
            }
        }
    }
}

@Composable
private fun savedMovieCard(
    movie: TmdbMovie,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val expanded = remember { mutableStateOf(false) }
    val overview = movie.overview.orEmpty().ifEmpty { "No overview available." }
    val displayOverview = if (expanded.value || overview.length <= 120) {
        overview
    } else {
        overview.take(120).trimEnd() + "…"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!movie.posterPath.isNullOrBlank()) {
                AsyncImage(
                    model = TMDB_IMAGE_BASE_URL + movie.posterPath,
                    contentDescription = movie.displayTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(
                text = movie.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = movie.releaseDate.orEmpty().ifEmpty { "Unknown release" },
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = displayOverview,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (overview.length > 120) {
                TextButton(onClick = { expanded.value = !expanded.value }) {
                    Text(if (expanded.value) "Show less" else "Read more")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRemove) {
                Text("Remove from library")
            }
        }
    }
}
