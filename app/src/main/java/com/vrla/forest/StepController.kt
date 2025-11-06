package com.vrla.forest

import kotlin.math.max

class StepController {
    
    private val minSpeed = 0.3f
    private val maxSpeed = 2.5f
    private val baseStepsPerMinute = 60
    private val windowSizeMs = 10000L
    
    private val stepTimestamps = mutableListOf<Long>()
    private var currentSpeed = minSpeed
    
    private val averageStrideLength = 0.75f
    private val caloriesPerKm = 60
    
    fun addStep() {
        val currentTime = System.currentTimeMillis()
        stepTimestamps.add(currentTime)
        stepTimestamps.removeAll { it < currentTime - windowSizeMs }
        updateSpeed()
    }
    
    private fun updateSpeed() {
        if (stepTimestamps.isEmpty()) {
            currentSpeed = minSpeed
            return
        }
        
        val windowSizeMinutes = windowSizeMs / 60000f
        val stepsInWindow = stepTimestamps.size
        val stepsPerMinute = stepsInWindow / windowSizeMinutes
        
        currentSpeed = when {
            stepsPerMinute < 10 -> minSpeed
            stepsPerMinute >= 120 -> maxSpeed
            else -> {
                val ratio = stepsPerMinute / baseStepsPerMinute
                val speed = minSpeed + (1.0f - minSpeed) * (ratio - 0.167f) / 0.833f
                speed.coerceIn(minSpeed, maxSpeed)
            }
        }
    }
    
    fun getCurrentSpeed(): Float {
        if (stepTimestamps.isEmpty()) return minSpeed
        
        val timeSinceLastStep = System.currentTimeMillis() - stepTimestamps.last()
        if (timeSinceLastStep > 3000) {
            val decayFactor = max(0f, 1f - (timeSinceLastStep - 3000) / 5000f)
            return minSpeed + (currentSpeed - minSpeed) * decayFactor
        }
        
        return currentSpeed
    }
    
    fun getEstimatedDistance(totalSteps: Int): Float {
        return (totalSteps * averageStrideLength) / 1000f
    }
    
    fun getEstimatedCalories(totalSteps: Int): Int {
        val distanceKm = getEstimatedDistance(totalSteps)
        return (distanceKm * caloriesPerKm).toInt()
    }
}
