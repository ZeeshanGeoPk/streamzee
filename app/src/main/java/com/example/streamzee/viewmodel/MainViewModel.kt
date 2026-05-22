package com.example.streamzee.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.streamzee.data.NetworkClient
import com.example.streamzee.data.TmdbMovie
import com.example.streamzee.repository.StreamzeeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Screen {
    object Home : Screen
    object Search : Screen
    data class Details(val movie: TmdbMovie) : Screen
    object Setup : Screen
}

data class MainUiState(
    val apiKey: String? = null,
    val currentScreen: Screen = Screen.Setup,
    val trendingMovies: List<TmdbMovie> = emptyList(),
    val savedIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val searchResults: List<TmdbMovie> = emptyList(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
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
                _uiState.update { it.copy(savedIds = savedIds) }
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

    fun searchMovies(query: String) {
        viewModelScope.launch {
            val apiKey = _uiState.value.apiKey
            if (apiKey.isNullOrBlank()) {
                _uiState.update { it.copy(errorMessage = "TMDB token is required.") }
                return@launch
            }
            val trimmedQuery = query.trim()
            _uiState.update { it.copy(searchQuery = trimmedQuery, isSearching = true, errorMessage = null) }
            if (trimmedQuery.isBlank()) {
                _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
                return@launch
            }
            try {
                val results = repository.searchMovies(apiKey, trimmedQuery)
                _uiState.update { it.copy(searchResults = results, isSearching = false) }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        searchResults = emptyList(),
                        isSearching = false,
                        errorMessage = "Search failed: ${exception.message ?: "network error"}",
                    )
                }
            }
        }
    }

    fun openHome() {
        _uiState.update { it.copy(currentScreen = Screen.Home, errorMessage = null) }
    }

    fun openSearch() {
        _uiState.update { it.copy(currentScreen = Screen.Search, errorMessage = null) }
    }

    fun openDetails(movie: TmdbMovie) {
        _uiState.update { it.copy(currentScreen = Screen.Details(movie)) }
    }

    fun toggleSaved(movieId: String) {
        viewModelScope.launch {
            repository.toggleSaved(movieId)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
