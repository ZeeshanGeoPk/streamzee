package com.streamzee.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamzee.viewmodel.MainUiState

private val Purple = Color(0xFFA855F7)
private val CardBg = Color(0xFF161622)
private val TextSecondary = Color(0xFF8E8E9F)
private val ScreenBg = Color(0xFF050508)

@Composable
fun profileScreen(
    uiState: MainUiState,
    updateTheme: (String) -> Unit,
    updateQuality: (String) -> Unit,
    updateLanguage: (String) -> Unit,
    toggleSubtitles: () -> Unit,
    toggleNotifications: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ── Profile Header Card ──────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Settings, "Settings", tint = Purple)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar with gradient border
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(listOf(Purple, Color(0xFF6366F1))),
                            shape = CircleShape
                        )
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C3E))
                ) {
                    AsyncImage(
                        model = "https://i.pinimg.com/736x/9e/2b/e4/9e2be4f1a241a8be8d4836d5fbbe2ee2.jpg",
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Online indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .background(Color(0xFF22C55E), CircleShape)
                            .border(2.dp, ScreenBg, CircleShape)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Zeeshan Ali",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        "zeeshanali@gmail.com",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Purple, Color(0xFF6366F1))
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "⭐ Premium Plan",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // ── Stats Row ────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                statCard(
                    title = "Hours Watched",
                    value = uiState.hoursWatched.toString(),
                    modifier = Modifier.weight(1f)
                )
                statCard(
                    title = "Favorite Genres",
                    value = "Action, Anime,\nSci-Fi",
                    modifier = Modifier.weight(1f)
                )
                statCard(
                    title = "Anime Completed",
                    value = uiState.completedAnimeCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Settings ─────────────────────────────────────────────────────
        item {
            Text(
                "Settings",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column {
                    settingsRow(
                        icon = Icons.Default.DarkMode,
                        label = "Theme Mode",
                        value = uiState.themeMode,
                        onClick = {
                            updateTheme(if (uiState.themeMode == "Dark") "Light" else "Dark")
                        }
                    )
                    divider()
                    settingsRow(
                        icon = Icons.Default.HighQuality,
                        label = "Playback Quality",
                        value = uiState.playbackQuality,
                        onClick = {
                            val qualities = listOf("Auto (Best)", "1080p", "720p", "480p")
                            val idx = qualities.indexOf(uiState.playbackQuality)
                            updateQuality(qualities[(idx + 1) % qualities.size])
                        }
                    )
                    divider()
                    settingsRow(
                        icon = Icons.Default.Language,
                        label = "Language",
                        value = uiState.languagePreference,
                        onClick = {
                            val langs = listOf("English", "Japanese", "Korean", "Spanish")
                            val idx = langs.indexOf(uiState.languagePreference)
                            updateLanguage(langs[(idx + 1) % langs.size])
                        }
                    )
                    divider()
                    settingsRow(
                        icon = Icons.Default.Subtitles,
                        label = "Subtitles",
                        value = if (uiState.subtitlesEnabled) "On" else "Off",
                        onClick = toggleSubtitles
                    )
                    divider()
                    settingsRow(
                        icon = Icons.Default.Notifications,
                        label = "Notifications",
                        value = if (uiState.notificationsEnabled) "On" else "Off",
                        onClick = toggleNotifications
                    )
                }
            }
        }

        // ── More Section ─────────────────────────────────────────────────
        item {
            Text(
                "More",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column {
                    settingsRow(
                        icon = Icons.Default.History,
                        label = "Watch History",
                        value = "",
                        onClick = {}
                    )
                    divider()
                    settingsRow(
                        icon = Icons.Default.Download,
                        label = "My Downloads",
                        value = "",
                        onClick = {}
                    )
                    divider()
                    settingsRow(
                        icon = Icons.Default.Update,
                        label = "App Updates",
                        value = "v2.1.0",
                        onClick = {}
                    )
                    divider()
                    settingsRow(
                        icon = Icons.Default.Help,
                        label = "Help & Support",
                        value = "",
                        onClick = {}
                    )
                }
            }
        }

        // ── Logout Button ────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLogout() }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Logout,
                    contentDescription = "Logout",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Logout",
                    color = Color(0xFFEF4444),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun statCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                value,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun settingsRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = TextSecondary, modifier = Modifier.size(22.dp))
            Text(label, color = Color.White, fontSize = 15.sp)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (value.isNotBlank()) {
                Text(value, color = TextSecondary, fontSize = 14.sp)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun divider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = Color(0xFF2C2C3E),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
