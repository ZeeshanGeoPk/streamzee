package com.example.streamzee.data

import com.google.gson.annotations.SerializedName

data class TmdbMovieResponse(
    val page: Int,
    val results: List<TmdbMovie> = emptyList(),
)

data class TmdbMovie(
    val id: Long,
    val title: String?,
    val name: String? = null,
    val overview: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?, // Added
    @SerializedName("media_type") val mediaType: String? = "movie",
    @SerializedName("vote_average") val voteAverage: Double? = null, // Added
    @SerializedName("genre_ids") val genreIds: List<Int>? = emptyList(), // Added
) {
    val displayTitle: String get() = (title ?: name).orEmpty().ifEmpty { "Untitled" }
    val isMovie: Boolean get() = mediaType?.lowercase() == "movie" || (title != null && name == null)
    val isTv: Boolean get() = mediaType?.lowercase() == "tv" || (name != null && title == null)
    val isAnime: Boolean get() = mediaType?.lowercase() == "anime" || genreIds?.contains(16) == true
    // Helper to get the year regardless of format
    val displayYear: String get() = (releaseDate ?: firstAirDate)?.take(4).orEmpty()


}

data class TmdbSeasonResponse(
    val episodes: List<TmdbEpisode> = emptyList()
)

data class TmdbEpisode(
    @SerializedName("episode_number") val episodeNumber: Int,
    val name: String? = null,
    val overview: String? = null,
    @SerializedName("still_path") val stillPath: String? = null,
    @SerializedName("runtime") val runtime: Int? = null
)

// AllAnime data models for anime episode resolution
data class AllAnimeSearchResponse(
    val data: AllAnimeSearchData?
)

data class AllAnimeSearchData(
    val shows: AllAnimeShowsEdges?
)

data class AllAnimeShowsEdges(
    val edges: List<AllAnimeShow>? = emptyList()
)

data class AllAnimeShow(
    @SerializedName("_id") val aid: String = "",
    val name: String = "",
    val thumbnail: String? = null,
    @SerializedName("availableEpisodes") val episodeCount: Int? = null
)

data class AllAnimeEpisodeResponse(
    val data: AllAnimeEpisodeData?
)

data class AllAnimeEpisodeData(
    val episode: AllAnimeEpisode?
)

data class AllAnimeEpisode(
    val episodeString: String? = "",
    val sourceUrls: List<AllAnimeSourceUrl>? = emptyList()
)

data class AllAnimeSourceUrl(
    val sourceUrl: String? = "",
    val sourceName: String? = "",
    val priority: Float? = 0f
)
