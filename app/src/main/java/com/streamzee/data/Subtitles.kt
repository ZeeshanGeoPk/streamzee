package com.streamzee.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class SubtitleItem(
    val fileId: String,
    val fileName: String,
    val language: String,
    val release: String?,
    val uploader: String?,
    val directUrl: String?,
    val viaSubdl: Boolean = false,
    val viaWyzie: Boolean = false,
)

data class SubtitleSearchResult(
    val ok: Boolean,
    val results: List<SubtitleItem> = emptyList(),
    val error: String? = null,
)

suspend fun downloadAndExtractFirstSubtitle(http: OkHttpClient, url: String): File? =
    withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", "Streamzee").build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@withContext null
            val body = res.body ?: return@withContext null
            val tmpFile = File.createTempFile("streamzee_sub", ".zip")
            body.byteStream().use { input ->
                tmpFile.sink().buffer().use { out ->
                    out.writeAll(input.source())
                }
            }
            // Try to extract first subtitle file from the zip
            ZipInputStream(tmpFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name ?: ""
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (!entry.isDirectory && ext in setOf("srt", "vtt", "ass", "ssa")) {
                        val outFile = File.createTempFile("streamzee_sub_extracted", ".${ext}")
                        outFile.outputStream().use { fos ->
                            zis.copyTo(fos)
                        }
                        tmpFile.delete()
                        return@withContext outFile
                    }
                    entry = zis.nextEntry
                }
            }
            tmpFile.delete()
            null
        }
    }
