package com.example.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.PollutionAnalysis
import com.example.data.database.AirQualityScanEntity
import com.example.data.repository.AirQualityRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AirQualityViewModel(private val repository: AirQualityRepository) : ViewModel() {

    // Status UI States
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisStatus = MutableStateFlow("")
    val analysisStatus: StateFlow<String> = _analysisStatus.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val _lastAnalysisResult = MutableStateFlow<PollutionAnalysis?>(null)
    val lastAnalysisResult: StateFlow<PollutionAnalysis?> = _lastAnalysisResult.asStateFlow()

    // Real-time Scan History
    val scanHistory: StateFlow<List<AirQualityScanEntity>> = repository.allScans
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Alert Systems
    private val _alertThreshold = MutableStateFlow(100) // Default AQI threshold
    val alertThreshold: StateFlow<Int> = _alertThreshold.asStateFlow()

    private val _showDangerAlert = MutableStateFlow(false)
    val showDangerAlert: StateFlow<Boolean> = _showDangerAlert.asStateFlow()

    private val _dangerAlertScan = MutableStateFlow<AirQualityScanEntity?>(null)
    val dangerAlertScan: StateFlow<AirQualityScanEntity?> = _dangerAlertScan.asStateFlow()

    private val _currentLocation = MutableStateFlow("San Francisco, CA")
    val currentLocation: StateFlow<String> = _currentLocation.asStateFlow()

    fun setCurrentLocation(location: String) {
        _currentLocation.value = location
    }

    fun setAlertThreshold(threshold: Int) {
        _alertThreshold.value = threshold
    }

    fun dismissDangerAlert() {
        _showDangerAlert.value = false
        _dangerAlertScan.value = null
    }

    fun clearError() {
        _scanError.value = null
    }

    /**
     * Conducts air quality pollution scan from captured image or preset environment
     */
    fun performAirQualityScan(
        bitmap: Bitmap,
        isSimulated: Boolean,
        presetName: String?
    ) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _scanError.value = null
            _lastAnalysisResult.value = null
            
            _analysisStatus.value = "Uplinking visual data to AeroScan AI..."
            
            try {
                // Call repository
                val result = repository.detectPollution(bitmap, isSimulated, presetName)
                _lastAnalysisResult.value = result

                // Create a record for database
                val scanEntity = AirQualityScanEntity(
                    timestamp = System.currentTimeMillis(),
                    aqi = result.aqi,
                    category = result.category,
                    dominantPollutant = result.dominantPollutant,
                    description = result.description,
                    recommendations = result.recommendations,
                    isSimulated = isSimulated || presetName != null,
                    presetName = presetName,
                    location = _currentLocation.value
                )

                // Save scan History
                repository.saveScan(scanEntity)

                // Check alert triggers: drops below danger levels (custom threshold)
                if (result.aqi >= _alertThreshold.value) {
                    _dangerAlertScan.value = scanEntity
                    _showDangerAlert.value = true
                }

                _analysisStatus.value = "Analysis completed!"
            } catch (e: Exception) {
                _scanError.value = e.message ?: "Failed to perform visual scan."
                _analysisStatus.value = "Analysis failed."
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun deleteScan(id: Int) {
        viewModelScope.launch {
            repository.deleteScan(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun saveScan(scan: AirQualityScanEntity) {
        viewModelScope.launch {
            repository.saveScan(scan)
        }
    }

    fun clearLastResult() {
        _lastAnalysisResult.value = null
    }

    fun selectHistoryItem(result: PollutionAnalysis) {
        _lastAnalysisResult.value = result
    }

    fun postCameraError(msg: String) {
        _scanError.value = msg
    }

    fun triggerDangerAlert(scan: AirQualityScanEntity) {
        _dangerAlertScan.value = scan
        _showDangerAlert.value = true
    }

    fun generateMockTrendData() {
        viewModelScope.launch {
            val loc = _currentLocation.value
            val now = System.currentTimeMillis()
            val hourMs = 3600000L
            
            val mockPoints = listOf(
                Pair(now - 24 * hourMs, 45),
                Pair(now - 18 * hourMs, 82),
                Pair(now - 12 * hourMs, 148),
                Pair(now - 6 * hourMs, 95),
                Pair(now - 2 * hourMs, 120),
                Pair(now, 160)
            )
            
            mockPoints.forEach { (time, aqi) ->
                val category = when {
                    aqi <= 50 -> "Good"
                    aqi <= 100 -> "Moderate"
                    aqi <= 150 -> "Unhealthy for Sensitive Groups"
                    aqi <= 200 -> "Unhealthy"
                    else -> "Very Unhealthy"
                }
                val pollutant = when {
                    aqi <= 50 -> "None"
                    aqi <= 100 -> "PM10 / Dust"
                    else -> "PM2.5 / Smog"
                }
                val entity = AirQualityScanEntity(
                    timestamp = time,
                    aqi = aqi,
                    category = category,
                    dominantPollutant = pollutant,
                    description = "Trend analysis historical reading logged dynamically for location analysis.",
                    recommendations = "Observe standard safety precautions for $category conditions.",
                    isSimulated = true,
                    presetName = "moderate_dust",
                    location = loc
                )
                repository.saveScan(entity)
            }
        }
    }

    // Factory Provider
    class Factory(private val repository: AirQualityRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AirQualityViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AirQualityViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
