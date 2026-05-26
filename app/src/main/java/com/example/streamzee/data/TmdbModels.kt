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

// Updated Anime Model for Anikoto
data class AnikotoSearchResponse(
    val results: List<AnikotoShow> = emptyList()
)

data class AnikotoShow(
    val id: String, // This stores the mal_id from Jikan
    val title: String,
    val image: String?,
    @SerializedName("type") val animeType: String? = null,
    val episodeCount: Int? = 0,
    val score: String? = "N/A" // Added to match UI expectation
) {
    val aid: String get() = id
    val name: String get() = title
    val thumbnail: String? get() = image
}

data class AnikotoSeriesResponse(
    val id: String,
    val title: String,
    val episodes: List<AnikotoEpisode> = emptyList()
)

data class AnikotoEpisode(
    val number: Int,
    // Use SerializedName to match the API's snake_case key
    @SerializedName("episode_embed_id") val episodeEmbedId: String 
)

// Jikan Search Models
data class JikanSearchResponse(
    val data: List<JikanAnime> = emptyList()
)

data class JikanAnime(
    @SerializedName("mal_id") val malId: Int,
    val title: String,
    val images: JikanImages,
    val type: String?,
    val episodes: Int?,
    val score: Double? // Added to capture rating
)

data class JikanImages(
    val jpg: JikanImageUrls
)

data class JikanImageUrls(
    @SerializedName("image_url") val imageUrl: String
)

// Bridge Model (MAL ID -> Anikoto/HiAnime ID)
data class AnimeBridgeResponse(
    val id: String // The Anikoto ID (e.g. "solo-leveling-18718")
)

data class MalSyncResponse(
    @SerializedName("Sites") val sites: Map<String, Map<String, MalSyncSiteItem>>?
)

data class MalSyncSiteItem(
    val id: Int,             // This is the numeric ID (e.g., 346 or 81392)
    val identifier: String,
    val url: String
)