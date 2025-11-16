package com.vrla.forest

import android.content.Context
import android.net.Uri

/**
 * Helper class to manage video-related SharedPreferences
 * Provides a centralized way to store and retrieve video URIs and folder URIs
 */
class VideoPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Save video URI as string and set video changed flag
     */
    fun saveVideoUri(uriString: String) {
        prefs.edit()
            .putString(KEY_VIDEO_URI, uriString)
            .putBoolean(KEY_VIDEO_CHANGED_FLAG, true)
            .apply()
    }

    /**
     * Save video URI and set video changed flag
     */
    fun saveVideoUri(uri: Uri) {
        prefs.edit()
            .putString(KEY_VIDEO_URI, uri.toString())
            .putBoolean(KEY_VIDEO_CHANGED_FLAG, true)
            .apply()
    }

    /**
     * Get saved video URI
     * @return Saved video URI, or null if not set
     */
    fun getSavedVideoUri(): Uri? {
        return prefs.getString(KEY_VIDEO_URI, null)?.let { Uri.parse(it) }
    }

    /**
     * Check if video was changed in Settings and clear the flag
     * This is a one-time flag that gets cleared after reading
     * @return true if video was changed in Settings, false otherwise
     */
    fun checkAndClearVideoChangedFlag(): Boolean {
        val changed = prefs.getBoolean(KEY_VIDEO_CHANGED_FLAG, false)
        if (changed) {
            prefs.edit()
                .putBoolean(KEY_VIDEO_CHANGED_FLAG, false)
                .apply()
        }
        return changed
    }

    /**
     * Save video folder tree URI
     */
    fun saveVideoFolderUri(uri: Uri) {
        prefs.edit()
            .putString(KEY_VIDEO_FOLDER_URI, uri.toString())
            .apply()
    }

    /**
     * Get saved video folder tree URI
     * @return Saved folder URI, or null if not set
     */
    fun getVideoFolderUri(): Uri? {
        return prefs.getString(KEY_VIDEO_FOLDER_URI, null)?.let { Uri.parse(it) }
    }

    /**
     * Clear video URI
     */
    fun clearVideoUri() {
        prefs.edit()
            .remove(KEY_VIDEO_URI)
            .apply()
    }

    /**
     * Clear video folder URI
     */
    fun clearVideoFolderUri() {
        prefs.edit()
            .remove(KEY_VIDEO_FOLDER_URI)
            .apply()
    }

    /**
     * Clear all video preferences
     */
    fun clearAll() {
        prefs.edit()
            .remove(KEY_VIDEO_URI)
            .remove(KEY_VIDEO_FOLDER_URI)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "VRLAPrefs"
        private const val KEY_VIDEO_URI = "video_uri"
        private const val KEY_VIDEO_FOLDER_URI = "video_folder_uri"
        private const val KEY_VIDEO_CHANGED_FLAG = "video_changed_flag"
    }
}
