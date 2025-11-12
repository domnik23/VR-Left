package com.vrla.forest

import kotlin.math.max

class StepController {

    private val stepTimestamps = mutableListOf<Long>()
    private var currentSpeed = AppConfig.minSpeed
    
    fun addStep() {
        val currentTime = System.currentTimeMillis()
        stepTimestamps.add(currentTime)
        stepTimestamps.removeAll { it < currentTime - AppConfig.stepWindowMs }
        updateSpeed()
    }

    private fun updateSpeed() {
        if (stepTimestamps.isEmpty()) {
            currentSpeed = AppConfig.minSpeed
            return
        }

        val windowSizeMinutes = AppConfig.stepWindowMs / 60000f
        val stepsInWindow = stepTimestamps.size
        val stepsPerMinute = stepsInWindow / windowSizeMinutes

        currentSpeed = when {
            stepsPerMinute < 10 -> AppConfig.minSpeed  // No movement (default: 0.4x)
            stepsPerMinute >= 120 -> AppConfig.maxSpeed  // Fast jogging (default: 1.5x)
            else -> {
                // Linear interpolation between minSpeedMoving (default: 0.7x at 10 steps/min) and maxSpeed (at 120 steps/min)
                val progress = (stepsPerMinute - 10f) / 110f
                AppConfig.minSpeedMoving + progress * (AppConfig.maxSpeed - AppConfig.minSpeedMoving)
            }
        }
    }

    fun getCurrentSpeed(): Float {
        if (stepTimestamps.isEmpty()) return AppConfig.minSpeed

        val timeSinceLastStep = System.currentTimeMillis() - stepTimestamps.last()
        if (timeSinceLastStep > 3000) {
            val decayFactor = max(0f, 1f - (timeSinceLastStep - 3000) / 5000f)
            return AppConfig.minSpeed + (currentSpeed - AppConfig.minSpeed) * decayFactor
        }

        return currentSpeed
    }

    fun getEstimatedDistance(totalSteps: Int): Float {
        return (totalSteps * AppConfig.averageStrideLength) / 1000f
    }

    fun getEstimatedCalories(totalSteps: Int): Int {
        val distanceKm = getEstimatedDistance(totalSteps)
        return (distanceKm * AppConfig.caloriesPerKm).toInt()
    }

    fun reset() {
        stepTimestamps.clear()
        currentSpeed = AppConfig.minSpeed
    }
}
