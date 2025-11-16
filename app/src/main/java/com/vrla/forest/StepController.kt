package com.vrla.forest

import kotlin.math.max

/**
 * Step-based video playback speed controller
 *
 * Analyzes step frequency over a sliding time window to dynamically adjust video playback speed.
 * This creates a natural jogging/walking experience where the video speed matches your pace.
 *
 * ## Speed Calculation Algorithm
 *
 * The controller uses a sliding time window (default: 10 seconds) to track recent steps.
 * Speed is calculated based on steps per minute with linear interpolation:
 *
 * - **Idle** (< 10 steps/min): minSpeed (default 0.4x)
 * - **Walking** (10-120 steps/min): Linear interpolation from minSpeedMoving (0.7x) to maxSpeed (1.5x)
 * - **Jogging** (≥ 120 steps/min): maxSpeed (default 1.5x)
 *
 * ## Decay System
 *
 * When user stops moving (no steps for 3+ seconds), speed gradually decays to minSpeed over 5 seconds.
 * This prevents abrupt speed changes and feels more natural.
 *
 * ## Thread Safety
 *
 * All methods are synchronized on internal lock since they're called from:
 * - MainActivity sensor thread (addStep)
 * - UI update coroutine (getCurrentSpeed)
 *
 * @see AppConfig for speed threshold configuration
 */
class StepController {

    // Sliding window of step timestamps (milliseconds since epoch)
    private val stepTimestamps = mutableListOf<Long>()

    // Target speed calculated from step frequency (instant, not smoothed)
    private var targetSpeed = AppConfig.minSpeed

    // Smoothed speed that gradually transitions to targetSpeed
    // This is the actual speed returned to the video player
    private var smoothedSpeed = AppConfig.minSpeed

    // Lock for thread-safe access from sensor and UI threads
    private val lock = Any()

    /**
     * Register a new step event
     *
     * Called by MainActivity when STEP_COUNTER sensor fires.
     * Updates the sliding time window and recalculates playback speed.
     *
     * Thread-safe: Can be called from sensor event thread.
     */
    fun addStep() {
        synchronized(lock) {
            val currentTime = System.currentTimeMillis()
            stepTimestamps.add(currentTime)

            // Remove steps outside the sliding window (older than stepWindowMs)
            stepTimestamps.removeAll { it < currentTime - AppConfig.stepWindowMs }

            updateSpeed()
        }
    }

    /**
     * Calculate target playback speed based on current step frequency
     *
     * MUST be called within synchronized(lock) block!
     *
     * Speed calculation:
     * 1. Count steps within time window
     * 2. Calculate steps per minute
     * 3. Map to playback speed using linear interpolation
     *
     * Speed ranges:
     * - < 10 steps/min: Idle (minSpeed = 0.4x)
     * - 10-120 steps/min: Walking to jogging (linear interpolation)
     * - ≥ 120 steps/min: Fast jogging (maxSpeed = 1.5x)
     *
     * Note: This calculates the TARGET speed. The actual speed is smoothed
     * over time in getCurrentSpeed() based on speedSmoothingFactor.
     */
    private fun updateSpeed() {
        // Must be called within synchronized block
        if (stepTimestamps.isEmpty()) {
            targetSpeed = AppConfig.minSpeed
            return
        }

        // Calculate steps per minute from time window
        val windowSizeMinutes = AppConfig.stepWindowMs / 60000f
        val stepsInWindow = stepTimestamps.size
        val stepsPerMinute = stepsInWindow / windowSizeMinutes

        targetSpeed = when {
            stepsPerMinute < 10 -> {
                // No meaningful movement - use minimum speed
                AppConfig.minSpeed  // Default: 0.4x
            }
            stepsPerMinute >= 120 -> {
                // Fast jogging - use maximum speed
                AppConfig.maxSpeed  // Default: 1.5x
            }
            else -> {
                // Linear interpolation between minSpeedMoving and maxSpeed
                // At 10 steps/min: minSpeedMoving (0.7x)
                // At 120 steps/min: maxSpeed (1.5x)
                val progress = (stepsPerMinute - 10f) / 110f
                AppConfig.minSpeedMoving + progress * (AppConfig.maxSpeed - AppConfig.minSpeedMoving)
            }
        }
    }

    /**
     * Get current playback speed with smoothing and decay
     *
     * Returns the smoothed playback speed that gradually transitions to the target speed.
     * Also applies decay when user stops moving.
     *
     * Smoothing:
     * - Uses exponential smoothing: smoothedSpeed moves toward targetSpeed
     * - Speed controlled by speedSmoothingFactor (0.0 = very smooth, 1.0 = instant)
     * - Creates natural acceleration/deceleration feel
     *
     * Decay behavior (when stopped):
     * - First 3 seconds after last step: Normal smoothing continues
     * - Next 5 seconds: Linear decay from current speed to minSpeed
     * - After 8 seconds: Full decay to minSpeed
     *
     * Thread-safe: Can be called from UI thread.
     *
     * @return Playback speed (0.1x - 3.0x, typically 0.4x - 1.5x)
     */
    fun getCurrentSpeed(): Float {
        synchronized(lock) {
            if (stepTimestamps.isEmpty()) {
                // No steps yet - smoothly transition to minSpeed
                smoothedSpeed += (AppConfig.minSpeed - smoothedSpeed) * AppConfig.speedSmoothingFactor
                return smoothedSpeed
            }

            val timeSinceLastStep = System.currentTimeMillis() - stepTimestamps.last()

            // Determine the final target speed (with decay if stopped)
            val finalTarget = if (timeSinceLastStep > 3000) {
                // User stopped moving - apply decay over 5 seconds
                // decayFactor: 1.0 at 3s, 0.0 at 8s
                val decayFactor = max(0f, 1f - (timeSinceLastStep - 3000) / 5000f)

                // Interpolate between minSpeed and targetSpeed based on decay
                AppConfig.minSpeed + (targetSpeed - AppConfig.minSpeed) * decayFactor
            } else {
                // User is moving - use calculated target speed
                targetSpeed
            }

            // Smooth transition to final target using exponential smoothing
            // Formula: smoothed = smoothed + (target - smoothed) * factor
            // Higher factor = faster convergence to target
            smoothedSpeed += (finalTarget - smoothedSpeed) * AppConfig.speedSmoothingFactor

            return smoothedSpeed
        }
    }

    /**
     * Calculate estimated distance traveled based on total steps
     *
     * Uses average stride length from AppConfig (default 0.75m).
     *
     * @param totalSteps Total step count since session start
     * @return Distance in kilometers
     */
    fun getEstimatedDistance(totalSteps: Int): Float {
        return (totalSteps * AppConfig.averageStrideLength) / 1000f
    }

    /**
     * Calculate estimated calories burned based on total steps
     *
     * Uses simple formula: distance (km) × calories per km (default 60 kcal/km).
     * This is a rough estimate - actual calories depend on weight, terrain, intensity.
     *
     * @param totalSteps Total step count since session start
     * @return Estimated calories burned (kcal)
     */
    fun getEstimatedCalories(totalSteps: Int): Int {
        val distanceKm = getEstimatedDistance(totalSteps)
        return (distanceKm * AppConfig.caloriesPerKm).toInt()
    }

    /**
     * Reset controller state for new session
     *
     * Clears step history and resets speeds to minimum.
     * Called when user restarts the video (double-press Volume Down).
     */
    fun reset() {
        synchronized(lock) {
            stepTimestamps.clear()
            targetSpeed = AppConfig.minSpeed
            smoothedSpeed = AppConfig.minSpeed
        }
    }
}
