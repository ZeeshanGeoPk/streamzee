package com.streamzee.data

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("trending/movie/week")
    suspend fun getTrendingMovies(
        @Header("Authorization") authorization: String,
    ): TmdbMovieResponse

    @GET("trending/tv/week")
    suspend fun getTrendingTv(
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

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeasonDetails(
    @Header("Authorization") authorization: String,
    @Path("tv_id") tvId: Long,
    @Path("season_number") seasonNumber: Int
    ): TmdbSeasonResponse
    
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
    
    // 1. Search via Jikan
    @GET("https://api.jikan.moe/v4/anime")
    suspend fun searchJikan(@Query("q") query: String): JikanSearchResponse
    
    @GET("https://api.jikan.moe/v4/anime/{anime_id}")
    suspend fun getAnimeById(
        @Path("anime_id") animeId: Int
    ): JikanAnimeResponse

    // For Home Screen Recommendations
    // Movies
    @GET("movie/now_playing")
    suspend fun getRecentMovies(@Header("Authorization") auth: String): TmdbMovieResponse

    @GET("movie/top_rated")
    suspend fun getTopMovies(@Header("Authorization") auth: String): TmdbMovieResponse

    // TV
    @GET("tv/on_the_air")
    suspend fun getRecentTv(@Header("Authorization") auth: String): TmdbMovieResponse

    @GET("tv/top_rated")
    suspend fun getTopTv(@Header("Authorization") auth: String): TmdbMovieResponse

    // Anime (Jikan)
    @GET("https://api.jikan.moe/v4/top/anime")
    suspend fun getTopAnime(
        @Query("filter") filter: String? = null,
    ): JikanSearchResponse

    @GET("https://api.jikan.moe/v4/seasons/now")
    suspend fun getRecentAnime(): JikanSearchResponse
}
