package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.StreamConfig
import com.example.model.StreamStatus
import com.example.model.VideoMetadata
import com.example.service.LiveStreamService
import com.example.storage.SecurePreferences
import com.example.util.VideoUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val securePreferences = SecurePreferences(application)

    private val _selectedVideo = MutableStateFlow<VideoMetadata?>(null)
    val selectedVideo: StateFlow<VideoMetadata?> = _selectedVideo.asStateFlow()

    private val _config = MutableStateFlow(StreamConfig())
    val config: StateFlow<StreamConfig> = _config.asStateFlow()

    private val _isKeyVisible = MutableStateFlow(false)
    val isKeyVisible: StateFlow<Boolean> = _isKeyVisible.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    // Real-time stream status shared from the Foreground Service
    val streamStatus: StateFlow<StreamStatus> = LiveStreamService.streamStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreamStatus())

    init {
        loadSavedData()
    }

    private fun loadSavedData() {
        viewModelScope.launch {
            val savedConfig = securePreferences.loadConfig()
            _config.value = savedConfig

            val savedUri = securePreferences.getSelectedVideoUri()
            if (savedUri != null) {
                try {
                    val metadata = VideoUtils.extractMetadata(getApplication(), savedUri)
                    if (metadata != null && metadata.durationMs > 0) {
                        _selectedVideo.value = metadata
                    }
                } catch (e: Exception) {
                    // Stale URI or file removed
                }
            }
        }
    }

    fun onVideoSelected(uri: Uri) {
        val context = getApplication<Application>()
        try {
            // Persist read permission across reboots & background service
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            // Some pickers or content providers do not support persistable flags
        }

        viewModelScope.launch {
            val metadata = VideoUtils.extractMetadata(context, uri)
            _selectedVideo.value = metadata
            securePreferences.saveSelectedVideoUri(uri)
            _validationError.value = null
            LiveStreamService.log("Selected video: ${metadata?.name ?: "Unknown"} (${metadata?.formattedResolution})")
        }
    }

    fun updateServerUrl(url: String) {
        _config.update { it.copy(serverUrl = url) }
        saveCurrentConfig()
    }

    fun updateStreamKey(key: String) {
        _config.update { it.copy(streamKey = key) }
        _validationError.value = null
        saveCurrentConfig()
    }

    fun updateResolution(width: Int, height: Int) {
        _config.update { it.copy(resolutionWidth = width, resolutionHeight = height) }
        saveCurrentConfig()
    }

    fun updateFps(fps: Int) {
        _config.update { it.copy(fps = fps) }
        saveCurrentConfig()
    }

    fun updateVideoBitrate(kbps: Int) {
        _config.update { it.copy(videoBitrateKbps = kbps) }
        saveCurrentConfig()
    }

    fun updateAudioBitrate(kbps: Int) {
        _config.update { it.copy(audioBitrateKbps = kbps) }
        saveCurrentConfig()
    }

    fun updateAutoReconnect(autoReconnect: Boolean) {
        _config.update { it.copy(autoReconnect = autoReconnect) }
        saveCurrentConfig()
    }

    fun toggleKeyVisibility() {
        _isKeyVisible.update { !it }
    }

    fun clearValidationError() {
        _validationError.value = null
    }

    private fun saveCurrentConfig() {
        securePreferences.saveConfig(_config.value)
    }

    fun startLiveStream() {
        val video = _selectedVideo.value
        if (video == null) {
            _validationError.value = "Please select a video from phone storage first."
            return
        }

        val currentCfg = _config.value
        if (currentCfg.streamKey.trim().isEmpty()) {
            _validationError.value = "Please enter your YouTube Stream Key."
            return
        }

        if (!currentCfg.serverUrl.startsWith("rtmp://") && !currentCfg.serverUrl.startsWith("rtmps://")) {
            _validationError.value = "Server URL must start with rtmp:// or rtmps://"
            return
        }

        _validationError.value = null
        saveCurrentConfig()

        val context = getApplication<Application>()
        val serviceIntent = Intent(context, LiveStreamService::class.java).apply {
            action = LiveStreamService.ACTION_START
            putExtra(LiveStreamService.EXTRA_VIDEO_URI, video.uri.toString())
            putExtra(LiveStreamService.EXTRA_VIDEO_NAME, video.name)
            putExtra(LiveStreamService.EXTRA_SERVER_URL, currentCfg.serverUrl)
            putExtra(LiveStreamService.EXTRA_STREAM_KEY, currentCfg.streamKey)
            putExtra(LiveStreamService.EXTRA_WIDTH, currentCfg.resolutionWidth)
            putExtra(LiveStreamService.EXTRA_HEIGHT, currentCfg.resolutionHeight)
            putExtra(LiveStreamService.EXTRA_FPS, currentCfg.fps)
            putExtra(LiveStreamService.EXTRA_VIDEO_BITRATE, currentCfg.videoBitrateKbps)
            putExtra(LiveStreamService.EXTRA_AUDIO_BITRATE, currentCfg.audioBitrateKbps)
            putExtra(LiveStreamService.EXTRA_AUTO_RECONNECT, currentCfg.autoReconnect)
        }

        ContextCompat.startForegroundService(context, serviceIntent)
    }

    fun stopLiveStream() {
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, LiveStreamService::class.java).apply {
            action = LiveStreamService.ACTION_STOP
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
