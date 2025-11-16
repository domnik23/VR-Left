# VR Left - Code Maintenance Guide

## Table of Contents
- [Architecture Overview](#architecture-overview)
- [Key Components](#key-components)
- [Timecode Parameter System](#timecode-parameter-system)
- [Video Selection Workflow](#video-selection-workflow)
- [Adding New Features](#adding-new-features)
- [Debugging Tips](#debugging-tips)
- [Configuration Files](#configuration-files)
- [Android-Specific Notes](#android-specific-notes)

---

## Architecture Overview

### Core Architecture
The app is built using:
- **OpenGL ES 3.0** for VR video rendering (`VRRenderer.kt`)
- **Android MediaPlayer** for video playback
- **Sensor APIs** for head tracking (gyroscope, accelerometer)
- **Step Counter API** for fitness tracking
- **Android Scoped Storage** for file access (tree URIs)

### Data Flow
```
User selects folder → Tree URI stored in SharedPreferences
                   ↓
User selects video → Video URI + Tree URI passed to MainActivity
                   ↓
TimecodeParameterLoader searches for JSON file in tree URI
                   ↓
VRRenderer applies parameters during playback
                   ↓
UI updates every frame with stats and overlays
```

---

## Key Components

### 1. MainActivity.kt
**Purpose**: Main activity handling VR rendering, UI, and video playback

**Key Responsibilities**:
- Initialize OpenGL surface and VR renderer
- Handle sensor input for head tracking
- Manage video selection (file picker or folder list)
- Display stats overlay (steps, speed, distance, time, calories)
- Show timecode overlay text from JSON parameters
- Handle lifecycle events (pause/resume)

**Important Methods**:
- `showVideoListFromFolder(Uri)`: Displays videos from selected folder
- `initializeVRWithVideo()`: Starts VR playback with selected video
- `updateTimecodeOverlay()`: Updates overlay text from parameter loader
- `showInfoBox(String)`: Shows help messages to user

**UI Elements**:
- `overlayContainer`: Top bar with stats (steps, speed, etc.)
- `timecodeOverlayText`: Pink transparent box for JSON overlay text
- `infoBox`: Bottom instruction box
- `videoListContainer`: Video selection list overlay

---

### 2. VRRenderer.kt
**Purpose**: OpenGL ES 3.0 renderer for 360° VR video playback

**Key Responsibilities**:
- Render equirectangular video to sphere
- Handle stereo/mono mode switching
- Apply head rotation matrix from sensors
- Control video playback speed based on step detection
- Update timecode parameters during playback

**Important Methods**:
- `onDrawFrame()`: Main render loop (called every frame)
- `updateTimecodeParameters()`: Applies JSON parameters every 30 frames
- `setVideoUri(Uri)`: Prepares MediaPlayer with new video
- `getCurrentOverlay()`: Returns active overlay config for UI

**Performance Notes**:
- Updates timecode parameters every 30 frames (not every frame) to reduce overhead
- Uses frame counter to throttle updates

---

### 3. TimecodeParameterLoader.kt
**Purpose**: Load and manage JSON parameter files for videos

**Key Responsibilities**:
- Search for parameter files in multiple locations
- Parse JSON into data classes
- Track current timecode entry based on video position
- Provide active parameters to VRRenderer

**Search Order** (in `loadParametersForVideo()`):
1. Same location as video URI (if single file selected)
2. Folder tree URI (if folder selected) - **PRIMARY METHOD**
3. Internal storage: `/Android/data/com.vrla.forest/files/parameters/`
4. Assets folder: `assets/parameters/`

**Important Methods**:
- `loadParametersForVideo(videoFileName, videoUri?, folderTreeUri?)`: Main entry point
- `tryLoadFromFolderTree(folderTreeUri, paramFileName)`: Uses DocumentsContract to find JSON in folder
- `updateForTime(currentTimeMs)`: Updates active timecode entry
- `getCurrentOverlay()`: Returns active overlay or null

**JSON File Format**:
```json
{
  "timecodes": [
    {
      "startTimeMs": 0,
      "endTimeMs": 30000,
      "parameters": {
        "minSpeed": 2.0,
        "maxSpeed": 3.0,
        "fieldOfView": 95.0,
        "rotationX": 0.0,
        "rotationY": 5.0,
        "rotationZ": 0.0,
        "volume": 0.8,
        "calorieMultiplier": 1.2
      },
      "overlay": {
        "text": "Warm up - slow pace",
        "textSize": 24.0,
        "textColor": "#FFFFFF",
        "backgroundColor": "#30FF69B4",
        "position": "center"
      }
    }
  ]
}
```

---

### 4. SettingsActivity.kt
**Purpose**: User configuration screen

**Key Features**:
- **Folder Selection**: Opens Android folder picker (ACTION_OPEN_DOCUMENT_TREE)
- **File Selection**: Opens file picker for single video (ACTION_OPEN_DOCUMENT)
- **Settings**: Video rotation, volume, IPD, stride length, speed limits

**Important Methods**:
- `openFolderPicker()`: Launches folder selection intent
- `saveVideoFolderUri(String)`: Persists tree URI with permissions
- `loadSettings()`: Loads all settings from SharedPreferences

**Persistent Permissions**:
```kotlin
contentResolver.takePersistableUriPermission(
    treeUri,
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
)
```

---

### 5. StepController.kt
**Purpose**: Manage step detection and video speed control

**Key Logic**:
- Monitors step frequency to determine walking speed
- Maps step frequency to video playback speed
- Applies min/max speed constraints
- Supports timecode parameter overrides

---

### 6. AppConfig.kt (Singleton)
**Purpose**: Global configuration state

**Stored Settings**:
- Video rotation (0°, 90°, 180°, 270°)
- Stereo mode (on/off)
- IPD (inter-pupillary distance)
- Stride length
- Calories per km
- Min/max speed settings

---

## Timecode Parameter System

### How It Works

1. **File Naming Convention**:
   - Video: `my_video.mp4`
   - Parameter file: `my_video.json` (same base name)

2. **File Location**:
   - **Best practice**: Place JSON file in same folder as video
   - User selects folder once in Settings
   - App has access to all files in folder via tree URI

3. **Loading Process**:
   ```
   User opens video → MainActivity gets video filename
                   ↓
   TimecodeParameterLoader.loadParametersForVideo(filename, videoUri, folderTreeUri)
                   ↓
   Search in folder using DocumentsContract API
                   ↓
   Parse JSON → Store in TimecodeConfig
                   ↓
   VRRenderer.setTimecodeLoader(loader)
   ```

4. **Runtime Application**:
   ```
   Every 30 frames:
   - VRRenderer.updateTimecodeParameters()
   - Gets current video position (milliseconds)
   - TimecodeLoader.updateForTime(currentTimeMs)
   - Finds active timecode entry (if any)
   - Applies parameters to renderer
   - MainActivity updates overlay text
   ```

### Adding New Parameters

To add a new parameter type:

1. **Update `TimecodeParameter.kt`**:
   ```kotlin
   data class VideoParameters(
       val minSpeed: Float? = null,
       val maxSpeed: Float? = null,
       // ... existing parameters ...
       val yourNewParameter: Float? = null  // ADD THIS
   )
   ```

2. **Update `VRRenderer.kt`**:
   ```kotlin
   private fun applyTimecodeParameters(params: VideoParameters) {
       // ... existing code ...
       params.yourNewParameter?.let { value ->
           // Apply your new parameter logic here
       }
   }
   ```

3. **Update JSON documentation** in `assets/parameters/README.md`

---

## Video Selection Workflow

### Workflow A (Recommended): Folder-Based Selection

**User Flow**:
1. Open Settings → "Video-Ordner wählen"
2. Select folder containing videos and JSON files
3. App stores tree URI with persistent permissions
4. On app start: Shows list of all videos in folder
5. User selects video from list
6. Parameter file automatically loaded

**Implementation**:
- `MainActivity.findAndLoadVideo()`: Checks if folder URI exists
- `MainActivity.showVideoListFromFolder()`: Queries folder contents
- Uses `DocumentsContract.buildChildDocumentsUriUsingTree()` to list files
- Displays `VideoListAdapter` with RecyclerView

**Advantages**:
- No permission issues (folder access grants access to all files)
- Easy parameter file management
- Shows which videos have parameter files

### Workflow B (Fallback): Single File Selection

**User Flow**:
1. No folder selected → File picker opens
2. User selects single video file
3. App only has access to that one file

**Limitations**:
- Cannot access sibling JSON files (Android Scoped Storage restriction)
- Must use internal storage or assets for parameters

---

## Adding New Features

### Example: Adding a New UI Element

1. **Update Layout** (`activity_main.xml`):
   ```xml
   <TextView
       android:id="@+id/myNewText"
       android:layout_width="wrap_content"
       android:layout_height="wrap_content"
       android:text="My Feature"
       android:textColor="#FFFFFF" />
   ```

2. **Add View Reference** (`MainActivity.kt`):
   ```kotlin
   private lateinit var myNewText: TextView

   private fun initViews() {
       // ... existing code ...
       myNewText = findViewById(R.id.myNewText)
   }
   ```

3. **Update View**:
   ```kotlin
   private fun updateMyFeature() {
       runOnUiThread {
           myNewText.text = "New value"
       }
   }
   ```

### Example: Adding a New Sensor Input

1. **Register Sensor** (`MainActivity.kt`):
   ```kotlin
   private var mySensor: Sensor? = null

   private fun initSensors() {
       // ... existing code ...
       mySensor = sensorManager.getDefaultSensor(Sensor.TYPE_YOUR_SENSOR)
       sensorManager.registerListener(this, mySensor, SensorManager.SENSOR_DELAY_GAME)
   }
   ```

2. **Handle Events**:
   ```kotlin
   override fun onSensorChanged(event: SensorEvent) {
       when (event.sensor.type) {
           Sensor.TYPE_YOUR_SENSOR -> {
               // Process sensor data
               vrRenderer.setMySensorData(event.values)
           }
       }
   }
   ```

---

## Debugging Tips

### Logging

**Enable verbose logging**:
```bash
adb logcat -s MainActivity:D VRRenderer:D TimecodeParamLoader:D
```

**Key log tags**:
- `MainActivity`: Video loading, UI events
- `VRRenderer`: Render loop, parameter updates
- `TimecodeParamLoader`: File search, JSON parsing
- `StepController`: Step detection, speed calculation

### Common Issues

#### Issue: Parameter file not found
**Check**:
1. Log shows: "Searching for parameter file in folder tree: ..."
   - If missing → Folder URI is null (no folder selected)
2. File name matches exactly (case-sensitive)
3. File is in same folder as video

**Solution**:
```bash
adb logcat | grep "parameter"
# Look for: "Found parameter file in tree: ..." or "Parameter file not found"
```

#### Issue: Video permission denied
**Check**:
1. Tree URI permissions persisted?
2. App has READ_MEDIA_VIDEO permission (Android 13+)?

**Solution**:
```kotlin
// Check permissions
contentResolver.persistedUriPermissions.forEach { permission ->
    Log.d("Permissions", "URI: ${permission.uri}")
}
```

#### Issue: Overlay not showing
**Check**:
1. `timecodeOverlayText.visibility` is VISIBLE?
2. Overlay has non-empty text?
3. Background color visible? (Try #FFFF0000 for testing)

**Solution**:
```bash
adb logcat | grep "Overlay"
# Look for: "Active overlay: ..." in VRRenderer
```

### Performance Profiling

**GPU Rendering Profile**:
```bash
adb shell setprop debug.hwui.profile visual_bars
# Green bars = 60 FPS (good)
# Yellow/Red bars = dropped frames (bad)
```

**Frame timing**:
```kotlin
// Add to VRRenderer.onDrawFrame()
private var frameCount = 0
private var lastFpsTime = System.currentTimeMillis()

fun onDrawFrame(gl: GL10?) {
    frameCount++
    val now = System.currentTimeMillis()
    if (now - lastFpsTime > 1000) {
        Log.d("VRRenderer", "FPS: $frameCount")
        frameCount = 0
        lastFpsTime = now
    }
    // ... rest of render code ...
}
```

---

## Configuration Files

### build.gradle.kts
**Dependencies**:
- `androidx.recyclerview:recyclerview:1.3.2` - Video list
- `kotlinx-coroutines-android:1.7.3` - Async operations
- Minimum SDK: 26 (Android 8.0)
- Target SDK: 34 (Android 14)

### AndroidManifest.xml
**Permissions**:
- `ACTIVITY_RECOGNITION` - Step counting
- `READ_MEDIA_VIDEO` - Video access (Android 13+)
- `READ_EXTERNAL_STORAGE` - Legacy video access

**Required Features**:
- OpenGL ES 3.0
- Gyroscope sensor
- Accelerometer sensor

**Screen Orientations**:
- `MainActivity`: `sensorLandscape` (VR mode)
- `SettingsActivity`: `sensorLandscape` (landscape for VR headset)

### SharedPreferences Keys
Stored in `"VRLAPrefs"`:
- `video_uri`: Last selected video URI
- `video_folder_uri`: Selected folder tree URI
- `stereo_mode`: Boolean (true = 3D)
- `ipd`: Float (50-80mm range)
- `video_rotation`: Int (0, 90, 180, 270)
- `stride_length`: Float (meters)
- `calories_per_km`: Float
- `min_speed`: Float (0.0-1.0)
- `min_speed_moving`: Float (0.0-1.0)
- `max_speed`: Float (1.0-2.0)
- `volume`: Float (0.0-1.0)

---

## Android-Specific Notes

### Scoped Storage (Android 10+)

**Problem**: Apps cannot access arbitrary files, only:
1. Files explicitly selected by user (via `ACTION_OPEN_DOCUMENT`)
2. Files in app-specific directory
3. Files in folder with tree URI permission (via `ACTION_OPEN_DOCUMENT_TREE`)

**Solution Used**: Folder selection (Workflow A)
- User selects folder once
- App stores tree URI with persistent permissions
- App can access all files in that folder

**API Used**:
```kotlin
// Build URI for folder children
val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
    folderTreeUri,
    DocumentsContract.getTreeDocumentId(folderTreeUri)
)

// Query all files
val cursor = contentResolver.query(
    childrenUri,
    arrayOf(COLUMN_DOCUMENT_ID, COLUMN_DISPLAY_NAME, COLUMN_MIME_TYPE),
    null, null, null
)
```

### MediaPlayer Lifecycle

**Important**: Must release MediaPlayer resources properly

```kotlin
// In VRRenderer.kt
fun release() {
    mediaPlayer?.let {
        try {
            if (it.isPlaying) it.stop()
            it.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaPlayer: ${e.message}")
        }
    }
    mediaPlayer = null
}
```

**Called from**:
- `MainActivity.onDestroy()`
- `MainActivity.onPause()` (pause playback)
- `MainActivity.onResume()` (resume playback)

### OpenGL Context

**Important**: All OpenGL calls must happen on GLThread

```kotlin
// WRONG - crashes
fun someMethod() {
    glUniform1f(...)  // Called from UI thread - CRASH
}

// CORRECT - queue for GL thread
glSurfaceView.queueEvent {
    glUniform1f(...)  // Executed on GL thread
}
```

### UI Updates from Renderer

**Important**: Cannot update UI directly from render thread

```kotlin
// In VRRenderer.kt
private var onOverlayUpdate: ((OverlayConfig?) -> Unit)? = null

fun setOverlayUpdateCallback(callback: (OverlayConfig?) -> Unit) {
    onOverlayUpdate = callback
}

private fun updateTimecodeParameters() {
    // ... update parameters ...
    onOverlayUpdate?.invoke(getCurrentOverlay())  // Triggers callback
}

// In MainActivity.kt
vrRenderer.setOverlayUpdateCallback { overlay ->
    runOnUiThread {  // Switch to UI thread
        updateTimecodeOverlay(overlay)
    }
}
```

---

## Version History

### v2.1 (Current)
- Timecode parameter system with JSON file support
- Folder-based video selection (Workflow A)
- Video list UI with parameter file detection
- Landscape mode for all screens
- Optimized stats overlay (minimal height)
- Pink transparent overlay for JSON text
- Info box with step-by-step instructions

### v2.0
- VR video playback with head tracking
- Step-based speed control
- Settings screen with all parameters
- Stereo/mono mode switching
- Fitness tracking (distance, calories)

### v1.0
- Initial release
- Basic video playback
- Simple UI

---

## Future Considerations

### Potential Improvements
1. **Caching**: Cache parsed JSON files to avoid re-parsing
2. **Validation**: JSON schema validation with error reporting
3. **Editor**: In-app parameter file editor
4. **Analytics**: Track which parameters improve user experience
5. **Multi-language**: Support for overlay text in multiple languages
6. **Transitions**: Smooth parameter transitions instead of instant changes

### Known Limitations
1. **File Format**: Only supports MP4 videos
2. **Resolution**: High-res videos (4K+) may cause performance issues on older devices
3. **JSON Size**: Very large JSON files (>1MB) may cause loading delays
4. **Timecode Precision**: Parameters update every 30 frames (~500ms at 60fps)

---

## Getting Help

### Documentation
- Parameter file format: `app/src/main/assets/parameters/README.md`
- Android Scoped Storage: https://developer.android.com/training/data-storage
- OpenGL ES: https://developer.android.com/develop/ui/views/graphics/opengl

### Debugging Commands
```bash
# View app logs
adb logcat -s MainActivity:D VRRenderer:D TimecodeParamLoader:D

# Check permissions
adb shell run-as com.vrla.forest ls /data/data/com.vrla.forest/shared_prefs/

# Clear app data (reset everything)
adb shell pm clear com.vrla.forest

# Install debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

**Last Updated**: 2025-11-16
**Maintained By**: Development Team
**Update Policy**: Update this file when pushing to master or after major architectural changes
