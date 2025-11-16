# VRLA - VR Fitness Experience

Android VR app for immersive 360° video playback with step-based speed control. Designed for fitness/jogging videos in a VR headset with real-time step tracking.

## Features

- 🎥 **360° Stereo/Mono Video Playback** - Side-by-side stereo or mono equirectangular video
- 🎮 **Full 6DOF Head Tracking** - Gyroscope-based rotation tracking (pitch, yaw, roll)
- 👟 **Step Counter Integration** - Real-time playback speed based on walking/jogging pace
- 📊 **Live Stats** - Steps, distance, calories, time, current speed
- ⚙️ **Customizable Settings** - IPD, video volume, step speed thresholds
- 🔄 **Orientation Calibration** - Auto-calibration on start + manual recalibration
- 📱 **In-Headset Controls** - Volume button controls (no need to remove headset)
- 🎯 **Optimized Performance** - Runs smoothly on mid-range devices (Samsung S8+)

## Requirements

### Hardware
- Android device with:
  - Gyroscope sensor (REQUIRED)
  - Step counter sensor (recommended)
  - OpenGL ES 3.0 support
- VR headset (Cardboard-compatible)

### Software
- Android 8.0 (API 26) or higher
- Android Studio (for development)

## Quick Start

### 1. Clone Repository
```bash
git clone https://github.com/yourusername/VR-Left.git
cd VR-Left
```

### 2. Add Video
Place your 360° video file in one of these locations:
- **Recommended**: `/sdcard/Movies/forest_jog.mp4` or `/sdcard/Videos/forest_jog.mp4`
- **Alternative**: Use file picker on first launch to select any video

### 3. Build & Install
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. First Run
1. Grant permissions (Activity Recognition, Storage)
2. Select video if not auto-detected
3. Place phone in VR headset (landscape orientation)
4. Put on headset and look straight ahead
5. Start walking - video begins automatically

## Video Format Support

### Recommended Formats
- **Projection**: Equirectangular 360°
- **Layout**: Side-by-side stereo OR mono (configurable in settings)
- **Codec**: H.264 or H.265
- **Resolution**: Up to 4K (3840x2160)
- **Aspect Ratio**: 2:1 (360° equirectangular standard)

### Tested Cameras
- Insta360 cameras (side-by-side stereo export)
- Most 360° cameras with equirectangular output

## Architecture Overview

### Core Components

```
MainActivity.kt          - Activity lifecycle, sensor management, UI updates
VRRenderer.kt           - OpenGL ES rendering, head tracking, video playback
StepController.kt       - Step counting logic, speed calculation
SettingsActivity.kt     - User settings management
AppConfig.kt            - Global configuration singleton
```

### Head Tracking System

The app uses **rotation matrices** instead of Euler angles to avoid gimbal lock:

```
Sensor → Coordinate Remap → Calibration → View Matrix → Rendering
  ↓            ↓                 ↓              ↓            ↓
Rotation   Landscape        Offset to      Camera       OpenGL
Vector     Orientation      "Forward"    Transforms    Display
```

**Key Implementation Details:**

1. **Sensor Input** (`MainActivity.kt:561-586`)
   - Uses `TYPE_ROTATION_VECTOR` (fused gyro + accel + mag)
   - 50-100 updates/second for smooth tracking

2. **Coordinate Remapping** (`MainActivity.kt:576-581`)
   - Phone is rotated 90° in VR headset
   - Remaps: `AXIS_MINUS_Y → X`, `AXIS_X → Y`
   - Converts portrait → landscape coordinate system

3. **Calibration** (`VRRenderer.kt:492-500`)
   - Stores inverse of current rotation as "forward"
   - Uses **uncalibrated** rotation to prevent stacking
   - Applied via matrix multiplication: `calibration * current`

4. **View Matrices** (`VRRenderer.kt:152-179`)
   - Separate matrices for left/right eyes
   - IPD (Inter-Pupillary Distance) offset for stereo 3D
   - Matrix order: `translation * rotation` (critical!)

