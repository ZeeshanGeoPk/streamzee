package com.streamzee.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamzee.data.NetworkClient
import com.streamzee.data.PlaybackSource
import com.streamzee.data.TmdbMovie
import com.streamzee.data.TmdbEpisode
import com.streamzee.data.MegaPlayShow
import com.streamzee.data.MegaPlayEpisode
import com.streamzee.data.JikanAnime
import com.streamzee.repository.StreamzeeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

//val movies = repository.fetchTrending(apiKey)

enum class SearchMode {
    MOVIES,
    TV,
    ANIME,
}

enum class HomeSection(val title: String, val isAnime: Boolean = false) {
    TRENDING_MOVIES("Trending Movies"),
    TRENDING_TV("Trending TV Shows"),
    TRENDING_ANIME("Trending Anime", isAnime = true),
    RECENT_MOVIES("New Movies"),
    RECENT_TV("New TV Shows"),
    RECENT_ANIME("New Anime", isAnime = true),
    TOP_MOVIES("Top Movies"),
    TOP_TV("Top TV Shows"),
    TOP_ANIME("Top Anime", isAnime = true),
}

sealed interface Screen {
    object Home : Screen
    object Search : Screen
    object Library : Screen
    object Downloads : Screen
    object Profile : Screen
    data class HomeBrowse(val section: HomeSection) : Screen
    data class Details(val movie: TmdbMovie) : Screen
    data class Player(
        val movie: TmdbMovie,
        val source: PlaybackSource,
        val tvSeason: Int? = null,
        val tvEpisode: Int? = null,
        val resumePositionMs: Long? = null,
    ) : Screen
    data class AnimeDetails(val show: MegaPlayShow) : Screen
    data class AnimePlayer(
        val show: MegaPlayShow,
        val episode: Int,
        val streamUrl: String, // The resolved direct link
        val translationType: String = "sub",
    ) : Screen
    object Setup : Screen
}

data class CustomCollection(
    val name: String,
    val itemCount: Int,
    val imageUrl: String
)

data class DownloadItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val sizeBytes: Long,
    val downloadedBytes: Long,
    var status: String, // "Downloading", "Paused", "Completed", "Failed"
    val imageUrl: String
)

data class HomeBrowseUiState(
    val section: HomeSection? = null,
    val movies: List<TmdbMovie> = emptyList(),
    val anime: List<MegaPlayShow> = emptyList(),
    val nextPage: Int = 1,
    val isLoading: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
)

data class ContinueWatchingItem(
    val movie: TmdbMovie,
    val progress: Float,
    val positionMs: Long,
    val season: Int? = null,
    val episode: Int? = null,
)

