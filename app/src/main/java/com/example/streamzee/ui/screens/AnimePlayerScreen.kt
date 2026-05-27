package com.example.streamzee.ui.screens

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.streamzee.data.AnikotoShow

@Composable
fun animePlayerScreen(
    show: AnikotoShow,
    episode: Int,
    streamUrl: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val purple = Color(0xFFA855F7)
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val activity = context as? android.app.Activity
    var isFullScreen by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webViewRef.value = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center // Center video in portrait
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    // FIX 1: Force Hardware Acceleration for the video layer
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    
                    // FIX 2: Ensure background doesn't block the video surface
                    setBackgroundColor(android.graphics.Color.BLACK)

                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        
                        @Suppress("DEPRECATION")
                        safeBrowsingEnabled = false 

                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                    }

                    webViewClient = object : WebViewClient() {
                        // FIX 1: This stops the page from redirecting to an ad URL
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val newUrl = request?.url?.toString() ?: return false
                            
                            // Allow the player domain and internal data/blobs
                            if (newUrl.contains("megaplay.buzz") || newUrl.startsWith("data:") || newUrl.startsWith("blob:")) {
                                return false // Let it load
                            }
                            
                            // Block any other navigation (this is the ad redirect)
                            return true 
                        }

                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                            handler?.proceed()
                        }

                        // FIX 2: Block ad scripts from loading in the background
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null
                            val adDomains = listOf(
                                "popads", "popunder", "adclick", "doubleclick", "googlesyndication",
                                "mobicow", "adservice", "analytics", "asacdn", "clksite", "adx1",
                                "nf.sixmossin.com", "realizationnewestfangs", "kettledroopingcontinuation"
                            )
                            if (adDomains.any { url.contains(it, ignoreCase = true) }) {
                                return WebResourceResponse("text/plain", "utf-8", null)
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }
                    
                    webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest) {
                                request.grant(request.resources)
                            }

                            // Triggered when full-screen button is clicked in the player
                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                isFullScreen = true
                                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                            }

                            // Triggered when exiting full-screen
                            override fun onHideCustomView() {
                                isFullScreen = false
                                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                            }
                        }

                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun onTimeUpdate(seconds: Float) {
                            // Logic for progress tracking goes here
                        }
                    }, "Android")

                    val htmlWrapper = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                            <style>
                                body, html { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
                                iframe { border: none; width: 100%; height: 100%; position: absolute; top: 0; left: 0; }
                            </style>
                        </head>
                        <body>
                            <iframe 
                                id="player" 
                                src="$streamUrl" 
                                /* The 'allow-top-navigation' is omitted to block redirects */
                                sandbox="allow-scripts allow-forms allow-same-origin allow-presentation"
                                allow="autoplay; fullscreen; encrypted-media; picture-in-picture" 
                                allowfullscreen 
                                scrolling="no">
                            </iframe>
                            <script>
                                window.addEventListener("message", function(event) {
                                    if (event.data.event === "time") {
                                        Android.onTimeUpdate(event.data.time);
                                    }
                                    if (event.data.type === "watching-log") {
                                        Android.onTimeUpdate(event.data.currentTime);
                                    }
                                });
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    
                    // Update: Remove 'position: absolute' from CSS to prevent clipping
                    val updatedHtml = htmlWrapper.replace("position: absolute; top: 0; left: 0;", "")
                    loadDataWithBaseURL("https://megaplay.buzz", updatedHtml, "text/html", "UTF-8", "https://megaplay.buzz/")
                    webViewRef.value = this
                }
                    },
                    modifier = if (isFullScreen) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f) // Standard video ratio for portrait view
                    }
                )

        // Hide your custom overlay when in full screen to avoid clutter
        if (!isFullScreen) {
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
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = show.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "Episode $episode",
                    color = purple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}