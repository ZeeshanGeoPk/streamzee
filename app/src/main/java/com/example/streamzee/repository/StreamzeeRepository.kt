package com.example.streamzee.repository

import android.content.Context
import com.example.streamzee.data.AppDataStore
import com.example.streamzee.data.TmdbApi
import com.example.streamzee.data.TmdbMovie
import com.example.streamzee.data.TmdbSeasonResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import com.example.streamzee.data.SubtitleItem
import com.example.streamzee.data.SubtitleSearchResult
import com.example.streamzee.data.downloadAndExtractFirstSubtitle
import com.example.streamzee.data.AllAnimeApi

class StreamzeeRepository(
    private val api: TmdbApi,
    private val context: Context,
    private val allAnimeApi: AllAnimeApi,
) {
    fun apiKeyFlow() = AppDataStore.apiKeyFlow(context)

    fun savedIdsFlow(): Flow<Set<String>> = AppDataStore.savedIdsFlow(context)

    suspend fun saveApiKey(apiKey: String) {
        AppDataStore.saveApiKey(context, apiKey)
    }

    suspend fun toggleSaved(movieId: String) {
        val current = AppDataStore.savedIdsFlow(context).first().toMutableSet()
        if (!current.add(movieId)) {
            current.remove(movieId)
        }
        AppDataStore.setSavedIds(context, current)
    }

    suspend fun fetchTrending(apiKey: String): List<TmdbMovie> {
        return api.getTrendingAll("Bearer $apiKey").results
    }

    suspend fun searchMovies(apiKey: String, query: String): List<TmdbMovie> {
        return api.searchMovies("Bearer $apiKey", query.trim()).results
    }

    suspend fun searchTv(apiKey: String, query: String): List<TmdbMovie> {
        return api.searchTv("Bearer $apiKey", query.trim()).results
    }

    suspend fun getMovieDetails(apiKey: String, movieId: String): TmdbMovie {
        val id = movieId.toLongOrNull() ?: throw IllegalArgumentException("Invalid movie ID: $movieId")
        return api.getMovieDetails("Bearer $apiKey", id)
    }
    
    suspend fun getTvShowDetails(apiKey: String, tvId: String): TmdbMovie {
    val id = tvId.toLongOrNull() ?: throw IllegalArgumentException("Invalid TV ID: $tvId")
    return api.getTvShowDetails("Bearer $apiKey", id)
    }

    suspend fun fetchTvSeason(apiKey: String, tvId: Long, seasonNumber: Int): TmdbSeasonResponse {
    return api.getTvSeasonDetails("Bearer $apiKey", tvId, seasonNumber)
    }
    
    suspend fun fetchSavedMovies(apiKey: String, movieIds: Set<String>): List<TmdbMovie> = coroutineScope {
        movieIds.mapNotNull { id ->
            id.toLongOrNull()?.let { parsed ->
                async {
                    try {
                        val movie = api.getMovieDetails("Bearer $apiKey", parsed)
                        // If it's actually a TV show, TMDB movie endpoint might return 
                        // a result without a title. We check if it's valid.
                        if (movie.title != null) movie else api.getTvShowDetails("Bearer $apiKey", parsed)
                    } catch (e: Exception) {
                        try {
                            api.getTvShowDetails("Bearer $apiKey", parsed)
                        } catch (e2: Exception) { null }
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private val httpClient = OkHttpClient()

    suspend fun searchSubtitles(
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        languages: String?,
        subdlApiKey: String?,
        wyzieApiKey: String?,
    ): SubtitleSearchResult = withContext(Dispatchers.IO) {
        // Try SubDL first when key provided
        if (!subdlApiKey.isNullOrBlank()) {
            try {
                val params = StringBuilder().apply {
                    append("api_key=").append(subdlApiKey)
                    append("&tmdb_id=").append(tmdbId)
                    append("&type=").append(if (mediaType == "tv") "tv" else "movie")
                    append("&subs_per_page=30")
                    if (mediaType == "tv" && season != null) append("&season_number=").append(season)
                    if (mediaType == "tv" && episode != null) append("&episode_number=").append(episode)
                    if (!languages.isNullOrBlank()) append("&languages=").append(languages.split("-")[0].uppercase())
                }
                val req = Request.Builder()
                    .url("https://api.subdl.com/api/v1/subtitles?${params}")
                    .header("User-Agent", "Streamzee")
                    .build()
                httpClient.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@withContext SubtitleSearchResult(false, error = "SubDL error ${res.code}")
                    val body = res.body?.string().orEmpty()
                    val gson = com.google.gson.Gson()
                    val json = try {
                        gson.fromJson(body, com.google.gson.JsonObject::class.java)
                    } catch (_: Exception) {
                        null
                    }
                    val status = json?.get("status")
                    if (status == null || status.isJsonNull) return@withContext SubtitleSearchResult(false, error = "SubDL returned no results")
                    val subs = json.getAsJsonArray("subtitles")
                    val results = mutableListOf<SubtitleItem>()
                    for (el in subs) {
                        try {
                            val o = el.asJsonObject
                            val sdId = if (o.has("sd_id")) o.get("sd_id").asString else continue
                            val url = if (o.has("url")) o.get("url").asString else continue
                            val name = when {
                                o.has("name") -> o.get("name").asString
                                o.has("release_name") -> o.get("release_name").asString
                                else -> ""
                            }
                            val lang = if (o.has("lang")) o.get("lang").asString.lowercase() else ""
                            results += SubtitleItem(
                                fileId = "subdl_${sdId}_" + java.net.URLEncoder.encode(url, "UTF-8"),
                                fileName = name,
                                language = lang,
                                release = if (o.has("release_name")) o.get("release_name").asString else null,
                                uploader = if (o.has("author")) o.get("author").asString else null,
                                directUrl = null,
                                viaSubdl = true,
                                viaWyzie = false,
                            )
                        } catch (_: Exception) { /* skip entry */ }
                    }
                    if (results.isEmpty()) return@withContext SubtitleSearchResult(false, error = "SubDL: no results")
                    return@withContext SubtitleSearchResult(true, results = results)
                }
            } catch (e: Exception) {
                // fallthrough to Wyzie
            }
        }

        // Wyzie fallback
        try {
            val baseUrl = if (!wyzieApiKey.isNullOrBlank()) "https://sub.wyzie.io/search" else "https://subs.wyzie.ru/search"
            val params = StringBuilder().apply {
                append("id=").append(tmdbId)
                append("&format=srt")
                if (!languages.isNullOrBlank()) append("&language=").append(languages)
                if (mediaType == "tv" && season != null) append("&season=").append(season)
                if (mediaType == "tv" && episode != null) append("&episode=").append(episode)
                if (!wyzieApiKey.isNullOrBlank()) append("&key=").append(wyzieApiKey)
            }
            val req = Request.Builder().url("${baseUrl}?${params}").header("User-Agent", "Streamzee").build()
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext SubtitleSearchResult(false, error = "Wyzie error ${res.code}")
                val body = res.body?.string().orEmpty()
                val gson = com.google.gson.Gson()
                val arr = try {
                    gson.fromJson(body, com.google.gson.JsonArray::class.java)
                } catch (_: Exception) {
                    com.google.gson.JsonArray()
                }
                val results = mutableListOf<SubtitleItem>()
                for ((i, el) in arr.withIndex()) {
                    try {
                        val o = el.asJsonObject
                        val rawUrl = if (o.has("url")) o.get("url").asString else ""
                        val fullUrl = if (rawUrl.startsWith("http")) rawUrl else "https://subs.wyzie.ru${if (rawUrl.startsWith("/")) "" else "/"}$rawUrl"
                        val displayName = listOf("display_name", "name", "release_name", "title", "SubFileName", "fileName").firstOrNull { o.has(it) }?.let { o.get(it).asString } ?: ""
                        val lang = if (o.has("language")) o.get("language").asString else ""
                        results += SubtitleItem(
                            fileId = "wyzie_${i}_" + java.net.URLEncoder.encode(fullUrl, "UTF-8"),
                            fileName = displayName.ifBlank { "$lang subtitle #${i + 1}" },
                            language = lang,
                            release = displayName,
                            uploader = "Wyzie",
                            directUrl = fullUrl,
                            viaSubdl = false,
                            viaWyzie = true,
                        )
                    } catch (_: Exception) { /* skip */ }
                }
                if (results.isEmpty()) return@withContext SubtitleSearchResult(false, error = "Wyzie: no results")
                return@withContext SubtitleSearchResult(true, results = results)
            }
        } catch (e: Exception) {
            return@withContext SubtitleSearchResult(false, error = e.message ?: "unknown error")
        }
    }

    suspend fun getSubtitleUrl(fileId: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            if (fileId.startsWith("subdl_")) {
                val parts = fileId.split("_")
                val subdlPath = java.net.URLDecoder.decode(parts.drop(2).joinToString("_"), "UTF-8")
                val downloadUrl = "https://dl.subdl.com${subdlPath}"
                val extracted = downloadAndExtractFirstSubtitle(httpClient, downloadUrl)
                return@withContext Pair(extracted != null, extracted?.absolutePath)
            }
            if (fileId.startsWith("wyzie_")) {
                val url = java.net.URLDecoder.decode(fileId.split("_").drop(2).joinToString("_"), "UTF-8")
                return@withContext Pair(true, url)
            }
            return@withContext Pair(false, null)
        } catch (e: Exception) {
            return@withContext Pair(false, null)
        }
    }

    fun watchProgressFlow(movieId: String): Flow<Triple<Long, Int, Int>> =
        AppDataStore.watchHistoryFlow(context, movieId)

    suspend fun saveWatchProgress(movieId: String, positionMs: Long, season: Int? = null, episode: Int? = null) {
        AppDataStore.saveWatchProgress(context, movieId, positionMs, season, episode)
    }

    suspend fun searchAnime(query: String): List<com.example.streamzee.data.AllAnimeShow> = withContext(Dispatchers.IO) {
        allAnimeApi.searchAnime(query)
    }

    suspend fun resolveAnimeEpisode(
        showId: String,
        episodeString: String,
        translationType: String = "sub"
    ): List<com.example.streamzee.data.AllAnimeSourceUrl> = withContext(Dispatchers.IO) {
        allAnimeApi.resolveEpisode(showId, episodeString, translationType)
    }
}
