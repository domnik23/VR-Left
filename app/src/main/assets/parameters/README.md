# Timecode Parameter Files

This folder contains JSON configuration files that define time-based video parameters and text overlays for the VR jogging experience.

## File Naming Convention

Parameter files must match the video filename with a `.json` extension:
- Video: `forest_jog.mp4` → Parameters: `forest_jog.json`
- Video: `mountain_run.mp4` → Parameters: `mountain_run.json`

## File Locations

The app searches for parameter files in this order:
1. **Internal storage**: `/Android/data/com.vrla.forest/files/parameters/`
2. **Assets folder**: `assets/parameters/` (this folder)

## JSON Structure

```json
{
  "videoFile": "forest_jog.mp4",
  "timecodes": [
    {
      "timeRange": "MM:SS-MM:SS",
      "parameters": {
        "minSpeed": 0.4,
        "minSpeedMoving": 0.7,
        "maxSpeed": 1.5,
        "videoRotation": -90,
        "videoVolume": 0.5,
        "caloriesPerKm": 60,
        "averageStrideLength": 0.75
      },
      "overlay": {
        "text": "Welcome! Let's start jogging! 🏃",
        "position": "center",
        "textSize": 24,
        "textColor": "#FFFFFF",
        "backgroundColor": "#CC000000"
      }
    }
  ]
}
```

## Timecode Ranges

### Time Format
- **MM:SS** - Minutes and seconds (e.g., `"02:30"` = 2 minutes 30 seconds)
- **HH:MM:SS** - Hours, minutes, and seconds (e.g., `"01:02:30"` = 1 hour 2 minutes 30 seconds)

### Range Syntax
`"timeRange": "START-END"`
- Examples:
  - `"00:00-02:30"` - From start to 2 minutes 30 seconds
  - `"02:30-05:00"` - From 2:30 to 5:00
  - `"01:00:00-01:30:00"` - From 1 hour to 1 hour 30 minutes

## Parameters

All parameters are **optional**. Only specified parameters will override the default values from the app settings.

### Speed Parameters
- **`minSpeed`** (0.0 - 1.0): Playback speed when idle (< 10 steps/min)
  - Default: 0.4
  - Example: 0.5 = 50% speed

- **`minSpeedMoving`** (0.0 - 1.0): Minimum speed when walking starts (10 steps/min)
  - Default: 0.7
  - Example: 0.8 = 80% speed

- **`maxSpeed`** (1.0 - 2.0): Maximum speed when jogging fast (≥ 120 steps/min)
  - Default: 1.5
  - Example: 2.0 = 200% speed (double speed)

### Video Parameters
- **`videoRotation`** (-180 to 180): Video orientation correction in degrees
  - Default: -90
  - Positive = rotate up, Negative = rotate down

- **`videoVolume`** (0.0 - 1.0): Audio volume
  - Default: 0.5
  - 0.0 = mute, 1.0 = full volume

### Fitness Parameters
- **`caloriesPerKm`** (30 - 130): Calorie burn rate per kilometer
  - Default: 60 kcal/km
  - Use higher values for uphill sections (e.g., 80-100)
  - Use lower values for downhill sections (e.g., 40-50)

- **`averageStrideLength`** (0.5 - 1.0): Stride length in meters
  - Default: 0.75m
  - Walking: 0.6 - 0.8m
  - Jogging: 0.8 - 1.0m

## Text Overlays

Display motivational messages, terrain information, or instructions at specific times.

### Overlay Fields

- **`text`** (required): The message to display
  - Supports emojis: 🏃 💪 ⭐ 🔥 🌄
  - Supports line breaks with `\n`

- **`position`** (optional): Vertical position
  - `"top"` - Top of screen
  - `"center"` - Middle of screen (default)
  - `"bottom"` - Bottom of screen

- **`textSize`** (optional): Text size in SP
  - Default: 18
  - Range: 12 - 36

- **`textColor`** (optional): Text color in hex format
  - Default: `"#FFFFFF"` (white)
  - Examples:
    - `"#FF0000"` - Red
    - `"#00FF00"` - Green
    - `"#FFFF00"` - Yellow

- **`backgroundColor`** (optional): Background color with alpha
  - Default: `"#CC000000"` (semi-transparent black)
  - Format: `#AARRGGBB` (AA = alpha/transparency)
  - Examples:
    - `"#CC000000"` - 80% transparent black
    - `"#FF000000"` - Fully opaque black
    - `"#00000000"` - Fully transparent

## Use Cases

### 1. Terrain Simulation
Adjust speed ranges for different terrain types:
```json
{
  "timeRange": "02:00-04:00",
  "parameters": {
    "minSpeed": 0.5,
    "maxSpeed": 1.8,
    "caloriesPerKm": 80
  },
  "overlay": {
    "text": "Going uphill! 💪",
    "position": "bottom"
  }
}
```

### 2. Interval Training
Create structured workout intervals:
```json
{
  "timeRange": "05:00-07:00",
  "parameters": {
    "minSpeedMoving": 1.0,
    "maxSpeed": 2.0
  },
  "overlay": {
    "text": "Sprint interval - 2 minutes! 🔥",
    "textColor": "#FF0000"
  }
}
```

### 3. Guided Tours
Add narrative and points of interest:
```json
{
  "timeRange": "03:30-03:45",
  "overlay": {
    "text": "On your left, you'll see the forest lake 🌊",
    "position": "bottom",
    "textSize": 20
  }
}
```

### 4. Motivation Milestones
Celebrate achievements:
```json
{
  "timeRange": "10:00-10:15",
  "overlay": {
    "text": "10 minutes done! You're amazing! ⭐",
    "position": "center",
    "textSize": 24,
    "textColor": "#00FF00"
  }
}
```

## Creating Your Own Parameter Files

1. **Create a JSON file** matching your video name
2. **Test locally** by placing it in the app's internal storage:
   - Connect device to computer
   - Navigate to: `Android/data/com.vrla.forest/files/parameters/`
   - Copy your `.json` file there
3. **Package with app** by placing it in `assets/parameters/` and rebuilding

## Tips

- **Keep overlays short**: 5-10 seconds is ideal for reading while exercising
- **Use contrasting colors**: Ensure text is readable against video background
- **Test your timings**: Video timestamps are in playback time (affected by speed changes)
- **Gradual transitions**: Avoid sudden speed changes - use overlapping timecodes for smooth transitions
- **Emojis enhance motivation**: Use them sparingly for visual impact

## Troubleshooting

- **Parameters not loading?**
  - Check file name matches video exactly (case-sensitive)
  - Verify JSON syntax is valid (use a JSON validator)
  - Check app logs for error messages

- **Overlay not showing?**
  - Verify `timeRange` format is correct
  - Ensure time falls within video duration
  - Check that `text` field is not empty

- **Wrong timing?**
  - Remember: times are in playback time, not real time
  - Speed changes affect when timecodes are reached

## Example Files

See `forest_jog.json` in this folder for a complete working example with:
- Welcome message at start
- Uphill section with increased difficulty
- Halfway milestone celebration
- Downhill cooldown
- Final sprint encouragement
