# VRLA Forest Jog - Automatic Project Setup Script for Windows
# PowerShell Script to create complete Android Studio project

Write-Host "🚀 VRLA Forest Jog - Project Setup Starting..." -ForegroundColor Green
Write-Host ""

# Project configuration
$PROJECT_NAME = "VRLA"
$PACKAGE_PATH = "com/vrla/forest"

# Create project root
Write-Host "📁 Creating project directory: $PROJECT_NAME" -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $PROJECT_NAME | Out-Null
Set-Location $PROJECT_NAME

# Create directory structure
Write-Host "📂 Creating directory structure..." -ForegroundColor Cyan
$directories = @(
    "app/src/main/java/$PACKAGE_PATH",
    "app/src/main/res/layout",
    "app/src/main/res/values",
    "app/src/main/res/raw",
    "app/src/main/res/mipmap-mdpi",
    "app/src/main/res/mipmap-hdpi",
    "app/src/main/res/mipmap-xhdpi",
    "app/src/main/res/mipmap-xxhdpi",
    "app/src/main/res/mipmap-xxxhdpi",
    "gradle/wrapper"
)

foreach ($dir in $directories) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

Write-Host "✅ Directory structure created" -ForegroundColor Green
Write-Host ""

# ============================================
# 1. settings.gradle.kts
# ============================================
Write-Host "⚙️  Creating settings.gradle.kts..." -ForegroundColor Cyan
@'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "VRLA"
include(":app")
'@ | Out-File -FilePath "settings.gradle.kts" -Encoding UTF8

# ============================================
# 2. build.gradle.kts (Project level)
# ============================================
Write-Host "⚙️  Creating build.gradle.kts (Project)..." -ForegroundColor Cyan
@'
// Top-level build file
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
'@ | Out-File -FilePath "build.gradle.kts" -Encoding UTF8

# ============================================
# 3. build.gradle.kts (App level)
# ============================================
Write-Host "⚙️  Creating build.gradle.kts (App)..." -ForegroundColor Cyan
@'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vrla.forest"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vrla.forest"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
'@ | Out-File -FilePath "app/build.gradle.kts" -Encoding UTF8

# ============================================
# 4. gradle.properties
# ============================================
Write-Host "⚙️  Creating gradle.properties..." -ForegroundColor Cyan
@'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
'@ | Out-File -FilePath "gradle.properties" -Encoding UTF8

# ============================================
# 5. gradle-wrapper.properties
# ============================================
Write-Host "⚙️  Creating gradle-wrapper.properties..." -ForegroundColor Cyan
@'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
'@ | Out-File -FilePath "gradle/wrapper/gradle-wrapper.properties" -Encoding UTF8

# ============================================
# 6. AndroidManifest.xml
# ============================================
Write-Host "📄 Creating AndroidManifest.xml..." -ForegroundColor Cyan
@'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    
    <uses-feature android:glEsVersion="0x00030000" android:required="true" />
    <uses-feature android:name="android.hardware.sensor.gyroscope" android:required="true" />
    <uses-feature android:name="android.hardware.sensor.accelerometer" android:required="true" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.AppCompat.NoActionBar"
        android:hardwareAccelerated="true">
        
        <activity
            android:name=".MainActivity"
            android:screenOrientation="sensorLandscape"
            android:configChanges="orientation|keyboardHidden|screenSize"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
'@ | Out-File -FilePath "app/src/main/AndroidManifest.xml" -Encoding UTF8

# ============================================
# 7. strings.xml
# ============================================
Write-Host "📄 Creating strings.xml..." -ForegroundColor Cyan
@'
<resources>
    <string name="app_name">VRLA Forest Jog</string>
</resources>
'@ | Out-File -FilePath "app/src/main/res/values/strings.xml" -Encoding UTF8

