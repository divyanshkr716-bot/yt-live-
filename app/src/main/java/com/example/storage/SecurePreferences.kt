package com.example.storage

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.model.StreamConfig

class SecurePreferences(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "youtube_loop_live_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to standard prefs", e)
        context.getSharedPreferences("youtube_loop_live_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "SecurePreferences"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_STREAM_KEY = "stream_key"
        private const val KEY_WIDTH = "resolution_width"
        private const val KEY_HEIGHT = "resolution_height"
        private const val KEY_FPS = "fps"
        private const val KEY_VIDEO_BITRATE = "video_bitrate"
        private const val KEY_AUDIO_BITRATE = "audio_bitrate"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_SELECTED_VIDEO_URI = "selected_video_uri"
    }

    fun saveConfig(config: StreamConfig) {
        prefs.edit()
            .putString(KEY_SERVER_URL, config.serverUrl)
            .putString(KEY_STREAM_KEY, config.streamKey)
            .putInt(KEY_WIDTH, config.resolutionWidth)
            .putInt(KEY_HEIGHT, config.resolutionHeight)
            .putInt(KEY_FPS, config.fps)
            .putInt(KEY_VIDEO_BITRATE, config.videoBitrateKbps)
            .putInt(KEY_AUDIO_BITRATE, config.audioBitrateKbps)
            .putBoolean(KEY_AUTO_RECONNECT, config.autoReconnect)
            .apply()
    }

    fun loadConfig(): StreamConfig {
        return StreamConfig(
            serverUrl = prefs.getString(KEY_SERVER_URL, StreamConfig.DEFAULT_SERVER_URL)
                ?: StreamConfig.DEFAULT_SERVER_URL,
            streamKey = prefs.getString(KEY_STREAM_KEY, "") ?: "",
            resolutionWidth = prefs.getInt(KEY_WIDTH, 1280),
            resolutionHeight = prefs.getInt(KEY_HEIGHT, 720),
            fps = prefs.getInt(KEY_FPS, 30),
            videoBitrateKbps = prefs.getInt(KEY_VIDEO_BITRATE, 2500),
            audioBitrateKbps = prefs.getInt(KEY_AUDIO_BITRATE, 128),
            autoReconnect = prefs.getBoolean(KEY_AUTO_RECONNECT, true)
        )
    }

    fun saveSelectedVideoUri(uri: Uri?) {
        prefs.edit().putString(KEY_SELECTED_VIDEO_URI, uri?.toString()).apply()
    }

    fun getSelectedVideoUri(): Uri? {
        val uriStr = prefs.getString(KEY_SELECTED_VIDEO_URI, null) ?: return null
        return try {
            Uri.parse(uriStr)
        } catch (e: Exception) {
            null
        }
    }
}
