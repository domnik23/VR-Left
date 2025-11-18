package com.vrla.forest

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

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

    companion object {
        // Speed calculation thresholds
        private const val MIN_STEPS_PER_MINUTE = 10f  // Below this = idle
        private const val MAX_STEPS_PER_MINUTE = 120f // Above this = max speed

        // Decay timing when user stops moving
        private const val DECAY_START_MS = 3000L  // Start decay after 3 seconds
        private const val DECAY_DURATION_MS = 5000L // Full decay over 5 seconds
    }

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
     * 4. Apply acceleration curve for non-linear response
     *
     * Speed ranges:
     * - < 10 steps/min: Idle (minSpeed = 0.4x)
     * - 10-120 steps/min: Walking to jogging (linear or curved interpolation)
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
            stepsPerMinute < MIN_STEPS_PER_MINUTE -> {
                // No meaningful movement - use minimum speed
                AppConfig.minSpeed
            }
            stepsPerMinute >= MAX_STEPS_PER_MINUTE -> {
                // Fast jogging - use maximum speed
                AppConfig.maxSpeed
            }
            else -> {
                // Calculate linear progress (0.0 to 1.0)
                val range = MAX_STEPS_PER_MINUTE - MIN_STEPS_PER_MINUTE
                val linearProgress = (stepsPerMinute - MIN_STEPS_PER_MINUTE) / range

                // Apply acceleration curve for non-linear response
                val curvedProgress = applyAccelerationCurve(linearProgress)

                // Interpolate speed based on curved progress
                AppConfig.minSpeedMoving + curvedProgress * (AppConfig.maxSpeed - AppConfig.minSpeedMoving)
            }
        }
    }

    /**
     * Apply non-linear acceleration curve to progress value
     *
     * Creates a curve that is less sensitive around the middle (0.5 = 1.0x speed)
     * and more sensitive at the extremes. This makes it easier to maintain speeds
     * around 1.0x without constant fluctuation.
     *
     * Algorithm:
     * 1. Calculate distance from center (0.5)
     * 2. Apply power function: distance^exponent
     * 3. Reconstruct progress from modified distance
     *
     * For exponent = 1.0: Linear (no change)
     * For exponent > 1.0: Flatter around center, steeper at edges
     *
     * Example with exponent = 2.0:
     * - Input 0.0 -> Output 0.0 (no change)
     * - Input 0.25 -> Output 0.125 (slower acceleration)
     * - Input 0.5 -> Output 0.5 (no change at center)
     * - Input 0.75 -> Output 0.875 (slower acceleration)
     * - Input 1.0 -> Output 1.0 (no change)
     *
     * @param progress Linear progress value (0.0 to 1.0)
     * @return Curved progress value (0.0 to 1.0)
     */
    private fun applyAccelerationCurve(progress: Float): Float {
        if (AppConfig.accelerationCurve == 1.0f) {
            // Linear mode - no curve applied
            return progress
        }

        // Center point (corresponds to 1.0x speed)
        val center = 0.5f

        // Calculate distance from center
        val distance = abs(progress - center)

        // Apply power function to distance
        // Higher exponent = flatter curve around center
        val modifiedDistance = distance.pow(AppConfig.accelerationCurve)

        // Reconstruct progress from modified distance
        return if (progress < center) {
            center - modifiedDistance
        } else {
            center + modifiedDistance
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
            val finalTarget = if (timeSinceLastStep > DECAY_START_MS) {
                // User stopped moving - apply decay
                // decayFactor: 1.0 at DECAY_START_MS, 0.0 at (DECAY_START_MS + DECAY_DURATION_MS)
                val decayProgress = (timeSinceLastStep - DECAY_START_MS).toFloat() / DECAY_DURATION_MS
                val decayFactor = max(0f, 1f - decayProgress)

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