# ============================================
# 8. activity_main.xml
# ============================================
Write-Host "📄 Creating activity_main.xml..." -ForegroundColor Cyan
@'
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <android.opengl.GLSurfaceView
        android:id="@+id/glSurfaceView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="invisible" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:gravity="center"
        android:background="#000000">

        <Button
            android:id="@+id/startButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Start VR Experience"
            android:textSize="20sp"
            android:padding="20dp"
            android:backgroundTint="#4CAF50" />

    </LinearLayout>

    <LinearLayout
        android:id="@+id/overlayContainer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="top|center_horizontal"
        android:layout_marginTop="40dp"
        android:orientation="vertical"
        android:gravity="center"
        android:padding="20dp"
        android:background="#80000000"
        android:visibility="gone">

        <TextView
            android:id="@+id/stepCountText"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Steps: 0"
            android:textColor="#FFFFFF"
            android:textSize="24sp"
            android:textStyle="bold"
            android:shadowColor="#000000"
            android:shadowDx="2"
            android:shadowDy="2"
            android:shadowRadius="4" />

        <TextView
            android:id="@+id/speedText"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Speed: 0.3x"
            android:textColor="#FFFFFF"
            android:textSize="18sp"
            android:layout_marginTop="8dp"
            android:shadowColor="#000000"
            android:shadowDx="2"
            android:shadowDy="2"
            android:shadowRadius="4" />

        <TextView
            android:id="@+id/timeText"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="00:00"
            android:textColor="#FFFFFF"
            android:textSize="18sp"
            android:layout_marginTop="4dp"
            android:shadowColor="#000000"
            android:shadowDx="2"
            android:shadowDy="2"
            android:shadowRadius="4" />

        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginTop="8dp">

            <TextView
                android:id="@+id/distanceText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Distance: 0.00 km"
                android:textColor="#FFFFFF"
                android:textSize="16sp"
                android:layout_marginEnd="16dp"
                android:shadowColor="#000000"
                android:shadowDx="2"
                android:shadowDy="2"
                android:shadowRadius="4" />

            <TextView
                android:id="@+id/caloriesText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Calories: 0 kcal"
                android:textColor="#FFFFFF"
                android:textSize="16sp"
                android:shadowColor="#000000"
                android:shadowDx="2"
                android:shadowDy="2"
                android:shadowRadius="4" />

        </LinearLayout>

    </LinearLayout>

    <Button
        android:id="@+id/calibrateButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|center_horizontal"
        android:layout_marginBottom="40dp"
        android:text="Recalibrate"
        android:textSize="16sp"
        android:padding="16dp"
        android:backgroundTint="#FF9800"
        android:visibility="gone" />

</FrameLayout>
'@ | Out-File -FilePath "app/src/main/res/layout/activity_main.xml" -Encoding UTF8

