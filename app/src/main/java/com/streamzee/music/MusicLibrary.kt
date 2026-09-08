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
data class MusicPlaylist(val id: String, val name: String, val tracks: List<MusicTrack> = emptyList())

data class MusicDownload(val track: MusicTrack, val requestId: Long)
data class MusicLibraryState(
    val recommendations: List<MusicRecommendation> = emptyList(),
    val hiddenRecommendations: Set<String> = emptySet(),
    val isLoadingRecommendations: Boolean = false,
    val recommendationError: String? = null,
    val playlists: List<MusicPlaylist> = emptyList(),
    val recent: List<MusicTrack> = emptyList(),
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
        recommendations = read("recommendations"),
        hiddenRecommendations = prefs.getStringSet("hidden_recommendations", emptySet()).orEmpty().toSet(),
        playlists = read("playlists"), recent = read("recent"), favorites = read("favorites"), downloads = read("downloads"), wifiOnly = prefs.getBoolean("wifi_only", true),
    ))
    val state = _state.asStateFlow()
    private var recommendationsJob: Job? = null
    private var recommendationKey = prefs.getString("recommendation_key", "").orEmpty()
    private var recommendationTime = prefs.getLong("recommendation_time", 0L)
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
    fun refreshRecommendations(force: Boolean = false) {
        val snapshot = _state.value
        val seeds = recommendationSeeds(snapshot)
        val excluded = (snapshot.recent + snapshot.favorites + snapshot.playlists.flatMap { it.tracks })
            .map { it.id }.toSet() + snapshot.hiddenRecommendations
        val key = gson.toJson(seeds) + excluded.sorted().joinToString(",")
        if (key == recommendationKey && (snapshot.isLoadingRecommendations ||
            (!force && System.currentTimeMillis() - recommendationTime in 0 until 6 * 60 * 60_000L))) return
        recommendationsJob?.cancel()
        val existing = snapshot.recommendations.filterNot { it.track.id in excluded }
        if (recommendationKey != key) recommendationTime = 0L
        recommendationKey = key
        if (seeds.isEmpty()) {
            _state.value = snapshot.copy(recommendations = emptyList(), isLoadingRecommendations = false, recommendationError = null)
            prefs.edit().remove("recommendations").remove("recommendation_key").remove("recommendation_time").apply()
            recommendationTime = 0L
            return
        }
        _state.value = snapshot.copy(recommendations = existing, isLoadingRecommendations = true, recommendationError = null)
        recommendationsJob = scope.launch {
            val candidates = linkedMapOf<String, List<MusicTrack>>()
            var failures = 0
            for (seed in seeds) {
                try {
                    candidates[seed.artist] = withContext(Dispatchers.IO) { YouTubeMusicSource.search(seed.artist) }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    failures++
                }
            }
            val next = (if (candidates.isEmpty()) existing else rankRecommendations(seeds, candidates, excluded))
                .filterNot { it.track.id in _state.value.hiddenRecommendations }
            _state.value = _state.value.copy(recommendations = next, isLoadingRecommendations = false,
                recommendationError = if (failures > 0) "Some recommendations could not refresh. Check your connection and retry." else null)
            if (failures > 0) recommendationTime = 0L
            if (candidates.isNotEmpty()) {
                recommendationTime = if (failures == 0) System.currentTimeMillis() else 0L
                prefs.edit().putString("recommendations", gson.toJson(next)).putString("recommendation_key", key)
                    .putLong("recommendation_time", recommendationTime).apply()
            }
        }
    }
    fun hideRecommendation(id: String) {
        val hidden = _state.value.hiddenRecommendations + id
        val next = _state.value.recommendations.filterNot { it.track.id == id }
        _state.value = _state.value.copy(hiddenRecommendations = hidden, recommendations = next)
        prefs.edit().putStringSet("hidden_recommendations", hidden).putString("recommendations", gson.toJson(next)).apply()
    }
    fun resetRecommendationFeedback() {
        _state.value = _state.value.copy(hiddenRecommendations = emptySet())
        prefs.edit().remove("hidden_recommendations").apply()
        refreshRecommendations(force = true)
    }
    fun recordPlayed(track: MusicTrack) {
        val next = (listOf(track) + _state.value.recent.filterNot { it.id == track.id }).take(40)
        _state.value = _state.value.copy(recent = next)
        prefs.edit().putString("recent", gson.toJson(next)).apply()
    }
    fun createPlaylist(name: String): String? {
        val cleaned = name.trim().take(60)
        if (cleaned.isEmpty()) { message("Enter a playlist name"); return null }
        val playlist = MusicPlaylist(java.util.UUID.randomUUID().toString(), cleaned)
        savePlaylists(_state.value.playlists + playlist)
        return playlist.id
    }
    fun renamePlaylist(id: String, name: String) {
        val cleaned = name.trim().take(60)
        if (cleaned.isEmpty()) return
        savePlaylists(_state.value.playlists.map { if (it.id == id) it.copy(name = cleaned) else it })
    }
    fun deletePlaylist(id: String) = savePlaylists(_state.value.playlists.filterNot { it.id == id })
    fun addToPlaylist(id: String, track: MusicTrack) {
        savePlaylists(_state.value.playlists.map { if (it.id == id) it.copy(tracks = (it.tracks + track).distinctBy { song -> song.id }) else it })
        message("Added to playlist")
    }
    fun removeFromPlaylist(id: String, trackId: String) = savePlaylists(_state.value.playlists.map {
        if (it.id == id) it.copy(tracks = it.tracks.filterNot { track -> track.id == trackId }) else it
    })
    fun movePlaylistTrack(id: String, trackId: String, offset: Int) = savePlaylists(_state.value.playlists.map {
        if (it.id == id) it.copy(tracks = moveMusicTrack(it.tracks, trackId, offset)) else it
    })
    private fun savePlaylists(playlists: List<MusicPlaylist>) {
        _state.value = _state.value.copy(playlists = playlists)
        prefs.edit().putString("playlists", gson.toJson(playlists)).apply()
    }
    fun clearRecent() {
        _state.value = _state.value.copy(recent = emptyList())
        prefs.edit().putString("recent", "[]").apply()
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

internal fun moveMusicTrack(tracks: List<MusicTrack>, id: String, offset: Int): List<MusicTrack> {
    val index = tracks.indexOfFirst { it.id == id }
    if (index < 0) return tracks
    val target = (index + offset).coerceIn(tracks.indices)
    return tracks.toMutableList().apply { add(target, removeAt(index)) }
}
