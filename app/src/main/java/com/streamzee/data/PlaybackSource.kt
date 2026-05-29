package com.streamzee.data

enum class PlaybackContentType {
    MOVIE_TV,
    ANIME
}

data class PlaybackSource(
    val contentType: PlaybackContentType,
    val id: String,
    val label: String,
    val tag: String? = null,
    val note: String? = null,
    val supportsProgress: Boolean,
    val progressViaFrames: Boolean = false,
    val async: Boolean = false,
    val movieUrl: (String) -> String,
    val movieUrlCandidates: List<(String) -> String> = emptyList(),
    val tvUrl: (String, Int, Int) -> String = { _, _, _ -> "" },
    val tvUrlCandidates: List<(String, Int, Int) -> String> = emptyList(),
)

val playerSources = listOf(

    // =========================
    // MOVIE / TV SOURCES
    // =========================

    PlaybackSource(
        contentType = PlaybackContentType.MOVIE_TV,

        id = "videasy",
        label = "Videasy",

        supportsProgress = true,

        movieUrl = { id ->
            "https://player.videasy.net/movie/$id"
        },

        movieUrlCandidates = listOf(
            { id ->
                "https://player.videasy.net/movie/$id"
            },
            { id ->
                "https://www.videasy.net/movie/$id"
            }
        ),

        tvUrl = { id, season, episode ->
            "https://player.videasy.net/tv/$id/$season/$episode"
        },

        tvUrlCandidates = listOf(
            { id, season, episode ->
                "https://player.videasy.net/tv/$id/$season/$episode"
            },
            { id, season, episode ->
                "https://www.videasy.net/tv/$id/$season/$episode"
            }
        )
    ),

    PlaybackSource(
        contentType = PlaybackContentType.MOVIE_TV,

        id = "vidsrc",
        label = "VidSrc",

        supportsProgress = true,
        progressViaFrames = true,

        movieUrl = { id ->
            "https://vidsrc.to/embed/movie/$id"
        },

        movieUrlCandidates = listOf(
            { id ->
                "https://vidsrc.to/embed/movie/$id"
            },
            { id ->
                "https://www.vidsrc.to/embed/movie/$id"
            }
        ),

        tvUrl = { id, season, episode ->
            "https://vidsrc.to/embed/tv/$id/$season/$episode"
        },

        tvUrlCandidates = listOf(
            { id, season, episode ->
                "https://vidsrc.to/embed/tv/$id/$season/$episode"
            },
            { id, season, episode ->
                "https://www.vidsrc.to/embed/tv/$id/$season/$episode"
            }
        )
    ),

    // =========================
    // ANIME SOURCES
    // =========================

    PlaybackSource(
        contentType = PlaybackContentType.ANIME,

        id = "megaplay",
        label = "MegaPlay",

        tag = "ANIME",

        supportsProgress = true,
        async = true,

        movieUrl = {
            "https://megaplay.buzz"
        },

        tvUrl = { _, _, _ ->
            "https://megaplay.buzz"
        }
    )
)


// =======================================
// FILTERED LISTS (OPTIONAL HELPERS)
// =======================================

val movieTvPlayerSources =
    playerSources.filter {
        it.contentType == PlaybackContentType.MOVIE_TV
    }

val animePlayerSources =
    playerSources.filter {
        it.contentType == PlaybackContentType.ANIME
    }