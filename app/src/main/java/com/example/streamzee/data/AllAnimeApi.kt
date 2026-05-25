package com.example.streamzee.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.net.URL

class AllAnimeApi(private val httpClient: OkHttpClient) {
    private val baseUrl = "https://api.allanime.day/api"

    private val allAnimeHexMap = mapOf(
        "79" to "A", "7a" to "B", "7b" to "C", "7c" to "D", "7d" to "E", "7e" to "F", "7f" to "G",
        "70" to "H", "71" to "I", "72" to "J", "73" to "K", "74" to "L", "75" to "M", "76" to "N",
        "77" to "O", "68" to "P", "69" to "Q", "6a" to "R", "6b" to "S", "6c" to "T", "6d" to "U",
        "6e" to "V", "6f" to "W", "60" to "X", "61" to "Y", "62" to "Z", "59" to "a", "5a" to "b",
        "5b" to "c", "5c" to "d", "5d" to "e", "5e" to "f", "5f" to "g", "50" to "h", "51" to "i",
        "52" to "j", "53" to "k", "54" to "l", "55" to "m", "56" to "n", "57" to "o", "48" to "p",
        "49" to "q", "4a" to "r", "4b" to "s", "4c" to "t", "4d" to "u", "4e" to "v", "4f" to "w",
        "40" to "x", "41" to "y", "42" to "z", "08" to "0", "09" to "1", "0a" to "2", "0b" to "3",
        "0c" to "4", "0d" to "5", "0e" to "6", "0f" to "7", "00" to "8", "01" to "9", "15" to "-",
        "16" to ".", "67" to "_", "46" to "~", "02" to ":", "17" to "/", "07" to "?", "1b" to "#",
        "63" to "[", "65" to "]", "78" to "@", "19" to "!", "1c" to "$", "1e" to "&", "10" to "(",
        "11" to ")", "12" to "*", "13" to "+", "14" to ",", "03" to ";", "05" to "=", "1d" to "%"
    )

    private fun decodeAllanimeUrl(encoded: String): String {
        var str = encoded
        if (str.startsWith("--")) str = str.substring(2)
        val sb = java.lang.StringBuilder()
        var i = 0
        while (i < str.length) {
            if (i + 2 <= str.length) {
                val pair = str.substring(i, i + 2)
                val mapped = allAnimeHexMap[pair]
                if (mapped != null) {
                    sb.append(mapped)
                } else {
                    sb.append(pair)
                }
            } else {
                sb.append(str.substring(i))
            }
            i += 2
        }
        return sb.toString().replace("\\u002F", "/").replace("\\|", "")
    }

