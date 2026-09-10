package com.example.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StreamConfig
import com.example.model.StreamStateEnum
import com.example.model.StreamStatus
import com.example.model.VideoMetadata
import com.example.ui.theme.DarkCanvas
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.YouTubeRed
import com.example.ui.theme.YouTubeRedDark
import com.example.ui.theme.YouTubeRedGlow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val selectedVideo by viewModel.selectedVideo.collectAsState()
    val config by viewModel.config.collectAsState()
    val streamStatus by viewModel.streamStatus.collectAsState()
    val isKeyVisible by viewModel.isKeyVisible.collectAsState()
    val validationError by viewModel.validationError.collectAsState()

    var showLogsDialog by remember { mutableStateOf(false) }
    var showSettingsExpanded by remember { mutableStateOf(false) }

    // SAF File Picker for video files
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onVideoSelected(uri)
        }
    }

    // Android 13+ Notification Permission Launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = DarkCanvas,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(YouTubeRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Loop,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "YouTube Loop Live",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showLogsDialog = true },
                        modifier = Modifier.testTag("show_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Stream Logs",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DarkCanvas
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Validation Error Banner
            AnimatedVisibility(visible = validationError != null) {
                validationError?.let { err ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = ErrorRed.copy(alpha = 0.15f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearValidationError() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 1. Live Broadcast Status Card
            LiveStatusCard(
                status = streamStatus,
                isStreaming = streamStatus.isStreaming,
                onStartLive = { viewModel.startLiveStream() },
                onStopLive = { viewModel.stopLiveStream() }
            )

            // 2. Video Selection Card
            VideoSelectionCard(
                video = selectedVideo,
                isStreaming = streamStatus.isStreaming,
                onPickVideo = {
                    videoPickerLauncher.launch(arrayOf("video/*"))
                }
            )

            // 3. YouTube RTMP Settings Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = YouTubeRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "YouTube Live Credentials",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                        }
                    }

                    // Server URL Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "RTMP Server URL",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        OutlinedTextField(
                            value = config.serverUrl,
                            onValueChange = { viewModel.updateServerUrl(it) },
                            enabled = !streamStatus.isStreaming,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("server_url_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = YouTubeRed,
                                unfocusedBorderColor = DarkSurfaceBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = DarkSurfaceElevated,
                                unfocusedContainerColor = DarkSurfaceElevated
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        // Server URL Quick Select Chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            FilterChip(
                                selected = config.serverUrl == StreamConfig.DEFAULT_SERVER_URL,
                                onClick = { viewModel.updateServerUrl(StreamConfig.DEFAULT_SERVER_URL) },
                                label = { Text("Primary (YouTube)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YouTubeRed.copy(alpha = 0.2f),
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = config.serverUrl == StreamConfig.BACKUP_SERVER_URL,
                                onClick = { viewModel.updateServerUrl(StreamConfig.BACKUP_SERVER_URL) },
                                label = { Text("Backup Server") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YouTubeRed.copy(alpha = 0.2f),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // Stream Key Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Stream Key (Encrypted & Secure)",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        OutlinedTextField(
                            value = config.streamKey,
                            onValueChange = { viewModel.updateStreamKey(it) },
                            enabled = !streamStatus.isStreaming,
                            singleLine = true,
                            visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            val clip = clipboardManager.getText()?.text
                                            if (!clip.isNullOrEmpty() && !streamStatus.isStreaming) {
                                                viewModel.updateStreamKey(clip.trim())
                                            }
                                        },
                                        enabled = !streamStatus.isStreaming
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Paste Stream Key",
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.toggleKeyVisibility() },
                                        modifier = Modifier.testTag("toggle_key_visibility")
                                    ) {
                                        Icon(
                                            imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Key Visibility",
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            placeholder = {
                                Text(
                                    text = "xxxx-xxxx-xxxx-xxxx-xxxx",
                                    color = Color.White.copy(alpha = 0.3f)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("stream_key_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = YouTubeRed,
                                unfocusedBorderColor = DarkSurfaceBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = DarkSurfaceElevated,
                                unfocusedContainerColor = DarkSurfaceElevated
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Text(
                            text = "Found in YouTube Studio > Live Stream > Stream Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.45f)
                        )
                    }

                    // Expandable Stream Pipeline Settings (Resolution, Bitrate, FPS)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSettingsExpanded = !showSettingsExpanded }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Encoding Settings (${config.resolutionLabel}, ${config.videoBitrateKbps} kbps)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Text(
                            text = if (showSettingsExpanded) "Hide" else "Customize",
                            style = MaterialTheme.typography.labelMedium,
                            color = YouTubeRedGlow
                        )
                    }

                    AnimatedVisibility(visible = showSettingsExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceElevated, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Resolution Selector
                            Text(
                                text = "Output Resolution",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ResolutionChip(
                                    label = "720p (HD Recommended)",
                                    width = 1280,
                                    height = 720,
                                    currentWidth = config.resolutionWidth,
                                    currentHeight = config.resolutionHeight,
                                    enabled = !streamStatus.isStreaming,
                                    onSelect = { w, h -> viewModel.updateResolution(w, h) }
                                )
                                ResolutionChip(
                                    label = "1080p (FHD)",
                                    width = 1920,
                                    height = 1080,
                                    currentWidth = config.resolutionWidth,
                                    currentHeight = config.resolutionHeight,
                                    enabled = !streamStatus.isStreaming,
                                    onSelect = { w, h -> viewModel.updateResolution(w, h) }
                                )
                                ResolutionChip(
                                    label = "480p (SD)",
                                    width = 854,
                                    height = 480,
                                    currentWidth = config.resolutionWidth,
                                    currentHeight = config.resolutionHeight,
                                    enabled = !streamStatus.isStreaming,
                                    onSelect = { w, h -> viewModel.updateResolution(w, h) }
                                )
                            }

                            // Bitrate Selector
                            Text(
                                text = "Video Bitrate",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BitrateChip("2500 kbps (Standard)", 2500, config.videoBitrateKbps, !streamStatus.isStreaming) {
                                    viewModel.updateVideoBitrate(it)
                                }
                                BitrateChip("4500 kbps (High Quality)", 4500, config.videoBitrateKbps, !streamStatus.isStreaming) {
                                    viewModel.updateVideoBitrate(it)
                                }
                                BitrateChip("1500 kbps (Low Data)", 1500, config.videoBitrateKbps, !streamStatus.isStreaming) {
                                    viewModel.updateVideoBitrate(it)
                                }
                            }

                            // Frame Rate
                            Text(
                                text = "Frame Rate (FPS)",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = config.fps == 30,
                                    onClick = { if (!streamStatus.isStreaming) viewModel.updateFps(30) },
                                    label = { Text("30 FPS (Optimal Battery)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = YouTubeRed.copy(alpha = 0.3f),
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = config.fps == 60,
                                    onClick = { if (!streamStatus.isStreaming) viewModel.updateFps(60) },
                                    label = { Text("60 FPS (Smooth)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = YouTubeRed.copy(alpha = 0.3f),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }

                            // Auto Reconnect Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Auto Reconnect on Network Failure",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Retries with exponential backoff if disconnected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                                Switch(
                                    checked = config.autoReconnect,
                                    onCheckedChange = { viewModel.updateAutoReconnect(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = YouTubeRed
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 4. Background & Hardware Optimization Info
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = LiveGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Background Streaming & Battery Optimized",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                        Text(
                            text = "Using hardware H.264 encoder. App can be safely minimized or screen locked; streaming continues seamlessly in foreground service.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Logs Dialog
    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            confirmButton = {
                TextButton(onClick = { showLogsDialog = false }) {
                    Text("Close", color = YouTubeRed)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stream Activity Log", color = Color.White)
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (streamStatus.logs.isEmpty()) {
                        item {
                            Text(
                                text = "No log events yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    } else {
                        items(streamStatus.logs) { logLine ->
                            Text(
                                text = logLine,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = when {
                                    logLine.contains("LIVE", ignoreCase = true) -> LiveGreen
                                    logLine.contains("failed", ignoreCase = true) || logLine.contains("error", ignoreCase = true) -> ErrorRed
                                    logLine.contains("Reconnect", ignoreCase = true) -> WarningAmber
                                    else -> Color.White.copy(alpha = 0.85f)
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }
}

@Composable
fun LiveStatusCard(
    status: StreamStatus,
    isStreaming: Boolean,
    onStartLive: () -> Unit,
    onStopLive: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (status.state) {
                StreamStateEnum.LIVE -> Color(0xFF1E0B0D)
                StreamStateEnum.RECONNECTING -> Color(0xFF1E170A)
                else -> DarkSurface
            }
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isStreaming) 1.5.dp else 1.dp,
            color = when (status.state) {
                StreamStateEnum.LIVE -> YouTubeRed.copy(alpha = 0.7f)
                StreamStateEnum.RECONNECTING -> WarningAmber.copy(alpha = 0.7f)
                else -> DarkSurfaceBorder
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Status Badge & Live Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Status Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when (status.state) {
                        StreamStateEnum.LIVE -> YouTubeRed
                        StreamStateEnum.RECONNECTING -> WarningAmber
                        StreamStateEnum.STARTING -> YouTubeRedDark
                        StreamStateEnum.STOPPED -> Color(0xFF444444)
                        StreamStateEnum.ERROR -> ErrorRed
                        StreamStateEnum.OFFLINE -> Color(0xFF333333)
                    },
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (status.state == StreamStateEnum.LIVE) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .alpha(pulseAlpha)
                                    .background(Color.White, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else if (status.state == StreamStateEnum.RECONNECTING) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = when (status.state) {
                                StreamStateEnum.LIVE -> "LIVE NOW"
                                StreamStateEnum.RECONNECTING -> "RECONNECTING"
                                StreamStateEnum.STARTING -> "STARTING..."
                                StreamStateEnum.STOPPED -> "STOPPED"
                                StreamStateEnum.ERROR -> "ERROR"
                                StreamStateEnum.OFFLINE -> "READY TO STREAM"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = if (status.state == StreamStateEnum.RECONNECTING) Color.Black else Color.White
                        )
                    }
                }

                // Live Bitrate Badge
                if (status.state == StreamStateEnum.LIVE) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurfaceElevated
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = LiveGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${status.currentBitrateKbps} kbps",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = LiveGreen
                            )
                        }
                    }
                }
            }

            // Central Metric Display
            if (status.state == StreamStateEnum.LIVE || status.state == StreamStateEnum.RECONNECTING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetricItem(label = "DURATION", value = status.formattedDuration)
                    MetricItem(label = "LOOP COUNT", value = "#${status.loopCount}")
                    MetricItem(label = "RECONNECTS", value = "${status.reconnectCount}")
                }
            }

            // Big Action Button: START LIVE / STOP LIVE
            if (isStreaming) {
                Button(
                    onClick = onStopLive,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E2E)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("stop_live_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STOP LIVE STREAM",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = ErrorRed
                        )
                    )
                }
            } else {
                Button(
                    onClick = onStartLive,
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_live_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "START LIVE",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = Color.White
        )
    }
}

@Composable
fun VideoSelectionCard(
    video: VideoMetadata?,
    isStreaming: Boolean,
    onPickVideo: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = YouTubeRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Looping Source Video",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = LiveGreen.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Loop,
                            contentDescription = null,
                            tint = LiveGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Infinite Loop",
                            style = MaterialTheme.typography.labelSmall,
                            color = LiveGreen
                        )
                    }
                }
            }

            if (video != null) {
                // Video Details Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(YouTubeRed.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoFile,
                                contentDescription = null,
                                tint = YouTubeRed,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${video.formattedResolution} • ${video.formattedDuration} • ${video.formattedSize}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onPickVideo,
                    enabled = !isStreaming,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("select_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Change Video")
                }
            } else {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceElevated)
                        .clickable(enabled = !isStreaming, onClick = onPickVideo)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.VideoFile,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap to select local video from phone",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "Supports MP4, MKV, WebM (SAF Storage)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }

                Button(
                    onClick = onPickVideo,
                    enabled = !isStreaming,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("select_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = YouTubeRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Video File", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ResolutionChip(
    label: String,
    width: Int,
    height: Int,
    currentWidth: Int,
    currentHeight: Int,
    enabled: Boolean,
    onSelect: (Int, Int) -> Unit
) {
    val selected = currentWidth == width && currentHeight == height
    FilterChip(
        selected = selected,
        onClick = { if (enabled) onSelect(width, height) },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = YouTubeRed.copy(alpha = 0.3f),
            selectedLabelColor = Color.White
        )
    )
}

@Composable
fun BitrateChip(
    label: String,
    bitrate: Int,
    currentBitrate: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit
) {
    val selected = currentBitrate == bitrate
    FilterChip(
        selected = selected,
        onClick = { if (enabled) onSelect(bitrate) },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = YouTubeRed.copy(alpha = 0.3f),
            selectedLabelColor = Color.White
        )
    )
}
