package com.streamzee.music

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.TimeUnit

/** All provider-specific code lives here; stream URLs are resolved afresh, never persisted. */
data class ResolvedMusicAudio(val url: String, val mimeType: String)

object YouTubeMusicSource {
    private val client = OkHttpClient.Builder().callTimeout(35, TimeUnit.SECONDS).build()
    private val initialized by lazy {
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                val builder = okhttp3.Request.Builder().url(request.url())
                    .header("User-Agent", USER_AGENT)
                request.headers().forEach { (name, values) ->
                    builder.removeHeader(name)
                    values.forEach { builder.addHeader(name, it) }
                }
                val body = request.dataToSend()?.toRequestBody(null)
                    ?: if (request.httpMethod() == "POST") ByteArray(0).toRequestBody(null) else null
                builder.method(request.httpMethod(), body)
                return client.newCall(builder.build()).execute().use {
                    if (it.code == 429) throw IOException("YouTube is rate limiting requests. Try again later.")
                    Response(it.code, it.message, it.headers.toMultimap(), it.body?.string().orEmpty(), it.request.url.toString())
                }
            }
        })
        true
    }

    fun search(query: String): List<MusicTrack> {
        check(initialized)
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(query, listOf("music_songs"), "")
        return SearchInfo.getInfo(service, handler).relatedItems.filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                val id = service.streamLHFactory.getId(item.url)
                if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) null else MusicTrack(
                    id, item.name, item.uploaderName.orEmpty(), item.thumbnails.firstOrNull()?.url,
                    item.duration.coerceAtLeast(0),
                )
            }.distinctBy { it.id }
    }

    fun resolve(id: String): String = resolveAudio(id).url

    fun resolveAudio(id: String): ResolvedMusicAudio {
        require(id.matches(Regex("[A-Za-z0-9_-]{11}"))) { "Invalid music ID" }
        check(initialized)
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
        val stream = info.audioStreams.filter { it.isUrl }.maxByOrNull { it.averageBitrate }
            ?: throw IOException("No audio stream is available for this song.")
        return ResolvedMusicAudio(stream.content, stream.format?.mimeType ?: "application/octet-stream")
    }
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
}
