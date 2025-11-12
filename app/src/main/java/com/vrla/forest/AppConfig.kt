package com.vrla.forest

object AppConfig {
    // Video Mode
    var stereoMode = false  // false = Mono (both eyes see same), true = Stereo (3D depth)

    // Speed Settings
    var minSpeed = 0.3f          // No movement
    var minSpeedMoving = 0.7f    // Slow walking
    var maxSpeed = 1.5f          // Fast jogging

    // VR Settings
    var ipd = 0.064f  // Interpupillary distance in meters (64mm)

    // Fitness Tracking
    var averageStrideLength = 0.75f  // meters
    var caloriesPerKm = 60           // kcal per kilometer

    // Step Detection
    var baseStepsPerMinute = 60
    var stepWindowMs = 10000L  // 10 seconds
}
