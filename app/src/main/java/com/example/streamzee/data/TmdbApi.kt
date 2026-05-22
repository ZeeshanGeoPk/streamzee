package com.example.streamzee.data

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface TmdbApi {
    @GET("trending/movie/week")
    suspend fun getTrendingMovies(
        @Header("Authorization") authorization: String,
    ): TmdbMovieResponse

    @GET("search/movie")
    suspend fun searchMovies(
        @Header("Authorization") authorization: String,
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1,
    ): TmdbMovieResponse
}
