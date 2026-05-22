package com.example.streamzee.repository

import android.content.Context
import com.example.streamzee.data.AppDataStore
import com.example.streamzee.data.TmdbApi
import com.example.streamzee.data.TmdbMovie
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class StreamzeeRepository(
    private val api: TmdbApi,
    private val context: Context,
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
        return api.getTrendingMovies("Bearer $apiKey").results
    }

    suspend fun searchMovies(apiKey: String, query: String): List<TmdbMovie> {
        return api.searchMovies("Bearer $apiKey", query.trim()).results
    }

    suspend fun getMovieDetails(apiKey: String, movieId: String): TmdbMovie {
        val id = movieId.toLongOrNull() ?: throw IllegalArgumentException("Invalid movie ID: $movieId")
        return api.getMovieDetails("Bearer $apiKey", id)
    }

    suspend fun fetchSavedMovies(apiKey: String, movieIds: Set<String>): List<TmdbMovie> = coroutineScope {
        movieIds.mapNotNull { id ->
            id.toLongOrNull()?.let { parsed ->
                async { api.getMovieDetails("Bearer $apiKey", parsed) }
            }
        }.awaitAll()
    }
}
