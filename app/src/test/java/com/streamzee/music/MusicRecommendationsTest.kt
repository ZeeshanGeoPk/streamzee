package com.streamzee.music

import org.junit.Assert.*
import org.junit.Test

class MusicRecommendationsTest {
    private fun song(id: String, artist: String = "Artist") = MusicTrack(id, "Song $id", artist, null, 180)

    @Test fun coldStartDoesNotInventTasteAndIgnoresUnknownArtists() {
        assertTrue(recommendationSeeds(MusicLibraryState()).isEmpty())
        assertTrue(recommendationSeeds(MusicLibraryState(recent = listOf(song("1", ""), song("2", "Unknown")))).isEmpty())
    }
    @Test fun favoritesRecencyAndPlaylistsContributeWithNormalizedArtists() {
        val state = MusicLibraryState(
            favorites = listOf(song("1", "Artist - Topic")),
            recent = listOf(song("2", "artist"), song("3", "Other")),
            playlists = listOf(MusicPlaylist("p", "Mix", listOf(song("4", "Third")))),
        )
        val seeds = recommendationSeeds(state)
        assertEquals(3, seeds.size)
        assertEquals("Artist", seeds.first().artist)
        assertEquals(22, seeds.first().weight)
        assertTrue(seeds.any { it.artist == "Third" })
    }
    @Test fun excludesKnownAndDismissedTracksAndRemovesDuplicates() {
        val seeds = listOf(RecommendationSeed("Artist", 12), RecommendationSeed("Other", 10))
        val candidates = mapOf("Artist" to listOf(song("known"), song("hidden"), song("new")),
            "Other" to listOf(song("new"), song("different", "Other")))
        val ranked = rankRecommendations(seeds, candidates, setOf("known", "hidden"))
        assertEquals(setOf("new", "different"), ranked.map { it.track.id }.toSet())
        assertEquals(2, ranked.size)
        assertEquals("Inspired by Artist", ranked.first().reason)
    }
    @Test fun limitsArtistRepetitionAndTotalRecommendations() {
        val seeds = listOf(RecommendationSeed("Artist", 12))
        val candidates = mapOf("Artist" to (1..30).map { song("$it") })
        assertEquals(4, rankRecommendations(seeds, candidates, emptySet()).size)
        val diverse = mapOf("Artist" to (1..30).map { song("$it", "Artist $it") })
        assertEquals(20, rankRecommendations(seeds, diverse, emptySet()).size)
    }
    @Test fun boundsDiscoveryToThreeArtists() {
        assertEquals(3, recommendationSeeds(MusicLibraryState(favorites = (1..20).map { song("$it", "Artist $it") })).size)
    }
}
