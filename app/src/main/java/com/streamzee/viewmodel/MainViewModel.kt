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
import com.streamzee.data.DownloadItem
import com.streamzee.data.DownloadMediaType
import com.streamzee.data.DownloadSettings
import com.streamzee.data.DownloadStorage
import com.streamzee.data.StreamDownloadManager
import com.streamzee.data.CapturedMediaStream
import com.streamzee.repository.StreamzeeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
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
    object Music : Screen
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
        val resumePositionMs: Long = 0L,
    ) : Screen
    data class OfflinePlayer(val downloadId: String) : Screen
    object Setup : Screen
}

data class CustomCollection(
    val name: String,
    val itemCount: Int,
    val imageUrl: String
)

data class HomeBrowseUiState(
    val section: HomeSection? = null,
    val movies: List<TmdbMovie> = emptyList(),
    val anime: List<MegaPlayShow> = emptyList(),
    val nextPage: Int = 1,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
)

data class ContinueWatchingItem(
    val movie: TmdbMovie? = null,
    val anime: MegaPlayShow? = null,
    val progress: Float,
    val positionMs: Long,
    val season: Int? = null,
    val episode: Int? = null,
) {
    val key: String
        get() = movie?.let { "${it.mediaType}_${it.tmdbID}" }
            ?: anime?.let { "anime_${it.animeID}" }
            ?: "unknown"
}

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
    val isRefreshingHome: Boolean = false,
    val isSearching: Boolean = false,
    val isLoadingSaved: Boolean = false,
    val isRefreshingLibrary: Boolean = false,
    val currentMovieWatchProgressMs: Long? = null,
    val currentAnimeWatchProgressMs: Long? = null,
    val lastWatchedAnimeEpisode: Int? = null,
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
    val themeMode: String = "System",
    val accentColor: String = "Purple",
    val playbackQuality: String = "Auto (Best)",
    val languagePreference: String = "English",
    val subtitlesEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val settingsMessage: String? = null,
    val hoursWatched: Int = 0,
    val completedAnimeCount: Int = 0,
    val customCollections: List<CustomCollection> = emptyList(),
    val downloadsQueue: List<DownloadItem> = emptyList(),
    val downloadSettings: DownloadSettings = DownloadSettings(),
    val downloadStorage: DownloadStorage = DownloadStorage(),
    val downloadsPaused: Boolean = false,
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
    private val downloadManager = StreamDownloadManager.get(application)
    private var detailsJob: Job? = null
    private var tokenJob: Job? = null
    private var searchJob: Job? = null
    private var libraryJob: Job? = null
    private var seasonJob: Job? = null
    private var continueWatchingJob: Job? = null
    private var movieWatchProgressJob: Job? = null
    private var animeWatchProgressJob: Job? = null

    private val _uiState = MutableStateFlow(MainUiState(currentScreen =
        if (application.getSharedPreferences("content_mode", android.content.Context.MODE_PRIVATE).getBoolean("music", false)) Screen.Music else Screen.Setup))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private fun navigateTo(screen: Screen, addToBackStack: Boolean = true) {
        detailsJob?.cancel()
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
                isLoading = false,
                backStack = nextBackStack,
                errorMessage = null,
                currentMovieWatchProgressMs = null,
                currentAnimeWatchProgressMs = null,
            )
        }
    }

    fun navigateBack(): Boolean {
        detailsJob?.cancel()
        _uiState.update { it.copy(isLoading = false) }
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
            loadLibrary()
        }

        if (previous is Screen.Details) {
            if (previous.movie.isTv) {
                loadSeason(previous.movie.tmdbID, _uiState.value.lastWatchedSeason ?: 1)
            }
            loadWatchProgress("${if (previous.movie.isTv) "tv" else "movie"}_${previous.movie.tmdbID}")
        }

        if (previous is Screen.AnimeDetails) {
            loadAnimeWatchProgress(previous.show.animeID)
        }

        if (previous is Screen.Home) {
            refreshContinueWatchingFromHistory()
        }

        return true
    }

    init {
        viewModelScope.launch {
            repository.apiKeyFlow().distinctUntilChanged().collectLatest { apiKey ->
                _uiState.update { state ->
                    val screen = if (apiKey.isNullOrBlank()) {
                        if (state.currentScreen is Screen.Music) Screen.Music else Screen.Setup
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
            repository.savedIdsFlow().distinctUntilChanged().collectLatest { savedIds ->
                _uiState.update { state ->
                    state.copy(
                        savedIds = savedIds
                    )
                }
                if (_uiState.value.currentScreen is Screen.Library) {
                    loadLibrary()
                }
            }
        }

        viewModelScope.launch {
            repository.watchHistoryIdsFlow().distinctUntilChanged().collectLatest { historyIds ->
                loadContinueWatching(_uiState.value.apiKey, historyIds)
            }
        }

        viewModelScope.launch {
            repository.appPreferencesFlow().collectLatest { preferences ->
                _uiState.update {
                    it.copy(
                        themeMode = preferences.themeMode,
                        accentColor = preferences.accentColor,
                        playbackQuality = preferences.playbackQuality,
                        languagePreference = preferences.language,
                        subtitlesEnabled = preferences.subtitlesEnabled,
                        notificationsEnabled = preferences.notificationsEnabled,
                        reducedMotion = preferences.reducedMotion,
                    )
                }
            }
        }

        viewModelScope.launch {
            downloadManager.downloads.collectLatest { downloads ->
                _uiState.update { it.copy(downloadsQueue = downloads) }
            }
        }

        viewModelScope.launch {
            downloadManager.settings.collectLatest { settings ->
                _uiState.update { it.copy(downloadSettings = settings) }
            }
        }

        viewModelScope.launch {
            downloadManager.storage.collectLatest { storage ->
                _uiState.update { it.copy(downloadStorage = storage) }
            }
        }

        viewModelScope.launch {
            downloadManager.downloadsPaused.collectLatest { paused ->
                _uiState.update { it.copy(downloadsPaused = paused) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = query, searchResults = emptyList(),
            animeSearchResults = emptyList(), isSearching = false, errorMessage = null) }
        searchJob = viewModelScope.launch {
            delay(400)
            search(query)
        }
    }

    fun updateSearchMode(mode: SearchMode) {
        if (mode == _uiState.value.searchMode) return
        searchJob?.cancel()
        _uiState.update { it.copy(searchMode = mode, searchResults = emptyList(),
            animeSearchResults = emptyList(), isSearching = false, errorMessage = null) }
        search(_uiState.value.searchQuery)
    }

    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
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
                if (exception is CancellationException) throw exception
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
        tokenJob?.cancel()
        tokenJob = viewModelScope.launch {
            if (apiKey.isBlank()) {
                _uiState.update { it.copy(errorMessage = "Please enter a valid TMDB token.") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                repository.fetchTrendingMovies(apiKey.trim())
                repository.saveApiKey(apiKey.trim())
                _uiState.update { it.copy(isLoading = false, currentScreen = Screen.Home, backStack = emptyList()) }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Unable to verify or save TMDB token: ${exception.message ?: "unexpected error"}",
                    )
                }
            }
        }
    }

    private fun loadHomeContent(apiKey: String, isRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh,
                    isRefreshingHome = isRefresh,
                    errorMessage = null,
                )
            }
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
                        isRefreshingHome = false,
                    )
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshingHome = false,
                        errorMessage = "Unable to load home content: ${exception.message ?: "network error"}",
                    )
                }
            }
        }
    }

    fun refreshHome() {
        val apiKey = _uiState.value.apiKey
        if (apiKey.isNullOrBlank() || _uiState.value.isRefreshingHome) return

        loadHomeContent(apiKey, isRefresh = true)
        viewModelScope.launch {
            loadContinueWatching(apiKey, repository.watchHistoryIdsFlow().first())
        }
    }

    fun openHome(addToBackStack: Boolean = true) {
        navigateTo(Screen.Home, addToBackStack)
        refreshContinueWatchingFromHistory()
    }

    private fun refreshContinueWatchingFromHistory() {
        viewModelScope.launch {
            loadContinueWatching(
                _uiState.value.apiKey,
                repository.watchHistoryIdsFlow().first(),
            )
        }
    }

    fun openHomeBrowse(section: HomeSection) {
        navigateTo(Screen.HomeBrowse(section))
        _uiState.update { it.copy(homeBrowse = HomeBrowseUiState(section = section)) }
        loadNextHomeBrowsePage()
    }

    fun loadNextHomeBrowsePage() {
        if (_uiState.value.homeBrowse.isRefreshing) return
        loadHomeBrowsePage(isRefresh = false)
    }

    fun refreshHomeBrowse() {
        val section = _uiState.value.homeBrowse.section ?: return
        if (_uiState.value.homeBrowse.isRefreshing) return

        _uiState.update {
            it.copy(
                homeBrowse = it.homeBrowse.copy(
                    nextPage = 1,
                    isRefreshing = true,
                    endReached = false,
                    errorMessage = null,
                )
            )
        }
        loadHomeBrowsePage(isRefresh = true)
    }

    private fun loadHomeBrowsePage(isRefresh: Boolean) {
        val browse = _uiState.value.homeBrowse
        val section = browse.section ?: return
        if (browse.isLoading || (!isRefresh && browse.endReached)) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    homeBrowse = it.homeBrowse.copy(
                        isLoading = !isRefresh,
                        isRefreshing = isRefresh,
                        errorMessage = null,
                    )
                )
            }

            try {
                val apiKey = _uiState.value.apiKey
                val page = _uiState.value.homeBrowse.nextPage

                if (!section.isAnime && apiKey.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            homeBrowse = it.homeBrowse.copy(
                                isLoading = false,
                                isRefreshing = false,
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
                        val merged = if (isRefresh) {
                            newItems.distinctBy { anime -> anime.animeID }
                        } else {
                            (it.homeBrowse.anime + newItems).distinctBy { anime -> anime.animeID }
                        }
                        it.copy(
                            homeBrowse = it.homeBrowse.copy(
                                anime = merged,
                                nextPage = page + 1,
                                isLoading = false,
                                isRefreshing = false,
                                endReached = newItems.isEmpty(),
                            )
                        )
                    }
                } else {
                    val newItems = fetchHomeBrowseMovies(section, apiKey!!, page)
                    _uiState.update {
                        val merged = if (isRefresh) {
                            newItems.distinctBy { movie -> movie.tmdbID }
                        } else {
                            (it.homeBrowse.movies + newItems).distinctBy { movie -> movie.tmdbID }
                        }
                        it.copy(
                            homeBrowse = it.homeBrowse.copy(
                                movies = merged,
                                nextPage = page + 1,
                                isLoading = false,
                                isRefreshing = false,
                                endReached = newItems.isEmpty(),
                            )
                        )
                    }
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                _uiState.update {
                    it.copy(
                        homeBrowse = it.homeBrowse.copy(
                            isLoading = false,
                            isRefreshing = false,
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
        if (historyIds.isEmpty()) {
            continueWatchingJob?.cancel()
            _uiState.update { it.copy(continueWatching = emptyList()) }
            return
        }

        continueWatchingJob?.cancel()
        continueWatchingJob = viewModelScope.launch {
            val existingItems = _uiState.value.continueWatching.associateBy { it.key }
            val items = historyIds.take(12).mapNotNull { mediaKey ->
                try {
                    val id = mediaKey.substringAfter("_")
                    if (mediaKey.startsWith("anime_")) {
                        val (positionMs, _, episode) = repository.watchProgressFlow(mediaKey).first()
                        val anime = try {
                            repository.getAnimeShowById(id.toInt())
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            existingItems[mediaKey]?.anime
                                ?: findLoadedAnime(id)
                                ?: return@mapNotNull null
                        }

                        return@mapNotNull ContinueWatchingItem(
                            anime = anime,
                            progress = estimateAnimeWatchProgress(positionMs),
                            positionMs = positionMs,
                            episode = episode,
                        )
                    }

                    if (apiKey.isNullOrBlank()) return@mapNotNull null
                    val isTv = mediaKey.startsWith("tv_")
                    val movie = if (isTv) {
                        repository.getTvShowDetails(apiKey, id).copy(mediaType = "tv")
                    } else {
                        repository.getMovieDetails(apiKey, id).copy(mediaType = "movie")
                    }
                    val (positionMs, season, episode) = repository.watchProgressFlow(mediaKey).first()
                    ContinueWatchingItem(
                        movie = movie,
                        progress = estimateWatchProgress(positionMs, isTv),
                        positionMs = positionMs,
                        season = season.takeIf { isTv },
                        episode = episode.takeIf { isTv },
                    )
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    existingItems[mediaKey]
                }
            }

            _uiState.update { it.copy(continueWatching = items) }
        }
    }

    private fun findLoadedAnime(animeId: String): MegaPlayShow? {
        val state = _uiState.value
        return (
            state.trendingAnime +
                state.recentAnime +
                state.topAnime +
                state.animeSearchResults +
                state.homeBrowse.anime
            ).firstOrNull { it.animeID == animeId }
    }

    private fun addAnimeToContinueWatching(
        show: MegaPlayShow,
        episodeNumber: Int,
        positionMs: Long,
    ) {
        val item = ContinueWatchingItem(
            anime = show,
            progress = estimateAnimeWatchProgress(positionMs),
            positionMs = positionMs,
            episode = episodeNumber,
        )

        _uiState.update {
            it.copy(
                continueWatching = (
                    listOf(item) + it.continueWatching.filterNot { existing -> existing.key == item.key }
                    ).take(12)
            )
        }
    }

    private fun estimateWatchProgress(positionMs: Long, isTv: Boolean): Float {
        val estimatedDurationMs = if (isTv) 45 * 60 * 1000L else 120 * 60 * 1000L
        if (positionMs <= 0L) return 0.08f
        return (positionMs.toFloat() / estimatedDurationMs).coerceIn(0.08f, 0.95f)
    }

    private fun estimateAnimeWatchProgress(positionMs: Long): Float {
        val estimatedDurationMs = 24 * 60 * 1000L
        if (positionMs <= 0L) return 0.08f
        return (positionMs.toFloat() / estimatedDurationMs).coerceIn(0.08f, 0.95f)
    }

    fun openSearch() {
        navigateTo(Screen.Search)
    }

    fun openLibrary() {
        navigateTo(Screen.Library)
        loadLibrary()
    }

    fun refreshLibrary() = loadLibrary(isRefresh = true)

    private fun loadLibrary(isRefresh: Boolean = false) {
        libraryJob?.cancel()
        libraryJob = viewModelScope.launch {
            val state = _uiState.value
            val ids = state.savedIds
            _uiState.update { it.copy(isLoadingSaved = true,
                isRefreshingLibrary = isRefresh, errorMessage = null) }
            var failures = 0
            val movies = mutableListOf<TmdbMovie>()
            val anime = mutableListOf<JikanAnime>()
            for (key in ids) {
                try {
                    when {
                        key.startsWith("movie_") -> movies += repository.getMovieDetails(
                            requireNotNull(state.apiKey), key.removePrefix("movie_"))
                        key.startsWith("tv_") -> movies += repository.getTvShowDetails(
                            requireNotNull(state.apiKey), key.removePrefix("tv_"))
                        key.startsWith("anime_") -> anime += repository.getAnimeById(
                            key.removePrefix("anime_").toInt())
                    }
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    failures++
                    state.savedMovies.firstOrNull {
                        "${if (it.isTv) "tv" else "movie"}_${it.tmdbID}" == key
                    }?.let { movies += it }
                    state.savedAnime.firstOrNull { "anime_${it.malId}" == key }
                        ?.let { anime += it }
                }
            }
            _uiState.update { it.copy(savedMovies = movies, savedAnime = anime,
                isLoadingSaved = false, isRefreshingLibrary = false,
                errorMessage = if (failures > 0) "Unable to refresh $failures saved titles. Pull to refresh to retry." else null) }
        }
    }

    fun openDownloads() {
        navigateTo(Screen.Downloads)
    }

    fun queueMovieDownload(movie: TmdbMovie, stream: CapturedMediaStream) {
        val id = "movie_${movie.tmdbID}"
        downloadManager.queue(
            id = id,
            title = movie.displayTitle,
            subtitle = "Movie",
            imageUrl = movie.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
            mediaType = DownloadMediaType.MOVIE,
            pageUrl = stream.pageUrl,
        )
        downloadManager.markResolved(id, stream.url, stream.mimeType, stream.requestHeaders)
    }

    fun queueTvEpisodeDownload(
        movie: TmdbMovie,
        season: Int,
        episode: Int,
        stream: CapturedMediaStream,
    ) {
        val id = "tv_${movie.tmdbID}_s${season}_e${episode}"
        downloadManager.queue(
            id = id,
            title = movie.displayTitle,
            subtitle = "S$season E$episode",
            imageUrl = movie.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
            mediaType = DownloadMediaType.TV_EPISODE,
            pageUrl = stream.pageUrl,
        )
        downloadManager.markResolved(id, stream.url, stream.mimeType, stream.requestHeaders)
    }

    fun queueAnimeEpisodeDownload(
        show: MegaPlayShow,
        episode: Int,
        stream: CapturedMediaStream,
    ) {
        val language = _uiState.value.selectedTranslationType
        val id = "anime_${show.animeID}_e${episode}_$language"
        downloadManager.queue(
            id = id,
            title = show.name,
            subtitle = "Episode $episode - ${language.uppercase()}",
            imageUrl = show.thumbnail,
            mediaType = DownloadMediaType.ANIME_EPISODE,
            pageUrl = stream.pageUrl,
        )
        downloadManager.markResolved(id, stream.url, stream.mimeType, stream.requestHeaders)
    }

    fun pauseDownload(id: String) = downloadManager.pause(id)

    fun resumeDownload(id: String) = downloadManager.resume(id)

    fun retryDownload(id: String) = downloadManager.retry(id)

    fun removeDownload(id: String) = downloadManager.remove(id)

    fun pauseAllDownloads() = downloadManager.pauseAll()

    fun resumeAllDownloads() = downloadManager.resumeAll()

    fun updateDownloadSettings(settings: DownloadSettings) {
        downloadManager.updateSettings(settings)
    }

    fun playOfflineDownload(id: String) {
        navigateTo(Screen.OfflinePlayer(id))
    }

    fun openMusic() = selectContentMode(true)

    fun selectContentMode(music: Boolean) {
        getApplication<Application>().getSharedPreferences("content_mode", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("music", music).apply()
        detailsJob?.cancel()
        _uiState.update { it.copy(
            currentScreen = if (music) Screen.Music else if (it.apiKey.isNullOrBlank()) Screen.Setup else Screen.Home,
            backStack = emptyList(), isLoading = false, errorMessage = null,
        ) }
    }

    fun openProfile() {
        navigateTo(Screen.Profile)
    }

    fun updateThemeMode(mode: String) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch { repository.saveThemeMode(mode) }
    }

    fun updateAccentColor(color: String) {
        _uiState.update { it.copy(accentColor = color) }
        viewModelScope.launch { repository.saveAccentColor(color) }
    }

    fun updatePlaybackQuality(quality: String) {
        _uiState.update { it.copy(playbackQuality = quality) }
        viewModelScope.launch { repository.savePlaybackQuality(quality) }
    }

    fun updateLanguagePreference(lang: String) {
        _uiState.update { it.copy(languagePreference = lang) }
        viewModelScope.launch { repository.saveLanguage(lang) }
    }

    fun toggleSubtitles() {
        val enabled = !_uiState.value.subtitlesEnabled
        _uiState.update { it.copy(subtitlesEnabled = enabled) }
        viewModelScope.launch { repository.saveSubtitlesEnabled(enabled) }
    }

    fun toggleNotifications() {
        val enabled = !_uiState.value.notificationsEnabled
        _uiState.update { it.copy(notificationsEnabled = enabled) }
        viewModelScope.launch { repository.saveNotificationsEnabled(enabled) }
    }

    fun toggleReducedMotion() {
        val enabled = !_uiState.value.reducedMotion
        _uiState.update { it.copy(reducedMotion = enabled) }
        viewModelScope.launch { repository.saveReducedMotion(enabled) }
    }

    fun updateApiKeyFromProfile(apiKey: String) {
        if (apiKey.isBlank()) {
            _uiState.update { it.copy(settingsMessage = "TMDB token cannot be empty.") }
            return
        }

        tokenJob?.cancel()
        tokenJob = viewModelScope.launch {
            try {
                repository.fetchTrendingMovies(apiKey.trim())
                repository.saveApiKey(apiKey.trim())
                _uiState.update { it.copy(settingsMessage = "TMDB token updated.") }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                _uiState.update { it.copy(settingsMessage = "Unable to verify TMDB token. Check the token and your connection.") }
            }
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            try {
                repository.clearWatchHistory()
                _uiState.update { it.copy(continueWatching = emptyList(),
                    settingsMessage = "Watch history cleared.") }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                _uiState.update { it.copy(settingsMessage = "Unable to clear watch history.") }
            }
        }
    }

    fun clearAppCache() {
        viewModelScope.launch {
            val cleared = repository.clearCache()
            _uiState.update {
                it.copy(
                    settingsMessage = if (cleared) {
                        "Cache cleared."
                    } else {
                        "Some cached files could not be removed."
                    }
                )
            }
        }
    }

    fun clearSettingsMessage() {
        _uiState.update { it.copy(settingsMessage = null) }
    }

    fun openDetails(movie: TmdbMovie) {
        movieWatchProgressJob?.cancel()
        animeWatchProgressJob?.cancel()
        seasonJob?.cancel()
        detailsJob?.cancel()
        detailsJob = viewModelScope.launch {
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
                        currentMovieWatchProgressMs = null,
                        lastWatchedSeason = 1,
                        lastWatchedEpisode = 1,
                        isLoading = false,
                    )
                }
                
                if (fullMovie.isTv) loadSeason(fullMovie.tmdbID, 1)
                loadWatchProgress("${if (fullMovie.isTv) "tv" else "movie"}_${fullMovie.tmdbID}")
                
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
            state.copy(
                animeEpisodes = generatedEpisodes,
                currentAnimeWatchProgressMs = null,
                lastWatchedAnimeEpisode = 1,
                isLoading = false,
                errorMessage = null,
            )
        }
        loadAnimeWatchProgress(show.animeID)
    }

    fun playAnime(
        show: MegaPlayShow,
        episodeNumber: Int,
        resumePositionMs: Long = 0L,
    ) {
        val language = _uiState.value.selectedTranslationType // "sub" or "dub"
        
        // MEGA-PLAY MAL ENDPOINT: /stream/mal/{mal-id}/{ep-num}/{language}
        // show.animeMalID is the MAL ID we got from Jikan
        val megaPlayUrl = "https://megaplay.buzz/stream/mal/${show.animeMalID}/$episodeNumber/$language"

        addAnimeToContinueWatching(show, episodeNumber, resumePositionMs)
        viewModelScope.launch {
            repository.saveAnimeWatchProgress(show.animeID, episodeNumber, resumePositionMs)
        }

        navigateTo(
            Screen.AnimePlayer(
                show = show,
                episode = episodeNumber,
                streamUrl = megaPlayUrl,
                translationType = language,
                resumePositionMs = resumePositionMs,
            )
        )
    }
    
    
    fun loadSeason(tvId: Long, seasonNumber: Int) {
        seasonJob?.cancel()
        _uiState.update { it.copy(currentSeasonEpisodes = emptyList()) }
        seasonJob = viewModelScope.launch {
            try {
                val apiKey = _uiState.value.apiKey ?: return@launch
                val response = repository.fetchTvSeason(apiKey, tvId, seasonNumber)
                _uiState.update { it.copy(currentSeasonEpisodes = response.episodes) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
                if (e is CancellationException) throw e
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

    private fun loadWatchProgress(movieId: String) {
        movieWatchProgressJob?.cancel()
        movieWatchProgressJob = viewModelScope.launch {
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

    private fun loadAnimeWatchProgress(animeId: String) {
        animeWatchProgressJob?.cancel()
        animeWatchProgressJob = viewModelScope.launch {
            repository.watchProgressFlow("anime_$animeId").collectLatest { (pos, _, episode) ->
                _uiState.update { state ->
                    state.copy(
                        currentAnimeWatchProgressMs = pos,
                        lastWatchedAnimeEpisode = episode,
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

    fun saveAnimePlaybackProgress(animeId: String, episode: Int, positionMs: Long) {
        viewModelScope.launch {
            repository.saveAnimeWatchProgress(animeId, episode, positionMs)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
