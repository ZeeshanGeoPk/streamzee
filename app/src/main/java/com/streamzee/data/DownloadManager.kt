package com.streamzee.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.scheduler.Requirements
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class DownloadMediaType {
    MOVIE,
    TV_EPISODE,
    ANIME_EPISODE,
}

enum class DownloadStatus {
    RESOLVING,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    REMOVING,
}

enum class DownloadQuality(val label: String) {
    DATA_SAVER("Data saver"),
    STANDARD("Standard"),
    BEST("Best"),
}

data class DownloadItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val mediaType: DownloadMediaType,
    val status: DownloadStatus,
    val downloadedBytes: Long = 0L,
    val sizeBytes: Long = 0L,
    val percentDownloaded: Float = 0f,
    val errorMessage: String? = null,
    val pageUrl: String,
    val resolvedUrl: String? = null,
    val mimeType: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val createdAtMs: Long = System.currentTimeMillis(),
)

data class DownloadSettings(
    val wifiOnly: Boolean = true,
    val maxParallelDownloads: Int = 2,
    val quality: DownloadQuality = DownloadQuality.STANDARD,
)

data class DownloadStorage(
    val usedBytes: Long = 0L,
    val availableBytes: Long = 0L,
    val totalBytes: Long = 0L,
)

data class CapturedMediaStream(
    val url: String,
    val mimeType: String?,
    val requestHeaders: Map<String, String>,
    val pageUrl: String,
)

private data class DownloadCatalogEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val mediaType: DownloadMediaType,
    val pageUrl: String,
    val resolvedUrl: String? = null,
    val mimeType: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val resolving: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis(),
)