    private fun decryptTobeparsed(blob: String): String {
        return try {
            val keyBytes = MessageDigest.getInstance("SHA-256")
                .digest("Xot36i3lK3:v1".toByteArray(Charsets.UTF_8))
            val buf = Base64.decode(blob, Base64.DEFAULT)
            val iv12 = buf.copyOfRange(1, 13)
            val iv16 = ByteArray(16)
            System.arraycopy(iv12, 0, iv16, 0, 12)
            iv16[12] = 0
            iv16[13] = 0
            iv16[14] = 0
            iv16[15] = 2

            val ct = buf.copyOfRange(13, buf.size - 16)
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            val secretKeySpec = SecretKeySpec(keyBytes, "AES")
            val ivParameterSpec = IvParameterSpec(iv16)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
            val decryptedBytes = cipher.doFinal(ct)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun searchAnime(query: String, limit: Int = 40): List<AllAnimeShow> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
            val variables = mapOf(
                "search" to mapOf(
                    "allowAdult" to true,
                    "allowUnknown" to false,
                    "query" to query.lowercase()
                ),
                "limit" to limit,
                "page" to 1,
                "translationType" to "sub",
                "countryOrigin" to "ALL"
            )
            val graphQLRequest = mapOf(
                "query" to SEARCH_GQL,
                "variables" to variables
            )
            val body = com.google.gson.Gson().toJson(graphQLRequest)
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(baseUrl)
                .post(body)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://allmanga.to")
                .header("Origin", "https://allmanga.to")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val responseBody = response.body?.string() ?: return@withContext emptyList()
            val gson = com.google.gson.Gson()
            val parsed = gson.fromJson(responseBody, com.google.gson.JsonObject::class.java)
            val edges = parsed?.getAsJsonObject("data")
                ?.getAsJsonObject("shows")
                ?.getAsJsonArray("edges")

                // 2. Update the mapping logic inside searchAnime function:
                edges?.mapNotNull { el ->
                    try {
                        val obj = el.asJsonObject
                        AllAnimeShow(
                            aid = obj.get("_id").asString,
                            name = obj.get("name").asString,
                            thumbnail = obj.get("thumbnail")?.asString, // Map this
                            episodeCount = if (obj.has("availableEpisodes")) {
                                val avail = obj.getAsJsonObject("availableEpisodes")
                                // Get count based on sub/dub - usually 'sub' is safest default
                                avail.get("sub")?.asInt ?: avail.get("dub")?.asInt ?: 0
                            } else 0
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun resolveEpisode(
        showId: String,
        episodeString: String,
        translationType: String = "sub"
    ): List<AllAnimeSourceUrl> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val variables = mapOf(
                "showId" to showId,
                "translationType" to translationType,
                "episodeString" to episodeString
            )
            val graphQLRequest = mapOf(
                "query" to EPISODE_GQL,
                "variables" to variables
            )
            val body = com.google.gson.Gson().toJson(graphQLRequest)
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(baseUrl)
                .post(body)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://allmanga.to")
                .header("Origin", "https://allmanga.to")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val responseBody = response.body?.string() ?: return@withContext emptyList()

            // 1. Extract raw sourceUrls or tobeparsed encrypted block
            val allSources = mutableListOf<AllAnimeSourceUrl>()

            if (responseBody.contains("tobeparsed")) {
                val tbRegex = "\"tobeparsed\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val match = tbRegex.find(responseBody)
                if (match != null) {
                    val encryptedBlob = match.groupValues[1]
                    val decryptedPlain = decryptTobeparsed(encryptedBlob)
                    if (decryptedPlain.isNotBlank()) {
                        val chunks = decryptedPlain.split("{", "}")
                        for (chunk in chunks) {
                            if (chunk.contains("\"sourceUrl\"")) {
                                val urlRegex = "\"sourceUrl\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                                val nameRegex = "\"sourceName\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                                val prioRegex = "\"priority\"\\s*:\\s*([0-9.]+)".toRegex()

                                val url = urlRegex.find(chunk)?.groupValues?.get(1) ?: ""
                                val name = nameRegex.find(chunk)?.groupValues?.get(1) ?: ""
                                val prioStr = prioRegex.find(chunk)?.groupValues?.get(1) ?: "0"
                                val prio = prioStr.toFloatOrNull() ?: 0f

                                if (url.startsWith("--")) {
                                    allSources.add(AllAnimeSourceUrl(sourceUrl = url, sourceName = name, priority = prio))
                                }
                            }
                        }
                    }
                }
            }

            // Fallback/additive: try to parse standard unencrypted sourceUrls from JSON
            try {
                val gson = com.google.gson.Gson()
                val parsed = gson.fromJson(responseBody, com.google.gson.JsonObject::class.java)
                val sourceUrls = parsed?.getAsJsonObject("data")
                    ?.getAsJsonObject("episode")
                    ?.getAsJsonArray("sourceUrls")

                sourceUrls?.forEach { el ->
                    try {
                        val obj = el.asJsonObject
                        val url = obj.get("sourceUrl").asString
                        val name = obj.get("sourceName").asString
                        val prio = obj.get("priority").asFloat
                        if (url.startsWith("--") && allSources.none { it.sourceUrl == url }) {
                            allSources.add(AllAnimeSourceUrl(sourceUrl = url, sourceName = name, priority = prio))
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            // 2. Decode and try to resolve direct video stream for each source
            val providerPriority = listOf("S-mp4", "Luf-Mp4", "Yt-mp4", "Default", "Sl-Hls")
            val sortedSources = allSources
                .filter { it.sourceUrl?.startsWith("--") == true }
                .sortedWith(Comparator { a, b ->
                    val idxA = providerPriority.indexOf(a.sourceName).let { if (it == -1) 99 else it }
                    val idxB = providerPriority.indexOf(b.sourceName).let { if (it == -1) 99 else it }
                    idxA.compareTo(idxB)
                })

            val resolvedList = mutableListOf<AllAnimeSourceUrl>()

            for (src in sortedSources) {
                val path = decodeAllanimeUrl(src.sourceUrl.orEmpty()).replace("/clock", "/clock.json")
                var fetchUrl = path
                if (fetchUrl.startsWith("//")) {
                    fetchUrl = "https:$fetchUrl"
                } else if (fetchUrl.startsWith("/")) {
                    fetchUrl = "https://allanime.day$fetchUrl"
                } else if (!fetchUrl.startsWith("http")) {
                    fetchUrl = "https://allanime.day/$fetchUrl"
                }

                try {
                    if (fetchUrl.contains("fast4speed.rsvp") || src.sourceName == "Yt-mp4") {
                        val req = Request.Builder()
                            .url(fetchUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .header("Referer", "https://allmanga.to")
                            .build()
                        httpClient.newCall(req).execute().use { resp ->
                            val finalUrl = resp.request.url.toString()
                            if (finalUrl.isNotBlank() && !finalUrl.contains("youtube.com/watch") && !finalUrl.contains("youtu.be/")) {
                                resolvedList.add(AllAnimeSourceUrl(
                                    sourceUrl = finalUrl,
                                    sourceName = src.sourceName,
                                    priority = src.priority
                                ))
                            }
                        }
                    } else {
                        val req = Request.Builder()
                            .url(fetchUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .header("Referer", "https://allmanga.to")
                            .build()
                        httpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val body = resp.body?.string().orEmpty()
                                val json = Gson().fromJson(body, JsonObject::class.java)
                                val links = json?.getAsJsonArray("links")
                                if (links != null && links.size() > 0) {
                                    val linkList = mutableListOf<JsonObject>()
                                    for (el in links) {
                                        if (el.isJsonObject) linkList.add(el.asJsonObject)
                                    }

                                    val mp4Links = linkList.filter {
                                        val l = it.get("link")?.asString.orEmpty()
                                        !l.contains(".m3u8") && !l.contains("master.")
                                    }
                                    val bestLinks = mp4Links.ifEmpty { linkList }

                                    val sortedLinks = bestLinks.sortedByDescending {
                                        val resStr = it.get("resolutionStr")?.asString.orEmpty()
                                        resStr.filter { c -> c.isDigit() }.toIntOrNull() ?: 0
                                    }
                                    val bestLink = sortedLinks.firstOrNull()?.get("link")?.asString
                                    if (!bestLink.isNullOrBlank()) {
                                        resolvedList.add(AllAnimeSourceUrl(
                                            sourceUrl = bestLink,
                                            sourceName = src.sourceName,
                                            priority = src.priority
                                        ))
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            return@withContext resolvedList.ifEmpty { allSources } // Fallback to raw if resolution fails entirely
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        // 1. Update the Query string to include thumbnail and availableEpisodes
        private const val SEARCH_GQL = $$"""
            query($search:SearchInput $limit:Int $page:Int $translationType:VaildTranslationTypeEnumType $countryOrigin:VaildCountryOriginEnumType){
                shows(search:$search limit:$limit page:$page translationType:$translationType countryOrigin:$countryOrigin){
                    edges{
                        _id 
                        name 
                        thumbnail 
                        availableEpisodes 
                        __typename
                    }
                }
            }
        """

        private const val EPISODE_GQL = $$"""
            query($showId:String! $translationType:VaildTranslationTypeEnumType! $episodeString:String!){
                episode(showId:$showId translationType:$translationType episodeString:$episodeString){
                    episodeString sourceUrls tobeparsed
                }
            }
        """
    }
}
