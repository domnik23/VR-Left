package com.vrla.forest

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.Sensor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
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

    private lateinit var overlayContainer: View
    private lateinit var finishOverlay: View
    private lateinit var versionText: TextView
    private lateinit var stepCountText: TextView
    private lateinit var speedText: TextView
    private lateinit var distanceText: TextView
    private lateinit var timeText: TextView
    private lateinit var caloriesText: TextView
    private lateinit var finishText: TextView
    private lateinit var settingsButton: TextView
    private lateinit var finishRestartButton: android.widget.Button
    private lateinit var finishExitButton: android.widget.Button
    private lateinit var timecodeOverlayText: TextView

    // Timecode parameter loading
    private var timecodeLoader: TimecodeParameterLoader? = null

    // Volume button double-press detection
    private var lastVolumeUpPressTime = 0L
    private var lastVolumeDownPressTime = 0L
    private val DOUBLE_PRESS_INTERVAL = 500L // milliseconds

    private var rotationVector: Sensor? = null
    private var stepCounter: Sensor? = null

    private var isVRActive = false
    private var isVideoStarted = false
    private var startTime = 0L
    private var totalSteps = 0
    private var sessionSteps = 0

    private val rotationMatrix = FloatArray(9)
    private val remappedRotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var calibrationYaw = 0f

    private var selectedVideoUri: Uri? = null

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                selectedVideoUri = uri
                saveVideoUri(uri.toString())
                initializeVRWithVideo()
            }
        } else {
            // User cancelled - exit app
            Toast.makeText(this, "Video is required to run the app", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val DEFAULT_VIDEO_NAME = "forest_jog.mp4"
        private const val PREFS_NAME = "VRLAPrefs"
        private const val KEY_VIDEO_URI = "video_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        // Load settings from preferences
        AppConfig.loadFromPreferences(this)

        initViews()
        initSensors()

        // Request all permissions first, then load video
        checkAllPermissions()
    }

    private fun checkAllPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Check Activity Recognition
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        // Check Storage permissions
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, storagePermission)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(storagePermission)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            // All permissions granted, proceed
            findAndLoadVideo()
        }
    }

    private fun initViews() {
        glSurfaceView = findViewById(R.id.glSurfaceView)
        overlayContainer = findViewById(R.id.overlayContainer)
        finishOverlay = findViewById(R.id.finishOverlay)
        versionText = findViewById(R.id.versionText)
        stepCountText = findViewById(R.id.stepCountText)
        speedText = findViewById(R.id.speedText)
        distanceText = findViewById(R.id.distanceText)
        timeText = findViewById(R.id.timeText)
        caloriesText = findViewById(R.id.caloriesText)
        finishText = findViewById(R.id.finishText)
        settingsButton = findViewById(R.id.settingsButton)
        finishRestartButton = findViewById(R.id.finishRestartButton)
        finishExitButton = findViewById(R.id.finishExitButton)
        timecodeOverlayText = findViewById(R.id.timecodeOverlayText)

        // Settings button click
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // Finish overlay buttons
        finishRestartButton.setOnClickListener {
            restartSession()
        }

        finishExitButton.setOnClickListener {
            finish()
        }

        // Set version number
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            versionText.text = "v${packageInfo.versionName}"
        } catch (e: Exception) {
            versionText.text = "v?.?"
        }

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

        // Set video end callback
        vrRenderer.onVideoEnded = {
            runOnUiThread {
                showFinishOverlay()
            }
        }

        // Set video error callback
        vrRenderer.onVideoError = { errorMessage ->
            runOnUiThread {
                showVideoErrorDialog(errorMessage)
            }
        }

        overlayContainer.visibility = View.VISIBLE
    }

    private fun initSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepController = StepController()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            var activityRecognitionGranted = false
            var storageGranted = false

            // Check which permissions were granted
            for (i in permissions.indices) {
                when (permissions[i]) {
                    Manifest.permission.ACTIVITY_RECOGNITION -> {
                        activityRecognitionGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    }
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.READ_MEDIA_VIDEO -> {
                        storageGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    }
                }
            }

            // Handle Activity Recognition
            if (!activityRecognitionGranted) {
                stepCountText.text = "Schrittzähler-Berechtigung fehlt"
                Toast.makeText(this, "Schrittzählung benötigt 'Körperliche Aktivität' Berechtigung", Toast.LENGTH_LONG).show()
            }

            // Proceed with video loading regardless
            findAndLoadVideo()
        }
    }

    private fun findAndLoadVideo() {
        // OLD METHOD - using res/raw (commented out)
        // Check if default video exists in res/raw
        // val resourceId = resources.getIdentifier("forest_jog", "raw", packageName)
        // if (resourceId != 0) {
        //     selectedVideoUri = Uri.parse("android.resource://$packageName/$resourceId")
        //     initializeVRWithVideo()
        //     return
        // }

        // Check saved video URI
        val savedUri = getSavedVideoUri()
        if (savedUri != null) {
            try {
                contentResolver.openInputStream(savedUri)?.close()
                selectedVideoUri = savedUri
                initializeVRWithVideo()
                return
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Saved video no longer accessible: ${e.message}")
            }
        }

        // Search for default video (permissions already granted in onCreate)
        continueVideoSearch()
    }

    private fun continueVideoSearch() {
        // Search in Movies/Videos for forest_jog.mp4
        val foundUri = searchForDefaultVideo()
        if (foundUri != null) {
            selectedVideoUri = foundUri
            saveVideoUri(foundUri.toString())
            initializeVRWithVideo()
        } else {
            // Not found - open file picker
            openVideoPicker()
        }
    }

    private fun searchForDefaultVideo(): Uri? {
        try {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME
            )
            val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(DEFAULT_VIDEO_NAME)

            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val id = cursor.getLong(idColumn)
                    android.util.Log.d("MainActivity", "Found default video: $DEFAULT_VIDEO_NAME")
                    return Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error searching for video: ${e.message}")
        }
        return null
    }

    private fun openVideoPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/mp4"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        videoPickerLauncher.launch(intent)
    }

    private fun saveVideoUri(uriString: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VIDEO_URI, uriString)
            .apply()
    }

    private fun getSavedVideoUri(): Uri? {
        val uriString = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VIDEO_URI, null)
        return uriString?.let { Uri.parse(it) }
    }

    private fun initializeVRWithVideo() {
        android.util.Log.d("MainActivity", "Initializing VR with video: $selectedVideoUri")
        // Start VR after short delay (so OpenGL has time)
        glSurfaceView.postDelayed({
            startVRExperience()
        }, 500)
    }

    private fun startVRExperience() {
        if (isVRActive) return

        android.util.Log.d("MainActivity", "Starting VR Experience")

        // Check if rotation sensor is available (CRITICAL for VR)
        if (rotationVector == null) {
            showGyroscopeWarning()
            return
        }

        // Set video URI in renderer before starting
        selectedVideoUri?.let { uri ->
            vrRenderer.setVideoUri(uri)

            // Try to load timecode parameters for this video
            val videoFileName = getVideoFileName(uri)
            if (videoFileName != null) {
                timecodeLoader = TimecodeParameterLoader(this)
                if (timecodeLoader!!.loadParametersForVideo(videoFileName, uri)) {
                    android.util.Log.d("MainActivity", "Loaded timecode parameters for $videoFileName")
                    vrRenderer.setTimecodeLoader(timecodeLoader)
                } else {
                    android.util.Log.d("MainActivity", "No timecode parameters found for $videoFileName")
                    timecodeLoader = null
                }
            }
        }

        isVRActive = true
        startTime = System.currentTimeMillis()
        sessionSteps = 0

        sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_GAME)

        // Check if step counter is available
        if (stepCounter == null) {
            showStepCounterWarning()
        } else {
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
            totalSteps = 0
        }

        // Video will start immediately (video starts on app launch)
        isVideoStarted = true
        vrRenderer.startVideo()
        android.util.Log.d("MainActivity", "Starting video immediately...")

        startUIUpdateLoop()
        calibrateOrientation()  // Auto-calibrate on start (v1.3 behavior)
    }

    private fun showGyroscopeWarning() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Gyroscope/Rotation Sensor fehlt")
            .setMessage("Ihr Gerät hat keinen Rotation Vector Sensor.\n\n" +
                    "Dieser Sensor ist ZWINGEND für VR Head Tracking erforderlich.\n\n" +
                    "Die App kann ohne diesen Sensor nicht funktionieren.")
            .setPositiveButton("Beenden") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showStepCounterWarning() {
        runOnUiThread {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Schrittzähler nicht verfügbar")
                .setMessage("Der Schrittzähler ist auf diesem Gerät nicht verfügbar.\n\n" +
                        "Die Videogeschwindigkeit wird NICHT automatisch angepasst.\n\n" +
                        "Empfehlung: Passen Sie die Geschwindigkeiten im Einstellungsmenü (⋮) manuell an.")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                .setNegativeButton("Beenden") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun showVideoErrorDialog(errorMessage: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Video-Fehler")
            .setMessage("$errorMessage\n\nMöchten Sie ein anderes Video auswählen?")
            .setPositiveButton("Video auswählen") { _, _ ->
                openVideoPicker()
            }
            .setNegativeButton("Beenden") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun calibrateOrientation() {
        vrRenderer.calibrateOrientation()
        Toast.makeText(this, "View recalibrated", Toast.LENGTH_SHORT).show()
    }

    /**
     * Handle volume button presses for VR headset controls
     *
     * Since the phone is inside the VR headset, normal touch controls are not accessible.
     * Volume buttons provide a way to control the app without removing the headset.
     *
     * Controls:
     * - Double Volume Up: Recalibrate head orientation (set current view as "forward")
     * - Double Volume Down: Restart current session
     *
     * Implementation:
     * - Detects double-press within 500ms interval
     * - First press stores timestamp
     * - Second press within interval triggers action
     * - Returns true to consume event and prevent system volume change
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastPress = currentTime - lastVolumeUpPressTime

                if (timeSinceLastPress < DOUBLE_PRESS_INTERVAL) {
                    // Double press detected - Recalibrate orientation
                    calibrateOrientation()
                    lastVolumeUpPressTime = 0L
                    return true  // Consume event, prevent volume change
                } else {
                    lastVolumeUpPressTime = currentTime
                }
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastPress = currentTime - lastVolumeDownPressTime

                if (timeSinceLastPress < DOUBLE_PRESS_INTERVAL) {
                    // Double press detected - Restart session
                    restartSession()
                    lastVolumeDownPressTime = 0L
                    return true  // Consume event, prevent volume change
                } else {
                    lastVolumeDownPressTime = currentTime
                }
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun restartSession() {
        android.util.Log.d("MainActivity", "Restarting session")

        // Reset stats
        sessionSteps = 0
        startTime = System.currentTimeMillis()
        isVideoStarted = false
        stepController.reset()

        // Hide finish overlay
        finishOverlay.visibility = View.GONE

        // Restart video
        vrRenderer.restartVideo()

        Toast.makeText(this, "Session restarted", Toast.LENGTH_SHORT).show()
    }

    private fun showFinishOverlay() {
        val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
        val distance = stepController.getEstimatedDistance(sessionSteps)
        val calories = stepController.getEstimatedCalories(sessionSteps)

        val finishMessage = """Zeit: ${formatTime(elapsedSeconds)}
Schritte: $sessionSteps
Distanz: ${String.format("%.1f", distance)}km
Kalorien: ${calories}kcal"""

        finishText.text = finishMessage
        finishOverlay.visibility = View.VISIBLE

        android.util.Log.d("MainActivity", "Video finished - showing completion overlay")
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

        // Update timecode overlay
        updateTimecodeOverlay()
    }

    private fun updateTimecodeOverlay() {
        val overlay = vrRenderer.getCurrentOverlay()

        if (overlay != null) {
            timecodeOverlayText.text = overlay.text
            timecodeOverlayText.textSize = overlay.textSize

            // Parse colors
            try {
                timecodeOverlayText.setTextColor(android.graphics.Color.parseColor(overlay.textColor))
                timecodeOverlayText.setBackgroundColor(android.graphics.Color.parseColor(overlay.backgroundColor))
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Error parsing overlay colors: ${e.message}")
            }

            // Set position (gravity)
            val gravity = when (overlay.position.lowercase()) {
                "top" -> android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                "bottom" -> android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                else -> android.view.Gravity.CENTER
            }
            (timecodeOverlayText.layoutParams as android.widget.FrameLayout.LayoutParams).gravity = gravity

            timecodeOverlayText.visibility = View.VISIBLE
        } else {
            timecodeOverlayText.visibility = View.GONE
        }
    }

    private fun formatTime(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

    /**
     * Handle sensor updates for head tracking and step counting
     *
     * Two sensors:
     * 1. ROTATION_VECTOR: Combines gyroscope + accelerometer + magnetometer
     *    - Provides device orientation as rotation matrix
     *    - Used for VR head tracking
     *    - Rate: SENSOR_DELAY_GAME (~50-100 updates/sec)
     *
     * 2. STEP_COUNTER: Hardware step detector
     *    - Cumulative step count since device boot
     *    - Used for playback speed control
     *    - Rate: SENSOR_DELAY_NORMAL (updates on each step)
     */
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                if (isVRActive) {
                    // Convert rotation vector to 3x3 rotation matrix
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                    // Remap coordinate system for landscape VR headset orientation
                    // Phone is rotated 90° LEFT (counterclockwise) when placed in headset
                    //
                    // Android default coordinate system (portrait):
                    //   X: Right, Y: Up, Z: Out of screen
                    //
                    // VR headset coordinate system (landscape):
                    //   X: Up (was Y), Y: Left (was -X), Z: Out of screen
                    //
                    // Remapping: AXIS_MINUS_Y → new X, AXIS_X → new Y
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_MINUS_Y,  // new X-axis (pointing up in landscape)
                        SensorManager.AXIS_X,        // new Y-axis (pointing left in landscape)
                        remappedRotationMatrix
                    )

                    // Send remapped rotation to renderer for head tracking
                    vrRenderer.updateHeadRotation(remappedRotationMatrix)
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

                        // Start video on first step
                        if (!isVideoStarted) {
                            isVideoStarted = true
                            vrRenderer.startVideo()
                            android.util.Log.d("MainActivity", "First step detected! Starting video...")
                        }

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

        // Reload settings when returning from SettingsActivity
        AppConfig.loadFromPreferences(this)

        // Check if video URI changed
        val savedUri = getSavedVideoUri()
        if (savedUri != null && savedUri != selectedVideoUri && isVRActive) {
            // New video selected - reload and reset
            selectedVideoUri = savedUri
            android.util.Log.d("MainActivity", "New video detected - reloading: $savedUri")
            vrRenderer.setVideoUri(savedUri)
            restartSession()
        } else {
            // Just update volume if player is running
            vrRenderer.updateVolume()
        }

        // Re-register sensor listeners if VR is active
        if (isVRActive) {
            rotationVector?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            stepCounter?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
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

    /**
     * Extract filename from video URI
     *
     * @param uri Video URI
     * @return Filename (e.g., "forest_jog.mp4") or null if cannot be determined
     */
    private fun getVideoFileName(uri: Uri): String? {
        return try {
            // Try to get filename from content resolver
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    if (displayNameIndex >= 0) {
                        return it.getString(displayNameIndex)
                    }
                }
            }

            // Fallback: extract from URI path
            uri.lastPathSegment
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Error extracting filename from URI: ${e.message}")
            null
        }
    }
}