data class MainUiState(
    val apiKey: String? = null,
    val lastWatchedSeason: Int? = null,
    val lastWatchedEpisode: Int? = null,
    val currentSeasonEpisodes: List<TmdbEpisode> = emptyList(),
    val currentScreen: Screen = Screen.Setup,
    val backStack: List<Screen> = emptyList(),
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val trendingMovies: List<TmdbMovie> = emptyList(),
    val savedIds: Set<String> = emptySet(),
    val savedMovies: List<TmdbMovie> = emptyList(),
    val savedAnime: List<JikanAnime> = emptyList(),
    val searchMode: SearchMode = SearchMode.MOVIES,
    val searchQuery: String = "",
    val searchResults: List<TmdbMovie> = emptyList(),
    val animeSearchResults: List<MegaPlayShow> = emptyList(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val isLoadingSaved: Boolean = false,
    val currentMovieWatchProgressMs: Long? = null,
    val subtitleSearchResults: List<com.streamzee.data.SubtitleItem> = emptyList(),
    val isSearchingSubtitles: Boolean = false,
    val subtitleErrorMessage: String? = null,
    val errorMessage: String? = null,
    val trendingAll: List<TmdbMovie> = emptyList(),
    val trendingTv: List<TmdbMovie> = emptyList(),
    val trendingAnime: List<MegaPlayShow> = emptyList(),
    val topMovies: List<TmdbMovie> = emptyList(),
    val topTv: List<TmdbMovie> = emptyList(),
    val topAnime: List<MegaPlayShow> = emptyList(),
    val recentMovies: List<TmdbMovie> = emptyList(),
    val recentTv: List<TmdbMovie> = emptyList(),
    val recentAnime: List<MegaPlayShow> = emptyList(),
    val homeBrowse: HomeBrowseUiState = HomeBrowseUiState(),
    
    // Premium custom states
    val themeMode: String = "Dark",
    val playbackQuality: String = "Auto (Best)",
    val languagePreference: String = "English",
    val subtitlesEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val hoursWatched: Int = 285,
    val completedAnimeCount: Int = 32,
    val customCollections: List<CustomCollection> = listOf(
        CustomCollection("Weekend Movies", 12, "https://image.tmdb.org/t/p/w500/or06vlH62MvjAcZgOI27H14HjK8.jpg"),
        CustomCollection("Best Action Anime", 25, "https://image.tmdb.org/t/p/w500/1X6v4t7j5j1zQoFhY75kG4Qd81m.jpg"),
        CustomCollection("Family Watchlist", 18, "https://image.tmdb.org/t/p/w500/jRXYjXN1CYegZJ2gZo58BMj7u0T.jpg")
    ),
    val downloadsQueue: List<DownloadItem> = listOf(
        DownloadItem("dl_1", "Demon Slayer: Kimetsu no Yaiba", "S3 E5", 600_000_000L, 245_000_000L, "Downloading", "https://image.tmdb.org/t/p/w300/1X6v4t7j5j1zQoFhY75kG4Qd81m.jpg"),
        DownloadItem("dl_2", "The Batman", "Movie", 1_200_000_000L, 300_000_000L, "Downloading", "https://image.tmdb.org/t/p/w300/74xTEgt7R36F650zOn25oHqggzV.jpg"),
        DownloadItem("dl_3", "Jujutsu Kaisen", "S2 E10", 400_000_000L, 400_000_000L, "Paused", "https://image.tmdb.org/t/p/w300/oio9oVea5Y5iA8J9x3K1QZ51m.jpg"),
        DownloadItem("dl_4", "Attack on Titan", "S4 (17 Episodes)", 4_200_000_000L, 4_200_000_000L, "Completed", "https://image.tmdb.org/t/p/w300/h56O0jfHwY7e47xO6Jb2tYVn3mC.jpg"),
        DownloadItem("dl_5", "Puss in Boots: The Last Wish", "Movie", 1_100_000_000L, 1_100_000_000L, "Completed", "https://image.tmdb.org/t/p/w300/kuf6mR2IYH4szcc2653IY37jU55.jpg"),
        DownloadItem("dl_6", "Breaking Bad", "S1 E1", 120_000_000L, 120_000_000L, "Failed", "https://image.tmdb.org/t/p/w300/ggFHwq43upj6H1jOb5870YjOE1Z.jpg")
    ),
    val storageUsedGb: Double = 45.6,
    val storageTotalGb: Double = 128.0,
    val selectedTranslationType: String = "sub", // Added
    val animeEpisodes: List<MegaPlayEpisode> = emptyList() // Added
)

private data class HomeContent(
    val trendingMovies: List<TmdbMovie>,
    val trendingTv: List<TmdbMovie>,
    val trendingAnime: List<MegaPlayShow>,
    val recentMovies: List<TmdbMovie>,
    val recentTv: List<TmdbMovie>,
    val recentAnime: List<MegaPlayShow>,
    val topMovies: List<TmdbMovie>,
    val topTv: List<TmdbMovie>,
    val topAnime: List<MegaPlayShow>,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StreamzeeRepository(NetworkClient.tmdbApi, application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private fun navigateTo(screen: Screen, addToBackStack: Boolean = true) {
        _uiState.update { state ->
            val nextBackStack = if (
                addToBackStack &&
                state.currentScreen !is Screen.Setup &&
                state.currentScreen != screen
            ) {
                state.backStack + state.currentScreen
            } else {
                state.backStack
            }

            state.copy(
                currentScreen = screen,
                backStack = nextBackStack,
                errorMessage = null,
                currentMovieWatchProgressMs = null,
            )
        }
    }

    fun navigateBack(): Boolean {
        val previous = _uiState.value.backStack.lastOrNull() ?: return false

        _uiState.update {
            it.copy(
                currentScreen = previous,
                backStack = it.backStack.dropLast(1),
                errorMessage = null,
                currentMovieWatchProgressMs = if (previous is Screen.Player) it.currentMovieWatchProgressMs else null,
            )
        }

        if (previous is Screen.Library) {
            val ids = _uiState.value.savedIds
            loadSavedMovies(_uiState.value.apiKey, ids)
            loadSavedAnime(ids)
        }

        if (previous is Screen.Details) {
            if (previous.movie.isTv) {
                loadSeason(previous.movie.tmdbID, _uiState.value.lastWatchedSeason ?: 1)
            }
            loadWatchProgress(previous.movie.tmdbID.toString())
        }

        return true
    }

    init {
        viewModelScope.launch {
            repository.apiKeyFlow().collectLatest { apiKey ->
                _uiState.update { state ->
                    val screen = if (apiKey.isNullOrBlank()) {
                        Screen.Setup
                    } else if (state.currentScreen is Screen.Setup) {
                        Screen.Home
                    } else {
                        state.currentScreen
                    }
                    state.copy(apiKey = apiKey, currentScreen = screen, errorMessage = null)
                }
                if (!apiKey.isNullOrBlank()) {
                    loadHomeContent(apiKey)
                    loadContinueWatching(apiKey, repository.watchHistoryIdsFlow().first())
                }
            }
        }

        viewModelScope.launch {
            repository.savedIdsFlow().collectLatest { savedIds ->
                _uiState.update { state ->
                    state.copy(
                        savedIds = savedIds,
                        hoursWatched = 280 + savedIds.size * 5,
                        completedAnimeCount = 30 + savedIds.size / 2
                    )
                }
                if (_uiState.value.currentScreen is Screen.Library) {
                    loadSavedMovies(_uiState.value.apiKey, savedIds)
                    loadSavedAnime(savedIds)
                }
            }
        }

        viewModelScope.launch {
            repository.watchHistoryIdsFlow().collectLatest { historyIds ->
                loadContinueWatching(_uiState.value.apiKey, historyIds)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateSearchMode(mode: SearchMode) {
        _uiState.update {
            it.copy(
                searchMode = mode,
                errorMessage = null,
                searchResults = if (mode == SearchMode.ANIME) emptyList() else it.searchResults,
                animeSearchResults = if (mode == SearchMode.ANIME) it.animeSearchResults else emptyList(),
            )
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            val mode = _uiState.value.searchMode
            val trimmedQuery = query.trim()
            val apiKey = _uiState.value.apiKey

            if (mode != SearchMode.ANIME && apiKey.isNullOrBlank()) {
                _uiState.update { it.copy(errorMessage = "TMDB token is required.") }
                return@launch
            }

            _uiState.update {
                it.copy(
                    searchQuery = query,
                    isSearching = true,
                    errorMessage = null,
                )
            }

            if (trimmedQuery.isBlank()) {
                _uiState.update {
                    it.copy(
                        searchResults = emptyList(),
                        animeSearchResults = emptyList(),
                        isSearching = false,
                    )
                }
                return@launch
            }

            try {
                when (mode) {
                    SearchMode.MOVIES -> {
                        val results = repository.searchMovies(apiKey!!, trimmedQuery)
                        _uiState.update {
                            it.copy(
                                searchResults = results,
                                animeSearchResults = emptyList(),
                                isSearching = false,
                            )
                        }
                    }
                    SearchMode.TV -> {
                        val results = repository.searchTv(apiKey!!, trimmedQuery)
                        _uiState.update {
                            it.copy(
                                searchResults = results,
                                animeSearchResults = emptyList(),
                                isSearching = false,
                            )
                        }
                    }
                    SearchMode.ANIME -> {
                        val results = repository.searchAnime(trimmedQuery)
                        _uiState.update {
                            it.copy(
                                animeSearchResults = results,
                                searchResults = emptyList(),
                                isSearching = false,
                            )
                        }
                    }
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        searchResults = emptyList(),
                        animeSearchResults = emptyList(),
                        isSearching = false,
                        errorMessage = "Search failed: ${exception.message ?: "network error"}",
                    )
                }
            }
        }
    }

    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            if (apiKey.isBlank()) {
                _uiState.update { it.copy(errorMessage = "Please enter a valid TMDB token.") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                repository.saveApiKey(apiKey)
                _uiState.update { it.copy(isLoading = false, currentScreen = Screen.Home, backStack = emptyList()) }
                loadHomeContent(apiKey)
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to save API key: ${exception.message ?: "unexpected error"}",
                    )
                }
            }
        }
    }

    private fun loadHomeContent(apiKey: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val content = coroutineScope {
                    val trendingMovies = async { runCatching { repository.fetchTrendingMovies(apiKey) }.getOrDefault(emptyList()) }
                    val trendingTv = async { runCatching { repository.fetchTrendingTv(apiKey) }.getOrDefault(emptyList()) }
                    val trendingAnime = async { runCatching { repository.fetchTrendingAnime() }.getOrDefault(emptyList()) }
                    val recentMovies = async { runCatching { repository.fetchRecentMovies(apiKey) }.getOrDefault(emptyList()) }
                    val recentTv = async { runCatching { repository.fetchRecentTv(apiKey) }.getOrDefault(emptyList()) }
                    val recentAnime = async { runCatching { repository.fetchRecentAnime() }.getOrDefault(emptyList()) }
                    val topMovies = async { runCatching { repository.fetchTopMovies(apiKey) }.getOrDefault(emptyList()) }
                    val topTv = async { runCatching { repository.fetchTopTv(apiKey) }.getOrDefault(emptyList()) }
                    val topAnime = async { runCatching { repository.fetchTopAnime() }.getOrDefault(emptyList()) }

                    HomeContent(
                        trendingMovies = trendingMovies.await(),
                        trendingTv = trendingTv.await(),
                        trendingAnime = trendingAnime.await(),
                        recentMovies = recentMovies.await(),
                        recentTv = recentTv.await(),
                        recentAnime = recentAnime.await(),
                        topMovies = topMovies.await(),
                        topTv = topTv.await(),
                        topAnime = topAnime.await(),
                    )
                }

                _uiState.update {
                    it.copy(
                        trendingMovies = content.trendingMovies,
                        trendingAll = content.trendingMovies + content.trendingTv,
                        trendingTv = content.trendingTv,
                        trendingAnime = content.trendingAnime,
                        recentMovies = content.recentMovies,
                        recentTv = content.recentTv,
                        recentAnime = content.recentAnime,
                        topMovies = content.topMovies,
                        topTv = content.topTv,
                        topAnime = content.topAnime,
                        isLoading = false,
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Unable to load home content: ${exception.message ?: "network error"}",
                    )
                }
            }
        }
    }

    fun openHome(addToBackStack: Boolean = true) {
        navigateTo(Screen.Home, addToBackStack)
    }

    fun openHomeBrowse(section: HomeSection) {
        navigateTo(Screen.HomeBrowse(section))
        _uiState.update { it.copy(homeBrowse = HomeBrowseUiState(section = section)) }
        loadNextHomeBrowsePage()
    }

    fun loadNextHomeBrowsePage() {
        val browse = _uiState.value.homeBrowse
        val section = browse.section ?: return
        if (browse.isLoading || browse.endReached) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(homeBrowse = it.homeBrowse.copy(isLoading = true, errorMessage = null))
            }

            try {
                val apiKey = _uiState.value.apiKey
                val page = _uiState.value.homeBrowse.nextPage

                if (!section.isAnime && apiKey.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            homeBrowse = it.homeBrowse.copy(
                                isLoading = false,
                                endReached = true,
                                errorMessage = "TMDB token is required.",
                            )
                        )
                    }
                    return@launch
                }

                if (section.isAnime) {
                    val newItems = fetchHomeBrowseAnime(section, page)
                    _uiState.update {
                        val merged = (it.homeBrowse.anime + newItems).distinctBy { anime -> anime.animeID }
                        it.copy(
                            homeBrowse = it.homeBrowse.copy(
                                anime = merged,
                                nextPage = page + 1,
                                isLoading = false,
                                endReached = newItems.isEmpty(),
                            )
                        )
                    }
                } else {
                    val newItems = fetchHomeBrowseMovies(section, apiKey!!, page)
                    _uiState.update {
                        val merged = (it.homeBrowse.movies + newItems).distinctBy { movie -> movie.tmdbID }
                        it.copy(
                            homeBrowse = it.homeBrowse.copy(
                                movies = merged,
                                nextPage = page + 1,
                                isLoading = false,
                                endReached = newItems.isEmpty(),
                            )
                        )
                    }
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        homeBrowse = it.homeBrowse.copy(
                            isLoading = false,
                            errorMessage = "Unable to load more: ${exception.message ?: "network error"}",
                        )
                    )
                }
            }
        }
    }

    private suspend fun fetchHomeBrowseMovies(section: HomeSection, apiKey: String, page: Int): List<TmdbMovie> =
        when (section) {
            HomeSection.TRENDING_MOVIES -> repository.fetchTrendingMovies(apiKey, page)
            HomeSection.TRENDING_TV -> repository.fetchTrendingTv(apiKey, page)
            HomeSection.RECENT_MOVIES -> repository.fetchRecentMovies(apiKey, page)
            HomeSection.RECENT_TV -> repository.fetchRecentTv(apiKey, page)
            HomeSection.TOP_MOVIES -> repository.fetchTopMovies(apiKey, page)
            HomeSection.TOP_TV -> repository.fetchTopTv(apiKey, page)
            else -> emptyList()
        }

    private suspend fun fetchHomeBrowseAnime(section: HomeSection, page: Int): List<MegaPlayShow> =
        when (section) {
            HomeSection.TRENDING_ANIME -> repository.fetchTrendingAnime(page)
            HomeSection.RECENT_ANIME -> repository.fetchRecentAnime(page)
            HomeSection.TOP_ANIME -> repository.fetchTopAnime(page)
            else -> emptyList()
        }

    private fun loadContinueWatching(apiKey: String?, historyIds: List<String>) {
        if (apiKey.isNullOrBlank() || historyIds.isEmpty()) {
            _uiState.update { it.copy(continueWatching = emptyList()) }
            return
        }

        viewModelScope.launch {
            val items = historyIds.take(12).mapNotNull { mediaKey ->
                try {
                    val id = mediaKey.substringAfter("_")
                    val isTv = mediaKey.startsWith("tv_")
                    val movie = if (isTv) {
                        repository.getTvShowDetails(apiKey, id).copy(mediaType = "tv")
                    } else {
                        repository.getMovieDetails(apiKey, id).copy(mediaType = "movie")
                    }
                    val (positionMs, season, episode) = repository.watchProgressFlow(id).first()
                    ContinueWatchingItem(
                        movie = movie,
                        progress = estimateWatchProgress(positionMs, isTv),
                        positionMs = positionMs,
                        season = season.takeIf { isTv },
                        episode = episode.takeIf { isTv },
                    )
                } catch (exception: Exception) {
                    null
                }
            }

            _uiState.update { it.copy(continueWatching = items) }
        }
    }

    private fun estimateWatchProgress(positionMs: Long, isTv: Boolean): Float {
        val estimatedDurationMs = if (isTv) 45 * 60 * 1000L else 120 * 60 * 1000L
        if (positionMs <= 0L) return 0.08f
        return (positionMs.toFloat() / estimatedDurationMs).coerceIn(0.08f, 0.95f)
    }

    fun openSearch() {
        navigateTo(Screen.Search)
    }

    fun openLibrary() {
        val ids = _uiState.value.savedIds
        navigateTo(Screen.Library)
        loadSavedMovies(_uiState.value.apiKey, ids)
        loadSavedAnime(ids) // Added this line
    }

    fun openDownloads() {
        navigateTo(Screen.Downloads)
    }

    fun openProfile() {
        navigateTo(Screen.Profile)
    }

    fun updateThemeMode(mode: String) {
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun updatePlaybackQuality(quality: String) {
        _uiState.update { it.copy(playbackQuality = quality) }
    }

    fun updateLanguagePreference(lang: String) {
        _uiState.update { it.copy(languagePreference = lang) }
    }

    fun toggleSubtitles() {
        _uiState.update { it.copy(subtitlesEnabled = !it.subtitlesEnabled) }
    }

    fun toggleNotifications() {
        _uiState.update { it.copy(notificationsEnabled = !it.notificationsEnabled) }
    }

    fun openDetails(movie: TmdbMovie) {
        viewModelScope.launch {
            val apiKey = _uiState.value.apiKey ?: return@launch
            val previousScreen = _uiState.value.currentScreen
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Fetch FULL details to get 'numberOfSeasons' and 'firstAirDate'
                val fullMovie = if (movie.isTv) {
                    repository.getTvShowDetails(apiKey, movie.tmdbID.toString())
                } else {
                    repository.getMovieDetails(apiKey, movie.tmdbID.toString())
                }
                
                _uiState.update {
                    val nextBackStack = if (previousScreen !is Screen.Setup) {
                        it.backStack + previousScreen
                    } else {
                        it.backStack
                    }

                    it.copy(
                        currentScreen = Screen.Details(fullMovie),
                        backStack = nextBackStack,
                        currentSeasonEpisodes = emptyList(),
                        isLoading = false,
                    )
                }
                
                if (fullMovie.isTv) loadSeason(fullMovie.tmdbID, 1)
                loadWatchProgress(fullMovie.tmdbID.toString())
                
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun openAnimeDetails(show: MegaPlayShow) {
        // Generate the list of episodes locally based on Jikan's total count
        val totalEpisodes = show.episodeCount ?: 1
        val generatedEpisodes = (1..totalEpisodes).map { 
            MegaPlayEpisode(number = it, episodeEmbedId = "") // Embed ID is empty because we use MAL ID
        }

        navigateTo(Screen.AnimeDetails(show))
        _uiState.update { state ->
            state.copy(animeEpisodes = generatedEpisodes, isLoading = false, errorMessage = null)
        }
    }

    fun playAnime(show: MegaPlayShow, episodeNumber: Int) {
        val language = _uiState.value.selectedTranslationType // "sub" or "dub"
        
        // MEGA-PLAY MAL ENDPOINT: /stream/mal/{mal-id}/{ep-num}/{language}
        // show.animeMalID is the MAL ID we got from Jikan
        val megaPlayUrl = "https://megaplay.buzz/stream/mal/${show.animeMalID}/$episodeNumber/$language"
        
        navigateTo(Screen.AnimePlayer(show, episodeNumber, megaPlayUrl))
    }
    
    
    fun loadSeason(tvId: Long, seasonNumber: Int) {
        viewModelScope.launch {
            try {
                val apiKey = _uiState.value.apiKey ?: return@launch
                val response = repository.fetchTvSeason(apiKey, tvId, seasonNumber)
                _uiState.update { it.copy(currentSeasonEpisodes = response.episodes) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }
    
    fun openPlayer(
        movie: TmdbMovie,
        source: PlaybackSource,
        tvSeason: Int? = null,
        tvEpisode: Int? = null,
        resumePositionMs: Long? = null,
    ) {
        navigateTo(Screen.Player(movie, source, tvSeason, tvEpisode, resumePositionMs), addToBackStack = false)
        _uiState.update {
            val current = it.currentScreen
            val shouldAddDetails = it.backStack.lastOrNull() !is Screen.Details
            val nextBackStack = if (shouldAddDetails) {
                it.backStack + Screen.Details(movie)
            } else {
                it.backStack
            }

            it.copy(
                currentScreen = current,
                backStack = nextBackStack,
                currentMovieWatchProgressMs = resumePositionMs,
            )
        }
    }
    
    fun updateAnimeTranslation(type: String) {
    _uiState.update { it.copy(selectedTranslationType = type) }
    }

    fun toggleSaved(prefixedId: String) {
        viewModelScope.launch {
            repository.toggleSaved(prefixedId)
        }
    }

    fun searchSubtitles(
        tmdbId: String,
        mediaType: String,
        season: Int? = null,
        episode: Int? = null,
        languages: String? = null,
        subdlApiKey: String? = null,
        wyzieApiKey: String? = null,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingSubtitles = true, subtitleErrorMessage = null) }
            try {
                val res = repository.searchSubtitles(tmdbId, mediaType, season, episode, languages, subdlApiKey, wyzieApiKey)
                if (res.ok) {
                    _uiState.update { it.copy(subtitleSearchResults = res.results, isSearchingSubtitles = false) }
                } else {
                    _uiState.update { it.copy(subtitleSearchResults = emptyList(), isSearchingSubtitles = false, subtitleErrorMessage = res.error) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(subtitleSearchResults = emptyList(), isSearchingSubtitles = false, subtitleErrorMessage = e.message) }
            }
        }
    }

    suspend fun fetchSubtitleFile(fileId: String): String? {
        return try {
            val (ok, path) = repository.getSubtitleUrl(fileId)
            if (ok) path else null
        } catch (e: Exception) {
            null
        }
    }

    private fun loadSavedMovies(apiKey: String?, savedIds: Set<String>) {

        if (apiKey.isNullOrBlank() || savedIds.isEmpty()) {
            _uiState.update {
                it.copy(
                    savedMovies = emptyList(),
                    isLoadingSaved = false
                )
            }
            return
        }

        viewModelScope.launch {

            _uiState.update {
                it.copy(isLoadingSaved = true, errorMessage = null)
            }

            try {

                val results = savedIds.mapNotNull { prefixedId ->
                    if (!prefixedId.startsWith("movie_") && !prefixedId.startsWith("tv_")) return@mapNotNull null
                    
                    val id = prefixedId.split("_")[1]
                        if (prefixedId.startsWith("tv_")) {
                            // Explicitly set mediaType to "tv"
                            repository.getTvShowDetails(apiKey, id).copy(mediaType = "tv")
                        } else {
                            // Explicitly set mediaType to "movie"
                            repository.getMovieDetails(apiKey, id).copy(mediaType = "movie")
                        }
        }
            _uiState.update { it.copy(savedMovies = results, isLoadingSaved = false) 
            
            }

            } catch (exception: Exception) {

                _uiState.update {
                    it.copy(
                        savedMovies = emptyList(),
                        isLoadingSaved = false,
                        errorMessage =
                            "Unable to load saved library: ${
                                exception.message ?: "network error"
                            }"
                    )
                }
            }
        }
    }

    
    private fun loadSavedAnime(savedIds: Set<String>) {

        val animeIds = savedIds
            .filter { it.startsWith("anime_") }
            .map { it.removePrefix("anime_") }

        if (animeIds.isEmpty()) {
            _uiState.update { it.copy(savedAnime = emptyList()) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSaved = true) }

            val animeList = animeIds.mapNotNull { id ->
                try {
                    repository.getAnimeById(id.toInt()) // Jikan call
                } catch (e: Exception) {
                    null
                }
            }

            _uiState.update {
                it.copy(
                    savedAnime = animeList,
                    isLoadingSaved = false
                )
            }
        }
    }    
    
    private fun loadWatchProgress(movieId: String) {
        viewModelScope.launch {
            // Collect the Triple (Position, Season, Episode)
            repository.watchProgressFlow(movieId).collectLatest { (pos, season, episode) ->
                _uiState.update { state ->
                    state.copy(
                        currentMovieWatchProgressMs = pos,
                        lastWatchedSeason = season,
                        lastWatchedEpisode = episode
                    )
                }
            }
        }
    }

    // This is called from the PlayerScreen when the user leaves
    fun savePlaybackProgress(movieId: String, positionMs: Long, season: Int? = null, episode: Int? = null) {
        viewModelScope.launch {
            repository.saveWatchProgress(movieId, positionMs, season, episode)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
