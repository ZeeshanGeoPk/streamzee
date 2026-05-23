package com.example.streamzee.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.example.streamzee.data.playerSources
import com.example.streamzee.ui.screens.detailsScreen
import com.example.streamzee.ui.screens.homeScreen
import com.example.streamzee.ui.screens.libraryScreen
import com.example.streamzee.ui.screens.playerScreen
import com.example.streamzee.ui.screens.searchScreen
import com.example.streamzee.ui.screens.setupScreen
import com.example.streamzee.ui.theme.streamzeeTheme
import com.example.streamzee.viewmodel.MainViewModel
import com.example.streamzee.viewmodel.Screen

@Composable
fun streamzeeApp(viewModel: MainViewModel) {
    val uiState = viewModel.uiState.collectAsState().value

    streamzeeTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold { contentPadding ->
                val screen = uiState.currentScreen
                val contentModifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                when (screen) {
                    is Screen.Setup -> setupScreen(
                        onSaveToken = viewModel::saveApiKey,
                        isLoading = uiState.isLoading,
                        errorMessage = uiState.errorMessage,
                        modifier = contentModifier,
                    )
                    is Screen.Home -> homeScreen(
                        trendingMovies = uiState.trendingMovies,
                        savedIds = uiState.savedIds,
                        onSearchClicked = viewModel::openSearch,
                        onLibraryClicked = viewModel::openLibrary,
                        onMovieClicked = viewModel::openDetails,
                        onToggleSave = viewModel::toggleSaved,
                        isLoading = uiState.isLoading,
                        errorMessage = uiState.errorMessage,
                        modifier = contentModifier,
                    )
                    is Screen.Search -> searchScreen(
                        query = uiState.searchQuery,
                        searchResults = uiState.searchResults,
                        onQueryChange = viewModel::updateSearchQuery,
                        onSearchSubmit = viewModel::searchMovies,
                        onMovieClicked = viewModel::openDetails,
                        onBack = viewModel::openHome,
                        isSearching = uiState.isSearching,
                        errorMessage = uiState.errorMessage,
                        modifier = contentModifier,
                    )
                    is Screen.Library -> libraryScreen(
                        savedMovies = uiState.savedMovies,
                        savedIds = uiState.savedIds,
                        onMovieClicked = viewModel::openDetails,
                        onRemove = viewModel::toggleSaved,
                        onBack = viewModel::openHome,
                        isLoading = uiState.isLoadingSaved,
                        errorMessage = uiState.errorMessage,
                        modifier = contentModifier,
                    )
                    is Screen.Details -> detailsScreen(
                        movie = screen.movie,
                        resumePositionMs = uiState.currentMovieWatchProgressMs,
                        isSaved = uiState.savedIds.contains(screen.movie.id.toString()),
                        onBack = viewModel::openHome,
                        onToggleSave = { viewModel.toggleSaved(screen.movie.id.toString()) },
                        onPlay = { viewModel.openPlayer(screen.movie, playerSources.first()) },
                        modifier = contentModifier,
                    )
                    is Screen.Player -> playerScreen(
                        movie = screen.movie,
                        source = screen.source,
                        resumePositionMs = uiState.currentMovieWatchProgressMs,
                        onBack = { viewModel.openDetails(screen.movie) },
                        onPlaybackPositionUpdate = { positionMs ->
                            viewModel.savePlaybackProgress(screen.movie.id.toString(), positionMs)
                        },
                        modifier = contentModifier,
                    )
                }
            }
        }
    }
}
