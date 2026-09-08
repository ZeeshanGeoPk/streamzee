package com.streamzee.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.streamzee.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun musicModeHasItsOwnNavigationAndEditablePlaylists() {
        compose.onNodeWithText("Music", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your music").assertIsDisplayed()
        compose.onNodeWithText("Offline").assertIsDisplayed()
        compose.onNodeWithText("Watchlist").assertDoesNotExist()
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Create playlist").performClick()
        val name = "Test playlist ${System.nanoTime()}"
        compose.onNode(hasSetTextAction()).performTextInput(name)
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText(name).assertIsDisplayed().performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Renamed playlist")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Renamed playlist").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        compose.onAllNodesWithText("Delete").onLast().performClick()
        compose.onNodeWithText("Renamed playlist").assertDoesNotExist()
        compose.onNodeWithText("Movies · TV · Anime", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Offline").assertDoesNotExist()
    }
}
