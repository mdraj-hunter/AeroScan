package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.api.PollutionAnalysis
import com.example.data.database.AirQualityScanEntity
import com.example.ui.viewmodel.AirQualityViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PollutionAppScreen(
    viewModel: AirQualityViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Observe State flows from ViewModel
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisStatus by viewModel.analysisStatus.collectAsState()
    val scanError by viewModel.scanError.collectAsState()
    val lastResult by viewModel.lastAnalysisResult.collectAsState()
    val historyList by viewModel.scanHistory.collectAsState()
    val alertThreshold by viewModel.alertThreshold.collectAsState()
    val showDangerAlert by viewModel.showDangerAlert.collectAsState()
    val dangerAlertScan by viewModel.dangerAlertScan.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()

    val activeLocationScans = remember(historyList, currentLocation) {
        historyList.filter { it.location.equals(currentLocation, ignoreCase = true) }
    }

    // UI Configuration States
    var selectedPreset by remember { mutableStateOf("clear_sky") }
    var useLiveCamera by remember { mutableStateOf(false) }
    var showExplanationDialog by remember { mutableStateOf(false) }
    
    // Periodical automatic background monitor simulation state
    var isBackgroundTrackerOn by remember { mutableStateOf(false) }
    var lastSimulatedCheckTime by remember { mutableStateOf<String?>(null) }

    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    // Dynamic color matching the current AQI or Preset
    val activeAqiColor = getAqiColor(
        lastResult?.aqi ?: when (selectedPreset) {
            "clear_sky" -> 15
            "moderate_dust" -> 65
            "traffic_intersection" -> 120
            "industrial_smog" -> 175
            "hazardous_wildfire" -> 350
            else -> 0
        }
    )

    // Periodic Local Tracker Simulation Logic
    LaunchedEffect(isBackgroundTrackerOn) {
        if (isBackgroundTrackerOn) {
            while (isBackgroundTrackerOn) {
                delay(30000) // update every 30 seconds
                val calendar = Calendar.getInstance()
                val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                lastSimulatedCheckTime = formatter.format(calendar.time)

                // Simulate AQI shift around the preset level
                val baseLevel = when (selectedPreset) {
                    "clear_sky" -> 15
                    "moderate_dust" -> 70
                    "traffic_intersection" -> 125
                    "industrial_smog" -> 180
                    "hazardous_wildfire" -> 360
                    else -> 20
                }
                val currentSimAqi = baseLevel + Random.nextInt(-10, 15)
                if (currentSimAqi >= alertThreshold) {
                    // Trigger emergency warning automatically
                    val mockedScan = AirQualityScanEntity(
                        timestamp = System.currentTimeMillis(),
                        aqi = currentSimAqi,
                        category = getAqiCategory(currentSimAqi),
                        dominantPollutant = getMockDominantPollutant(selectedPreset),
                        description = "Simulated real-time tracking sensor detected a dangerous spike in particulate air indicators under regional ${selectedPreset.replace("_", " ")} conditions.",
                        recommendations = "Critical alert triggered as pollution spikes past comfort levels! Put on respirator mask and keep air filters operating.",
                        isSimulated = true,
                        presetName = selectedPreset
                    )
                    viewModel.saveScan(mockedScan)
                    viewModel.triggerDangerAlert(mockedScan)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // APP HEADER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Air,
                                contentDescription = null,
                                tint = activeAqiColor,
                                modifier = Modifier
                                    .size(28.dp)
                                    .padding(end = 4.dp)
                            )
                            Text(
                                text = "AeroScan AI",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                        Text(
                            text = "Atmospheric Camera Sensor",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Simulated Alarm Toggle Indicator
                    Surface(
                        onClick = { isBackgroundTrackerOn = !isBackgroundTrackerOn },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isBackgroundTrackerOn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.testTag("tracker_toggle_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val activeStateColor = if (isBackgroundTrackerOn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            Icon(
                                imageVector = if (isBackgroundTrackerOn) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                                contentDescription = null,
                                tint = activeStateColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isBackgroundTrackerOn) "BG Tracker On" else "BG Tracker Off",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = activeStateColor
                            )
                        }
                    }
                }

                // SCAN METHOD TABS (Live Camera vs Simulator Scenes)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            TabButton(
                                text = "Simulator Sky Scenes",
                                icon = Icons.Filled.Science,
                                isSelected = !useLiveCamera,
                                modifier = Modifier.weight(1f),
                                onClick = { useLiveCamera = false }
                            )
                            TabButton(
                                text = "Live Camera Feed",
                                icon = Icons.Filled.CameraAlt,
                                isSelected = useLiveCamera,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    useLiveCamera = true
                                    if (!cameraPermissionState.status.isGranted) {
                                        cameraPermissionState.launchPermissionRequest()
                                    }
                                }
                            )
                        }

                        // VIEW DECK CONTAINER
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                        ) {
                            if (useLiveCamera && cameraPermissionState.status.isGranted) {
                                // Real CameraX preview element
                                LiveCameraXView(
                                    onCaptured = { capturedBitmap ->
                                        viewModel.performAirQualityScan(
                                            bitmap = capturedBitmap,
                                            isSimulated = false,
                                            presetName = null
                                        )
                                    },
                                    onError = { err ->
                                        viewModel.postCameraError(err)
                                    }
                                )
                            } else {
                                // Programmatically drew high fidelity Gradient Sky Simulator representation based on active Preset!
                                SimulatedSkyLayout(preset = selectedPreset)

                                // Overlay info tags
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(12.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(activeAqiColor, CircleShape)
                                        )
                                        Text(
                                            text = "Environment: ${selectedPreset.replace("_", " ").uppercase()}",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (useLiveCamera && !cameraPermissionState.status.isGranted) {
                                    // Alert user that permission is needed, giving fallback simulation option
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.75f))
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Warning,
                                                contentDescription = null,
                                                tint = Color.Yellow,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Text(
                                                text = "Camera Permission Required",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "To analyze live local sky air quality, please authorize camera access. Alternatively, continue using the Sky Simulator Scenes.",
                                                color = Color.LightGray,
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center
                                            )
                                            Button(
                                                onClick = { cameraPermissionState.launchPermissionRequest() },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Text("Grant Permission", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SIMULATOR ENV PRESET SELECTOR (Only shown if tabs is set to Simulator)
                if (!useLiveCamera) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Select Outdoor Atmosphere Scenario:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PresetChip("Clear Azure Sky", "clear_sky", selectedPreset, Color(0xFF2ECC71)) {
                                selectedPreset = "clear_sky"
                            }
                            PresetChip("Fine Dust Haze", "moderate_dust", selectedPreset, Color(0xFFF1C40F)) {
                                selectedPreset = "moderate_dust"
                            }
                            PresetChip("Auto Exhaust", "traffic_intersection", selectedPreset, Color(0xFFE67E22)) {
                                selectedPreset = "traffic_intersection"
                            }
                            PresetChip("Factory Smog", "industrial_smog", selectedPreset, Color(0xFFE74C3C)) {
                                selectedPreset = "industrial_smog"
                            }
                            PresetChip("Wildfire Orange", "hazardous_wildfire", selectedPreset, Color(0xFF8E44AD)) {
                                selectedPreset = "hazardous_wildfire"
                            }
                        }

                        // SIMULATOR TRIGGER ACTION BUTTON
                        Button(
                            onClick = {
                                // Generate programmatic bitmap corresponding to the selected scene representation
                                val generatedBitmap = createAtmosphericBitmap(selectedPreset)
                                viewModel.performAirQualityScan(
                                    bitmap = generatedBitmap,
                                    isSimulated = true,
                                    presetName = selectedPreset
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("simulate_capture_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = activeAqiColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Science,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Analyze Preset Sky Atmosphere",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                // DANGER ALERTS SETTINGS PANEL (slider for configuring alert threshold)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Breathing Safety Alerts Settings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(onClick = { showExplanationDialog = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "Show Info",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            text = "Dangerous levels alarm threshold: AQI >= $alertThreshold (${getAqiCategory(alertThreshold)})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )

                        Slider(
                            value = alertThreshold.toFloat(),
                            onValueChange = { viewModel.setAlertThreshold(it.toInt()) },
                            valueRange = 50f..300f,
                            steps = 5,
                            modifier = Modifier.testTag("aqi_alert_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Background Pollution Tracker Simulation",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Switch(
                                checked = isBackgroundTrackerOn,
                                onCheckedChange = { isBackgroundTrackerOn = it },
                                modifier = Modifier.testTag("bg_tracker_switch")
                            )
                        }

                        if (isBackgroundTrackerOn) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color.Green, CircleShape)
                                    )
                                    Text(
                                        text = "Simulating air cycles... Last check: ${lastSimulatedCheckTime ?: "Waiting..."}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // LOCATION AND TRENDS CENTER
                LocationAndTrendsCard(
                    currentLocation = currentLocation,
                    historyList = historyList,
                    onSelectLocation = { viewModel.setCurrentLocation(it) },
                    onMockTrendData = { viewModel.generateMockTrendData() }
                )

                // BUSY LOADER
                AnimatedVisibility(
                    visible = isAnalyzing,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.5.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Deconstructing Air Quality...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = analysisStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // ERROR NOTIFIER
                scanError?.let { err ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Particulate Analysis Interrupted",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                IconButton(onClick = { viewModel.clearError() }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Dismiss",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = err,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // FOCUS ANALYSIS REPORT CARD
                val activeDisplayResult = lastResult
                if (activeDisplayResult != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Focused Air Quality Report",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        AnalysisReportCard(
                            result = activeDisplayResult,
                            onClose = { viewModel.clearLastResult() }
                        )
                    }
                }

                // HISTORY LOG BOARD
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sensor History Log ($currentLocation)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (activeLocationScans.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.clearAllHistory() },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear Logs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (activeLocationScans.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Cloud,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "No scanning entries for $currentLocation yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "Aim your camera at the sky or choose a preset context scenario and trigger an atmosphere check for this city.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            activeLocationScans.forEach { historyItem ->
                                HistoryRowItem(
                                    item = historyItem,
                                    onSelect = {
                                        viewModel.selectHistoryItem(
                                            PollutionAnalysis(
                                                aqi = historyItem.aqi,
                                                category = historyItem.category,
                                                dominantPollutant = historyItem.dominantPollutant,
                                                description = historyItem.description,
                                                recommendations = historyItem.recommendations
                                            )
                                        )
                                    },
                                    onDelete = { viewModel.deleteScan(historyItem.id) }
                                )
                            }
                        }
                    }
                }
            }

            // FULL OVERLAY SAFETY THRESHOLD ALERT POPUP
            if (showDangerAlert && dangerAlertScan != null) {
                val alertItem = dangerAlertScan!!
                val alertColor = getAqiColor(alertItem.aqi)
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.82f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .animateContentSize()
                            .testTag("danger_alert_dialog"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(3.dp, alertColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Flash warning beacon visual
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(alertColor.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val pulseScale = rememberInfiniteTransition().animateFloat(
                                    initialValue = 0.85f,
                                    targetValue = 1.15f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "Danger Warning",
                                    tint = alertColor,
                                    modifier = Modifier
                                        .size(36.dp * pulseScale.value)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                RawFlashPulsingText(text = "AQI EXCEEDS LIMIT")
                                Text(
                                    text = "DANGEROUS AIR DETECTED",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    ),
                                    color = alertColor
                                )
                            }

                            // Big Index Gauge
                            Row(
                                modifier = Modifier
                                    .background(alertColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AQI ${alertItem.aqi}",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = alertColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = alertColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant)

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Classification: ${alertItem.category}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Dominant Pollutant: ${alertItem.dominantPollutant}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = alertItem.description,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = alertColor.copy(alpha = 0.08f),
                                    border = BorderStroke(0.5.dp, alertColor.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = alertColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = alertItem.recommendations,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { viewModel.dismissDangerAlert() },
                                colors = ButtonDefaults.buttonColors(containerColor = alertColor),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Acknowledge & Close Advice", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // EXPLANATION EXPLAINER BOTTOM SHEET DIALOG
            if (showExplanationDialog) {
                AlertDialog(
                    onDismissRequest = { showExplanationDialog = false },
                    confirmButton = {
                        TextButton(onClick = { showExplanationDialog = false }) {
                            Text("Acknowledge", fontWeight = FontWeight.Bold)
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Air, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("AQI Threshold Reference", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "Configuring your Threshold Alert lets the system trigger safety notifications automatically whenever matching levels are detected in live checks or tracker loops:",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            AqiReferenceRow("Good (0-50)", Color(0xFF2ECC71), "Excellent oxygen levels, optimal breathing environments.")
                            AqiReferenceRow("Moderate (51-100)", Color(0xFFF1C40F), "Acceptable, slight fine haze suspended. Safe for most.")
                            AqiReferenceRow("Unhealthy for Sensitive (101-150)", Color(0xFFE67E22), "Mild congestion hazards. Breathing alerts advisory starts.")
                            AqiReferenceRow("Unhealthy (151-200)", Color(0xFFE74C3C), "High smog index. Outdoor masking and filters required.")
                            AqiReferenceRow("Very Unhealthy (201-300)", Color(0xFF9B59B6), "Heavy chemical suspension, respiratory alerts activated.")
                            AqiReferenceRow("Hazardous (301-500)", Color(0xFF8E44AD), "Severe environmental emergency. Quarantine index.")
                        }
                    }
                )
            }
        }
    }
}

// Subordinate composables

@Composable
fun TabButton(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun RawFlashPulsingText(text: String) {
    val flashColor = rememberInfiniteTransition().animateColor(
        initialValue = Color.Red,
        targetValue = Color(0xFFFFCC00),
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    Text(
        text = text,
        color = flashColor.value,
        fontWeight = FontWeight.Black,
        fontSize = 13.sp,
        letterSpacing = 1.5.sp
    )
}

@Composable
fun PresetChip(
    label: String,
    presetName: String,
    selectedPresetName: String,
    chipColor: Color,
    onSelected: () -> Unit
) {
    val isSelected = presetName == selectedPresetName
    Surface(
        onClick = onSelected,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) chipColor else MaterialTheme.colorScheme.outlineVariant
        ),
        color = if (isSelected) chipColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("preset_chip_$presetName")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(chipColor, CircleShape)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                color = if (isSelected) chipColor else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SimulatedSkyLayout(preset: String, modifier: Modifier = Modifier) {
    val gradientColors = when (preset) {
        "clear_sky" -> listOf(Color(0xFF1976D2), Color(0xFF64B5F6))
        "moderate_dust" -> listOf(Color(0xFF90A4AE), Color(0xFFCFD8DC))
        "traffic_intersection" -> listOf(Color(0xFF607D8B), Color(0xFFB0BEC5))
        "industrial_smog" -> listOf(Color(0xFF455A64), Color(0xFF78909C))
        "hazardous_wildfire" -> listOf(Color(0xFFE65100), Color(0xFFFFB74D))
        else -> listOf(Color(0xFF1976D2), Color(0xFF64B5F6))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Render matching visual scenery silhouettes
            val cityscapePaintColor = when (preset) {
                "clear_sky" -> Color(0x330D47A1)
                "moderate_dust" -> Color(0x3837474F)
                "traffic_intersection" -> Color(0x48263238)
                "industrial_smog" -> Color(0x55111E25)
                "hazardous_wildfire" -> Color(0x403E2723)
                else -> Color(0x330D47A1)
            }

            // Draw skyscraper bars
            drawRect(
                color = cityscapePaintColor,
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.08f, h * 0.55f),
                size = androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.45f)
            )
            drawRect(
                color = cityscapePaintColor,
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.4f),
                size = androidx.compose.ui.geometry.Size(w * 0.22f, h * 0.6f)
            )
            drawRect(
                color = cityscapePaintColor,
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.72f, h * 0.6f),
                size = androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.4f)
            )

            // Dynamic atmospheric smoke clouds or particles
            if (preset == "industrial_smog") {
                // Represent exhaust smoke plumes
                drawCircle(Color(0x28FFFFFF), radius = 35f, center = androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.45f))
                drawCircle(Color(0x18FFFFFF), radius = 55f, center = androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.38f))
            }

            // Draw tiny specs for particle density rendering
            if (preset != "clear_sky") {
                val filterColor = when (preset) {
                    "moderate_dust" -> Color(0x22A1887F)
                    "traffic_intersection" -> Color(0x3337474F)
                    "industrial_smog" -> Color(0x44263238)
                    "hazardous_wildfire" -> Color(0x55E65100)
                    else -> Color.Transparent
                }
                
                val rand = Random(1234)
                for (i in 0..30) {
                    val px = rand.nextFloat() * w
                    val py = rand.nextFloat() * h
                    val r = rand.nextFloat() * 3.5f + 1f
                    drawCircle(color = filterColor, radius = r, center = androidx.compose.ui.geometry.Offset(px, py))
                }
            }
        }
    }
}

@Composable
fun AnalysisReportCard(
    result: PollutionAnalysis,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val aqiColor = getAqiColor(result.aqi)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, aqiColor),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Title + Closing key
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(aqiColor, CircleShape)
                    )
                    Text(
                        text = result.category.uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        ),
                        color = aqiColor
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear Result",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Standard M3 Metric Board
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(aqiColor.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large digital reading
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AIR QUALITY INDEX",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = result.aqi.toString(),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = aqiColor
                    )
                }

                // Divider line
                VerticalDivider(
                    modifier = Modifier
                        .height(44.dp)
                        .padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Dominant substance classification
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = "DOMINANT POLLUTANT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = result.dominantPollutant,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Detailed descriptions
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Visual Atmospheric Observations:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = result.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            // Safety actionable advices
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = aqiColor.copy(alpha = 0.05f),
                border = BorderStroke(0.5.dp, aqiColor.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = aqiColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "Breath Guard Recommendations:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = aqiColor
                        )
                        Text(
                            text = result.recommendations,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryRowItem(
    item: AirQualityScanEntity,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val aqiColor = getAqiColor(item.aqi)
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy - h:mm a", Locale.getDefault()) }
    val formattedDate = formatter.format(Date(item.timestamp))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("history_item_${item.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rounded Index Indicator Badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(aqiColor.copy(alpha = 0.12f), CircleShape)
                    .border(1.5.dp, aqiColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = item.aqi.toString(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = aqiColor
                    )
                    Text(
                        text = "AQI",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = aqiColor
                    )
                }
            }

            // Description metadata block
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = aqiColor
                    )
                    if (item.isSimulated) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("SIM", fontSize = 8.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    text = "Pollutant: ${item.dominantPollutant}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formattedDate,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Row deletes
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp).testTag("delete_history_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun AqiReferenceRow(
    label: String,
    color: Color,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Color and text mapping helper functions

fun getAqiColor(aqi: Int): Color {
    return when {
        aqi <= 50 -> Color(0xFF2ECC71) // Good : Green
        aqi <= 100 -> Color(0xFFF1C40F) // Moderate : Yellow
        aqi <= 150 -> Color(0xFFE67E22) // Unhealthy for Sensitive : Orange
        aqi <= 200 -> Color(0xFFE74C3C) // Unhealthy : Red
        aqi <= 300 -> Color(0xFF9B59B6) // Very Unhealthy : Purple
        else -> Color(0xFF78281F) // Hazardous : Deep Crimson
    }
}

fun getAqiCategory(aqi: Int): String {
    return when {
        aqi <= 50 -> "Good"
        aqi <= 100 -> "Moderate"
        aqi <= 150 -> "Unhealthy for Sensitive Groups"
        aqi <= 200 -> "Unhealthy"
        aqi <= 300 -> "Very Unhealthy"
        else -> "Hazardous"
    }
}

fun getMockDominantPollutant(preset: String): String {
    return when (preset) {
        "clear_sky" -> "None / Clear Sky"
        "moderate_dust" -> "PM10 / Dust / Pollen"
        "traffic_intersection" -> "Smoke / Combustion"
        "industrial_smog" -> "PM2.5 / Smog"
        "hazardous_wildfire" -> "Smoke / Combustion"
        else -> "None"
    }
}

// PROGRAMMATIC BITMAP FACTORY
// Generates stylized canvas images with landmarks and distinct environmental colors to send real image buffers to the Gemini API
fun createAtmosphericBitmap(preset: String): Bitmap {
    val w = 400
    val h = 400
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { isAntiAlias = true }

    val gradientColors = when (preset) {
        "clear_sky" -> intArrayOf(0xFF1E88E5.toInt(), 0xFF90CAF9.toInt())
        "moderate_dust" -> intArrayOf(0xFFB0BEC5.toInt(), 0xFFECEFF1.toInt())
        "traffic_intersection" -> intArrayOf(0xFF78909C.toInt(), 0xFFCFD8DC.toInt())
        "industrial_smog" -> intArrayOf(0xFF546E7A.toInt(), 0xFF90A4AE.toInt())
        "hazardous_wildfire" -> intArrayOf(0xFFE65100.toInt(), 0xFFFFB74D.toInt())
        else -> intArrayOf(0xFF1E88E5.toInt(), 0xFF90CAF9.toInt())
    }

    val shader = LinearGradient(0f, 0f, 0f, h.toFloat(), gradientColors[0], gradientColors[1], Shader.TileMode.CLAMP)
    paint.shader = shader
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

    // Reset shader for drawing solid skylines
    paint.shader = null
    paint.color = when (preset) {
        "clear_sky" -> 0x501565C0.toInt()
        "moderate_dust" -> 0x60546E7A.toInt()
        "traffic_intersection" -> 0x7037474F.toInt()
        "industrial_smog" -> 0x90263238.toInt()
        "hazardous_wildfire" -> 0x803E2723.toInt()
        else -> 0x501565C0.toInt()
    }

    // Skyscrapers silhouettes
    canvas.drawRect(30f, 250f, 110f, 400f, paint)
    canvas.drawRect(150f, 180f, 250f, 400f, paint)
    canvas.drawRect(290f, 280f, 370f, 400f, paint)

    return bitmap
}

// Interactive Live CameraX composable which checks status dynamically
@Composable
fun LiveCameraXView(
    onCaptured: (Bitmap) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var checkCameraBound by remember { mutableStateOf(false) }
    var hasBackCamera by remember { mutableStateOf(true) }

    LaunchedEffect(cameraProviderFuture) {
        val provider = cameraProviderFuture.get()
        hasBackCamera = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
        checkCameraBound = true
    }

    if (!checkCameraBound) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
    } else if (!hasBackCamera) {
        // Fallback for emulators with disabled cameras
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "AeroCamera Hardware Unavailable",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "No compatible rear camera lens was detected on this physical device. Sky Simulator Scenes are fully functional below!",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    val viewFinder = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val mainExecutor = ContextCompat.getMainExecutor(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = androidx.camera.core.Preview.Builder().build().apply {
                            setSurfaceProvider(viewFinder.surfaceProvider)
                        }
                        
                        imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture
                            )
                        } catch (exc: Exception) {
                            onError("Camera initialization failed: ${exc.localizedMessage}")
                        }
                    }, mainExecutor)
                    viewFinder
                },
                modifier = Modifier.fillMaxSize()
            )

            // Dynamic Snap triggering overlay button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                Button(
                    onClick = {
                        val capture = imageCapture
                        if (capture == null) {
                            onError("Camera scanner engine not active yet.")
                            return@Button
                        }
                        
                        val defaultExecutor = ContextCompat.getMainExecutor(context)
                        capture.takePicture(defaultExecutor, object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bitmap = image.toBitmap()
                                onCaptured(bitmap)
                                image.close()
                            }

                            override fun onError(exception: ImageCaptureException) {
                                onError("Photo capture failed: ${exception.localizedMessage}")
                            }
                        })
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(54.dp).testTag("capture_live_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Camera,
                        contentDescription = "Capture live frame",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// Convert CameraX Image proxy frame directly back into standard Android bitmap for Gemini base64 converter
fun ImageProxy.toBitmap(): Bitmap {
    val byteBuffer = planes[0].buffer
    val bytes = ByteArray(byteBuffer.remaining())
    byteBuffer.get(bytes)
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationAndTrendsCard(
    currentLocation: String,
    historyList: List<AirQualityScanEntity>,
    onSelectLocation: (String) -> Unit,
    onMockTrendData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomLocationInput by remember { mutableStateOf(false) }
    var customLocationText by remember { mutableStateOf("") }

    val presetCities = listOf(
        "San Francisco, CA",
        "New York, NY",
        "London, UK",
        "Tokyo, Japan"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title & Active Target Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "Location Pin",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "AIR QUALITY MONITORING LOCATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = currentLocation,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                IconButton(onClick = { showCustomLocationInput = !showCustomLocationInput }) {
                    Icon(
                        imageVector = if (showCustomLocationInput) Icons.Filled.Close else Icons.Filled.EditLocation,
                        contentDescription = "Edit Location",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Custom Location Text Input Row
            AnimatedVisibility(visible = showCustomLocationInput) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customLocationText,
                        onValueChange = { customLocationText = it },
                        placeholder = { Text("Enter city...", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_location_input_field"),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                    Button(
                        onClick = {
                            if (customLocationText.isNotBlank()) {
                                onSelectLocation(customLocationText.trim())
                                showCustomLocationInput = false
                                customLocationText = ""
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("submit_custom_location_button")
                    ) {
                        Text("Set", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Quick Select Preset Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                presetCities.forEach { city ->
                    val isSelected = city.equals(currentLocation, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectLocation(city) },
                        label = { Text(city, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            if (isSelected) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("location_chip_$city")
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Filter scans corresponding to current location
            val locationScans = remember(historyList, currentLocation) {
                historyList.filter { it.location.equals(currentLocation, ignoreCase = true) }
            }

            if (locationScans.isEmpty()) {
                // Empty trends state with a Call to Action
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No Scans Recorded in $currentLocation",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Scan the sky to check the air index or pre-populate historical trend curves to monitor shifts over time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Button(
                            onClick = onMockTrendData,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("populate_mock_trends_button")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Populate Mock History Trends", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Trend Graph
                EnvironmentalTrendChart(scans = locationScans)
            }
        }
    }
}

@Composable
fun EnvironmentalTrendChart(
    scans: List<AirQualityScanEntity>,
    modifier: Modifier = Modifier
) {
    // Sort scans chronological
    val sortedScans = remember(scans) { scans.sortedBy { it.timestamp } }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "AQI TREND OVER TIME",
                fontSize = 11.sp,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val primaryColor = MaterialTheme.colorScheme.primary
            val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

            // Use absolute package for Canvas to avoid android.graphics.Canvas class conflict
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(vertical = 12.dp, horizontal = 24.dp)
            ) {
                val width = size.width
                val height = size.height
                
                // Max AQI we expect to draw scale correctly
                val maxAqi = (sortedScans.maxOfOrNull { it.aqi } ?: 200).toFloat().coerceAtLeast(150f)
                
                // Draw Horizontal Guidelines
                val thresholds = listOf(50f, 100f, 150f, 200f)
                thresholds.forEach { valAqi ->
                    if (valAqi <= maxAqi) {
                        val y = height - (valAqi / maxAqi) * height
                        drawLine(
                            color = when {
                                valAqi <= 50f -> Color(0xFF2ECC71).copy(alpha = 0.15f)
                                valAqi <= 100f -> Color(0xFFF1C40F).copy(alpha = 0.15f)
                                valAqi <= 150f -> Color(0xFFE67E22).copy(alpha = 0.15f)
                                else -> Color(0xFFE74C3C).copy(alpha = 0.15f)
                            },
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                // Map points
                val stepX = if (sortedScans.size > 1) width / (sortedScans.size - 1) else width
                val points = sortedScans.mapIndexed { idx, scan ->
                    val x = if (sortedScans.size > 1) idx * stepX else width / 2
                    val percentVal = scan.aqi.toFloat() / maxAqi
                    val y = height - (percentVal * height)
                    androidx.compose.ui.geometry.Offset(x, y)
                }

                // Draw filled gradient area under curve/line
                if (points.isNotEmpty()) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(points.first().x, height)
                        points.forEach { pt ->
                            lineTo(pt.x, pt.y)
                        }
                        lineTo(points.last().x, height)
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.12f), Color.Transparent),
                            startY = 0f,
                            endY = height
                        )
                    )
                }

                // Draw connecting bezier lines
                if (points.size > 1) {
                    val strokePath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            val pPrev = points[i - 1]
                            val pCurr = points[i]
                            val controlX1 = (pPrev.x + pCurr.x) / 2
                            val controlY1 = pPrev.y
                            val controlX2 = (pPrev.x + pCurr.x) / 2
                            val controlY2 = pCurr.y
                            cubicTo(controlX1, controlY1, controlX2, controlY2, pCurr.x, pCurr.y)
                        }
                    }
                    drawPath(
                        path = strokePath,
                        color = primaryColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
                    )
                } else if (points.size == 1) {
                    drawCircle(
                        color = primaryColor,
                        radius = 5.dp.toPx(),
                        center = points.first()
                    )
                }

                // Draw circular points & AQI texts
                val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                points.forEachIndexed { idx, pt ->
                    val scan = sortedScans[idx]
                    val ptColor = getAqiColor(scan.aqi)
                    
                    // Draw outer ring
                    drawCircle(
                        color = ptColor,
                        radius = 5.dp.toPx(),
                        center = pt
                    )
                    // Draw inner white dot
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = pt
                    )

                    // Draw AQI reading on top of dot
                    val textPaint = android.graphics.Paint().apply {
                        color = ptColor.value.toInt()
                        textSize = 8.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        scan.aqi.toString(),
                        pt.x,
                        pt.y - 7.dp.toPx(),
                        textPaint
                    )

                    // Draw timestamp under X axis
                    if (sortedScans.size <= 5 || idx % (sortedScans.size / 3).coerceAtLeast(1) == 0 || idx == sortedScans.lastIndex) {
                        val timeStr = dateFormat.format(Date(scan.timestamp))
                        val labelPaint = android.graphics.Paint().apply {
                            color = labelColor.value.toInt()
                            textSize = 7.dp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            timeStr,
                            pt.x,
                            height + 12.dp.toPx(),
                            labelPaint
                        )
                    }
                }
            }
            
            // Subtle footnote keys
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendChip("Healthy (<50)", Color(0xFF2ECC71))
                LegendChip("Moderate (50-100)", Color(0xFFF1C40F))
                LegendChip("Unsafe (>100)", Color(0xFFE67E22))
            }
        }
    }
}

@Composable
fun LegendChip(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}
