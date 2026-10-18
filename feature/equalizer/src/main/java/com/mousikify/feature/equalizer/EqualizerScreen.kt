package com.mousikify.feature.equalizer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mousikify.core.designsystem.theme.SpotifyBackground
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifySurfaceElevated
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary

val EQ_LABELS = listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")

@Composable
fun EqualizerRoute(
    onBackClick: () -> Unit = {},
    viewModel: EqualizerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    BackHandler(onBack = onBackClick)
    EqualizerScreen(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick
    )
}

@Composable
fun EqualizerScreen(
    uiState: EqualizerUiState,
    onIntent: (EqualizerIntent) -> Unit,
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSaveDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top Bar / Title & Toggle
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SpotifyTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Equalizer", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        text = "10-Band Biquad Hardware DSP",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpotifyTextSecondary
                    )
                }
                Switch(
                    checked = uiState.isEnabled,
                    onCheckedChange = { onIntent(EqualizerIntent.SetEnabled(it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = SpotifyGreen,
                        uncheckedTrackColor = SpotifyCardBackground
                    )
                )
            }
        }

        // Preset Chips
        item {
            Text(text = "Presets", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.presets) { preset ->
                    FilterChip(
                        selected = preset.presetId == uiState.selectedPresetId,
                        onClick = { onIntent(EqualizerIntent.SelectPreset(preset)) },
                        label = { Text(preset.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpotifyGreen,
                            selectedLabelColor = Color.Black,
                            containerColor = SpotifyCardBackground,
                            labelColor = SpotifyTextPrimary
                        )
                    )
                }
            }
        }

        // 10-Band Sliders Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SpotifySurfaceElevated),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Frequency Response", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(
                            onClick = { showSaveDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyGreen)
                        ) {
                            Text("Save Preset", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    uiState.bandLevelsMb.forEachIndexed { index, levelMb ->
                        val freqLabel = EQ_LABELS.getOrElse(index) { "Band $index" }
                        val dbGain = levelMb / 100f
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(freqLabel, style = MaterialTheme.typography.bodyMedium, color = SpotifyTextSecondary)
                                Text(
                                    text = String.format("%+.1f dB", dbGain),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (dbGain != 0f) SpotifyGreen else SpotifyTextPrimary
                                )
                            }
                            Slider(
                                value = dbGain,
                                onValueChange = { newDb ->
                                    val mb = (newDb * 100).toInt()
                                    onIntent(EqualizerIntent.SetBandLevel(index, mb))
                                },
                                valueRange = -15f..15f,
                                enabled = uiState.isEnabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = SpotifyGreen,
                                    activeTrackColor = SpotifyGreen,
                                    inactiveTrackColor = SpotifyCardBackground
                                )
                            )
                        }
                    }
                }
            }
        }

        // DSP Effects: Bass Boost & Virtualizer
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SpotifySurfaceElevated),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Audio Enhancements", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Bass Boost
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bass Boost", style = MaterialTheme.typography.bodyMedium, color = SpotifyTextSecondary)
                        Text("${uiState.bassBoostStrength / 10}%", style = MaterialTheme.typography.bodyMedium, color = SpotifyGreen)
                    }
                    Slider(
                        value = uiState.bassBoostStrength.toFloat(),
                        onValueChange = { onIntent(EqualizerIntent.SetBassBoost(it.toInt())) },
                        valueRange = 0f..1000f,
                        enabled = uiState.isEnabled,
                        colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Virtualizer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Virtualizer (Spatial Audio)", style = MaterialTheme.typography.bodyMedium, color = SpotifyTextSecondary)
                        Text("${uiState.virtualizerStrength / 10}%", style = MaterialTheme.typography.bodyMedium, color = SpotifyGreen)
                    }
                    Slider(
                        value = uiState.virtualizerStrength.toFloat(),
                        onValueChange = { onIntent(EqualizerIntent.SetVirtualizer(it.toInt())) },
                        valueRange = 0f..1000f,
                        enabled = uiState.isEnabled,
                        colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen)
                    )
                }
            }
        }

        // Dual-ExoPlayer Crossfade Duration
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SpotifySurfaceElevated),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Dual-ExoPlayer Crossfade", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Overlapping sine/equal-power crossfade transition between tracks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpotifyTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Crossfade Duration", style = MaterialTheme.typography.bodyMedium, color = SpotifyTextSecondary)
                        Text(
                            text = if (uiState.crossfadeDurationSeconds == 0f) "Off" else "${uiState.crossfadeDurationSeconds.toInt()} seconds",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SpotifyGreen
                        )
                    }
                    Slider(
                        value = uiState.crossfadeDurationSeconds,
                        onValueChange = { onIntent(EqualizerIntent.SetCrossfadeDuration(it)) },
                        valueRange = 0f..12f,
                        steps = 11,
                        colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen)
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        SavePresetDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                onIntent(EqualizerIntent.SaveCustomPreset(name))
                showSaveDialog = false
            }
        )
    }
}

@Composable
private fun SavePresetDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpotifyCardBackground,
        title = { Text("Save Equalizer Preset", color = SpotifyTextPrimary) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Preset name", color = SpotifyTextSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SpotifyGreen,
                    unfocusedBorderColor = SpotifyTextSecondary,
                    focusedTextColor = SpotifyTextPrimary,
                    unfocusedTextColor = SpotifyTextPrimary
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onSave(name) },
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
            ) {
                Text("Save", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SpotifyTextSecondary)
            }
        }
    )
}
