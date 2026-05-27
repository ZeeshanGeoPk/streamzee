package com.streamzee.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamzee.data.NetworkClient
import com.streamzee.data.PlaybackSource
import com.streamzee.data.TmdbMovie
import com.streamzee.data.TmdbEpisode
import com.streamzee.data.AnikotoShow
import com.streamzee.data.AnikotoEpisode
import com.streamzee.repository.StreamzeeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

//val movies = repository.fetchTrending(apiKey)

enum class SearchMode {
    MOVIES,
    TV,
    ANIME,
}

sealed interface Screen {
    object Home : Screen
    object Search : Screen
    object Library : Screen
    object Downloads : Screen
    object Profile : Screen
    data class Details(val movie: TmdbMovie) : Screen
    data class Player(
        val movie: TmdbMovie,
        val source: PlaybackSource,
        val tvSeason: Int? = null,
        val tvEpisode: Int? = null,
        val resumePositionMs: Long? = null,
    ) : Screen
    data class AnimeDetails(val show: AnikotoShow) : Screen
    data class AnimePlayer(
        val show: AnikotoShow,
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

data class MainUiState(
    val apiKey: String? = null,
    val lastWatchedSeason: Int? = null,
    val lastWatchedEpisode: Int? = null,
    val currentSeasonEpisodes: List<TmdbEpisode> = emptyList(),
    val currentScreen: Screen = Screen.Setup,
    val trendingMovies: List<TmdbMovie> = emptyList(),
    val savedIds: Set<String> = emptySet(),
    val savedMovies: List<TmdbMovie> = emptyList(),
    val searchMode: SearchMode = SearchMode.MOVIES,
    val searchQuery: String = "",
    val searchResults: List<TmdbMovie> = emptyList(),
    val animeSearchResults: List<AnikotoShow> = emptyList(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val isLoadingSaved: Boolean = false,
    val currentMovieWatchProgressMs: Long? = null,
    val subtitleSearchResults: List<com.streamzee.data.SubtitleItem> = emptyList(),
    val isSearchingSubtitles: Boolean = false,
    val subtitleErrorMessage: String? = null,
    val errorMessage: String? = null,
    
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
    val animeEpisodes: List<AnikotoEpisode> = emptyList() // Added
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StreamzeeRepository(NetworkClient.tmdbApi, application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

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
                    loadTrending(apiKey)
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
                }
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
                _uiState.update { it.copy(isLoading = false, currentScreen = Screen.Home) }
                loadTrending(apiKey)
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

    private fun loadTrending(apiKey: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val movies = repository.fetchTrending(apiKey)
                _uiState.update { it.copy(trendingMovies = movies, isLoading = false) }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Unable to load trending movies: ${exception.message ?: "network error"}",
                    )
                }
            }
        }
    }

    fun openHome() {
        _uiState.update { it.copy(currentScreen = Screen.Home, errorMessage = null, currentMovieWatchProgressMs = null) }
    }

    fun openSearch() {
        _uiState.update { it.copy(currentScreen = Screen.Search, errorMessage = null, currentMovieWatchProgressMs = null) }
    }

    fun openLibrary() {
        _uiState.update { it.copy(currentScreen = Screen.Library, errorMessage = null, currentMovieWatchProgressMs = null) }
        loadSavedMovies(_uiState.value.apiKey, _uiState.value.savedIds)
    }

    fun openDownloads() {
        _uiState.update { it.copy(currentScreen = Screen.Downloads, errorMessage = null, currentMovieWatchProgressMs = null) }
    }

    fun openProfile() {
        _uiState.update { it.copy(currentScreen = Screen.Profile, errorMessage = null, currentMovieWatchProgressMs = null) }
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
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Fetch FULL details to get 'numberOfSeasons' and 'firstAirDate'
                val fullMovie = if (movie.isTv) {
                    repository.getTvShowDetails(apiKey, movie.tmdbID.toString())
                } else {
                    repository.getMovieDetails(apiKey, movie.tmdbID.toString())
                }
                
                _uiState.update { it.copy(
                    currentScreen = Screen.Details(fullMovie), 
                    currentSeasonEpisodes = emptyList(),
                    isLoading = false 
                )}
                
                if (fullMovie.isTv) loadSeason(fullMovie.tmdbID, 1)
                loadWatchProgress(fullMovie.tmdbID.toString())
                
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun openAnimeDetails(show: AnikotoShow) {
        // Generate the list of episodes locally based on Jikan's total count
        val totalEpisodes = show.episodeCount ?: 1
        val generatedEpisodes = (1..totalEpisodes).map { 
            AnikotoEpisode(number = it, episodeEmbedId = "") // Embed ID is empty because we use MAL ID
        }

        _uiState.update { state ->
            state.copy(
                currentScreen = Screen.AnimeDetails(show),
                animeEpisodes = generatedEpisodes,
                isLoading = false,
                errorMessage = null
            )
        }
    }

    fun playAnime(show: AnikotoShow, episodeNumber: Int) {
        val language = _uiState.value.selectedTranslationType // "sub" or "dub"
        
        // MEGA-PLAY MAL ENDPOINT: /stream/mal/{mal-id}/{ep-num}/{language}
        // show.animeMalID is the MAL ID we got from Jikan
        val megaPlayUrl = "https://megaplay.buzz/stream/mal/${show.animeMalID}/$episodeNumber/$language"
        
        _uiState.update { it.copy(
            currentScreen = Screen.AnimePlayer(show, episodeNumber, megaPlayUrl),
            errorMessage = null
        )}
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
        _uiState.update { it.copy(currentScreen = Screen.Player(movie, source, tvSeason, tvEpisode, resumePositionMs), errorMessage = null) }
    }
    
    fun updateAnimeTranslation(type: String) {
    _uiState.update { it.copy(selectedTranslationType = type) }
    }

    fun toggleSaved(movieId: String) {
        viewModelScope.launch {
            repository.toggleSaved(movieId)
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
            _uiState.update { it.copy(savedMovies = emptyList(), isLoadingSaved = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSaved = true, errorMessage = null) }
            try {
                val movies = repository.fetchSavedMovies(apiKey, savedIds)
                _uiState.update { it.copy(savedMovies = movies, isLoadingSaved = false) }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        savedMovies = emptyList(),
                        isLoadingSaved = false,
                        errorMessage = "Unable to load saved library: ${exception.message ?: "network error"}",
                    )
                }
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
