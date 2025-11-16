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
     * Save video URI as string
     */
    fun saveVideoUri(uriString: String) {
        prefs.edit()
            .putString(KEY_VIDEO_URI, uriString)
            .apply()
    }

    /**
     * Save video URI
     */
    fun saveVideoUri(uri: Uri) {
        saveVideoUri(uri.toString())
    }

    /**
     * Get saved video URI
     * @return Saved video URI, or null if not set
     */
    fun getSavedVideoUri(): Uri? {
        return prefs.getString(KEY_VIDEO_URI, null)?.let { Uri.parse(it) }
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
    }
}
