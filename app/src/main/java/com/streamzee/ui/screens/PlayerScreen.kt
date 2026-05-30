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
import androidx.compose.foundation.layout.aspectRatio
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
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
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
                        android.graphics.Color.WHITE
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
                        userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                        
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

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: android.net.http.SslError?
                            ) {
                                handler?.proceed()
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

                        modifier =
                            if (isFullScreen) {

                                Modifier.fillMaxSize()

                            } else {

                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    // Center Vertical Allignment
                                    .align(Alignment.Center)
                            }
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