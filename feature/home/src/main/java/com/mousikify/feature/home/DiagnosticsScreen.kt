package com.mousikify.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mousikify.core.designsystem.theme.SpotifyBlack
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifySurfaceElevated
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary
import com.mousikify.core.media.diagnostics.DiagEvent
import com.mousikify.core.media.diagnostics.DiagLogLevel
import com.mousikify.core.media.remote.ResolverProbeResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissToast()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        DiagnosticsScreen(
            uiState = uiState,
            onNavigateBack = onNavigateBack,
            onRunHealthCheck = { viewModel.runHealthCheck() },
            onClearUrlCache = { viewModel.clearUrlCache() },
            onClearLogs = { viewModel.clearLogs() },
            onFilterSelected = { viewModel.setLogFilter(it) },
            onCopyReport = { viewModel.copyReportToClipboard() }
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
fun DiagnosticsScreen(
    uiState: DiagnosticsUiState,
    onNavigateBack: () -> Unit,
    onRunHealthCheck: () -> Unit,
    onClearUrlCache: () -> Unit,
    onClearLogs: () -> Unit,
    onFilterSelected: (String) -> Unit,
    onCopyReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBlack)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top App Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SpotifyTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Diagnostics & Health",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyTextPrimary
                    )
                    Text(
                        text = "Streaming API & System Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = SpotifyTextSecondary
                    )
                }
                IconButton(
                    onClick = onRunHealthCheck,
                    enabled = !uiState.isTesting
                ) {
                    if (uiState.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = SpotifyGreen,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Health Check",
                            tint = SpotifyGreen
                        )
                    }
                }
            }
        }

        // Health Status Overview Card
        item {
            val report = uiState.diagnosticReport
            val isOperational = report?.isOperational ?: true
            val isDegraded = report?.isDegraded ?: false

            val (badgeColor, statusTitle, statusIcon) = when {
                !isOperational -> Triple(Color(0xFFE53935), "STREAMING OFFLINE", Icons.Default.Error)
                isDegraded -> Triple(Color(0xFFFFB300), "DEGRADED STREAMING", Icons.Default.Warning)
                else -> Triple(SpotifyGreen, "OPERATIONAL", Icons.Default.CheckCircle)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SpotifyCardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }

                        if (uiState.isTesting) {
                            Text(
                                text = "Probing...",
                                style = MaterialTheme.typography.labelSmall,
                                color = SpotifyTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = report?.overallVerdict ?: "Running streaming diagnostics...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpotifyTextPrimary
                    )

                    if (report != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Search API",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SpotifyTextSecondary
                                )
                                Text(
                                    text = if (report.searchOk) "OK (${report.searchLatencyMs}ms)" else "FAIL (${report.searchError})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (report.searchOk) SpotifyGreen else Color(0xFFE53935)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Cached Stream URLs",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SpotifyTextSecondary
                                )
                                Text(
                                    text = "${report.cachedUrlCount} URLs in memory",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SpotifyTextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRunHealthCheck,
                    enabled = !uiState.isTesting,
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (uiState.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isTesting) "Testing" else "Run Test",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                OutlinedButton(
                    onClick = onCopyReport,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyTextPrimary),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = SpotifyTextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Copy Report",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = onClearUrlCache,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyTextSecondary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = "Clear Cache",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Resolver Probe Breakdown
        item {
            Text(
                text = "Stream Resolvers Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SpotifyTextPrimary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        val report = uiState.diagnosticReport
        if (report != null && report.resolverResults.isNotEmpty()) {
            items(report.resolverResults) { result ->
                ResolverResultItem(result = result)
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SpotifyCardBackground.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "Press 'Run Test' above to probe individual streaming endpoints.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpotifyTextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        // System & Environment Information
        item {
            Text(
                text = "Environment & Build Data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SpotifyTextPrimary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            val env = uiState.environment
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SpotifyCardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnvRow(label = "App Version", value = env.appVersion)
                    EnvRow(label = "NewPipeExtractor", value = env.newPipeExtractorVersion)
                    EnvRow(label = "Android OS", value = env.androidVersion)
                    EnvRow(label = "Device", value = env.deviceModel)
                    EnvRow(
                        label = "Network",
                        value = "${env.networkType} (${if (env.isNetworkConnected) "Connected" else "Offline"})",
                        valueColor = if (env.isNetworkConnected) SpotifyGreen else Color(0xFFE53935)
                    )
                    EnvRow(label = "URL Cache Count", value = "${env.cachedStreamUrls} active entries")
                }
            }
        }

        // Live Diagnostic Logs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Diagnostic Logs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyTextPrimary
                )
                Text(
                    text = "Clear Logs",
                    style = MaterialTheme.typography.labelSmall,
                    color = SpotifyTextSecondary,
                    modifier = Modifier.clickable { onClearLogs() }
                )
            }
        }

        item {
            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                val filters = listOf("ALL", "ERRORS", "STREAMING", "SEARCH")
                items(filters) { filter ->
                    val selected = uiState.selectedFilter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterSelected(filter) },
                        label = {
                            Text(
                                text = filter,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpotifyGreen,
                            selectedLabelColor = Color.Black,
                            containerColor = SpotifySurfaceElevated,
                            labelColor = SpotifyTextSecondary
                        )
                    )
                }
            }
        }

        if (uiState.recentEvents.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SpotifyCardBackground.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "No log events recorded for this filter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpotifyTextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(uiState.recentEvents) { event ->
                DiagnosticEventItem(event = event)
            }
        }
    }
}

@Composable
private fun ResolverResultItem(result: ResolverProbeResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SpotifyCardBackground),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.resolverName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyTextPrimary
                )
                if (result.isSuccess) {
                    val info = buildString {
                        append("${result.latencyMs}ms")
                        result.bitrate?.let { append(" • ${it / 1000}kbps") }
                        result.format?.let { append(" • $it") }
                    }
                    Text(
                        text = info,
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyTextSecondary
                    )
                } else {
                    Text(
                        text = result.errorMessage ?: "Failed",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE53935),
                        maxLines = 2
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (result.isSuccess) SpotifyGreen.copy(alpha = 0.15f) else Color(0xFFE53935).copy(alpha = 0.15f),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = if (result.isSuccess) "PASS" else "FAIL",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (result.isSuccess) SpotifyGreen else Color(0xFFE53935),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EnvRow(
    label: String,
    value: String,
    valueColor: Color = SpotifyTextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SpotifyTextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
private fun DiagnosticEventItem(event: DiagEvent) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val timeStr = remember(event.timestampEpochMs) { dateFormat.format(Date(event.timestampEpochMs)) }

    val (badgeBg, badgeFg) = when (event.level) {
        DiagLogLevel.SUCCESS -> Pair(SpotifyGreen.copy(alpha = 0.15f), SpotifyGreen)
        DiagLogLevel.WARN -> Pair(Color(0xFFFFB300).copy(alpha = 0.15f), Color(0xFFFFB300))
        DiagLogLevel.ERROR -> Pair(Color(0xFFE53935).copy(alpha = 0.15f), Color(0xFFE53935))
        DiagLogLevel.INFO -> Pair(Color.White.copy(alpha = 0.08f), SpotifyTextSecondary)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SpotifyCardBackground.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = event.level.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = event.tag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyTextPrimary
                    )
                }

                Text(
                    text = timeStr,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = SpotifyTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = event.message,
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyTextPrimary
            )

            val details = event.details
            if (!details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = details,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = SpotifyTextSecondary,
                    maxLines = 3
                )
            }
        }
    }
}
