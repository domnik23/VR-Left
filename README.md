# VRLA - VR  Experience

Android VR app for immersive jogging with step counter integration.

## Features
- 360° Stereo VR video playback
- Head tracking with gyroscope
- Step counter controls playback speed
- Real-time stats (steps, distance, calories, time)
- Optimized for mid-range devices (Samsung S8+)

## Requirements
- Android 8.0 (API 26) or higher
- Gyroscope sensor
- Step counter sensor
- OpenGL ES 3.0
- VR headset (Cardboard-compatible)

## Setup
1. Clone the repository
2. Add your 360° video as `app/src/main/res/raw/forest_jog.mp4`
3. Open project in Android Studio
4. Build and run

## Video Format
- Side-by-Side 360° Stereo
- H.264/H.265 codec
- Up to 4K resolution
- Filmed with Insta360 camera

## Customization
Edit `StepController.kt` to adjust speed settings:
- `minSpeed` - Minimum playback speed
- `maxSpeed` - Maximum playback speed
- `baseStepsPerMinute` - Steps/min for 1.0x speed

## License
MIT
