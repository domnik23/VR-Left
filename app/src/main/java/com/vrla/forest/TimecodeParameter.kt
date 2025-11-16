package com.vrla.forest

import org.json.JSONArray
import org.json.JSONObject

/**
 * Data classes for timecode-based parameter and overlay configuration
 *
 * Allows loading JSON files that define time-based changes to video parameters
 * and overlay text during video playback.
 *
 * Example JSON structure:
 * ```json
 * {
 *   "videoFile": "forest_jog.mp4",
 *   "timecodes": [
 *     {
 *       "timeRange": "00:00-02:30",
 *       "parameters": {
 *         "minSpeed": 0.4,
 *         "maxSpeed": 1.5,
 *         "videoRotation": -90
 *       },
 *       "overlay": {
 *         "text": "Welcome! Let's start jogging! 🏃",
 *         "position": "center"
 *       }
 *     }
 *   ]
 * }
 * ```
 */

/**
 * Root configuration loaded from JSON file
 *
 * @property videoFile The video filename this configuration applies to
 * @property timecodes List of time-based configurations
 */
data class TimecodeConfig(
    val videoFile: String,
    val timecodes: List<TimecodeEntry>
) {
    companion object {
        /**
         * Parse TimecodeConfig from JSON string
         *
         * @param json JSON string containing the configuration
         * @return Parsed TimecodeConfig
         * @throws org.json.JSONException if JSON is malformed
         */
        fun fromJson(json: String): TimecodeConfig {
            val root = JSONObject(json)
            val videoFile = root.optString("videoFile", "")
            val timecodesArray = root.getJSONArray("timecodes")

            val timecodes = mutableListOf<TimecodeEntry>()
            for (i in 0 until timecodesArray.length()) {
                val entry = timecodesArray.getJSONObject(i)
                timecodes.add(TimecodeEntry.fromJson(entry))
            }

            return TimecodeConfig(videoFile, timecodes)
        }
    }
}

/**
 * Single timecode entry with time range and associated configuration
 *
 * @property startTimeMs Start time in milliseconds
 * @property endTimeMs End time in milliseconds
 * @property parameters Optional video/speed parameters to apply
 * @property overlay Optional text overlay to display
 */
data class TimecodeEntry(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val parameters: VideoParameters?,
    val overlay: OverlayConfig?
) {
    /**
     * Check if given time falls within this entry's time range
     *
     * @param currentTimeMs Current video playback time in milliseconds
     * @return true if time is within range [startTimeMs, endTimeMs]
     */
    fun isActive(currentTimeMs: Long): Boolean {
        return currentTimeMs in startTimeMs..endTimeMs
    }

    companion object {
        /**
         * Parse TimecodeEntry from JSON object
         *
         * @param json JSONObject containing the entry
         * @return Parsed TimecodeEntry
         */
        fun fromJson(json: JSONObject): TimecodeEntry {
            // Parse time range (format: "MM:SS-MM:SS" or "HH:MM:SS-HH:MM:SS")
            val timeRange = json.optString("timeRange", "00:00-00:00")
            val (startMs, endMs) = parseTimeRange(timeRange)

            // Parse optional parameters
            val parameters = if (json.has("parameters")) {
                VideoParameters.fromJson(json.getJSONObject("parameters"))
            } else {
                null
            }

            // Parse optional overlay
            val overlay = if (json.has("overlay")) {
                OverlayConfig.fromJson(json.getJSONObject("overlay"))
            } else {
                null
            }

            return TimecodeEntry(startMs, endMs, parameters, overlay)
        }

        /**
         * Parse time range string to milliseconds
         *
         * Supports formats:
         * - "MM:SS-MM:SS" (e.g., "02:30-05:00")
         * - "HH:MM:SS-HH:MM:SS" (e.g., "01:02:30-01:05:00")
         *
         * @param timeRange Time range string
         * @return Pair of (startMs, endMs)
         */
        private fun parseTimeRange(timeRange: String): Pair<Long, Long> {
            val parts = timeRange.split("-")
            if (parts.size != 2) {
                return Pair(0L, 0L)
            }

            val startMs = parseTimeToMs(parts[0].trim())
            val endMs = parseTimeToMs(parts[1].trim())

            return Pair(startMs, endMs)
        }

        /**
         * Parse time string to milliseconds
         *
         * Supports:
         * - "MM:SS" (e.g., "02:30" = 150000ms)
         * - "HH:MM:SS" (e.g., "01:02:30" = 3750000ms)
         *
         * @param time Time string
         * @return Time in milliseconds
         */
        private fun parseTimeToMs(time: String): Long {
            val parts = time.split(":")

            return when (parts.size) {
                2 -> {
                    // MM:SS
                    val minutes = parts[0].toLongOrNull() ?: 0
                    val seconds = parts[1].toLongOrNull() ?: 0
                    (minutes * 60 + seconds) * 1000
                }
                3 -> {
                    // HH:MM:SS
                    val hours = parts[0].toLongOrNull() ?: 0
                    val minutes = parts[1].toLongOrNull() ?: 0
                    val seconds = parts[2].toLongOrNull() ?: 0
                    (hours * 3600 + minutes * 60 + seconds) * 1000
                }
                else -> 0L
            }
        }
    }
}

