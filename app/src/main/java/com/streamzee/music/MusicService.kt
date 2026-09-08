package com.streamzee.music

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.streamzee.MainActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object MusicStatus {
    internal val mutable = MutableStateFlow<String?>(null)
    val error = mutable.asStateFlow()
    internal val cacheSize = MutableStateFlow(0L)
    val cachedBytes = cacheSize.asStateFlow()
    internal val timer = MutableStateFlow<Long?>(null)
    val sleepUntil = timer.asStateFlow()
}

@OptIn(UnstableApi::class)
fun MusicTrack.mediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id).setUri("https://music.streamzee.invalid/$id").setCustomCacheKey(id)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist)
        .setArtworkUri(artwork?.let(Uri::parse)).build()).build()

@OptIn(UnstableApi::class)
class MusicService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var cache: SimpleCache
    private lateinit var database: StandaloneDatabaseProvider
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepJob: Job? = null
    private var restoring = true
    private var retriedId: String? = null

    override fun onCreate() {
        super.onCreate()
        val library = MusicLibrary.get(this)
        database = StandaloneDatabaseProvider(this)
        cache = SimpleCache(File(filesDir, "music_stream_cache"), LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024), database)
        val http = DefaultHttpDataSource.Factory().setUserAgent(YouTubeMusicSource.USER_AGENT)
            .setConnectTimeoutMs(20_000).setReadTimeoutMs(30_000)
        val urls = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()
        val upstream = ResolvingDataSource.Factory(DefaultDataSource.Factory(this, http)) { spec ->
            val id = requireNotNull(spec.key) { "Missing song ID" }
            val local = library.localUri(id)
            if (local != null) spec.withUri(local) else {
                val existing = urls[id]?.takeIf { System.currentTimeMillis() - it.second < 5 * 60_000 }
                val url = existing?.first ?: YouTubeMusicSource.resolve(id).also {
                    urls[id] = it to System.currentTimeMillis()
                }
                spec.withUri(Uri.parse(url))
            }
        }
        val source = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        player = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(source)).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                retriedId = null
                MusicStatus.mutable.value = null
                persistQueue()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                persistQueue()
                if (isPlaying) player.currentMediaItem?.let { item ->
                    library.recordPlayed(MusicTrack(item.mediaId, item.mediaMetadata.title.toString(),
                        item.mediaMetadata.artist.toString(), item.mediaMetadata.artworkUri?.toString(),
                        player.duration.coerceAtLeast(0) / 1000))
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                val id = player.currentMediaItem?.mediaId
                if (id != null && retriedId != id && error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                    retriedId = id
                    urls.remove(id)
                    player.prepare()
                    player.play()
                } else {
                    MusicStatus.mutable.value = "Unable to play this song. Check your connection or try another song. YouTube may require an extractor update."
                }
            }
        })
        val callback = object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                val result = super.onConnect(session, controller)
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(result.availableSessionCommands.buildUpon()
                        .add(SessionCommand("sleep", Bundle.EMPTY))
                        .add(SessionCommand("clear_cache", Bundle.EMPTY)).build()).build()
            }
            override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                if (customCommand.customAction == "clear_cache") {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { cache.keys.toList().forEach { cache.removeResource(it) } }
                            MusicStatus.cacheSize.value = cache.cacheSpace
                            MusicLibrary.get(this@MusicService).message("Playback cache cleared. Downloads are preserved.")
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            MusicLibrary.get(this@MusicService).message("Could not clear playback cache.")
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (customCommand.customAction != "sleep") return super.onCustomCommand(session, controller, customCommand, args)
                sleepJob?.cancel()
                val minutes = args.getInt("minutes").coerceIn(0, 120)
                MusicStatus.timer.value = if (minutes == 0) null else System.currentTimeMillis() + minutes * 60_000L
                if (minutes > 0) sleepJob = scope.launch {
                    delay(minutes * 60_000L)
                    player.pause()
                    MusicStatus.timer.value = null
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }
        val intent = PendingIntent.getActivity(this, 901, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setCallback(callback).setSessionActivity(intent).build()
        restoreQueue()
        restoring = false
        scope.launch { while (isActive) { delay(5000); persistQueue(); MusicStatus.cacheSize.value = cache.cacheSpace } }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    private fun persistQueue() {
        if (restoring) return
        val tracks = (0 until player.mediaItemCount).map { index ->
            val item = player.getMediaItemAt(index)
            MusicTrack(item.mediaId, item.mediaMetadata.title.toString(), item.mediaMetadata.artist.toString(), item.mediaMetadata.artworkUri?.toString(), 0)
        }
        getSharedPreferences("music_queue", MODE_PRIVATE).edit()
            .putString("tracks", Gson().toJson(tracks)).putInt("index", player.currentMediaItemIndex.coerceAtLeast(0))
            .putLong("position", player.currentPosition.coerceAtLeast(0)).putInt("repeat", player.repeatMode)
            .putBoolean("shuffle", player.shuffleModeEnabled).apply()
    }
    private fun restoreQueue() {
        val prefs = getSharedPreferences("music_queue", MODE_PRIVATE)
        val tracks = runCatching { Gson().fromJson<List<MusicTrack>>(prefs.getString("tracks", "[]"), object : TypeToken<List<MusicTrack>>() {}.type) }.getOrNull().orEmpty()
        if (tracks.isNotEmpty()) {
            player.setMediaItems(tracks.map { it.mediaItem() }, prefs.getInt("index", 0).coerceIn(tracks.indices), prefs.getLong("position", 0).coerceAtLeast(0))
        }
        player.repeatMode = prefs.getInt("repeat", Player.REPEAT_MODE_OFF).coerceIn(0, 2)
        player.shuffleModeEnabled = prefs.getBoolean("shuffle", false)
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady) stopSelf()
    }
    override fun onDestroy() {
        persistQueue()
        scope.cancel()
        MusicStatus.timer.value = null
        session?.release()
        player.release()
        cache.release()
        database.close()
        super.onDestroy()
    }
}
