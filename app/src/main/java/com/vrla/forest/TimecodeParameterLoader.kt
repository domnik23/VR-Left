package com.vrla.forest

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Loader and manager for timecode-based parameter configurations
 *
 * Loads JSON files that define time-based video parameters and overlays.
 * Provides thread-safe access to current parameters based on video playback time.
 *
 * ## File Location
 * Parameter files are searched in this order:
 * 1. Same folder as the video file (if video URI is provided)
 * 2. Internal storage: `/Android/data/com.vrla.forest/files/parameters/`
 * 3. Assets folder: `assets/parameters/`
 *
 * ## File Naming Convention
 * Parameter files should match the video filename with `.json` extension:
 * - Video: `forest_jog.mp4` → Parameters: `forest_jog.json`
 *
 * ## Usage Example
 * ```kotlin
 * val loader = TimecodeParameterLoader(context)
 * loader.loadParametersForVideo("forest_jog.mp4", videoUri)
 *
 * // In rendering loop
 * val currentTimeMs = mediaPlayer.currentPosition.toLong()
 * loader.updateForTime(currentTimeMs)
 * val overlay = loader.getCurrentOverlay()
 * ```
 *
 * @property context Android application context
 */
class TimecodeParameterLoader(private val context: Context) {

    private var config: TimecodeConfig? = null
    private var currentEntry: TimecodeEntry? = null
    private val lock = Any()

    companion object {
        private const val TAG = "TimecodeParamLoader"
        private const val PARAMETERS_DIR = "parameters"
    }

    /**
     * Load parameter configuration for a specific video file
     *
     * Searches for parameter file in this order:
     * 1. Same folder as the video file (if videoUri is provided)
     * 2. Internal storage: `/Android/data/com.vrla.forest/files/parameters/[videoname].json`
     * 3. Assets folder: `assets/parameters/[videoname].json`
     *
     * @param videoFileName Name of the video file (e.g., "forest_jog.mp4")
     * @param videoUri Optional URI of the video file to search in its folder
     * @return true if parameters were loaded successfully, false otherwise
     */
    fun loadParametersForVideo(videoFileName: String, videoUri: Uri? = null): Boolean {
        synchronized(lock) {
            // Extract base name without extension
            val baseName = videoFileName.substringBeforeLast(".")
            val paramFileName = "$baseName.json"

            // Try loading from video folder first (if URI provided)
            if (videoUri != null) {
                if (tryLoadFromVideoFolder(videoUri, paramFileName)) {
                    return true
                }
            }

            // Try loading from internal storage
            val internalFile = File(context.getExternalFilesDir(PARAMETERS_DIR), paramFileName)
            if (internalFile.exists()) {
                return loadFromFile(internalFile)
            }

            // Fall back to assets
            return loadFromAssets(paramFileName)
        }
    }

