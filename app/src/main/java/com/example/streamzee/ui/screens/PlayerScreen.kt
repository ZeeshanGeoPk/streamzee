package com.example.streamzee.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width // Fixes unresolved 'width'
import androidx.compose.material.icons.Icons // Fixes unresolved 'Icons'
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

private val ScreenBg = Color(0xFF050508)
private val Purple = Color(0xFFA855F7)

@Composable
fun playerScreen(
    movie: TmdbMovie,
    source: PlaybackSource,
    resumePositionMs: Long?,
    onBack: () -> Unit,
    onPlaybackPositionUpdate: (Long) -> Unit,
    tvSeason: Int? = null,
    tvEpisode: Int? = null,
    modifier: Modifier = Modifier,
) {
    val allPrioritySources = remember { playerSources }
    val startSourceIndex = remember(source) {
        allPrioritySources.indexOfFirst { it.id == source.id }.takeIf { it >= 0 } ?: 0
    }
    var currentSourceIndex by remember { mutableStateOf(startSourceIndex) }
    val currentSource = allPrioritySources[currentSourceIndex]
    var currentCandidateIndex by remember { mutableStateOf(0) }
    val isTvPlayback = tvSeason != null && tvEpisode != null
    val candidateUrls = remember(currentSourceIndex, currentCandidateIndex, tvSeason, tvEpisode) {
        if (isTvPlayback) {
            currentSource.tvUrlCandidates.ifEmpty { listOf(currentSource.tvUrl) }
                .map { it(movie.id.toString(), tvSeason, tvEpisode) }
        } else {
            currentSource.movieUrlCandidates.ifEmpty { listOf(currentSource.movieUrl) }
                .map { it(movie.id.toString()) }
        }
    }
    val currentUrl = candidateUrls.getOrNull(currentCandidateIndex).orEmpty()
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
            val nextUrl = candidateUrls[currentCandidateIndex]
            webView.loadUrl(nextUrl)
            return
        }
        if (currentSourceIndex + 1 < allPrioritySources.size) {
            currentSourceIndex += 1
            currentCandidateIndex = 0
            val nextSource = allPrioritySources[currentSourceIndex]
            val nextCandidates = if (isTvPlayback) {
                nextSource.tvUrlCandidates.ifEmpty { listOf(nextSource.tvUrl) }
                    .map { it(movie.id.toString(), tvSeason, tvEpisode) }
            } else {
                nextSource.movieUrlCandidates.ifEmpty { listOf(nextSource.movieUrl) }
                    .map { it(movie.id.toString()) }
            }
            if (nextCandidates.isNotEmpty()) {
                webView.loadUrl(nextCandidates[0])
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                    }
                },
                    update = { webView -> webView.loadUrl(currentUrl) },
                    modifier = Modifier.fillMaxSize() // Fill full screen
            )
            
                // Cinematic Overlay (Title and Back Button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color.Black.copy(0.5f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            
                Spacer(Modifier.width(12.dp))
                Column {
                Text(movie.displayTitle, color = Color.White, fontWeight = FontWeight.Bold)
                    if (isTvPlayback) {
                    Text("S$tvSeason E$tvEpisode", color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
            }
        }
    }

    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.destroy()
            onPlaybackPositionUpdate(0L)
        }
    }
}
