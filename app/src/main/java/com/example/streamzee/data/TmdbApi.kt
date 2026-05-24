package com.example.streamzee.data

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
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

    @GET("search/tv")
    suspend fun searchTv(
        @Header("Authorization") authorization: String,
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1,
    ): TmdbMovieResponse
    
    @GET("trending/all/week") // Fetches both Movies and TV shows for the Home screen
    suspend fun getTrendingAll(
    @Header("Authorization") authorization: String,
    ): TmdbMovieResponse

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Header("Authorization") authorization: String,
        @Path("movie_id") movieId: Long,
        @Query("language") language: String = "en-US",
    ): TmdbMovie
    
    @GET("tv/{tv_id}") // Specifically for TV Show details
    suspend fun getTvShowDetails(
    @Header("Authorization") authorization: String,
    @Path("tv_id") tvId: Long,
    @Query("language") language: String = "en-US",
    ): TmdbMovie
}
