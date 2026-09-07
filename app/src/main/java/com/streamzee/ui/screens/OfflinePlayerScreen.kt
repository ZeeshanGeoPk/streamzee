package com.streamzee.ui.screens

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.streamzee.data.StreamDownloadManager
import kotlinx.coroutines.delay

private tailrec fun Context.findPlayerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findPlayerActivity()
    else -> null
}

@OptIn(UnstableApi::class)
@Composable
fun offlinePlayerScreen(downloadId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context.findPlayerActivity()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val manager = remember(context) { StreamDownloadManager.get(context) }
    val player = remember(downloadId) { manager.createOfflinePlayer(context, downloadId) }
    var error by remember(downloadId) { mutableStateOf<String?>(null) }
    var playing by remember(downloadId) { mutableStateOf(false) }
    var fullscreen by remember(downloadId) { mutableStateOf(false) }
    var controlsVisible by remember(downloadId) { mutableStateOf(false) }
    var playerView by remember(downloadId) { mutableStateOf<PlayerView?>(null) }
    val originalOrientation = remember(activity) { activity?.requestedOrientation }

    fun savePosition() {
        player?.let {
            manager.saveOfflinePosition(downloadId,
                if (it.playbackState == Player.STATE_ENDED) 0L else it.currentPosition)
        }
    }

    DisposableEffect(activity, fullscreen) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousBehavior = controller?.systemBarsBehavior
        val originalCutoutMode = if (Build.VERSION.SDK_INT >= 28) window?.attributes?.layoutInDisplayCutoutMode else null
        if (fullscreen) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            if (Build.VERSION.SDK_INT >= 28 && window != null) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        onDispose {
            if (fullscreen) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                previousBehavior?.let { controller?.systemBarsBehavior = it }
                if (Build.VERSION.SDK_INT >= 28 && window != null && originalCutoutMode != null) {
                    window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = originalCutoutMode }
                }
            }
        }
    }
    LaunchedEffect(fullscreen, playerView) {
        if (fullscreen) {
            playerView?.hideController()
            controlsVisible = false
        }
    }

    BackHandler {
        if (fullscreen) {
            fullscreen = false
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else onBack()
    }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(exception: PlaybackException) {
                error = "Cannot play this download. It may be incomplete or use an unsupported format. Try downloading it again."
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) savePosition()
            }
        }
        var resumeOnStart = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumeOnStart = player?.playWhenReady == true
                    savePosition()
                    player?.pause()
                }
                Lifecycle.Event.ON_START -> if (resumeOnStart) {
                    player?.play()
                    resumeOnStart = false
                }
                else -> Unit
            }
        }
        player?.addListener(listener)
        playing = player?.isPlaying == true
        lifecycle.addObserver(observer)
        onDispose {
            savePosition()
            lifecycle.removeObserver(observer)
            player?.removeListener(listener)
            player?.release()
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(player) {
        while (player != null) {
            delay(5_000)
            savePosition()
        }
    }
    Box(modifier.fillMaxSize().background(Color.Black)
        .then(if (fullscreen) Modifier else Modifier.navigationBarsPadding())) {
        if (player != null) {
            AndroidView(
                factory = { PlayerView(it).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 3_000
                    controllerAutoShow = false
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        controlsVisible = visibility == View.VISIBLE
                    })
                    playerView = this
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                } },
                update = { it.player = player; it.keepScreenOn = playing },
                onRelease = {
                    it.setControllerVisibilityListener(null as PlayerView.ControllerVisibilityListener?)
                    it.player = null
                    it.keepScreenOn = false
                    playerView = null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (player == null || error != null) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "This download is not available offline.", color = Color.White)
                if (player != null) TextButton(onClick = { error = null; player.prepare(); player.play() }) { Text("Retry") }
            }
        }
        if (!fullscreen || controlsVisible || player == null || error != null) Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            TextButton(onClick = {
                fullscreen = !fullscreen
                activity?.requestedOrientation = if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }) { Text(if (fullscreen) "Exit fullscreen" else "Fullscreen", color = Color.White) }
        }
    }
}
