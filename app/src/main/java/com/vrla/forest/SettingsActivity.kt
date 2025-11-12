package com.vrla.forest

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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

class SettingsActivity : AppCompatActivity() {

    private lateinit var selectVideoButton: Button
    private lateinit var currentVideoText: TextView
    private lateinit var videoRotationSpinner: Spinner
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var volumeValueText: TextView
    private lateinit var stereoSwitch: SwitchCompat
    private lateinit var ipdSeekBar: SeekBar
    private lateinit var ipdValueText: TextView
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
    private lateinit var appVersionText: TextView
    private lateinit var resetButton: Button
    private lateinit var saveButton: Button

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
                Toast.makeText(this, "Video ausgewählt", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        loadSettings()
        setupListeners()
    }

    private fun initViews() {
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
        appVersionText = findViewById(R.id.appVersionText)
        resetButton = findViewById(R.id.resetButton)
        saveButton = findViewById(R.id.saveButton)

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
            currentVideoText.text = "Aktuell: $fileName"
        } else {
            currentVideoText.text = "Aktuell: Kein Video ausgewählt"
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
            uri.lastPathSegment ?: "Unbekannt"
        } catch (e: Exception) {
            uri.lastPathSegment ?: "Unbekannt"
        }
    }

    private fun setupListeners() {
        selectVideoButton.setOnClickListener {
            openVideoPicker()
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

        // Video
        val rotation = prefs.getFloat("video_rotation", -90f)
        videoRotationSpinner.setSelection(when (rotation) {
            0f -> 0    // 0°
            90f -> 1   // 90° CW
            180f, -180f -> 2  // 180°
            else -> 3  // 270° CW / -90°
        })
        volumeSeekBar.progress = prefs.getInt("video_volume", 50)

        // Display
        stereoSwitch.isChecked = prefs.getBoolean("stereo_mode", false)
        val ipdMm = (prefs.getFloat("ipd", 0.064f) * 1000).toInt()
        ipdSeekBar.progress = ipdMm - 50

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
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("VRLAPrefs", Context.MODE_PRIVATE).edit()

        // Video
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

        prefs.apply()

        // Update AppConfig
        AppConfig.videoRotation = rotation
        AppConfig.videoVolume = volumeSeekBar.progress / 100f
        AppConfig.stereoMode = stereoSwitch.isChecked
        AppConfig.ipd = ipdMm / 1000f
        AppConfig.averageStrideLength = strideLength
        AppConfig.caloriesPerKm = caloriesPerKm
        AppConfig.minSpeed = minSpeed
        AppConfig.minSpeedMoving = minSpeedMoving
        AppConfig.maxSpeed = maxSpeed

        Toast.makeText(this, "Einstellungen gespeichert", Toast.LENGTH_SHORT).show()
    }

    private fun resetToDefaults() {
        videoRotationSpinner.setSelection(3) // -90° (Default to fix rotation issue)
        volumeSeekBar.progress = 50
        stereoSwitch.isChecked = false
        ipdSeekBar.progress = 14 // 64mm
        strideLengthSeekBar.progress = 25 // 0.75m
        caloriesSeekBar.progress = 30 // 60 kcal/km
        minSpeedSeekBar.progress = 40 // 0.4x
        minSpeedMovingSeekBar.progress = 70 // 0.7x
        maxSpeedSeekBar.progress = 50 // 1.5x (1.0 + 0.5)

        Toast.makeText(this, "Auf Standardwerte zurückgesetzt", Toast.LENGTH_SHORT).show()
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
        getSharedPreferences("VRLAPrefs", Context.MODE_PRIVATE)
            .edit()
            .putString("video_uri", uriString)
            .apply()
    }
}
