package com.streamzee.music

import java.util.Locale

data class MusicRecommendation(val track: MusicTrack, val reason: String)
internal data class RecommendationSeed(val artist: String, val weight: Int)

internal fun artistKey(artist: String): String = artist.trim()
    .replace(Regex("\\s+-\\s+Topic$", RegexOption.IGNORE_CASE), "")
    .lowercase(Locale.ROOT)

/** Local taste model: likes carry most weight, followed by recent listening and playlists. */
internal fun recommendationSeeds(state: MusicLibraryState): List<RecommendationSeed> {
    val weights = linkedMapOf<String, Int>()
    val names = mutableMapOf<String, String>()
    fun add(track: MusicTrack, weight: Int) {
        val key = artistKey(track.artist)
        if (key.isBlank() || key == "unknown" || key == "null") return
        names.putIfAbsent(key, track.artist.trim().replace(Regex("\\s+-\\s+Topic$", RegexOption.IGNORE_CASE), ""))
        weights[key] = (weights[key] ?: 0) + weight
    }
    state.favorites.forEach { add(it, 12) }
    state.recent.take(20).forEachIndexed { index, track -> add(track, (10 - index / 2).coerceAtLeast(1)) }
    state.playlists.flatMap { it.tracks }.distinctBy { it.id }.forEach { add(it, 3) }
    return weights.entries.sortedByDescending { it.value }.take(3)
        .map { RecommendationSeed(names.getValue(it.key), it.value) }
}

internal fun rankRecommendations(
    seeds: List<RecommendationSeed>,
    candidates: Map<String, List<MusicTrack>>,
    excludedIds: Set<String>,
): List<MusicRecommendation> {
    data class Scored(val track: MusicTrack, val reason: String, val score: Int)
    val ranked = seeds.flatMap { seed ->
        candidates[seed.artist].orEmpty().mapIndexed { index, track ->
            Scored(track, "Inspired by ${seed.artist}", seed.weight + (20 - index).coerceAtLeast(0) +
                if (artistKey(track.artist) == artistKey(seed.artist)) 10 else 0)
        }
    }.filter { it.track.id !in excludedIds && it.track.title.isNotBlank() }
        .sortedByDescending { it.score }.distinctBy { it.track.id }
    val artistCounts = mutableMapOf<String, Int>()
    return ranked.filter {
        val key = artistKey(it.track.artist)
        val count = artistCounts[key] ?: 0
        if (count >= 4) false else { artistCounts[key] = count + 1; true }
    }.take(20).map { MusicRecommendation(it.track, it.reason) }
}
