package com.example.streamzee.data

import com.google.gson.annotations.SerializedName

data class TmdbMovieResponse(
    val page: Int,
    val results: List<TmdbMovie> = emptyList(),
)

data class TmdbMovie(
    val id: Long,
    val title: String?,
    val overview: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("media_type") val mediaType: String? = "movie",
    val name: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
) {
    val displayTitle: String get() = (title ?: name).orEmpty().ifEmpty { "Untitled" }
    val isAnime: Boolean get() = mediaType?.lowercase() == "anime"
    val isTv: Boolean get() = mediaType?.lowercase() == "tv"
    val isMovie: Boolean get() = mediaType?.lowercase() != "tv" && mediaType?.lowercase() != "anime"
}

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
    val _id: String = "",
    val name: String = ""
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
