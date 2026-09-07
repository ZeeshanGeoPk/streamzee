package com.streamzee.data

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class OfflineCacheTest {
    @Test
    fun cachedBytesPlayAndMissingBytesFailWithoutNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val directory = File(context.cacheDir, "offline-test-${System.nanoTime()}")
        val database = StandaloneDatabaseProvider(context)
        val cache = SimpleCache(directory, NoOpCacheEvictor(), database)
        val url = "https://example.invalid/offline.mp4"
        try {
            val hole = requireNotNull(cache.startReadWrite(url, 0, 4))
            try {
                val file = cache.startFile(url, 0, 4)
                file.writeBytes(byteArrayOf(1, 2, 3, 4))
                cache.commitFile(file, 4)
            } finally {
                cache.releaseHoleSpan(hole)
            }
            val source = createOfflineDataSourceFactory(cache).createDataSource()
            try {
                source.open(DataSpec.Builder().setUri(url).setLength(4).build())
                val bytes = ByteArray(4)
                assertEquals(4, source.read(bytes, 0, 4))
                assertArrayEquals(byteArrayOf(1, 2, 3, 4), bytes)
            } finally { source.close() }
            val missing = createOfflineDataSourceFactory(cache).createDataSource()
            try {
                missing.open(DataSpec.Builder().setUri(url).setPosition(4).setLength(1).build())
                fail("An uncached range must fail instead of requesting the network")
            } catch (_: IOException) {
                // No upstream is configured, so the unavailable range fails immediately.
            } finally { missing.close() }
        } finally {
            cache.release()
            database.close()
            directory.deleteRecursively()
        }
    }
}
