package com.example.streamzee.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.streamzee.data.TmdbMovie

@Composable
fun homeScreen(
    trendingMovies: List<TmdbMovie>,
    savedIds: Set<String>,
    onSearchClicked: () -> Unit,
    onMovieClicked: (TmdbMovie) -> Unit,
    onToggleSave: (String) -> Unit,
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
                text = "Streamzee",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = onSearchClicked) {
                Text("Search")
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
                text = "Loading trending movies…",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(trendingMovies) { movie ->
                movieCard(
                    movie = movie,
                    isSaved = savedIds.contains(movie.id.toString()),
                    onClick = { onMovieClicked(movie) },
                    onToggleSave = { onToggleSave(movie.id.toString()) },
                )
            }
        }
    }
}

@Composable
private fun movieCard(
    movie: TmdbMovie,
    isSaved: Boolean,
    onClick: () -> Unit,
    onToggleSave: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = movie.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(onClick = onToggleSave) {
                    Text(if (isSaved) "Unsave" else "Save")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = movie.releaseDate.orEmpty().ifEmpty { "Unknown release" },
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = movie.overview.orEmpty().take(160).let { if (it.length < movie.overview.orEmpty().length) "$it…" else it },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
