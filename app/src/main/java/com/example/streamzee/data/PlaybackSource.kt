package com.example.streamzee.data

data class PlaybackSource(
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
    PlaybackSource(
        id = "videasy",
        label = "Videasy",
        supportsProgress = true,
        movieUrl = { id -> "https://player.videasy.net/movie/$id" },
        movieUrlCandidates = listOf(
            { id -> "https://player.videasy.net/movie/$id" },
            { id -> "https://www.videasy.net/movie/$id" },
        ),
        tvUrl = { id, season, episode -> "https://player.videasy.net/tv/$id/$season/$episode" },
        tvUrlCandidates = listOf(
            { id, season, episode -> "https://player.videasy.net/tv/$id/$season/$episode" },
            { id, season, episode -> "https://www.videasy.net/tv/$id/$season/$episode" },
        ),
    ),
    PlaybackSource(
        id = "vidsrc",
        label = "VidSrc",
        supportsProgress = true,
        progressViaFrames = true,
        movieUrl = { id -> "https://vidsrc.to/embed/movie/$id" },
        movieUrlCandidates = listOf(
            { id -> "https://vidsrc.to/embed/movie/$id" },
            { id -> "https://www.vidsrc.to/embed/movie/$id" },
        ),
        tvUrl = { id, season, episode -> "https://vidsrc.to/embed/tv/$id/$season/$episode" },
        tvUrlCandidates = listOf(
            { id, season, episode -> "https://vidsrc.to/embed/tv/$id/$season/$episode" },
            { id, season, episode -> "https://www.vidsrc.to/embed/tv/$id/$season/$episode" },
        ),
    ),
    PlaybackSource(
        id = "2embed",
        label = "2Embed",
        note = "unstable",
        supportsProgress = true,
        progressViaFrames = true,
        movieUrl = { id -> "https://www.2embed.online/embed/movie/$id" },
        movieUrlCandidates = listOf(
            { id -> "https://www.2embed.online/embed/movie/$id" },
            { id -> "https://2embed.to/embed/movie/$id" },
        ),
        tvUrl = { id, season, episode -> "https://www.2embed.online/embed/tv/$id/$season/$episode" },
        tvUrlCandidates = listOf(
            { id, season, episode -> "https://www.2embed.online/embed/tv/$id/$season/$episode" },
            { id, season, episode -> "https://2embed.to/embed/tv/$id/$season/$episode" },
        ),
    ),
    PlaybackSource(
        id = "anikoto",
        label = "Anikoto",
        tag = "ANIME",
        supportsProgress = true,
        async = true,
        movieUrl = { _ -> "https://megaplay.buzz" },
        tvUrl = { _, _, _ -> "https://megaplay.buzz" },
    ),
)