/**
 * Video playback and speed parameters
 *
 * All fields are optional - only specified fields will override AppConfig defaults.
 *
 * @property minSpeed Minimum playback speed (idle), range: 0.0-1.0
 * @property minSpeedMoving Minimum speed when moving, range: 0.0-1.0
 * @property maxSpeed Maximum playback speed (jogging), range: 1.0-2.0
 * @property videoRotation Video orientation correction in degrees
 * @property videoVolume Audio volume, range: 0.0-1.0
 * @property caloriesPerKm Calorie burn rate, range: 30-130 kcal/km
 * @property averageStrideLength Stride length in meters, range: 0.5-1.0
 */
data class VideoParameters(
    val minSpeed: Float?,
    val minSpeedMoving: Float?,
    val maxSpeed: Float?,
    val videoRotation: Float?,
    val videoVolume: Float?,
    val caloriesPerKm: Int?,
    val averageStrideLength: Float?
) {
    companion object {
        /**
         * Parse VideoParameters from JSON object
         *
         * @param json JSONObject containing parameters
         * @return Parsed VideoParameters
         */
        fun fromJson(json: JSONObject): VideoParameters {
            return VideoParameters(
                minSpeed = if (json.has("minSpeed")) json.getDouble("minSpeed").toFloat() else null,
                minSpeedMoving = if (json.has("minSpeedMoving")) json.getDouble("minSpeedMoving").toFloat() else null,
                maxSpeed = if (json.has("maxSpeed")) json.getDouble("maxSpeed").toFloat() else null,
                videoRotation = if (json.has("videoRotation")) json.getDouble("videoRotation").toFloat() else null,
                videoVolume = if (json.has("videoVolume")) json.getDouble("videoVolume").toFloat() else null,
                caloriesPerKm = if (json.has("caloriesPerKm")) json.getInt("caloriesPerKm") else null,
                averageStrideLength = if (json.has("averageStrideLength")) json.getDouble("averageStrideLength").toFloat() else null
            )
        }
    }

    /**
     * Apply these parameters to AppConfig
     *
     * Only non-null parameters will override AppConfig values.
     */
    fun applyToAppConfig() {
        minSpeed?.let { AppConfig.minSpeed = it }
        minSpeedMoving?.let { AppConfig.minSpeedMoving = it }
        maxSpeed?.let { AppConfig.maxSpeed = it }
        videoRotation?.let { AppConfig.videoRotation = it }
        videoVolume?.let { AppConfig.videoVolume = it }
        caloriesPerKm?.let { AppConfig.caloriesPerKm = it }
        averageStrideLength?.let { AppConfig.averageStrideLength = it }
    }
}

/**
 * Text overlay configuration
 *
 * @property text The text to display (supports emojis)
 * @property position Vertical position: "top", "center", or "bottom"
 * @property textSize Text size in SP (default: 18)
 * @property textColor Text color in hex format (default: "#FFFFFF")
 * @property backgroundColor Background color in hex with alpha (default: "#CC000000")
 */
data class OverlayConfig(
    val text: String,
    val position: String = "center",
    val textSize: Float = 18f,
    val textColor: String = "#FFFFFF",
    val backgroundColor: String = "#CC000000"
) {
    companion object {
        /**
         * Parse OverlayConfig from JSON object
         *
         * @param json JSONObject containing overlay configuration
         * @return Parsed OverlayConfig
         */
        fun fromJson(json: JSONObject): OverlayConfig {
            return OverlayConfig(
                text = json.getString("text"),
                position = json.optString("position", "center"),
                textSize = json.optDouble("textSize", 18.0).toFloat(),
                textColor = json.optString("textColor", "#FFFFFF"),
                backgroundColor = json.optString("backgroundColor", "#CC000000")
            )
        }
    }
}
