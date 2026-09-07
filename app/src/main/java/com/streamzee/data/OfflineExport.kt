package com.streamzee.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*

/** Export a standalone video using only downloaded data, never the provider's network. */
@OptIn(UnstableApi::class)
suspend fun exportOfflineVideo(context: Context, item: DownloadItem, destination: Uri) {
    val appContext = context.applicationContext
    var temporary: File? = null
    var completed = false
    try {
        require(item.status == DownloadStatus.COMPLETED) { "Wait for the download to finish." }
        val url = requireNotNull(item.resolvedUrl) { "The downloaded video is missing." }
        temporary = withContext(Dispatchers.IO) {
            File.createTempFile("offline-export-", ".mp4", appContext.cacheDir)
        }
        val output = temporary
        withContext(Dispatchers.Main) {
            val factory = ExoPlayerAssetLoader.Factory(
                appContext, DefaultDecoderFactory.Builder(appContext).build(), Clock.DEFAULT,
                DefaultMediaSourceFactory(StreamDownloadManager.get(appContext).offlineDataSourceFactory()),
            )
            val transformer = Transformer.Builder(appContext)
                .setAssetLoaderFactory(factory)
                .build()
            try {
                suspendCancellableCoroutine<Unit> { continuation ->
                    transformer.addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (continuation.isActive) continuation.resumeWithException(exportException)
                        }
                    })
                    transformer.start(MediaItem.Builder().setUri(url).setMimeType(item.mimeType).build(), output.absolutePath)
                }
            } finally {
                transformer.cancel()
            }
        }
        withContext(Dispatchers.IO) {
            val stream = appContext.contentResolver.openOutputStream(destination, "wt")
                ?: error("Cannot write to the selected file.")
            stream.use { sink ->
                output.inputStream().use { source ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        sink.write(buffer, 0, count)
                    }
                }
            }
        }
        completed = true
    } finally {
        withContext(NonCancellable + Dispatchers.IO) {
            temporary?.delete()
            if (!completed) runCatching { DocumentsContract.deleteDocument(appContext.contentResolver, destination) }
        }
    }
}