    /**
     * Try to load parameter file from the same folder as the video
     *
     * @param videoUri URI of the video file
     * @param paramFileName Name of the parameter file (e.g., "forest_jog.json")
     * @return true if file was found and loaded successfully, false otherwise
     */
    private fun tryLoadFromVideoFolder(videoUri: Uri, paramFileName: String): Boolean {
        return try {
            // Try to load via ContentResolver for content:// URIs
            if (videoUri.scheme == "content") {
                // Use DocumentFile to access the video's parent directory
                val videoDocFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, videoUri)
                if (videoDocFile != null && videoDocFile.parentFile != null) {
                    val parentDir = videoDocFile.parentFile
                    val paramFile = parentDir?.findFile(paramFileName)

                    if (paramFile != null && paramFile.exists()) {
                        // Read from URI
                        val inputStream = context.contentResolver.openInputStream(paramFile.uri)
                        if (inputStream != null) {
                            val json = inputStream.bufferedReader().use { it.readText() }
                            config = TimecodeConfig.fromJson(json)
                            Log.i(TAG, "Loaded parameters from video folder: ${paramFile.uri}")
                            Log.i(TAG, "Configuration contains ${config?.timecodes?.size ?: 0} timecode entries")
                            return true
                        }
                    }
                }
            }

            // Try to extract file path for file:// URIs
            if (videoUri.scheme == "file") {
                val videoFile = File(videoUri.path ?: return false)
                val videoFolder = videoFile.parentFile
                if (videoFolder != null && videoFolder.exists()) {
                    val paramFile = File(videoFolder, paramFileName)
                    if (paramFile.exists()) {
                        return loadFromFile(paramFile)
                    }
                }
            }

            false
        } catch (e: Exception) {
            Log.w(TAG, "Could not load from video folder: ${e.message}")
            false
        }
    }

    /**
     * Load parameter file from internal storage
     *
     * @param file File object pointing to the JSON parameter file
     * @return true if loaded successfully, false otherwise
     */
    private fun loadFromFile(file: File): Boolean {
        return try {
            val json = file.readText()
            config = TimecodeConfig.fromJson(json)
            Log.i(TAG, "Loaded parameters from file: ${file.absolutePath}")
            Log.i(TAG, "Configuration contains ${config?.timecodes?.size ?: 0} timecode entries")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error reading parameter file: ${file.absolutePath}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing parameter file: ${file.absolutePath}", e)
            false
        }
    }

    /**
     * Load parameter file from app assets
     *
     * @param fileName Name of the JSON file in assets/parameters/ folder
     * @return true if loaded successfully, false otherwise
     */
    private fun loadFromAssets(fileName: String): Boolean {
        return try {
            val assetPath = "$PARAMETERS_DIR/$fileName"
            val json = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            config = TimecodeConfig.fromJson(json)
            Log.i(TAG, "Loaded parameters from assets: $assetPath")
            Log.i(TAG, "Configuration contains ${config?.timecodes?.size ?: 0} timecode entries")
            true
        } catch (e: IOException) {
            Log.w(TAG, "No parameter file found in assets: $PARAMETERS_DIR/$fileName")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing parameter file from assets: $fileName", e)
            false
        }
    }

    /**
     * Update current state based on video playback time
     *
     * Finds the timecode entry matching the current time and applies its parameters.
     * Should be called regularly during video playback (e.g., every frame or every second).
     *
     * @param currentTimeMs Current video playback time in milliseconds
     * @return true if parameters were updated, false if no matching entry found
     */
    fun updateForTime(currentTimeMs: Long): Boolean {
        synchronized(lock) {
            val cfg = config ?: return false

            // Find active entry for current time
            val activeEntry = cfg.timecodes.firstOrNull { it.isActive(currentTimeMs) }

            // Check if we switched to a different entry
            if (activeEntry != currentEntry) {
                currentEntry = activeEntry

                if (activeEntry != null) {
                    // Apply parameters to AppConfig
                    activeEntry.parameters?.applyToAppConfig()

                    Log.d(TAG, "Applied timecode entry at ${formatTime(currentTimeMs)}: " +
                            "range ${formatTime(activeEntry.startTimeMs)}-${formatTime(activeEntry.endTimeMs)}")

                    return true
                } else {
                    Log.d(TAG, "No active timecode entry at ${formatTime(currentTimeMs)}")
                }
            }

            return activeEntry != null
        }
    }

    /**
     * Get the current overlay configuration
     *
     * @return Current OverlayConfig if an entry is active and has an overlay, null otherwise
     */
    fun getCurrentOverlay(): OverlayConfig? {
        synchronized(lock) {
            return currentEntry?.overlay
        }
    }

    /**
     * Get the current video parameters
     *
     * @return Current VideoParameters if an entry is active and has parameters, null otherwise
     */
    fun getCurrentParameters(): VideoParameters? {
        synchronized(lock) {
            return currentEntry?.parameters
        }
    }

    /**
     * Check if any parameter configuration is loaded
     *
     * @return true if a configuration is loaded, false otherwise
     */
    fun hasConfig(): Boolean {
        synchronized(lock) {
            return config != null
        }
    }

    /**
     * Clear loaded configuration
     *
     * Useful when switching videos or resetting state.
     */
    fun clear() {
        synchronized(lock) {
            config = null
            currentEntry = null
            Log.i(TAG, "Cleared parameter configuration")
        }
    }

    /**
     * Get all timecode entries for debugging
     *
     * @return List of all timecode entries, or empty list if no config loaded
     */
    fun getAllEntries(): List<TimecodeEntry> {
        synchronized(lock) {
            return config?.timecodes ?: emptyList()
        }
    }

    /**
     * Format milliseconds to human-readable time string
     *
     * @param ms Time in milliseconds
     * @return Formatted string "HH:MM:SS" or "MM:SS"
     */
    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Get directory path for parameter files
     *
     * @return File object pointing to the parameters directory in internal storage
     */
    fun getParametersDirectory(): File? {
        return context.getExternalFilesDir(PARAMETERS_DIR)
    }
}
