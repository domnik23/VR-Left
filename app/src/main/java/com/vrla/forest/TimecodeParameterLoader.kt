package com.vrla.forest

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
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

        // Cache for parsed parameter files (filename -> config)
        private val configCache = mutableMapOf<String, TimecodeConfig>()

        /**
         * Clear the parameter file cache
         * Call this if parameter files have been modified
         */
        fun clearCache() {
            synchronized(configCache) {
                configCache.clear()
                Log.d(TAG, "Parameter cache cleared")
            }
        }
    }

    /**
     * Load parameter configuration for a specific video file
     *
     * Searches for parameter file in this order:
     * 1. Same folder as the video file (if videoUri is provided)
     * 2. Video folder tree (if folderTreeUri is provided)
     * 3. Internal storage: `/Android/data/com.vrla.forest/files/parameters/[videoname].json`
     * 4. Assets folder: `assets/parameters/[videoname].json`
     *
     * @param videoFileName Name of the video file (e.g., "forest_jog.mp4")
     * @param videoUri Optional URI of the video file to search in its folder
     * @param folderTreeUri Optional tree URI of the folder containing videos
     * @return true if parameters were loaded successfully, false otherwise
     */
    fun loadParametersForVideo(videoFileName: String, videoUri: Uri? = null, folderTreeUri: Uri? = null): Boolean {
        synchronized(lock) {
            // Extract base name without extension
            val baseName = videoFileName.substringBeforeLast(".")
            val paramFileName = "$baseName.json"

            // Check cache first
            synchronized(configCache) {
                configCache[paramFileName]?.let { cachedConfig ->
                    config = cachedConfig
                    Log.d(TAG, "Loaded parameters from cache: $paramFileName")
                    return true
                }
            }

            // Try loading from video folder first (if URI provided)
            if (videoUri != null) {
                if (tryLoadFromVideoFolder(videoUri, paramFileName)) {
                    cacheConfig(paramFileName)
                    return true
                }
            }

            // Try loading from folder tree URI (if provided)
            if (folderTreeUri != null) {
                if (tryLoadFromFolderTree(folderTreeUri, paramFileName)) {
                    cacheConfig(paramFileName)
                    return true
                }
            }

            // Try loading from internal storage
            val internalFile = File(context.getExternalFilesDir(PARAMETERS_DIR), paramFileName)
            if (internalFile.exists()) {
                val result = loadFromFile(internalFile)
                if (result) cacheConfig(paramFileName)
                return result
            }

            // Fall back to assets
            val result = loadFromAssets(paramFileName)
            if (result) cacheConfig(paramFileName)
            return result
        }
    }

    /**
     * Cache the current configuration
     *
     * @param paramFileName Name of the parameter file to use as cache key
     */
    private fun cacheConfig(paramFileName: String) {
        config?.let { currentConfig ->
            synchronized(configCache) {
                configCache[paramFileName] = currentConfig
                Log.d(TAG, "Cached parameters: $paramFileName")
            }
        }
    }

    /**
     * Try to load parameter file from the same folder as the video
     *
     * Uses DocumentsContract API to search for files in the same directory
     * as the video, even with content:// URIs from MediaStore.
     *
     * @param videoUri URI of the video file
     * @param paramFileName Name of the parameter file (e.g., "forest_jog.json")
     * @return true if file was found and loaded successfully, false otherwise
     */
    private fun tryLoadFromVideoFolder(videoUri: Uri, paramFileName: String): Boolean {
        return try {
            // Try to load via DocumentsContract for content:// URIs
            if (videoUri.scheme == "content") {
                Log.d(TAG, "Attempting to find parameter file in video folder via DocumentsContract")

                // Try to find the JSON file by querying the same directory
                val foundUri = findFileInSameDirectory(videoUri, paramFileName)

                if (foundUri != null) {
                    Log.d(TAG, "Found parameter file in same directory: $foundUri")
                    val inputStream = context.contentResolver.openInputStream(foundUri)
                    if (inputStream != null) {
                        val json = inputStream.bufferedReader().use { it.readText() }
                        config = TimecodeConfig.fromJson(json)
                        Log.i(TAG, "Loaded parameters from video folder: $foundUri")
                        Log.i(TAG, "Configuration contains ${config?.timecodes?.size ?: 0} timecode entries")
                        return true
                    }
                } else {
                    Log.d(TAG, "No parameter file found in video folder")
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
            Log.w(TAG, "Could not load from video folder: ${e.message}", e)
            false
        }
    }

    /**
     * Find a file in the same directory as a given content:// URI
     *
     * Uses DocumentsContract to query the parent directory and search
     * for a file with the specified name.
     *
     * @param uri URI of a file (e.g., video file)
     * @param fileName Name of the file to find in the same directory
     * @return URI of the found file, or null if not found
     */
    private fun findFileInSameDirectory(uri: Uri, fileName: String): Uri? {
        var cursor: Cursor? = null
        try {
            // Check if this is a document URI
            if (!DocumentsContract.isDocumentUri(context, uri)) {
                Log.d(TAG, "URI is not a document URI, trying MediaStore query")
                return findFileViaMediaStoreQuery(uri, fileName)
            }

            // Get the document ID of the video
            val documentId = DocumentsContract.getDocumentId(uri)
            Log.d(TAG, "Video document ID: $documentId")

            // Try to extract parent directory from document ID
            // For MediaStore documents, the ID format is usually: "primary:path/to/file"
            val parentPath = documentId.substringBeforeLast('/', "")
            if (parentPath.isEmpty()) {
                Log.d(TAG, "Could not extract parent path from document ID")
                return findFileViaMediaStoreQuery(uri, fileName)
            }

            Log.d(TAG, "Parent path: $parentPath")

            // Build the document ID for the JSON file in the same directory
            val jsonDocumentId = "$parentPath/$fileName"
            Log.d(TAG, "Looking for JSON with document ID: $jsonDocumentId")

            // Build a document URI for the JSON file
            val authority = uri.authority ?: return null
            val jsonUri = DocumentsContract.buildDocumentUri(authority, jsonDocumentId)

            // Check if the JSON file exists by trying to query it
            cursor = context.contentResolver.query(
                jsonUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null
            )

            if (cursor != null && cursor.moveToFirst()) {
                Log.d(TAG, "Successfully found JSON file via document URI")
                cursor.close()
                return jsonUri
            }

            cursor?.close()
            cursor = null

            // Fallback: Try MediaStore query
            Log.d(TAG, "Document URI approach failed, trying MediaStore query")
            return findFileViaMediaStoreQuery(uri, fileName)

        } catch (e: Exception) {
            Log.w(TAG, "Error finding file in same directory: ${e.message}", e)
            return null
        } finally {
            cursor?.close()
        }
    }

    /**
     * Find a file via MediaStore by looking in the same DATA path
     *
     * This is a fallback method that queries MediaStore to find files
     * in the same directory.
     *
     * @param videoUri URI of the video file
     * @param fileName Name of the file to find
     * @return URI of the found file, or null if not found
     */
    private fun findFileViaMediaStoreQuery(videoUri: Uri, fileName: String): Uri? {
        var cursor: Cursor? = null
        return try {
            Log.d(TAG, "Querying MediaStore for: $fileName")

            cursor = context.contentResolver.query(
                videoUri,
                arrayOf(android.provider.MediaStore.Video.Media.DATA),
                null, null, null
            ) ?: run {
                Log.d(TAG, "MediaStore query returned null")
                return null
            }

            if (!cursor.moveToFirst()) {
                Log.d(TAG, "No data in cursor")
                return null
            }

            val dataIndex = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.DATA)
            if (dataIndex < 0) {
                Log.d(TAG, "DATA column not accessible")
                return null
            }

            val videoPath = cursor.getString(dataIndex) ?: run {
                Log.d(TAG, "Scoped Storage: cannot access video path")
                return null
            }

            cursor.close()

            val parentDir = File(videoPath).parentFile ?: run {
                Log.d(TAG, "Cannot access parent directory")
                return null
            }

            if (!parentDir.exists()) {
                Log.d(TAG, "Parent directory does not exist")
                return null
            }

            val jsonFile = File(parentDir, fileName)
            if (jsonFile.exists() && jsonFile.canRead()) {
                Log.i(TAG, "Found JSON file: ${jsonFile.absolutePath}")
                Uri.fromFile(jsonFile)
            } else {
                Log.d(TAG, "JSON file not accessible")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying MediaStore: ${e.message}", e)
            null
        } finally {
            cursor?.close()
        }
    }

    /**
     * Try to load parameter file from a folder tree URI
     *
     * This method is used when the user has selected a video folder via
     * ACTION_OPEN_DOCUMENT_TREE, giving the app access to all files in that folder.
     *
     * @param folderTreeUri Tree URI of the folder containing videos
     * @param paramFileName Name of the parameter file (e.g., "forest_jog.json")
     * @return true if file was found and loaded successfully, false otherwise
     */
    private fun tryLoadFromFolderTree(folderTreeUri: Uri, paramFileName: String): Boolean {
        var cursor: Cursor? = null
        return try {
            Log.d(TAG, "Searching for parameter file in folder tree: $folderTreeUri")

            // Build URI for querying children of the tree
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                folderTreeUri,
                DocumentsContract.getTreeDocumentId(folderTreeUri)
            )

            // Query all children to find the JSON file
            cursor = context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(
                        cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    )

                    // Check if this is the parameter file we're looking for
                    if (displayName == paramFileName) {
                        val documentId = cursor.getString(
                            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        )
                        val jsonUri = DocumentsContract.buildDocumentUriUsingTree(folderTreeUri, documentId)

                        Log.d(TAG, "Found parameter file in tree: $jsonUri")

                        // Load the JSON file
                        val inputStream = context.contentResolver.openInputStream(jsonUri)
                        if (inputStream != null) {
                            val json = inputStream.bufferedReader().use { it.readText() }
                            config = TimecodeConfig.fromJson(json)
                            Log.i(TAG, "Loaded parameters from folder tree: $jsonUri")
                            Log.i(TAG, "Configuration contains ${config?.timecodes?.size ?: 0} timecode entries")
                            return true
                        }
                    }
                }
            }

            Log.d(TAG, "Parameter file not found in folder tree")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error loading from folder tree: ${e.message}", e)
            false
        } finally {
            cursor?.close()
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
