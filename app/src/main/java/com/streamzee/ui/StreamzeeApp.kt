package com.streamzee.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import com.streamzee.data.playerSources
import com.streamzee.data.DownloadStatus
import com.streamzee.ui.screens.animeDetailsScreen
import com.streamzee.ui.screens.animePlayerScreen
import com.streamzee.ui.screens.detailsScreen
import com.streamzee.ui.screens.downloadsScreen
import com.streamzee.ui.screens.homeBrowseScreen
import com.streamzee.ui.screens.homeScreen
import com.streamzee.ui.screens.libraryScreen
import com.streamzee.ui.screens.playerScreen
import com.streamzee.ui.screens.offlinePlayerScreen
import com.streamzee.ui.screens.profileScreen
import com.streamzee.ui.screens.searchScreen
import com.streamzee.ui.screens.setupScreen
import com.streamzee.ui.theme.streamzeeTheme
import com.streamzee.viewmodel.MainViewModel
import com.streamzee.viewmodel.Screen

@Composable
fun streamzeeApp(viewModel: MainViewModel) {
    val uiState = viewModel.uiState.collectAsState().value
    val screen = uiState.currentScreen
    val goBack: () -> Unit = {
        if (!viewModel.navigateBack() && screen !is Screen.Home) {
            viewModel.openHome(addToBackStack = false)
        }
    }

    val showBottomBar = screen is Screen.Home ||
            screen is Screen.Search ||
            screen is Screen.Library ||
            screen is Screen.Downloads ||
            screen is Screen.Profile

    BackHandler(enabled = screen !is Screen.Setup && (screen !is Screen.Home || uiState.backStack.isNotEmpty())) {
        goBack()
    }

    streamzeeTheme(themeMode = uiState.themeMode, accentName = uiState.accentColor) {
        val colors = androidx.compose.material3.MaterialTheme.colorScheme
        Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                bottomBar = {
                    if (showBottomBar) {
                        NavigationBar(
                            containerColor = colors.surface,
                            tonalElevation = 8.dp
                        ) {
                            NavigationBarItem(
                                selected = screen is Screen.Home,
                                onClick = { viewModel.openHome() },
                                icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Home") },
                                label = { Text("Home") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.primary,
                                    selectedTextColor = colors.primary,
                                    indicatorColor = colors.primaryContainer,
                                    unselectedIconColor = colors.onSurfaceVariant,
                                    unselectedTextColor = colors.onSurfaceVariant
                                )
                            )
                            NavigationBarItem(
                                selected = screen is Screen.Search,
                                onClick = { viewModel.openSearch() },
                                icon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Explore") },
                                label = { Text("Explore") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.primary,
                                    selectedTextColor = colors.primary,
                                    indicatorColor = colors.primaryContainer,
                                    unselectedIconColor = colors.onSurfaceVariant,
                                    unselectedTextColor = colors.onSurfaceVariant
                                )
                            )
                            NavigationBarItem(
                                selected = screen is Screen.Downloads,
                                onClick = { viewModel.openDownloads() },
                                icon = { Icon(imageVector = Icons.Default.Download, contentDescription = "Downloads") },
                                label = { Text("Downloads") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.primary,
                                    selectedTextColor = colors.primary,
                                    indicatorColor = colors.primaryContainer,
                                    unselectedIconColor = colors.onSurfaceVariant,
                                    unselectedTextColor = colors.onSurfaceVariant
                                )
                            )
                            NavigationBarItem(
                                selected = screen is Screen.Library,
                                onClick = { viewModel.openLibrary() },
                                icon = { Icon(imageVector = Icons.Default.Favorite, contentDescription = "Watchlist") },
                                label = { Text("Watchlist") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.primary,
                                    selectedTextColor = colors.primary,
                                    indicatorColor = colors.primaryContainer,
                                    unselectedIconColor = colors.onSurfaceVariant,
                                    unselectedTextColor = colors.onSurfaceVariant
                                )
                            )
                            NavigationBarItem(
                                selected = screen is Screen.Profile,
                                onClick = { viewModel.openProfile() },
                                icon = { Icon(imageVector = Icons.Default.Person, contentDescription = "Profile") },
                                label = { Text("Profile") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.primary,
                                    selectedTextColor = colors.primary,
                                    indicatorColor = colors.primaryContainer,
                                    unselectedIconColor = colors.onSurfaceVariant,
                                    unselectedTextColor = colors.onSurfaceVariant
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
                        trendingTv = uiState.trendingTv,
                        trendingAnime = uiState.trendingAnime,
                        recentMovies = uiState.recentMovies,
                        recentTv = uiState.recentTv,
                        recentAnime = uiState.recentAnime,
                        topMovies = uiState.topMovies,
                        topTv = uiState.topTv,
                        topAnime = uiState.topAnime,
                        continueWatching = uiState.continueWatching,
                        savedIds = uiState.savedIds,
                        onSearchClicked = viewModel::openSearch,
                        onLibraryClicked = viewModel::openLibrary,
                        onMovieClicked = viewModel::openDetails,
                        onAnimeClicked = viewModel::openAnimeDetails,
                        onSeeAllClicked = viewModel::openHomeBrowse,
                        onToggleSave = { id ->
                            viewModel.toggleSaved(id)
                        },
                        isLoading = uiState.isLoading,
                        isRefreshing = uiState.isRefreshingHome,
                        reducedMotion = uiState.reducedMotion,
                        onRefresh = viewModel::refreshHome,
                        errorMessage = uiState.errorMessage,
                        modifier = contentModifier,
                    )
                    is Screen.HomeBrowse -> homeBrowseScreen(
                        browseState = uiState.homeBrowse,
                        onBack = goBack,
                        onLoadMore = viewModel::loadNextHomeBrowsePage,
                        onRefresh = viewModel::refreshHomeBrowse,
                        onMovieClicked = viewModel::openDetails,
                        onAnimeClicked = viewModel::openAnimeDetails,
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
                        onBack = goBack,
                        isSearching = uiState.isSearching,
                        errorMessage = uiState.errorMessage,
                        modifier = contentModifier,
                    )
                    is Screen.Library -> libraryScreen(
                        savedMovies = uiState.savedMovies,
                        savedAnime = uiState.savedAnime,
                        savedIds = uiState.savedIds,
                        isLoading = uiState.isLoadingSaved,
                        isRefreshing = uiState.isRefreshingLibrary,
                        onRefresh = viewModel::refreshLibrary,
                        errorMessage = uiState.errorMessage,
                        onBack = goBack,
                        onMovieClicked = { viewModel.openDetails(it) },
                        onAnimeClicked = { anime ->
                            // Convert the library object back to the Details-compatible object
                            viewModel.openAnimeDetails(
                                com.streamzee.data.MegaPlayShow(
                                    animeMalID = anime.malId.toString(),
                                    title = anime.title,
                                    image = anime.images.jpg.imageUrl,
                                    animeType = anime.type,
                                    episodeCount = anime.episodes ?: 0,
                                    score = anime.score?.toString() ?: "N/A"
                                )
                            )
                        },
                        onRemove = { id, type -> viewModel.toggleSaved("${type}_$id") },
                        modifier = contentModifier
                    )
                    is Screen.Downloads -> downloadsScreen(
                        uiState = uiState,
                        onBack = goBack,
                        onPause = viewModel::pauseDownload,
                        onResume = viewModel::resumeDownload,
                        onRetry = viewModel::retryDownload,
                        onRemove = viewModel::removeDownload,
                        onPauseAll = viewModel::pauseAllDownloads,
                        onResumeAll = viewModel::resumeAllDownloads,
                        onSettingsChange = viewModel::updateDownloadSettings,
                        onPlay = viewModel::playOfflineDownload,
                        modifier = contentModifier,
                    )
                    is Screen.Profile -> profileScreen(
                        uiState = uiState,
                        updateTheme = viewModel::updateThemeMode,
                        updateAccent = viewModel::updateAccentColor,
                        updateApiKey = viewModel::updateApiKeyFromProfile,
                        updateQuality = viewModel::updatePlaybackQuality,
                        updateLanguage = viewModel::updateLanguagePreference,
                        toggleSubtitles = viewModel::toggleSubtitles,
                        toggleNotifications = viewModel::toggleNotifications,
                        toggleReducedMotion = viewModel::toggleReducedMotion,
                        clearCache = viewModel::clearAppCache,
                        clearHistory = viewModel::clearWatchHistory,
                        clearMessage = viewModel::clearSettingsMessage,
                        modifier = contentModifier,
                    )
                    is Screen.Details -> { 
                        val movieKey = if (screen.movie.isTv) "tv_${screen.movie.tmdbID}" else "movie_${screen.movie.tmdbID}" 
                            detailsScreen(
                        movie = screen.movie,
                        episodes = uiState.currentSeasonEpisodes,
                        lastSeason = uiState.lastWatchedSeason ?: 1,
                        lastEpisode = uiState.lastWatchedEpisode ?: 1,
                        similarMovies = uiState.trendingMovies,
                        resumePositionMs = uiState.currentMovieWatchProgressMs,
                        isSaved = uiState.savedIds.contains(movieKey),
                        onBack = goBack,
                        onToggleSave = { viewModel.toggleSaved(it) },
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
                        }
                    is Screen.Player -> playerScreen(
                        movie = screen.movie,
                        source = screen.source,
                        resumePositionMs = screen.resumePositionMs,
                        onBack = goBack,
                        onPlaybackPositionUpdate = { pos, s, e -> 
                            // If the movie is a TV show, we ALWAYS save the season/episode 
                            // even if the timestamp (pos) is 0.
                            viewModel.savePlaybackProgress(
                                movieId = screen.movie.tmdbID.toString(), 
                                positionMs = pos, 
                                season = s, 
                                episode = e
                            ) 
                        },
                        isDownloadQueued = uiState.downloadsQueue.any {
                            it.status != DownloadStatus.FAILED &&
                                it.id == if (screen.tvSeason != null && screen.tvEpisode != null) {
                                    "tv_${screen.movie.tmdbID}_s${screen.tvSeason}_e${screen.tvEpisode}"
                                } else {
                                    "movie_${screen.movie.tmdbID}"
                                }
                        },
                        onDownload = { stream ->
                            if (screen.tvSeason != null && screen.tvEpisode != null) {
                                viewModel.queueTvEpisodeDownload(
                                    movie = screen.movie,
                                    season = screen.tvSeason,
                                    episode = screen.tvEpisode,
                                    stream = stream,
                                )
                            } else {
                                viewModel.queueMovieDownload(screen.movie, stream)
                            }
                        },
                        tvSeason = screen.tvSeason,
                        tvEpisode = screen.tvEpisode,
                        modifier = contentModifier,
                    )
                    is Screen.AnimeDetails -> {
                        val animeKey = "anime_${screen.show.animeID}" 
                            animeDetailsScreen(
                        show = screen.show,
                        episodes = uiState.animeEpisodes,
                        isLoading = uiState.isLoading,
                        errorMessage = uiState.errorMessage,
                        selectedTranslation = uiState.selectedTranslationType,
                        onTranslationChange = { viewModel.updateAnimeTranslation(it) },
                        onBack = goBack,
                        onToggleSave = { viewModel.toggleSaved(it) },
                        isSaved = uiState.savedIds.contains(animeKey),
                        resumePositionMs = uiState.currentAnimeWatchProgressMs ?: 0L,
                        lastWatchedEpisode = uiState.lastWatchedAnimeEpisode ?: 1,
                        onPlayEpisode = { epNum ->
                            viewModel.playAnime(screen.show, epNum, resumePositionMs = 0L)
                        },
                        onResumeEpisode = { epNum, positionMs ->
                            viewModel.playAnime(screen.show, epNum, positionMs)
                        },
                        modifier = contentModifier
                    )
                        }
                    
                    is Screen.AnimePlayer -> animePlayerScreen(
                        show = screen.show,
                        episode = screen.episode,
                        streamUrl = screen.streamUrl, // Add this
                        resumePositionMs = screen.resumePositionMs,
                        onPlaybackPositionUpdate = { positionMs ->
                            viewModel.saveAnimePlaybackProgress(
                                animeId = screen.show.animeID,
                                episode = screen.episode,
                                positionMs = positionMs,
                            )
                        },
                        isDownloadQueued = uiState.downloadsQueue.any {
                            it.status != DownloadStatus.FAILED &&
                                it.id == "anime_${screen.show.animeID}_e${screen.episode}_${screen.translationType}"
                        },
                        onDownload = { stream ->
                            viewModel.queueAnimeEpisodeDownload(
                                show = screen.show,
                                episode = screen.episode,
                                stream = stream,
                            )
                        },
                        onBack = goBack,
                        modifier = contentModifier,
                    )
                    is Screen.OfflinePlayer -> offlinePlayerScreen(
                        downloadId = screen.downloadId,
                        onBack = goBack,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            }
        }
    }
}
