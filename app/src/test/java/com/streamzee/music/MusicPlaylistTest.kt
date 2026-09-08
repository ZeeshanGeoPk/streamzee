package com.streamzee.music

import org.junit.Assert.*
import org.junit.Test

class MusicPlaylistTest {
    private fun song(id: String) = MusicTrack(id, id, "Artist", null, 60)
    @Test fun reorderPreservesSongsAndDoesNotMutateOriginal() {
        val original = listOf(song("a"), song("b"), song("c"))
        assertEquals(listOf("b", "a", "c"), moveMusicTrack(original, "b", -1).map { it.id })
        assertEquals(listOf("a", "c", "b"), moveMusicTrack(original, "b", 1).map { it.id })
        assertEquals(listOf("a", "b", "c"), original.map { it.id })
    }
    @Test fun reorderHandlesEmptyMissingAndBoundaryItems() {
        assertTrue(moveMusicTrack(emptyList(), "a", 1).isEmpty())
        val songs = listOf(song("a"), song("b"))
        assertEquals(songs, moveMusicTrack(songs, "a", -1))
        assertEquals(songs, moveMusicTrack(songs, "b", 1))
        assertEquals(songs, moveMusicTrack(songs, "missing", 1))
    }
}
