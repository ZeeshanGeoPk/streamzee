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
) {
    val displayTitle: String get() = title.orEmpty().ifEmpty { "Untitled" }
}