# ============================================
# 9. MainActivity.kt
# ============================================
Write-Host "📄 Creating MainActivity.kt..." -ForegroundColor Cyan
@'
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
    
    private lateinit var startButton: Button
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
    }
    
    private fun initViews() {
        glSurfaceView = findViewById(R.id.glSurfaceView)
        startButton = findViewById(R.id.startButton)
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
        glSurfaceView.visibility = View.INVISIBLE
        
        startButton.setOnClickListener { startVRExperience() }
        calibrateButton.setOnClickListener { calibrateOrientation() }
        calibrateButton.visibility = View.GONE
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
            if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                stepCountText.text = "Permission needed for step tracking"
            }
        }
    }
    
    private fun startVRExperience() {
        if (isVRActive) return
        
        isVRActive = true
        startTime = System.currentTimeMillis()
        sessionSteps = 0
        
        startButton.visibility = View.GONE
        glSurfaceView.visibility = View.VISIBLE
        overlayContainer.visibility = View.VISIBLE
        calibrateButton.visibility = View.VISIBLE
        
        rotationVector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        stepCounter?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            totalSteps = 0
        }
        
        vrRenderer.startVideo()
        startUIUpdateLoop()
        calibrateOrientation()
    }
    
    private fun calibrateOrientation() {
        calibrationYaw = orientationAngles[0]
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
        
        stepCountText.text = "Steps: $sessionSteps"
        speedText.text = String.format("Speed: %.1fx", currentSpeed)
        timeText.text = formatTime(elapsedSeconds)
        distanceText.text = String.format("Distance: %.2f km", distance)
        caloriesText.text = String.format("Calories: %d kcal", calories)
        
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
                } else {
                    val currentTotal = event.values[0].toInt()
                    val newSteps = currentTotal - totalSteps
                    if (newSteps > 0 && newSteps < 100) {
                        sessionSteps += newSteps
                        totalSteps = currentTotal
                        stepController.addStep()
                        
                        if (!isVRActive && sessionSteps > 5) {
                            startVRExperience()
                        }
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
'@ | Out-File -FilePath "app/src/main/java/$PACKAGE_PATH/MainActivity.kt" -Encoding UTF8

# ============================================
# 10. VRRenderer.kt
# ============================================
Write-Host "📄 Creating VRRenderer.kt..." -ForegroundColor Cyan
@'
package com.vrla.forest

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class VRRenderer(private val context: Context) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    
    private var surfaceTexture: SurfaceTexture? = null
    private var mediaPlayer: MediaPlayer? = null
    private var textureId = 0
    private var program = 0
    
    private lateinit var sphereVertices: FloatBuffer
    private lateinit var sphereTexCoords: FloatBuffer
    private lateinit var sphereIndices: IntArray
    
    private val projectionMatrix = FloatArray(16)
    private val viewMatrixLeft = FloatArray(16)
    private val viewMatrixRight = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    
    private var yaw = 0f
    private var pitch = 0f
    private var roll = 0f
    
    private var playbackSpeed = 0.3f
    private var updateTexture = false
    
    private val sphereRadius = 10f
    private val sphereStacks = 30
    private val sphereSectors = 60
    private val ipd = 0.064f
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        
        program = createProgram()
        createSphereGeometry()
        textureId = createTexture()
        
        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener(this@VRRenderer)
        }
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat() / 2f
        Matrix.perspectiveM(projectionMatrix, 0, 90f, ratio, 0.1f, 100f)
    }
    
    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        
        if (updateTexture) {
            surfaceTexture?.updateTexImage()
            updateTexture = false
        }
        
        GLES30.glUseProgram(program)
        setupViewMatrices()
        
        val width = GLES30.GL_MAX_VIEWPORT_DIMS
        GLES30.glViewport(0, 0, width / 2, width)
        drawSphere(viewMatrixLeft, 0f)
        
        GLES30.glViewport(width / 2, 0, width / 2, width)
        drawSphere(viewMatrixRight, 0.5f)
    }
    
    private fun setupViewMatrices() {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, -pitch, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, -yaw, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, -roll, 0f, 0f, 1f)
        
        Matrix.setIdentityM(viewMatrixLeft, 0)
        Matrix.translateM(viewMatrixLeft, 0, ipd / 2f, 0f, 0f)
        Matrix.multiplyMM(viewMatrixLeft, 0, viewMatrixLeft, 0, modelMatrix, 0)
        
        Matrix.setIdentityM(viewMatrixRight, 0)
        Matrix.translateM(viewMatrixRight, 0, -ipd / 2f, 0f, 0f)
        Matrix.multiplyMM(viewMatrixRight, 0, viewMatrixRight, 0, modelMatrix, 0)
    }
    
    private fun drawSphere(viewMatrix: FloatArray, texOffsetU: Float) {
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        
        val positionHandle = GLES30.glGetAttribLocation(program, "aPosition")
        val texCoordHandle = GLES30.glGetAttribLocation(program, "aTexCoord")
        val mvpMatrixHandle = GLES30.glGetUniformLocation(program, "uMVPMatrix")
        val textureHandle = GLES30.glGetUniformLocation(program, "uTexture")
        val texOffsetHandle = GLES30.glGetUniformLocation(program, "uTexOffsetU")
        
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glUniform1i(textureHandle, 0)
        GLES30.glUniform1f(texOffsetHandle, texOffsetU)
        GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        
        sphereVertices.position(0)
        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glVertexAttribPointer(positionHandle, 3, GLES30.GL_FLOAT, false, 0, sphereVertices)
        
        sphereTexCoords.position(0)
        GLES30.glEnableVertexAttribArray(texCoordHandle)
        GLES30.glVertexAttribPointer(texCoordHandle, 2, GLES30.GL_FLOAT, false, 0, sphereTexCoords)
        
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, sphereIndices.size, GLES30.GL_UNSIGNED_INT, 
            IntBuffer.wrap(sphereIndices))
        
        GLES30.glDisableVertexAttribArray(positionHandle)
        GLES30.glDisableVertexAttribArray(texCoordHandle)
    }
    
    private fun createSphereGeometry() {
        val vertices = mutableListOf<Float>()
        val texCoords = mutableListOf<Float>()
        val indices = mutableListOf<Int>()
        
        for (i in 0..sphereStacks) {
            val lat = PI * i / sphereStacks - PI / 2
            val sinLat = sin(lat).toFloat()
            val cosLat = cos(lat).toFloat()
            
            for (j in 0..sphereSectors) {
                val lon = 2 * PI * j / sphereSectors
                val sinLon = sin(lon).toFloat()
                val cosLon = cos(lon).toFloat()
                
                vertices.add(-sphereRadius * cosLat * cosLon)
                vertices.add(sphereRadius * sinLat)
                vertices.add(-sphereRadius * cosLat * sinLon)
                
                texCoords.add(j.toFloat() / sphereSectors)
                texCoords.add(i.toFloat() / sphereStacks)
            }
        }
        
        for (i in 0 until sphereStacks) {
            for (j in 0 until sphereSectors) {
                val first = i * (sphereSectors + 1) + j
                val second = first + sphereSectors + 1
                
                indices.add(first)
                indices.add(second)
                indices.add(first + 1)
                
                indices.add(second)
                indices.add(second + 1)
                indices.add(first + 1)
            }
        }
        
        sphereVertices = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices.toFloatArray())
            .apply { position(0) }
        
        sphereTexCoords = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords.toFloatArray())
            .apply { position(0) }
        
        sphereIndices = indices.toIntArray()
    }
    
    private fun createProgram(): Int {
        val vertexShader = """
            #version 300 es
            in vec4 aPosition;
            in vec2 aTexCoord;
            uniform mat4 uMVPMatrix;
            uniform float uTexOffsetU;
            out vec2 vTexCoord;
            
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTexCoord = vec2(aTexCoord.x * 0.5 + uTexOffsetU, aTexCoord.y);
            }
        """.trimIndent()
        
        val fragmentShader = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            in vec2 vTexCoord;
            uniform samplerExternalOES uTexture;
            out vec4 fragColor;
            
            void main() {
                fragColor = texture(uTexture, vTexCoord);
            }
        """.trimIndent()
        
        val vShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexShader)
        val fShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentShader)
        
        return GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, vShader)
            GLES30.glAttachShader(it, fShader)
            GLES30.glLinkProgram(it)
        }
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES30.glCreateShader(type).also { shader ->
            GLES30.glShaderSource(shader, shaderCode)
            GLES30.glCompileShader(shader)
        }
    }
    
    private fun createTexture(): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        return textures[0]
    }
    
    fun startVideo() {
        mediaPlayer?.release()
        
        mediaPlayer = MediaPlayer.create(context, R.raw.forest_jog).apply {
            setSurface(Surface(surfaceTexture))
            isLooping = true
            playbackParams = playbackParams.setSpeed(playbackSpeed)
            start()
        }
    }
    
    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.1f, 3.0f)
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.playbackParams = it.playbackParams.setSpeed(playbackSpeed)
            }
        }
    }
    
    fun updateOrientation(yaw: Float, pitch: Float, roll: Float) {
        this.yaw = Math.toDegrees(yaw.toDouble()).toFloat()
        this.pitch = Math.toDegrees(pitch.toDouble()).toFloat()
        this.roll = Math.toDegrees(roll.toDouble()).toFloat()
    }
    
    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        updateTexture = true
    }
    
    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        surfaceTexture?.release()
        surfaceTexture = null
    }
}
'@ | Out-File -FilePath "app/src/main/java/$PACKAGE_PATH/VRRenderer.kt" -Encoding UTF8

# ============================================
# 11. StepController.kt
# ============================================
Write-Host "📄 Creating StepController.kt..." -ForegroundColor Cyan
@'
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
'@ | Out-File -FilePath "app/src/main/java/$PACKAGE_PATH/StepController.kt" -Encoding UTF8

# ============================================
# 12. Create placeholder icon
# ============================================
Write-Host "🎨 Creating placeholder launcher icon..." -ForegroundColor Cyan
# Create a simple text file as placeholder - user should replace with actual icon
@'
This is a placeholder for the launcher icon.
Replace the ic_launcher.png files in the mipmap folders with your own icon.
You can use Android Studio's Image Asset tool: Right-click res -> New -> Image Asset
'@ | Out-File -FilePath "app/src/main/res/mipmap-mdpi/README.txt" -Encoding UTF8

# ============================================
# 13. Create gradlew wrapper scripts
# ============================================
Write-Host "⚙️  Creating Gradle wrapper scripts..." -ForegroundColor Cyan

# gradlew.bat for Windows
@'
@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
'@ | Out-File -FilePath "gradlew.bat" -Encoding ASCII

# ============================================
# 14. Create README with video instructions
# ============================================
Write-Host "📄 Creating README..." -ForegroundColor Cyan
@'
# VRLA Forest Jog - VR Jogging Experience

## 🎥 IMPORTANT: Add Your Video!

**Before building the project, you MUST add your 360° video:**

1. Export your Insta360 video as **Side-by-Side Stereo 360°**
2. Rename it to: `forest_jog.mp4`
3. Copy it to: `app/src/main/res/raw/forest_jog.mp4`

### Video Requirements:
- Format: Side-by-Side 360° Stereo
- Codec: H.264 or H.265
- Resolution: Up to 4K (3840x1920 recommended)
- File size: Under 100MB for best performance

---

## 🚀 How to Build

### Step 1: Open in Android Studio
1. Open Android Studio
2. Click "Open an Existing Project"
3. Select the `VRLA` folder
4. Wait for Gradle sync to complete

### Step 2: Download Gradle Wrapper (First time only)
If you see Gradle errors, run this in the project directory:
```
gradle wrapper
```

### Step 3: Add Your Video
Copy `forest_jog.mp4` to `app/src/main/res/raw/`

### Step 4: Build and Run
1. Connect your Android device via USB (enable USB Debugging)
2. Click the green "Run" button in Android Studio
3. Select your device
4. Wait for the app to install

---

## 📱 Usage

1. Insert phone into VR headset (Cardboard-style)
2. Launch the app
3. Press "Start VR Experience" or start jogging in place
4. Look around by moving your head
5. Jog in place to control forward speed
6. Use "Recalibrate" button if orientation drifts

---

## ⚙️ Customization

### Adjust Speed Settings
Edit `app/src/main/java/com/vrla/forest/StepController.kt`:
- `minSpeed = 0.3f` - Minimum playback speed
- `maxSpeed = 2.5f` - Maximum playback speed
- `baseStepsPerMinute = 60` - Steps/min for 1.0x speed

### Change Video Loop Behavior
Edit `app/src/main/java/com/vrla/forest/VRRenderer.kt`, line ~240:
- `isLooping = true` for continuous loop
- `isLooping = false` for single playthrough

---

## 🐛 Troubleshooting

**Video not loading:**
- Verify `forest_jog.mp4` is in `app/src/main/res/raw/`
- File name must be exactly `forest_jog.mp4` (lowercase)
- Try Clean Project → Rebuild Project

**Permission errors:**
- Grant "Physical Activity" permission when prompted
- Check Android Settings → Apps → VRLA → Permissions

**Performance issues:**
- Reduce video resolution to 2K
- Lower sphere detail in VRRenderer.kt (sphereStacks/Sectors)

---

## 📊 Features

✅ 360° Stereo VR video playback
✅ Head tracking with gyroscope
✅ Step counter integration
✅ Dynamic speed control (0.3x - 2.5x)
✅ Real-time stats overlay
✅ Auto-calibration
✅ Fitness tracking (distance, calories)

---

## 🎯 Requirements

- Android 8.0 (API 26) or higher
- Gyroscope sensor
- Step counter sensor
- OpenGL ES 3.0 support
- VR headset (Cardboard-compatible)

---

Generated by VRLA Setup Script
'@ | Out-File -FilePath "README.md" -Encoding UTF8

Write-Host ""
Write-Host "✅ ============================================" -ForegroundColor Green
Write-Host "✅  VRLA Project Created Successfully!" -ForegroundColor Green
Write-Host "✅ ============================================" -ForegroundColor Green
Write-Host ""
Write-Host "📍 Project location: $(Get-Location)" -ForegroundColor Yellow
Write-Host ""
Write-Host "🎬 NEXT STEPS:" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. ➡️  Copy your video 'forest_jog.mp4' to:" -ForegroundColor White
Write-Host "   $PWD\app\src\main\res\raw\" -ForegroundColor Yellow
Write-Host ""
Write-Host "2. ➡️  Open Android Studio and select:" -ForegroundColor White
Write-Host "   File → Open → $(Get-Location)" -ForegroundColor Yellow
Write-Host ""
Write-Host "3. ➡️  Wait for Gradle sync to complete" -ForegroundColor White
Write-Host ""
Write-Host "4. ➡️  If Gradle sync fails, run:" -ForegroundColor White
Write-Host "   gradle wrapper" -ForegroundColor Yellow
Write-Host "   (in this directory)" -ForegroundColor White
Write-Host ""
Write-Host "5. ➡️  Connect your Android device and click Run!" -ForegroundColor White
Write-Host ""
Write-Host "📖 Read README.md for detailed instructions" -ForegroundColor Cyan
Write-Host ""
Write-Host "🎉 Happy VR Jogging!" -ForegroundColor Green
Write-Host ""