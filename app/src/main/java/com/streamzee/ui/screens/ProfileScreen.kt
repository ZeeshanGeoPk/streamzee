package com.streamzee.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamzee.ui.theme.accentColor
import com.streamzee.viewmodel.MainUiState

private val accentNames = listOf(
    "Purple",
    "Blue",
    "Green",
    "Teal",
    "Orange",
    "Red",
    "Pink",
    "Indigo",
)

private val themeModes = listOf("Light", "Dark", "Lite Dark", "System")

@Composable
fun profileScreen(
    uiState: MainUiState,
    updateTheme: (String) -> Unit,
    updateAccent: (String) -> Unit,
    updateApiKey: (String) -> Unit,
    updateQuality: (String) -> Unit,
    updateLanguage: (String) -> Unit,
    toggleSubtitles: () -> Unit,
    toggleNotifications: () -> Unit,
    toggleReducedMotion: () -> Unit,
    clearCache: () -> Unit,
    clearHistory: () -> Unit,
    clearMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.settingsMessage) {
        uiState.settingsMessage?.let {
            snackbarHostState.showSnackbar(it)
            clearMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Profile",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Playback, appearance and app preferences",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsSection(title = "Appearance") {
                    SettingsLabel(
                        icon = Icons.Default.ColorLens,
                        title = "Accent",
                        subtitle = uiState.accentColor,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        accentNames.forEach { name ->
                            AccentSwatch(
                                name = name,
                                selected = uiState.accentColor == name,
                                onClick = { updateAccent(name) },
                            )
                        }
                    }
                    SettingsDivider()
                    SettingsLabel(
                        icon = Icons.Default.DarkMode,
                        title = "Theme",
                        subtitle = uiState.themeMode,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        themeModes.chunked(2).forEach { rowModes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowModes.forEach { mode ->
                                    FilterChip(
                                        selected = uiState.themeMode == mode,
                                        onClick = { updateTheme(mode) },
                                        label = {
                                            Text(
                                                text = mode,
                                                modifier = Modifier.fillMaxWidth(),
                                                maxLines = 1,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                    SettingsDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.AutoAwesome,
                        title = "Reduced motion",
                        subtitle = "Limit carousel and interface animations",
                        checked = uiState.reducedMotion,
                        onCheckedChange = { toggleReducedMotion() },
                    )
                }
            }

            item {
                SettingsSection(title = "Playback") {
                    ClickableSettingsRow(
                        icon = Icons.Default.HighQuality,
                        title = "Preferred quality",
                        value = uiState.playbackQuality,
                        onClick = { showQualityDialog = true },
                    )
                    SettingsDivider()
                    ClickableSettingsRow(
                        icon = Icons.Default.Language,
                        title = "Content language",
                        value = uiState.languagePreference,
                        onClick = { showLanguageDialog = true },
                    )
                    SettingsDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = "Subtitles",
                        subtitle = "Enable subtitles by default",
                        checked = uiState.subtitlesEnabled,
                        onCheckedChange = { toggleSubtitles() },
                    )
                    SettingsDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        subtitle = "New releases and watchlist updates",
                        checked = uiState.notificationsEnabled,
                        onCheckedChange = { toggleNotifications() },
                    )
                }
            }

            item {
                SettingsSection(title = "Data") {
                    ClickableSettingsRow(
                        icon = Icons.Default.CleaningServices,
                        title = "Clear watch history",
                        value = "Remove playback progress and Continue Watching",
                        onClick = { showClearHistoryDialog = true },
                    )
                    SettingsDivider()
                    ClickableSettingsRow(
                        icon = Icons.Default.Key,
                        title = "TMDB API token",
                        value = maskApiKey(uiState.apiKey),
                        onClick = { showApiKeyDialog = true },
                    )
                    SettingsDivider()
                    ClickableSettingsRow(
                        icon = Icons.Default.CleaningServices,
                        title = "Clear cache",
                        value = "Images and temporary files",
                        danger = true,
                        onClick = { showClearCacheDialog = true },
                    )
                }
            }

            item {
                Text(
                    text = "Streamzee 1.0.0 beta 2",
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            currentKey = uiState.apiKey.orEmpty(),
            onDismiss = { showApiKeyDialog = false },
            onSave = {
                updateApiKey(it)
                showApiKeyDialog = false
            },
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear watch history?") },
            text = { Text("Playback progress and Continue Watching will be removed. Your watchlist and downloads will stay.") },
            confirmButton = {
                TextButton(onClick = {
                    clearHistory()
                    showClearHistoryDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear app cache?") },
            text = {
                Text("Downloaded images and temporary files will be removed. Your watchlist, history and settings will stay.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearCache()
                        showClearCacheDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showQualityDialog) {
        ChoiceDialog(
            title = "Preferred quality",
            choices = listOf("Auto (Best)", "1080p", "720p", "480p"),
            selected = uiState.playbackQuality,
            onSelect = {
                updateQuality(it)
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false },
        )
    }

    if (showLanguageDialog) {
        ChoiceDialog(
            title = "Content language",
            choices = listOf("English", "Japanese", "Korean", "Spanish"),
            selected = uiState.languagePreference,
            onSelect = {
                updateLanguage(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun AccentSwatch(name: String, selected: Boolean, onClick: () -> Unit) {
    val color = accentColor(name)
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                }
            ),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "$name accent selected",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsLabel(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClickableSettingsRow(
    icon: ImageVector,
    title: String,
    value: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SwitchSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun ApiKeyDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(currentKey) { mutableStateOf(currentKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update TMDB token") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Read access token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    choices: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(choice) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(choice, modifier = Modifier.weight(1f))
                        if (choice == selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun maskApiKey(apiKey: String?): String {
    if (apiKey.isNullOrBlank()) return "Not configured"
    if (apiKey.length <= 8) return "••••••••"
    return "${apiKey.take(4)}••••${apiKey.takeLast(4)}"
}
