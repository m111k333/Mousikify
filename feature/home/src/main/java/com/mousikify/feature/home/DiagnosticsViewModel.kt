package com.mousikify.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mousikify.core.media.diagnostics.DiagEvent
import com.mousikify.core.media.diagnostics.DiagLogLevel
import com.mousikify.core.media.diagnostics.DiagnosticsLog
import com.mousikify.core.media.remote.RemoteMusicRepository
import com.mousikify.core.media.remote.StreamingDiagnosticReport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SystemEnvironmentInfo(
    val appVersion: String = "1.0.0",
    val newPipeExtractorVersion: String = "v0.26.5",
    val androidVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val networkType: String,
    val isNetworkConnected: Boolean,
    val cachedStreamUrls: Int
)

data class DiagnosticsUiState(
    val isTesting: Boolean = false,
    val diagnosticReport: StreamingDiagnosticReport? = null,
    val environment: SystemEnvironmentInfo,
    val recentEvents: List<DiagEvent> = emptyList(),
    val selectedFilter: String = "ALL", // ALL, ERRORS, STREAMING, SEARCH
    val toastMessage: String? = null
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteMusicRepository: RemoteMusicRepository,
    private val diagnosticsLog: DiagnosticsLog
) : ViewModel() {

    private val _isTesting = MutableStateFlow(false)
    private val _diagnosticReport = MutableStateFlow<StreamingDiagnosticReport?>(null)
    private val _selectedFilter = MutableStateFlow("ALL")
    private val _toastMessage = MutableStateFlow<String?>(null)

    private fun getEnvironmentInfo(): SystemEnvironmentInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val netType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular Mobile Data"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> if (isConnected) "Connected" else "Offline / No Connection"
        }

        return SystemEnvironmentInfo(
            networkType = netType,
            isNetworkConnected = isConnected,
            cachedStreamUrls = remoteMusicRepository.getCachedUrlCount()
        )
    }

    val uiState: StateFlow<DiagnosticsUiState> = combine(
        _isTesting,
        _diagnosticReport,
        _selectedFilter,
        _toastMessage,
        diagnosticsLog.events
    ) { isTesting, report, filter, toast, events ->
        val filteredEvents = when (filter) {
            "ERRORS" -> events.filter { it.level == DiagLogLevel.ERROR || it.level == DiagLogLevel.WARN }
            "STREAMING" -> events.filter { it.tag.contains("Stream", ignoreCase = true) || it.tag.contains("ExoPlayer", ignoreCase = true) }
            "SEARCH" -> events.filter { it.tag.contains("Search", ignoreCase = true) }
            else -> events
        }.reversed() // Show newest first

        DiagnosticsUiState(
            isTesting = isTesting,
            diagnosticReport = report,
            environment = getEnvironmentInfo(),
            recentEvents = filteredEvents,
            selectedFilter = filter,
            toastMessage = toast
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DiagnosticsUiState(
            environment = getEnvironmentInfo()
        )
    )

    init {
        // Run initial diagnostics test on open
        runHealthCheck()
    }

    fun runHealthCheck(sampleVideoId: String = "dQw4w9WgXcQ") {
        if (_isTesting.value) return
        viewModelScope.launch {
            _isTesting.value = true
            try {
                val report = remoteMusicRepository.runStreamingDiagnostics(sampleVideoId)
                _diagnosticReport.value = report
                _toastMessage.value = "Health check completed"
            } catch (e: Exception) {
                _toastMessage.value = "Diagnostics failed: ${e.message}"
            } finally {
                _isTesting.value = false
            }
        }
    }

    fun clearUrlCache() {
        remoteMusicRepository.invalidateAllUrls()
        _toastMessage.value = "Stream URL cache cleared"
    }

    fun clearLogs() {
        diagnosticsLog.clear()
        _toastMessage.value = "Event logs cleared"
    }

    fun setLogFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun dismissToast() {
        _toastMessage.value = null
    }

    fun copyReportToClipboard() {
        val state = uiState.value
        val report = state.diagnosticReport
        val env = state.environment
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val text = buildString {
            appendLine("=== MOUSIKIFY DIAGNOSTICS & STREAM HEALTH REPORT ===")
            appendLine("Timestamp: ${dateFormat.format(Date())}")
            appendLine("App Version: ${env.appVersion}")
            appendLine("NewPipeExtractor: ${env.newPipeExtractorVersion}")
            appendLine("OS: ${env.androidVersion}")
            appendLine("Device: ${env.deviceModel}")
            appendLine("Network: ${env.networkType} (Connected: ${env.isNetworkConnected})")
            appendLine("Cached Stream URLs: ${env.cachedStreamUrls}")
            appendLine()
            appendLine("--- OVERALL STATUS ---")
            if (report != null) {
                appendLine("Verdict: ${report.overallVerdict}")
                appendLine("Operational: ${report.isOperational} | Degraded: ${report.isDegraded}")
                appendLine("Sample Track ID: ${report.sampleVideoId}")
                appendLine("Search API: ${if (report.searchOk) "OK (${report.searchItemCount} items, ${report.searchLatencyMs}ms)" else "FAILED: ${report.searchError}"}")
                appendLine()
                appendLine("--- RESOLVER PROBE BREAKDOWN ---")
                for (res in report.resolverResults) {
                    val status = if (res.isSuccess) "PASS" else "FAIL"
                    val info = if (res.isSuccess) "${res.latencyMs}ms, ${res.bitrate} bps, ${res.format}" else "Error: ${res.errorMessage}"
                    appendLine("[${status}] ${res.resolverName} -> $info")
                }
            } else {
                appendLine("No active test report generated.")
            }
            appendLine()
            appendLine("--- RECENT DIAGNOSTIC EVENTS (${state.recentEvents.size}) ---")
            for (ev in state.recentEvents.take(30)) {
                val time = dateFormat.format(Date(ev.timestampEpochMs))
                appendLine("[$time] [${ev.level}] [${ev.tag}]: ${ev.message}")
                val details = ev.details
                if (!details.isNullOrBlank()) {
                    appendLine("  Details: ${details.take(150)}")
                }
            }
            appendLine("=== END REPORT ===")
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Mousikify Diagnostics Report", text)
        clipboard?.setPrimaryClip(clip)
        _toastMessage.value = "Report copied to clipboard!"
    }
}