### Video Rendering Pipeline

```
MediaPlayer → SurfaceTexture → OpenGL Texture → Sphere Geometry → Stereo Views
```

1. **MediaPlayer** plays video onto `SurfaceTexture`
2. **SurfaceTexture** provides texture via `GL_TEXTURE_EXTERNAL_OES`
3. **Sphere Geometry** is a UV-mapped sphere (inside-out rendering)
4. **Vertex Shader** applies MVP matrix + texture scaling
5. **Fragment Shader** samples video texture
6. **Stereo Rendering** renders twice (left/right eyes) with viewport split

### Step-Based Speed Control

```
Step Counter → StepController → Speed Calculation → MediaPlayer.setSpeed()
```

**Speed Formula:**
```kotlin
speed = minSpeed + (currentStepsPerMin / baseStepsPerMin) * (maxSpeed - minSpeed)
```

- **Idle** (< 60 steps/min): `minSpeed` (0.3x default)
- **Walking** (~100 steps/min): ~1.0x
- **Jogging** (~140+ steps/min): `maxSpeed` (2.0x default)

## Controls

### In-Headset (Volume Buttons)
- **Double Volume Up**: Recalibrate head orientation
- **Double Volume Down**: Restart session

### Settings Menu (Touch Controls)
Access via ⋮ button when phone is out of headset:
- Video selection
- Stereo/Mono mode toggle
- IPD adjustment (eye separation)
- Video volume
- Step speed thresholds

## Development Guide

### Project Structure

```
app/src/main/
├── java/com/vrla/forest/
│   ├── MainActivity.kt         - Main VR activity
│   ├── VRRenderer.kt          - OpenGL rendering & head tracking
│   ├── StepController.kt      - Step counting logic
│   ├── SettingsActivity.kt    - Settings UI
│   └── AppConfig.kt           - Global config
├── res/
│   ├── layout/                - UI layouts
│   ├── values/                - Strings, colors, styles
│   └── mipmap/                - App icons
└── AndroidManifest.xml        - Permissions, screen orientation
```

### Key Code Sections

#### 1. Head Tracking (`VRRenderer.kt`)
```kotlin
// Lines 419-476: updateHeadRotation()
// - Converts 3x3 sensor matrix to 4x4 OpenGL matrix
// - Stores uncalibrated rotation for calibration
// - Applies calibration if active

// Lines 492-500: calibrateOrientation()
// - Makes current head position the new "forward"
// - Uses transpose (inverse) of uncalibrated rotation
```

#### 2. Coordinate Remapping (`MainActivity.kt`)
```kotlin
// Lines 576-581: Landscape coordinate system remapping
SensorManager.remapCoordinateSystem(
    rotationMatrix,
    SensorManager.AXIS_MINUS_Y,  // new X
    SensorManager.AXIS_X,        // new Y
    remappedRotationMatrix
)
```

#### 3. Stereo Rendering (`VRRenderer.kt`)
```kotlin
// Lines 152-179: setupViewMatrices()
// - Creates separate view matrices for left/right eyes
// - Applies IPD offset in camera space
// - Critical: viewMatrix = translation * rotation

// Lines 127-136: Split-screen rendering
// - Left eye: viewport (0, 0, width/2, height)
// - Right eye: viewport (width/2, 0, width/2, height)
```

### Important Notes for Maintainers

#### Why Rotation Matrices?
**DO NOT** convert to Euler angles! The original implementation used Euler angles and suffered from gimbal lock at 90° pitch. Rotation matrices are:
- Native output from Android sensors
- Free from singularities
- More accurate for VR

#### Matrix Multiplication Order
The IPD offset **MUST** be applied in camera space, not world space:
```kotlin
// CORRECT:
Matrix.multiplyMM(viewMatrix, 0, translation, 0, rotation, 0)

// WRONG (causes IPD to rotate with head):
Matrix.multiplyMM(viewMatrix, 0, rotation, 0, translation, 0)
```

