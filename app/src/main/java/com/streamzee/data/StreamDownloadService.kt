package com.streamzee.data

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import com.streamzee.MainActivity
import com.streamzee.R

@OptIn(UnstableApi::class)
class StreamDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DOWNLOAD_CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description,
) {
    override fun getDownloadManager(): DownloadManager =
        StreamDownloadManager.get(this).media3DownloadManager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, DOWNLOAD_JOB_ID)

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int,
    ): Notification {
        val active = downloads.filter {
            it.state == Download.STATE_DOWNLOADING ||
                it.state == Download.STATE_QUEUED ||
                it.state == Download.STATE_RESTARTING
        }
        val downloadedBytes = active.sumOf { it.bytesDownloaded }
        val totalBytes = active.sumOf { it.contentLength.coerceAtLeast(0L) }
        val progress = if (totalBytes > 0L) {
            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                if (active.size == 1) "Downloading 1 video" else "Downloading ${active.size} videos"
            )
            .setContentText(
                if (notMetRequirements != 0) {
                    "Waiting for the configured network"
                } else {
                    "Streamzee offline downloads"
                }
            )
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, totalBytes <= 0L)
            .build()
    }

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "streamzee_downloads"
        private const val FOREGROUND_NOTIFICATION_ID = 4301
        private const val DOWNLOAD_JOB_ID = 4302
    }
}
