package com.streamzee.data

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDataStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun concurrentWatchlistUpdatesAreNotLost() = runBlocking {
        AppDataStore.setSavedIds(context, emptySet())
        val ids = (1..50).map { "movie_$it" }.toSet()
        ids.map { async { AppDataStore.toggleSaved(context, it) } }.awaitAll()
        assertEquals(ids, AppDataStore.savedIdsFlow(context).first())
        ids.map { async { AppDataStore.toggleSaved(context, it) } }.awaitAll()
        assertTrue(AppDataStore.savedIdsFlow(context).first().isEmpty())
    }

    @Test
    fun movieAndTvWithSameIdKeepIndependentProgress() = runBlocking {
        AppDataStore.clearWatchHistory(context)
        AppDataStore.saveWatchProgress(context, "42", 12000L)
        AppDataStore.saveWatchProgress(context, "42", 34000L, 2, 3)
        assertEquals(Triple(12000L, 1, 1), AppDataStore.watchHistoryFlow(context, "movie_42").first())
        assertEquals(Triple(34000L, 2, 3), AppDataStore.watchHistoryFlow(context, "tv_42").first())
    }

    @Test
    fun clearingHistoryPreservesWatchlistAndPreferences() = runBlocking {
        AppDataStore.setSavedIds(context, setOf("anime_1"))
        AppDataStore.saveThemeMode(context, "Dark")
        AppDataStore.saveWatchProgress(context, "42", 12000L)
        AppDataStore.saveAnimeWatchProgress(context, "1", 4, 5000L)
        AppDataStore.clearWatchHistory(context)
        assertTrue(AppDataStore.watchHistoryIdsFlow(context).first().isEmpty())
        assertEquals(Triple(0L, 1, 1), AppDataStore.watchHistoryFlow(context, "movie_42").first())
        assertEquals(Triple(0L, 1, 1), AppDataStore.watchHistoryFlow(context, "anime_1").first())
        assertEquals(setOf("anime_1"), AppDataStore.savedIdsFlow(context).first())
        assertEquals("Dark", AppDataStore.appPreferencesFlow(context).first().themeMode)
    }
}
