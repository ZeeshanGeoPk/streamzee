package com.streamzee.music

import android.content.ComponentName
import android.content.Intent
import android.accessibilityservice.AccessibilityService
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streamzee.MainActivity
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MusicIntegrationTest {
    @Test fun serviceAcceptsQueueAndRepeatControls() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val future = withContext(Dispatchers.Main) {
            MediaController.Builder(context, SessionToken(context, ComponentName(context, MusicService::class.java))).buildAsync()
        }
        val controller = future.get(15, TimeUnit.SECONDS)
        try {
            withContext(Dispatchers.Main) {
                val first = MusicTrack("aqz-KE-bpKQ", "First", "Artist", null, 0)
                controller.setMediaItem(first.mediaItem())
                controller.addMediaItem(first.copy(id = "jNQXAC9IVRw", title = "Second").mediaItem())
                controller.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                controller.shuffleModeEnabled = true
            }
            delay(1000)
            withContext(Dispatchers.Main) {
                assertEquals(2, controller.mediaItemCount)
                assertEquals("First", controller.getMediaItemAt(0).mediaMetadata.title.toString())
                assertEquals(androidx.media3.common.Player.REPEAT_MODE_ALL, controller.repeatMode)
                assertTrue(controller.shuffleModeEnabled)
                controller.removeMediaItem(1)
                assertEquals(1, controller.mediaItemCount)
            }
        } finally {
            withContext(Dispatchers.Main) { controller.clearMediaItems(); MediaController.releaseFuture(future) }
        }
    }

    @Test fun liveAudioContinuesInBackground() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveMusic") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val track = withContext(Dispatchers.IO) { YouTubeMusicSource.search("Beethoven").first() }
        val future = withContext(Dispatchers.Main) {
            MediaController.Builder(context, SessionToken(context, ComponentName(context, MusicService::class.java))).buildAsync()
        }
        val controller = future.get(15, TimeUnit.SECONDS)
        try {
            withContext(Dispatchers.Main) {
                controller.setMediaItem(track.mediaItem())
                controller.prepare()
                controller.play()
            }
            withTimeout(60_000) {
                while (!withContext(Dispatchers.Main) { controller.isPlaying }) delay(500)
            }
            instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            delay(5000)
            assertTrue(withContext(Dispatchers.Main) { controller.isPlaying })
        } finally {
            withContext(Dispatchers.Main) { controller.pause(); controller.clearMediaItems(); MediaController.releaseFuture(future) }
        }
    }
}
