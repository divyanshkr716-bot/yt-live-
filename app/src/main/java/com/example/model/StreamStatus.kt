package com.example.model

enum class StreamStateEnum {
    OFFLINE,
    STARTING,
    LIVE,
    RECONNECTING,
    STOPPED,
    ERROR
}

data class StreamStatus(
    val state: StreamStateEnum = StreamStateEnum.OFFLINE,
    val durationSeconds: Long = 0,
    val reconnectCount: Int = 0,
    val currentBitrateKbps: Long = 0,
    val loopCount: Int = 0,
    val errorMessage: String? = null,
    val activeVideoName: String? = null,
    val activeSettingsSummary: String? = null,
    val logs: List<String> = emptyList()
) {
    val formattedDuration: String
        get() {
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }

    val isStreaming: Boolean
        get() = state == StreamStateEnum.LIVE || state == StreamStateEnum.RECONNECTING || state == StreamStateEnum.STARTING
}
