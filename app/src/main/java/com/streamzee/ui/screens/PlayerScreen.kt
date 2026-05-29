package com.streamzee.ui.screens

import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.View
import android.webkit.WebSettings
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.streamzee.data.PlaybackSource
import com.streamzee.data.TmdbMovie
import com.streamzee.data.movieTvPlayerSources

private val ScreenBg = Color(0xFF050508)
private val Purple = Color(0xFFA855F7)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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

    val context = LocalContext.current

    val activity = remember(context) {
        context as Activity
    }

    var isFullScreen by remember {
        mutableStateOf(false)
    }

    // NEW
    var showSourceDialog by remember {
        mutableStateOf(false)
    }

    val allPrioritySources = remember {
        movieTvPlayerSources
    }

    val startSourceIndex = remember(source) {
        allPrioritySources.indexOfFirst {
            it.id == source.id
        }.takeIf { it >= 0 } ?: 0
    }

    var lastKnownPosition by remember {
        mutableLongStateOf(resumePositionMs ?: 0L)
    }

    var currentSourceIndex by remember {
        mutableStateOf(startSourceIndex)
    }

    val currentSource = allPrioritySources[currentSourceIndex]

    var currentCandidateIndex by remember {
        mutableStateOf(0)
    }

    val isTvPlayback =
        tvSeason != null && tvEpisode != null

    val candidateUrls =
        remember(
            currentSourceIndex,
            currentCandidateIndex,
            tvSeason,
            tvEpisode
        ) {

            if (isTvPlayback) {

                currentSource.tvUrlCandidates
                    .ifEmpty {
                        listOf(currentSource.tvUrl)
                    }
                    .map {
                        it(
                            movie.tmdbID.toString(),
                            tvSeason,
                            tvEpisode
                        )
                    }

            } else {

                currentSource.movieUrlCandidates
                    .ifEmpty {
                        listOf(currentSource.movieUrl)
                    }
                    .map {
                        it(movie.tmdbID.toString())
                    }
            }
        }

    val currentUrl =
        candidateUrls
            .getOrNull(currentCandidateIndex)
            .orEmpty()

    val webViewRef =
        remember {
            mutableStateOf<WebView?>(null)
        }

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

        return blockedPatterns.any {
            url.contains(it, ignoreCase = true)
        }
    }

    fun tryNextCandidateOrSource(webView: WebView) {

        if (currentCandidateIndex + 1 < candidateUrls.size) {

            currentCandidateIndex += 1

            val nextUrl =
                candidateUrls[currentCandidateIndex]

            webView.loadUrl(nextUrl)

            return
        }

        if (currentSourceIndex + 1 < allPrioritySources.size) {

            currentSourceIndex += 1

            currentCandidateIndex = 0

            val nextSource =
                allPrioritySources[currentSourceIndex]

            val nextCandidates =
                if (isTvPlayback) {

                    nextSource.tvUrlCandidates
                        .ifEmpty {
                            listOf(nextSource.tvUrl)
                        }
                        .map {
                            it(
                                movie.tmdbID.toString(),
                                tvSeason,
                                tvEpisode
                            )
                        }

                } else {

                    nextSource.movieUrlCandidates
                        .ifEmpty {
                            listOf(nextSource.movieUrl)
                        }
                        .map {
                            it(movie.tmdbID.toString())
                        }
                }

            if (nextCandidates.isNotEmpty()) {
                webView.loadUrl(nextCandidates[0])
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        AndroidView(

            factory = { ctx ->

                WebView(ctx).apply {

                    webViewRef.value = this

                    overScrollMode = View.OVER_SCROLL_NEVER

                    setBackgroundColor(
                        android.graphics.Color.BLACK
                    )

                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        
                        // --- NEW BLOCKERS ---
                        setSupportMultipleWindows(true) // Required to intercept window.open calls
                        javaScriptCanOpenWindowsAutomatically = false

                        builtInZoomControls = false

                        displayZoomControls = false

                        setSupportZoom(false)

                        mixedContentMode =
                            WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    webChromeClient =
                        object : WebChromeClient() {
                            
                            override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message?
                                ): Boolean {
                                    // Kill any attempt to open a new window (popups)
                                    return false 
                                }

                            private var customView: View? = null

                            private var customViewCallback:
                                    CustomViewCallback? = null

                            override fun onPermissionRequest(
                                request: PermissionRequest
                            ) {
                                request.grant(request.resources)
                            }

                            override fun onShowCustomView(
                                view: View?,
                                callback: CustomViewCallback?
                            ) {

                                if (customView != null) {

                                    callback?.onCustomViewHidden()

                                    return
                                }

                                customView = view

                                customViewCallback = callback

                                val decorView =
                                    activity.window.decorView
                                            as FrameLayout

                                decorView.addView(
                                    customView,
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )

                                WindowCompat
                                    .setDecorFitsSystemWindows(
                                        activity.window,
                                        false
                                    )

                                val controller =
                                    WindowInsetsControllerCompat(
                                        activity.window,
                                        decorView
                                    )

                                controller.hide(
                                    WindowInsetsCompat.Type.systemBars()
                                )

                                controller.systemBarsBehavior =
                                    WindowInsetsControllerCompat
                                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                                isFullScreen = true

                                activity.requestedOrientation =
                                    ActivityInfo
                                        .SCREEN_ORIENTATION_LANDSCAPE
                            }

                            override fun onHideCustomView() {

                                val decorView =
                                    activity.window.decorView
                                            as FrameLayout

                                customView?.let {
                                    decorView.removeView(it)
                                }

                                customView = null

                                WindowCompat
                                    .setDecorFitsSystemWindows(
                                        activity.window,
                                        true
                                    )

                                WindowInsetsControllerCompat(
                                    activity.window,
                                    decorView
                                ).show(
                                    WindowInsetsCompat.Type.systemBars()
                                )

                                customViewCallback
                                    ?.onCustomViewHidden()

                                customViewCallback = null

                                isFullScreen = false

                                activity.requestedOrientation =
                                    ActivityInfo
                                        .SCREEN_ORIENTATION_PORTRAIT
                            }
                        }

                    webViewClient =
                        object : WebViewClient() {

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return false
                            }

                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest
                            ): WebResourceResponse? {

                                val url =
                                    request.url.toString()

                                if (shouldBlockUrl(url)) {

                                    return WebResourceResponse(
                                        "text/plain",
                                        "utf-8",
                                        null
                                    )
                                }

                                return super.shouldInterceptRequest(
                                    view,
                                    request
                                )
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: android.net.http.SslError?
                            ) {
                                handler?.proceed()
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError
                            ) {

                                if (request.isForMainFrame) {
                                    tryNextCandidateOrSource(view)
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView,
                                request: WebResourceRequest,
                                errorResponse: WebResourceResponse
                            ) {

                                if (
                                    request.isForMainFrame &&
                                    errorResponse.statusCode >= 400
                                ) {
                                    tryNextCandidateOrSource(view)
                                }
                            }
                        }

                    loadUrl(currentUrl)
                }
            },

            update = { webView ->

                if (webView.url != currentUrl) {
                    webView.loadUrl(currentUrl)
                }
            },

            modifier = Modifier.fillMaxSize()
        )

        if (!isFullScreen) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp)
                    .align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {

                IconButton(

                    onClick = {

                        activity.requestedOrientation =
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

                        onBack()
                    },

                    modifier = Modifier.background(
                        Color.Black.copy(0.5f),
                        CircleShape
                    )
                ) {

                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        null,
                        tint = Color.White
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column {

                    Text(
                        movie.displayTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    if (isTvPlayback) {

                        Text(
                            "S$tvSeason E$tvEpisode",
                            color = Purple,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

ExposedDropdownMenuBox(
    expanded = showSourceDialog,
    onExpandedChange = {
        showSourceDialog = !showSourceDialog
    }
) {

    Box(
        modifier = Modifier
            .menuAnchor(
                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                enabled = true
            )
            .background(
                Color.DarkGray.copy(0.6f),
                CircleShape
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
    {
        Text(
            text = "Source ${currentSourceIndex + 1}",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }

    ExposedDropdownMenu(
        expanded = showSourceDialog,
        onDismissRequest = {
            showSourceDialog = false
        }
    ) {

        allPrioritySources.forEachIndexed { index, src ->

            DropdownMenuItem(
                text = { Text("Source ${index + 1}") },
                onClick = {
                    currentSourceIndex = index
                    currentCandidateIndex = 0
                    showSourceDialog = false
                }
            )
        }
    }
}
            }
        }

    }

    DisposableEffect(Unit) {

        onDispose {

            val webView = webViewRef.value

            onPlaybackPositionUpdate(
                lastKnownPosition,
                tvSeason,
                tvEpisode
            )

            webView?.stopLoading()

            webView?.loadUrl("about:blank")

            webView?.destroy()

            webViewRef.value = null
        }
    }
}