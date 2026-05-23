package com.example.streamzee.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.streamzee.data.PlaybackSource
import com.example.streamzee.data.TmdbMovie

@Composable
fun playerScreen(
    movie: TmdbMovie,
    source: PlaybackSource,
    resumePositionMs: Long?,
    onBack: () -> Unit,
    onPlaybackPositionUpdate: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mediaUrl = source.movieUrl(movie.id.toString())
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

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
            )
            Button(onClick = onBack) {
                Text("Back")
            }
        }

        Text(
            text = "Playing from ${source.label}",
            style = MaterialTheme.typography.bodyLarge,
        )

        source.note?.let {
            Text(
                text = "Note: $it",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                        }
                        webViewClient = WebViewClient()
                        loadUrl(mediaUrl)
                        webViewRef.value = this
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
            )
        }

        Text(
            text = "The video player is loaded in the web view above.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.destroy()
            onPlaybackPositionUpdate(0L)
        }
    }
}
