package com.example.model

data class StreamConfig(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val streamKey: String = "",
    val resolutionWidth: Int = 1280,
    val resolutionHeight: Int = 720,
    val fps: Int = 30,
    val videoBitrateKbps: Int = 2500,
    val audioBitrateKbps: Int = 128,
    val autoReconnect: Boolean = true
) {
    companion object {
        const val DEFAULT_SERVER_URL = "rtmp://a.rtmp.youtube.com/live2"
        const val BACKUP_SERVER_URL = "rtmp://b.rtmp.youtube.com/live2?backup=1"
        const val RTMPS_SERVER_URL = "rtmps://a.rtmps.youtube.com/live2"
    }

    val fullRtmpEndpoint: String
        get() {
            val trimmedUrl = serverUrl.trim()
            val trimmedKey = streamKey.trim()
            return if (trimmedUrl.endsWith("/")) {
                "$trimmedUrl$trimmedKey"
            } else {
                "$trimmedUrl/$trimmedKey"
            }
        }

    val maskedStreamKey: String
        get() = if (streamKey.length > 8) {
            "${streamKey.take(4)}••••••••${streamKey.takeLast(4)}"
        } else if (streamKey.isNotEmpty()) {
            "••••••••"
        } else {
            ""
        }

    val resolutionLabel: String
        get() = when {
            resolutionHeight == 1080 -> "1080p (FHD)"
            resolutionHeight == 720 -> "720p (HD)"
            resolutionHeight == 480 -> "480p (SD)"
            resolutionHeight == 360 -> "360p"
            else -> "${resolutionWidth}x${resolutionHeight}"
        }
}
