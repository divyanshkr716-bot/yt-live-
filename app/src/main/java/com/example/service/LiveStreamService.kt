package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.model.StreamConfig
import com.example.model.StreamStateEnum
import com.example.model.StreamStatus
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.decoder.AudioDecoderInterface
import com.pedro.encoder.input.decoder.VideoDecoderInterface
import com.pedro.library.rtmp.RtmpFromFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LiveStreamService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null
    private var reconnectJob: Job? = null

    private var rtmpFromFile: RtmpFromFile? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var currentVideoUri: Uri? = null
    private var cachedVideoFile: File? = null
    private var currentVideoName: String = "Selected Video"
    private var currentConfig: StreamConfig = StreamConfig()
    private var isUserInitiatedStop = false
    private var consecutiveFailures = 0

    companion object {
        private const val TAG = "LiveStreamService"
        const val CHANNEL_ID = "youtube_loop_live_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.action.START_STREAM"
        const val ACTION_STOP = "com.example.action.STOP_STREAM"
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_NAME = "extra_video_name"
        const val EXTRA_SERVER_URL = "extra_server_url"
        const val EXTRA_STREAM_KEY = "extra_stream_key"
        const val EXTRA_WIDTH = "extra_width"
        const val EXTRA_HEIGHT = "extra_height"
        const val EXTRA_FPS = "extra_fps"
        const val EXTRA_VIDEO_BITRATE = "extra_video_bitrate"
        const val EXTRA_AUDIO_BITRATE = "extra_audio_bitrate"
        const val EXTRA_AUTO_RECONNECT = "extra_auto_reconnect"

        private const val MAX_RECONNECT_ATTEMPTS = 5

        private val _streamStatus = MutableStateFlow(StreamStatus())
        val streamStatus: StateFlow<StreamStatus> = _streamStatus.asStateFlow()

        fun log(msg: String) {
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val entry = "[$timestamp] $msg"
            Log.d(TAG, entry)
            _streamStatus.update { current ->
                val updatedLogs = (listOf(entry) + current.logs).take(50)
                current.copy(logs = updatedLogs)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isUserInitiatedStop = false
                consecutiveFailures = 0
                val videoUriStr = intent.getStringExtra(EXTRA_VIDEO_URI)
                currentVideoName = intent.getStringExtra(EXTRA_VIDEO_NAME) ?: "Selected Video"
                val serverUrl = intent.getStringExtra(EXTRA_SERVER_URL) ?: StreamConfig.DEFAULT_SERVER_URL
                val streamKey = intent.getStringExtra(EXTRA_STREAM_KEY) ?: ""
                val width = intent.getIntExtra(EXTRA_WIDTH, 1280)
                val height = intent.getIntExtra(EXTRA_HEIGHT, 720)
                val fps = intent.getIntExtra(EXTRA_FPS, 30)
                val videoBitrate = intent.getIntExtra(EXTRA_VIDEO_BITRATE, 2500)
                val audioBitrate = intent.getIntExtra(EXTRA_AUDIO_BITRATE, 128)
                val autoReconnect = intent.getBooleanExtra(EXTRA_AUTO_RECONNECT, true)

                if (videoUriStr.isNullOrEmpty() || streamKey.isEmpty()) {
                    log("Cannot start: Missing video URI or stream key")
                    _streamStatus.update {
                        it.copy(
                            state = StreamStateEnum.ERROR,
                            errorMessage = "Video or Stream Key missing"
                        )
                    }
                    stopSelf()
                    return START_NOT_STICKY
                }

                currentVideoUri = Uri.parse(videoUriStr)
                currentConfig = StreamConfig(
                    serverUrl = serverUrl,
                    streamKey = streamKey,
                    resolutionWidth = width,
                    resolutionHeight = height,
                    fps = fps,
                    videoBitrateKbps = videoBitrate,
                    audioBitrateKbps = audioBitrate,
                    autoReconnect = autoReconnect
                )

                // Start Foreground immediately
                startForegroundNotification()

                log("Initiating live stream with ${currentConfig.resolutionWidth}x${currentConfig.resolutionHeight} @ ${currentConfig.fps}fps, ${currentConfig.videoBitrateKbps}kbps")
                _streamStatus.update {
                    it.copy(
                        state = StreamStateEnum.STARTING,
                        activeVideoName = currentVideoName,
                        activeSettingsSummary = "${currentConfig.resolutionLabel} • ${currentConfig.fps}FPS • ${currentConfig.videoBitrateKbps}kbps",
                        errorMessage = null,
                        durationSeconds = 0,
                        loopCount = 0,
                        reconnectCount = 0
                    )
                }

                startStreamingPipeline()
            }
            ACTION_STOP -> {
                log("User requested to stop stream")
                stopStreamingInternal(StreamStateEnum.STOPPED)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startStreamingPipeline(forceSoftwareCodec: Boolean = false) {
        val sourceUri = currentVideoUri ?: return
        val endpoint = currentConfig.fullRtmpEndpoint

        // Android 10+ uses scoped storage. Materialize the selected SAF document
        // into the app's private cache so MediaExtractor/MediaCodec can access it
        // reliably after the app is backgrounded.
        val uri = try {
            getLocalCacheUri(sourceUri)
        } catch (e: Exception) {
            log("Failed to prepare local video: ${e.message}")
            Log.e(TAG, "Failed to cache selected video", e)
            _streamStatus.update {
                it.copy(
                    state = StreamStateEnum.ERROR,
                    errorMessage = "Cannot access selected video"
                )
            }
            stopStreamingInternal(StreamStateEnum.ERROR)
            stopSelf()
            return
        }

        try {
            // Clean up previous instance if any
            rtmpFromFile?.let {
                if (it.isStreaming) it.stopStream()
            }

            val connectChecker = object : ConnectChecker {
                override fun onConnectionStarted(url: String) {
                    log("RTMP connection started to: ${currentConfig.serverUrl}")
                    _streamStatus.update { it.copy(state = StreamStateEnum.STARTING) }
                    updateNotification()
                }

                override fun onConnectionSuccess() {
                    log("Connected successfully! Stream is now LIVE on YouTube")
                    consecutiveFailures = 0
                    _streamStatus.update {
                        it.copy(
                            state = StreamStateEnum.LIVE,
                            errorMessage = null
                        )
                    }
                    startDurationTimer()
                    updateNotification()
                }

                override fun onConnectionFailed(reason: String) {
                    log("Connection failed: $reason")
                    handleConnectionFailure("Connection failed: $reason")
                }

                override fun onDisconnect() {
                    log("RTMP disconnected from server")
                    if (!isUserInitiatedStop) {
                        handleConnectionFailure("Disconnected from YouTube Live")
                    }
                }

                override fun onAuthError() {
                    log("Authentication failed! Please verify your YouTube Stream Key")
                    _streamStatus.update {
                        it.copy(
                            state = StreamStateEnum.ERROR,
                            errorMessage = "Auth error: Invalid Stream Key"
                        )
                    }
                    stopStreamingInternal(StreamStateEnum.ERROR)
                    stopSelf()
                }

                override fun onAuthSuccess() {
                    log("Stream authentication accepted")
                }

                override fun onNewBitrate(bitrate: Long) {
                    val kbps = bitrate / 1000
                    _streamStatus.update { it.copy(currentBitrateKbps = kbps) }
                }
            }

            val videoDecoderInterface = object : VideoDecoderInterface {
                override fun onVideoDecoderFinished() {
                    _streamStatus.update { current ->
                        val newCount = current.loopCount + 1
                        log("Video finished. Auto-looping seamless iteration #$newCount")
                        current.copy(loopCount = newCount)
                    }
                    updateNotification()
                }
            }

            val audioDecoderInterface = object : AudioDecoderInterface {
                override fun onAudioDecoderFinished() {
                    // Handled automatically in loop mode
                }
            }

            val rtmp = RtmpFromFile(connectChecker, videoDecoderInterface, audioDecoderInterface)

            // Prefer the device H.264/AAC hardware codecs. Some Android 10
            // vendor codecs fail at MediaCodec.start(); the catch block below
            // retries once with Android's software codecs for compatibility.
            if (forceSoftwareCodec) {
                rtmp.forceCodecType(CodecUtil.CodecType.SOFTWARE, CodecUtil.CodecType.SOFTWARE)
                log("Retrying with software H.264/AAC codecs for Android compatibility")
            } else {
                rtmp.forceCodecType(CodecUtil.CodecType.HARDWARE, CodecUtil.CodecType.HARDWARE)
                log("Using hardware H.264/AAC codecs")
            }

            // Enable native infinite seamless looping
            rtmp.setLoopMode(true)

            // Prepare Video & Audio with requested configuration
            val videoBitrateBits = currentConfig.videoBitrateKbps * 1024
            val audioBitrateBits = currentConfig.audioBitrateKbps * 1024

            val videoPrepared = rtmp.prepareVideo(this, uri, videoBitrateBits, 0)
            if (!videoPrepared) {
                log("Failed to prepare video decoder/encoder for selected file")
                _streamStatus.update {
                    it.copy(
                        state = StreamStateEnum.ERROR,
                        errorMessage = "Failed to initialize video codec"
                    )
                }
                stopStreamingInternal(StreamStateEnum.ERROR)
                stopSelf()
                return
            }

            // Audio is optional (some videos might have no audio track)
            try {
                val audioPrepared = rtmp.prepareAudio(this, uri, audioBitrateBits)
                if (audioPrepared) {
                    log("Audio decoder prepared (${currentConfig.audioBitrateKbps} kbps)")
                } else {
                    log("No compatible audio track or audio decoder initialization skipped")
                }
            } catch (e: Exception) {
                log("Audio preparation notice: ${e.message}")
            }

            rtmpFromFile = rtmp
            rtmp.startStream(endpoint)
            log("Streaming pipeline started with hardware H.264 MediaCodec")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting streaming pipeline", e)
            log("Error starting streaming pipeline: ${e.javaClass.simpleName}: ${e.message}")

            // A number of Android 10 devices expose a hardware H.264/AAC
            // encoder that passes prepareVideo/prepareAudio but fails when
            // MediaCodec.start() is actually called. Retry once with the
            // software codecs instead of immediately killing the stream.
            if (!forceSoftwareCodec) {
                try {
                    rtmpFromFile?.let {
                        if (it.isStreaming) it.stopStream()
                    }
                } catch (stopError: Exception) {
                    Log.w(TAG, "Failed to stop failed hardware pipeline", stopError)
                }
                rtmpFromFile = null

                log("Hardware codec start failed; retrying once with software codecs...")
                serviceScope.launch {
                    delay(500)
                    if (isActive && !isUserInitiatedStop) {
                        startStreamingPipeline(forceSoftwareCodec = true)
                    }
                }
            } else {
                _streamStatus.update {
                    it.copy(
                        state = StreamStateEnum.ERROR,
                        errorMessage = e.message ?: "Failed to start stream on hardware and software codecs"
                    )
                }
                stopStreamingInternal(StreamStateEnum.ERROR)
                stopSelf()
            }
        }
    }

    private fun getLocalCacheUri(sourceUri: Uri): Uri {
        val existing = cachedVideoFile
        if (existing != null && existing.exists() && existing.length() > 0L) {
            return Uri.fromFile(existing)
        }

        val extension = when {
            sourceUri.toString().contains(".mkv", ignoreCase = true) -> ".mkv"
            sourceUri.toString().contains(".webm", ignoreCase = true) -> ".webm"
            else -> ".mp4"
        }
        val target = File(cacheDir, "stream_source$extension")
        if (target.exists()) target.delete()

        contentResolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "Unable to open selected video" }
            target.outputStream().use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
            }
        }

        if (!target.exists() || target.length() == 0L) {
            throw IllegalStateException("Selected video is empty or inaccessible")
        }

        cachedVideoFile = target
        log("Video copied to private app cache (${target.length() / (1024 * 1024)} MB)")
        return Uri.fromFile(target)
    }

    private fun handleConnectionFailure(reason: String) {
        if (isUserInitiatedStop) return

        consecutiveFailures++
        _streamStatus.update {
            it.copy(
                state = StreamStateEnum.RECONNECTING,
                reconnectCount = consecutiveFailures,
                errorMessage = reason
            )
        }
        updateNotification()

        if (!currentConfig.autoReconnect) {
            log("Auto-reconnect disabled. Stopping stream.")
            stopStreamingInternal(StreamStateEnum.ERROR)
            stopSelf()
            return
        }

        if (consecutiveFailures > MAX_RECONNECT_ATTEMPTS) {
            log("Max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) reached. Stopping to prevent infinite rapid loop.")
            _streamStatus.update {
                it.copy(
                    state = StreamStateEnum.ERROR,
                    errorMessage = "Failed to reconnect after $MAX_RECONNECT_ATTEMPTS attempts."
                )
            }
            stopStreamingInternal(StreamStateEnum.ERROR)
            stopSelf()
            return
        }

        // Exponential backoff: 3s, 5s, 8s, 12s, 15s
        val delayMs = when (consecutiveFailures) {
            1 -> 3000L
            2 -> 5000L
            3 -> 8000L
            4 -> 12000L
            else -> 15000L
        }

        log("Reconnecting in ${delayMs / 1000}s (Attempt $consecutiveFailures/$MAX_RECONNECT_ATTEMPTS)...")
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            delay(delayMs)
            if (isActive && !isUserInitiatedStop) {
                log("Executing reconnect attempt $consecutiveFailures...")
                startStreamingPipeline()
            }
        }
    }

    private fun startDurationTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                _streamStatus.update {
                    if (it.state == StreamStateEnum.LIVE) {
                        it.copy(durationSeconds = it.durationSeconds + 1)
                    } else {
                        it
                    }
                }
                // Update notification every 5 seconds or when duration advances
                if (_streamStatus.value.durationSeconds % 5 == 0L) {
                    updateNotification()
                }
            }
        }
    }

    private fun stopStreamingInternal(finalState: StreamStateEnum) {
        isUserInitiatedStop = true
        timerJob?.cancel()
        reconnectJob?.cancel()

        try {
            rtmpFromFile?.let {
                if (it.isStreaming) {
                    it.stopStream()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping rtmp stream", e)
        }
        rtmpFromFile = null

        _streamStatus.update {
            it.copy(
                state = finalState,
                currentBitrateKbps = 0
            )
        }
        releaseLocks()
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "YouTubeLoopLive:StreamWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // 24 hours max safeguard
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "YouTubeLoopLive:WifiLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            log("WakeLock & WifiLock acquired for uninterrupted background streaming")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake/wifi lock", e)
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null

            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release locks", e)
        }
    }

    private fun registerNetworkCallback() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    log("Internet connection lost!")
                    if (_streamStatus.value.state == StreamStateEnum.LIVE) {
                        serviceScope.launch {
                            handleConnectionFailure("Network connection lost")
                        }
                    }
                }

                override fun onAvailable(network: Network) {
                    log("Internet connection available")
                    if (_streamStatus.value.state == StreamStateEnum.RECONNECTING && !isUserInitiatedStop) {
                        serviceScope.launch {
                            delay(1000)
                            log("Network restored. Triggering reconnection...")
                            startStreamingPipeline()
                        }
                    }
                }
            }
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            networkCallback = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister network callback", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YouTube Loop Live Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification for YouTube Live streaming service"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val status = _streamStatus.value
        val title = when (status.state) {
            StreamStateEnum.LIVE -> "🔴 LIVE: ${status.activeVideoName ?: currentVideoName}"
            StreamStateEnum.RECONNECTING -> "🔄 Reconnecting (Attempt ${status.reconnectCount}/$MAX_RECONNECT_ATTEMPTS)..."
            StreamStateEnum.STARTING -> "⏳ Starting YouTube Live..."
            else -> "YouTube Loop Live"
        }

        val content = when (status.state) {
            StreamStateEnum.LIVE -> "Duration: ${status.formattedDuration} • ${status.currentBitrateKbps} kbps • Loop: #${status.loopCount}"
            StreamStateEnum.RECONNECTING -> "Network recovery in progress. Screen can remain OFF."
            else -> "Preparing stream pipeline..."
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LiveStreamService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "STOP STREAM",
                stopPendingIntent
            )

        return builder.build()
    }

    private fun startForegroundNotification() {
        val notification = buildNotification()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            serviceType
        )
    }

    private fun updateNotification() {
        try {
            val notification = buildNotification()
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreamingInternal(StreamStateEnum.STOPPED)
        unregisterNetworkCallback()
        releaseLocks()
        cachedVideoFile?.let {
            try { if (it.exists()) it.delete() } catch (_: Exception) {}
        }
        cachedVideoFile = null
        log("LiveStreamService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
