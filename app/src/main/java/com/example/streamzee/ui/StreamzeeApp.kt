package com.example.streamzee.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.streamzee.data.playerSources
import com.example.streamzee.ui.screens.animeDetailsScreen
import com.example.streamzee.ui.screens.animePlayerScreen
import com.example.streamzee.ui.screens.detailsScreen
import com.example.streamzee.ui.screens.downloadsScreen
import com.example.streamzee.ui.screens.homeScreen
import com.example.streamzee.ui.screens.libraryScreen
import com.example.streamzee.ui.screens.playerScreen
import com.example.streamzee.ui.screens.profileScreen
import com.example.streamzee.ui.screens.searchScreen
import com.example.streamzee.ui.screens.setupScreen
import com.example.streamzee.ui.theme.streamzeeTheme
import com.example.streamzee.viewmodel.MainViewModel
import com.example.streamzee.viewmodel.Screen

@Composable
fun streamzeeApp(viewModel: MainViewModel) {
    val uiState = viewModel.uiState.collectAsState().value
    val screen = uiState.currentScreen

    val showBottomBar = screen is Screen.Home ||
            screen is Screen.Search ||
            screen is Screen.Library ||
            screen is Screen.Downloads ||
            screen is Screen.Profile

    streamzeeTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF050508)) {
            Scaffold(
                bottomBar = {
                    if (showBottomBar) {
                        NavigationBar(
                            containerColor = Color(0xFF09090F),
                            tonalElevation = 8.dp
                        ) {
                            NavigationBarItem(
                                selected = screen is Screen.Home,
                                onClick = { viewModel.openHome() },
                                icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Home") },
                                label = { Text("Home") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFFA855F7),
                                    selectedTextColor = Color(0xFFA855F7),
                                    indicatorColor = Color(0xFF1E1B29),
                                    unselectedIconColor = Color(0xFF8E8E9F),
                                    unselectedTextColor = Color(0xFF8E8E9F)
                                )
                            )
                            NavigationBarItem(
                                selected = screen is Screen.Search,
                                onClick = { viewModel.openSearch() },
                                icon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Explore") },
                                label = { Text("Explore") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFFA855F7),
                                    selectedTextColor = Color(0xFFA855F7),
                                    indicatorColor = Color(0xFF1E1B29),
                                    unselectedIconColor = Color(0xFF8E8E9F),
                                    unselectedTextColor = Color(0xFF8E8E9F)
                                )
                            )
                            NavigationBarItem(
                                selected = screen is Screen.Downloads,
                                onClick = { viewModel.openDownloads() },
                                icon = { Icon(imageVector = Icons.Default.Download, contentDescription = "Downloads") },
                                label = { Text("Downloads") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFFA855F7),
                                    selectedTextColor = Color(0xFFA855F7),
                                    indicatorColor = Color(0xFF1E1B29),
                                    unselectedIconColor = Color(0xFF8E8E9F),
                                    unselectedTextColor = Color(0xFF8E8E9F)
                                )
                            )
                            NavigationBarItem(
                                selected = screen is Screen.Library,
                                onClick = { viewModel.openLibrary() },
                                icon = { Icon(imageVector = Icons.Default.Favorite, contentDescription = "Watchlist") },
                                label = { Text("Watchlist") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFFA855F7),
                                    selectedTextColor = Color(0xFFA855F7),
                                    indicatorColor = Color(0xFF1E1B29),
                                    unselectedIconColor = Color(0xFF8E8E9F),
                                    unselectedTextColor = Color(0xFF8E8E9F)
                                )
                            )
                            NavigationBarItem(
                                selected = screen is Screen.Profile,
                                onClick = { viewModel.openProfile() },
                                icon = { Icon(imageVector = Icons.Default.Person, contentDescription = "Profile") },
                                label = { Text("Profile") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFFA855F7),
                                    selectedTextColor = Color(0xFFA855F7),
                                    indicatorColor = Color(0xFF1E1B29),
                                    unselectedIconColor = Color(0xFF8E8E9F),
                                    unselectedTextColor = Color(0xFF8E8E9F)
                                )
                            )
                        }
                    }
                }
            ) { contentPadding ->
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
                        searchMode = uiState.searchMode,
                        searchResults = uiState.searchResults,
                        animeSearchResults = uiState.animeSearchResults,
                        onQueryChange = viewModel::updateSearchQuery,
                        onSearchSubmit = viewModel::search,
                        onModeSelected = viewModel::updateSearchMode,
                        onMovieClicked = viewModel::openDetails,
                        onAnimeClicked = viewModel::openAnimeDetails,
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
                    is Screen.Downloads -> downloadsScreen(
                        uiState = uiState,
                        onBack = viewModel::openHome,
                        modifier = contentModifier,
                    )
                    is Screen.Profile -> profileScreen(
                        uiState = uiState,
                        updateTheme = viewModel::updateThemeMode,
                        updateQuality = viewModel::updatePlaybackQuality,
                        updateLanguage = viewModel::updateLanguagePreference,
                        toggleSubtitles = viewModel::toggleSubtitles,
                        toggleNotifications = viewModel::toggleNotifications,
                        onLogout = viewModel::openHome,
                        modifier = contentModifier,
                    )
                    is Screen.Details -> detailsScreen(
                        movie = screen.movie,
                        episodes = uiState.currentSeasonEpisodes,
                        similarMovies = uiState.trendingMovies,
                        resumePositionMs = uiState.currentMovieWatchProgressMs,
                        isSaved = uiState.savedIds.contains(screen.movie.id.toString()),
                        onBack = viewModel::openHome,
                        onToggleSave = { viewModel.toggleSaved(screen.movie.id.toString()) },
                        onPlay = { id, season, episode, position -> 
                                    viewModel.openPlayer(
                                        movie = screen.movie, 
                                        source = playerSources.first(), 
                                        tvSeason = season, 
                                        tvEpisode = episode,
                                        resumePositionMs = position // Pass the timestamp to the ViewModel
                                    ) 
                                },
                        onSeasonChange = { id, seasonNumber -> viewModel.loadSeason(id, seasonNumber) }, // Added
                        onMovieClicked = { movie -> viewModel.openDetails(movie) }, // Added
                        // onPlayEpisode = { /* ... */ },
                        modifier = contentModifier
                    )
                    is Screen.Player -> playerScreen(
                        movie = screen.movie,
                        source = screen.source,
                        resumePositionMs = uiState.currentMovieWatchProgressMs,
                        onBack = { viewModel.openDetails(screen.movie) },
                        onPlaybackPositionUpdate = { positionMs ->
                            viewModel.savePlaybackProgress(screen.movie.id.toString(), positionMs)
                        },
                        tvSeason = screen.tvSeason,
                        tvEpisode = screen.tvEpisode,
                        modifier = contentModifier,
                    )
                    is Screen.AnimeDetails -> animeDetailsScreen(
                        show = screen.show,
                        onBack = viewModel::openSearch,
                        onPlayEpisode = { episode -> viewModel.openAnimePlayer(screen.show, episode) },
                        modifier = contentModifier,
                    )
                    is Screen.AnimePlayer -> animePlayerScreen(
                        show = screen.show,
                        episode = screen.episode,
                        onBack = { viewModel.openAnimeDetails(screen.show) },
                        resolveEpisode = viewModel::resolveAnimeEpisode,
                        modifier = contentModifier,
                    )
                }
            }
        }
    }
}
