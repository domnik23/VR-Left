package com.vrla.forest

import android.content.Context

/**
 * Global application configuration
 *
 * Thread-safe singleton that stores all app settings. Settings are loaded from
 * SharedPreferences on app start and can be modified via SettingsActivity.
 *
 * ## Configuration Categories
 *
 * 1. **Video Settings**: Stereo/mono mode, volume, video orientation
 * 2. **Speed Settings**: Playback speed thresholds for different movement levels
 * 3. **VR Settings**: IPD (eye separation) for stereo rendering
 * 4. **Fitness Tracking**: Stride length and calorie estimation
 * 5. **Step Detection**: Time window for step frequency analysis
 *
 * ## Thread Safety
 *
 * All properties are marked @Volatile for visibility across threads.
 * The loadFromPreferences() method uses synchronized block for atomic updates.
 *
 * @see SettingsActivity for UI to modify these values
 * @see StepController for speed calculation using these thresholds
 */
object AppConfig {

    // ============================================================
    // VIDEO SETTINGS
    // ============================================================

    /**
     * Video display mode
     *
     * - **false (Mono)**: Both eyes see the same full video (no 3D depth)
     * - **true (Stereo)**: Side-by-side stereo video with 3D depth effect
     *
     * Stereo mode uses texture scaling:
     * - Left eye sees left half of video (U: 0.0 - 0.5)
     * - Right eye sees right half of video (U: 0.5 - 1.0)
     *
     * Default: false (Mono) - works with any 360° video
     */
    @Volatile var stereoMode = false

    /**
     * Video playback volume
     *
     * Range: 0.0 (mute) to 1.0 (full volume)
     * Default: 0.5 (50%)
     *
     * Applied to MediaPlayer.setVolume() for both left/right channels.
     */
    @Volatile var videoVolume = 0.5f

    /**
     * Video orientation correction offset
     *
     * User-adjustable rotation offset applied on top of the base 90° rotation
     * (which is required due to landscape sensor remapping).
     *
     * Default: 0° (no additional rotation)
     * Range: -90° to +180°
     *
     * This rotates the 360° sphere around X-axis (pitch) before head tracking is applied.
     */
    @Volatile var videoRotation = 0f

    // ============================================================
    // SPEED SETTINGS
    // ============================================================

    /**
     * Minimum playback speed (idle/no movement)
     *
     * Used when:
     * - User is completely still (< 10 steps/min)
     * - No steps detected yet
     *
     * Range: 0.0 - 1.0
     * Default: 0.4x (40% speed)
     *
     * @see StepController.updateSpeed for speed calculation logic
     */
    @Volatile var minSpeed = 0.4f

    /**
     * Minimum playback speed when moving
     *
     * Used when user just started moving (10 steps/min).
     * Speed linearly interpolates from this value to maxSpeed as steps increase.
     *
     * Range: 0.0 - 1.0
     * Default: 0.7x (70% speed) - slow walking
     *
     * @see StepController.updateSpeed for interpolation formula
     */
    @Volatile var minSpeedMoving = 0.7f

    /**
     * Maximum playback speed (fast jogging)
     *
     * Used when user is jogging fast (≥ 120 steps/min).
     *
     * Range: 1.0 - 2.0
     * Default: 1.5x (150% speed)
     *
     * Note: MediaPlayer supports speeds from 0.1x to 3.0x, but higher speeds
     * may cause audio distortion or video stuttering.
     */
    @Volatile var maxSpeed = 1.5f

    // ============================================================
    // VR SETTINGS
    // ============================================================

    /**
     * IPD - Inter-Pupillary Distance
     *
     * Distance between the centers of the pupils, used for stereo 3D rendering.
     * Each eye's view is offset by ±IPD/2 in camera space.
     *
     * Range: 0.05 - 0.08 meters (50mm - 80mm)
     * Default: 0.064m (64mm) - average adult IPD
     *
     * How to measure your IPD:
     * 1. Stand in front of mirror with ruler
     * 2. Measure distance between pupil centers
     * 3. Convert to meters (e.g., 64mm = 0.064m)
     *
     * Incorrect IPD causes:
     * - Too low: Flattened 3D effect, eye strain
     * - Too high: Exaggerated depth, cross-eye effect
     *
     * @see VRRenderer.setupViewMatrices for IPD application
     */
    @Volatile var ipd = 0.064f

