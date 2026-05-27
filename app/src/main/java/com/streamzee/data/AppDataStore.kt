package com.streamzee.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "streamzee_prefs")

object AppDataStore {
    private val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
    private val SAVED_IDS = stringSetPreferencesKey("saved_media_ids")

    private fun watchProgressKey(movieId: String) = stringPreferencesKey("watch_progress_$movieId")
    private fun lastSeasonKey(movieId: String) = stringPreferencesKey("last_season_$movieId")
    private fun lastEpisodeKey(movieId: String) = stringPreferencesKey("last_episode_$movieId")

    // Update watchProgressFlow to return a Triple (Position, Season, Episode)
    fun watchHistoryFlow(context: Context, movieId: String): Flow<Triple<Long, Int, Int>> =
        context.dataStore.data.map { preferences ->
            val pos = preferences[watchProgressKey(movieId)]?.toLongOrNull() ?: 0L
            val season = preferences[lastSeasonKey(movieId)]?.toIntOrNull() ?: 1
            val episode = preferences[lastEpisodeKey(movieId)]?.toIntOrNull() ?: 1
            Triple(pos, season, episode)
        }

    // Update save function to include Season and Episode
    suspend fun saveWatchProgress(context: Context, movieId: String, positionMs: Long, season: Int? = null, episode: Int? = null) {
        context.dataStore.edit { preferences ->
            preferences[watchProgressKey(movieId)] = positionMs.toString()
            season?.let { preferences[lastSeasonKey(movieId)] = it.toString() }
            episode?.let { preferences[lastEpisodeKey(movieId)] = it.toString() }
        }
    }
    
    
    fun apiKeyFlow(context: Context): Flow<String?> =
        context.dataStore.data.map { preferences: Preferences -> preferences[TMDB_API_KEY] }

    suspend fun saveApiKey(context: Context, value: String) {
        context.dataStore.edit { preferences: MutablePreferences ->
            preferences[TMDB_API_KEY] = value.trim()
        }
    }

    fun savedIdsFlow(context: Context): Flow<Set<String>> =
        context.dataStore.data.map { preferences: Preferences -> preferences[SAVED_IDS] ?: emptySet() }

    suspend fun setSavedIds(context: Context, ids: Set<String>) {
        context.dataStore.edit { preferences: MutablePreferences ->
            preferences[SAVED_IDS] = ids
        }
    }

    fun watchProgressFlow(context: Context, movieId: String): Flow<Long> =
        context.dataStore.data.map { preferences: Preferences ->
            preferences[watchProgressKey(movieId)]?.toLongOrNull() ?: 0L
        }
}
