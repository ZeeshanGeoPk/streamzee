package com.example.streamzee.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.streamzee.data.AllAnimeShow

@Composable
fun animeDetailsScreen(
    show: AllAnimeShow,
    onBack: () -> Unit,
    onPlayEpisode: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var episodeText by remember { mutableStateOf("1") }
    val parsedEpisode = episodeText.toIntOrNull() ?: 1

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = show.name,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = onBack) {
                Text("Back")
            }
        }

        Text(
            text = "AllAnime result",
            style = MaterialTheme.typography.bodyLarge,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter an episode number to resolve the best available anime source from AllAnime.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = episodeText,
                        onValueChange = { episodeText = it.filter { char -> char.isDigit() } },
                        label = { Text("Episode") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        onClick = { onPlayEpisode(parsedEpisode) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text("Play")
                    }
                }
            }
        }

        Text(
            text = "If you don’t know the exact episode number, start at episode 1 and adjust it until the player resolves the correct stream.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