    // ============================================================
    // FITNESS TRACKING
    // ============================================================

    /**
     * Average stride length per step
     *
     * Used to calculate distance traveled: distance = steps × strideLength
     *
     * Range: 0.5 - 1.0 meters
     * Default: 0.75m (75cm) - average adult stride
     *
     * Typical values:
     * - Walking: 0.6 - 0.8m
     * - Jogging: 0.8 - 1.2m
     * - Running: 1.2 - 1.8m
     *
     * How to measure your stride:
     * 1. Walk/jog 10 steps at your normal pace
     * 2. Measure total distance covered
     * 3. Divide by 10
     *
     * @see StepController.getEstimatedDistance
     */
    @Volatile var averageStrideLength = 0.75f

    /**
     * Calorie burn rate per kilometer
     *
     * Used for rough calorie estimation: calories = distance × caloriesPerKm
     *
     * Range: 30 - 130 kcal/km
     * Default: 60 kcal/km (moderate walking)
     *
     * Typical values:
     * - Slow walking: 40-50 kcal/km
     * - Moderate walking: 50-70 kcal/km
     * - Jogging: 70-100 kcal/km
     * - Running: 100-130 kcal/km
     *
     * Note: Actual calories depend on body weight, terrain, intensity.
     * This is a simplified estimation for motivation tracking.
     *
     * @see StepController.getEstimatedCalories
     */
    @Volatile var caloriesPerKm = 60

    // ============================================================
    // STEP DETECTION
    // ============================================================

    /**
     * Base steps per minute for normal walking
     *
     * Currently unused - kept for potential future step counting algorithms.
     *
     * Default: 60 steps/min
     */
    @Volatile var baseStepsPerMinute = 60

    /**
     * Sliding time window for step frequency analysis
     *
     * StepController tracks steps within this time window to calculate steps/min.
     * Older steps are discarded when they fall outside the window.
     *
     * Default: 10000ms (10 seconds)
     *
     * Trade-offs:
     * - Shorter window: More responsive to speed changes, but noisier
     * - Longer window: Smoother speed changes, but slower response
     *
     * @see StepController.addStep for window sliding logic
     */
    @Volatile var stepWindowMs = 10000L

    // ============================================================
    // PERSISTENCE
    // ============================================================

    // Lock for thread-safe preference loading
    private val lock = Any()

    /**
     * Load all settings from SharedPreferences
     *
     * Called on app startup (MainActivity.onCreate) and when returning from
     * SettingsActivity (MainActivity.onResume).
     *
     * Thread-safe: Uses synchronized block to ensure atomic updates of all settings.
     *
     * SharedPreferences keys:
     * - "video_volume": int 0-100 (converted to float 0.0-1.0)
     * - "stereo_mode": boolean
     * - "video_rotation": float degrees
     * - "ipd": float meters
     * - "stride_length": float meters
     * - "calories_per_km": int kcal/km
     * - "min_speed": float 0.0-1.0
     * - "min_speed_moving": float 0.0-1.0
     * - "max_speed": float 1.0-2.0
     *
     * @param context Android context for accessing SharedPreferences
     */
    fun loadFromPreferences(context: Context) {
        synchronized(lock) {
            val prefs = context.getSharedPreferences("VRLAPrefs", Context.MODE_PRIVATE)

            // Video settings
            videoVolume = prefs.getInt("video_volume", 50) / 100f
            stereoMode = prefs.getBoolean("stereo_mode", false)
            videoRotation = prefs.getFloat("video_rotation", 0f)

            // VR settings
            ipd = prefs.getFloat("ipd", 0.064f)

            // Fitness tracking
            averageStrideLength = prefs.getFloat("stride_length", 0.75f)
            caloriesPerKm = prefs.getInt("calories_per_km", 60)

            // Speed settings
            minSpeed = prefs.getFloat("min_speed", 0.4f)
            minSpeedMoving = prefs.getFloat("min_speed_moving", 0.7f)
            maxSpeed = prefs.getFloat("max_speed", 1.5f)
        }
    }
}
