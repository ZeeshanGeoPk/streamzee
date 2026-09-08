package com.streamzee.music

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class YouTubeMusicSourceTest {
    @Test fun invalidIdsNeverReachTheNetwork() {
        listOf("", "../file", "https://example.com", "bad id").forEach { id ->
            try { YouTubeMusicSource.resolve(id); fail("Invalid ID accepted: $id") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun timelineHandlesUnknownDurationAndLongTracks() {
        assertEquals("0:00", musicTime(-9223372036854775807L))
        assertEquals("1:05", musicTime(65_000))
        assertEquals("90:00", musicTime(5_400_000))
    }
    @Test fun liveMusicSearchAndAudioResolution() {
        assumeTrue("Opt-in test contacts YouTube", System.getenv("STREAMZEE_LIVE_MUSIC_TEST") == "1")
        val songs = YouTubeMusicSource.search("Beethoven")
        assertTrue("Music search returned no tracks", songs.isNotEmpty())
        assertTrue(songs.all { it.id.matches(Regex("[A-Za-z0-9_-]{11}")) && it.title.isNotBlank() })
        val audio = YouTubeMusicSource.resolve(songs.first().id)
        assertTrue("Expected a direct HTTPS audio URL", audio.startsWith("https://"))
        val request = okhttp3.Request.Builder().url(audio)
            .header("User-Agent", YouTubeMusicSource.USER_AGENT).header("Range", "bytes=0-1023").build()
        okhttp3.OkHttpClient.Builder().callTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
            .newCall(request).execute().use { response ->
                assertTrue("Audio request failed with ${response.code}", response.isSuccessful)
                assertTrue("Empty audio response", requireNotNull(response.body).byteStream().read() >= 0)
            }
    }
}