@OptIn(UnstableApi::class)
class StreamDownloadManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences("streamzee_downloads", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val databaseProvider = StandaloneDatabaseProvider(appContext)
    private val cacheDirectory = File(appContext.filesDir, "offline_media")
    private val cache = SimpleCache(cacheDirectory, NoOpCacheEvictor(), databaseProvider)
    private val upstreamFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(STREAM_USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(20_000)
        .setReadTimeoutMs(30_000)
    private val resolvingUpstreamFactory = ResolvingDataSource.Factory(
        upstreamFactory
    ) { dataSpec ->
        val headers = headersFor(dataSpec.uri)
        if (headers.isEmpty()) dataSpec else dataSpec.withAdditionalHeaders(headers)
    }
    val cacheDataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(resolvingUpstreamFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    private val downloaderExecutor = Executors.newFixedThreadPool(3)
    private val resolverHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    val media3DownloadManager = DownloadManager(
        appContext,
        androidx.media3.exoplayer.offline.DefaultDownloadIndex(databaseProvider),
        DefaultDownloaderFactory(cacheDataSourceFactory, downloaderExecutor),
    )

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<DownloadSettings> = _settings.asStateFlow()

    private val _storage = MutableStateFlow(DownloadStorage())
    val storage: StateFlow<DownloadStorage> = _storage.asStateFlow()

    private val _downloadsPaused = MutableStateFlow(false)
    val downloadsPaused: StateFlow<Boolean> = _downloadsPaused.asStateFlow()

    private var catalog: MutableList<DownloadCatalogEntry> = readCatalog()
        .map {
            if (it.resolving) {
                it.copy(
                    resolving = false,
                    errorMessage = "Open this title, start playback, then tap Download in the player.",
                )
            } else {
                it
            }
        }
        .toMutableList()

    init {
        persistCatalog()
        media3DownloadManager.addListener(
            object : DownloadManager.Listener {
                override fun onInitialized(downloadManager: DownloadManager) {
                    applySettings(_settings.value)
                    refreshSoon()
                }

                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?,
                ) {
                    if (finalException != null) {
                        updateCatalog(download.request.id) {
                            it.copy(errorMessage = finalException.message ?: "Download failed.")
                        }
                    }
                    refreshSoon()
                }

                override fun onDownloadRemoved(
                    downloadManager: DownloadManager,
                    download: Download,
                ) {
                    refreshSoon()
                }

                override fun onDownloadsPausedChanged(
                    downloadManager: DownloadManager,
                    downloadsPaused: Boolean,
                ) {
                    _downloadsPaused.value = downloadsPaused
                    refreshSoon()
                }
            }
        )

        applySettings(_settings.value)
        scope.launch {
            while (true) {
                refreshState()
                delay(1_000L)
            }
        }
    }

    fun queue(
        id: String,
        title: String,
        subtitle: String,
        imageUrl: String?,
        mediaType: DownloadMediaType,
        pageUrl: String,
    ) {
        val entry = DownloadCatalogEntry(
            id = id,
            title = title,
            subtitle = subtitle,
            imageUrl = imageUrl,
            mediaType = mediaType,
            pageUrl = pageUrl,
        )
        synchronized(this) {
            catalog.removeAll { it.id == id }
            catalog.add(0, entry)
            persistCatalog()
        }
        refreshSoon()
    }

    fun markResolved(
        id: String,
        mediaUrl: String,
        mimeType: String?,
        requestHeaders: Map<String, String> = emptyMap(),
    ) {
        val normalizedMimeType = mimeType ?: inferMimeType(mediaUrl)
        val sanitizedHeaders = sanitizeRequestHeaders(requestHeaders)
        val entry = synchronized(this) {
            val current = catalog.firstOrNull { it.id == id } ?: return
            val updated = current.copy(
                resolvedUrl = mediaUrl,
                mimeType = normalizedMimeType,
                requestHeaders = sanitizedHeaders,
                errorMessage = null,
                resolving = false,
            )
            catalog[catalog.indexOf(current)] = updated
            persistCatalog()
            updated
        }

        scope.launch {
            val preparedUrl = if (normalizedMimeType == MimeTypes.APPLICATION_M3U8) {
                selectHlsVariant(
                    masterUrl = mediaUrl,
                    headers = sanitizedHeaders,
                    quality = _settings.value.quality,
                )
            } else {
                mediaUrl
            }
            updateCatalog(id) {
                it.copy(resolvedUrl = preparedUrl)
            }
            val request = DownloadRequest.Builder(id, Uri.parse(preparedUrl))
                .setMimeType(normalizedMimeType)
                .setData(entry.id.toByteArray(Charsets.UTF_8))
                .build()
            DownloadService.sendAddDownload(
                appContext,
                StreamDownloadService::class.java,
                request,
                true,
            )
            refreshSoon()
        }
    }

    fun markResolutionFailed(id: String, message: String) {
        updateCatalog(id) {
            it.copy(
                resolving = false,
                errorMessage = message,
            )
        }
        refreshSoon()
    }

    fun retry(id: String) {
        val entry = synchronized(this) { catalog.firstOrNull { it.id == id } } ?: return
        val resolvedUrl = entry.resolvedUrl
        if (resolvedUrl.isNullOrBlank()) {
            updateCatalog(id) {
                it.copy(
                    resolving = false,
                    errorMessage = "Open this title, start playback, then tap Download in the player.",
                )
            }
            refreshSoon()
            return
        }
        markResolved(
            id = id,
            mediaUrl = resolvedUrl,
            mimeType = entry.mimeType,
            requestHeaders = entry.requestHeaders,
        )
    }

    fun pause(id: String) {
        DownloadService.sendSetStopReason(
            appContext,
            StreamDownloadService::class.java,
            id,
            USER_PAUSED_STOP_REASON,
            true,
        )
    }

    fun resume(id: String) {
        DownloadService.sendSetStopReason(
            appContext,
            StreamDownloadService::class.java,
            id,
            Download.STOP_REASON_NONE,
            true,
        )
    }

    fun pauseAll() {
        DownloadService.sendPauseDownloads(
            appContext,
            StreamDownloadService::class.java,
            true,
        )
    }

    fun resumeAll() {
        DownloadService.sendResumeDownloads(
            appContext,
            StreamDownloadService::class.java,
            true,
        )
    }

    fun remove(id: String) {
        preferences.edit().remove("offline_position_$id").apply()
        val hasResolvedDownload = synchronized(this) {
            catalog.firstOrNull { it.id == id }?.resolvedUrl != null
        }
        synchronized(this) {
            catalog.removeAll { it.id == id }
            persistCatalog()
        }
        if (hasResolvedDownload) {
            DownloadService.sendRemoveDownload(
                appContext,
                StreamDownloadService::class.java,
                id,
                true,
            )
        }
        refreshSoon()
    }

    fun updateSettings(settings: DownloadSettings) {
        val sanitized = settings.copy(
            maxParallelDownloads = settings.maxParallelDownloads.coerceIn(1, 3)
        )
        preferences.edit()
            .putBoolean(KEY_WIFI_ONLY, sanitized.wifiOnly)
            .putInt(KEY_MAX_PARALLEL, sanitized.maxParallelDownloads)
            .putString(KEY_QUALITY, sanitized.quality.name)
            .apply()
        _settings.value = sanitized
        applySettings(sanitized)
    }

    fun offlineDataSourceFactory(): CacheDataSource.Factory = createOfflineDataSourceFactory(cache)

    fun offlinePosition(id: String): Long = preferences.getLong("offline_position_$id", 0L)

    fun saveOfflinePosition(id: String, positionMs: Long) {
        preferences.edit().putLong("offline_position_$id", positionMs.coerceAtLeast(0L)).apply()
    }

    fun createOfflinePlayer(context: Context, id: String): ExoPlayer? {
        val item = _downloads.value.firstOrNull {
            it.id == id && it.status == DownloadStatus.COMPLETED
        } ?: return null
        val url = item.resolvedUrl ?: return null

        val mediaItem = MediaItem.Builder()
            .setMediaId(id)
            .setUri(url)
            .setMimeType(item.mimeType)
            .build()
        val mediaSourceFactory = DefaultMediaSourceFactory(offlineDataSourceFactory())
        return ExoPlayer.Builder(
            context,
            DefaultRenderersFactory(context),
            mediaSourceFactory,
        ).setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build().apply {
            setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
            setMediaItem(mediaItem, offlinePosition(id))
            prepare()
            playWhenReady = true
        }
    }

    private fun applySettings(settings: DownloadSettings) {
        mainHandler.post {
            media3DownloadManager.maxParallelDownloads = settings.maxParallelDownloads
            media3DownloadManager.requirements = Requirements(
                if (settings.wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK
            )
        }
    }

    private fun refreshSoon() {
        scope.launch { refreshState() }
    }

    private suspend fun refreshState() = withContext(Dispatchers.IO) {
        val downloadMap = buildMap {
            runCatching {
                media3DownloadManager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) {
                        val download = cursor.download
                        put(download.request.id, download)
                    }
                }
            }
        }
        val snapshot = synchronized(this@StreamDownloadManager) { catalog.toList() }
        val items = snapshot.map { entry ->
            val download = downloadMap[entry.id]
            DownloadItem(
                id = entry.id,
                title = entry.title,
                subtitle = entry.subtitle,
                imageUrl = entry.imageUrl,
                mediaType = entry.mediaType,
                status = resolveStatus(entry, download),
                downloadedBytes = download?.bytesDownloaded ?: 0L,
                sizeBytes = download?.contentLength?.coerceAtLeast(0L) ?: 0L,
                percentDownloaded = download?.percentDownloaded
                    ?.takeIf { it >= 0f }
                    ?.coerceIn(0f, 100f)
                    ?: 0f,
                errorMessage = entry.errorMessage ?: downloadFailureMessage(download),
                pageUrl = entry.pageUrl,
                resolvedUrl = entry.resolvedUrl,
                mimeType = entry.mimeType,
                requestHeaders = entry.requestHeaders,
                createdAtMs = entry.createdAtMs,
            )
        }.sortedByDescending { it.createdAtMs }

        _downloads.value = items
        _downloadsPaused.value = media3DownloadManager.downloadsPaused
        val stat = StatFs(appContext.filesDir.absolutePath)
        _storage.value = DownloadStorage(
            usedBytes = cache.cacheSpace,
            availableBytes = stat.availableBytes,
            totalBytes = stat.totalBytes,
        )
    }

    private fun resolveStatus(
        entry: DownloadCatalogEntry,
        download: Download?,
    ): DownloadStatus {
        if (entry.resolving) return DownloadStatus.RESOLVING
        if (download == null) {
            return if (entry.errorMessage != null) DownloadStatus.FAILED else DownloadStatus.QUEUED
        }
        return when (download.state) {
            Download.STATE_QUEUED -> DownloadStatus.QUEUED
            Download.STATE_STOPPED -> DownloadStatus.PAUSED
            Download.STATE_DOWNLOADING, Download.STATE_RESTARTING -> DownloadStatus.DOWNLOADING
            Download.STATE_COMPLETED -> DownloadStatus.COMPLETED
            Download.STATE_FAILED -> DownloadStatus.FAILED
            Download.STATE_REMOVING -> DownloadStatus.REMOVING
            else -> DownloadStatus.QUEUED
        }
    }

    private fun downloadFailureMessage(download: Download?): String? =
        if (download?.state == Download.STATE_FAILED) {
            "The provider stopped or rejected this download. Retry to resolve it again."
        } else {
            null
        }

    private fun headersFor(uri: Uri): Map<String, String> {
        val entry = synchronized(this) {
            catalog.firstOrNull { catalogEntry ->
                val resolvedHost = catalogEntry.resolvedUrl
                    ?.let(Uri::parse)
                    ?.host
                resolvedHost != null && resolvedHost == uri.host
            }
        } ?: return emptyMap()

        return buildMap {
            putAll(entry.requestHeaders)
            putIfAbsent("Referer", entry.pageUrl)
            putIfAbsent("User-Agent", STREAM_USER_AGENT)
        }
    }

    private fun updateCatalog(
        id: String,
        transform: (DownloadCatalogEntry) -> DownloadCatalogEntry,
    ) {
        synchronized(this) {
            val index = catalog.indexOfFirst { it.id == id }
            if (index < 0) return
            catalog[index] = transform(catalog[index])
            persistCatalog()
        }
    }

    private fun readCatalog(): List<DownloadCatalogEntry> {
        val json = preferences.getString(KEY_CATALOG, null) ?: return emptyList()
        val type = object : TypeToken<List<DownloadCatalogEntry>>() {}.type
        return runCatching {
            gson.fromJson<List<DownloadCatalogEntry>>(json, type)
        }.getOrDefault(emptyList())
    }

    private fun persistCatalog() {
        preferences.edit().putString(KEY_CATALOG, gson.toJson(catalog)).apply()
    }

    private fun readSettings(): DownloadSettings = DownloadSettings(
        wifiOnly = preferences.getBoolean(KEY_WIFI_ONLY, true),
        maxParallelDownloads = preferences.getInt(KEY_MAX_PARALLEL, 2),
        quality = runCatching {
            DownloadQuality.valueOf(
                preferences.getString(KEY_QUALITY, DownloadQuality.STANDARD.name)
                    ?: DownloadQuality.STANDARD.name
            )
        }.getOrDefault(DownloadQuality.STANDARD),
    )

    private fun selectHlsVariant(
        masterUrl: String,
        headers: Map<String, String>,
        quality: DownloadQuality,
        depth: Int = 0,
    ): String {
        if (depth >= 2) return masterUrl
        return runCatching {
            val request = Request.Builder()
                .url(masterUrl)
                .apply {
                    headers.forEach { (name, value) -> header(name, value) }
                    header("User-Agent", STREAM_USER_AGENT)
                }
                .build()
            val body = resolverHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching masterUrl
                response.body?.string().orEmpty()
            }
            if (!body.contains("#EXT-X-STREAM-INF")) return@runCatching masterUrl
            // Selecting only a video variant loses separately declared audio/subtitle renditions.
            if (body.lineSequence().any { it.trim().startsWith("#EXT-X-MEDIA:") }) {
                return@runCatching masterUrl
            }

            val lines = body.lineSequence().map(String::trim).toList()
            val variants = buildList {
                lines.forEachIndexed { index, line ->
                    if (!line.startsWith("#EXT-X-STREAM-INF")) return@forEachIndexed
                    val bandwidth = Regex("(?:AVERAGE-)?BANDWIDTH=(\\d+)")
                        .find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toLongOrNull()
                        ?: 0L
                    val path = lines.drop(index + 1).firstOrNull {
                        it.isNotBlank() && !it.startsWith("#")
                    } ?: return@forEachIndexed
                    add(bandwidth to java.net.URI(masterUrl).resolve(path).toString())
                }
            }.sortedBy { it.first }
            val selected = when (quality) {
                DownloadQuality.DATA_SAVER -> variants.firstOrNull()
                DownloadQuality.STANDARD -> variants.getOrNull(variants.size / 2)
                DownloadQuality.BEST -> variants.lastOrNull()
            }?.second ?: masterUrl

            if (selected == masterUrl) {
                selected
            } else {
                selectHlsVariant(selected, headers, quality, depth + 1)
            }
        }.getOrDefault(masterUrl)
    }

    companion object {
        private const val KEY_CATALOG = "download_catalog"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_MAX_PARALLEL = "max_parallel"
        private const val KEY_QUALITY = "download_quality"
        private const val USER_PAUSED_STOP_REASON = 1
        const val STREAM_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

        @Volatile
        private var instance: StreamDownloadManager? = null

        fun get(context: Context): StreamDownloadManager =
            instance ?: synchronized(this) {
                instance ?: StreamDownloadManager(context).also { instance = it }
            }

        fun inferMimeType(url: String): String? {
            val lower = url.lowercase()
            return when {
                ".m3u8" in lower || "mpegurl" in lower || "playlist" in lower ->
                    MimeTypes.APPLICATION_M3U8
                ".mpd" in lower -> MimeTypes.APPLICATION_MPD
                ".mp4" in lower -> MimeTypes.VIDEO_MP4
                else -> null
            }
        }

        fun isDownloadableMediaUrl(url: String): Boolean {
            if (
                url.isBlank() ||
                url.startsWith("blob:") ||
                url.contains(".vtt", ignoreCase = true) ||
                url.contains(".srt", ignoreCase = true)
            ) {
                return false
            }
            val adMarkers = listOf(
                "doubleclick",
                "googlesyndication",
                "popads",
                "popunder",
                "/ads/",
                "preroll",
                "vast.",
                "adservice",
            )
            return adMarkers.none { url.contains(it, ignoreCase = true) } &&
                inferMimeType(url) != null
        }

        private fun sanitizeRequestHeaders(headers: Map<String, String>): Map<String, String> {
            val allowed = setOf(
                "authorization",
                "cookie",
                "origin",
                "referer",
                "user-agent",
            )
            return headers.filterKeys { it.lowercase() in allowed }
        }
    }
}
