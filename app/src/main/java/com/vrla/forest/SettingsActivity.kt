package com.vrla.forest

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingsActivity : AppCompatActivity() {

    private lateinit var videoPrefs: VideoPreferences

    private lateinit var selectVideoFolderButton: Button
    private lateinit var currentFolderText: TextView
    private lateinit var selectVideoButton: Button
    private lateinit var currentVideoText: TextView
    private lateinit var videoRotationSpinner: Spinner
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var volumeValueText: TextView
    private lateinit var stereoSwitch: SwitchCompat
    private lateinit var ipdSeekBar: SeekBar
    private lateinit var ipdValueText: TextView
    private lateinit var fovSeekBar: SeekBar
    private lateinit var fovValueText: TextView
    private lateinit var strideLengthSeekBar: SeekBar
    private lateinit var strideLengthValueText: TextView
    private lateinit var caloriesSeekBar: SeekBar
    private lateinit var caloriesValueText: TextView
    private lateinit var minSpeedSeekBar: SeekBar
    private lateinit var minSpeedValueText: TextView
    private lateinit var minSpeedMovingSeekBar: SeekBar
    private lateinit var minSpeedMovingValueText: TextView
    private lateinit var maxSpeedSeekBar: SeekBar
    private lateinit var maxSpeedValueText: TextView
    private lateinit var smoothingSeekBar: SeekBar
    private lateinit var smoothingValueText: TextView
    private lateinit var accelerationCurveSeekBar: SeekBar
    private lateinit var accelerationCurveValueText: TextView
    private lateinit var stepsBeforeStartSeekBar: SeekBar
    private lateinit var stepsBeforeStartValueText: TextView
    private lateinit var appVersionText: TextView
    private lateinit var resetButton: Button
    private lateinit var saveButton: Button

    // Video list
    private lateinit var videoListContainer: View
    private lateinit var videoRecyclerView: RecyclerView
    private lateinit var selectOtherVideoButton: Button
    private lateinit var cancelVideoListButton: Button

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { treeUri ->
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                videoPrefs.saveVideoFolderUri(treeUri)
                updateCurrentFolderDisplay()

                // Show video list from the selected folder
                showVideoListFromFolder(treeUri)
            }
        }
    }

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                saveVideoUri(uri.toString())
                updateCurrentVideoDisplay()
                Toast.makeText(this, "Video selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize video preferences
        videoPrefs = VideoPreferences(this)

        initViews()
        loadSettings()
        setupListeners()
    }

    private fun initViews() {
        selectVideoFolderButton = findViewById(R.id.selectVideoFolderButton)
        currentFolderText = findViewById(R.id.currentFolderText)
        selectVideoButton = findViewById(R.id.selectVideoButton)
        currentVideoText = findViewById(R.id.currentVideoText)
        videoRotationSpinner = findViewById(R.id.videoRotationSpinner)
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        volumeValueText = findViewById(R.id.volumeValueText)
        stereoSwitch = findViewById(R.id.stereoSwitch)

        // Setup video rotation spinner
        val rotationOptions = arrayOf("0° (Normal)", "90° CW", "180°", "270° CW (-90°)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, rotationOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        videoRotationSpinner.adapter = adapter
        ipdSeekBar = findViewById(R.id.ipdSeekBar)
        ipdValueText = findViewById(R.id.ipdValueText)
        fovSeekBar = findViewById(R.id.fovSeekBar)
        fovValueText = findViewById(R.id.fovValueText)
        strideLengthSeekBar = findViewById(R.id.strideLengthSeekBar)
        strideLengthValueText = findViewById(R.id.strideLengthValueText)
        caloriesSeekBar = findViewById(R.id.caloriesSeekBar)
        caloriesValueText = findViewById(R.id.caloriesValueText)
        minSpeedSeekBar = findViewById(R.id.minSpeedSeekBar)
        minSpeedValueText = findViewById(R.id.minSpeedValueText)
        minSpeedMovingSeekBar = findViewById(R.id.minSpeedMovingSeekBar)
        minSpeedMovingValueText = findViewById(R.id.minSpeedMovingValueText)
        maxSpeedSeekBar = findViewById(R.id.maxSpeedSeekBar)
        maxSpeedValueText = findViewById(R.id.maxSpeedValueText)
        smoothingSeekBar = findViewById(R.id.smoothingSeekBar)
        smoothingValueText = findViewById(R.id.smoothingValueText)
        accelerationCurveSeekBar = findViewById(R.id.accelerationCurveSeekBar)
        accelerationCurveValueText = findViewById(R.id.accelerationCurveValueText)
        stepsBeforeStartSeekBar = findViewById(R.id.stepsBeforeStartSeekBar)
        stepsBeforeStartValueText = findViewById(R.id.stepsBeforeStartValueText)
        appVersionText = findViewById(R.id.appVersionText)
        resetButton = findViewById(R.id.resetButton)
        saveButton = findViewById(R.id.saveButton)

        // Video list
        videoListContainer = findViewById(R.id.videoListContainer)
        videoRecyclerView = findViewById(R.id.videoRecyclerView)
        selectOtherVideoButton = findViewById(R.id.selectOtherVideoButton)
        cancelVideoListButton = findViewById(R.id.cancelVideoListButton)

        // Setup RecyclerView
        videoRecyclerView.layoutManager = LinearLayoutManager(this)

        // Set app version
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            appVersionText.text = "Version ${packageInfo.versionName}"
        } catch (e: Exception) {
            appVersionText.text = "Version ?.?"
        }

        // Show current video
        updateCurrentVideoDisplay()
    }

    private fun updateCurrentVideoDisplay() {
        val prefs = getSharedPreferences("VRLAPrefs", Context.MODE_PRIVATE)
        val uriString = prefs.getString("video_uri", null)

        if (uriString != null) {
            val uri = Uri.parse(uriString)
            val fileName = getFileNameFromUri(uri)
            currentVideoText.text = "Current: $fileName"
        } else {
            currentVideoText.text = "Current: No video selected"
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        return it.getString(nameIndex)
                    }
                }
            }
            // Fallback: use last path segment
            uri.lastPathSegment ?: "Unknown"
        } catch (e: Exception) {
            uri.lastPathSegment ?: "Unknown"
        }
    }

    private fun setupListeners() {
        selectVideoFolderButton.setOnClickListener {
            openFolderPicker()
        }

        selectVideoButton.setOnClickListener {
            // Check if folder is selected, if yes show video list
            val folderTreeUri = videoPrefs.getVideoFolderUri()
            if (folderTreeUri != null) {
                showVideoListFromFolder(folderTreeUri)
            } else {
                openVideoPicker()
            }
        }

        selectOtherVideoButton.setOnClickListener {
            videoListContainer.visibility = View.GONE
            openVideoPicker()
        }

        cancelVideoListButton.setOnClickListener {
            videoListContainer.visibility = View.GONE
        }

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                volumeValueText.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        ipdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val ipd = 50 + progress // 50mm to 80mm
                ipdValueText.text = "${ipd}mm"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        fovSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val fov = 60 + progress // 60° to 100°
                fovValueText.text = "${fov}°"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        strideLengthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val stride = 0.5f + progress * 0.01f // 0.5m to 1.0m
                strideLengthValueText.text = String.format("%.2fm", stride)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        caloriesSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val calories = 30 + progress // 30 to 130 kcal/km
                caloriesValueText.text = "$calories kcal"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        minSpeedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress * 0.01f // 0.0x to 1.0x
                minSpeedValueText.text = String.format("%.1fx", speed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        minSpeedMovingSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress * 0.01f // 0.0x to 1.0x
                minSpeedMovingValueText.text = String.format("%.1fx", speed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        maxSpeedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = 1.0f + progress * 0.01f // 1.0x to 2.0x
                maxSpeedValueText.text = String.format("%.1fx", speed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        smoothingSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val smoothing = progress * 0.01f // 0.0 to 1.0
                smoothingValueText.text = when {
                    smoothing < 0.2f -> "Very Smooth"
                    smoothing < 0.4f -> "Smooth"
                    smoothing < 0.6f -> "Normal"
                    smoothing < 0.8f -> "Direct"
                    else -> "Instant"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        accelerationCurveSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val curve = 1.0f + progress * 0.01f // 1.0 to 3.0
                accelerationCurveValueText.text = when {
                    curve < 1.2f -> "Linear"
                    curve < 1.7f -> "Moderate"
                    curve < 2.3f -> "Strong"
                    else -> "Very Strong"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        stepsBeforeStartSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                stepsBeforeStartValueText.text = if (progress == 0) {
                    "Instant"
                } else if (progress == 1) {
                    "1 step"
                } else {
                    "$progress steps"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        resetButton.setOnClickListener {
            resetToDefaults()
        }

        saveButton.setOnClickListener {
            saveSettings()
            finish()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("VRLAPrefs", Context.MODE_PRIVATE)

        // Video rotation
        val rotation = prefs.getFloat("video_rotation", -90f)
        videoRotationSpinner.setSelection(when (rotation) {
            0f -> 0    // 0°
            90f -> 1   // 90° CW
            180f, -180f -> 2  // 180°
            else -> 3  // 270° CW / -90° (Default - calibrated)
        })
        volumeSeekBar.progress = prefs.getInt("video_volume", 50)

        // Display
        stereoSwitch.isChecked = prefs.getBoolean("stereo_mode", false)
        val ipdMm = (prefs.getFloat("ipd", 0.064f) * 1000).toInt()
        ipdSeekBar.progress = ipdMm - 50
        val fov = prefs.getFloat("field_of_view", 75f).toInt()
        fovSeekBar.progress = fov - 60 // Range: 60-100°

        // Fitness
        val strideLength = prefs.getFloat("stride_length", 0.75f)
        strideLengthSeekBar.progress = ((strideLength - 0.5f) * 100).toInt()
        caloriesSeekBar.progress = prefs.getInt("calories_per_km", 60) - 30

        // Speed
        val minSpeed = prefs.getFloat("min_speed", 0.4f)
        minSpeedSeekBar.progress = (minSpeed * 100).toInt() // 0.0 - 1.0
        val minSpeedMoving = prefs.getFloat("min_speed_moving", 0.7f)
        minSpeedMovingSeekBar.progress = (minSpeedMoving * 100).toInt() // 0.0 - 1.0
        val maxSpeed = prefs.getFloat("max_speed", 1.5f)
        maxSpeedSeekBar.progress = ((maxSpeed - 1.0f) * 100).toInt() // 1.0 - 2.0
        val smoothing = prefs.getFloat("speed_smoothing", 0.3f)
        smoothingSeekBar.progress = (smoothing * 100).toInt() // 0.0 - 1.0
        val accelerationCurve = prefs.getFloat("acceleration_curve", 1.0f)
        accelerationCurveSeekBar.progress = ((accelerationCurve - 1.0f) * 100).toInt() // 1.0 - 3.0

        // Step detection
        val stepsBeforeStart = prefs.getInt("steps_before_video_start", 3)
        stepsBeforeStartSeekBar.progress = stepsBeforeStart // 0 - 10

        // Update display
        updateCurrentFolderDisplay()
        updateCurrentVideoDisplay()
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("VRLAPrefs", Context.MODE_PRIVATE).edit()

        // Video rotation
        val rotation = when (videoRotationSpinner.selectedItemPosition) {
            0 -> 0f      // 0°
            1 -> 90f     // 90° CW
            2 -> 180f    // 180°
            3 -> -90f    // 270° CW / -90°
            else -> -90f
        }
        prefs.putFloat("video_rotation", rotation)
        prefs.putInt("video_volume", volumeSeekBar.progress)

        // Display
        prefs.putBoolean("stereo_mode", stereoSwitch.isChecked)
        val ipdMm = 50 + ipdSeekBar.progress
        prefs.putFloat("ipd", ipdMm / 1000f)
        val fov = 60 + fovSeekBar.progress // 60° to 100°
        prefs.putFloat("field_of_view", fov.toFloat())

        // Fitness
        val strideLength = 0.5f + strideLengthSeekBar.progress * 0.01f
        prefs.putFloat("stride_length", strideLength)
        val caloriesPerKm = 30 + caloriesSeekBar.progress
        prefs.putInt("calories_per_km", caloriesPerKm)

        // Speed
        val minSpeed = minSpeedSeekBar.progress * 0.01f // 0.0 - 1.0
        prefs.putFloat("min_speed", minSpeed)
        val minSpeedMoving = minSpeedMovingSeekBar.progress * 0.01f // 0.0 - 1.0
        prefs.putFloat("min_speed_moving", minSpeedMoving)
        val maxSpeed = 1.0f + maxSpeedSeekBar.progress * 0.01f // 1.0 - 2.0
        prefs.putFloat("max_speed", maxSpeed)
        val smoothing = smoothingSeekBar.progress * 0.01f // 0.0 - 1.0
        prefs.putFloat("speed_smoothing", smoothing)
        val accelerationCurve = 1.0f + accelerationCurveSeekBar.progress * 0.01f // 1.0 - 3.0
        prefs.putFloat("acceleration_curve", accelerationCurve)

        // Step detection
        val stepsBeforeStart = stepsBeforeStartSeekBar.progress // 0 - 10
        prefs.putInt("steps_before_video_start", stepsBeforeStart)

        prefs.apply()

        // Update AppConfig
        AppConfig.videoRotation = rotation
        AppConfig.videoVolume = volumeSeekBar.progress / 100f
        AppConfig.stereoMode = stereoSwitch.isChecked
        AppConfig.ipd = ipdMm / 1000f
        AppConfig.fieldOfView = fov.toFloat()
        AppConfig.averageStrideLength = strideLength
        AppConfig.caloriesPerKm = caloriesPerKm
        AppConfig.minSpeed = minSpeed
        AppConfig.minSpeedMoving = minSpeedMoving
        AppConfig.maxSpeed = maxSpeed
        AppConfig.speedSmoothingFactor = smoothing
        AppConfig.accelerationCurve = accelerationCurve
        AppConfig.stepsBeforeVideoStart = stepsBeforeStart

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun resetToDefaults() {
        videoRotationSpinner.setSelection(3) // 270° CW / -90° (Default - calibrated)
        volumeSeekBar.progress = 50
        stereoSwitch.isChecked = false
        ipdSeekBar.progress = 14 // 64mm
        fovSeekBar.progress = 15 // 75° (60 + 15)
        strideLengthSeekBar.progress = 25 // 0.75m
        caloriesSeekBar.progress = 30 // 60 kcal/km
        minSpeedSeekBar.progress = 40 // 0.4x
        minSpeedMovingSeekBar.progress = 70 // 0.7x
        maxSpeedSeekBar.progress = 50 // 1.5x (1.0 + 0.5)
        smoothingSeekBar.progress = 30 // 0.3 (Normal)
        accelerationCurveSeekBar.progress = 0 // 1.0 (Linear)
        stepsBeforeStartSeekBar.progress = 3 // 3 steps (short warm-up)

        Toast.makeText(this, "Reset to default values", Toast.LENGTH_SHORT).show()
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
        // Use VideoPreferences to save URI and set the video changed flag
        videoPrefs.saveVideoUri(uriString)
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        folderPickerLauncher.launch(intent)
    }

    private fun updateCurrentFolderDisplay() {
        val prefs = getSharedPreferences("VRLAPrefs", Context.MODE_PRIVATE)
        val folderUriString = prefs.getString("video_folder_uri", null)

        if (folderUriString != null) {
            val uri = Uri.parse(folderUriString)
            // Extract folder name from tree URI
            val folderName = uri.lastPathSegment?.substringAfter(':') ?: "Folder"
            currentFolderText.text = "Folder: $folderName"
        } else {
            currentFolderText.text = "No folder selected"
        }
    }

    /**
     * Show video list from the selected folder
     *
     * @param folderTreeUri Tree URI of the selected folder
     */
    private fun showVideoListFromFolder(folderTreeUri: Uri) {
        val videos = mutableListOf<VideoItem>()
        var cursor: Cursor? = null

        try {
            // Build URI for querying children of the tree
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                folderTreeUri,
                DocumentsContract.getTreeDocumentId(folderTreeUri)
            )

            // Query all children to find video files
            cursor = contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME + " ASC"
            )

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(
                        cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    )
                    val mimeType = cursor.getString(
                        cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    )

                    // Check if it's a video file
                    if (mimeType?.startsWith("video/") == true || displayName.endsWith(".mp4", true)) {
                        val documentId = cursor.getString(
                            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        )
                        val videoUri = DocumentsContract.buildDocumentUriUsingTree(folderTreeUri, documentId)

                        // Check if parameter file exists
                        val baseName = displayName.substringBeforeLast(".")
                        val hasParameters = checkParameterFileExists(folderTreeUri, "$baseName.json")

                        videos.add(VideoItem(videoUri, displayName, hasParameters))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "Error loading videos from folder: ${e.message}", e)
        } finally {
            cursor?.close()
        }

        if (videos.isEmpty()) {
            Toast.makeText(
                this,
                "No videos found in folder",
                Toast.LENGTH_LONG
            ).show()
        } else {
            // Show video list
            showVideoList(videos)
        }
    }

    /**
     * Check if a parameter file exists in the folder
     *
     * @param folderTreeUri Tree URI of the folder
     * @param paramFileName Name of the parameter file
     * @return true if file exists, false otherwise
     */
    private fun checkParameterFileExists(folderTreeUri: Uri, paramFileName: String): Boolean {
        var cursor: Cursor? = null
        return try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                folderTreeUri,
                DocumentsContract.getTreeDocumentId(folderTreeUri)
            )

            cursor = contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(
                        cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    )
                    if (displayName == paramFileName) {
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        } finally {
            cursor?.close()
        }
    }

    /**
     * Display the video list UI
     *
     * @param videos List of videos to display
     */
    private fun showVideoList(videos: List<VideoItem>) {
        runOnUiThread {
            // Set up adapter
            val adapter = VideoListAdapter(videos) { videoItem ->
                // Video selected
                videoPrefs.saveVideoUri(videoItem.uri)
                videoListContainer.visibility = View.GONE
                updateCurrentVideoDisplay()
                Toast.makeText(this, "Video selected: ${videoItem.fileName}", Toast.LENGTH_SHORT).show()
            }
            videoRecyclerView.adapter = adapter

            // Show video list overlay
            videoListContainer.visibility = View.VISIBLE
        }
    }
}
