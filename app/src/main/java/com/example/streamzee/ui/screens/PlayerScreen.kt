package com.example.streamzee.ui.screens

import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.streamzee.data.PlaybackSource
import com.example.streamzee.data.TmdbMovie
import com.example.streamzee.data.playerSources

@Composable
fun playerScreen(
    movie: TmdbMovie,
    source: PlaybackSource,
    resumePositionMs: Long?,
    onBack: () -> Unit,
    onPlaybackPositionUpdate: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allPrioritySources = remember { playerSources }
    val startSourceIndex = remember(source) {
        allPrioritySources.indexOfFirst { it.id == source.id }.takeIf { it >= 0 } ?: 0
    }
    var currentSourceIndex by remember { mutableStateOf(startSourceIndex) }
    val currentSource = allPrioritySources[currentSourceIndex]
    var currentCandidateIndex by remember { mutableStateOf(0) }
    val candidateUrls = currentSource.movieUrlCandidates.ifEmpty { listOf(currentSource.movieUrl) }
    val currentUrl = candidateUrls[currentCandidateIndex](movie.id.toString())
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    fun shouldBlockUrl(url: String): Boolean {
        val blockedPatterns = listOf(
            "doubleclick.net",
            "googlesyndication.com",
            "pagead2.googlesyndication.com",
            "adservice.google.com",
            "amazon-adsystem.com",
            "adsystem.com",
            "google-analytics.com",
            "facebook.net",
            "tracker",
            "analytics",
            "popads",
            "popunder",
            "adclick",
        )
        return blockedPatterns.any { url.contains(it, ignoreCase = true) }
    }

    fun tryNextCandidateOrSource(webView: WebView) {
        if (currentCandidateIndex + 1 < candidateUrls.size) {
            currentCandidateIndex += 1
            val nextUrl = candidateUrls[currentCandidateIndex](movie.id.toString())
            webView.loadUrl(nextUrl)
            return
        }
        if (currentSourceIndex + 1 < allPrioritySources.size) {
            currentSourceIndex += 1
            currentCandidateIndex = 0
            val nextSource = allPrioritySources[currentSourceIndex]
            val nextCandidates = nextSource.movieUrlCandidates.ifEmpty { listOf(nextSource.movieUrl) }
            val nextUrl = nextCandidates[0](movie.id.toString())
            webView.loadUrl(nextUrl)
        }
    }

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

        currentSource.note?.let {
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
                            javaScriptCanOpenWindowsAutomatically = false
                            setSupportMultipleWindows(false)
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?,
                            ): Boolean {
                                return false
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? {
                                val url = request.url.toString()
                                if (shouldBlockUrl(url)) {
                                    return WebResourceResponse("text/plain", "utf-8", null)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (request.isForMainFrame) {
                                    tryNextCandidateOrSource(view)
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView,
                                request: WebResourceRequest,
                                errorResponse: WebResourceResponse,
                            ) {
                                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                                    tryNextCandidateOrSource(view)
                                }
                            }
                        }
                        loadUrl(currentUrl)
                        webViewRef.value = this
                    }
                },
                update = { webView ->
                    webView.loadUrl(currentUrl)
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
