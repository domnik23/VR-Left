package com.vrla.forest

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var vrRenderer: VRRenderer
    private lateinit var sensorManager: SensorManager
    private lateinit var stepController: StepController

    private lateinit var calibrateButton: Button
    private lateinit var overlayContainer: View
    private lateinit var stepCountText: TextView
    private lateinit var speedText: TextView
    private lateinit var distanceText: TextView
    private lateinit var timeText: TextView
    private lateinit var caloriesText: TextView

    private var rotationVector: Sensor? = null
    private var stepCounter: Sensor? = null

    private var isVRActive = false
    private var startTime = 0L
    private var totalSteps = 0
    private var sessionSteps = 0

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var calibrationYaw = 0f

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        initViews()
        initSensors()
        checkPermissions()

        // Start VR nach kurzer Verzögerung (damit OpenGL Zeit hat)
        glSurfaceView.postDelayed({
            startVRExperience()
        }, 500)
    }

    private fun initViews() {
        glSurfaceView = findViewById(R.id.glSurfaceView)
        calibrateButton = findViewById(R.id.calibrateButton)
        overlayContainer = findViewById(R.id.overlayContainer)
        stepCountText = findViewById(R.id.stepCountText)
        speedText = findViewById(R.id.speedText)
        distanceText = findViewById(R.id.distanceText)
        timeText = findViewById(R.id.timeText)
        caloriesText = findViewById(R.id.caloriesText)

        glSurfaceView.setEGLContextClientVersion(3)
        vrRenderer = VRRenderer(this)
        glSurfaceView.setRenderer(vrRenderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        glSurfaceView.post {
            vrRenderer.setScreenDimensions(
                resources.displayMetrics.widthPixels,
                resources.displayMetrics.heightPixels
            )
        }

        calibrateButton.setOnClickListener { calibrateOrientation() }
        overlayContainer.visibility = View.VISIBLE
    }

    private fun initSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepController = StepController()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted - restart sensors
                if (isVRActive) {
                    stepCounter?.let {
                        sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                    }
                }
            } else {
                stepCountText.text = "Permission needed for step tracking"
            }
        }
    }

    private fun startVRExperience() {
        if (isVRActive) return

        android.util.Log.d("MainActivity", "Starting VR Experience")

        isVRActive = true
        startTime = System.currentTimeMillis()
        sessionSteps = 0

        rotationVector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        stepCounter?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            totalSteps = 0
        }

        // Video starten - VRRenderer wartet automatisch bis Surface bereit ist
        vrRenderer.startVideo()

        startUIUpdateLoop()
        calibrateOrientation()
    }

    private fun calibrateOrientation() {
        calibrationYaw = orientationAngles[0]
        android.util.Log.d("MainActivity", "Orientation calibrated: $calibrationYaw")
    }

    private fun startUIUpdateLoop() {
        lifecycleScope.launch {
            while (isVRActive) {
                updateUI()
                delay(100)
            }
        }
    }

    private fun updateUI() {
        val currentSpeed = stepController.getCurrentSpeed()
        val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
        val distance = stepController.getEstimatedDistance(sessionSteps)
        val calories = stepController.getEstimatedCalories(sessionSteps)

        stepCountText.text = "$sessionSteps"
        speedText.text = String.format("%.1fx", currentSpeed)
        timeText.text = formatTime(elapsedSeconds)
        distanceText.text = String.format("%.1fkm", distance)
        caloriesText.text = String.format("%dkcal", calories)

        vrRenderer.setPlaybackSpeed(currentSpeed)
    }

    private fun formatTime(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                if (isVRActive) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)

                    val calibratedYaw = orientationAngles[0] - calibrationYaw
                    vrRenderer.updateOrientation(
                        calibratedYaw,
                        orientationAngles[1],
                        orientationAngles[2]
                    )
                }
            }

            Sensor.TYPE_STEP_COUNTER -> {
                if (totalSteps == 0) {
                    totalSteps = event.values[0].toInt()
                    android.util.Log.d("MainActivity", "Initial step count: $totalSteps")
                } else {
                    val currentTotal = event.values[0].toInt()
                    val newSteps = currentTotal - totalSteps
                    if (newSteps > 0 && newSteps < 100) {
                        sessionSteps += newSteps
                        totalSteps = currentTotal
                        stepController.addStep()
                        android.util.Log.d("MainActivity", "Steps: $sessionSteps")
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        vrRenderer.release()
    }
}