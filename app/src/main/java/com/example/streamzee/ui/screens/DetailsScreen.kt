package com.example.streamzee.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.streamzee.data.PlaybackSource
import com.example.streamzee.data.TmdbMovie
import com.example.streamzee.data.playerSources

private const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w300"

@Composable
fun detailsScreen(
    movie: TmdbMovie,
    resumePositionMs: Long?,
    isSaved: Boolean,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = movie.displayTitle,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = onBack) {
                Text("Home")
            }
        }

        Text(
            text = "Release date: ${movie.releaseDate.orEmpty().ifEmpty { "Unknown" }}",
            style = MaterialTheme.typography.bodyLarge,
        )

        if (resumePositionMs != null) {
            Text(
                text = "Resume playback at ${formatMillis(resumePositionMs)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (!movie.posterPath.isNullOrBlank()) {
            AsyncImage(
                model = TMDB_IMAGE_BASE_URL + movie.posterPath,
                contentDescription = movie.displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = movie.overview.orEmpty().ifEmpty { "No overview available." },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onToggleSave) {
                    Text(if (isSaved) "Remove from saved" else "Save movie")
                }
            }
        }

        Text(
            text = "Play using the best available source",
            style = MaterialTheme.typography.titleMedium,
        )

        Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
            Text("Play")
        }
    }
}

private fun formatMillis(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
