package com.streamzee.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "streamzee_prefs")

data class AppPreferences(
    val themeMode: String = "System",
    val accentColor: String = "Purple",
    val playbackQuality: String = "Auto (Best)",
    val language: String = "English",
    val subtitlesEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
)

object AppDataStore {
    private val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
    private val SAVED_IDS = stringSetPreferencesKey("saved_media_ids")
    private val WATCH_HISTORY_IDS = stringPreferencesKey("watch_history_ids")
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val ACCENT_COLOR = stringPreferencesKey("accent_color")
    private val PLAYBACK_QUALITY = stringPreferencesKey("playback_quality")
    private val LANGUAGE = stringPreferencesKey("language")
    private val SUBTITLES_ENABLED = booleanPreferencesKey("subtitles_enabled")
    private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    private val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")

    private fun watchProgressKey(movieId: String) = stringPreferencesKey("watch_progress_$movieId")
    private fun lastSeasonKey(movieId: String) = stringPreferencesKey("last_season_$movieId")
    private fun lastEpisodeKey(movieId: String) = stringPreferencesKey("last_episode_$movieId")

    // Update watchProgressFlow to return a Triple (Position, Season, Episode)
    fun watchHistoryFlow(context: Context, movieId: String): Flow<Triple<Long, Int, Int>> =
        context.dataStore.data.map { preferences ->
            // Older versions stored movie and TV progress under the bare TMDB ID.
            // Only inherit it for the media type most recently watched with that ID.
            val legacyId = movieId.substringAfter("_")
            val legacyOwner = preferences[WATCH_HISTORY_IDS]?.split(",")?.firstOrNull {
                it == "movie_$legacyId" || it == "tv_$legacyId"
            }
            val fallbackId = if (legacyOwner == movieId) legacyId else movieId
            val pos = (preferences[watchProgressKey(movieId)]
                ?: preferences[watchProgressKey(fallbackId)])?.toLongOrNull() ?: 0L
            val season = (preferences[lastSeasonKey(movieId)] ?: preferences[lastSeasonKey(fallbackId)])?.toIntOrNull() ?: 1
            val episode = (preferences[lastEpisodeKey(movieId)] ?: preferences[lastEpisodeKey(fallbackId)])?.toIntOrNull() ?: 1
            Triple(pos, season, episode)
        }

    fun watchHistoryIdsFlow(context: Context): Flow<List<String>> =
        context.dataStore.data.map { preferences ->
            preferences[WATCH_HISTORY_IDS]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        }

    // Update save function to include Season and Episode
    suspend fun saveWatchProgress(context: Context, movieId: String, positionMs: Long, season: Int? = null, episode: Int? = null) {
        context.dataStore.edit { preferences ->
            val legacyOwner = preferences[WATCH_HISTORY_IDS]?.split(",")?.firstOrNull {
                it == "movie_$movieId" || it == "tv_$movieId"
            }
            if (legacyOwner != null) {
                listOf(::watchProgressKey, ::lastSeasonKey, ::lastEpisodeKey).forEach { key ->
                    preferences[key(movieId)]?.let { value ->
                        if (preferences[key(legacyOwner)] == null) preferences[key(legacyOwner)] = value
                        preferences.remove(key(movieId))
                    }
                }
            }
            val mediaKey = if (season != null || episode != null) "tv_$movieId" else "movie_$movieId"
            preferences[watchProgressKey(mediaKey)] = positionMs.coerceAtLeast(0L).toString()
            season?.let { preferences[lastSeasonKey(mediaKey)] = it.toString() }
            episode?.let { preferences[lastEpisodeKey(mediaKey)] = it.toString() }

            val currentIds = preferences[WATCH_HISTORY_IDS]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() && it != mediaKey }
                ?: emptyList()
            preferences[WATCH_HISTORY_IDS] = (listOf(mediaKey) + currentIds).take(30).joinToString(",")
        }
    }

    suspend fun saveAnimeWatchProgress(
        context: Context,
        animeId: String,
        episode: Int,
        positionMs: Long = 0L,
    ) {
        val mediaKey = "anime_$animeId"
        context.dataStore.edit { preferences ->
            preferences[watchProgressKey(mediaKey)] = positionMs.toString()
            preferences[lastEpisodeKey(mediaKey)] = episode.toString()

            val currentIds = preferences[WATCH_HISTORY_IDS]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() && it != mediaKey }
                ?: emptyList()
            preferences[WATCH_HISTORY_IDS] = (listOf(mediaKey) + currentIds).take(30).joinToString(",")
        }
    }
    
    
    fun apiKeyFlow(context: Context): Flow<String?> =
        context.dataStore.data.map { preferences: Preferences -> preferences[TMDB_API_KEY] }

    suspend fun saveApiKey(context: Context, value: String) {
        context.dataStore.edit { preferences: MutablePreferences ->
            preferences[TMDB_API_KEY] = value.trim()
        }
    }

    fun appPreferencesFlow(context: Context): Flow<AppPreferences> =
        context.dataStore.data.map { preferences ->
            AppPreferences(
                themeMode = preferences[THEME_MODE] ?: "System",
                accentColor = preferences[ACCENT_COLOR] ?: "Purple",
                playbackQuality = preferences[PLAYBACK_QUALITY] ?: "Auto (Best)",
                language = preferences[LANGUAGE] ?: "English",
                subtitlesEnabled = preferences[SUBTITLES_ENABLED] ?: true,
                notificationsEnabled = preferences[NOTIFICATIONS_ENABLED] ?: true,
                reducedMotion = preferences[REDUCED_MOTION] ?: false,
            )
        }

    suspend fun saveThemeMode(context: Context, value: String) {
        context.dataStore.edit { it[THEME_MODE] = value }
    }

    suspend fun saveAccentColor(context: Context, value: String) {
        context.dataStore.edit { it[ACCENT_COLOR] = value }
    }

    suspend fun savePlaybackQuality(context: Context, value: String) {
        context.dataStore.edit { it[PLAYBACK_QUALITY] = value }
    }

    suspend fun saveLanguage(context: Context, value: String) {
        context.dataStore.edit { it[LANGUAGE] = value }
    }

    suspend fun saveSubtitlesEnabled(context: Context, value: Boolean) {
        context.dataStore.edit { it[SUBTITLES_ENABLED] = value }
    }

    suspend fun saveNotificationsEnabled(context: Context, value: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = value }
    }

    suspend fun saveReducedMotion(context: Context, value: Boolean) {
        context.dataStore.edit { it[REDUCED_MOTION] = value }
    }

    fun savedIdsFlow(context: Context): Flow<Set<String>> =
        context.dataStore.data.map { preferences: Preferences -> preferences[SAVED_IDS] ?: emptySet() }

    suspend fun clearWatchHistory(context: Context) {
        context.dataStore.edit { preferences ->
            preferences.asMap().keys.filter {
                it.name.startsWith("watch_progress_") ||
                    it.name.startsWith("last_season_") || it.name.startsWith("last_episode_")
            }.forEach { preferences.remove(it) }
            preferences.remove(WATCH_HISTORY_IDS)
        }
    }

    suspend fun toggleSaved(context: Context, id: String) {
        context.dataStore.edit { preferences ->
            val ids = preferences[SAVED_IDS].orEmpty().toMutableSet()
            if (!ids.add(id)) ids.remove(id)
            preferences[SAVED_IDS] = ids
        }
    }

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
