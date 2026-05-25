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
import androidx.compose.runtime.LaunchedEffect // Added for JS polling
import androidx.compose.runtime.mutableLongStateOf
import kotlin.time.Duration.Companion.seconds
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
    onPlaybackPositionUpdate: (Long, Int?, Int?) -> Unit,
    tvSeason: Int? = null,
    tvEpisode: Int? = null,
    modifier: Modifier = Modifier,
) {
    val allPrioritySources = remember { playerSources }
    val startSourceIndex = remember(source) {
        allPrioritySources.indexOfFirst { it.id == source.id }.takeIf { it >= 0 } ?: 0
    }
    // Initialize with the resume position passed from previous screen
    var lastKnownPosition by remember { mutableLongStateOf(resumePositionMs ?: 0L) }
    
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
            "google-analytics.com",
            "googletagmanager.com",
            "googletagservices.com",
            "doubleclick.net",
            "adservice.google",
            "pagead2.googlesyndication.com",
            "stats.g.doubleclick.net",
            "yt3.ggpht.com/ytc",
            "fonts.googleapis.com",
            "fonts.gstatic.com",
            "googleapis.com",
            "gstatic.com",
            "cdn.adx1.com",
            "intelligenceadx.com",
            "adsco.re",
            "mc.yandex",
            "bvtpk.com",
            "my.rtmark.net",
            "b7510.com",
            "gt.unbrownunflat.com",
            "im.malocacomals.com",
            "users.videasy.net",
            "nf.sixmossin.com",
            "realizationnewestfangs.com",
            "acscdn.com",
            "lt.taloseempest.com",
            "profitableratecpm.com",
            "preferencenail.com",
            "protrafficinspector.com",
            "s10.histats.com",
            "weirdopt.com",
            "cloudflareinsights.com",
            "kettledroopingcontinuation.com",
            "wayfarerorthodox.com",
            "woxaglasuy.net",
            "adeptspiritual.com",
            "calculating-laugh.com",
            "amavhxdlofklxjg.xyz",
            "u3qleufcm6vure326ktfpbj.cfd",
            "get64t9vqg8pnbex1y463o.rest",
            "usrpubtrk.com",
            "adexchangeclear.com",
            "rzjzjnavztycv.online",
            "cloudnestra.com",
            "neonhorizonworkshops.com",
            "popads",
            "popunder",
            "adclick",
            "googlesyndication.com",
            "amazon-adsystem.com",
            "adsystem.com",
            "facebook.net",
            "tracker",
            "analytics",
            "popunder"
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
            
            LaunchedEffect(webViewRef.value) {
                while (true) {
                    kotlinx.coroutines.delay(5.seconds) // Poll every 5 seconds
                    webViewRef.value?.evaluateJavascript(
                            """
                            (function() {
                                var v = document.querySelector('video');
                                if (v) return v.currentTime;
                                
                                // Try searching inside iframes (might be blocked, but worth a shot)
                                var iframes = document.getElementsByTagName('iframe');
                                for (var i = 0; i < iframes.length; i++) {
                                    try {
                                        var iv = iframes[i].contentWindow.document.querySelector('video');
                                        if (iv) return iv.currentTime;
                                    } catch(e) {}
                                }
                                return 0;
                            })();
                            """.trimIndent()
                        ) { result ->
                            // The result often comes wrapped in quotes like "120.5"
                            val cleanedResult = result.replace("\"", "")
                            val seconds = cleanedResult.toDoubleOrNull() ?: 0.0
                            if (seconds > 0) {
                                lastKnownPosition = (seconds * 1000).toLong()
                            }
                        }
                }
            }
        
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
                    // Get the current webView from the ref
                    val webView = webViewRef.value
                    
                    // 1. Save the actual tracked position (lastKnownPosition)
                    // If the JS polling worked, this will be the current second.
                    // If it failed, it will be the initial resumePositionMs.
                    onPlaybackPositionUpdate(lastKnownPosition, tvSeason, tvEpisode)
                    
                    // 2. Clean up memory
                    webView?.stopLoading()
                    webView?.loadUrl("about:blank")
                    webView?.destroy()
                    webViewRef.value = null
                }
            }
}
