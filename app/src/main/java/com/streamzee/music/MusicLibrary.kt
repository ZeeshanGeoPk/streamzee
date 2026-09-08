package com.streamzee.music

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class MusicTrack(val id: String, val title: String, val artist: String, val artwork: String?, val durationSeconds: Long)
data class MusicDownload(val track: MusicTrack, val requestId: Long)
data class MusicLibraryState(
    val favorites: List<MusicTrack> = emptyList(),
    val downloads: List<MusicDownload> = emptyList(),
    val downloadStatus: Map<String, String> = emptyMap(),
    val message: String? = null,
    val wifiOnly: Boolean = true,
)

class MusicLibrary private constructor(context: Context) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("music_library", Context.MODE_PRIVATE)
    private val downloadPrefs = this.context.getSharedPreferences("music_downloads", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val systemDownloads = this.context.getSystemService(DownloadManager::class.java)
    private val _state = MutableStateFlow(MusicLibraryState(
        favorites = read("favorites"), downloads = read("downloads"), wifiOnly = prefs.getBoolean("wifi_only", true),
    ))
    val state = _state.asStateFlow()
    private val pending = mutableSetOf<String>()
    init {
        scope.launch {
            while (isActive) {
                val snapshot = _state.value.downloads
                val status = withContext(Dispatchers.IO) {
                    snapshot.associate { it.track.id to status(it.requestId) }
                }
                _state.value = _state.value.copy(downloadStatus = status)
                delay(1500)
            }
        }
    }
    private inline fun <reified T> read(key: String): List<T> = runCatching {
        gson.fromJson<List<T>>((if (key == "downloads") downloadPrefs else prefs).getString(key, "[]"), object : TypeToken<List<T>>() {}.type)
    }.getOrNull().orEmpty()
    fun favorite(track: MusicTrack) {
        val old = _state.value.favorites
        val next = if (old.any { it.id == track.id }) old.filterNot { it.id == track.id } else listOf(track) + old
        _state.value = _state.value.copy(favorites = next)
        prefs.edit().putString("favorites", gson.toJson(next)).apply()
    }
    fun setWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean("wifi_only", enabled).apply()
        _state.value = _state.value.copy(wifiOnly = enabled)
    }
    fun message(text: String?) { _state.value = _state.value.copy(message = text) }
    private fun status(id: Long): String = runCatching {
        systemDownloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return@use "Missing"
            when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> "Saved"
                DownloadManager.STATUS_FAILED -> "Failed — remove and retry"
                DownloadManager.STATUS_PAUSED -> "Waiting for network"
                DownloadManager.STATUS_RUNNING -> {
                    val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    if (total > 0) "Downloading ${bytes * 100 / total}%" else "Downloading"
                }
                else -> "Queued"
            }
        }
    }.getOrDefault("Unavailable")
    fun exportName(track: MusicTrack): String {
        val download = _state.value.downloads.firstOrNull { it.track.id == track.id }
        val mime = download?.let { systemDownloads.getMimeTypeForDownloadedFile(it.requestId) }.orEmpty()
        val extension = if (mime.contains("webm")) "webm" else if (mime.contains("mpeg")) "mp3" else "m4a"
        val title = track.title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(100)
        return "$title.$extension"
    }
    fun localUri(id: String): Uri? {
        val item = _state.value.downloads.firstOrNull { it.track.id == id } ?: return null
        return systemDownloads.getUriForDownloadedFile(item.requestId)
    }
    fun download(track: MusicTrack) {
        if (_state.value.downloads.any { it.track.id == track.id } || !pending.add(track.id)) return
        message("Resolving ${track.title}…")
        scope.launch {
            try {
                val requestId = withContext(Dispatchers.IO) {
                    val audio = YouTubeMusicSource.resolveAudio(track.id)
                    val request = DownloadManager.Request(Uri.parse(audio.url)).setMimeType(audio.mimeType)
                        .setTitle(track.title).setDescription(track.artist)
                        .setAllowedOverRoaming(false)
                        .setAllowedNetworkTypes(if (_state.value.wifiOnly) DownloadManager.Request.NETWORK_WIFI else DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .addRequestHeader("User-Agent", YouTubeMusicSource.USER_AGENT)
                        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MUSIC, "${track.id}.audio")
                    systemDownloads.enqueue(request)
                }
                val next = _state.value.downloads + MusicDownload(track, requestId)
                _state.value = _state.value.copy(downloads = next, message = "Download queued")
                downloadPrefs.edit().putString("downloads", gson.toJson(next)).apply()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message("Download failed: ${e.message}")
            } finally { pending.remove(track.id) }
        }
    }
    fun removeDownload(track: MusicTrack) {
        _state.value.downloads.firstOrNull { it.track.id == track.id }?.let { systemDownloads.remove(it.requestId) }
        val next = _state.value.downloads.filterNot { it.track.id == track.id }
        _state.value = _state.value.copy(downloads = next)
        downloadPrefs.edit().putString("downloads", gson.toJson(next)).apply()
        File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "${track.id}.audio").delete()
    }
    companion object {
        @Volatile private var instance: MusicLibrary? = null
        fun get(context: Context): MusicLibrary = instance ?: synchronized(this) {
            instance ?: MusicLibrary(context).also { instance = it }
        }
    }
}
