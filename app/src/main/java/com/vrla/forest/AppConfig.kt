package com.vrla.forest

import android.content.Context

object AppConfig {
    // Video Settings
    var stereoMode = false  // false = Mono (both eyes see same), true = Stereo (3D depth)
    var videoVolume = 0.5f  // 0.0 to 1.0
    var videoRotation = -90f  // Rotation offset in degrees (fix for 90° CW issue)

    // Speed Settings
    var minSpeed = 0.4f          // No movement (0.0 - 1.0)
    var minSpeedMoving = 0.7f    // Slow walking (0.0 - 1.0)
    var maxSpeed = 1.5f          // Fast jogging (1.0 - 2.0)

    // VR Settings
    var ipd = 0.064f  // Interpupillary distance in meters (64mm)

    // Fitness Tracking
    var averageStrideLength = 0.75f  // meters
    var caloriesPerKm = 60           // kcal per kilometer

    // Step Detection
    var baseStepsPerMinute = 60
    var stepWindowMs = 10000L  // 10 seconds

    fun loadFromPreferences(context: Context) {
        val prefs = context.getSharedPreferences("VRLAPrefs", Context.MODE_PRIVATE)

        // Video
        videoVolume = prefs.getInt("video_volume", 50) / 100f
        stereoMode = prefs.getBoolean("stereo_mode", false)
        videoRotation = prefs.getFloat("video_rotation", -90f)

        // VR
        ipd = prefs.getFloat("ipd", 0.064f)

        // Fitness
        averageStrideLength = prefs.getFloat("stride_length", 0.75f)
        caloriesPerKm = prefs.getInt("calories_per_km", 60)

        // Speed
        minSpeed = prefs.getFloat("min_speed", 0.4f)
        minSpeedMoving = prefs.getFloat("min_speed_moving", 0.7f)
        maxSpeed = prefs.getFloat("max_speed", 1.5f)
    }
}