#### Calibration Stacking Prevention
Always use `uncalibratedRotation` for calibration:
```kotlin
// CORRECT:
Matrix.transposeM(calibrationMatrix, 0, uncalibratedRotation, 0)

// WRONG (causes stacking, position shifts):
Matrix.transposeM(calibrationMatrix, 0, headRotationMatrix, 0)
```

## Known Issues & Solutions

### Issue: Video shows sky instead of forward
**Cause**: Video was filmed with camera pointing up
**Solution**: Model matrix applies -90° pitch rotation (VRRenderer.kt:156)

### Issue: Recalibration causes "looking down" view
**Status**: Under investigation
**Workaround**: Hold head perfectly still when double-pressing Volume Up

### Issue: Video appears rotated 180° when phone flips
**Solution**: Screen orientation locked to `landscape` (AndroidManifest.xml:23)

### Issue: Half of mono video is cut off
**Cause**: Texture scaling was hard-coded for stereo
**Solution**: Dynamic scaling based on `AppConfig.stereoMode` (VRRenderer.kt:125)

## Troubleshooting

### No head tracking
- **Check**: Device has gyroscope sensor
- **Check**: App shows "Rotation Sensor fehlt" dialog
- **Fix**: Gyroscope is required, app won't work without it

### Inverted or weird head movement
- **Check**: Phone orientation in headset
- **Expected**: Phone rotated 90° LEFT (volume buttons on top)
- **Fix**: Adjust coordinate remapping in MainActivity.kt:576-581

### Video won't start
- **Check**: Video file is accessible
- **Check**: Logcat for MediaPlayer errors
- **Common**: Video codec not supported (use H.264)

### Steps not counting
- **Check**: Activity Recognition permission granted
- **Check**: Device has step counter sensor
- **Fallback**: Speed won't auto-adjust, stays at minSpeed

## Configuration

### Edit AppConfig.kt for defaults:
```kotlin
var ipd = 0.063f              // Inter-pupillary distance (meters)
var stereoMode = true         // Side-by-side stereo video
var videoVolume = 1.0f        // Volume (0.0 - 1.0)
```

### Edit StepController.kt for step speeds:
```kotlin
private val minSpeed = 0.3f            // Idle speed
private val maxSpeed = 2.0f            // Max jogging speed
private val baseStepsPerMinute = 100   // Steps/min for 1.0x speed
```

## Performance Optimization

### Shader Locations Cached
All uniform/attribute locations are cached after program creation (VRRenderer.kt:84-89) instead of querying every frame.

### SurfaceTexture Updates
Only updates texture when new frame available (VRRenderer.kt:116-119), not every render call.

### Matrix Reuse
All matrices pre-allocated as FloatArray(16), no per-frame allocations.

## Version History

- **v1.5** - Lock screen orientation to landscape (prevent 180° rotation bug)
- **v1.4** - Fix calibration stacking bug (use uncalibratedRotation)
- **v1.3** - Fix video orientation (-90° pitch), immediate video start
- **v1.2** - Fix texture mapping for mono mode (dynamic scaling)
- **v1.1** - Switch to rotation matrix approach (avoid gimbal lock)
- **v1.0** - Initial release

## License

MIT License - See LICENSE file for details

## Contributing

When contributing, please:
1. **Preserve rotation matrix approach** - Don't convert to Euler angles
2. **Test head tracking thoroughly** - All axes (pitch, yaw, roll)
3. **Verify calibration** - Test both auto and manual recalibration
4. **Check performance** - Maintain 60+ FPS on mid-range devices
5. **Update this README** - Document any architectural changes

## Credits

Developed for VR fitness experiences with 360° jogging videos.
Tested with Insta360 camera footage and Cardboard-style VR headsets.
