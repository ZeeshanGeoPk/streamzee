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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.streamzee.data.AllAnimeShow
import com.example.streamzee.data.AllAnimeSourceUrl

@Composable
fun animePlayerScreen(
    show: AllAnimeShow,
    episode: Int,
    onBack: () -> Unit,
    resolveEpisode: suspend (String, String) -> List<AllAnimeSourceUrl>,
    modifier: Modifier = Modifier,
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sourceUrls by remember { mutableStateOf<List<AllAnimeSourceUrl>>(emptyList()) }
    var currentCandidateIndex by remember { mutableStateOf(0) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val currentUrl = sourceUrls.getOrNull(currentCandidateIndex)?.sourceUrl.orEmpty()

    LaunchedEffect(show.uid, episode) {
        isLoading = true
        errorMessage = null
        currentCandidateIndex = 0
        try {
            val resolved = resolveEpisode(show.uid, episode.toString())
                .sortedByDescending { it.priority ?: 0f }
            sourceUrls = resolved
            if (resolved.isEmpty()) {
                errorMessage = "No sources found for episode $episode."
            }
        } catch (exception: Exception) {
            errorMessage = "Unable to resolve anime source: ${exception.message ?: "unknown error"}"
            sourceUrls = emptyList()
        }
        isLoading = false
    }

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

    fun tryNextCandidate(webView: WebView) {
        if (currentCandidateIndex + 1 < sourceUrls.size) {
            currentCandidateIndex += 1
            val nextUrl = sourceUrls[currentCandidateIndex].sourceUrl.orEmpty()
            if (nextUrl.isNotBlank()) {
                webView.loadUrl(nextUrl)
            }
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
                text = "${show.name} — Episode $episode",
                style = MaterialTheme.typography.headlineLarge,
            )
            Button(onClick = onBack) {
                Text("Back")
            }
        }

        if (isLoading) {
            Text(
                text = "Resolving anime sources…",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        errorMessage?.let { message ->
            if (message.isNotBlank()) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        if (!isLoading && currentUrl.isNotBlank()) {
            Text(
                text = "Playing via AllAnime resolution. If the player fails, the next available source will be tried automatically.",
                style = MaterialTheme.typography.bodySmall,
            )
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
                                        tryNextCandidate(view)
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    errorResponse: WebResourceResponse,
                                ) {
                                    if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                                        tryNextCandidate(view)
                                    }
                                }
                            }
                            
                            val isDirectVideo = currentUrl.startsWith("http") && (
                                currentUrl.contains(".mp4") || 
                                currentUrl.contains(".m3u8") || 
                                currentUrl.contains(".webm") ||
                                currentUrl.contains("googlevideo.com")
                            )

                            if (isDirectVideo) {
                                val isM3u8 = currentUrl.contains(".m3u8")
                                val html = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                        <meta charset="utf-8">
                                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                        <style>
                                            * { margin: 0; padding: 0; box-sizing: border-box; }
                                            html, body { width: 100%; height: 100%; background: #000; overflow: hidden; display: flex; align-items: center; justify-content: center; }
                                            video { width: 100%; height: 100%; object-fit: contain; display: block; }
                                        </style>
                                    </head>
                                    <body>
                                        <video id="v" src="${if (isM3u8) "" else currentUrl}" autoplay controls playsinline crossorigin="anonymous"></video>
                                        ${if (isM3u8) """
                                        <script src="https://cdn.jsdelivr.net/npm/hls.js@latest/dist/hls.min.js"></script>
                                        <script>
                                            const video = document.getElementById('v');
                                            const src = "$currentUrl";
                                            if (Hls.isSupported()) {
                                                const hls = new Hls();
                                                hls.loadSource(src);
                                                hls.attachMedia(video);
                                                hls.on(Hls.Events.MANIFEST_PARSED, () => {
                                                    video.play().catch(() => {});
                                                });
                                            } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                                                video.src = src;
                                            }
                                        </script>
                                        """ else ""}
                                    </body>
                                    </html>
                                """.trimIndent()
                                loadDataWithBaseURL("https://allmanga.to", html, "text/html", "utf-8", null)
                            } else {
                                loadUrl(currentUrl)
                            }
                            webViewRef.value = this
                        }
                    },
                    update = { webView ->
                        if (currentUrl.isNotBlank()) {
                            val isDirectVideo = currentUrl.startsWith("http") && (
                                currentUrl.contains(".mp4") || 
                                currentUrl.contains(".m3u8") || 
                                currentUrl.contains(".webm") ||
                                currentUrl.contains("googlevideo.com")
                            )

                            if (isDirectVideo) {
                                val isM3u8 = currentUrl.contains(".m3u8")
                                val html = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                        <meta charset="utf-8">
                                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                        <style>
                                            * { margin: 0; padding: 0; box-sizing: border-box; }
                                            html, body { width: 100%; height: 100%; background: #000; overflow: hidden; display: flex; align-items: center; justify-content: center; }
                                            video { width: 100%; height: 100%; object-fit: contain; display: block; }
                                        </style>
                                    </head>
                                    <body>
                                        <video id="v" src="${if (isM3u8) "" else currentUrl}" autoplay controls playsinline crossorigin="anonymous"></video>
                                        ${if (isM3u8) """
                                        <script src="https://cdn.jsdelivr.net/npm/hls.js@latest/dist/hls.min.js"></script>
                                        <script>
                                            const video = document.getElementById('v');
                                            const src = "$currentUrl";
                                            if (Hls.isSupported()) {
                                                const hls = new Hls();
                                                hls.loadSource(src);
                                                hls.attachMedia(video);
                                                hls.on(Hls.Events.MANIFEST_PARSED, () => {
                                                    video.play().catch(() => {});
                                                });
                                            } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                                                video.src = src;
                                            }
                                        </script>
                                        """ else ""}
                                    </body>
                                    </html>
                                """.trimIndent()
                                webView.loadDataWithBaseURL("https://allmanga.to", html, "text/html", "utf-8", null)
                            } else {
                                webView.loadUrl(currentUrl)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.destroy()
        }
    }
}
