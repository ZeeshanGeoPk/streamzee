package com.example.streamzee.data

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
}
